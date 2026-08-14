package com.hari.bookingservice.security;

import com.hari.bookingservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization rules only — no business behaviour is asserted here. Every endpoint used either
 * reads from the local database or is rejected before reaching business logic, so these tests
 * need no other service running.
 *
 * The jwt() post-processor injects an already-decoded token, so these tests exercise the
 * authorization rules rather than signature verification. Decoding itself is covered by
 * JwtValidationTest in identity-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleEnforcementIT {

    @Autowired private MockMvc mockMvc;

    private static final UUID USER_ID = UUID.randomUUID();

    private static RequestPostProcessor customer() {
        return jwt()
                .jwt(builder -> builder
                        .subject(USER_ID.toString())
                        .claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor admin() {
        return jwt()
                .jwt(builder -> builder
                        .subject(USER_ID.toString())
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    // ---------- unauthenticated ----------

    @Test
    void seatMapRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/seats").param("eventId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingASeatHoldRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/seat-holds")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + UUID.randomUUID() + "\",\"seatIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingBookingsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/bookings"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- both roles may browse ----------

    @Test
    void aCustomerMayViewTheSeatMap() throws Exception {
        mockMvc.perform(get("/seats")
                        .param("eventId", UUID.randomUUID().toString())
                        .with(customer()))
                .andExpect(status().isOk());
    }

    @Test
    void anAdminMayViewTheSeatMap() throws Exception {
        mockMvc.perform(get("/seats")
                        .param("eventId", UUID.randomUUID().toString())
                        .with(admin()))
                .andExpect(status().isOk());
    }

    // ---------- admin-only ----------

    @Test
    void aCustomerMayNotCreateSeats() throws Exception {
        mockMvc.perform(post("/seats")
                        .with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"%s","seatLabel":"A1","price":50.00,"currency":"EUR"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCustomerMayNotMarkASeatUnavailable() throws Exception {
        mockMvc.perform(post("/seats/" + UUID.randomUUID() + "/unavailable").with(customer()))
                .andExpect(status().isForbidden());
    }

    // ---------- customer-only: roles are exclusive, not hierarchical ----------

    @Test
    void anAdminMayNotHoldSeats() throws Exception {
        // Per the permissions table, "Hold seats" is No for ADMIN and Yes for CUSTOMER.
        // An admin is deliberately not a superset of a customer.
        mockMvc.perform(post("/seat-holds")
                        .with(admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"%s","seatIds":["%s"]}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminMayNotListBookings() throws Exception {
        mockMvc.perform(get("/bookings").with(admin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCustomerMayListTheirOwnBookings() throws Exception {
        mockMvc.perform(get("/bookings").with(customer()))
                .andExpect(status().isOk());
    }

    // ---------- token without the expected role claim ----------

    @Test
    void aTokenWithNoRolesIsForbiddenFromEverythingRoleGuarded() throws Exception {
        RequestPostProcessor noRoles = jwt()
                .jwt(builder -> builder.subject(USER_ID.toString()));

        mockMvc.perform(get("/bookings").with(noRoles))
                .andExpect(status().isForbidden());
    }
}