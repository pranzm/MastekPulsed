package com.mastek.pulse.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: GlobalExceptionHandler.java
 * Journey: API Boundary Middleware across all journeys (Proposal Pages 14 & 15).
 *
 * Why it is designed this way:
 *   1. Translates any domain `PulseException` or Bean Validation failure into the exact
 *      `ProblemDetail` JSON structure (`status`, `code`, `message`, `timestamp`) defined
 *      in `docs/aidlc/openapi.yaml` and `docs/aidlc/02-domain-model-and-contracts.md`.
 *   2. Maps validation errors (`@Valid`) and missing required headers (such as
 *      `Idempotency-Key` on `POST /api/v1/requests`) to HTTP `422 Unprocessable Entity`
 *      (as required by Proposal Page 15: "422 for invalid input").
 *   3. Configures Cross-Origin Resource Sharing (CORS) so the React Web App,
 *      Role-Based Publishing Portal, and Expo Mobile client can call `/api/v1/**`
 *      seamlessly during local development and live demos.
 *
 * Key Annotations Explained for Interns:
 *   - `@RestControllerAdvice`: Global interceptor that wraps all `@RestController`
 *     classes. Whenever any controller or service throws an exception, Spring routes
 *     it here instead of returning a generic HTML stack-trace page.
 *   - `@Configuration`: Allows this class to also register Spring `@Bean` definitions
 *     (specifically our `corsConfigurer()` bean).
 *   - `@ExceptionHandler(SomeException.class)`: Registers a method to catch and
 *     transform a specific exception type into a structured HTTP `ResponseEntity`.
 * ============================================================================
 */
@RestControllerAdvice
@Configuration
public class GlobalExceptionHandler {

    /**
     * Catches all domain-specific `PulseException` instances (401, 403, 404, 409, 422)
     * thrown anywhere in our services or controllers and serializes them into our
     * canonical JSON error format.
     *
     * @param ex The `PulseException` thrown by business logic or authorization checks.
     * @return `ResponseEntity` with the exact HTTP status (`ex.getStatus()`) and
     *         JSON body `{status, code, message, timestamp}`.
     */
    @ExceptionHandler(PulseException.class)
    public ResponseEntity<Map<String, Object>> handlePulseException(PulseException ex) {
        // Set the HTTP status code from the exception and serialize its ProblemDetail map as JSON.
        return ResponseEntity.status(ex.getStatus()).body(ex.toProblemDetail());
    }

    /**
     * Catches Jakarta Bean Validation (`@Valid`) failures on incoming request DTOs
     * and converts them into an HTTP `422 Unprocessable Entity` response.
     *
     * @param ex The `MethodArgumentNotValidException` raised by Spring MVC when DTO validation fails.
     * @return `ResponseEntity` with HTTP status 422 and joined field-level validation messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // Extract each failing field name and its validation message, joining them with "; ".
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        // Return HTTP 422 Unprocessable Entity with a structured ProblemDetail map.
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "status", 422,
                "code", "VALIDATION_FAILED",
                "message", details.isBlank() ? "Invalid input payload" : details,
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Catches missing required HTTP headers (for example, omitting `Idempotency-Key`
     * on `POST /api/v1/requests`) and returns HTTP `422 Unprocessable Entity`.
     *
     * @param ex The `MissingRequestHeaderException` thrown when a required `@RequestHeader` is absent.
     * @return `ResponseEntity` with HTTP status 422 naming the missing header.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        // Return HTTP 422 Unprocessable Entity explaining which required header was omitted.
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "status", 422,
                "code", "VALIDATION_FAILED",
                "message", "Missing required header: " + ex.getHeaderName(),
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Registers a `WebMvcConfigurer` bean that enables CORS (Cross-Origin Resource Sharing)
     * on `/api/**` for all origins, HTTP verbs (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`),
     * and custom headers (`X-Pulse-User-Id`, `Idempotency-Key`, `Content-Type`).
     *
     * @return Configured `WebMvcConfigurer` instance.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Allow browser and mobile clients running on any local port (e.g., Vite :5173, Expo :8081)
                // to invoke `/api/**` endpoints with custom headers.
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
