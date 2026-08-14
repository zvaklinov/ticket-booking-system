package com.hari.identityservice.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final JWKSet publicJwkSet;

    public JwksController(RSAKey rsaKey) {
        // toPublicJWK() strips the private key material. Publishing the full key would
        // let anyone mint tokens, which defeats the entire point of asymmetric signing.
        this.publicJwkSet = new JWKSet(rsaKey.toPublicJWK());
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return publicJwkSet.toJSONObject();
    }
}