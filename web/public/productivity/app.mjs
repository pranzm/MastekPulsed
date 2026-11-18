/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/public/productivity/app.mjs
 * Journey: Section 14 / Make me Productive Standalone Browser Prototype (`MP-01`).
 *
 * WHY IT IS DESIGNED THIS WAY:
 *   - Interactive, persona-scoped synthetic preview (`sessionStorage` key
 *     `campfire_productivity_demo_v1:${person}`).
 *   - In `mode=live`, displays the explicit Privacy Gate and hides habit entry
 *     because header-based demo impersonation (`X-Pulse-User-Id`) cannot guarantee
 *     employee-only privacy until verified Entra/OIDC principal authentication
 *     (`MP-02`) is active.
 * ============================================================================
 */
import { localDate, weekDates, scheduled, remaining, phase } from './model.mjs';

const $ = (id) => document.getElementById(id),
  q = new URLSearchParams(location.search),
  live = q.get('mode') === 'live',
  person = q.get('persona') || 'demo',
  key = `campfire_productivity_demo_v1:${person}`;

let state = { habits: [], durations: { focus: 25, short: 5, long: 15, reset: 2 }, completed: 0 },
  editing = null,
  mode = 'focus',
  seconds = 1500,
  total = 1500,
  deadline = null,
  done = false;

if (live) {
  $('privacy').textContent =
    'Personal tools are planned for verified employee sign-in. Habit entry is unavailable in this live POC because demo identity headers cannot protect private records. Preview with fictional data in Demo Mode.';
  $('tools').hidden = true;
} else {
  $('privacy').textContent =
    'Interactive demo — fictional habits only. Saved separately for this demo persona in this browser tab. This is not secure employee storage or cross-device sync.';
  try {
    const s = JSON.parse(sessionStorage.getItem(key));
    if (s && Array.isArray(s.habits) && s.durations) state = s;
  } catch {}
}

function save() {
  try {
    sessionStorage.setItem(key, JSON.stringify(state));
  } catch {
    $('feedback').textContent = 'Browser storage is unavailable. Changes last only while this page stays open.';
  }
}

function render() {
  const dates = weekDates(),
    today = localDate();
  $('week').textContent = `Week of ${dates[0].toLocaleDateString()} · ${
    Intl.DateTimeFormat().resolvedOptions().timeZone
  }`;
  $('days').replaceChildren();
  const tr = document.createElement('tr');
  for (const label of [
    'Habit',
    ...dates.map((d) => d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' })),
    'Manage',
  ]) {
    const th = document.createElement('th');
    th.textContent = label;
    tr.append(th);
  }
  $('days').append(tr);
  $('habits').replaceChildren();
  let planned = 0,
    checked = 0;

  for (const h of state.habits) {
    const row = document.createElement('tr'),
      name = document.createElement('td');
    name.textContent = h.name;
    row.append(name);
    for (const d of dates) {
      const cell = document.createElement('td'),
        day = localDate(d),
        eligible = scheduled(h.schedule, d) && day <= today;
      if (eligible) {
        planned++;
        if (h.days.includes(day)) checked++;
      }
      const box = document.createElement('input');
      box.type = 'checkbox';
      box.disabled = !eligible;
      box.checked = h.days.includes(day);
      box.setAttribute('aria-label', `${h.name}, ${day}`);
      box.onchange = () => {
        h.days = box.checked ? [...new Set([...h.days, day])] : h.days.filter((x) => x !== day);
        save();
        render();
      };
      cell.append(box);
      row.append(cell);
    }
    const actions = document.createElement('td');
    for (const [label, fn] of [
      [
        'Edit',
        () => {
          editing = h.id;
          $('name').value = h.name;
          $('schedule').value = h.schedule;
          $('save-habit').textContent = 'Save habit';
          $('cancel').hidden = false;
          $('name').focus();
        },
      ],
      [
        'Delete',
        () => {
          if (confirm(`Delete “${h.name}” and its demo history?`)) {
            state.habits = state.habits.filter((x) => x.id !== h.id);
            save();
            render();
          }
        },
      ],
    ]) {
      const b = document.createElement('button');
      b.textContent = label;
      b.className = 'secondary';
      b.onclick = fn;
      actions.append(b);
    }
    row.append(actions);
    $('habits').append(row);
  }
  $('progress').textContent = planned
    ? `${checked} / ${planned} due check-ins this week`
    : 'Your routine starts with one habit';
}

function cancelEdit() {
  editing = null;
  $('habit-form').reset();
  $('save-habit').textContent = 'Add habit';
  $('cancel').hidden = true;
}

$('habit-form').onsubmit = (e) => {
  e.preventDefault();
  const name = $('name').value.trim();
  if (!name) return;
  const h = state.habits.find((x) => x.id === editing);
  if (h) {
    h.name = name;
    h.schedule = $('schedule').value;
  } else {
    state.habits.push({ id: crypto.randomUUID(), name, schedule: $('schedule').value, days: [] });
  }
  save();
  cancelEdit();
  render();
};
$('cancel').onclick = cancelEdit;

for (const id of ['focus', 'short', 'long']) $(id).value = state.durations[id];
$('reset-min').value = state.durations.reset;

function choose(next, override) {
  const input = $(next === 'breath' || next === 'mind' ? 'reset-min' : next);
  if (!input.reportValidity()) return;
  deadline = null;
  mode = next;
  seconds = total = (override || Number(input.value)) * 60;
  done = false;
  state.durations = {
    focus: Number($('focus').value),
    short: Number($('short').value),
    long: Number($('long').value),
    reset: Number($('reset-min').value),
  };
  save();
  draw();
}

function draw() {
  if (deadline !== null) {
    seconds = remaining(deadline);
    if (seconds === 0) {
      deadline = null;
      done = true;
      if (mode === 'focus') {
        state.completed++;
        save();
      }
    }
  }
  $('clock').textContent = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(
    2,
    '0'
  )}`;
  $('timer-label').textContent = {
    focus: 'Focus',
    short: 'Short break',
    long: 'Long break',
    breath: 'Box breathing',
    mind: 'Mindfulness',
  }[mode];
  const message = done
    ? 'Session complete. Choose your next step.'
    : deadline === null
    ? seconds < total
      ? 'Paused — resume when ready'
      : 'Ready when you are'
    : mode === 'breath'
    ? phase(total - seconds)
    : mode === 'mind'
    ? 'Notice. Breathe naturally. Gently return.'
    : 'One small step at a time';
  if ($('phase').textContent !== message) $('phase').textContent = message;
  $('start').textContent = deadline !== null ? 'Pause' : done ? 'Start again' : seconds < total ? 'Resume' : 'Start';
  $('completed').textContent = `${state.completed} focus sessions completed in this demo`;
  for (const id of ['focus', 'short', 'long', 'reset-min']) $(id).disabled = deadline !== null;
}

$('start').onclick = () => {
  if (deadline !== null) {
    seconds = remaining(deadline);
    deadline = null;
  } else {
    if (done) {
      seconds = total;
      done = false;
    }
    deadline = Date.now() + seconds * 1000;
  }
  draw();
};
$('reset-timer').onclick = () => {
  deadline = null;
  seconds = total;
  done = false;
  draw();
};
document.querySelectorAll('[data-mode]').forEach((b) => (b.onclick = () => choose(b.dataset.mode)));
$('five').onclick = () => {
  choose('focus', 5);
  $('start').focus();
};
for (const id of ['focus', 'short', 'long', 'reset-min']) $(id).onchange = () => choose(mode);
$('clear').onclick = () => {
  if (confirm('Clear all habits and settings for this demo persona?')) {
    sessionStorage.removeItem(key);
    location.reload();
  }
};

if (!live) {
  render();
  choose('focus');
  setInterval(draw, 250);
  document.addEventListener('visibilitychange', draw);
}
