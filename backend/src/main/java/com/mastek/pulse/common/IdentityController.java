package com.mastek.pulse.common;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: IdentityController.java
 * Journey: Employee Profile, Interests Management & Demo Persona Switcher
 *          (Proposal Pages 3, 6, 10, 12).
 *
 * Why it is designed this way:
 *   Exposes the identity, audience discovery, and opt-in preference endpoints:
 *   - `GET /api/v1/me`: Returns the currently authenticated employee's profile,
 *     scoped roles, opt-in interests, permitted audiences, and host availability.
 *   - `GET /api/v1/personas`: Lists all seeded synthetic personas (`usr_asha`,
 *     `usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`, `usr_rohan`, `usr_sophie`,
 *     `usr_carlos`) so hackathon judges and developers can switch personas in one click
 *     and observe role-based and audience-based access control live.
 *   - `GET /api/v1/audiences`: Returns all audiences for portal editorial roles, or
 *     only the caller's permitted audiences for regular employees.
 *   - `PATCH /api/v1/me/preferences`: Lets employees update their explicit topic
 *     interests, notification cadence (`DAILY_DIGEST`, `WEEKLY_DIGEST`, `IMMEDIATE`),
 *     or pause 15-minute host availability (`hostPaused` - Proposal Page 12).
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1")
public class IdentityController {

    // Central authentication and authorization service.
    private final IdentityService identityService;

    // JPA repository for querying and updating `users` rows.
    private final UserRepository userRepository;

    // JPA repository for querying `audiences` metadata.
    private final AudienceRepository audienceRepository;

    /**
     * Constructor injection for identity controller dependencies.
     *
     * @param identityService    Service handling header authentication and DTO mapping.
     * @param userRepository     Repository for `UserEntity` persistence.
     * @param audienceRepository Repository for `AudienceEntity` lookups.
     */
    public IdentityController(IdentityService identityService,
                              UserRepository userRepository,
                              AudienceRepository audienceRepository) {
        this.identityService = identityService;
        this.userRepository = userRepository;
        this.audienceRepository = audienceRepository;
    }

    /**
     * Returns the profile DTO of the currently authenticated employee.
     *
     * HTTP Status Codes:
     *   - `200 OK`: Profile returned successfully.
     *   - `401 Unauthorized`: Missing/invalid `X-Pulse-User-Id` header.
     *
     * @param userIdHeader Optional `X-Pulse-User-Id` header (defaults to `usr_asha` in demo mode).
     * @return Map containing user profile fields, roles, interests, and permitted audiences.
     */
    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Resolve the UserEntity from the X-Pulse-User-Id header (or throw 401 if invalid).
        UserEntity user = identityService.authenticate(userIdHeader);
        // Convert the JPA entity and its permitted audiences into a clean JSON response map.
        return identityService.toUserProfileDto(user);
    }

    /**
     * Lists all synthetic demo personas seeded in the database so the web and mobile
     * persona switcher dropdown can display their names, roles, regions, and audiences.
     *
     * HTTP Status Codes:
     *   - `200 OK`: List of persona profile DTOs returned.
     *
     * @return List of user profile maps for all seeded personas.
     */
    @GetMapping("/personas")
    public List<Map<String, Object>> listDemoPersonas() {
        // Fetch all users from `users` table and map each one to a profile DTO.
        return userRepository.findAll().stream()
                .map(identityService::toUserProfileDto)
                .toList();
    }

    /**
     * Lists audience cohorts visible to the caller.
     * Editorial roles (`ADMIN`, `REVIEWER`, `PUBLISHER`) see all audiences so they can
     * manage cross-cohort publishing; regular employees see only audiences they belong to.
     *
     * HTTP Status Codes:
     *   - `200 OK`: List of `AudienceEntity` objects returned.
     *   - `401 Unauthorized`: Invalid caller identity.
     *
     * @param userIdHeader Optional `X-Pulse-User-Id` header identifying the caller.
     * @return List of `AudienceEntity` cohorts.
     */
    @GetMapping("/audiences")
    public List<AudienceEntity> listAudiences(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Authenticate the calling user.
        UserEntity user = identityService.authenticate(userIdHeader);
        // If the caller holds a Publishing Portal governance role, return all defined audiences.
        if (user.hasRole("ADMIN") || user.hasRole("REVIEWER") || user.hasRole("PUBLISHER")) {
            return audienceRepository.findAll();
        }
        // Otherwise, look up only the audience IDs this employee is a member of in `audience_members`.
        List<String> permitted = identityService.getPermittedAudiences(user.getId());
        // Fetch and return the AudienceEntity records matching those permitted IDs.
        return audienceRepository.findAllById(permitted);
    }

    /**
     * Partially updates the authenticated employee's opt-in preferences:
     *   - `interests`: List of topic tags used to rank stories after audience filtering.
     *   - `notificationFrequency`: Digest cadence (`DAILY_DIGEST`, `WEEKLY_DIGEST`, `IMMEDIATE`).
     *   - `hostPaused`: Boolean toggle allowing a story host to pause incoming 15-minute
     *     connection requests at any time (Proposal Page 12).
     *
     * `@Transactional` ensures the database update commits atomically.
     *
     * HTTP Status Codes:
     *   - `200 OK`: Preferences updated and refreshed profile DTO returned.
     *   - `401 Unauthorized`: Invalid caller identity.
     *
     * @param userIdHeader Optional `X-Pulse-User-Id` header identifying the caller.
     * @param body         JSON patch map containing any combination of `interests`,
     *                     `notificationFrequency`, or `hostPaused`.
     * @return Updated user profile DTO map.
     */
    @PatchMapping("/me/preferences")
    @Transactional
    public Map<String, Object> updatePreferences(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> body) {
        // Step 1: Authenticate the caller.
        UserEntity user = identityService.authenticate(userIdHeader);

        // Step 2: If `interests` array was provided, sanitize and join non-empty tags into CSV format.
        if (body.containsKey("interests") && body.get("interests") instanceof List<?> list) {
            String csv = list.stream().map(Object::toString).map(String::trim)
                    .filter(s -> !s.isEmpty()).reduce((a, b) -> a + "," + b).orElse("");
            user.setInterestsCsv(csv);
        }

        // Step 3: If `notificationFrequency` string was provided, update the user's digest preference.
        if (body.containsKey("notificationFrequency") && body.get("notificationFrequency") instanceof String freq) {
            user.setNotificationFrequency(freq);
        }

        // Step 4: If `hostPaused` boolean was provided, update the user's 15-minute host availability flag.
        if (body.containsKey("hostPaused") && body.get("hostPaused") instanceof Boolean paused) {
            user.setHostPaused(paused);
        }

        // Step 5: Persist the updated entity to PostgreSQL and return the refreshed profile DTO.
        userRepository.save(user);
        return identityService.toUserProfileDto(user);
    }
}
