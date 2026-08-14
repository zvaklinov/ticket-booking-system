package com.hari.identityservice.auth;

import com.hari.identityservice.auth.dto.*;
import com.hari.identityservice.auth.exceptions.InvalidCredentialsException;
import com.hari.identityservice.jwt.JwtIssuer;
import com.hari.identityservice.user.Role;
import com.hari.identityservice.user.User;
import com.hari.identityservice.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtIssuer jwtIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Self-registration always creates a CUSTOMER. Admin accounts are provisioned
        // out of band — an endpoint that lets callers choose their own role is a
        // privilege-escalation hole.
        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                Role.CUSTOMER);

        // The unique constraint is the authority on duplicate emails; a pre-check would
        // be a check-then-act race.
        User saved = userRepository.saveAndFlush(user);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(),
                    "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        JwtIssuer.IssuedToken issued = jwtIssuer.issue(user);
        return new AuthResponse(
                issued.token(), "Bearer", issued.expiresAt(),
                user.getId(), user.getRole().name());
    }
}