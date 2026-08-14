package com.hari.identityservice.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Configuration
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    /**
     * Self-generating keys is a development convenience. In production, keys are provisioned
     * externally (secrets manager, mounted volume, KMS), and the application only ever reads
     * them — an application that can create its own signing keys can also silently replace
     * them, which invalidates every token already issued.
     */
    @Bean
    public RSAKey rsaKey(@Value("${identity.jwt.key-file}") String keyFilePath) throws Exception {
        Path path = Path.of(keyFilePath);

        if (Files.exists(path)) {
            return RSAKey.parse(Files.readString(path));
        }

        log.warn("No signing key found at {} — generating a new one. "
                + "Existing tokens (if any) are now invalid.", path.toAbsolutePath());

        RSAKey generated = new RSAKeyGenerator(2048)
                .keyID(UUID.randomUUID().toString())
                .generate();

        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, generated.toJSONString());

        return generated;
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }
}