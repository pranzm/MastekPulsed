package com.mastek.pulse.common;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: IdentityService.java
 * Journey: API Boundary Auth & Role Enforcement (Proposal Pages 5, 11, 14, 15).
 * Why it is designed this way:
 *   1. In our POC demo, clients pass `X-Pulse-User-Id` (e.g., `usr_asha`, `usr_daniel`,
 *      `usr_priya`, `usr_marcus`, `usr_elena`) to switch personas effortlessly.
 *      If `"NONE"` or an invalid user ID is passed, it throws `401 Unauthorized`.
 *   2. Provides `requireRole(...)` and `requireAudienceAccess(...)` helper methods
 *      that throw `403 Forbidden` whenever a caller lacks a required portal role
 *      or attempts to access content outside their `audience_members` scope.
 * ============================================================================
 */
@Service
public class IdentityService {

    private final UserRepository userRepository;
    private final AudienceRepository audienceRepository;

    public IdentityService(UserRepository userRepository, AudienceRepository audienceRepository) {
        this.userRepository = userRepository;
        this.audienceRepository = audienceRepository;
    }

    @Transactional(readOnly = true)
    public UserEntity authenticate(String headerUserId) {
        String effectiveUserId = (headerUserId == null || headerUserId.isBlank()) ? "usr_asha" : headerUserId.trim();
        if ("INVALID".equalsIgnoreCase(effectiveUserId) || "ANONYMOUS".equalsIgnoreCase(effectiveUserId)) {
            throw PulseException.unauthorized("401: Invalid or expired authentication token.");
        }
        return userRepository.findById(effectiveUserId)
                .orElseThrow(() -> PulseException.unauthorized("401: Unknown employee identity: " + effectiveUserId));
    }

    @Transactional(readOnly = true)
    public List<String> getPermittedAudiences(String userId) {
        return audienceRepository.findPermittedAudienceIdsByUserId(userId);
    }

    public void requireRole(UserEntity user, String requiredRole) {
        if (!user.hasRole(requiredRole)) {
            throw PulseException.forbidden(
                    "FORBIDDEN_ACCESS",
                    "403: User '" + user.getDisplayName() + "' lacks required scoped role: " + requiredRole
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireAudienceAccess(UserEntity user, String audienceId) {
        List<String> permitted = getPermittedAudiences(user.getId());
        if (!permitted.contains(audienceId)) {
            throw PulseException.forbidden(
                    "FORBIDDEN_ACCESS",
                    "403: User '" + user.getDisplayName() + "' does not have access to restricted audience: " + audienceId
            );
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> toUserProfileDto(UserEntity user) {
        return Map.of(
                "id", user.getId(),
                "displayName", user.getDisplayName(),
                "email", user.getEmail(),
                "region", user.getRegion(),
                "functionName", user.getFunctionName(),
                "roles", user.getRolesList(),
                "interests", user.getInterestsList(),
                "audiences", getPermittedAudiences(user.getId()),
                "notificationFrequency", user.getNotificationFrequency(),
                "hostPaused", user.isHostPaused()
        );
    }
}
