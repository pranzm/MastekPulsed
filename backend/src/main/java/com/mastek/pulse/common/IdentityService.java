package com.mastek.pulse.common;

import com.mastek.pulse.content.ContentItemEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: IdentityService.java
 * Journey: Shared Publication Visibility & Scoped Authorization Policy
 *          (Proposal Pages 5, 11, 14, 15 + Campfire CF-010 / D2).
 * Why it is designed this way:
 *   1. Synthetic Demo Identity vs. Strict Live Header Check:
 *      In controlled demo mode, `X-Pulse-User-Id` selects one of our synthetic
 *      personas (`usr_asha`, `usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`,
 *      `usr_rohan`, `usr_sophie`, `usr_carlos`). It does NOT claim corporate SSO.
 *      When `requireExplicitHeader` is enabled (or `"NONE"` / `"INVALID"` /
 *      `"ANONYMOUS"` is passed), missing/unknown identities throw `401 Unauthorized`.
 *   2. Centralized Visibility & Editorial Policy (D2 / CF-010):
 *      - `requirePortalAccess(user)`: Blocks ordinary employees (`EMPLOYEE`, `CREATOR`)
 *        from reading the Publishing Portal review queue or audit log (`403`).
 *      - `requirePublishedVisibility(user, item)`: Ensures employee feed, story detail,
 *        appreciations, and AI context ONLY read `PUBLISHED` items (`publishedVersion != null`)
 *        and check `item.getEffectivePublishedAudienceId()` so draft audience edits
 *        or withdrawn items are never disclosed!
 * ============================================================================
 */
@Service
public class IdentityService {

    private final UserRepository userRepository;
    private final AudienceRepository audienceRepository;

    @Value("${pulse.auth.allow-default-persona:true}")
    private boolean allowDefaultPersona;

    private Boolean runtimeRequireExplicitHeader = null;

    public IdentityService(UserRepository userRepository, AudienceRepository audienceRepository) {
        this.userRepository = userRepository;
        this.audienceRepository = audienceRepository;
    }

    public void setRuntimeRequireExplicitHeader(Boolean requireExplicit) {
        this.runtimeRequireExplicitHeader = requireExplicit;
    }

    @Transactional(readOnly = true)
    public UserEntity authenticate(String headerUserId) {
        boolean mustProvideHeader = runtimeRequireExplicitHeader != null
                ? runtimeRequireExplicitHeader
                : !allowDefaultPersona;

        if (headerUserId == null || headerUserId.isBlank()) {
            if (mustProvideHeader) {
                throw PulseException.unauthorized("401: Missing required authentication identity header.");
            }
            headerUserId = "usr_asha";
        }

        String effectiveUserId = headerUserId.trim();
        if ("INVALID".equalsIgnoreCase(effectiveUserId)
                || "ANONYMOUS".equalsIgnoreCase(effectiveUserId)
                || "NONE".equalsIgnoreCase(effectiveUserId)) {
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

    public boolean hasPortalAccess(UserEntity user) {
        return user.hasRole("REVIEWER")
                || user.hasRole("PUBLISHER")
                || user.hasRole("MODERATOR")
                || user.hasRole("ADMIN");
    }

    public void requirePortalAccess(UserEntity user) {
        if (!hasPortalAccess(user)) {
            throw PulseException.forbidden(
                    "FORBIDDEN_ACCESS",
                    "403: User '" + user.getDisplayName() + "' lacks Publishing Portal role (REVIEWER, PUBLISHER, MODERATOR, or ADMIN)."
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

    /**
     * Enforces D2 / CF-010 Published Snapshot Visibility:
     * 1. Checks caller's audience access against the item's immutable published audience
     *    (or working audience if never published, so unauthorized probes always get 403).
     * 2. Requires `PUBLISHED` status and a non-null `publishedVersion`. Drafts,
     *    in-review items, approved-but-unpublished items, and withdrawn items
     *    are denied (`404 Not Found`) on employee detail, appreciation, and AI reads!
     */
    @Transactional(readOnly = true)
    public void requirePublishedVisibility(UserEntity caller, ContentItemEntity item) {
        String targetAudience = item.getEffectivePublishedAudienceId();
        requireAudienceAccess(caller, targetAudience);

        if (!"PUBLISHED".equals(item.getStatus()) || item.getPublishedVersion() == null) {
            throw PulseException.notFound("404: Content item is not published or has been withdrawn: " + item.getId());
        }
    }

    /**
     * Enforces owner or scoped editorial preview access for unpublished revisions.
     */
    @Transactional(readOnly = true)
    public void requirePreviewVisibility(UserEntity caller, ContentItemEntity item) {
        if (item.getAuthorId().equals(caller.getId())) {
            return;
        }
        if (caller.hasRole("REVIEWER") || caller.hasRole("PUBLISHER") || caller.hasRole("MODERATOR")) {
            requireAudienceAccess(caller, item.getAudienceId());
            return;
        }
        throw PulseException.forbidden(
                "FORBIDDEN_ACCESS",
                "403: Only the author or a scoped reviewer/publisher for audience '" + item.getAudienceId() + "' can preview unpublished revisions."
        );
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
