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
 * Why it is designed this way:
 *   Exposes `/api/v1/me` (current user profile), `/api/v1/personas` (lists the
 *   synthetic personas for easy hackathon demonstration), and `PATCH /api/v1/me/preferences`
 *   so employees can update their explicit interests, notification frequency,
 *   or pause host availability (`let hosts pause availability` - Page 12).
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1")
public class IdentityController {

    private final IdentityService identityService;
    private final UserRepository userRepository;
    private final AudienceRepository audienceRepository;

    public IdentityController(IdentityService identityService,
                              UserRepository userRepository,
                              AudienceRepository audienceRepository) {
        this.identityService = identityService;
        this.userRepository = userRepository;
        this.audienceRepository = audienceRepository;
    }

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        UserEntity user = identityService.authenticate(userIdHeader);
        return identityService.toUserProfileDto(user);
    }

    @GetMapping("/personas")
    public List<Map<String, Object>> listDemoPersonas() {
        return userRepository.findAll().stream()
                .map(identityService::toUserProfileDto)
                .toList();
    }

    @GetMapping("/audiences")
    public List<AudienceEntity> listAudiences(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        UserEntity user = identityService.authenticate(userIdHeader);
        if (user.hasRole("ADMIN") || user.hasRole("REVIEWER") || user.hasRole("PUBLISHER")) {
            return audienceRepository.findAll();
        }
        List<String> permitted = identityService.getPermittedAudiences(user.getId());
        return audienceRepository.findAllById(permitted);
    }

    @PatchMapping("/me/preferences")
    @Transactional
    public Map<String, Object> updatePreferences(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> body) {
        UserEntity user = identityService.authenticate(userIdHeader);
        if (body.containsKey("interests") && body.get("interests") instanceof List<?> list) {
            String csv = list.stream().map(Object::toString).map(String::trim)
                    .filter(s -> !s.isEmpty()).reduce((a, b) -> a + "," + b).orElse("");
            user.setInterestsCsv(csv);
        }
        if (body.containsKey("notificationFrequency") && body.get("notificationFrequency") instanceof String freq) {
            user.setNotificationFrequency(freq);
        }
        if (body.containsKey("hostPaused") && body.get("hostPaused") instanceof Boolean paused) {
            user.setHostPaused(paused);
        }
        userRepository.save(user);
        return identityService.toUserProfileDto(user);
    }
}
