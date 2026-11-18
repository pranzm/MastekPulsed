/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/public/productivity/model.mjs
 * Journey: Section 14 / Make me Productive (Proposal Pages 20–23 & MP-01/MP-05).
 *
 * WHY IT IS DESIGNED THIS WAY:
 *   1. Local Date Strings (`localDate`):
 *      Uses the employee browser's local calendar day (`YYYY-MM-DD`) rather
 *      than UTC timestamps so midnight/DST shifts never move a habit check-in
 *      to the wrong calendar day.
 *   2. Monday-First Local Week (`weekDates`):
 *      Anchors at 12:00 noon local time when stepping across the 7 days of
 *      the week so Daylight Saving Time (1-hour spring/fall shifts) can never
 *      skip or duplicate a date.
 *   3. Deadline Arithmetic (`remaining`):
 *      Computes `Math.max(0, Math.ceil((deadline - now) / 1000))` instead of
 *      decrementing a counter inside `setInterval`, preventing timer drift
 *      when browser tabs or mobile apps are backgrounded.
 *   4. 4-4-4-4 Box Breathing Cycle (`phase`):
 *      Cycles deterministically every 4 elapsed seconds through:
 *      `'Breathe in' -> 'Hold gently' -> 'Breathe out' -> 'Hold gently'`.
 * ============================================================================
 */

export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate()
  ).padStart(2, '0')}`;
}

export function weekDates(now = new Date()) {
  const start = new Date(now);
  start.setHours(12, 0, 0, 0);
  start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    return d;
  });
}

export function scheduled(schedule, date) {
  return (
    schedule === 'daily' ||
    (schedule === 'weekdays'
      ? date.getDay() > 0 && date.getDay() < 6
      : date.getDay() === 0 || date.getDay() === 6)
  );
}

export function remaining(deadline, now = Date.now()) {
  return Math.max(0, Math.ceil((deadline - now) / 1000));
}

export function phase(elapsed) {
  return ['Breathe in', 'Hold gently', 'Breathe out', 'Hold gently'][Math.floor(elapsed / 4) % 4];
}
