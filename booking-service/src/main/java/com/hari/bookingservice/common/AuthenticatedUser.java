package com.hari.bookingservice.common;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    /**
     * The user id is always the JWT subject — never a request field. A client-supplied user id
     * would let any authenticated caller act as anyone else.
     */
    public static UUID idOf(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}