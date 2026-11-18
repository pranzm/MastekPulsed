/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/public/campfire/engine.test.mjs
 * Journey: Automated Node.js Unit Test Suite (`node --test`) for the Standalone
 *          Campfire Engagement & Recognition Engine (`engine.mjs`).
 *
 * Why it is designed this way:
 *   Verifies all 10 domain invariants of the Campfire Weekly Challenge &
 *   Recognition loop in milliseconds without requiring a browser:
 *   1. Publication (not submission or approval) awards the +20 contribution
 *      points and unlocks `First Spark`.
 *   2. Scoped roles (`Reviewer`, `Publisher`), no self-approval, and exact
 *      revision numbers protect every state transition.
 *   3. Requesting changes increments the revision number on resubmission and
 *      invalidates stale revision approvals.
 *   4. Self-appreciation and duplicate appreciation from the same colleague are
 *      rejected so scores cannot be inflated.
 *   5. Saathi's deterministic `summary()` includes ONLY `PUBLISHED` stories and
 *      immediately drops `WITHDRAWN` stories.
 *   6. Giving appreciations is capped at 3 rewards (+15 pts max) and unlocks
 *      `Connection Maker` when supporting 3 distinct teams.
 *   7. Withdrawing a published story reverses both the author's +20 points and
 *      any giver/receiver appreciation points tied to that story.
 *   8. Opting out (`visibility: hidden`) hides the user from `leaderboard()`
 *      while keeping their private `progress()` points intact.
 *   9. Weekly boundaries snap deterministically to Monday 00:00 UTC.
 *  10. Five distinct colleagues appreciating Asha's published story unlock
 *      `Community Favourite` (+20 contribution + 25 receiving = 45 points).
 * ============================================================================
 */

// Import Node's built-in test runner (`node:test`).
import test from 'node:test';
// Import Node's strict assertion library (`node:assert/strict`).
import assert from 'node:assert/strict';
// Import the pure domain functions under test from `engine.mjs`.
import { initialState, act, progress, summary, leaderboard, weekKey } from './engine.mjs';

/**
 * Helper: Submits Asha's weekly challenge story (Revision 1 -> `IN_REVIEW`).
 * @param {object} s Current state snapshot.
 * @returns {object} Updated state with Asha's story in `IN_REVIEW`.
 */
const submit = (s) =>
  act(s, {
    type: 'submit',
    user: 'asha',
    id: 'a',
    title: 'A story beyond work',
    body: 'Badminton helps me practise teamwork and enjoy learning with other people.',
  });

/**
 * Helper: Approves Revision 1 of Asha's story as Reviewer Priya (`IN_REVIEW` -> `APPROVED`).
 * @param {object} s Current state snapshot.
 * @returns {object} Updated state with Asha's story `APPROVED`.
 */
const approve = (s) => act(s, { type: 'review', user: 'priya', story: 'a', revision: 1, approve: true });

/**
 * Helper: Publishes Revision 1 of Asha's story as Publisher Marcus (`APPROVED` -> `PUBLISHED`).
 * @param {object} s Current state snapshot.
 * @returns {object} Updated state with Asha's story `PUBLISHED`.
 */
const publish = (s) => act(s, { type: 'publish', user: 'marcus', story: 'a', revision: 1 });

// TEST 1: Verify that points are awarded ONLY upon publication, never upon submission or approval.
test('publication, not submission or approval, earns contribution points', () => {
  // Submitting places the story in IN_REVIEW -> 0 points.
  let s = submit(initialState());
  assert.equal(progress(s, 'asha').points, 0);
  // Approving as Priya transitions to APPROVED -> still 0 points.
  s = approve(s);
  assert.equal(progress(s, 'asha').points, 0);
  // Publishing as Marcus transitions to PUBLISHED -> +20 points and 'First Spark' badge.
  s = publish(s);
  assert.equal(progress(s, 'asha').points, 20);
  assert.deepEqual(progress(s, 'asha').badges, ['First Spark']);
});

// TEST 2: Verify role checks, self-approval prevention, and exact revision number enforcement.
test('roles and exact revision protect transitions', () => {
  const s = submit(initialState());
  // Author Asha cannot review/approve her own story.
  assert.throws(() => act(s, { type: 'review', user: 'asha', story: 'a', revision: 1, approve: true }));
  // Publisher Marcus cannot publish an unapproved (IN_REVIEW) story.
  assert.throws(() => publish(s));
  // Reviewer Priya cannot approve a non-existent revision number (revision 2 when current is 1).
  assert.throws(() => act(s, { type: 'review', user: 'priya', story: 'a', revision: 2, approve: true }));
  // Creator Asha cannot publish even after Priya approves Revision 1.
  assert.throws(() => act(approve(s), { type: 'publish', user: 'asha', story: 'a', revision: 1 }));
});

// TEST 3: Verify that requesting changes and resubmitting increments revision to 2 and rejects stale revision 1 calls.
test('request changes increments revision and invalidates old approval', () => {
  let s = submit(initialState());
  // Priya requests changes on Revision 1 with explanatory feedback.
  s = act(s, { type: 'review', user: 'priya', story: 'a', revision: 1, approve: false, note: 'Explain the lesson.' });
  // Asha resubmits her updated story -> creates Revision 2 in IN_REVIEW.
  s = submit(s);
  assert.equal(s.stories.find((x) => x.id === 'a').revision, 2);
  // Attempting to approve stale Revision 1 must throw an error.
  assert.throws(() => approve(s));
});

// TEST 4: Verify that self-appreciation and duplicate appreciation from the same colleague are blocked.
test('self appreciation and duplicate appreciation cannot inflate score', () => {
  let s = publish(approve(submit(initialState())));
  // Asha cannot appreciate her own published story.
  assert.throws(() => act(s, { type: 'appreciate', user: 'asha', story: 'a' }));
  // Colleague Daniel appreciates Asha's story -> Asha moves from 20 to 25 points.
  s = act(s, { type: 'appreciate', user: 'daniel', story: 'a' });
  assert.equal(progress(s, 'asha').points, 25);
  // Daniel cannot appreciate the same story a second time.
  assert.throws(() => act(s, { type: 'appreciate', user: 'daniel', story: 'a' }));
});

// TEST 5: Verify that Saathi's bulletin summary only includes PUBLISHED stories and drops WITHDRAWN ones.
test('summary excludes unpublished and withdrawn content', () => {
  // While IN_REVIEW, Asha's badminton story must not appear in summary().
  let s = submit(initialState());
  assert.ok(!summary(s).includes('Badminton'));
  // Once published, Asha's story appears in summary().
  s = publish(approve(s));
  assert.ok(summary(s).includes('Badminton'));
  // Once withdrawn by Marcus, it disappears from summary() and Asha's points return to 0.
  s = act(s, { type: 'withdraw', user: 'marcus', story: 'a' });
  assert.ok(!summary(s).includes('Badminton'));
  assert.equal(progress(s, 'asha').points, 0);
});

// TEST 6: Verify the 3-story weekly giving cap (+15 pts max) and the 3-team Connection Maker badge.
test('giving cap and cross-team badge', () => {
  let s = initialState();
  // Asha appreciates all 3 seeded stories (Daniel/Delivery, Elena/Platform, Marcus/Operations).
  for (const x of s.stories) s = act(s, { type: 'appreciate', user: 'asha', story: x.id });
  // 3 stories * 5 pts = 15 pts, plus Connection Maker for supporting 3 distinct teams.
  assert.equal(progress(s, 'asha').points, 15);
  assert.ok(progress(s, 'asha').badges.includes('Connection Maker'));
  // Appreciating a 4th story is allowed, but giving points stay capped at 15.
  s.stories.push({ ...s.stories[0], id: 'extra' });
  s = act(s, { type: 'appreciate', user: 'asha', story: 'extra' });
  assert.equal(progress(s, 'asha').points, 15);
});

// TEST 7: Verify that withdrawing a story reverses both contribution and appreciation points.
test('withdrawal reverses connected appreciation rewards', () => {
  let s = publish(approve(submit(initialState())));
  // Daniel appreciates Asha's published story (+5 to Daniel for giving, +5 to Asha for receiving).
  s = act(s, { type: 'appreciate', user: 'daniel', story: 'a' });
  // Marcus withdraws Asha's story -> Asha drops to 0 pts, and Daniel drops back to his 20 seeded pts.
  s = act(s, { type: 'withdraw', user: 'marcus', story: 'a' });
  assert.equal(progress(s, 'asha').points, 0);
  assert.equal(progress(s, 'daniel').points, 20);
});

// TEST 8: Verify that leaderboard opt-out hides public ranking while keeping private progress intact.
test('opt-out hides ranking, keeps private points', () => {
  let s = initialState();
  // Daniel opts out of public leaderboard visibility.
  s = act(s, { type: 'visibility', user: 'daniel', hidden: true });
  // Daniel is omitted from leaderboard(s), but his private progress(s, 'daniel') still shows 20 points.
  assert.ok(!leaderboard(s).some((x) => x.id === 'daniel'));
  assert.equal(progress(s, 'daniel').points, 20);
});

// TEST 9: Verify that weekKey() snaps Sunday 23:59 UTC to the current Monday and Monday 00:00 UTC to the new week.
test('week boundary is Monday UTC', () => {
  assert.equal(weekKey(new Date('2026-11-22T23:59:00Z')), '2026-11-16');
  assert.equal(weekKey(new Date('2026-11-23T00:00:00Z')), '2026-11-23');
});

// TEST 10: Verify that duplicate weekly submissions and appreciations on unapproved stories are rejected.
test('pending and duplicate submissions are rejected', () => {
  let s = submit(initialState());
  // Asha cannot submit a second challenge story while her first is IN_REVIEW.
  assert.throws(() => submit(s));
  // Daniel cannot appreciate Asha's story while it is still IN_REVIEW.
  assert.throws(() => act(s, { type: 'appreciate', user: 'daniel', story: 'a' }));
});

// TEST 11: Verify that 5 distinct colleagues appreciating Asha's story unlock Community Favourite (45 pts).
test('five distinct colleagues unlock Community Favourite', () => {
  let s = publish(approve(submit(initialState())));
  // All 5 other personas appreciate Asha's published story (+5 each = +25 receiving points).
  for (const user of ['daniel', 'priya', 'marcus', 'elena', 'rohan'])
    s = act(s, { type: 'appreciate', user, story: 'a' });
  assert.equal(progress(s, 'asha').received, 5);
  assert.equal(progress(s, 'asha').points, 45);
  assert.ok(progress(s, 'asha').badges.includes('Community Favourite'));
});
