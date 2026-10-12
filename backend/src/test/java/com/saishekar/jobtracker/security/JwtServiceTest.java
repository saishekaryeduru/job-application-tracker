package com.saishekar.jobtracker.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwt = new JwtService(
            "unit-test-secret-unit-test-secret-unit-test-secret-0123456789", 60_000);

    @Test
    void roundTripsTheSubject() {
        String token = jwt.generateToken("someone@example.com");
        assertTrue(jwt.isValid(token));
        assertEquals("someone@example.com", jwt.extractEmail(token));
    }

    @Test
    void rejectsGarbageTokens() {
        assertFalse(jwt.isValid("not-a-jwt"));
        assertFalse(jwt.isValid(""));
    }

    @Test
    void rejectsTokensFromAnotherSecret() {
        JwtService other = new JwtService(
                "different-secret-different-secret-different-secret-9876543210", 60_000);
        assertFalse(jwt.isValid(other.generateToken("someone@example.com")));
    }
}
