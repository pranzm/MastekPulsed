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
 *
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
 *      - `requirePreviewVisibility(user, item)`: Restricts unpublished draft/review preview
 *        to the item's own author or a scoped `REVIEWER` / `PUBLISHER` / `MODERATOR`
 *        for that audience (`403` otherwise).
 * ============================================================================
 */
@Service
public class IdentityService {

    // Repository used to look up employee accounts by `users.id`.
    private final UserRepository userRepository;

    // Repository used to resolve a user's permitted `audience_id` memberships.
    private final AudienceRepository audienceRepository;

    /**
     * `@Value("${pulse.auth.allow-default-persona:true}")` controls whether requests
     * without an `X-Pulse-User-Id` header fall back to `"usr_asha"` (convenient for
     * browser exploration) or fail with `401 Unauthorized`.
     */
    @Value("${pulse.auth.allow-default-persona:true}")
    private boolean allowDefaultPersona;

    /**
     * Optional runtime override used by automated integration tests to enforce strict
     * header requirement without restarting the Spring context.
     */
    private Boolean runtimeRequireExplicitHeader = null;

    /**
     * Constructor injection for `IdentityService`.
     *
     * @param userRepository     Repository for `UserEntity` lookups.
     * @param audienceRepository Repository for `audience_members` membership queries.
     */
    public IdentityService(UserRepository userRepository, AudienceRepository audienceRepository) {
        this.userRepository = userRepository;
        this.audienceRepository = audienceRepository;
    }

    /**
     * Allows tests or admin switches to toggle whether an explicit `X-Pulse-User-Id`
     * header is mandatory on every request.
     *
     * @param requireExplicit `true` to require explicit header, `false` to allow fallback,
     *                        or `null` to defer to `pulse.auth.allow-default-persona`.
     */
    public void setRuntimeRequireExplicitHeader(Boolean requireExplicit) {
        this.runtimeRequireExplicitHeader = requireExplicit;
    }

    /**
     * Authenticates the caller based on the `X-Pulse-User-Id` request header.
     *
     * Step-by-step logic:
     *   1. Determine if an explicit header is mandatory (`mustProvideHeader`).
     *   2. If `headerUserId` is null/blank:
     *      - Throw `401 Unauthorized` if `mustProvideHeader` is true.
     *      - Otherwise default to `"usr_asha"` (Asha Patel, Senior Engineer in UK Engineering).
     *   3. If the header value is `"INVALID"`, `"ANONYMOUS"`, or `"NONE"` (used by security
     *      contract tests and unauthenticated probes), throw `401 Unauthorized`.
     *   4. Look up the user in PostgreSQL by ID, throwing `401 Unauthorized` if unknown.
     *
     * @param headerUserId Raw value of the `X-Pulse-User-Id` HTTP header (may be null).
     * @return Authenticated `UserEntity` from the database.
     * @throws PulseException HTTP 401 (`UNAUTHORIZED`) if missing, invalid, or unknown.
     */
    @Transactional(readOnly = true)
    public UserEntity authenticate(String headerUserId) {
        // Step 1: Check whether default persona fallback is disabled.
        boolean mustProvideHeader = runtimeRequireExplicitHeader != null
                ? runtimeRequireExplicitHeader
                : !allowDefaultPersona;

        // Step 2: Handle missing or blank X-Pulse-User-Id header.
        if (headerUserId == null || headerUserId.isBlank()) {
            if (mustProvideHeader) {
                throw PulseException.unauthorized("401: Missing required authentication identity header.");
            }
            // In demo mode, default to Asha Patel ("usr_asha") when no header is supplied.
            headerUserId = "usr_asha";
        }

        // Step 3: Trim whitespace and reject explicit unauthenticated/invalid sentinel tokens.
        String effectiveUserId = headerUserId.trim();
        if ("INVALID".equalsIgnoreCase(effectiveUserId)
                || "ANONYMOUS".equalsIgnoreCase(effectiveUserId)
                || "NONE".equalsIgnoreCase(effectiveUserId)) {
            throw PulseException.unauthorized("401: Invalid or expired authentication token.");
        }

        // Step 4: Load the UserEntity from PostgreSQL or throw 401 if the ID does not exist.
        return userRepository.findById(effectiveUserId)
                .orElseThrow(() -> PulseException.unauthorized("401: Unknown employee identity: " + effectiveUserId));
    }

    /**
     * Returns the list of `audience_id` strings that the given user is a member of
     * in the `audience_members` table.
     *
     * @param userId Employee user ID (e.g., `"usr_asha"`).
     * @return List of permitted audience IDs.
     */
    @Transactional(readOnly = true)
    public List<String> getPermittedAudiences(String userId) {
        // Delegate to native SQL query on `audience_members`.
        return audienceRepository.findPermittedAudienceIdsByUserId(userId);
    }

    /**
     * Verifies that the authenticated user possesses a specific role in `roles_csv`.
     * Throws HTTP `403 Forbidden` if the user lacks the required role.
     *
     * @param user         Authenticated user entity.
     * @param requiredRole Role code required for the action (e.g., `"REVIEWER"`, `"PUBLISHER"`, `"MODERATOR"`).
     * @throws PulseException HTTP 403 (`FORBIDDEN_ACCESS`) when the role is missing.
     */
    public void requireRole(UserEntity user, String requiredRole) {
        // Check parsed roles list on UserEntity.
        if (!user.hasRole(requiredRole)) {
            throw PulseException.forbidden(
                    "FORBIDDEN_ACCESS",
                    "403: User '" + user.getDisplayName() + "' lacks required scoped role: " + requiredRole
            );
        }
    }

    /**
     * Checks whether the user holds any Publishing Portal governance role
     * (`REVIEWER`, `PUBLISHER`, `MODERATOR`, or `ADMIN`).
     *
     * @param user Authenticated user entity.
     * @return `true` if the user is authorized to access Publishing Portal queues/logs.
     */
    public boolean hasPortalAccess(UserEntity user) {
        // Return true if the user has at least one portal editorial/moderation role.
        return user.hasRole("REVIEWER")
                || user.hasRole("PUBLISHER")
                || user.hasRole("MODERATOR")
                || user.hasRole("ADMIN");
    }

    /**
     * Enforces that the caller has Publishing Portal access (`REVIEWER`, `PUBLISHER`,
     * `MODERATOR`, or `ADMIN`), throwing `403 Forbidden` for regular employees (`EMPLOYEE`, `CREATOR`).
     *
     * @param user Authenticated user entity.
     * @throws PulseException HTTP 403 (`FORBIDDEN_ACCESS`) if the user has no portal role.
     */
    public void requirePortalAccess(UserEntity user) {
        if (!hasPortalAccess(user)) {
            throw PulseException.forbidden(
                    "FORBIDDEN_ACCESS",
                    "403: User '" + user.getDisplayName() + "' lacks Publishing Portal role (REVIEWER, PUBLISHER, MODERATOR, or ADMIN)."
            );
        }
    }

    /**
     * Enforces the core "Filter Before Rank" audience boundary (Proposal Page 4 & 14):
     * verifies that `user` is a member of `audienceId` in `audience_members`.
     *
     * @param user       Authenticated user entity.
     * @param audienceId Target audience ID (e.g., `"aud_leaders"`, `"aud_uk_eng"`).
     * @throws PulseException HTTP 403 (`FORBIDDEN_ACCESS`) if `user` is not in `audienceId`.
     */
    @Transactional(readOnly = true)
    public void requireAudienceAccess(UserEntity user, String audienceId) {
        // Query all audiences permitted for this user.
        List<String> permitted = getPermittedAudiences(user.getId());
        // Reject with 403 Forbidden if the target audience is not in the user's permitted set.
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
     *
     * @param caller Authenticated employee requesting the content item.
     * @param item   The `ContentItemEntity` being accessed.
     * @throws PulseException HTTP 403 if the caller lacks audience access;
     *                        HTTP 404 if the item is not currently `PUBLISHED`.
     */
    @Transactional(readOnly = true)
    public void requirePublishedVisibility(UserEntity caller, ContentItemEntity item) {
        // Resolve the published audience snapshot (falls back to working audienceId only if never published).
        String targetAudience = item.getEffectivePublishedAudienceId();
        // Step 1: Verify audience membership first so unauthorized users always receive 403 Forbidden.
        requireAudienceAccess(caller, targetAudience);

        // Step 2: Verify the item is actively PUBLISHED and has a valid publishedVersion pointer.
        if (!"PUBLISHED".equals(item.getStatus()) || item.getPublishedVersion() == null) {
            throw PulseException.notFound("404: Content item is not published or has been withdrawn: " + item.getId());
        }
    }

    /**
     * Enforces owner or scoped editorial preview access for unpublished revisions.
     * Used when `preview=true` is passed to `GET /api/v1/content/{id}` or `/revisions`.
     *
     * Rules:
     *   - The item's author (`item.getAuthorId().equals(caller.getId())`) can always preview their own work.
     *   - A `REVIEWER`, `PUBLISHER`, or `MODERATOR` can preview the item ONLY if they also have
     *     membership in the item's target audience (`item.getAudienceId()`).
     *   - All other users are rejected with `403 Forbidden`.
     *
     * @param caller Authenticated user requesting preview access.
     * @param item   Target `ContentItemEntity`.
     * @throws PulseException HTTP 403 (`FORBIDDEN_ACCESS`) if neither author nor scoped reviewer/publisher.
     */
    @Transactional(readOnly = true)
    public void requirePreviewVisibility(UserEntity caller, ContentItemEntity item) {
        // Rule 1: The author of the story/update is always allowed to preview their own draft/revisions.
        if (item.getAuthorId().equals(caller.getId())) {
            return;
        }
        // Rule 2: Portal editorial roles (REVIEWER, PUBLISHER, MODERATOR) may preview if scoped to the item's audience.
        if (caller.hasRole("REVIEWER") || caller.hasRole("PUBLISHER") || caller.hasRole("MODERATOR")) {
            requireAudienceAccess(caller, item.getAudienceId());
            return;
        }
        // Rule 3: Ordinary employees cannot preview another user's unpublished work.
        throw PulseException.forbidden(
                "FORBIDDEN_ACCESS",
                "403: Only the author or a scoped reviewer/publisher for audience '" + item.getAudienceId() + "' can preview unpublished revisions."
        );
    }

    /**
     * Converts a `UserEntity` and its permitted audiences into a clean JSON map
     * returned by `/api/v1/me`, `/api/v1/personas`, and `PATCH /api/v1/me/preferences`.
     *
     * @param user The `UserEntity` to serialize.
     * @return Immutable map representing the user's profile and permissions.
     */
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
