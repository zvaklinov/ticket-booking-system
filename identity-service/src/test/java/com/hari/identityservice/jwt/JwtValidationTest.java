package com.hari.identityservice.jwt;

import com.hari.identityservice.user.Role;
import com.hari.identityservice.user.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token issuance and validation, with no Spring context and no database — this is pure crypto
 * and claim handling. Covers expiry behaviour
 * and invalid-signature behaviour.
 */
class JwtValidationTest {

    private static final String ISSUER = "http://localhost:8083";
    private static final Duration TTL = Duration.ofMinutes(30);

    private static RSAKey signingKey;
    private static RSAKey otherKey;
    private static User user;

    @BeforeAll
    static void generateKeys() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("other-key").generate();

        user = new User("alice@example.com", "$2a$10$irrelevant", Role.CUSTOMER);
        // The entity's id is normally assigned by Hibernate on persist; this test never touches
        // a database, so it is set directly.
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void issuedTokenCarriesTheExpectedClaims() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtIssuer issuer = issuerAt(now);

        Jwt decoded = decoderFor(signingKey).decode(issuer.issue(user).token());

        assertEquals(user.getId().toString(), decoded.getSubject(),
                "the subject must be the user id — other services derive user_id from it");
        assertEquals(ISSUER, decoded.getClaimAsString("iss"));
        assertEquals(List.of("CUSTOMER"), decoded.getClaimAsStringList("roles"));
        assertEquals("alice@example.com", decoded.getClaimAsString("email"));
        assertEquals(now, decoded.getIssuedAt());
        assertEquals(now.plus(TTL), decoded.getExpiresAt());
    }

    @Test
    void theTokenNeverContainsThePasswordHash() throws Exception {
        JwtIssuer issuer = issuerAt(Instant.now());
        Jwt decoded = decoderFor(signingKey).decode(issuer.issue(user).token());

        // JWT payloads are base64-encoded, not encrypted — anyone holding a token can read
        // every claim in it. Nothing sensitive belongs in there.
        assertFalse(decoded.getClaims().containsKey("passwordHash"));
        assertFalse(decoded.getClaims().containsKey("password"));
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        // Issued far enough in the past that it expired well before now.
        JwtIssuer issuer = issuerAt(Instant.now().minus(Duration.ofHours(2)));
        String expiredToken = issuer.issue(user).token();

        JwtException thrown = assertThrows(JwtException.class,
                () -> decoderFor(signingKey).decode(expiredToken));

        assertTrue(thrown.getMessage().toLowerCase().contains("expired")
                        || thrown.getMessage().toLowerCase().contains("exp"),
                () -> "expected an expiry failure but got: " + thrown.getMessage());
    }

    @Test
    void aTokenSignedWithADifferentKeyIsRejected() throws Exception {
        // Signed with otherKey, verified against signingKey — this is what a forged token
        // looks like to a resource server.
        JwtIssuer forger = new JwtIssuer(
                new ImmutableJWKSet<>(new JWKSet(otherKey)),
                Clock.systemUTC(), ISSUER, TTL);

        String forgedToken = forger.issue(user).token();

        assertThrows(JwtException.class, () -> decoderFor(signingKey).decode(forgedToken));
    }

    @Test
    void aTamperedTokenIsRejected() throws Exception {
        JwtIssuer issuer = issuerAt(Instant.now());
        String token = issuer.issue(user).token();

        // Flip a character in the payload segment. The signature covers header and payload,
        // so any edit invalidates it — this is what stops a client editing its own role claim.
        String[] parts = token.split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'e' ? 'f' : 'e';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        assertThrows(JwtException.class, () -> decoderFor(signingKey).decode(tampered));
    }

    @Test
    void thePublishedJwkContainsNoPrivateKeyMaterial() {
        RSAKey published = signingKey.toPublicJWK();

        // "d" is the private exponent. Publishing it at the JWKS endpoint would let anyone
        // mint tokens, defeating the entire point of asymmetric signing.
        assertFalse(published.toJSONObject().containsKey("d"));
        assertFalse(published.toJSONObject().containsKey("p"));
        assertFalse(published.toJSONObject().containsKey("q"));
        assertTrue(published.toJSONObject().containsKey("n"));
        assertTrue(published.toJSONObject().containsKey("e"));
    }

    private JwtIssuer issuerAt(Instant now) {
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(signingKey));
        return new JwtIssuer(source, Clock.fixed(now, ZoneOffset.UTC), ISSUER, TTL);
    }

    private NimbusJwtDecoder decoderFor(RSAKey key) throws Exception {
        return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
    }
}