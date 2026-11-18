package com.mastek.pulse.productivity;

import com.mastek.pulse.common.PulseClock;
import com.mastek.pulse.common.PulseException;
import com.mastek.pulse.common.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ProductivityService.java
 * Module: `productivity/` (Section 14 — Make me Productive, MP-03 / MP-05)
 *
 * 1. WHAT THIS FILE DOES:
 *    Implements all business rules for the optional, private "Make me Productive"
 *    employee space:
 *      - Habit CRUD (`listHabits`, `createHabit`, `updateHabit`, `deleteHabit`)
 *      - Calendar-day check-ins (`listCheckins`, `upsertCheckin`)
 *      - Timer duration preferences (`getPreferences`, `updatePreferences`)
 *      - Completed focus/reset session recording (`recordSession`, `getCompletedFocusCount`)
 *      - Full owner data purge (`purgeAllMyData`)
 *
 * 2. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Owner-Only Access Without Existence Disclosure (`404 Not Found`):
 *      Every read, update, check-in, and delete scopes by `caller.getId()`.
 *      If User B guesses User A's `habitId`, `findByIdAndOwnerId(habitId, B)`
 *      returns empty and throws `404`—never `403` (which would confirm the ID exists).
 *      Even `ADMIN`, `REVIEWER`, or `PUBLISHER` roles get NO bypass.
 *    - Local Calendar Day + Schedule Validation:
 *      Check-ins validate against `ZoneId.of(habit.getTimezone())` so a tick
 *      never shifts across midnight/DST, rejects future days, and rejects
 *      off-schedule days (e.g., ticking a `weekends` habit on Wednesday).
 *    - Optimistic Locking (`409 STALE_VERSION`):
 *      `PATCH /habits/{id}` requires `expectedVersion` matching `habit.version`.
 *    - Zero Recognition / Audit / AI Leakage:
 *      Notice this service NEVER writes to `recognition_events`, `audit_events`
 *      with habit text, or `ai_usage_ledger`. Habits stay strictly private.
 * ============================================================================
 */
@Service
public class ProductivityService {

    private static final Set<String> VALID_SCHEDULES = Set.of("daily", "weekdays", "weekends");
    private static final Set<String> VALID_SESSION_KINDS = Set.of("focus", "short", "long", "breath", "mind");

    private final PersonalHabitRepository habitRepository;
    private final HabitCheckinRepository checkinRepository;
    private final ProductivityPreferenceRepository preferenceRepository;
    private final FocusSessionRepository sessionRepository;
    private final PulseClock pulseClock;

    public ProductivityService(PersonalHabitRepository habitRepository,
                               HabitCheckinRepository checkinRepository,
                               ProductivityPreferenceRepository preferenceRepository,
                               FocusSessionRepository sessionRepository,
                               PulseClock pulseClock) {
        this.habitRepository = habitRepository;
        this.checkinRepository = checkinRepository;
        this.preferenceRepository = preferenceRepository;
        this.sessionRepository = sessionRepository;
        this.pulseClock = pulseClock;
    }

    /**
     * Lists all habits owned by `caller` (ordered deterministically by creation time).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listHabits(UserEntity caller) {
        return habitRepository.findByOwnerIdOrderByCreatedAtAscIdAsc(caller.getId())
                .stream()
                .map(this::toHabitDto)
                .toList();
    }

    /**
     * Creates a new personal habit for `caller`, honoring optional `Idempotency-Key`
     * so mobile/web retries never create duplicate habits.
     */
    @Transactional
    public Map<String, Object> createHabit(UserEntity caller,
                                           String idempotencyKey,
                                           Map<String, Object> payload) {
        String trimmedKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;
        if (trimmedKey != null) {
            Optional<PersonalHabitEntity> existing = habitRepository
                    .findByOwnerIdAndIdempotencyKey(caller.getId(), trimmedKey);
            if (existing.isPresent()) {
                return toHabitDto(existing.get());
            }
        }

        String name = validateHabitName(payload.get("name"));
        String schedule = validateSchedule(payload.get("schedule"));
        String timezone = validateTimezone(payload.get("timezone"));

        Instant now = pulseClock.now();
        PersonalHabitEntity habit = new PersonalHabitEntity();
        habit.setId("hab_" + UUID.randomUUID().toString().substring(0, 8));
        habit.setOwnerId(caller.getId());
        habit.setName(name);
        habit.setSchedule(schedule);
        habit.setTimezone(timezone);
        habit.setIdempotencyKey(trimmedKey);
        habit.setCreatedAt(now);
        habit.setUpdatedAt(now);

        PersonalHabitEntity saved = habitRepository.save(habit);
        return toHabitDto(saved);
    }

    /**
     * Updates a habit's `name` or `schedule` with optimistic version checking (`expectedVersion`).
     */
    @Transactional
    public Map<String, Object> updateHabit(UserEntity caller,
                                           String habitId,
                                           Map<String, Object> payload) {
        PersonalHabitEntity habit = habitRepository.findByIdAndOwnerId(habitId, caller.getId())
                .orElseThrow(() -> PulseException.notFound("404: Habit not found: " + habitId));

        Object rawVer = payload.get("expectedVersion");
        if (!(rawVer instanceof Number expectedVersionNum)) {
            throw PulseException.unprocessable("422: expectedVersion (number) is required.");
        }
        long expectedVersion = expectedVersionNum.longValue();
        if (!Objects.equals(habit.getVersion(), expectedVersion)) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Habit was modified in another session (expected version "
                            + expectedVersion + ", current version " + habit.getVersion() + ")."
            );
        }

        if (payload.containsKey("name")) {
            habit.setName(validateHabitName(payload.get("name")));
        }
        if (payload.containsKey("schedule")) {
            habit.setSchedule(validateSchedule(payload.get("schedule")));
        }
        if (payload.containsKey("timezone")) {
            habit.setTimezone(validateTimezone(payload.get("timezone")));
        }
        habit.setUpdatedAt(pulseClock.now());

        PersonalHabitEntity saved = habitRepository.saveAndFlush(habit);
        return toHabitDto(saved);
    }

    /**
     * Deletes a habit and its associated check-in history for `caller`.
     */
    @Transactional
    public Map<String, Object> deleteHabit(UserEntity caller, String habitId) {
        PersonalHabitEntity habit = habitRepository.findByIdAndOwnerId(habitId, caller.getId())
                .orElseThrow(() -> PulseException.notFound("404: Habit not found: " + habitId));

        checkinRepository.deleteForHabit(habit.getId(), caller.getId());
        habitRepository.delete(habit);
        return Map.of("deleted", true, "id", habitId);
    }

    /**
     * Returns owner-scoped check-ins between `from` and `to` (inclusive, max 93-day window).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCheckins(UserEntity caller, String fromStr, String toStr) {
        LocalDate from = parseDateOrDefault(fromStr, LocalDate.now(pulseClock.getClock()).minusDays(6));
        LocalDate to = parseDateOrDefault(toStr, LocalDate.now(pulseClock.getClock()));

        if (to.isBefore(from)) {
            throw PulseException.unprocessable("422: 'to' date must be on or after 'from' date.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 93) {
            throw PulseException.unprocessable("422: Check-in query window cannot exceed 93 days.");
        }

        return checkinRepository.findByOwnerIdAndLocalDateBetween(caller.getId(), from, to)
                .stream()
                .map(this::toCheckinDto)
                .toList();
    }

    /**
     * Idempotently records (`completed: true | false`) a habit check-in for a specific
     * calendar day in the habit's local timezone.
     */
    @Transactional
    public Map<String, Object> upsertCheckin(UserEntity caller,
                                             String habitId,
                                             String dateStr,
                                             Map<String, Object> payload) {
        PersonalHabitEntity habit = habitRepository.findByIdAndOwnerId(habitId, caller.getId())
                .orElseThrow(() -> PulseException.notFound("404: Habit not found: " + habitId));

        LocalDate targetDate = parseStrictDate(dateStr);
        if (!(payload.get("completed") instanceof Boolean completed)) {
            throw PulseException.unprocessable("422: completed (boolean) is required.");
        }

        ZoneId zone = safeZoneId(habit.getTimezone());
        LocalDate todayInHabitZone = LocalDate.now(pulseClock.getClock().withZone(zone));
        if (targetDate.isAfter(todayInHabitZone)) {
            throw PulseException.unprocessable("422: Cannot check in for a future date (" + targetDate + ").");
        }
        if (!isScheduledForDate(habit.getSchedule(), targetDate)) {
            throw PulseException.unprocessable(
                    "422: Date " + targetDate + " is not scheduled for habit schedule '" + habit.getSchedule() + "'."
            );
        }

        HabitCheckinEntity checkin = checkinRepository.findByHabitIdAndLocalDate(habit.getId(), targetDate)
                .orElseGet(() -> {
                    HabitCheckinEntity c = new HabitCheckinEntity();
                    c.setId("chk_" + UUID.randomUUID().toString().substring(0, 8));
                    c.setHabitId(habit.getId());
                    c.setOwnerId(caller.getId());
                    c.setLocalDate(targetDate);
                    return c;
                });

        checkin.setCompleted(completed);
        checkin.setUpdatedAt(pulseClock.now());
        HabitCheckinEntity saved = checkinRepository.save(checkin);
        return toCheckinDto(saved);
    }

    /**
     * Returns the caller's Pomodoro & Reset duration preferences (or defaults 25/5/15/2)
     * plus total completed focus sessions.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPreferences(UserEntity caller) {
        ProductivityPreferenceEntity pref = preferenceRepository.findById(caller.getId())
                .orElseGet(() -> defaultPreferences(caller.getId()));
        long completedFocus = sessionRepository.countByOwnerIdAndKind(caller.getId(), "focus");
        return toPreferenceDto(pref, completedFocus);
    }

    /**
     * Updates the caller's Pomodoro & Reset timer duration preferences (validated ranges).
     */
    @Transactional
    public Map<String, Object> updatePreferences(UserEntity caller, Map<String, Object> payload) {
        ProductivityPreferenceEntity pref = preferenceRepository.findById(caller.getId())
                .orElseGet(() -> defaultPreferences(caller.getId()));

        if (payload.containsKey("focusMinutes")) {
            pref.setFocusMinutes(validateIntRange(payload.get("focusMinutes"), 1, 120, "focusMinutes"));
        }
        if (payload.containsKey("shortBreakMinutes")) {
            pref.setShortBreakMinutes(validateIntRange(payload.get("shortBreakMinutes"), 1, 60, "shortBreakMinutes"));
        }
        if (payload.containsKey("longBreakMinutes")) {
            pref.setLongBreakMinutes(validateIntRange(payload.get("longBreakMinutes"), 1, 60, "longBreakMinutes"));
        }
        if (payload.containsKey("resetMinutes")) {
            pref.setResetMinutes(validateIntRange(payload.get("resetMinutes"), 1, 30, "resetMinutes"));
        }
        if (payload.containsKey("timezone")) {
            pref.setTimezone(validateTimezone(payload.get("timezone")));
        }
        pref.setUpdatedAt(pulseClock.now());
        ProductivityPreferenceEntity saved = preferenceRepository.save(pref);
        long completedFocus = sessionRepository.countByOwnerIdAndKind(caller.getId(), "focus");
        return toPreferenceDto(saved, completedFocus);
    }

    /**
     * Records a completed focus, break, breathing, or mindfulness session deduplicated
     * by `clientSessionId` (`UNIQUE(owner_id, client_session_id)`).
     */
    @Transactional
    public Map<String, Object> recordSession(UserEntity caller, Map<String, Object> payload) {
        String clientSessionId = payload.get("clientSessionId") != null
                ? payload.get("clientSessionId").toString().trim()
                : "";
        if (clientSessionId.isBlank() || clientSessionId.length() > 64) {
            throw PulseException.unprocessable("422: clientSessionId (1-64 chars) is required.");
        }

        Optional<FocusSessionEntity> existing = sessionRepository
                .findByOwnerIdAndClientSessionId(caller.getId(), clientSessionId);
        if (existing.isPresent()) {
            long completedFocus = sessionRepository.countByOwnerIdAndKind(caller.getId(), "focus");
            return toSessionDto(existing.get(), completedFocus);
        }

        String kind = payload.get("kind") != null ? payload.get("kind").toString().trim().toLowerCase() : "";
        if (!VALID_SESSION_KINDS.contains(kind)) {
            throw PulseException.unprocessable("422: kind must be one of focus, short, long, breath, mind.");
        }
        int plannedSeconds = validateIntRange(payload.get("plannedSeconds"), 60, 7200, "plannedSeconds");

        FocusSessionEntity session = new FocusSessionEntity();
        session.setId("ses_" + UUID.randomUUID().toString().substring(0, 8));
        session.setOwnerId(caller.getId());
        session.setClientSessionId(clientSessionId);
        session.setKind(kind);
        session.setPlannedSeconds(plannedSeconds);
        session.setCompletedAt(pulseClock.now());

        FocusSessionEntity saved = sessionRepository.save(session);
        long completedFocus = sessionRepository.countByOwnerIdAndKind(caller.getId(), "focus");
        return toSessionDto(saved, completedFocus);
    }

    /**
     * Purges all productivity records belonging to `caller` (habits, check-ins,
     * preferences, and focus sessions). Requires `{ "confirm": true }`.
     */
    @Transactional
    public Map<String, Object> purgeAllMyData(UserEntity caller, Map<String, Object> payload) {
        if (!Boolean.TRUE.equals(payload.get("confirm"))) {
            throw PulseException.unprocessable("422: Explicit confirmation { \"confirm\": true } is required.");
        }
        String ownerId = caller.getId();
        int checkinsDeleted = checkinRepository.deleteAllForOwner(ownerId);
        int habitsDeleted = habitRepository.deleteAllForOwner(ownerId);
        int sessionsDeleted = sessionRepository.deleteAllForOwner(ownerId);
        preferenceRepository.deleteById(ownerId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("purged", true);
        result.put("habitsDeleted", habitsDeleted);
        result.put("checkinsDeleted", checkinsDeleted);
        result.put("sessionsDeleted", sessionsDeleted);
        return result;
    }

    // =========================================================================
    // Validation & DTO Helpers
    // =========================================================================

    private String validateHabitName(Object raw) {
        String name = raw != null ? raw.toString().trim() : "";
        if (name.isEmpty() || name.length() > 80) {
            throw PulseException.unprocessable("422: Habit name must be between 1 and 80 characters.");
        }
        return name;
    }

    private String validateSchedule(Object raw) {
        String schedule = raw != null ? raw.toString().trim().toLowerCase() : "";
        if (!VALID_SCHEDULES.contains(schedule)) {
            throw PulseException.unprocessable("422: Habit schedule must be daily, weekdays, or weekends.");
        }
        return schedule;
    }

    private String validateTimezone(Object raw) {
        String tz = raw != null ? raw.toString().trim() : "UTC";
        if (tz.isEmpty()) return "UTC";
        try {
            ZoneId.of(tz);
            return tz;
        } catch (Exception e) {
            throw PulseException.unprocessable("422: Invalid IANA timezone: " + tz);
        }
    }

    private int validateIntRange(Object raw, int min, int max, String field) {
        if (!(raw instanceof Number num)) {
            throw PulseException.unprocessable("422: " + field + " must be a number between " + min + " and " + max + ".");
        }
        int val = num.intValue();
        if (val < min || val > max) {
            throw PulseException.unprocessable("422: " + field + " must be between " + min + " and " + max + ".");
        }
        return val;
    }

    private LocalDate parseStrictDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw PulseException.unprocessable("422: Invalid ISO local date (expected YYYY-MM-DD): " + dateStr);
        }
    }

    private LocalDate parseDateOrDefault(String dateStr, LocalDate fallback) {
        if (dateStr == null || dateStr.isBlank()) {
            return fallback;
        }
        return parseStrictDate(dateStr.trim());
    }

    private ZoneId safeZoneId(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    private boolean isScheduledForDate(String schedule, LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return switch (schedule) {
            case "daily" -> true;
            case "weekdays" -> dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            case "weekends" -> dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            default -> false;
        };
    }

    private ProductivityPreferenceEntity defaultPreferences(String ownerId) {
        ProductivityPreferenceEntity p = new ProductivityPreferenceEntity();
        p.setOwnerId(ownerId);
        p.setFocusMinutes(25);
        p.setShortBreakMinutes(5);
        p.setLongBreakMinutes(15);
        p.setResetMinutes(2);
        p.setTimezone("UTC");
        p.setUpdatedAt(pulseClock.now());
        return p;
    }

    private Map<String, Object> toHabitDto(PersonalHabitEntity h) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", h.getId());
        dto.put("name", h.getName());
        dto.put("schedule", h.getSchedule());
        dto.put("timezone", h.getTimezone());
        dto.put("version", h.getVersion());
        dto.put("createdAt", h.getCreatedAt().toString());
        dto.put("updatedAt", h.getUpdatedAt().toString());
        return dto;
    }

    private Map<String, Object> toCheckinDto(HabitCheckinEntity c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", c.getId());
        dto.put("habitId", c.getHabitId());
        dto.put("localDate", c.getLocalDate().toString());
        dto.put("completed", c.isCompleted());
        dto.put("updatedAt", c.getUpdatedAt().toString());
        return dto;
    }

    private Map<String, Object> toPreferenceDto(ProductivityPreferenceEntity p, long completedFocusSessions) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("focusMinutes", p.getFocusMinutes());
        dto.put("shortBreakMinutes", p.getShortBreakMinutes());
        dto.put("longBreakMinutes", p.getLongBreakMinutes());
        dto.put("resetMinutes", p.getResetMinutes());
        dto.put("timezone", p.getTimezone());
        dto.put("completedFocusSessions", completedFocusSessions);
        dto.put("updatedAt", p.getUpdatedAt().toString());
        return dto;
    }

    private Map<String, Object> toSessionDto(FocusSessionEntity s, long completedFocusSessions) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", s.getId());
        dto.put("clientSessionId", s.getClientSessionId());
        dto.put("kind", s.getKind());
        dto.put("plannedSeconds", s.getPlannedSeconds());
        dto.put("completedAt", s.getCompletedAt().toString());
        dto.put("completedFocusSessions", completedFocusSessions);
        return dto;
    }
}
