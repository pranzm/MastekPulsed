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
 *
 * Why it is designed this way:
 *   Page 15 of the Mastek Pulse proposal explicitly specifies HTTP status codes:
 *   - 401 (`UNAUTHORIZED`): Invalid or missing authentication.
 *   - 403 (`FORBIDDEN`): Denied audience access, missing portal role, or self-approval attempt.
 *   - 404 (`NOT_FOUND`): Unknown entity ID, or unpublished/withdrawn content on employee reads.
 *   - 409 (`CONFLICT`): Stale version number, full host capacity, or expired/paused invitation.
 *   - 422 (`UNPROCESSABLE_ENTITY`): Invalid user input, AI refusal policy, or schema violation.
 *   Because `PulseException` extends `RuntimeException` (an unchecked exception in Java),
 *   any `@Transactional` service method that throws a `PulseException` automatically
 *   triggers a clean database transaction rollback in Spring, and `GlobalExceptionHandler`
 *   serializes `toProblemDetail()` into a consistent JSON response for React Web,
 *   Publishing Portal, and React Native Mobile clients.
 * ============================================================================
 */
public class PulseException extends RuntimeException {

    // HTTP status enum (e.g. 401, 403, 404, 409, 422) returned in the HTTP response header.
    private final HttpStatus status;

    // Machine-readable error code string (e.g. "FORBIDDEN_ACCESS", "SELF_APPROVAL_FORBIDDEN", "CAPACITY_FULL").
    private final String code;

    /**
     * Constructs a new `PulseException` with an explicit HTTP status, machine code, and human message.
     *
     * @param status  Spring `HttpStatus` to return to the client.
     * @param code    Machine-readable error code for frontend branching.
     * @param message Human-readable explanation of what went wrong.
     */
    public PulseException(HttpStatus status, String code, String message) {
        // Pass the human-readable message up to RuntimeException.
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * @return The HTTP status code associated with this exception.
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * @return The machine-readable error code string (e.g., `"FORBIDDEN_ACCESS"`).
     */
    public String getCode() {
        return code;
    }

    /**
     * Factory method for `401 Unauthorized` errors (missing or invalid `X-Pulse-User-Id` identity).
     *
     * @param message Human-readable explanation.
     * @return `PulseException` with status `401 UNAUTHORIZED` and code `"UNAUTHORIZED"`.
     */
    public static PulseException unauthorized(String message) {
        return new PulseException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    /**
     * Factory method for `403 Forbidden` errors (audience restriction, missing portal role,
     * or attempting to self-approve one's own content).
     *
     * @param code    Specific forbidden code (e.g., `"FORBIDDEN_ACCESS"`, `"SELF_APPROVAL_FORBIDDEN"`).
     * @param message Human-readable explanation.
     * @return `PulseException` with status `403 FORBIDDEN`.
     */
    public static PulseException forbidden(String code, String message) {
        return new PulseException(HttpStatus.FORBIDDEN, code, message);
    }

    /**
     * Factory method for `409 Conflict` errors (stale revision approval, full host capacity,
     * paused host availability, or invalid state machine transition).
     *
     * @param code    Specific conflict code (e.g., `"CAPACITY_FULL"`, `"STALE_VERSION"`).
     * @param message Human-readable explanation.
     * @return `PulseException` with status `409 CONFLICT`.
     */
    public static PulseException conflict(String code, String message) {
        return new PulseException(HttpStatus.CONFLICT, code, message);
    }

    /**
     * Factory method for `422 Unprocessable Entity` errors (validation failure, missing required
     * field/header, or AI Trust Policy refusal).
     *
     * @param message Human-readable explanation.
     * @return `PulseException` with status `422 UNPROCESSABLE_ENTITY` and code `"VALIDATION_FAILED"`.
     */
    public static PulseException unprocessable(String message) {
        return new PulseException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
    }

    /**
     * Factory method for `404 Not Found` errors (unknown ID or unpublished/withdrawn content).
     *
     * @param message Human-readable explanation.
     * @return `PulseException` with status `404 NOT_FOUND` and code `"RESOURCE_NOT_FOUND"`.
     */
    public static PulseException notFound(String message) {
        return new PulseException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    /**
     * Converts this exception into the standardized `ProblemDetail` JSON map defined
     * in `docs/aidlc/openapi.yaml`.
     *
     * @return Immutable map containing `status`, `code`, `message`, and ISO-8601 `timestamp`.
     */
    public Map<String, Object> toProblemDetail() {
        return Map.of(
                "status", status.value(),
                "code", code,
                "message", getMessage(),
                "timestamp", Instant.now().toString()
        );
    }
}
