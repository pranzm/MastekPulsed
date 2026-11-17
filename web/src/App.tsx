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
import { EmployeeWorkspace } from './employee/EmployeeWorkspace';
import { PublishingPortalWorkspace } from './portal/PublishingPortalWorkspace';
import { Play, Pause, RotateCcw, Headphones, UserCheck, AlertTriangle, Database, Sparkles } from 'lucide-react';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/App.tsx
 * Journey: Unified Top-Level Shell for Mastek Campfire (Standalone Browser Demo,
 *          Employee Web & Role-Based Publishing Portal + Persistent Audio Bar).
 * Why it is designed this way (Implements Plan Step D1, D4, D5 & CF-003, CF-011):
 *   1. NO BACKGROUND FETCH ON STANDALONE DEMO: When `workspaceMode === 'CAMPFIRE'`
 *      (the pure browser iframe `/campfire/index.html`), no `/api/v1` requests
 *      are fired in the background.
 *   2. EXPLICIT DEMO VS. LIVE TRANSPORT INDICATOR: Displays whether the app is
 *      running in `demo` (in-browser simulation) or `live` (authoritative Java 21 +
 *      PostgreSQL 16 API). Live errors surface with `status`, `code`, `message`,
 *      and a Retry button—never an indefinite spinner or silent local fallback!
 *   3. ROLE-AWARE LOADING & PERSONA CACHE CLEARING:
 *      - Clears persona-scoped state (`feed`, `requests`, `portalQueue`, `challenge`,
 *        `recognition`) immediately on persona/mode switch so restricted content
 *        never leaks across personas.
 *      - Fetches `/me` first, loads Employee & Campfire recognition data for all
 *        authenticated users, and fetches `/portal/*` ONLY when the user holds a
 *        scoped portal role (`REVIEWER`, `PUBLISHER`, `MODERATOR`, `ADMIN`).
 * ============================================================================
 */

export const App: React.FC = () => {
  const [activePersonaId, setActivePersonaId] = useState<string>('usr_asha');
  const [workspaceMode, setWorkspaceMode] = useState<'EMPLOYEE' | 'PORTAL' | 'CAMPFIRE'>('CAMPFIRE');
  const [appMode, setAppMode] = useState<AppMode>(() => getAppMode());

  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [loadError, setLoadError] = useState<{ status: number; code: string; message: string } | null>(null);

  const [personas, setPersonas] = useState<UserProfileDto[]>([]);
  const [currentUser, setCurrentUser] = useState<UserProfileDto | null>(null);
  const [feed, setFeed] = useState<ContentCardDto[]>([]);
  const [radioQueues, setRadioQueues] = useState<AudioListenItemDto[]>([]);
  const [cultureCasts, setCultureCasts] = useState<AudioListenItemDto[]>([]);
  const [requests, setRequests] = useState<ConnectionRequestDto[]>([]);
  const [challenge, setChallenge] = useState<ChallengeDto | null>(null);
  const [myRecognition, setMyRecognition] = useState<RecognitionProgressDto | null>(null);
  const [leaderboard, setLeaderboard] = useState<LeaderboardResponseDto | null>(null);

  const [portalQueue, setPortalQueue] = useState<PortalQueueItemDto[]>([]);
  const [auditLog, setAuditLog] = useState<AuditEventDto[]>([]);
  const [pilotMetrics, setPilotMetrics] = useState<PilotMetricsDto | null>(null);
  const [aiBudget, setAiBudget] = useState<AiBudgetStatusDto | null>(null);

  // Persistent Bottom Audio Player Bar State (Matches Proposal Page 10)
  const [activeAudio, setActiveAudio] = useState<AudioListenItemDto | null>(null);
  const [isPlayingAudio, setIsPlayingAudio] = useState<boolean>(false);
  const [audioPositionSec, setAudioPositionSec] = useState<number>(130);
  const [showBottomTranscriptModal, setShowBottomTranscriptModal] = useState<boolean>(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const hasPortalRole = (user: UserProfileDto | null): boolean => {
    if (!user) return false;
    return (
      user.roles.includes('REVIEWER') ||
      user.roles.includes('PUBLISHER') ||
      user.roles.includes('MODERATOR') ||
      user.roles.includes('ADMIN')
    );
  };

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

  const loadAllData = async (personaId = activePersonaId, targetWorkspace = workspaceMode) => {
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
        setPortalQueue([]);
        setAuditLog([]);
        setPilotMetrics(null);
        setAiBudget(null);
      }
    } catch (err: any) {
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
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (workspaceMode === 'CAMPFIRE') {
      return;
    }
    clearPersonaScopedState();
    if (isPlayingAudio) {
      setIsPlayingAudio(false);
      audioRef.current?.pause();
    }
    loadAllData(activePersonaId, workspaceMode);
  }, [activePersonaId, workspaceMode, appMode]);

  useEffect(() => {
    if (!isPlayingAudio || !activeAudio || workspaceMode === 'CAMPFIRE') return;
    const interval = setInterval(() => {
      setAudioPositionSec((prev) => {
        const next = prev + 1;
        if (next % 5 === 0) {
          pulseApi.savePlaybackProgress(activePersonaId, activeAudio.id, next, activeAudio.durationSeconds).catch(() => {});
        }
        return next >= activeAudio.durationSeconds ? 0 : next;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [isPlayingAudio, activeAudio, activePersonaId, workspaceMode]);

  const handleSwitchTransportMode = (nextMode: AppMode) => {
    setAppModeOverride(nextMode);
    setAppMode(nextMode);
  };

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

  const handleSeekAudio = (deltaSec: number) => {
    if (!activeAudio) return;
    const next = Math.max(0, Math.min(activeAudio.durationSeconds, audioPositionSec + deltaSec));
    setAudioPositionSec(next);
    pulseApi.savePlaybackProgress(activePersonaId, activeAudio.id, next, activeAudio.durationSeconds).catch(() => {});
  };

  const formatTime = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  // Standalone Browser Engagement Demo Route (Explicitly labelled as browser simulation)
  if (workspaceMode === 'CAMPFIRE') {
    return (
      <div className="min-h-screen flex flex-col">
        <div className="bg-[#002180] text-white px-4 py-2.5 flex flex-wrap justify-between items-center gap-3 text-xs sm:text-sm">
          <div className="flex items-center gap-2">
            <img src="/brand/campfire-tech-logo.png" alt="" width="28" height="28" className="object-contain" />
            <span className="font-bold">Mastek Campfire · Browser Simulation POC</span>
            <span className="bg-amber-400/20 text-amber-200 border border-amber-300/30 px-2 py-0.5 rounded-full text-[11px] font-semibold">
              Pure Browser Engine (Not Java API)
            </span>
          </div>
          <div className="flex items-center gap-3">
            <button
              className="bg-white text-[#002180] hover:bg-blue-50 px-3 py-1 rounded-md text-xs font-bold shadow-sm"
              onClick={() => {
                handleSwitchTransportMode('live');
                setWorkspaceMode('EMPLOYEE');
              }}
            >
              Open Live Java + PostgreSQL Workspace →
            </button>
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
      <audio ref={audioRef} className="hidden" />

      {/* Top Mastek Campfire Header */}
      <header className="bg-[#002180] text-white shadow-md sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-3">
          <div className="flex items-center gap-4">
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

            {/* Workspace Switcher */}
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

          {/* Right Controls: Persona Switcher & Demo Reset */}
          <div className="flex items-center gap-2.5">
            <div className="flex items-center gap-1.5">
              <UserCheck className="w-4 h-4 text-blue-200 hidden sm:inline" />
              <label htmlFor="persona-select" className="text-xs text-blue-200 hidden lg:inline">
                Persona:
              </label>
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
          </div>
        </div>
      </header>

      {/* Main Workspace Body with Explicit Error & Retry States (D1: No infinite loading spinner!) */}
      <div className="flex-1">
        {loadError ? (
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
        ) : isLoading || !currentUser ? (
          <div className="min-h-[60vh] flex items-center justify-center text-[#002180] font-semibold text-sm">
            Loading Mastek Campfire ({appMode.toUpperCase()} mode)...
          </div>
        ) : workspaceMode === 'EMPLOYEE' ? (
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

      {/* Transcript Modal from Bottom Bar */}
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

      {/* Persistent Bottom Audio Bar */}
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
