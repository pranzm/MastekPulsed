/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/public/campfire/app.mjs
 * Journey: Standalone Browser UI Controller for the Mastek Campfire POC
 *          (Mounted by `/campfire/index.html` and embedded via `<iframe>` when
 *          `workspaceMode === 'CAMPFIRE'` in `App.tsx`).
 *
 * Why it is designed this way:
 *   1. ZERO-DEPENDENCY ES MODULE CONTROLLER:
 *      - Connects the pure domain reducer in `./engine.mjs` (`initialState`,
 *        `act`, `progress`, `leaderboard`, `summary`) to the DOM in
 *        `/campfire/index.html` without requiring a backend server.
 *   2. STRICT XSS PROTECTION (`escape()`):
 *      - Every dynamic user string (`title`, `body`, `note`, `name`, `team`) is
 *        passed through `escape()` before interpolation into `.innerHTML` so
 *        untrusted input is rendered as safe HTML entities.
 *   3. HONEST SAATHI & VOICE COMPANION DISCLOSURE:
 *      - Implements the deterministic Saathi command parser (`Summarize my
 *        bulletin`, `Open leaderboard`, `Open challenge`) and browser
 *        `window.speechSynthesis` read-aloud, while explicitly disclosing that
 *        cloud speech/moderation services are not connected in this browser POC.
 * ============================================================================
 */

// Import the canonical personas and pure state functions from the Campfire engine module.
import { users, weekKey, initialState, act, progress, leaderboard, summary } from './engine.mjs';

// LocalStorage key used to persist standalone POC state across page refreshes within the same UTC week.
const key = 'campfire_demo_v1';

// Initialize mutable UI controller state: domain `state`, active `user` persona, and active `tab`.
let state = initialState(),
  user = 'asha',
  tab = 'challenge';

// Attempt to hydrate saved state from `localStorage` if it matches schema v1 and the current Monday UTC week.
try {
  const saved = JSON.parse(localStorage.getItem(key));
  if (
    saved?.schema === 1 &&
    saved.week === weekKey() &&
    Array.isArray(saved.stories) &&
    Array.isArray(saved.kudos) &&
    Array.isArray(saved.hidden)
  )
    state = saved;
} catch {
  // Ignore JSON parse or storage access errors (e.g., private browsing mode) and keep `initialState()`.
}

/**
 * Shorthand DOM query helper (`document.querySelector`).
 * @param {string} s CSS selector string.
 * @returns {Element|null} First matching DOM element.
 */
const $ = (s) => document.querySelector(s),
  /**
   * Escapes `&`, `<`, `>`, `"`, and `'` into safe HTML entities to prevent XSS
   * whenever rendering user-entered titles, story bodies, or reviewer notes.
   * @param {any} s Input value to sanitize.
   * @returns {string} HTML-escaped string.
   */
  escape = (s) =>
    String(s ?? '').replace(
      /[&<>"']/g,
      (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
    );

/**
 * Resolves a persona ID (e.g., `'asha'`) to its full display name (`'Asha Patel'`).
 * @param {string} id Persona identifier.
 * @returns {string} Display name or fallback ID.
 */
const name = (id) => users.find((x) => x.id === id)?.name || id;

/**
 * Generates an inline SVG portrait data-URI for each of the 6 standalone Campfire personas.
 */
const avatarColors = {
  asha: { bg: '#002180', skin: '#d99b72', hair: '#1e1b18', shirt: '#0f766e' },
  priya: { bg: '#5b21b6', skin: '#c98860', hair: '#171717', shirt: '#9333ea' },
  marcus: { bg: '#065f46', skin: '#8d5524', hair: '#111827', shirt: '#0f172a' },
  daniel: { bg: '#1e3a8a', skin: '#f1c29b', hair: '#5c3a21', shirt: '#1e293b' },
  elena: { bg: '#92400e', skin: '#f5cfa8', hair: '#78350f', shirt: '#1e3a8a' },
  rohan: { bg: '#0369a1', skin: '#d29166', hair: '#1f2937', shirt: '#0284c7' },
};
function avatar(id) {
  const c = avatarColors[id] || avatarColors.asha;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><circle cx="50" cy="50" r="50" fill="${c.bg}"/><path d="M15 100 C18 76, 34 70, 50 70 C66 70, 82 76, 85 100 Z" fill="${c.shirt}"/><circle cx="50" cy="43" r="21" fill="${c.skin}"/><path d="M29 41 C29 21, 71 21, 71 41 C63 31, 37 31, 29 41 Z" fill="${c.hair}"/><circle cx="42" cy="44" r="2.2" fill="#0f172a"/><circle cx="58" cy="44" r="2.2" fill="#0f172a"/><path d="M43 54 Q50 59 57 54" stroke="#991b1b" stroke-width="2.2" fill="none" stroke-linecap="round"/></svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

/**
 * Updates the live ARIA status banner (`#feedback`) with a user-visible message.
 * @param {string} message Status or error text to display.
 */
function notify(message) {
  $('#feedback').textContent = message;
}

/**
 * Persists the current `state` snapshot to `localStorage` under `campfire_demo_v1`.
 * Gracefully notifies the user if browser storage is disabled or full.
 */
function save() {
  try {
    localStorage.setItem(key, JSON.stringify(state));
  } catch {
    notify('Storage unavailable. This demo works until the page closes.');
  }
}

/**
 * Dispatches an action `event` (enriched with the active `user`) through `act()`,
 * saves the new state, re-renders the active tab, and displays `message`.
 * If `act()` throws a validation/role guardrail error, catches it and notifies the user.
 *
 * @param {object} event Domain event object passed to `act()`.
 * @param {string} message Success feedback message if the transition succeeds.
 */
function change(event, message) {
  try {
    state = act(state, { ...event, user });
    save();
    render();
    notify(message);
  } catch (e) {
    notify(e.message);
  }
}

// Populate the top-bar `<select id="persona">` dropdown with all 6 fictional colleagues and their roles.
$('#persona').innerHTML = users.map((u) => `<option value="${u.id}">${u.name} · ${u.role}</option>`).join('');

// Handle persona switching: update `user`, stop any ongoing speech synthesis, re-render, and announce the persona.
$('#persona').onchange = (e) => {
  user = e.target.value;
  window.speechSynthesis?.cancel();
  render();
  notify(`Now demonstrating as ${name(user)}.`);
};

// Display the authoritative Monday UTC week key in the header badge.
$('#week').textContent = `Week of ${state.week} · UTC`;

// Handle the "Reset demo" button: restore initial seed state, reset persona to Asha, and return to the Challenge tab.
$('#reset').onclick = () => {
  state = initialState();
  save();
  user = 'asha';
  $('#persona').value = user;
  tab = 'challenge';
  window.speechSynthesis?.cancel();
  render();
  notify('Demo reset. Start with Asha’s story.');
};

// Wire click listeners to all navigation tab buttons (`data-tab="challenge|stories|review|leaders|saathi"`).
document.querySelectorAll('[data-tab]').forEach((b) => (b.onclick = () => go(b.dataset.tab)));

/**
 * Switches the active navigation tab, triggers a full re-render, and clears stale status messages.
 * @param {string} next Target tab identifier (`'challenge' | 'stories' | 'review' | 'leaders' | 'saathi'`).
 */
function go(next) {
  tab = next;
  render();
  notify('');
}

/**
 * Renders the currently active tab into `<main id="view">` and binds event handlers
 * for forms and action buttons inside the newly rendered markup.
 */
function render() {
  // Update visual `.active` state and `aria-current="page"` accessibility attribute on nav buttons.
  document.querySelectorAll('[data-tab]').forEach((b) => {
    b.classList.toggle('active', b.dataset.tab === tab);
    b.setAttribute('aria-current', b.dataset.tab === tab ? 'page' : 'false');
  });

  // Look up the active persona (`me`), their computed weekly recognition progress (`p`), and their own active story (`own`).
  const me = users.find((u) => u.id === user),
    p = progress(state, user),
    own = state.stories.find((s) => s.author === user && s.status !== 'WITHDRAWN');

  // TAB 1: WEEKLY CONNECTION CHALLENGE ("Beyond My Job Title" + Personal Progress Sidebar).
  if (tab === 'challenge') {
    $('#view').innerHTML = `<div class="grid"><section class="card warm"><p class="eyebrow">WEEKLY CONNECTION CHALLENGE</p><h2>Beyond My Job Title</h2><p>Share a hobby, a lesson learned or someone who helped you. Give a colleague a reason to say hello.</p><p class="muted">Audience: all six fictional demo colleagues.</p>${
      own && own.status !== 'CHANGES_REQUESTED'
        ? `<p><strong>Your story: ${escape(own.status.replaceAll('_', ' '))}</strong></p><h3>${escape(
            own.title
          )}</h3><p>Revision ${own.revision}. ${
            own.status === 'PUBLISHED'
              ? 'Your contribution is live.'
              : 'Next: switch to ' + (own.status === 'APPROVED' ? 'Marcus to publish.' : 'Priya to review.')
          }</p>`
        : `${
            own ? `<p>Reviewer feedback: ${escape(own.note)}</p>` : ''
          }<form id="story-form" class="form"><label for="title">Story title</label><input id="title" required minlength="5" maxlength="100" value="${escape(
            own?.title || 'What badminton taught me about teamwork'
          )}"><label for="body">Your story</label><textarea id="body" required minlength="30" maxlength="1200">${escape(
            own?.body ||
              'I play badminton outside work. Doubles taught me to communicate early, cover for my partner and celebrate small improvements. I would love to meet colleagues who enjoy a friendly game.'
          )}</textarea><button>Submit for human review</button></form>`
    }</section><aside class="card"><p class="eyebrow">YOUR WEEK AT CAMPFIRE</p><div class="avatar-row"><img class="avatar-img" src="${avatar(user)}" alt="${escape(me.name)}"><div><h2 style="margin:0">${escape(
      me.name
    )}</h2><small>${escape(me.role)} · ${escape(me.team)}</small></div></div><div class="metrics"><div><strong>${p.points}</strong>points</div><div><strong>${
      p.received
    }</strong>appreciations</div></div><div class="badges">${
      p.badges.map((b) => `<span class="badge">${b}</span>`).join('') ||
      '<p class="muted">Publish your first story to unlock First Spark.</p>'
    }</div><h3>Recognition rules</h3><p><b>+20</b> for your first approved, published story.</p><p><b>+5</b> for appreciating a story, up to three rewards per week.</p><p><b>+5</b> per distinct colleague appreciating your story, up to five rewards.</p><small>Maximum 60 points per week. No points for clicks, listening, self-appreciation or private feedback.</small></aside></div>`;

    // Bind form submission handler to dispatch `type: 'submit'` with a new UUID.
    $('#story-form')?.addEventListener('submit', (e) => {
      e.preventDefault();
      change(
        { type: 'submit', id: crypto.randomUUID(), title: $('#title').value, body: $('#body').value },
        'Story submitted. No points yet — switch to Priya for human review.'
      );
    });
  }

  // TAB 2: PUBLISHED STORIES FEED ("Around the campfire" + Colleague Appreciation buttons).
  if (tab === 'stories') {
    const stories = state.stories.filter((s) => s.status === 'PUBLISHED');
    $('#view').innerHTML = `<h2>Around the campfire</h2><p>Approved and published stories from your demo colleagues.</p>${
      stories
        .map(
          (s) =>
            `<article class="card"><div class="avatar-row"><img class="avatar-img" src="${avatar(s.author)}" alt="${escape(name(s.author))}"><p class="eyebrow" style="margin:0">${escape(name(s.author))} · ${escape(
              users.find((u) => u.id === s.author).team
            )}</p></div><h3>${escape(s.title)}</h3><p class="story-body">${escape(
              s.body
            )}</p><div class="actions"><button data-kudos="${escape(s.id)}" ${
              s.author === user || state.kudos.some((k) => k.user === user && k.story === s.id) ? 'disabled' : ''
            }>${
              state.kudos.some((k) => k.user === user && k.story === s.id) ? 'Appreciated ✓' : 'Appreciate'
            }</button><span>${state.kudos.filter((k) => k.story === s.id).length} appreciations</span></div></article>`
        )
        .join('') || '<p>No published stories yet.</p>'
    }`;

    // Bind click handlers for all `Appreciate` buttons (`data-kudos`).
    document
      .querySelectorAll('[data-kudos]')
      .forEach((b) => (b.onclick = () => change({ type: 'appreciate', story: b.dataset.kudos }, 'Appreciation recorded. Points respect the weekly caps.')));
  }

  // TAB 3: HUMAN REVIEW & PUBLICATION QUEUE (Priya reviews `IN_REVIEW`; Marcus publishes `APPROVED` or withdraws `PUBLISHED`).
  if (tab === 'review') {
    const pending = state.stories.filter((s) =>
      me.role === 'Reviewer'
        ? s.status === 'IN_REVIEW'
        : me.role === 'Publisher'
        ? ['APPROVED', 'PUBLISHED'].includes(s.status)
        : false
    );
    $('#view').innerHTML = `<h2>Human review & publication</h2><p>Priya reviews. Marcus publishes. The author cannot approve their own story.</p>${
      pending
        .map(
          (s) =>
            `<article class="card"><p>${escape(name(s.author))} · ${s.status} · Revision ${s.revision}</p><h3>${escape(
              s.title
            )}</h3><p class="story-body">${escape(s.body)}</p>${
              s.status === 'IN_REVIEW'
                ? `<p class="notice">Manual checklist: appropriate content, intended audience, no private details. Cloud moderation is not connected in this demo.</p><label>Review notes <input id="note-${escape(
                    s.id
                  )}" placeholder="Feedback for the author"></label><div class="actions"><button data-action="approve" data-id="${
                    s.id
                  }">Approve revision ${
                    s.revision
                  }</button><button class="secondary" data-action="return" data-id="${
                    s.id
                  }">Request changes</button></div>`
                : `<div class="actions"><button data-action="${
                    s.status === 'APPROVED' ? 'publish' : 'withdraw'
                  }" data-id="${s.id}">${
                    s.status === 'APPROVED' ? 'Publish approved revision' : 'Withdraw story'
                  }</button></div>`
            }</article>`
        )
        .join('') ||
      '<div class="card">No review actions for this persona. Submit as Asha, review as Priya, then publish as Marcus.</div>'
    }`;

    // Bind Reviewer (`approve`/`return`) and Publisher (`publish`/`withdraw`) action buttons.
    document.querySelectorAll('[data-action]').forEach(
      (b) =>
        (b.onclick = () => {
          const s = state.stories.find((x) => x.id === b.dataset.id),
            a = b.dataset.action;
          change(
            {
              type: a === 'approve' || a === 'return' ? 'review' : a,
              story: s.id,
              revision: s.revision,
              approve: a === 'approve',
              note: document.getElementById('note-' + s.id)?.value,
            },
            a === 'publish'
              ? 'Published. Contribution points and badges have updated.'
              : a === 'withdraw'
              ? 'Withdrawn. Related points and badges have been recalculated.'
              : 'Review saved.'
          );
        })
    );
  }

  // TAB 4: COMMUNITY CHAMPIONS LEADERBOARD (Top 5 optional ranking + tie-aware competition rank + opt-out toggle).
  if (tab === 'leaders') {
    const rows = leaderboard(state),
      myRank = rows.findIndex((x) => x.id === user),
      // Standard competition ranking (`1 + count of visible users with strictly higher points`) so ties share a rank.
      rank = (score) => 1 + rows.filter((x) => x.points > score).length;
    $('#view').innerHTML = `<h2>This Week’s Community Champions</h2><p>Celebrate participation. Ranking is optional and is never an appraisal score.</p><div class="card"><label><input type="checkbox" id="hide" ${
      state.hidden.includes(user) ? 'checked' : ''
    }> Hide my name from the leaderboard</label><p>Your points: <b>${p.points}</b> · ${
      myRank < 0 ? 'Your public ranking is hidden.' : 'Rank ' + rank(p.points)
    } · New week, fresh opportunity.</p><table class="board"><thead><tr><th>Rank</th><th>Colleague</th><th>Contribution</th><th>Points</th></tr></thead><tbody>${rows
      .slice(0, 5)
      .map(
        (u) =>
          `<tr class="${u.id === user ? 'self' : ''}"><td>${rank(u.points)}</td><td><div class="avatar-row" style="margin:0"><img class="avatar-img" src="${avatar(u.id)}" alt="${escape(u.name)}"><div><b>${u.name}</b><br><small>${
            u.team
          }</small></div></div></td><td>${u.contribution ? 'Shared a story' : 'Joining in'}<br><small>Encouraged ${
            u.given
          } colleagues</small></td><td><b>${u.points}</b></td></tr>`
      )
      .join(
        ''
      )}</tbody></table><p class="muted">Ties share a rank. This demo shows one weekly period; resetting clears its history.</p></div><div class="card"><h3>Your badges</h3><p>${
      p.badges.join(' · ') || 'Your first badge is waiting.'
    }</p><p>First Spark: publish a story. Connection Maker: appreciate stories from three different teams (${
      p.teams
    }/3). Community Favourite: receive appreciation from five distinct colleagues (${
      p.received
    }/5).</p><small>Switch between the five other personas to demonstrate Community Favourite.</small></div>`;

    // Bind opt-out checkbox (`#hide`) to dispatch `type: 'visibility'`.
    $('#hide').onchange = (e) =>
      change({ type: 'visibility', hidden: e.target.checked }, 'Your leaderboard preference is saved.');
  }

  // TAB 5: MAKE ME PRODUCTIVE (Embedded isolated demo iframe scoped to current persona).
  if (tab === 'productivity') {
    const frame = document.createElement('iframe');
    frame.title = 'Make me Productive';
    frame.src = '../productivity/index.html?mode=demo&persona=' + encodeURIComponent(user);
    frame.style.cssText = 'width:100%;height:1700px;border:0;border-radius:9px';
    $('#view').replaceChildren(frame);
  }

  // TAB 6: SAATHI OPTIONAL COMPANION (Deterministic command runner + Web Speech API synthesis + Honest scope notes).
  if (tab === 'productivity') {
    const frame = document.createElement('iframe');
    frame.title = 'Make me Productive';
    frame.src = '../productivity/index.html?mode=demo&persona=' + encodeURIComponent(user);
    frame.style.cssText = 'width:100%;height:1700px;border:0';
    $('#view').replaceChildren(frame);
  }
  if (tab === 'saathi') {
    $('#view').innerHTML = `<div class="grid"><section class="card"><p class="eyebrow">YOUR OPTIONAL COMPANION</p><h2>Meet Saathi</h2><p>Catch up with published stories and find your next screen.</p><form id="commands" class="command"><label for="command" class="muted">Command</label><input id="command" placeholder="Summarize my bulletin" required><button>Ask Saathi</button></form><div class="actions"><button class="secondary" data-command="Summarize my bulletin">Summarize bulletin</button><button class="secondary" data-command="Open leaderboard">Open leaderboard</button><button class="secondary" data-command="Open challenge">Open challenge</button></div><div id="response" class="response" role="status">Type a command to begin. This POC uses deterministic text, not an AI model.</div><div class="actions"><button id="speak" class="secondary">Read summary aloud</button><button id="stop" class="secondary">Stop audio</button></div><p><small>Read aloud uses your browser’s available speech voice; it may use an online service. It is not the developer’s Piper/Whisper integration.</small></p></section><aside class="card"><h3>Voice service integration</h3><p>The developer reports push-to-talk, local Whisper recognition, Piper output, permitted bulletin selection via Azure Foundry and optional Azure Content Safety assessment.</p><p>That service is absent from the inspected repository. Live microphone capture, voice quality and Azure calls remain unverified here.</p><p><b>Human review stays mandatory.</b> Saathi cannot approve, publish or award points.</p><p>Planned voice phrase: “Hey Saathi.” Text and on-screen controls remain available.</p></aside></div>`;

    /**
     * Parses and executes a deterministic Saathi text command.
     * Supports navigation (`open leaderboard`, `open challenge`) and published-only
     * bulletin summarization (`summarize my bulletin`).
     * @param {string} command Raw command string from input or quick-action button.
     */
    const run = (command) => {
      const c = command.toLowerCase().replace(/[.!?]/g, '').trim();
      if (['open leaderboard', 'show leaderboard'].includes(c)) {
        go('leaders');
        return;
      }
      if (['open challenge', 'show challenge'].includes(c)) {
        go('challenge');
        return;
      }
      if (['summarize my bulletin', 'summarise my bulletin', 'summarize bulletin'].includes(c)) {
        $('#response').textContent = summary(state);
        return;
      }
      $('#response').textContent =
        'Try “Summarize my bulletin”, “Open leaderboard” or “Open challenge”. Approval and publication require a human reviewer/publisher.';
    };

    // Bind command form submit and quick-command chip buttons.
    $('#commands').onsubmit = (e) => {
      e.preventDefault();
      run($('#command').value);
    };
    document.querySelectorAll('[data-command]').forEach((b) => (b.onclick = () => run(b.dataset.command)));

    // Bind browser speech synthesis ("Read summary aloud") with graceful fallback if unavailable.
    $('#speak').onclick = () => {
      if (!('speechSynthesis' in window)) {
        notify('Speech output is unavailable. Read the text summary instead.');
        return;
      }
      window.speechSynthesis.cancel();
      const text = summary(state);
      $('#response').textContent = text;
      const speech = new SpeechSynthesisUtterance(text);
      speech.onerror = () => notify('Speech playback unavailable. The text summary remains available.');
      speech.lang = 'en-IN';
      window.speechSynthesis.speak(speech);
    };
    // Bind "Stop audio" button to cancel active speech synthesis.
    $('#stop').onclick = () => window.speechSynthesis?.cancel();
  }
}

// Perform initial render on module load.
render();
