// @refresh reset
import React, { useEffect, useRef, useState } from 'react';
import {
  AiBudgetStatusDto,
  AppMode,
  AudioListenItemDto,
  AuditEventDto,
  ChallengeDto,
  ConnectionRequestDto,
  ContentCardDto,
  LeaderboardResponseDto,
  PilotMetricsDto,
  PortalQueueItemDto,
  PulseHttpError,
  RecognitionProgressDto,
  UserProfileDto,
} from './api/contracts';
import { getAppMode, pulseApi, resetDemoState, setAppModeOverride } from './api/apiClient';
import { DEMO_PASSWORD, DEMO_PERSONAS, getPersonaAvatar, getPersonaMeta } from './api/personas';
import { EmployeeWorkspace } from './employee/EmployeeWorkspace';
import { PublishingPortalWorkspace } from './portal/PublishingPortalWorkspace';
import { Play, Pause, RotateCcw, Headphones, AlertTriangle, Database, Lock, Eye, EyeOff, LogOut, CheckCircle2 } from 'lucide-react';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/App.tsx
 * Journey: Unified Top-Level Shell for Mastek Campfire (Standalone Browser Demo,
 *          Employee Web & Role-Based Publishing Portal + Persistent Audio Bar).
 *
 * Why it is designed this way (Implements Plan Step D1, D4, D5 & CF-003, CF-011):
 *   1. NO BACKGROUND FETCH ON STANDALONE DEMO (`workspaceMode === 'CAMPFIRE'`):
 *      - When the user is viewing the pure browser simulation iframe
 *        (`/campfire/index.html`), `App.tsx` intentionally short-circuits
 *        `loadAllData()` and `useEffect` so zero `/api/v1` network requests
 *        fire in the background.
 *   2. EXPLICIT DEMO VS. LIVE TRANSPORT INDICATOR (`appMode`):
 *      - Displays whether the app is running in `'demo'` (in-browser simulation)
 *        or `'live'` (authoritative Java 21 + PostgreSQL 16 API).
 *      - Live API errors surface with HTTP `status`, domain `code`, `message`,
 *        and a Retry button—never an indefinite loading spinner or a silent
 *        fallback to local demo state!
 *   3. ROLE-AWARE LOADING & PERSONA CACHE CLEARING:
 *      - `clearPersonaScopedState()` wipes `feed`, `requests`, `portalQueue`,
 *        `challenge`, `myRecognition`, and `leaderboard` immediately on persona
 *        or transport mode switch so restricted audience content (such as
 *        `aud_uk_restricted`) never flashes across personas.
 *      - `loadAllData()` fetches `/me` first, loads Employee & Campfire
 *        recognition data for all authenticated users, and calls `/portal/*`
 *        ONLY when `hasPortalRole(me)` is true (`REVIEWER`, `PUBLISHER`,
 *        `MODERATOR`, `ADMIN`).
 * ============================================================================
 */

/**
 * Top-level Mastek Campfire application shell component.
 * Coordinates persona switching, transport mode toggling (`demo` vs `live`),
 * workspace routing (`CAMPFIRE` | `EMPLOYEE` | `PORTAL`), role-aware data
 * loading, and the persistent bottom audio player bar.
 */
export const App: React.FC = () => {
  // Tracks whether the presenter/user has completed the pre-filled Demo Sign-In screen.
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  // Pre-stored demo password in the Login screen (`Campfire@2026!`).
  const [loginPassword, setLoginPassword] = useState<string>(DEMO_PASSWORD);
  // Toggles password visibility on the Login screen.
  const [showPassword, setShowPassword] = useState<boolean>(false);
  // Currently selected canonical server persona ID (defaults to Asha Patel in India Engineering).
  const [activePersonaId, setActivePersonaId] = useState<string>('usr_asha');
  // Active top-level view: standalone browser POC ('CAMPFIRE'), Employee Web ('EMPLOYEE'), or Publishing Portal ('PORTAL').
  const [workspaceMode, setWorkspaceMode] = useState<'EMPLOYEE' | 'PORTAL' | 'CAMPFIRE'>('EMPLOYEE');
  // Explicit transport mode ('demo' or 'live') initialized from `sessionStorage` override or `VITE_APP_MODE`.
  const [appMode, setAppMode] = useState<AppMode>(() => getAppMode());

  // Tracks whether `loadAllData` is currently awaiting API responses.
  const [isLoading, setIsLoading] = useState<boolean>(false);
  // Animated splash screen transition flag shown when igniting the Campfire workspace.
  const [isSplashBooting, setIsSplashBooting] = useState<boolean>(false);
  // Controls the 5-step Guided Walkthrough modal for new users.
  const [showWalkthrough, setShowWalkthrough] = useState<boolean>(false);
  const [walkthroughStep, setWalkthroughStep] = useState<number>(0);
  // External tab jump requested from the Guided Walkthrough modal.
  const [tourTargetTab, setTourTargetTab] = useState<
    'PULSE' | 'CHAMPIONS' | 'PRODUCTIVE' | 'LISTEN' | 'CONNECT' | 'CONTRIBUTE' | 'PROFILE' | null
  >(null);
  // Holds structured HTTP/network error details when a `live` or `demo` request fails.
  const [loadError, setLoadError] = useState<{ status: number; code: string; message: string } | null>(null);

  // List of available pilot personas returned by `/api/v1/personas` for the header switcher.
  const [personas, setPersonas] = useState<UserProfileDto[]>([]);
  // Authenticated profile for `activePersonaId` returned by `/api/v1/me`.
  const [currentUser, setCurrentUser] = useState<UserProfileDto | null>(null);
  // Audience-filtered and interest-ranked feed cards returned by `/api/v1/feed`.
  const [feed, setFeed] = useState<ContentCardDto[]>([]);
  // Curated commute radio queues returned by `/api/v1/listen/radio`.
  const [radioQueues, setRadioQueues] = useState<AudioListenItemDto[]>([]);
  // On-demand Culture Cast podcast episodes returned by `/api/v1/listen/culture-cast`.
  const [cultureCasts, setCultureCasts] = useState<AudioListenItemDto[]>([]);
  // 15-minute colleague connection requests (sent, received, accepted) returned by `/api/v1/requests`.
  const [requests, setRequests] = useState<ConnectionRequestDto[]>([]);
  // Active Weekly Campfire Challenge prompt and the user's own entry (`/api/v1/challenges/current`).
  const [challenge, setChallenge] = useState<ChallengeDto | null>(null);
  // Current user's weekly recognition points, caps, and badges (`/api/v1/recognition/me`).
  const [myRecognition, setMyRecognition] = useState<RecognitionProgressDto | null>(null);
  // Optional Top 5 Community Champions leaderboard (`/api/v1/recognition/leaderboard`).
  const [leaderboard, setLeaderboard] = useState<LeaderboardResponseDto | null>(null);

  // Editorial review/publish queue items (`/api/v1/portal/queue`) — fetched ONLY for portal roles.
  const [portalQueue, setPortalQueue] = useState<PortalQueueItemDto[]>([]);
  // Immutable editorial & security audit log events (`/api/v1/portal/audit-log`).
  const [auditLog, setAuditLog] = useState<AuditEventDto[]>([]);
  // Four-week pilot threshold gate metrics (`/api/v1/pilot/metrics`).
  const [pilotMetrics, setPilotMetrics] = useState<PilotMetricsDto | null>(null);
  // Four-week pilot AI action budget and hard-cap status (`/api/v1/ai/budget`).
  const [aiBudget, setAiBudget] = useState<AiBudgetStatusDto | null>(null);

  // Persistent Bottom Audio Player Bar State (Matches Proposal Page 10)
  // Currently loaded audio episode/queue in the persistent bottom player bar.
  const [activeAudio, setActiveAudio] = useState<AudioListenItemDto | null>(null);
  // True when the `<audio>` element and playback progress timer are actively playing.
  const [isPlayingAudio, setIsPlayingAudio] = useState<boolean>(false);
  // Current playback position in seconds (defaults to 130s / 02:10 to match Wireframe 08).
  const [audioPositionSec, setAudioPositionSec] = useState<number>(130);
  // Controls visibility of the verified transcript modal opened from the bottom audio bar.
  const [showBottomTranscriptModal, setShowBottomTranscriptModal] = useState<boolean>(false);
  // Reference to the hidden HTML5 `<audio>` element used for real WAV/MP3 playback.
  const audioRef = useRef<HTMLAudioElement | null>(null);

  /**
   * Checks whether the given user profile holds at least one scoped Publishing Portal role
   * (`REVIEWER`, `PUBLISHER`, `MODERATOR`, or `ADMIN`).
   * Ordinary `EMPLOYEE` / `CREATOR` personas return `false` so we never call `/portal/*`
   * during Employee workspace initialization.
   *
   * @param user Authenticated user profile or `null`.
   * @returns `true` if the user is authorized to load Publishing Portal data.
   */
  const hasPortalRole = (user: UserProfileDto | null): boolean => {
    if (!user) return false;
    return (
      user.roles.includes('REVIEWER') ||
      user.roles.includes('PUBLISHER') ||
      user.roles.includes('MODERATOR') ||
      user.roles.includes('ADMIN')
    );
  };

  /**
   * Immediately clears all persona-scoped data arrays and objects prior to loading
   * a new persona or transport mode. This prevents restricted audience stories
   * (e.g., `aud_uk_restricted`) or another colleague's private requests/points from
   * remaining visible while new requests are in flight.
   */
  const clearPersonaScopedState = () => {
    setCurrentUser(null);
    setFeed([]);
    setRequests([]);
    setChallenge(null);
    setMyRecognition(null);
    setLeaderboard(null);
    setPortalQueue([]);
    setAuditLog([]);
    setPilotMetrics(null);
    setAiBudget(null);
  };

  /**
   * Loads workspace data for `personaId` in three role-aware stages:
   * - Stage 1: Authenticate `/me` and fetch `/personas`.
   * - Stage 2: Fetch Employee & Campfire engagement endpoints (`/feed`, `/listen/*`,
   *   `/requests`, `/challenges/current`, `/recognition/me`, `/recognition/leaderboard`).
   * - Stage 3: Conditionally fetch `/portal/queue`, `/portal/audit-log`, `/pilot/metrics`,
   *   and `/ai/budget` ONLY if `hasPortalRole(me)` is true.
   *
   * @param personaId Canonical user ID to impersonate via `X-Pulse-User-Id`.
   * @param targetWorkspace Active workspace mode (`'CAMPFIRE'` short-circuits immediately).
   */
  const loadAllData = async (personaId = activePersonaId, targetWorkspace = workspaceMode) => {
    // Guardrail: Never fire `/api/v1` requests while viewing the standalone browser POC iframe.
    if (targetWorkspace === 'CAMPFIRE') {
      return;
    }
    setIsLoading(true);
    setLoadError(null);
    try {
      // STEP 1 (D1): Authenticate & fetch current user FIRST
      const [me, allPersonas] = await Promise.all([
        pulseApi.getCurrentUser(personaId),
        pulseApi.getPersonas(personaId),
      ]);
      setCurrentUser(me);
      setPersonas(allPersonas);

      // STEP 2 (D1): Load Employee & Campfire Engagement data (never requires portal endpoints!)
      const [feedList, radios, casts, reqs, activeChallenge, myRec, lb] = await Promise.all([
        pulseApi.getFeed(personaId),
        pulseApi.getRadioQueues(personaId),
        pulseApi.getCultureCasts(personaId),
        pulseApi.listRequests(personaId),
        pulseApi.getCurrentChallenge(personaId),
        pulseApi.getMyRecognitionProgress(personaId),
        pulseApi.getLeaderboard(personaId, 'aud_pilot'),
      ]);

      setFeed(feedList);
      setRadioQueues(radios);
      setCultureCasts(casts);
      setRequests(reqs);
      setChallenge(activeChallenge);
      setMyRecognition(myRec);
      setLeaderboard(lb);

      // Pre-populate the persistent bottom audio bar with Culture Cast Episode 03 (paused) if empty.
      if (!activeAudio && casts[0]) {
        setActiveAudio(casts[0]);
        setAudioPositionSec(casts[0].resumePositionSeconds || 130);
      }

      // STEP 3 (D1): Fetch editorial portal endpoints ONLY if user has a portal role!
      if (hasPortalRole(me)) {
        const [pQueue, audits, metricsAndBudget] = await Promise.all([
          pulseApi.getPortalQueue(personaId),
          pulseApi.getAuditLog(personaId),
          pulseApi.getPilotMetricsAndBudget(personaId),
        ]);
        setPortalQueue(pQueue);
        setAuditLog(audits);
        setPilotMetrics(metricsAndBudget.metrics);
        setAiBudget(metricsAndBudget.budget);
      } else {
        // Ensure portal state remains empty for non-portal personas.
        setPortalQueue([]);
        setAuditLog([]);
        setPilotMetrics(null);
        setAiBudget(null);
      }
    } catch (err: any) {
      // Surface typed `PulseHttpError` (`status`, `code`, `message`) directly in the UI alert banner.
      if (err instanceof PulseHttpError) {
        setLoadError({ status: err.status, code: err.code, message: err.message });
      } else {
        setLoadError({
          status: 500,
          code: 'UNEXPECTED_ERROR',
          message: err?.message || 'Failed to load workspace data.',
        });
      }
    } finally {
      // Always clear the loading flag so the UI never hangs on an infinite spinner.
      setIsLoading(false);
    }
  };

  /**
   * Effect: Whenever `activePersonaId`, `workspaceMode`, or `appMode` changes,
   * clear any stale persona state, pause active audio playback, and reload data
   * from the selected transport (`demo` or `live`).
   */
  useEffect(() => {
    if (!isAuthenticated || workspaceMode === 'CAMPFIRE') {
      return;
    }
    clearPersonaScopedState();
    if (isPlayingAudio) {
      setIsPlayingAudio(false);
      audioRef.current?.pause();
    }
    loadAllData(activePersonaId, workspaceMode);
  }, [isAuthenticated, activePersonaId, workspaceMode, appMode]);

  /**
   * Effect: Advances `audioPositionSec` by 1 second every 1,000ms while audio is
   * playing, and persists cross-device resume progress (`savePlaybackProgress`)
   * every 5 seconds.
   */
  useEffect(() => {
    if (!isPlayingAudio || !activeAudio || workspaceMode === 'CAMPFIRE') return;
    const interval = setInterval(() => {
      setAudioPositionSec((prev) => {
        const next = prev + 1;
        // Persist playback position every 5 seconds for cross-device resume.
        if (next % 5 === 0) {
          pulseApi.savePlaybackProgress(activePersonaId, activeAudio.id, next, activeAudio.durationSeconds).catch(() => {});
        }
        // Loop back to 0 when the track reaches its total duration.
        return next >= activeAudio.durationSeconds ? 0 : next;
      });
    }, 1000);
    // Clean up the interval timer when paused, unmounted, or switching tracks/personas.
    return () => clearInterval(interval);
  }, [isPlayingAudio, activeAudio, activePersonaId, workspaceMode]);

  /**
   * Switches the active transport mode (`'demo'` or `'live'`), persists the
   * override in `sessionStorage`, and triggers a state reload via `useEffect`.
   * @param nextMode Target transport mode (`'demo' | 'live'`).
   */
  const handleSwitchTransportMode = (nextMode: AppMode) => {
    setAppModeOverride(nextMode);
    setAppMode(nextMode);
  };

  /**
   * Starts playing a `RADIO_QUEUE` or `CULTURE_CAST` item from `startAtSec` (or its
   * saved `resumePositionSeconds`) in the persistent bottom player bar.
   * @param item Audio item to load and play.
   * @param startAtSec Optional explicit timestamp in seconds (e.g., when clicking a chapter).
   */
  const handleStartAudio = (item: AudioListenItemDto, startAtSec?: number) => {
    const pos = typeof startAtSec === 'number' ? startAtSec : item.resumePositionSeconds || 0;
    setActiveAudio(item);
    setAudioPositionSec(pos);
    setIsPlayingAudio(true);
    if (audioRef.current) {
      audioRef.current.src = item.signedAudioUrl;
      audioRef.current.play().catch(() => {});
    }
  };

  /**
   * Toggles play/pause on the currently loaded `activeAudio` track and immediately
   * saves playback progress when pausing.
   */
  const handleTogglePlayPause = () => {
    if (!activeAudio) return;
    if (isPlayingAudio) {
      setIsPlayingAudio(false);
      audioRef.current?.pause();
      pulseApi.savePlaybackProgress(activePersonaId, activeAudio.id, audioPositionSec, activeAudio.durationSeconds).catch(() => {});
    } else {
      setIsPlayingAudio(true);
      if (audioRef.current) {
        if (!audioRef.current.src) audioRef.current.src = activeAudio.signedAudioUrl;
        audioRef.current.play().catch(() => {});
      }
    }
  };

  /**
   * Seeks the active audio track forward or backward by `deltaSec` seconds
   * (e.g., `-15` for Back 15s, `+15` for Forward 15s), clamped to `[0, durationSeconds]`.
   * @param deltaSec Relative seek offset in seconds.
   */
  const handleSeekAudio = (deltaSec: number) => {
    if (!activeAudio) return;
    const next = Math.max(0, Math.min(activeAudio.durationSeconds, audioPositionSec + deltaSec));
    setAudioPositionSec(next);
    pulseApi.savePlaybackProgress(activePersonaId, activeAudio.id, next, activeAudio.durationSeconds).catch(() => {});
  };

  /**
   * Formats a duration in seconds as `MM:SS` (e.g., `130` -> `'02:10'`).
   * @param sec Duration or position in seconds.
   * @returns Zero-padded `MM:SS` string.
   */
  const formatTime = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  // =========================================================================
  // PRE-FILLED DEMO SIGN-IN SCREEN (Epic 8 / TASK-802)
  // =========================================================================
  if (isAuthenticated !== true) {
    const selectedMeta = getPersonaMeta(activePersonaId);
    return (
      <div className="min-h-screen bg-gradient-to-br from-[#00144d] via-[#002180] to-blue-900 flex items-center justify-center p-4 sm:p-6">
        <div className="max-w-5xl w-full bg-white rounded-2xl shadow-2xl overflow-hidden grid grid-cols-1 lg:grid-cols-12 border border-blue-200/30">
          {/* Left Column: Brand & Persona Photo Selector */}
          <div className="lg:col-span-7 bg-slate-50 p-6 sm:p-8 border-b lg:border-b-0 lg:border-r border-slate-200 space-y-5">
            <div className="flex items-center justify-between flex-wrap gap-3">
              <div className="flex items-center gap-3">
                <img src="/brand/campfire-tech-logo.png" alt="Mastek Campfire" width="44" height="44" className="object-contain" />
                <div>
                  <div className="text-xl font-extrabold text-[#002180] tracking-tight">mastek campfire</div>
                  <div className="text-xs text-slate-600 font-medium">
                    Small stories · Meaningful connections · Weekly recognition
                  </div>
                </div>
              </div>
              <span className="bg-emerald-100 text-emerald-900 border border-emerald-300 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1.5">
                <CheckCircle2 className="w-3.5 h-3.5" /> Demo Credentials Stored
              </span>
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-2.5">
                1. Choose Your Demo Persona (Click a colleague to load their stored profile & credentials)
              </label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {Object.values(DEMO_PERSONAS).map((p) => {
                  const selected = p.id === activePersonaId;
                  return (
                    <button
                      key={p.id}
                      type="button"
                      onClick={() => {
                        setActivePersonaId(p.id);
                        setLoginPassword(DEMO_PASSWORD);
                      }}
                      className={`text-left p-3 rounded-xl border-2 transition-all flex items-center gap-3 ${
                        selected
                          ? 'bg-blue-50/90 border-[#002180] shadow-sm ring-2 ring-[#002180]/15'
                          : 'bg-white border-slate-200 hover:border-blue-300'
                      }`}
                    >
                      <img
                        src={p.avatarUrl}
                        alt={p.displayName}
                        className="w-12 h-12 rounded-full object-cover border-2 border-white shadow-sm shrink-0"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="font-bold text-sm text-slate-900 truncate flex items-center gap-1.5">
                          <span>{p.displayName}</span>
                          {selected && <span className="text-[10px] bg-[#002180] text-white px-1.5 py-0.5 rounded">Selected</span>}
                        </div>
                        <div className="text-xs text-slate-600 truncate">
                          {p.jobTitle} · {p.region}
                        </div>
                        <div className="flex flex-wrap gap-1 mt-1">
                          {p.roles.map((r) => (
                            <span
                              key={r}
                              className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-200"
                            >
                              {r}
                            </span>
                          ))}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Right Column: Pre-Filled Corporate SSO Login Form */}
          <div className="lg:col-span-5 p-6 sm:p-8 flex flex-col justify-between bg-white space-y-6">
            <div className="space-y-5">
              {/* Selected Persona Hero Card */}
              <div className="flex items-center gap-3.5 p-3.5 rounded-xl bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200">
                <img
                  src={selectedMeta.avatarUrl}
                  alt={selectedMeta.displayName}
                  className="w-16 h-16 rounded-full object-cover border-2 border-[#002180] shadow-md shrink-0"
                />
                <div>
                  <div className="text-xs font-bold uppercase tracking-wider text-[#002180]">Signing In As</div>
                  <div className="text-lg font-extrabold text-slate-900">{selectedMeta.displayName}</div>
                  <div className="text-xs text-slate-600">
                    {selectedMeta.jobTitle} ({selectedMeta.region})
                  </div>
                </div>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  setIsSplashBooting(true);
                  setIsAuthenticated(true);
                  setTimeout(() => {
                    setIsSplashBooting(false);
                    setWalkthroughStep(0);
                    setShowWalkthrough(true);
                  }, 1250);
                }}
                className="space-y-4"
              >
                <div>
                  <label htmlFor="demo-email" className="block text-xs font-bold text-slate-700 mb-1">
                    Mastek Corporate Email (Entra ID / SSO)
                  </label>
                  <input
                    id="demo-email"
                    type="email"
                    readOnly
                    value={selectedMeta.email}
                    className="w-full rounded-lg border border-slate-300 bg-slate-50 px-3.5 py-2.5 text-sm font-semibold text-slate-800"
                  />
                </div>

                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label htmlFor="demo-password" className="block text-xs font-bold text-slate-700">
                      Stored Demo Password
                    </label>
                    <span className="text-[11px] text-emerald-700 font-semibold flex items-center gap-1">
                      <Lock className="w-3 h-3" /> Auto-filled for POC
                    </span>
                  </div>
                  <div className="relative">
                    <input
                      id="demo-password"
                      type={showPassword ? 'text' : 'password'}
                      value={loginPassword}
                      onChange={(e) => setLoginPassword(e.target.value)}
                      className="w-full rounded-lg border border-slate-300 px-3.5 py-2.5 pr-10 text-sm font-mono text-slate-900"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-800"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2 pt-1">
                  <div>
                    <label className="block text-[11px] font-bold text-slate-600 mb-1">Launch Workspace</label>
                    <select
                      value={workspaceMode}
                      onChange={(e) => setWorkspaceMode(e.target.value as 'EMPLOYEE' | 'PORTAL' | 'CAMPFIRE')}
                      className="w-full rounded-lg border border-slate-300 px-2.5 py-2 text-xs font-semibold bg-white"
                    >
                      <option value="EMPLOYEE">Employee Web App</option>
                      <option value="PORTAL">Publishing Portal</option>
                      <option value="CAMPFIRE">Standalone POC View</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-[11px] font-bold text-slate-600 mb-1">API Transport Mode</label>
                    <select
                      value={appMode}
                      onChange={(e) => handleSwitchTransportMode(e.target.value as AppMode)}
                      className="w-full rounded-lg border border-slate-300 px-2.5 py-2 text-xs font-semibold bg-white"
                    >
                      <option value="demo">DEMO (In-Browser)</option>
                      <option value="live">LIVE (Java 21 + PG16)</option>
                    </select>
                  </div>
                </div>

                <button
                  type="submit"
                  className="w-full bg-[#002180] hover:bg-blue-900 text-white py-3 rounded-xl text-sm font-extrabold shadow-md transition-colors mt-2"
                >
                  Sign In as {selectedMeta.displayName} →
                </button>
              </form>
            </div>

            <div className="text-[11px] text-slate-500 border-t border-slate-100 pt-3">
              <strong>Walkthrough Tip:</strong> Once signed in, you can switch personas anytime from the top header bar or return to this Sign-In screen via <strong>Sign Out</strong>.
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Standalone Browser Engagement Demo Route (Explicitly labelled as browser simulation)
  if (workspaceMode === 'CAMPFIRE') {
    return (
      <div className="min-h-screen flex flex-col">
        {/* Top disclosure & mode-switching bar for the standalone browser POC */}
        <div className="bg-[#002180] text-white px-4 py-2.5 flex flex-wrap justify-between items-center gap-3 text-xs sm:text-sm">
          <div className="flex items-center gap-2">
            <img src="/brand/campfire-tech-logo.png" alt="" width="28" height="28" className="object-contain" />
            <span className="font-bold">Mastek Campfire · Browser Simulation POC</span>
            <span className="bg-amber-400/20 text-amber-200 border border-amber-300/30 px-2 py-0.5 rounded-full text-[11px] font-semibold">
              Pure Browser Engine (Not Java API)
            </span>
          </div>
          <div className="flex items-center gap-3">
            {/* Button to switch directly into the authoritative Live Java 21 + PostgreSQL 16 workspace */}
            <button
              className="bg-white text-[#002180] hover:bg-blue-50 px-3 py-1 rounded-md text-xs font-bold shadow-sm"
              onClick={() => {
                handleSwitchTransportMode('live');
                setWorkspaceMode('EMPLOYEE');
              }}
            >
              Open Live Java + PostgreSQL Workspace →
            </button>
            {/* Button to open the full React Employee & Publishing Portal workflows in simulated Demo mode */}
            <button
              className="underline text-blue-100 hover:text-white text-xs font-semibold"
              onClick={() => {
                handleSwitchTransportMode('demo');
                setWorkspaceMode('EMPLOYEE');
              }}
            >
              Open Full Employee & Publishing Workflows (Demo Mode)
            </button>
          </div>
        </div>
        {/* Isolated iframe hosting `/campfire/index.html` (`app.mjs` + `engine.mjs`) */}
        <iframe
          src="/campfire/index.html"
          title="Mastek Campfire engagement demo"
          style={{ width: '100%', height: 'calc(100vh - 46px)', border: 0 }}
        />
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      {/* Hidden HTML5 audio element controlled by `audioRef` */}
      <audio ref={audioRef} className="hidden" />

      {/* Top Mastek Campfire Header */}
      <header className="bg-[#002180] text-white shadow-md sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-3">
          <div className="flex items-center gap-4">
            {/* Brand Logo & Home Link */}
            <a
              href="#top"
              aria-label="Mastek Campfire home"
              onClick={(e) => {
                e.preventDefault();
                setWorkspaceMode('EMPLOYEE');
              }}
              className="flex items-center gap-2"
            >
              <img src="/brand/campfire-tech-logo.png" alt="" width="40" height="40" className="object-contain" />
              <span className="text-lg sm:text-xl font-extrabold tracking-tight">mastek campfire</span>
            </a>

            {/* Explicit Transport Mode Toggle (D1: demo vs live) */}
            <div
              className="hidden sm:flex items-center bg-blue-950/90 p-0.5 rounded-lg border border-blue-400/30 text-[11px]"
              role="group"
              aria-label="Transport Mode Selection"
            >
              <button
                onClick={() => handleSwitchTransportMode('demo')}
                className={`px-2.5 py-1 rounded-md font-bold transition-colors ${
                  appMode === 'demo' ? 'bg-amber-400 text-slate-950' : 'text-blue-200 hover:text-white'
                }`}
              >
                DEMO (Simulated)
              </button>
              <button
                onClick={() => handleSwitchTransportMode('live')}
                className={`px-2.5 py-1 rounded-md font-bold flex items-center gap-1 transition-colors ${
                  appMode === 'live' ? 'bg-emerald-400 text-slate-950' : 'text-blue-200 hover:text-white'
                }`}
              >
                <Database className="w-3 h-3" /> LIVE (Java+PG)
              </button>
            </div>

            {/* Workspace Switcher (Employee Web | Publishing Portal | Standalone POC) */}
            <div className="flex bg-blue-950/80 p-1 rounded-lg border border-blue-400/20">
              <button
                onClick={() => setWorkspaceMode('EMPLOYEE')}
                className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-colors ${
                  workspaceMode === 'EMPLOYEE' ? 'bg-white text-[#002180]' : 'text-blue-200 hover:text-white'
                }`}
              >
                Employee Web
              </button>
              <button
                onClick={() => setWorkspaceMode('PORTAL')}
                className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-colors ${
                  workspaceMode === 'PORTAL' ? 'bg-white text-[#002180]' : 'text-blue-200 hover:text-white'
                }`}
              >
                Publishing Portal
              </button>
              <button
                onClick={() => setWorkspaceMode('CAMPFIRE')}
                className="px-2.5 py-1 rounded-md text-xs font-semibold text-blue-200 hover:text-white hidden md:inline-block"
              >
                Standalone POC
              </button>
            </div>
          </div>

          {/* Right Controls: Active User Photo, Persona Switcher, Demo Reset & Sign Out */}
          <div className="flex items-center gap-2.5">
            <div className="flex items-center gap-2">
              <img
                src={getPersonaAvatar(activePersonaId)}
                alt={getPersonaMeta(activePersonaId).displayName}
                className="w-8 h-8 rounded-full object-cover border-2 border-blue-300 shadow-sm"
              />
              <label htmlFor="persona-select" className="text-xs text-blue-200 hidden lg:inline">
                Persona:
              </label>
              {/* Canonical Server Persona Selector (`usr_asha`, `usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`, `usr_rohan`) */}
              <select
                id="persona-select"
                value={activePersonaId}
                onChange={(e) => setActivePersonaId(e.target.value)}
                className="bg-blue-900 text-white text-xs font-semibold rounded-lg px-2.5 py-1.5 border border-blue-400/40 focus:outline-none"
              >
                {(personas.length > 0
                  ? personas
                  : [
                      { id: 'usr_asha', displayName: 'Asha Patel', region: 'INDIA', roles: ['EMPLOYEE', 'CREATOR'] },
                      { id: 'usr_daniel', displayName: 'Daniel Wright', region: 'UK', roles: ['EMPLOYEE', 'CREATOR'] },
                      { id: 'usr_priya', displayName: 'Priya Nair', region: 'INDIA', roles: ['EMPLOYEE', 'CREATOR', 'REVIEWER'] },
                      { id: 'usr_marcus', displayName: 'Marcus Vance', region: 'US', roles: ['EMPLOYEE', 'PUBLISHER', 'MODERATOR'] },
                      { id: 'usr_elena', displayName: 'Elena Rostova', region: 'UK', roles: ['EMPLOYEE', 'CREATOR', 'ADMIN'] },
                      { id: 'usr_rohan', displayName: 'Rohan Deshmukh', region: 'INDIA', roles: ['EMPLOYEE', 'CREATOR'] },
                    ]
                ).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.displayName} ({p.region} · {p.roles.filter((r) => r !== 'EMPLOYEE').join('/') || 'Employee'})
                  </option>
                ))}
              </select>
            </div>

            {/* Reset Demo button shown only when running in simulated `demo` mode */}
            {appMode === 'demo' && (
              <button
                onClick={() => {
                  resetDemoState();
                  loadAllData(activePersonaId, workspaceMode);
                }}
                title="Reset local demo state to fresh pilot seed data"
                className="text-xs bg-blue-900/70 hover:bg-blue-800 text-blue-100 px-2.5 py-1.5 rounded-lg border border-blue-400/30 flex items-center gap-1"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span className="hidden md:inline">Reset Demo</span>
              </button>
            )}

            {/* Guided Walkthrough Tour Button for New Users */}
            <button
              onClick={() => {
                setWalkthroughStep(0);
                setShowWalkthrough(true);
              }}
              title="Open 5-Step Guided Walkthrough Tour"
              className="text-xs bg-amber-400 hover:bg-amber-300 text-slate-950 font-extrabold px-2.5 py-1.5 rounded-lg shadow-sm flex items-center gap-1 transition-colors"
            >
              <span>🎯</span>
              <span className="hidden sm:inline">Walkthrough</span>
            </button>

            {/* Sign Out button to return to the pre-filled Demo Login screen */}
            <button
              onClick={() => {
                setShowWalkthrough(false);
                setIsAuthenticated(false);
              }}
              title="Sign out to Demo Login screen"
              className="text-xs bg-blue-950/80 hover:bg-red-700 text-blue-100 hover:text-white px-2.5 py-1.5 rounded-lg border border-blue-400/30 flex items-center gap-1 transition-colors"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span className="hidden md:inline">Sign Out</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Workspace Body with Explicit Error & Retry States (D1: No infinite loading spinner!) */}
      <div className="flex-1">
        {loadError ? (
          /* Explicit HTTP / Network Error Alert Banner with Retry & Explicit Demo Switch */
          <div className="max-w-3xl mx-auto my-12 px-4">
            <div
              role="alert"
              className="bg-red-50 border-2 border-red-300 rounded-xl p-6 shadow-sm space-y-4 text-red-950"
            >
              <div className="flex items-center gap-2 text-red-800 font-bold text-lg">
                <AlertTriangle className="w-6 h-6 shrink-0" />
                <span>
                  {appMode === 'live' ? 'Live API Error' : 'Application Error'} ({loadError.code} · HTTP{' '}
                  {loadError.status})
                </span>
              </div>
              <p className="text-sm text-red-900">{loadError.message}</p>
              <p className="text-xs text-red-700">
                Active Mode: <strong>{appMode.toUpperCase()}</strong>. In live mode, failed requests never fall back to
                simulated local state.
              </p>
              <div className="flex flex-wrap gap-3 pt-2">
                <button
                  onClick={() => loadAllData(activePersonaId, workspaceMode)}
                  className="bg-[#002180] hover:bg-blue-900 text-white px-4 py-2 rounded-lg text-xs font-bold"
                >
                  Retry Request
                </button>
                {appMode === 'live' && (
                  <button
                    onClick={() => handleSwitchTransportMode('demo')}
                    className="bg-white border border-slate-300 hover:bg-slate-100 text-slate-800 px-4 py-2 rounded-lg text-xs font-bold"
                  >
                    Switch to Explicit Demo Mode (Browser Simulation)
                  </button>
                )}
              </div>
            </div>
          </div>
        ) : isSplashBooting || isLoading || !currentUser ? (
          /* Animated Mastek Campfire Splash Loader */
          <div className="min-h-[75vh] flex items-center justify-center p-6">
            <div className="max-w-md w-full bg-gradient-to-br from-[#00144d] via-[#002180] to-blue-900 text-white rounded-2xl p-8 shadow-2xl border border-blue-400/30 text-center space-y-6">
              <div className="relative w-24 h-24 mx-auto flex items-center justify-center">
                <div className="absolute inset-0 rounded-full border-2 border-dashed border-amber-400/60 campfire-orbit-ring" />
                <div className="w-20 h-20 rounded-full bg-blue-950/90 border-2 border-amber-400 flex items-center justify-center campfire-splash-emblem">
                  <img
                    src="/brand/campfire-tech-logo.png"
                    alt="Mastek Campfire"
                    width="48"
                    height="48"
                    className="object-contain"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <div className="text-xs font-extrabold uppercase tracking-widest text-amber-300">
                  Igniting Mastek Campfire · {appMode.toUpperCase()} Mode
                </div>
                <h2 className="text-xl font-extrabold tracking-tight">
                  Welcome, {getPersonaMeta(activePersonaId).displayName}
                </h2>
                <p className="text-xs text-blue-200">
                  {getPersonaMeta(activePersonaId).jobTitle} ({getPersonaMeta(activePersonaId).region})
                </p>
              </div>

              <div className="w-full bg-blue-950/80 h-2.5 rounded-full overflow-hidden border border-blue-400/30">
                <div className="bg-gradient-to-r from-amber-400 via-orange-400 to-emerald-400 h-full rounded-full campfire-progress-bar" />
              </div>

              <div className="text-left bg-blue-950/60 rounded-xl p-3.5 border border-blue-400/20 space-y-1.5 text-xs text-blue-100">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                  <span>Filtering stories &amp; audio by your permitted audiences</span>
                </div>
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                  <span>Loading Weekly Challenge &amp; Recognition Ledger</span>
                </div>
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                  <span>Preparing your private Make me Productive workspace</span>
                </div>
              </div>
            </div>
          </div>
        ) : workspaceMode === 'EMPLOYEE' ? (
          /* Employee Web Experience (Wireframes 01–08, Weekly Challenge, Make me Productive & Community Champions) */
          <EmployeeWorkspace
            appMode={appMode}
            currentUser={currentUser}
            feed={feed}
            radioQueues={radioQueues}
            cultureCasts={cultureCasts}
            requests={requests}
            challenge={challenge}
            myRecognition={myRecognition}
            leaderboard={leaderboard}
            activeAudio={activeAudio}
            isPlayingAudio={isPlayingAudio}
            audioPositionSec={audioPositionSec}
            onStartAudio={handleStartAudio}
            onTogglePlayPause={handleTogglePlayPause}
            onSeekAudio={handleSeekAudio}
            onRefreshAll={() => loadAllData(activePersonaId, 'EMPLOYEE')}
            onSwitchPersona={(uid) => setActivePersonaId(uid)}
            onOpenPortal={() => setWorkspaceMode('PORTAL')}
            externalNavTab={tourTargetTab}
          />
        ) : !hasPortalRole(currentUser) ? (
          /* D1 + D2 Gate: Ordinary employee denied access to Publishing Portal without breaking Employee mode! */
          <div className="max-w-3xl mx-auto my-12 px-4">
            <div
              role="alert"
              className="bg-amber-50 border-2 border-amber-300 rounded-xl p-6 shadow-sm space-y-4 text-amber-950"
            >
              <div className="flex items-center gap-2 font-bold text-lg text-amber-900">
                <AlertTriangle className="w-6 h-6 shrink-0" />
                <span>403 Forbidden (FORBIDDEN_ACCESS): Scoped Portal Role Required</span>
              </div>
              <p className="text-sm">
                Current user <strong>{currentUser.displayName}</strong> holds roles{' '}
                <code>{currentUser.roles.join(', ')}</code>. Accessing the Publishing Portal requires{' '}
                <code>REVIEWER</code>, <code>PUBLISHER</code>, <code>MODERATOR</code>, or <code>ADMIN</code>.
              </p>
              <div className="flex flex-wrap gap-3 pt-2">
                <button
                  onClick={() => setActivePersonaId('usr_priya')}
                  className="bg-[#002180] text-white px-4 py-2 rounded-lg text-xs font-bold"
                >
                  Switch to Priya Nair (Reviewer)
                </button>
                <button
                  onClick={() => setActivePersonaId('usr_marcus')}
                  className="bg-emerald-700 text-white px-4 py-2 rounded-lg text-xs font-bold"
                >
                  Switch to Marcus Vance (Publisher)
                </button>
                <button
                  onClick={() => setWorkspaceMode('EMPLOYEE')}
                  className="bg-white border border-amber-400 text-amber-950 px-4 py-2 rounded-lg text-xs font-bold"
                >
                  Return to Employee Workspace
                </button>
              </div>
            </div>
          </div>
        ) : (
          /* Role-Based Publishing Portal (Proposal Page 11 + Page 5/12 Pilot & AI Budget Gates) */
          <PublishingPortalWorkspace
            currentUser={currentUser}
            portalQueue={portalQueue}
            auditLog={auditLog}
            pilotMetrics={
              pilotMetrics || {
                usefulConnectionsCount: 1,
                unknownFeedbackCount: 0,
                usefulConnectionsPct: 25,
                usefulConnectionsTargetPct: 20,
                acceptanceRatePct: 100,
                acceptanceTargetPct: 50,
                acceptedRequestsCount: 1,
                pendingRequestsCount: 0,
                returnParticipationPct: 48,
                returnParticipationTargetPct: 40,
                medianHostWorkloadMinutes: 30,
                hostWorkloadTargetMinutes: 45,
                materialTrustIncidents: 0,
              }
            }
            aiBudget={
              aiBudget || {
                actionsUsed: 3,
                actionCap: 720,
                percentUsed: 0.4,
                estimatedInputTokens: 3000,
                estimatedOutputTokens: 1500,
                alertLevel: 'NORMAL',
                hardCapReached: false,
              }
            }
            onRefreshAll={() => loadAllData(activePersonaId, 'PORTAL')}
            onSwitchPersona={(uid) => setActivePersonaId(uid)}
          />
        )}
      </div>

      {/* Interactive 5-Step New-User Guided Walkthrough Modal */}
      {showWalkthrough && !isSplashBooting && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Mastek Campfire New-User Walkthrough"
          className="fixed inset-0 bg-slate-950/65 backdrop-blur-sm z-50 flex items-center justify-center p-4"
        >
          {(() => {
            const steps = [
              {
                badge: 'STEP 1 OF 5 · DISCOVER & CONNECT',
                title: 'Small stories that spark real 15-minute conversations',
                body: 'Browse colleague stories filtered by your audience and explicit interests. Every card shows a factual "WHY THIS?" badge—never a fake compatibility score. Open any story to send a 15-minute invitation (with optional AI drafting or template fallback) and complete a private bilateral check-in afterwards.',
                actionLabel: 'Explore Your Campfire Feed →',
                onJump: () => {
                  setWorkspaceMode('EMPLOYEE');
                  setTourTargetTab('PULSE');
                  setShowWalkthrough(false);
                },
              },
              {
                badge: 'STEP 2 OF 5 · WEEKLY CHALLENGE & CHAMPIONS',
                title: 'Submit a story, appreciate colleagues, earn up to 60 pts/week',
                body: 'Join the weekly "Beyond My Job Title" challenge! Submitting earns 0 points immediately; once Reviewer Priya approves and Publisher Marcus publishes your revision, you earn +20 points and the First Spark badge. Appreciate colleagues (+5/+5 pts) and opt in or out of the Top-5 Community Champions leaderboard anytime.',
                actionLabel: 'Open Challenge & Leaderboard →',
                onJump: () => {
                  setWorkspaceMode('EMPLOYEE');
                  setTourTargetTab('CHAMPIONS');
                  setShowWalkthrough(false);
                },
              },
              {
                badge: 'STEP 3 OF 5 · MASTEK RADIO & CULTURE CAST',
                title: 'Commute listening with chapters, transcripts & resume',
                body: 'Play the 12-minute Mastek Radio commute queue or dive into Culture Cast episodes with interactive chapters, human-verified transcripts, and cross-device playback resume from the persistent bottom player bar.',
                actionLabel: 'Open Listen Workspace →',
                onJump: () => {
                  setWorkspaceMode('EMPLOYEE');
                  setTourTargetTab('LISTEN');
                  setShowWalkthrough(false);
                },
              },
              {
                badge: 'STEP 4 OF 5 · MAKE ME PRODUCTIVE (PRIVATE SPACE)',
                title: 'Personal habits, Pomodoro focus & 4-4-4-4 Box Breathing',
                body: 'Your private space to grow: build a daily/weekday/weekend habit routine, run a deadline-based Pomodoro focus timer, use the 5-minute unstuck starter, or take a Box Breathing / Mindfulness pause. 100% owner-private—zero points, zero leaderboards, zero manager access.',
                actionLabel: 'Open Make me Productive →',
                onJump: () => {
                  setWorkspaceMode('EMPLOYEE');
                  setTourTargetTab('PRODUCTIVE');
                  setShowWalkthrough(false);
                },
              },
              {
                badge: 'STEP 5 OF 5 · PUBLISHING PORTAL & TRUST GATES',
                title: 'Role-based human review, 720-action AI cap & audit trail',
                body: 'Switch personas in the top header bar (e.g., Priya Nair for Reviewer or Marcus Vance for Publisher) and open the Publishing Portal to inspect revisions, approve/publish stories, and monitor the 4-week Pilot & AI 720-Action Budget Gates.',
                actionLabel: 'Switch to Priya & Open Publishing Portal →',
                onJump: () => {
                  setActivePersonaId('usr_priya');
                  setWorkspaceMode('PORTAL');
                  setShowWalkthrough(false);
                },
              },
            ];
            const current = steps[walkthroughStep] || steps[0];

            return (
              <div className="bg-white rounded-2xl max-w-xl w-full overflow-hidden shadow-2xl border border-blue-200">
                <div className="bg-gradient-to-r from-[#002180] to-blue-800 text-white p-6 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] font-extrabold uppercase tracking-widest bg-amber-400 text-slate-950 px-2.5 py-0.5 rounded-full">
                      {current.badge}
                    </span>
                    <button
                      onClick={() => setShowWalkthrough(false)}
                      className="text-xs text-blue-200 hover:text-white underline font-semibold"
                    >
                      Skip Tour ✕
                    </button>
                  </div>
                  <h3 className="text-xl font-extrabold leading-snug pt-1">{current.title}</h3>
                </div>

                <div className="p-6 space-y-5">
                  <p className="text-sm text-slate-700 leading-relaxed">{current.body}</p>

                  <button
                    type="button"
                    onClick={current.onJump}
                    className="w-full bg-blue-50 hover:bg-blue-100 text-[#002180] border border-blue-200 py-2.5 px-4 rounded-xl text-xs font-extrabold transition-colors"
                  >
                    {current.actionLabel}
                  </button>

                  {/* Progress Dots & Navigation Controls */}
                  <div className="flex items-center justify-between pt-3 border-t border-slate-200">
                    <div className="flex items-center gap-1.5">
                      {steps.map((_, idx) => (
                        <button
                          key={idx}
                          type="button"
                          aria-label={`Go to tour step ${idx + 1}`}
                          onClick={() => setWalkthroughStep(idx)}
                          className={`h-2.5 rounded-full transition-all ${
                            idx === walkthroughStep ? 'w-7 bg-[#002180]' : 'w-2.5 bg-slate-300 hover:bg-slate-400'
                          }`}
                        />
                      ))}
                    </div>

                    <div className="flex items-center gap-2">
                      {walkthroughStep > 0 && (
                        <button
                          type="button"
                          onClick={() => setWalkthroughStep((s) => Math.max(0, s - 1))}
                          className="px-3.5 py-2 rounded-lg border border-slate-300 text-xs font-bold text-slate-700 hover:bg-slate-100"
                        >
                          ← Back
                        </button>
                      )}
                      {walkthroughStep < steps.length - 1 ? (
                        <button
                          type="button"
                          onClick={() => setWalkthroughStep((s) => s + 1)}
                          className="px-4 py-2 rounded-lg bg-[#002180] hover:bg-blue-900 text-white text-xs font-bold"
                        >
                          Next Step →
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setShowWalkthrough(false)}
                          className="px-4 py-2 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-bold"
                        >
                          Start Exploring ✓
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })()}
        </div>
      )}

      {/* Transcript Modal opened from the Persistent Bottom Audio Bar */}
      {showBottomTranscriptModal && activeAudio && (
        <div className="fixed inset-0 bg-slate-900/50 z-40 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-xl w-full p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-200 pb-3">
              <h3 className="font-bold text-slate-900">Transcript — {activeAudio.title}</h3>
              <button
                onClick={() => setShowBottomTranscriptModal(false)}
                className="text-xs font-bold text-[#002180] underline"
              >
                Close
              </button>
            </div>
            <pre className="text-xs text-slate-700 bg-slate-50 p-4 rounded-lg border border-slate-200 whitespace-pre-wrap font-sans max-h-72 overflow-y-auto">
              {activeAudio.transcriptText}
            </pre>
          </div>
        </div>
      )}

      {/* Persistent Bottom Audio Bar (Proposal Page 10: Paused by default, cross-device resume, transcript toggle) */}
      {activeAudio && (
        <footer
          aria-label="Persistent audio player"
          className="fixed bottom-0 inset-x-0 bg-slate-100 border-t border-slate-300 shadow-lg z-30"
        >
          <div className="max-w-7xl mx-auto px-4 sm:px-6 h-14 flex items-center justify-between gap-4 text-xs sm:text-sm">
            <div className="flex items-center gap-2.5 truncate">
              <Headphones className="w-4 h-4 text-[#002180] shrink-0" />
              <span className="font-semibold text-slate-800 truncate">
                {isPlayingAudio ? 'Playing:' : 'Paused:'}{' '}
                {activeAudio.contentType === 'RADIO_QUEUE' ? 'Mastek Radio' : 'Culture Cast'} — {activeAudio.title} /{' '}
                <span className="font-mono text-[#002180]">
                  {formatTime(audioPositionSec)} of {formatTime(activeAudio.durationSeconds)}
                </span>
              </span>
            </div>

            <div className="flex items-center gap-3 shrink-0">
              <button
                onClick={handleTogglePlayPause}
                className="bg-[#002180] hover:bg-blue-900 text-white px-3.5 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1"
              >
                {isPlayingAudio ? (
                  <>
                    <Pause className="w-3.5 h-3.5" /> Pause
                  </>
                ) : (
                  <>
                    <Play className="w-3.5 h-3.5" /> Play
                  </>
                )}
              </button>
              <button
                onClick={() => setShowBottomTranscriptModal(!showBottomTranscriptModal)}
                className="text-xs font-semibold text-[#002180] hover:underline"
              >
                Transcript
              </button>
              <button
                onClick={() => {
                  const nextItem =
                    activeAudio.contentType === 'CULTURE_CAST' && radioQueues[0]
                      ? radioQueues[0]
                      : cultureCasts[0] || activeAudio;
                  handleStartAudio(nextItem, nextItem.resumePositionSeconds);
                }}
                className="text-xs font-semibold text-slate-700 hover:underline hidden sm:inline"
              >
                Switch Queue
              </button>
            </div>
          </div>
        </footer>
      )}
    </div>
  );
};
