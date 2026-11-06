package com.mastek.pulse.common;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: PulseException.java
 * Journey: All Journeys (API Boundary & Error Contract - Proposal Page 15).
 * Why it is designed this way:
 *   Page 15 of the Mastek Pulse proposal explicitly specifies HTTP status codes:
 *   - 401 (UNAUTHORIZED): Invalid or missing authentication.
 *   - 403 (FORBIDDEN): Denied audience access, missing portal role, or self-approval attempt.
 *   - 409 (CONFLICT): Stale version number, full host capacity, or expired/paused invitation.
 *   - 422 (UNPROCESSABLE_ENTITY): Invalid user input or schema constraint violation.
 *   Having one typed exception hierarchy makes every service error predictable for
 *   React Web, Publishing Portal, and React Native Mobile clients.
 * ============================================================================
 */
public class PulseException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PulseException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static PulseException unauthorized(String message) {
        return new PulseException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static PulseException forbidden(String code, String message) {
        return new PulseException(HttpStatus.FORBIDDEN, code, message);
    }

    public static PulseException conflict(String code, String message) {
        return new PulseException(HttpStatus.CONFLICT, code, message);
    }

    public static PulseException unprocessable(String message) {
        return new PulseException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
    }

    public static PulseException notFound(String message) {
        return new PulseException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    public Map<String, Object> toProblemDetail() {
        return Map.of(
                "status", status.value(),
                "code", code,
                "message", getMessage(),
                "timestamp", Instant.now().toString()
        );
    }
}
