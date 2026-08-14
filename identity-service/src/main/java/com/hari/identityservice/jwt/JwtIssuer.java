package com.hari.identityservice.jwt;

import com.hari.identityservice.user.User;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class JwtIssuer {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtIssuer(JWKSource<SecurityContext> jwkSource,
                     Clock clock,
                     @Value("${identity.jwt.issuer}") String issuer,
                     @Value("${identity.jwt.access-token-ttl}") Duration accessTokenTtl) {
        this.encoder = new NimbusJwtEncoder(jwkSource);
        this.clock = clock;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTokenTtl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                // The subject is the user id, not the email: other services derive user_id
                // from it, and an email can change while an id cannot.
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}