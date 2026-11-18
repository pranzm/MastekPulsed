package com.mastek.pulse.productivity;

import com.mastek.pulse.common.IdentityService;
import com.mastek.pulse.common.UserEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ProductivityController.java
 * Module: `productivity/` (Section 14 — Make me Productive, MP-03 / MP-05)
 *
 * 1. WHAT THIS FILE DOES:
 *    Exposes the owner-scoped `/api/v1/me/productivity/*` REST endpoints:
 *      - `GET    /api/v1/me/productivity/habits`
 *      - `POST   /api/v1/me/productivity/habits`
 *      - `PATCH  /api/v1/me/productivity/habits/{id}`
 *      - `DELETE /api/v1/me/productivity/habits/{id}`
 *      - `GET    /api/v1/me/productivity/checkins?from=&to=`
 *      - `PUT    /api/v1/me/productivity/habits/{id}/checkins/{date}`
 *      - `GET    /api/v1/me/productivity/preferences`
 *      - `PUT    /api/v1/me/productivity/preferences`
 *      - `POST   /api/v1/me/productivity/sessions`
 *      - `DELETE /api/v1/me/productivity/data`
 *
 * 2. WHY IT IS DESIGNED THIS WAY:
 *    - Every `GET` response sets `Cache-Control: no-store` so private habit
 *      names and check-ins are never cached by shared proxies or service workers.
 *    - Every endpoint derives `caller` via `identityService.authenticate(header)`
 *      and never accepts a `userId` query/body parameter.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1/me/productivity")
public class ProductivityController {

    private final IdentityService identityService;
    private final ProductivityService productivityService;

    public ProductivityController(IdentityService identityService,
                                  ProductivityService productivityService) {
        this.identityService = identityService;
        this.productivityService = productivityService;
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/habits")
    public ResponseEntity<List<Map<String, Object>>> listHabits(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.listHabits(caller));
    }

    @PostMapping("/habits")
    public ResponseEntity<Map<String, Object>> createHabit(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.createHabit(caller, idempotencyKey, payload));
    }

    @PatchMapping("/habits/{id}")
    public ResponseEntity<Map<String, Object>> updateHabit(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String habitId,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.updateHabit(caller, habitId, payload));
    }

    @DeleteMapping("/habits/{id}")
    public ResponseEntity<Map<String, Object>> deleteHabit(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String habitId) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.deleteHabit(caller, habitId));
    }

    @GetMapping("/checkins")
    public ResponseEntity<List<Map<String, Object>>> listCheckins(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.listCheckins(caller, from, to));
    }

    @PutMapping("/habits/{id}/checkins/{date}")
    public ResponseEntity<Map<String, Object>> upsertCheckin(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String habitId,
            @PathVariable("date") String date,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.upsertCheckin(caller, habitId, date, payload));
    }

    @GetMapping("/preferences")
    public ResponseEntity<Map<String, Object>> getPreferences(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.getPreferences(caller));
    }

    @PutMapping("/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.updatePreferences(caller, payload));
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> recordSession(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.recordSession(caller, payload));
    }

    @DeleteMapping("/data")
    public ResponseEntity<Map<String, Object>> purgeAllData(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return noStore(productivityService.purgeAllMyData(caller, payload));
    }
}
