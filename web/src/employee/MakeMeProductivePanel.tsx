import React, { useEffect, useRef, useState } from 'react';
import {
  AppMode,
  HabitCheckinDto,
  HabitSchedule,
  PersonalHabitDto,
  ProductivityPreferencesDto,
  ProductivitySessionKind,
  UserProfileDto,
} from '../api/contracts';
import { pulseApi } from '../api/apiClient';
import { CheckSquare, Clock, Compass, HeartHandshake, Play, Pause, RotateCcw, Shield, Trash2 } from 'lucide-react';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/employee/MakeMeProductivePanel.tsx
 * Journey: Section 14 / "Make me Productive" (Proposal Pages 20–23 & MP-04/MP-05).
 *
 * 1. WHAT THIS FILE DOES:
 *    Renders the voluntary personal productivity space inside Employee Web:
 *      - 01 / My Habits: Add, edit, delete, and tick/untick daily, weekday,
 *        or weekend habits across the current Monday–Sunday local week.
 *      - 02 / Pomodoro Focus: Customizable Focus (1–120m), Short Break (1–60m),
 *        and Long Break (1–60m) timers using deadline arithmetic.
 *      - 03 / Get Unstuck + Pause & Reset: 5-minute starter, practical NHS
 *        task-starting hints, 4-4-4-4 Box Breathing, and Mindfulness timers.
 *
 * 2. WHY IT IS DESIGNED THIS WAY:
 *    - Works in BOTH `DEMO` and `LIVE (Java 21 + PostgreSQL 16)` modes via
 *      `pulseApi.*Productivity*` (`/api/v1/me/productivity/*`).
 *    - Deadline-Based Timer (`deadlineMsRef`): Computes remaining seconds from
 *      `Math.ceil((deadline - Date.now()) / 1000)` instead of decrementing a
 *      counter inside `setInterval`, so browser tab throttling never causes drift.
 *    - Private by Design: Zero recognition points, zero manager visibility,
 *      and zero AI analysis of habit text.
 * ============================================================================
 */

interface MakeMeProductivePanelProps {
  appMode: AppMode;
  currentUser: UserProfileDto;
}

function toLocalDateStr(date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate()
  ).padStart(2, '0')}`;
}

function getMondayFirstWeekDates(now = new Date()): Date[] {
  const start = new Date(now);
  start.setHours(12, 0, 0, 0);
  start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    return d;
  });
}

function isScheduledOnDay(schedule: HabitSchedule, date: Date): boolean {
  if (schedule === 'daily') return true;
  const day = date.getDay();
  if (schedule === 'weekdays') return day > 0 && day < 6;
  return day === 0 || day === 6;
}

function getBoxBreathingPhase(elapsedSeconds: number): string {
  return ['Breathe in (4s)', 'Hold gently (4s)', 'Breathe out (4s)', 'Hold gently (4s)'][
    Math.floor(elapsedSeconds / 4) % 4
  ];
}

export const MakeMeProductivePanel: React.FC<MakeMeProductivePanelProps> = ({ appMode, currentUser }) => {
  const browserTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  const weekDays = getMondayFirstWeekDates();
  const todayStr = toLocalDateStr();
  const fromStr = toLocalDateStr(weekDays[0]);
  const toStr = toLocalDateStr(weekDays[6]);

  // Habits & Check-ins State
  const [habits, setHabits] = useState<PersonalHabitDto[]>([]);
  const [checkins, setCheckins] = useState<HabitCheckinDto[]>([]);
  const [prefs, setPrefs] = useState<ProductivityPreferencesDto>({
    focusMinutes: 25,
    shortBreakMinutes: 5,
    longBreakMinutes: 15,
    resetMinutes: 2,
    timezone: browserTimezone,
    completedFocusSessions: 0,
    updatedAt: '',
  });
  const [loading, setLoading] = useState<boolean>(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [statusMsg, setStatusMsg] = useState<string | null>(null);

  // Habit Form State
  const [habitName, setHabitName] = useState<string>('');
  const [habitSchedule, setHabitSchedule] = useState<HabitSchedule>('daily');
  const [editingHabit, setEditingHabit] = useState<PersonalHabitDto | null>(null);

  // Deadline-Based Timer State (MP-05)
  const [timerMode, setTimerMode] = useState<ProductivitySessionKind>('focus');
  const [totalSeconds, setTotalSeconds] = useState<number>(25 * 60);
  const [remainingSeconds, setRemainingSeconds] = useState<number>(25 * 60);
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [isCompleted, setIsCompleted] = useState<boolean>(false);
  const deadlineMsRef = useRef<number | null>(null);
  const activeSessionUuidRef = useRef<string>('');

  const loadProductivityData = async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const [hList, cList, pDto] = await Promise.all([
        pulseApi.listProductivityHabits(currentUser.id),
        pulseApi.listProductivityCheckins(currentUser.id, fromStr, toStr),
        pulseApi.getProductivityPreferences(currentUser.id),
      ]);
      setHabits(hList);
      setCheckins(cList);
      setPrefs(pDto);
      if (!isRunning && deadlineMsRef.current === null && remainingSeconds === totalSeconds) {
        const defaultSec = (pDto.focusMinutes || 25) * 60;
        setTotalSeconds(defaultSec);
        setRemainingSeconds(defaultSec);
      }
    } catch (e: any) {
      setErrorMsg(e?.message || 'Could not load productivity data.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    deadlineMsRef.current = null;
    setIsRunning(false);
    setIsCompleted(false);
    loadProductivityData();
  }, [currentUser.id, appMode]);

  // Deadline Ticker + Visibility Reconciliation
  useEffect(() => {
    if (!isRunning) return;

    const tick = () => {
      if (deadlineMsRef.current === null) return;
      const left = Math.max(0, Math.ceil((deadlineMsRef.current - Date.now()) / 1000));
      setRemainingSeconds(left);
      if (left === 0) {
        deadlineMsRef.current = null;
        setIsRunning(false);
        setIsCompleted(true);
        const sessId = activeSessionUuidRef.current || `ses_${Date.now()}`;
        pulseApi
          .recordProductivitySession(currentUser.id, {
            clientSessionId: sessId,
            kind: timerMode,
            plannedSeconds: totalSeconds,
          })
          .then((res) => {
            setPrefs((prev) => ({ ...prev, completedFocusSessions: res.completedFocusSessions }));
          })
          .catch(() => {});
      }
    };

    const interval = setInterval(tick, 250);
    const onVis = () => tick();
    document.addEventListener('visibilitychange', onVis);
    return () => {
      clearInterval(interval);
      document.removeEventListener('visibilitychange', onVis);
    };
  }, [isRunning, timerMode, totalSeconds, currentUser.id]);

  const selectTimerMode = (mode: ProductivitySessionKind, overrideMinutes?: number) => {
    deadlineMsRef.current = null;
    setIsRunning(false);
    setIsCompleted(false);
    setTimerMode(mode);
    const mins =
      overrideMinutes ??
      (mode === 'focus'
        ? prefs.focusMinutes
        : mode === 'short'
        ? prefs.shortBreakMinutes
        : mode === 'long'
        ? prefs.longBreakMinutes
        : prefs.resetMinutes);
    const secs = Math.max(1, mins) * 60;
    setTotalSeconds(secs);
    setRemainingSeconds(secs);
  };

  const handleStartPause = () => {
    if (isRunning) {
      if (deadlineMsRef.current !== null) {
        setRemainingSeconds(Math.max(0, Math.ceil((deadlineMsRef.current - Date.now()) / 1000)));
      }
      deadlineMsRef.current = null;
      setIsRunning(false);
    } else {
      let startFrom = remainingSeconds;
      if (isCompleted || startFrom <= 0) {
        startFrom = totalSeconds;
        setRemainingSeconds(totalSeconds);
        setIsCompleted(false);
      }
      activeSessionUuidRef.current = `ses_${currentUser.id}_${Date.now()}`;
      deadlineMsRef.current = Date.now() + startFrom * 1000;
      setIsRunning(true);
    }
  };

  const handleResetTimer = () => {
    deadlineMsRef.current = null;
    setIsRunning(false);
    setIsCompleted(false);
    setRemainingSeconds(totalSeconds);
  };

  const handleSaveHabit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!habitName.trim()) return;
    setErrorMsg(null);
    try {
      if (editingHabit) {
        await pulseApi.updateProductivityHabit(currentUser.id, editingHabit.id, {
          name: habitName.trim(),
          schedule: habitSchedule,
          expectedVersion: editingHabit.version,
        });
        setStatusMsg(`Updated habit "${habitName.trim()}".`);
        setEditingHabit(null);
      } else {
        await pulseApi.createProductivityHabit(currentUser.id, `hab-${currentUser.id}-${Date.now()}`, {
          name: habitName.trim(),
          schedule: habitSchedule,
          timezone: browserTimezone,
        });
        setStatusMsg(`Added habit "${habitName.trim()}".`);
      }
      setHabitName('');
      setHabitSchedule('daily');
      await loadProductivityData();
    } catch (err: any) {
      setErrorMsg(err?.message || 'Could not save habit.');
    }
  };

  const handleToggleCheckin = async (habit: PersonalHabitDto, localDate: string, nextCompleted: boolean) => {
    setErrorMsg(null);
    try {
      await pulseApi.upsertProductivityCheckin(currentUser.id, habit.id, localDate, nextCompleted);
      await loadProductivityData();
    } catch (err: any) {
      setErrorMsg(err?.message || 'Could not update check-in.');
    }
  };

  const handleDeleteHabit = async (habit: PersonalHabitDto) => {
    setErrorMsg(null);
    try {
      await pulseApi.deleteProductivityHabit(currentUser.id, habit.id);
      if (editingHabit?.id === habit.id) setEditingHabit(null);
      setStatusMsg(`Deleted "${habit.name}".`);
      await loadProductivityData();
    } catch (err: any) {
      setErrorMsg(err?.message || 'Could not delete habit.');
    }
  };

  const handleSaveDurations = async (next: Partial<ProductivityPreferencesDto>) => {
    const merged = { ...prefs, ...next };
    setPrefs(merged);
    try {
      const saved = await pulseApi.updateProductivityPreferences(currentUser.id, {
        focusMinutes: merged.focusMinutes,
        shortBreakMinutes: merged.shortBreakMinutes,
        longBreakMinutes: merged.longBreakMinutes,
        resetMinutes: merged.resetMinutes,
        timezone: browserTimezone,
      });
      setPrefs(saved);
      if (!isRunning) {
        const mins =
          timerMode === 'focus'
            ? saved.focusMinutes
            : timerMode === 'short'
            ? saved.shortBreakMinutes
            : timerMode === 'long'
            ? saved.longBreakMinutes
            : saved.resetMinutes;
        setTotalSeconds(mins * 60);
        setRemainingSeconds(mins * 60);
      }
    } catch (err: any) {
      setErrorMsg(err?.message || 'Invalid duration setting.');
    }
  };

  const handlePurgeAll = async () => {
    try {
      await pulseApi.purgeProductivityData(currentUser.id);
      setStatusMsg('All personal productivity records cleared.');
      await loadProductivityData();
    } catch (err: any) {
      setErrorMsg(err?.message || 'Could not clear data.');
    }
  };

  // Compute weekly check-in progress
  let plannedCount = 0;
  let completedCount = 0;
  for (const h of habits) {
    for (const d of weekDays) {
      const dayStr = toLocalDateStr(d);
      if (isScheduledOnDay(h.schedule, d) && dayStr <= todayStr) {
        plannedCount++;
        if (checkins.some((c) => c.habitId === h.id && c.localDate === dayStr && c.completed)) {
          completedCount++;
        }
      }
    }
  }

  const mm = String(Math.floor(remainingSeconds / 60)).padStart(2, '0');
  const ss = String(remainingSeconds % 60).padStart(2, '0');
  const modeLabel = {
    focus: 'Focus',
    short: 'Short break',
    long: 'Long break',
    breath: 'Box breathing',
    mind: 'Mindfulness',
  }[timerMode];

  const phaseAnnouncement = isCompleted
    ? 'Session complete. Choose your next step.'
    : !isRunning
    ? remainingSeconds < totalSeconds
      ? 'Paused — resume when ready'
      : 'Ready when you are'
    : timerMode === 'breath'
    ? getBoxBreathingPhase(totalSeconds - remainingSeconds)
    : timerMode === 'mind'
    ? 'Notice your breath or nearby sounds. Gently return when your mind wanders.'
    : 'One small step at a time';

  return (
    <div className="space-y-5">
      {/* Header & Privacy Disclosure Banner */}
      <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-3">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <div className="text-xs font-bold uppercase tracking-wider text-emerald-800 flex items-center gap-1.5">
              <Shield className="w-4 h-4" /> 14 / Make me Productive · Personal Space ({appMode.toUpperCase()} Mode)
            </div>
            <h1 className="text-2xl font-bold text-[#002180] mt-1">
              Small habits. Time to focus. Room to reset.
            </h1>
          </div>
          <span className="text-xs font-bold bg-emerald-100 text-emerald-900 px-3 py-1 rounded-full">
            Owner-Only · {appMode === 'live' ? 'Persisted in Java + PostgreSQL' : 'Browser Demo Storage'}
          </span>
        </div>

        <div className="bg-amber-50 border-l-4 border-amber-500 px-4 py-3 text-xs text-amber-950 rounded-r-lg">
          <strong>Private by design:</strong> Personal habits and timer history belong exclusively to{' '}
          <strong>{currentUser.displayName}</strong>. No manager view, no recognition points, no leaderboard, and no
          AI analysis of your habits.
        </div>

        {errorMsg && (
          <div role="alert" className="bg-red-50 border border-red-300 text-red-900 px-4 py-2.5 rounded-lg text-xs font-semibold">
            {errorMsg}
          </div>
        )}
        {statusMsg && (
          <div role="status" className="bg-emerald-50 border border-emerald-300 text-emerald-900 px-4 py-2.5 rounded-lg text-xs font-semibold">
            {statusMsg}
          </div>
        )}
      </div>

      {/* 01 / MY HABITS */}
      <section className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <div className="text-xs font-bold uppercase tracking-wider text-emerald-800 flex items-center gap-1.5">
              <CheckSquare className="w-4 h-4" /> 01 / Build Your Routine
            </div>
            <h2 className="text-xl font-bold text-slate-900 mt-0.5">My habits</h2>
            <p className="text-xs text-slate-600">
              Week of {weekDays[0].toLocaleDateString()} · {browserTimezone} (Missed days carry zero penalty)
            </p>
          </div>
          <span className="text-xs font-bold bg-blue-50 text-[#002180] border border-blue-200 px-3 py-1.5 rounded-full">
            {plannedCount > 0
              ? `${completedCount} / ${plannedCount} due check-ins this week`
              : 'Your routine starts with one habit'}
          </span>
        </div>

        {loading ? (
          <div className="text-xs text-slate-500 py-4">Loading your personal habits...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-200 text-slate-600">
                  <th className="text-left py-2.5 px-2 font-bold min-w-[160px]">Habit</th>
                  {weekDays.map((d) => (
                    <th key={toLocalDateStr(d)} className="text-center py-2.5 px-1.5 font-bold whitespace-nowrap">
                      {d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' })}
                    </th>
                  ))}
                  <th className="text-right py-2.5 px-2 font-bold">Manage</th>
                </tr>
              </thead>
              <tbody>
                {habits.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="text-center py-6 text-slate-500">
                      No habits added yet. Create your first routine below!
                    </td>
                  </tr>
                ) : (
                  habits.map((h) => (
                    <tr key={h.id} className="border-b border-slate-100 hover:bg-slate-50">
                      <td className="py-2.5 px-2 font-semibold text-slate-900">
                        <div>{h.name}</div>
                        <div className="text-[10px] text-slate-500 uppercase">{h.schedule}</div>
                      </td>
                      {weekDays.map((d) => {
                        const dayStr = toLocalDateStr(d);
                        const eligible = isScheduledOnDay(h.schedule, d) && dayStr <= todayStr;
                        const checked = checkins.some(
                          (c) => c.habitId === h.id && c.localDate === dayStr && c.completed
                        );
                        return (
                          <td key={dayStr} className="text-center py-2.5 px-1.5">
                            <input
                              type="checkbox"
                              aria-label={`${h.name}, ${dayStr}`}
                              disabled={!eligible}
                              checked={checked}
                              onChange={(e) => handleToggleCheckin(h, dayStr, e.target.checked)}
                              className="w-4 h-4 accent-[#002180] cursor-pointer disabled:opacity-30"
                            />
                          </td>
                        );
                      })}
                      <td className="text-right py-2.5 px-2 space-x-1.5 whitespace-nowrap">
                        <button
                          type="button"
                          onClick={() => {
                            setEditingHabit(h);
                            setHabitName(h.name);
                            setHabitSchedule(h.schedule);
                          }}
                          className="px-2 py-1 rounded border border-slate-300 text-slate-700 hover:bg-slate-100 font-semibold"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDeleteHabit(h)}
                          className="px-2 py-1 rounded border border-red-200 text-red-700 hover:bg-red-50 font-semibold"
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        <form onSubmit={handleSaveHabit} className="flex flex-wrap gap-2.5 pt-2">
          <input
            type="text"
            required
            maxLength={80}
            value={habitName}
            onChange={(e) => setHabitName(e.target.value)}
            placeholder="A small habit you want to build (e.g., 10-min walk after lunch)..."
            aria-label="Habit name"
            className="flex-1 min-w-[220px] rounded-lg border border-slate-300 px-3.5 py-2 text-sm"
          />
          <select
            value={habitSchedule}
            onChange={(e) => setHabitSchedule(e.target.value as HabitSchedule)}
            aria-label="Habit schedule"
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm bg-white font-medium"
          >
            <option value="daily">Every day</option>
            <option value="weekdays">Weekdays</option>
            <option value="weekends">Weekends</option>
          </select>
          <button
            type="submit"
            className="bg-[#002180] hover:bg-blue-900 text-white px-4 py-2 rounded-lg text-sm font-bold"
          >
            {editingHabit ? 'Save habit' : 'Add habit'}
          </button>
          {editingHabit && (
            <button
              type="button"
              onClick={() => {
                setEditingHabit(null);
                setHabitName('');
                setHabitSchedule('daily');
              }}
              className="bg-slate-100 hover:bg-slate-200 text-slate-800 px-3 py-2 rounded-lg text-sm font-semibold"
            >
              Cancel
            </button>
          )}
        </form>

        <div className="pt-2 border-t border-slate-100 flex justify-between items-center flex-wrap gap-2">
          <span className="text-[11px] text-slate-500">
            Tick or untick elapsed scheduled days. Stored strictly under your account.
          </span>
          <button
            type="button"
            onClick={handlePurgeAll}
            className="text-xs text-red-700 hover:underline flex items-center gap-1 font-semibold"
          >
            <Trash2 className="w-3.5 h-3.5" /> Clear my productivity data
          </button>
        </div>
      </section>

      {/* 02 / POMODORO FOCUS & 03 / GET UNSTUCK */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        <section className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
          <div>
            <div className="text-xs font-bold uppercase tracking-wider text-emerald-800 flex items-center gap-1.5">
              <Clock className="w-4 h-4" /> 02 / One Thing At A Time
            </div>
            <h2 className="text-xl font-bold text-slate-900 mt-0.5">Pomodoro focus</h2>
            <p className="text-xs text-slate-600">
              Choose one task. Focus, then take a break. Sessions never auto-start.
            </p>
          </div>

          <div className="grid grid-cols-3 gap-2.5 text-xs">
            <label className="font-semibold text-slate-700">
              Focus (1–120m)
              <input
                type="number"
                min={1}
                max={120}
                disabled={isRunning}
                value={prefs.focusMinutes}
                onChange={(e) => handleSaveDurations({ focusMinutes: Number(e.target.value) })}
                className="mt-1 w-full rounded-lg border border-slate-300 px-2.5 py-1.5"
              />
            </label>
            <label className="font-semibold text-slate-700">
              Short (1–60m)
              <input
                type="number"
                min={1}
                max={60}
                disabled={isRunning}
                value={prefs.shortBreakMinutes}
                onChange={(e) => handleSaveDurations({ shortBreakMinutes: Number(e.target.value) })}
                className="mt-1 w-full rounded-lg border border-slate-300 px-2.5 py-1.5"
              />
            </label>
            <label className="font-semibold text-slate-700">
              Long (1–60m)
              <input
                type="number"
                min={1}
                max={60}
                disabled={isRunning}
                value={prefs.longBreakMinutes}
                onChange={(e) => handleSaveDurations({ longBreakMinutes: Number(e.target.value) })}
                className="mt-1 w-full rounded-lg border border-slate-300 px-2.5 py-1.5"
              />
            </label>
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => selectTimerMode('focus')}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold ${
                timerMode === 'focus' ? 'bg-[#002180] text-white' : 'bg-slate-100 text-slate-800'
              }`}
            >
              Focus
            </button>
            <button
              type="button"
              onClick={() => selectTimerMode('short')}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold ${
                timerMode === 'short' ? 'bg-[#002180] text-white' : 'bg-slate-100 text-slate-800'
              }`}
            >
              Short break
            </button>
            <button
              type="button"
              onClick={() => selectTimerMode('long')}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold ${
                timerMode === 'long' ? 'bg-[#002180] text-white' : 'bg-slate-100 text-slate-800'
              }`}
            >
              Long break
            </button>
          </div>
        </section>

        <section className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-3">
          <div>
            <div className="text-xs font-bold uppercase tracking-wider text-emerald-800 flex items-center gap-1.5">
              <Compass className="w-4 h-4" /> 03 / Get Unstuck
            </div>
            <h2 className="text-xl font-bold text-slate-900 mt-0.5">A gentler next step</h2>
          </div>
          <ol className="text-xs text-slate-700 space-y-1.5 list-decimal pl-4">
            <li>Make the next action concrete: open the document and write one sentence.</li>
            <li>Try just five minutes, then decide whether to continue.</li>
            <li>Choose a time and place with fewer distractions.</li>
          </ol>
          <button
            type="button"
            onClick={() => selectTimerMode('focus', 5)}
            className="bg-emerald-700 hover:bg-emerald-800 text-white px-4 py-2 rounded-lg text-xs font-bold"
          >
            Try a five-minute start
          </button>
          <div className="text-[11px] text-slate-500">
            <a
              href="https://www.nhs.uk/every-mind-matters/mental-wellbeing-tips/self-help-cbt-techniques/tackling-your-to-do-list/"
              target="_blank"
              rel="noopener noreferrer"
              className="underline text-[#002180]"
            >
              Read the NHS task-starting guidance ↗
            </a>
          </div>
        </section>
      </div>

      {/* PAUSE & RESET + ACTIVE CLOCK */}
      <section className="bg-emerald-50/70 rounded-xl border border-emerald-200 p-6 shadow-sm grid grid-cols-1 md:grid-cols-12 gap-6 items-center">
        <div className="md:col-span-7 space-y-3">
          <div className="text-xs font-bold uppercase tracking-wider text-emerald-800 flex items-center gap-1.5">
            <HeartHandshake className="w-4 h-4" /> Pause &amp; Reset
          </div>
          <h2 className="text-xl font-bold text-slate-900">Find a little space</h2>
          <p className="text-xs text-slate-700 leading-relaxed">
            <strong>Box breathing:</strong> breathe in, hold, breathe out, hold — four seconds each. Stop and breathe
            naturally if it feels uncomfortable.
          </p>
          <p className="text-xs text-slate-700 leading-relaxed">
            <strong>Mindfulness:</strong> notice your breath or the sounds around you. Gently return attention when it
            wanders. Optional, with no promised therapeutic outcome.
          </p>
          <div className="flex flex-wrap items-center gap-3 pt-1">
            <label className="text-xs font-bold text-slate-700 flex items-center gap-2">
              Session minutes (1–30):
              <input
                type="number"
                min={1}
                max={30}
                disabled={isRunning}
                value={prefs.resetMinutes}
                onChange={(e) => handleSaveDurations({ resetMinutes: Number(e.target.value) })}
                className="w-20 rounded-lg border border-slate-300 px-2.5 py-1.5 bg-white"
              />
            </label>
            <button
              type="button"
              onClick={() => selectTimerMode('breath')}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold ${
                timerMode === 'breath' ? 'bg-emerald-800 text-white' : 'bg-white border border-emerald-300 text-emerald-900'
              }`}
            >
              Box breathing
            </button>
            <button
              type="button"
              onClick={() => selectTimerMode('mind')}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold ${
                timerMode === 'mind' ? 'bg-emerald-800 text-white' : 'bg-white border border-emerald-300 text-emerald-900'
              }`}
            >
              Mindfulness
            </button>
          </div>
          <div className="text-[11px] text-slate-600 space-x-2">
            <a
              href="https://health.clevelandclinic.org/box-breathing-benefits"
              target="_blank"
              rel="noopener noreferrer"
              className="underline text-emerald-900"
            >
              Cleveland Clinic Box Breathing ↗
            </a>
            <span>·</span>
            <a
              href="https://www.nhs.uk/mental-health/self-help/tips-and-support/mindfulness/"
              target="_blank"
              rel="noopener noreferrer"
              className="underline text-emerald-900"
            >
              NHS Mindfulness Guidance ↗
            </a>
          </div>
        </div>

        <div className="md:col-span-5 bg-white rounded-xl border border-emerald-200 p-5 text-center space-y-3 shadow-sm">
          <div className="text-xs font-bold uppercase tracking-wider text-emerald-800">{modeLabel}</div>
          <div className="text-5xl font-extrabold font-mono text-[#002180] tracking-tight">
            {mm}:{ss}
          </div>
          <div role="status" aria-live="polite" className="text-xs font-semibold text-slate-700 min-h-[20px]">
            {phaseAnnouncement}
          </div>
          <div className="flex justify-center gap-2.5">
            <button
              type="button"
              onClick={handleStartPause}
              className="bg-[#002180] hover:bg-blue-900 text-white px-5 py-2 rounded-lg text-xs font-bold flex items-center gap-1.5"
            >
              {isRunning ? (
                <>
                  <Pause className="w-3.5 h-3.5" /> Pause
                </>
              ) : (
                <>
                  <Play className="w-3.5 h-3.5" /> {isCompleted ? 'Start again' : remainingSeconds < totalSeconds ? 'Resume' : 'Start'}
                </>
              )}
            </button>
            <button
              type="button"
              onClick={handleResetTimer}
              className="bg-slate-100 hover:bg-slate-200 text-slate-800 px-4 py-2 rounded-lg text-xs font-bold flex items-center gap-1.5"
            >
              <RotateCcw className="w-3.5 h-3.5" /> Reset
            </button>
          </div>
          <div className="text-[11px] text-slate-500">
            {prefs.completedFocusSessions} focus sessions completed
          </div>
        </div>
      </section>
    </div>
  );
};
