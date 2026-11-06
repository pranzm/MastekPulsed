import React, { useEffect, useRef, useState } from 'react';
import {
  AiBudgetStatusDto,
  AudioListenItemDto,
  AuditEventDto,
  ConnectionRequestDto,
  ContentCardDto,
  PilotMetricsDto,
  PortalQueueItemDto,
  UserProfileDto,
} from './api/contracts';
import { pulseApi, resetDemoState } from './api/apiClient';
import { EmployeeWorkspace } from './employee/EmployeeWorkspace';
import { PublishingPortalWorkspace } from './portal/PublishingPortalWorkspace';
import { Play, Pause, RotateCcw, Headphones, Shield, UserCheck } from 'lucide-react';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/App.tsx
 * Journey: Unified Top-Level Shell for Employee Web (Page 10) & Role-Based
 *          Publishing Portal (Page 11) + Persistent Bottom Audio Player Bar.
 * Why it is designed this way:
 *   1. Provides the dark navy Mastek Pulse header (`#002180`) matching Page 10
 *      and Page 11 of the proposal.
 *   2. Includes a 1-click Demo Persona Switcher (`Asha`, `Daniel`, `Priya`,
 *      `Marcus`, `Elena`) and Workspace Switcher (`Employee Workspace` vs.
 *      `Publishing Portal`) so judges and developers can test every journey in
 *      seconds.
 *   3. Houses the persistent bottom audio player bar (`Paused: Culture Cast /
 *      04:12 of 08:00 · Play · Transcript · Queue` - Page 10) that syncs
 *      playback position (`PUT /api/v1/listen/progress`) for cross-device resume!
 * ============================================================================
 */

export const App: React.FC = () => {
  const [activePersonaId, setActivePersonaId] = useState<string>('usr_asha');
  const [workspaceMode, setWorkspaceMode] = useState<'EMPLOYEE' | 'PORTAL'>('EMPLOYEE');

  const [personas, setPersonas] = useState<UserProfileDto[]>([]);
  const [currentUser, setCurrentUser] = useState<UserProfileDto | null>(null);
  const [feed, setFeed] = useState<ContentCardDto[]>([]);
  const [radioQueues, setRadioQueues] = useState<AudioListenItemDto[]>([]);
  const [cultureCasts, setCultureCasts] = useState<AudioListenItemDto[]>([]);
  const [requests, setRequests] = useState<ConnectionRequestDto[]>([]);
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

  const loadAllData = async (personaId = activePersonaId) => {
    const [allPersonas, me, feedList, radios, casts, reqs, pQueue, audits, metricsAndBudget] = await Promise.all([
      pulseApi.getPersonas(personaId),
      pulseApi.getCurrentUser(personaId),
      pulseApi.getFeed(personaId),
      pulseApi.getRadioQueues(personaId),
      pulseApi.getCultureCasts(personaId),
      pulseApi.listRequests(personaId),
      pulseApi.getPortalQueue(personaId),
      pulseApi.getAuditLog(personaId),
      pulseApi.getPilotMetricsAndBudget(personaId),
    ]);

    setPersonas(allPersonas);
    setCurrentUser(me);
    setFeed(feedList);
    setRadioQueues(radios);
    setCultureCasts(casts);
    setRequests(reqs);
    setPortalQueue(pQueue);
    setAuditLog(audits);
    setPilotMetrics(metricsAndBudget.metrics);
    setAiBudget(metricsAndBudget.budget);

    if (!activeAudio && casts[0]) {
      setActiveAudio(casts[0]);
      setAudioPositionSec(casts[0].resumePositionSeconds || 130);
    }
  };

  useEffect(() => {
    loadAllData(activePersonaId);
  }, [activePersonaId]);

  // Simulated timer + actual HTML5 audio element for cross-device resume position
  useEffect(() => {
    if (!isPlayingAudio || !activeAudio) return;
    const interval = setInterval(() => {
      setAudioPositionSec((prev) => {
        const next = prev + 1;
        if (next % 5 === 0) {
          pulseApi.savePlaybackProgress(activePersonaId, activeAudio.id, next, activeAudio.durationSeconds);
        }
        return next >= activeAudio.durationSeconds ? 0 : next;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [isPlayingAudio, activeAudio, activePersonaId]);

  const handleStartAudio = (item: AudioListenItemDto, startAtSec?: number) => {
    const pos = typeof startAtSec === 'number' ? startAtSec : item.resumePositionSeconds || 0;
    setActiveAudio(item);
    setAudioPositionSec(pos);
    setIsPlayingAudio(true);
    if (audioRef.current) {
      audioRef.current.src = item.signedAudioUrl;
      audioRef.current.play().catch(() => {
        // Browser autoplay policy fallback; progress timer still runs smoothly
      });
    }
  };

  const handleTogglePlayPause = () => {
    if (!activeAudio) return;
    if (isPlayingAudio) {
      setIsPlayingAudio(false);
      audioRef.current?.pause();
      pulseApi.savePlaybackProgress(activePersonaId, activeAudio.id, audioPositionSec, activeAudio.durationSeconds);
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
    pulseApi.savePlaybackProgress(activePersonaId, activeAudio.id, next, activeAudio.durationSeconds);
  };

  const formatTime = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  if (!currentUser || !pilotMetrics || !aiBudget) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 text-[#002180] font-semibold">
        Loading Mastek Pulse...
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <audio ref={audioRef} className="hidden" />

      {/* Top Mastek Pulse Header (Matches Page 10 & Page 11 Wireframes) */}
      <header className="bg-[#002180] text-white shadow-md sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
          <div className="flex items-center gap-6">
            <div className="flex items-center gap-2">
              <span className="text-xl font-extrabold tracking-tight">mastek pulse</span>
              <span className="hidden md:inline-block text-xs bg-blue-900 text-blue-200 px-2.5 py-0.5 rounded-full border border-blue-400/30">
                {workspaceMode === 'EMPLOYEE'
                  ? 'Employee workspace'
                  : `Publishing portal / ${currentUser.roles.join(' · ')}`}
              </span>
            </div>

            {/* Workspace Mode Switcher (Employee Web vs Publishing Portal) */}
            <div className="flex bg-blue-950/80 p-1 rounded-lg border border-blue-400/20">
              <button
                onClick={() => setWorkspaceMode('EMPLOYEE')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-colors ${
                  workspaceMode === 'EMPLOYEE' ? 'bg-white text-[#002180]' : 'text-blue-200 hover:text-white'
                }`}
              >
                Employee Web (Page 10)
              </button>
              <button
                onClick={() => setWorkspaceMode('PORTAL')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-colors ${
                  workspaceMode === 'PORTAL' ? 'bg-white text-[#002180]' : 'text-blue-200 hover:text-white'
                }`}
              >
                Publishing Portal (Page 11)
              </button>
            </div>
          </div>

          {/* Right Controls: Persona Switcher & Demo Reset */}
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5">
              <UserCheck className="w-4 h-4 text-blue-200 hidden sm:inline" />
              <label htmlFor="persona-select" className="text-xs text-blue-200 hidden sm:inline">
                Persona:
              </label>
              <select
                id="persona-select"
                value={activePersonaId}
                onChange={(e) => setActivePersonaId(e.target.value)}
                className="bg-blue-900 text-white text-xs font-semibold rounded-lg px-2.5 py-1.5 border border-blue-400/40 focus:outline-none"
              >
                {personas.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.displayName} ({p.region} · {p.roles.filter((r) => r !== 'EMPLOYEE').join('/') || 'Employee'})
                  </option>
                ))}
              </select>
            </div>

            <button
              onClick={() => {
                resetDemoState();
                loadAllData(activePersonaId);
              }}
              title="Reset demo state to fresh pilot seed data"
              className="text-xs bg-blue-900/70 hover:bg-blue-800 text-blue-100 px-2.5 py-1.5 rounded-lg border border-blue-400/30 flex items-center gap-1"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Reset Demo</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Workspace Body */}
      <div className="flex-1">
        {workspaceMode === 'EMPLOYEE' ? (
          <EmployeeWorkspace
            currentUser={currentUser}
            feed={feed}
            radioQueues={radioQueues}
            cultureCasts={cultureCasts}
            requests={requests}
            activeAudio={activeAudio}
            isPlayingAudio={isPlayingAudio}
            audioPositionSec={audioPositionSec}
            onStartAudio={handleStartAudio}
            onTogglePlayPause={handleTogglePlayPause}
            onSeekAudio={handleSeekAudio}
            onRefreshAll={() => loadAllData(activePersonaId)}
            onSwitchPersona={(uid) => setActivePersonaId(uid)}
          />
        ) : (
          <PublishingPortalWorkspace
            currentUser={currentUser}
            portalQueue={portalQueue}
            auditLog={auditLog}
            pilotMetrics={pilotMetrics}
            aiBudget={aiBudget}
            onRefreshAll={() => loadAllData(activePersonaId)}
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

      {/* Persistent Bottom Audio Bar (Matches Proposal Page 10: "Paused: Culture Cast / 04:12 of 12:00 Play Transcript Queue") */}
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
