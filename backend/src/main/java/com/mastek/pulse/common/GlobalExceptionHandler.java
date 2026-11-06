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
 * Journey: API Boundary Middleware (Proposal Pages 14 & 15).
 * Why it is designed this way:
 *   1. Translates any `PulseException` or Bean Validation failure into the exact
 *      `ProblemDetail` JSON structure defined in `docs/aidlc/openapi.yaml`.
 *   2. Maps validation errors (`@Valid`) and missing `Idempotency-Key` headers
 *      to HTTP 422 Unprocessable Entity (as required by Page 15: "422 for invalid input").
 *   3. Configures CORS so the React Web App, Publishing Portal, and Expo Mobile
 *      client can call `/api/v1/**` seamlessly during local development.
 * ============================================================================
 */
@RestControllerAdvice
@Configuration
public class GlobalExceptionHandler {

    @ExceptionHandler(PulseException.class)
    public ResponseEntity<Map<String, Object>> handlePulseException(PulseException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.toProblemDetail());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "status", 422,
                "code", "VALIDATION_FAILED",
                "message", details.isBlank() ? "Invalid input payload" : details,
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "status", 422,
                "code", "VALIDATION_FAILED",
                "message", "Missing required header: " + ex.getHeaderName(),
                "timestamp", Instant.now().toString()
        ));
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
