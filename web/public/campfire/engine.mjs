/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/public/campfire/engine.mjs
 * Journey: Standalone Browser-Only Campfire POC Engagement & Recognition Engine
 *          (Tested by `engine.test.mjs` and rendered by `app.mjs` inside
 *          `/campfire/index.html`).
 *
 * Why it is designed this way:
 *   1. PURE FUNCTIONAL STATE REDUCER (`act(state, event)`):
 *      - Clones input state via `structuredClone(state)` before applying any
 *        mutation so every transition is deterministic, side-effect-free, and
 *        trivially unit-testable in Node (`node --test`).
 *   2. ENFORCES ALL CAMPFIRE EDITORIAL & RECOGNITION INVARIANTS:
 *      - Submission (`type: 'submit'`) creates Revision 1 in `IN_REVIEW` with
 *        `0` points; only `CHANGES_REQUESTED` stories can be resubmitted (which
 *        increments `revision` and clears `approvedRevision`).
 *      - Human Review (`type: 'review'`) requires `role === 'Reviewer'` and
 *        forbids self-approval (`story.author !== u.id`).
 *      - Publication (`type: 'publish'`) requires `role === 'Publisher'` and an
 *        exact revision match (`story.approvedRevision === story.revision`).
 *      - Points (`progress(s, id)`) are derived dynamically from `PUBLISHED`
 *        stories only—meaning withdrawing a story automatically reverses its
 *        +20 contribution points and any connected appreciation points/badges!
 *   3. HONEST SCOPE BOUNDARY:
 *      - Production authorization lives in the Java 21 + PostgreSQL backend;
 *        this pure module powers the standalone synthetic browser walkthrough.
 * ============================================================================
 */

/**
 * Canonical list of the six fictional Mastek colleagues used in the standalone
 * Campfire browser simulation, spanning 6 distinct teams so `Connection Maker`
 * (3 teams) and `Community Favourite` (5 distinct colleagues) can be demonstrated.
 */
export const users = [
  // Asha Patel: Engineering Creator (default starting persona in the walkthrough).
  { id: 'asha', name: 'Asha Patel', team: 'Engineering', role: 'Creator' },
  // Daniel Wright: Delivery Creator with a pre-seeded published photography story.
  { id: 'daniel', name: 'Daniel Wright', team: 'Delivery', role: 'Creator' },
  // Priya Nair: People team Reviewer who approves or requests changes on stories.
  { id: 'priya', name: 'Priya Nair', team: 'People', role: 'Reviewer' },
  // Marcus Vance: Operations Publisher who releases approved revisions or withdraws stories.
  { id: 'marcus', name: 'Marcus Vance', team: 'Operations', role: 'Publisher' },
  // Elena Rostova: Platform Creator with a pre-seeded published balcony gardening story.
  { id: 'elena', name: 'Elena Rostova', team: 'Platform', role: 'Creator' },
  // Rohan Das: Marketing Creator available as the 5th colleague to unlock Community Favourite.
  { id: 'rohan', name: 'Rohan Das', team: 'Marketing', role: 'Creator' },
];

/**
 * Computes the Monday `YYYY-MM-DD` UTC week key for any given `Date`.
 * @param {Date} [date=new Date()] Date instance to normalize to Monday 00:00:00 UTC.
 * @returns {string} ISO date string (`YYYY-MM-DD`) representing Monday of that UTC week.
 */
export function weekKey(date = new Date()) {
  // Clone the input date so we never mutate the caller's Date object.
  const d = new Date(date);
  // Zero out hours, minutes, seconds, and milliseconds in UTC.
  d.setUTCHours(0, 0, 0, 0);
  // Shift the date backward to Monday (`(getUTCDay() + 6) % 7` maps Monday->0, Sunday->6).
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7));
  // Format as `YYYY-MM-DD` by slicing the first 10 characters of the ISO-8601 string.
  return d.toISOString().slice(0, 10);
}

/**
 * Creates a fresh initial state snapshot for the standalone Campfire demo,
 * pre-populated with three reviewed and published colleague stories across
 * three different teams (`Delivery`, `Platform`, `Operations`).
 * @param {string} [week=weekKey()] The Monday UTC week key to bind to the state.
 * @returns {object} Fresh state object containing `schema`, `week`, `hidden`, `kudos`, and `stories`.
 */
export function initialState(week = weekKey()) {
  return {
    // Schema version number used by `app.mjs` to validate saved `localStorage` state.
    schema: 1,
    // Current Monday UTC week identifier (`YYYY-MM-DD`).
    week,
    // Array of user IDs who have opted out of public leaderboard display.
    hidden: [],
    // Array of appreciation edges `{ user, story }` recorded during the week.
    kudos: [],
    // Seeded stories so Asha can immediately appreciate 3 different teams to earn Connection Maker.
    stories: [
      {
        id: 'seed-daniel',
        author: 'daniel',
        title: 'The photographer behind the delivery lead',
        body: 'Photography taught me to notice what others miss. I enjoy early morning walks and would love to exchange composition tips with colleagues.',
        status: 'PUBLISHED',
        revision: 1,
        approvedRevision: 1,
        reviewer: 'priya',
        note: 'Seeded, reviewed example.',
      },
      {
        id: 'seed-elena',
        author: 'elena',
        title: 'Small gardens, patient progress',
        body: 'I grow herbs on my balcony. A few minutes of care each day taught me that small, consistent effort matters. Ask me about starting with mint.',
        status: 'PUBLISHED',
        revision: 1,
        approvedRevision: 1,
        reviewer: 'priya',
        note: 'Seeded, reviewed example.',
      },
      {
        id: 'seed-marcus',
        author: 'marcus',
        title: 'Finding rhythm beyond work',
        body: 'I am learning percussion. Practising with friends reminds me to listen before jumping in. I would enjoy meeting other beginner musicians.',
        status: 'PUBLISHED',
        revision: 1,
        approvedRevision: 1,
        reviewer: 'priya',
        note: 'Seeded, reviewed example.',
      },
    ],
  };
}

/**
 * Internal helper that looks up a persona object in `users` by its `id`.
 * @param {string} id Persona identifier (e.g., `'asha'`).
 * @returns {object|undefined} Matching persona record or `undefined`.
 */
const person = (id) => users.find((u) => u.id === id);

/**
 * Domain invariant assertion helper. Throws a standard `Error(message)` whenever
 * `condition` is falsy so invalid transitions fail fast and leave state untouched.
 * @param {any} condition Truthy condition required for the transition to proceed.
 * @param {string} message Human-readable error message surfaced in the UI or test assertion.
 */
function requireRule(condition, message) {
  if (!condition) throw Error(message);
}

/**
 * Pure state transition reducer for the Campfire browser engine.
 * Validates role permissions, character length bounds, revision numbers, and
 * self-approval/self-appreciation guardrails before returning a new state clone.
 *
 * @param {object} state Current immutable state snapshot.
 * @param {object} event Action payload (`type`: `'visibility' | 'submit' | 'review' | 'publish' | 'withdraw' | 'appreciate'`).
 * @returns {object} Deep-cloned updated state snapshot.
 */
export function act(state, event) {
  // Deep-clone the current state so failed assertions or caller references never mutate in place.
  const s = structuredClone(state),
    // Resolve the acting persona from `event.user`.
    u = person(event.user);
  // Guardrail: Every action must be executed by a valid demo persona.
  requireRule(u, 'Choose a demo persona.');

  // ACTION 1: Toggle public leaderboard visibility (`hidden` opt-out list).
  if (event.type === 'visibility') {
    // Remove any existing opt-out entry for this user first to avoid duplicates.
    s.hidden = s.hidden.filter((id) => id !== u.id);
    // If the user checked "Hide my name", add their ID to `s.hidden`.
    if (event.hidden) s.hidden.push(u.id);
    return s;
  }

  // ACTION 2: Submit a new Weekly Challenge story (or resubmit after `CHANGES_REQUESTED`).
  if (event.type === 'submit') {
    // Enforce title length between 5 and 100 characters.
    requireRule(
      event.title?.trim().length >= 5 && event.title.length <= 100,
      'Use a title of 5–100 characters.'
    );
    // Enforce story body length between 30 and 1,200 characters.
    requireRule(
      event.body?.trim().length >= 30 && event.body.length <= 1200,
      'Write a story of 30–1,200 characters.'
    );
    // Check if the user already has a non-withdrawn story in the current week.
    const existing = s.stories.find((x) => x.author === u.id && x.status !== 'WITHDRAWN');
    // Guardrail: Only one challenge story per person per week, unless resubmitting `CHANGES_REQUESTED`.
    requireRule(!existing || existing.status === 'CHANGES_REQUESTED', 'One challenge story per person each week.');
    if (existing) {
      // Resubmission path: update title/body, increment revision number, and invalidate prior approval.
      existing.title = event.title.trim();
      existing.body = event.body.trim();
      existing.revision++;
      existing.status = 'IN_REVIEW';
      existing.approvedRevision = null;
      existing.reviewer = null;
    } else {
      // First submission path: push Revision 1 in `IN_REVIEW` status (awards 0 points until published).
      s.stories.push({
        id: event.id,
        author: u.id,
        title: event.title.trim(),
        body: event.body.trim(),
        status: 'IN_REVIEW',
        revision: 1,
        note: '',
      });
    }
    return s;
  }

  // For story-targeted actions (`review`, `publish`, `withdraw`, `appreciate`), look up the target story.
  const story = s.stories.find((x) => x.id === event.story);
  requireRule(story, 'Story not found.');

  // ACTION 3: Human review decision (`approve: true` -> `APPROVED`, `approve: false` -> `CHANGES_REQUESTED`).
  if (event.type === 'review') {
    // Guardrail: Requires `Reviewer` role AND forbids self-approval (`story.author !== u.id`).
    requireRule(u.role === 'Reviewer' && story.author !== u.id, 'Only a different reviewer can review this story.');
    // Guardrail: Must match the exact revision number currently in `IN_REVIEW`.
    requireRule(
      story.status === 'IN_REVIEW' && story.revision === event.revision,
      'This revision is no longer awaiting review.'
    );
    // Guardrail: Requesting changes requires non-empty explanatory reviewer notes.
    requireRule(event.approve || event.note?.trim(), 'Explain the changes needed.');
    // Update status, lock `approvedRevision` to `story.revision` if approved, and record reviewer metadata.
    story.status = event.approve ? 'APPROVED' : 'CHANGES_REQUESTED';
    story.approvedRevision = event.approve ? story.revision : null;
    story.reviewer = u.id;
    story.note = event.note || 'Reviewed for this synthetic demo.';
    return s;
  }

  // ACTION 4: Publisher release (`APPROVED` -> `PUBLISHED`).
  if (event.type === 'publish') {
    // Guardrail: Only the `Publisher` role can release a story to the employee feed.
    requireRule(u.role === 'Publisher', 'Only the publisher can release a story.');
    // Guardrail: The story must be `APPROVED` and its `approvedRevision` must match the exact revision being published.
    requireRule(
      story.status === 'APPROVED' && story.approvedRevision === story.revision && story.revision === event.revision,
      'Publication requires approval of the exact revision.'
    );
    // Transition to `PUBLISHED`, which unlocks +20 contribution points and `First Spark` in `progress()`.
    story.status = 'PUBLISHED';
    return s;
  }

  // ACTION 5: Publisher withdrawal (`PUBLISHED` -> `WITHDRAWN`).
  if (event.type === 'withdraw') {
    // Guardrail: Only the `Publisher` can withdraw a currently `PUBLISHED` story.
    requireRule(
      u.role === 'Publisher' && story.status === 'PUBLISHED',
      'Only the publisher can withdraw a published story.'
    );
    // Transition to `WITHDRAWN`; `progress()` will automatically exclude it and reverse points/badges.
    story.status = 'WITHDRAWN';
    return s;
  }

  // ACTION 6: Colleague appreciation (`kudos` on a `PUBLISHED` story).
  if (event.type === 'appreciate') {
    // Guardrail: Draft, in-review, approved, or withdrawn stories cannot receive appreciation.
    requireRule(story.status === 'PUBLISHED', 'Only published stories can receive appreciation.');
    // Guardrail: Authors cannot appreciate their own stories.
    requireRule(story.author !== u.id, 'Appreciate a colleague’s contribution.');
    // Guardrail: Each colleague can appreciate a given story at most once.
    requireRule(
      !s.kudos.some((k) => k.user === u.id && k.story === story.id),
      'You already appreciated this story.'
    );
    // Record the appreciation edge `{ user, story }`.
    s.kudos.push({ user: u.id, story: story.id });
    return s;
  }

  // Any unrecognized `event.type` throws an explicit error.
  throw Error('Unknown action.');
}

/**
 * Computes weekly recognition points and milestone badges for user `id` from
 * the current state `s`. Because points are computed purely from `PUBLISHED`
 * stories, withdrawing a story automatically recalculates and removes associated
 * contribution and appreciation rewards.
 *
 * Weekly Point Rules (Cap: 60 pts):
 * - `+20` for having at least one `PUBLISHED` story (`First Spark` badge).
 * - `+5` per appreciation given to a `PUBLISHED` story, capped at 3 (`15` pts max).
 *   Appreciating stories across 3 distinct colleague teams unlocks `Connection Maker`.
 * - `+5` per distinct colleague who appreciated your `PUBLISHED` story, capped at 5 (`25` pts max).
 *   Receiving appreciation from 5 distinct colleagues unlocks `Community Favourite`.
 *
 * @param {object} s Current Campfire state snapshot.
 * @param {string} id Persona ID to compute progress for.
 * @returns {object} `{ points, contribution, given, received, teams, badges }`.
 */
export function progress(s, id) {
  // Only currently `PUBLISHED` stories count toward contribution or appreciation rewards.
  const pubs = s.stories.filter((x) => x.status === 'PUBLISHED');
  // Filter appreciations given by `id` where the target story is still `PUBLISHED`.
  const given = s.kudos.filter((k) => k.user === id && pubs.some((p) => p.id === k.story));
  // Filter appreciations received on `PUBLISHED` stories authored by `id`.
  const receivers = s.kudos.filter((k) => pubs.some((p) => p.id === k.story && p.author === id));
  // Count distinct colleague IDs who appreciated `id`'s published stories.
  const distinct = new Set(receivers.map((k) => k.user)).size;
  // Award +20 contribution points if `id` has at least one `PUBLISHED` story this week.
  const contribution = pubs.some((p) => p.author === id) ? 20 : 0;
  // Count distinct organizational teams among the authors of stories `id` appreciated.
  const teams = new Set(given.map((k) => person(pubs.find((p) => p.id === k.story).author).team)).size;
  // Return the capped point totals and unlocked milestone badges.
  return {
    points: contribution + Math.min(3, given.length) * 5 + Math.min(5, distinct) * 5,
    contribution,
    given: given.length,
    received: distinct,
    teams,
    badges: [
      ...(contribution ? ['First Spark'] : []),
      ...(teams >= 3 ? ['Connection Maker'] : []),
      ...(distinct >= 5 ? ['Community Favourite'] : []),
    ],
  };
}

/**
 * Computes the optional Community Champions leaderboard, excluding any user
 * whose ID appears in `s.hidden` (opt-out privacy control), sorted by points
 * descending and name alphabetically on ties.
 * @param {object} s Current Campfire state snapshot.
 * @returns {Array<object>} Ranked list of visible user progress objects.
 */
export function leaderboard(s) {
  return users
    .filter((u) => !s.hidden.includes(u.id))
    .map((u) => ({ ...u, ...progress(s, u.id) }))
    .sort((a, b) => b.points - a.points || a.name.localeCompare(b.name));
}

/**
 * Generates a deterministic plain-text bulletin summary for the Saathi companion
 * using ONLY currently `PUBLISHED` stories (excluding drafts, in-review, or
 * withdrawn stories).
 * @param {object} s Current Campfire state snapshot.
 * @returns {string} Multi-line summary string or `'No published stories yet.'`.
 */
export function summary(s) {
  const stories = s.stories.filter((x) => x.status === 'PUBLISHED');
  return stories.length
    ? stories.map((x) => `${person(x.author).name}: ${x.title}. ${x.body.split('. ')[0]}.`).join('\n')
    : 'No published stories yet.';
}
