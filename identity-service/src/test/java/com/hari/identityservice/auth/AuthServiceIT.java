package com.hari.identityservice.auth;

import com.hari.identityservice.TestcontainersConfiguration;
import com.hari.identityservice.auth.dto.AuthResponse;
import com.hari.identityservice.auth.dto.LoginRequest;
import com.hari.identityservice.auth.dto.RegisterRequest;
import com.hari.identityservice.auth.exceptions.InvalidCredentialsException;
import com.hari.identityservice.user.Role;
import com.hari.identityservice.user.User;
import com.hari.identityservice.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Authentication behaviour, tested independently of any business logic.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuthServiceIT {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE users CASCADE");
    }

    @Test
    void registrationCreatesACustomerAndReturnsAToken() {
        AuthResponse response = authService.register(
                new RegisterRequest("alice@example.com", "correct-horse-battery"));

        assertNotNull(response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals("CUSTOMER", response.role());
        assertNotNull(response.userId());
        assertTrue(response.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void registrationNeverStoresThePlaintextPassword() {
        String password = "correct-horse-battery";
        authService.register(new RegisterRequest("alice@example.com", password));

        User stored = userRepository.findByEmail("alice@example.com").orElseThrow();

        assertNotEquals(password, stored.getPasswordHash());
        assertTrue(stored.getPasswordHash().startsWith("$2"), "should be a BCrypt hash");
        assertTrue(passwordEncoder.matches(password, stored.getPasswordHash()));
    }

    @Test
    void thesamePasswordProducesDifferentHashesForDifferentUsers() {
        authService.register(new RegisterRequest("alice@example.com", "same-password"));
        authService.register(new RegisterRequest("bob@example.com", "same-password"));

        String aliceHash = userRepository.findByEmail("alice@example.com").orElseThrow().getPasswordHash();
        String bobHash = userRepository.findByEmail("bob@example.com").orElseThrow().getPasswordHash();

        assertNotEquals(aliceHash, bobHash);
    }

    @Test
    void registrationAlwaysAssignsCustomerRegardlessOfWhatIsRequested() {
        authService.register(new RegisterRequest("alice@example.com", "correct-horse-battery"));

        assertEquals(Role.CUSTOMER, userRepository.findByEmail("alice@example.com").orElseThrow().getRole());
    }

    @Test
    void duplicateEmailIsRejected() {
        authService.register(new RegisterRequest("alice@example.com", "correct-horse-battery"));

        assertThrows(DataIntegrityViolationException.class, () ->
                authService.register(new RegisterRequest("alice@example.com", "different-password")));

        assertEquals(1, userRepository.count());
    }

    @Test
    void emailIsNormalisedSoCaseDoesNotCreateDuplicateAccounts() {
        authService.register(new RegisterRequest("Alice@Example.COM", "correct-horse-battery"));

        assertTrue(userRepository.findByEmail("alice@example.com").isPresent());

        assertThrows(DataIntegrityViolationException.class, () ->
                authService.register(new RegisterRequest("ALICE@EXAMPLE.COM", "another-password")));
    }

    @Test
    void loginWithCorrectCredentialsReturnsAToken() {
        AuthResponse registered = authService.register(
                new RegisterRequest("alice@example.com", "correct-horse-battery"));

        AuthResponse loggedIn = authService.login(
                new LoginRequest("alice@example.com", "correct-horse-battery"));

        assertEquals(registered.userId(), loggedIn.userId());
        assertNotNull(loggedIn.accessToken());
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() {
        authService.register(new RegisterRequest("alice@example.com", "correct-horse-battery"));

        assertDoesNotThrow(() ->
                authService.login(new LoginRequest("ALICE@example.com", "correct-horse-battery")));
    }

    @Test
    void loginWithTheWrongPasswordIsRejected() {
        authService.register(new RegisterRequest("alice@example.com", "correct-horse-battery"));

        assertThrows(InvalidCredentialsException.class, () ->
                authService.login(new LoginRequest("alice@example.com", "wrong-password")));
    }

    @Test
    void loginWithAnUnknownEmailFailsIdenticallyToAWrongPassword() {
        authService.register(new RegisterRequest("alice@example.com", "correct-horse-battery"));

        InvalidCredentialsException unknownEmail = assertThrows(InvalidCredentialsException.class, () ->
                authService.login(new LoginRequest("nobody@example.com", "correct-horse-battery")));

        InvalidCredentialsException wrongPassword = assertThrows(InvalidCredentialsException.class, () ->
                authService.login(new LoginRequest("alice@example.com", "wrong-password")));

        // Identical failures on purpose: a distinguishable "no such user" response lets an
        // attacker enumerate which email addresses are registered.
        assertEquals(unknownEmail.getMessage(), wrongPassword.getMessage());
    }
}