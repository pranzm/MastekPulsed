import test from 'node:test';
import assert from 'node:assert/strict';
import { localDate, weekDates, scheduled, remaining, phase } from './model.mjs';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/public/productivity/model.test.mjs
 * Journey: Automated Unit Verification for Section 14 / Make me Productive
 *          (`MP-01`, `MP-05`, `MP-07`).
 * ============================================================================
 */

test('weekDates returns 7 consecutive days starting on Monday in local time', () => {
  const ref = new Date(2026, 10, 18, 9, 30, 0); // Wednesday 18 Nov 2026
  const days = weekDates(ref);
  assert.equal(days.length, 7);
  assert.equal(days[0].getDay(), 1); // Monday
  assert.equal(days[6].getDay(), 0); // Sunday
  assert.equal(localDate(days[0]), '2026-11-16');
  assert.equal(localDate(days[6]), '2026-11-22');
});

test('scheduled matches daily, weekdays, and weekends accurately', () => {
  const days = weekDates(new Date(2026, 10, 18)); // Mon..Sun
  assert.equal(days.filter((d) => scheduled('daily', d)).length, 7);
  assert.equal(days.filter((d) => scheduled('weekdays', d)).length, 5);
  assert.equal(days.filter((d) => scheduled('weekends', d)).length, 2);
});

test('remaining uses deadline arithmetic without drifting below zero', () => {
  const now = 1_700_000_000_000;
  const deadline = now + 25 * 60 * 1000;
  assert.equal(remaining(deadline, now), 1500);
  assert.equal(remaining(deadline, now + 1499_200), 1);
  assert.equal(remaining(deadline, now + 2000_000), 0);
});

test('phase cycles 4-second box breathing prompts deterministically', () => {
  assert.equal(phase(0), 'Breathe in');
  assert.equal(phase(3.9), 'Breathe in');
  assert.equal(phase(4), 'Hold gently');
  assert.equal(phase(8), 'Breathe out');
  assert.equal(phase(12), 'Hold gently');
  assert.equal(phase(16), 'Breathe in');
});
