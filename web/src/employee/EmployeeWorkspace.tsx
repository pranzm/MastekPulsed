import React, { useState } from 'react';
import {
  AiAssistanceResponseDto,
  AppMode,
  AudioChapter,
  AudioListenItemDto,
  ChallengeDto,
  ConnectionRequestDto,
  ContentCardDto,
  HappenedAnswer,
  LeaderboardResponseDto,
  RecognitionProgressDto,
  UserProfileDto,
} from '../api/contracts';
import { pulseApi } from '../api/apiClient';
import {
  Headphones,
  Sparkles,
  CheckCircle2,
  Clock,
  Users,
  Send,
  ArrowLeft,
  Play,
  Pause,
  FileText,
  RotateCcw,
  FastForward,
  AlertCircle,
  Volume2,
  PenTool,
  ShieldCheck,
  Award,
  Heart,
  Flame,
  EyeOff,
  Eye,
} from 'lucide-react';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/employee/EmployeeWorkspace.tsx
 * Journey: Responsive Employee Web Experience (Proposal Page 10) + All 8 Mobile
 *          Wireframe Flows (Proposal Pages 6, 7, 8, 9):
 *   - 01 / Your Campfire (`Pulse` tab): Search stories/interests, category filters
 *     (`For you / Learning / Community / Updates`), factual `WHY THIS?` badge,
 *     and right-hand `Listen` card (`Mastek Radio` & `Culture Cast`).
 *   - 02 / Story Detail: Full story text, optional audio + transcript toggle,
 *     and `Ask me about this` box (`15 minutes / 2 places this week`).
 *   - 03 / Invitation Composer: `Suggest a draft with AI` + deterministic
 *     `Use non-AI template` fallback, human review & edit, and idempotent `Send invitation`.
 *   - 04 / Host Request Inbox (`Connect` tab): `Received / Sent / Accepted`,
 *     1-Transaction `Accept` / `Decline` buttons, and capacity check.
 *   - 05 / Publish (`Contribute` tab): Local draft auto-save (`localStorage`),
 *     optional AI writing help, audience + invitation capacity selector, and
 *     exact revision preview before submitting for review.
 *   - 06 / Private Follow-Up Check-in: `Did the conversation happen? (Yes/No/Not yet)`
 *     and `Was it useful? (Yes/No)`, counting a useful connection ONLY when
 *     both participants confirm `Yes` + `Yes`.
 *   - 07 & 08 / Mastek Radio & Culture Cast (`Listen` tab): Curated commute
 *     queue, on-demand episodes, chapters, transcripts, playback speed, and
 *     cross-device resume.
 * ============================================================================
 */

interface EmployeeWorkspaceProps {
  appMode: AppMode;
  currentUser: UserProfileDto;
  feed: ContentCardDto[];
  radioQueues: AudioListenItemDto[];
  cultureCasts: AudioListenItemDto[];
  requests: ConnectionRequestDto[];
  challenge: ChallengeDto | null;
  myRecognition: RecognitionProgressDto | null;
  leaderboard: LeaderboardResponseDto | null;
  activeAudio: AudioListenItemDto | null;
  isPlayingAudio: boolean;
  audioPositionSec: number;
  onStartAudio: (item: AudioListenItemDto, startAtSec?: number) => void;
  onTogglePlayPause: () => void;
  onSeekAudio: (deltaSec: number) => void;
  onRefreshAll: () => Promise<void>;
  onSwitchPersona: (userId: string) => void;
  onOpenPortal: () => void;
}

export const EmployeeWorkspace: React.FC<EmployeeWorkspaceProps> = ({
  appMode,
  currentUser,
  feed,
  radioQueues,
  cultureCasts,
  requests,
  challenge,
  myRecognition,
  leaderboard,
  activeAudio,
  isPlayingAudio,
  audioPositionSec,
  onStartAudio,
  onTogglePlayPause,
  onSeekAudio,
  onRefreshAll,
  onSwitchPersona,
  onOpenPortal,
}) => {
  const [activeNav, setActiveNav] = useState<'PULSE' | 'CHAMPIONS' | 'LISTEN' | 'CONNECT' | 'CONTRIBUTE' | 'PROFILE'>('PULSE');
  const [categoryFilter, setCategoryFilter] = useState<'FOR_YOU' | 'LEARNING' | 'COMMUNITY' | 'UPDATES'>('FOR_YOU');
  const [searchQuery, setSearchQuery] = useState('');

  // Campfire Weekly Challenge submission state (CF-002 / D4)
  const [challengeTitle, setChallengeTitle] = useState<string>(
    'What weekend badminton taught me about sprint pairing'
  );
  const [challengeBody, setChallengeBody] = useState<string>(
    'Playing mixed doubles every Saturday taught me to communicate early and cover gaps without waiting to be asked. Our Pune engineering pod now uses the same habit during code reviews.'
  );
  const [submittingChallenge, setSubmittingChallenge] = useState<boolean>(false);

  // Wireframe 02 & 03: Selected Story & Invitation Composer state
  const [selectedStory, setSelectedStory] = useState<ContentCardDto | null>(null);
  const [showStoryTranscript, setShowStoryTranscript] = useState(false);
  const [composingInviteFor, setComposingInviteFor] = useState<ContentCardDto | null>(null);
  const [inviteMessage, setInviteMessage] = useState(
    'Hi Daniel, I would love to learn how you made retrospectives more inclusive. Could we compare notes?'
  );
  const [inviteAiAssisted, setInviteAiAssisted] = useState(false);
  const [inviteAiResponse, setInviteAiResponse] = useState<AiAssistanceResponseDto | null>(null);
  const [sendingInvite, setSendingInvite] = useState(false);

  // Update AI Summary state
  const [updateSummaryMap, setUpdateSummaryMap] = useState<Record<string, AiAssistanceResponseDto>>({});

  // Wireframe 04 & 06: Connect Tab & Private Follow-Up Check-in state
  const [connectSubTab, setConnectSubTab] = useState<'RECEIVED' | 'SENT' | 'ACCEPTED'>('RECEIVED');
  const [checkInRequest, setCheckInRequest] = useState<ConnectionRequestDto | null>(null);
  const [checkInHappened, setCheckInHappened] = useState<HappenedAnswer>('YES');
  const [checkInUseful, setCheckInUseful] = useState<boolean>(true);

  // Wireframe 07 & 08: Listen Tab state
  const [listenSubTab, setListenSubTab] = useState<'RADIO' | 'CULTURE_CAST'>('RADIO');
  const [selectedEpisode, setSelectedEpisode] = useState<AudioListenItemDto | null>(cultureCasts[0] || null);
  const [playbackSpeed, setPlaybackSpeed] = useState<1 | 1.25 | 1.5>(1);
  const [showEpisodeTranscript, setShowEpisodeTranscript] = useState(true);

  // Wireframe 05: Contribute / Publish Story state (Backed by localStorage draft!)
  const DRAFT_KEY = `pulse_local_draft_${currentUser.id}`;
  const savedLocalDraft = (() => {
    try {
      const raw = localStorage.getItem(DRAFT_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  })();

  const [draftTitle, setDraftTitle] = useState<string>(savedLocalDraft?.title || 'A better retrospective');
  const [draftBody, setDraftBody] = useState<string>(
    savedLocalDraft?.body ||
      'After our 15-minute connection with UK Delivery, we tried five minutes of silent writing before speaking in our sprint retrospective. Quieter colleagues shared three test automation improvements immediately.'
  );
  const [draftAudience, setDraftAudience] = useState<string>('aud_pilot');
  const [draftIncludeInvite, setDraftIncludeInvite] = useState<boolean>(true);
  const [draftCapacity, setDraftCapacity] = useState<number>(2);
  const [draftExpiresDays, setDraftExpiresDays] = useState<number>(7);
  const [draftAiHelp, setDraftAiHelp] = useState<AiAssistanceResponseDto | null>(null);
  const [showRevisionPreview, setShowRevisionPreview] = useState<boolean>(false);

  // Banner feedback
  const [toastMessage, setToastMessage] = useState<{ type: 'success' | 'error' | 'info'; text: string } | null>(null);

  const showToast = (type: 'success' | 'error' | 'info', text: string) => {
    setToastMessage({ type, text });
    setTimeout(() => setToastMessage(null), 5000);
  };

  const saveDraftLocally = (title: string, body: string) => {
    setDraftTitle(title);
    setDraftBody(body);
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify({ title, body, savedAt: new Date().toISOString() }));
    } catch {
      // ignore
    }
  };

  // Filtered Feed items
  const filteredFeed = feed.filter((item) => {
    if (categoryFilter === 'LEARNING') {
      if (item.contentType !== 'STORY') return false;
      if (!item.tags.includes('team learning') && !item.tags.includes('delivery excellence')) return false;
    } else if (categoryFilter === 'COMMUNITY') {
      if (item.contentType !== 'STORY') return false;
      if (!item.tags.includes('culture') && !item.tags.includes('inclusion') && !item.tags.includes('onboarding'))
        return false;
    } else if (categoryFilter === 'UPDATES') {
      if (item.contentType !== 'UPDATE') return false;
    }
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      return (
        item.title.toLowerCase().includes(q) ||
        item.summary.toLowerCase().includes(q) ||
        item.tags.some((t) => t.toLowerCase().includes(q)) ||
        item.authorName.toLowerCase().includes(q)
      );
    }
    return true;
  });

  const receivedPending = requests.filter((r) => r.hostId === currentUser.id && r.state === 'PENDING');
  const sentRequests = requests.filter((r) => r.requesterId === currentUser.id);
  const acceptedRequests = requests.filter((r) => r.state === 'ACCEPTED');

  const formatTime = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const rem = Math.floor(sec % 60);
    return `${String(mins).padStart(2, '0')}:${String(rem).padStart(2, '0')}`;
  };

  // Handlers for Primary & Supporting Journeys
  const handleOpenStory = (card: ContentCardDto) => {
    setSelectedStory(card);
    setComposingInviteFor(null);
    setShowStoryTranscript(false);
  };

  const handleStartInviteComposer = (card: ContentCardDto) => {
    const firstName = card.authorName.split(' ')[0];
    setComposingInviteFor(card);
    setInviteMessage(
      `Hi ${firstName}, I would love to learn how you approached "${card.title}". Could we compare notes for 15 minutes this week?`
    );
    setInviteAiAssisted(false);
    setInviteAiResponse(null);
  };

  const handleSuggestInviteWithAi = async () => {
    if (!composingInviteFor) return;
    try {
      const res = await pulseApi.requestAiAssist(
        currentUser.id,
        'INVITATION_DRAFT',
        composingInviteFor.id,
        inviteMessage
      );
      setInviteAiResponse(res);
      if (res.aiAvailable && res.aiSuggestion) {
        setInviteMessage(res.aiSuggestion);
        setInviteAiAssisted(true);
        showToast('info', 'AI draft suggested — review and edit before sending.');
      } else {
        setInviteMessage(res.templateFallback);
        setInviteAiAssisted(false);
        showToast('info', 'AI hard cap reached — populated verified non-AI template fallback.');
      }
    } catch (e: any) {
      showToast('error', e.message || 'Could not generate AI draft');
    }
  };

  const handleUseNonAiTemplateForInvite = async () => {
    if (!composingInviteFor) return;
    const firstName = composingInviteFor.authorName.split(' ')[0];
    setInviteMessage(
      `Hi ${firstName}, I read your story "${composingInviteFor.title}" and would love to have a 15-minute conversation to learn from your experience. Could we compare notes this week?`
    );
    setInviteAiAssisted(false);
    showToast('info', 'Applied deterministic non-AI template.');
  };

  const handleSendInvitation = async () => {
    if (!composingInviteFor || !composingInviteFor.invitation) return;
    setSendingInvite(true);
    try {
      const idemKey = `idem-${currentUser.id}-${composingInviteFor.invitation.id}`;
      await pulseApi.sendInvitationRequest(currentUser.id, idemKey, {
        invitationId: composingInviteFor.invitation.id,
        messageText: inviteMessage,
        aiAssisted: inviteAiAssisted,
      });
      await onRefreshAll();
      setComposingInviteFor(null);
      setSelectedStory(null);
      setActiveNav('CONNECT');
      setConnectSubTab('SENT');
      showToast(
        'success',
        `Invitation sent to ${composingInviteFor.authorName}! Tip: Switch persona to ${composingInviteFor.authorName} in the top bar to accept it.`
      );
    } catch (e: any) {
      showToast('error', e.message || 'Failed to send invitation');
    } finally {
      setSendingInvite(false);
    }
  };

  const handleHostDecision = async (req: ConnectionRequestDto, decision: 'ACCEPTED' | 'DECLINED' | 'CANCELLED') => {
    try {
      await pulseApi.updateRequestState(currentUser.id, req.id, decision, req.version);
      await onRefreshAll();
      if (decision === 'ACCEPTED') {
        setConnectSubTab('ACCEPTED');
        showToast(
          'success',
          `Accepted ${req.requesterName}'s invitation (1-Transaction Pessimistic Lock updated capacity; Outbox event queued). Next step: Complete private check-in!`
        );
      } else {
        showToast('info', `Request marked as ${decision}.`);
      }
    } catch (e: any) {
      showToast('error', e.message || 'Could not update request state');
    }
  };

  const handleSaveConfirmation = async () => {
    if (!checkInRequest) return;
    try {
      const updated = await pulseApi.submitConfirmation(
        currentUser.id,
        checkInRequest.id,
        checkInHappened,
        checkInHappened === 'YES' ? checkInUseful : null
      );
      await onRefreshAll();
      setCheckInRequest(null);
      if (updated.bilateralOutcome === 'USEFUL_CONFIRMED') {
        showToast(
          'success',
          'Both participants confirmed Happened=Yes & Useful=Yes! Counted as 1 verified Useful Connection in Pilot Metrics.'
        );
      } else {
        showToast(
          'info',
          `Private feedback saved! Outcome stays UNKNOWN until both ${updated.requesterName} and ${updated.hostName} confirm.`
        );
      }
    } catch (e: any) {
      showToast('error', e.message || 'Failed to save confirmation');
    }
  };

  const handleSummarizeUpdate = async (card: ContentCardDto) => {
    try {
      const res = await pulseApi.requestAiAssist(currentUser.id, 'UPDATE_SUMMARY', card.id, '');
      setUpdateSummaryMap((prev) => ({ ...prev, [card.id]: res }));
    } catch (e: any) {
      showToast('error', e.message || 'Summary failed');
    }
  };

  const handleSubmitContribution = async (submitForReview: boolean) => {
    if (!draftTitle.trim() || !draftBody.trim()) {
      showToast('error', 'Please enter a story title and body.');
      return;
    }
    try {
      await pulseApi.createContribution(currentUser.id, {
        title: draftTitle,
        bodyText: draftBody,
        audienceId: draftAudience,
        includeInvitation: draftIncludeInvite,
        invitationTopic: `Ask me about this: ${draftTitle}`,
        invitationCapacity: draftCapacity,
        invitationExpiresDays: draftExpiresDays,
        submitForReview,
      });
      await onRefreshAll();
      setShowRevisionPreview(false);
      showToast(
        'success',
        submitForReview
          ? 'Revision 1 submitted to the Publishing Portal Review Queue! Switch to Reviewer Priya Nair in the Publishing Portal to approve it.'
          : 'Draft saved to your account.'
      );
    } catch (e: any) {
      showToast('error', e.message || 'Failed to save contribution');
    }
  };

  const handleSubmitChallengeEntry = async () => {
    if (!challenge) return;
    setSubmittingChallenge(true);
    try {
      const idemKey = `idem-ch-${currentUser.id}-${Date.now()}`;
      const entry = await pulseApi.submitChallengeEntry(currentUser.id, challenge.id, idemKey, {
        title: challengeTitle,
        bodyText: challengeBody,
        submitForReview: true,
      });
      await onRefreshAll();
      showToast(
        'success',
        `Submitted Revision ${entry.revisionNumber} ("${entry.title}") to review! Points awarded now: 0 (+20 unlocks only after Priya approves & Marcus publishes).`
      );
    } catch (e: any) {
      showToast('error', e.message || 'Challenge submission failed');
    } finally {
      setSubmittingChallenge(false);
    }
  };

  const handleAppreciateStory = async (card: ContentCardDto) => {
    try {
      const res = await pulseApi.appreciateStory(currentUser.id, card.id);
      await onRefreshAll();
      if (res.alreadyAppreciated) {
        showToast('info', `You already appreciated "${card.title}" (duplicate clicks add 0 points).`);
      } else {
        showToast(
          'success',
          `Appreciated "${card.title}"! You earned +${res.giverPointsDelta} pts; ${card.authorName.split(' ')[0]} earned +${res.receiverPointsDelta} pts.`
        );
      }
    } catch (e: any) {
      showToast('error', e.message || 'Could not appreciate story');
    }
  };

  const handleToggleLeaderboardVisibility = async () => {
    if (!myRecognition) return;
    try {
      const nextShow = !myRecognition.showOnLeaderboard;
      await pulseApi.updateRecognitionPreferences(currentUser.id, nextShow);
      await onRefreshAll();
      showToast(
        'info',
        nextShow
          ? 'Your ranking is now visible on the Community Champions leaderboard.'
          : 'Opted out of public leaderboard — your private points and badges remain intact.'
      );
    } catch (e: any) {
      showToast('error', e.message || 'Failed to update leaderboard preference');
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 pb-28">
      {/* Interactive Demo Walkthrough Banner for Hackathon Judges & Developers */}
      <div className="mb-6 bg-gradient-to-r from-[#002180] to-blue-800 text-white rounded-xl p-4 shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-blue-200">
            <Sparkles className="w-4 h-4" /> Mastek Campfire ({appMode.toUpperCase()} Mode) · Walkthrough Guide
          </div>
          <p className="text-sm mt-1 text-blue-50">
            <strong>Campfire Loop:</strong> Open{' '}
            <button
              onClick={() => setActiveNav('CHAMPIONS')}
              className="underline font-bold text-amber-300 hover:text-white"
            >
              Challenge & Champions
            </button>{' '}
            → Submit as <strong>Asha (0 pts)</strong> → Approve as{' '}
            <button
              onClick={() => {
                onSwitchPersona('usr_priya');
                onOpenPortal();
              }}
              className="underline font-semibold text-white hover:text-blue-200"
            >
              Priya (Reviewer)
            </button>{' '}
            → Publish as{' '}
            <button
              onClick={() => {
                onSwitchPersona('usr_marcus');
                onOpenPortal();
              }}
              className="underline font-semibold text-white hover:text-blue-200"
            >
              Marcus (Publisher)
            </button>{' '}
            (<strong>+20 pts & First Spark</strong>) → Appreciate stories (+5/+5)!
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span className="text-xs bg-blue-900/70 border border-blue-400/30 px-2.5 py-1 rounded-full">
            <strong>{currentUser.displayName}</strong> · {myRecognition?.points ?? 0}/60 pts
          </span>
        </div>
      </div>

      {/* Toast Notification */}
      {toastMessage && (
        <div
          role="status"
          className={`mb-5 p-4 rounded-xl border flex items-center justify-between shadow-sm ${
            toastMessage.type === 'success'
              ? 'bg-emerald-50 border-emerald-300 text-emerald-900'
              : toastMessage.type === 'error'
              ? 'bg-red-50 border-red-300 text-red-900'
              : 'bg-blue-50 border-blue-300 text-blue-900'
          }`}
        >
          <div className="flex items-center gap-2.5 text-sm font-medium">
            <CheckCircle2 className="w-5 h-5 shrink-0" />
            <span>{toastMessage.text}</span>
          </div>
          <button onClick={() => setToastMessage(null)} className="text-xs font-semibold underline ml-4">
            Dismiss
          </button>
        </div>
      )}

      {/* Main Layout matching Proposal Page 10 ("05B / EMPLOYEE WEB WIREFRAME: Your Campfire. At your desk, too.") */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Navigation Sidebar (Desktop) & Top Pills (Mobile) */}
        <aside className="lg:col-span-2">
          <nav aria-label="Employee workspace navigation" className="bg-white rounded-xl border border-slate-200 p-2 shadow-sm">
            <div className="px-3 py-2 text-xs font-bold uppercase tracking-wider text-slate-400">
              Employee Workspace
            </div>
            {(
              [
                { id: 'PULSE', label: 'Your Campfire', badge: null },
                {
                  id: 'CHAMPIONS',
                  label: 'Challenge & Points',
                  badge: `${myRecognition?.points ?? 0} pts`,
                },
                { id: 'LISTEN', label: 'Listen', badge: 'Radio + Cast' },
                {
                  id: 'CONNECT',
                  label: 'Connect',
                  badge: receivedPending.length > 0 ? `${receivedPending.length} pending` : null,
                },
                { id: 'CONTRIBUTE', label: 'Contribute', badge: null },
                { id: 'PROFILE', label: 'Profile', badge: null },
              ] as const
            ).map((tab) => (
              <button
                key={tab.id}
                onClick={() => {
                  setActiveNav(tab.id);
                  setSelectedStory(null);
                  setComposingInviteFor(null);
                }}
                className={`w-full text-left px-3 py-2.5 rounded-lg text-sm font-medium flex items-center justify-between transition-colors mb-1 ${
                  activeNav === tab.id
                    ? 'bg-[#002180] text-white font-semibold shadow-sm'
                    : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                <span>{tab.label}</span>
                {tab.badge && (
                  <span
                    className={`text-[11px] px-2 py-0.5 rounded-full ${
                      activeNav === tab.id ? 'bg-blue-700 text-white' : 'bg-blue-100 text-[#002180] font-semibold'
                    }`}
                  >
                    {tab.badge}
                  </span>
                )}
              </button>
            ))}
          </nav>
        </aside>

        {/* Center Main Content Area */}
        <main className="lg:col-span-7 space-y-5">
          {/* =====================================================================
              TAB: CHALLENGE & COMMUNITY CHAMPIONS (Proposal Pages 16 & 17 · D4 & D5)
             ===================================================================== */}
          {activeNav === 'CHAMPIONS' && (
            <div className="space-y-5">
              {/* Weekly Challenge Card */}
              <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <div>
                    <div className="text-xs font-bold uppercase tracking-wider text-[#002180] flex items-center gap-1.5">
                      <Flame className="w-4 h-4 text-amber-600" /> 10 / Weekly Campfire Challenge ({challenge?.weekStart || '2026-11-16'} UTC)
                    </div>
                    <h1 className="text-2xl font-bold text-slate-900 mt-1">
                      {challenge?.title || 'Beyond My Job Title'}
                    </h1>
                    <p className="text-sm text-slate-600 mt-1">
                      {challenge?.prompt ||
                        'Share a hobby, a lesson, or someone who helped you beyond your immediate role. Participation is voluntary.'}
                    </p>
                  </div>
                  <span className="text-xs font-bold bg-emerald-100 text-emerald-800 px-3 py-1 rounded-full">
                    {challenge?.status || 'ACTIVE'} · Audience: {challenge?.audienceName || 'Colleagues in the Pilot'}
                  </span>
                </div>

                {challenge?.myEntry ? (
                  <div className="bg-blue-50/80 border border-blue-200 rounded-xl p-4 space-y-2 text-xs">
                    <div className="flex items-center justify-between flex-wrap gap-2">
                      <span className="font-bold text-[#002180] text-sm">
                        Your Challenge Entry: "{challenge.myEntry.title}" (Revision {challenge.myEntry.revisionNumber})
                      </span>
                      <span
                        className={`px-2.5 py-0.5 rounded-full font-bold ${
                          challenge.myEntry.status === 'PUBLISHED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-amber-100 text-amber-900'
                        }`}
                      >
                        Status: {challenge.myEntry.status} · Points Awarded: +{challenge.myEntry.pointsAwarded}
                      </span>
                    </div>
                    <p className="text-slate-700">{challenge.myEntry.bodyText}</p>
                    {challenge.myEntry.reviewerNotes && (
                      <p className="text-amber-900 font-semibold">
                        Reviewer Feedback: {challenge.myEntry.reviewerNotes}
                      </p>
                    )}
                    {challenge.myEntry.status !== 'PUBLISHED' && (
                      <div className="pt-2 flex flex-wrap gap-2">
                        <button
                          onClick={() => {
                            onSwitchPersona('usr_priya');
                            onOpenPortal();
                          }}
                          className="bg-[#002180] text-white px-3 py-1.5 rounded-lg font-semibold"
                        >
                          Switch to Priya (Reviewer) in Portal →
                        </button>
                        <button
                          onClick={() => {
                            onSwitchPersona('usr_marcus');
                            onOpenPortal();
                          }}
                          className="bg-emerald-700 text-white px-3 py-1.5 rounded-lg font-semibold"
                        >
                          Switch to Marcus (Publisher) in Portal →
                        </button>
                      </div>
                    )}
                  </div>
                ) : null}

                {(!challenge?.myEntry || challenge.myEntry.status === 'DRAFT') && (
                  <div className="border-t border-slate-200 pt-4 space-y-3">
                    <div className="text-xs font-bold uppercase tracking-wider text-slate-600">
                      {challenge?.myEntry?.status === 'DRAFT'
                        ? 'Resubmit Updated Revision for Review'
                        : 'Submit Your Challenge Story (Submission earns 0 pts; Publication unlocks +20 pts & First Spark)'}
                    </div>
                    <input
                      type="text"
                      value={challengeTitle}
                      onChange={(e) => setChallengeTitle(e.target.value)}
                      placeholder="Story title (5–100 characters)..."
                      className="w-full rounded-lg border border-slate-300 px-3.5 py-2 text-sm font-semibold"
                    />
                    <textarea
                      rows={3}
                      value={challengeBody}
                      onChange={(e) => setChallengeBody(e.target.value)}
                      placeholder="Share your story (30–1,200 characters)..."
                      className="w-full rounded-lg border border-slate-300 p-3.5 text-sm"
                    />
                    <button
                      type="button"
                      disabled={submittingChallenge}
                      onClick={handleSubmitChallengeEntry}
                      className="bg-[#002180] hover:bg-blue-900 disabled:opacity-50 text-white px-5 py-2.5 rounded-lg text-sm font-semibold shadow-sm"
                    >
                      {submittingChallenge
                        ? 'Submitting...'
                        : challenge?.myEntry?.status === 'DRAFT'
                        ? 'Resubmit Revision for Review'
                        : 'Submit Challenge Story for Review'}
                    </button>
                  </div>
                )}
              </div>

              {/* Personal Progress & Badges Card */}
              {myRecognition && (
                <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
                  <div className="flex items-center justify-between flex-wrap gap-2">
                    <div>
                      <h2 className="text-xl font-bold text-[#002180] flex items-center gap-2">
                        <Award className="w-5 h-5" /> Your Weekly Progress ({myRecognition.displayName})
                      </h2>
                      <p className="text-xs text-slate-600">
                        Week of {myRecognition.weekStart} ({myRecognition.timezone}) · Maximum 60 points/week. Points
                        never represent employee appraisal performance.
                      </p>
                    </div>
                    <div className="text-2xl font-extrabold text-[#002180]">
                      {myRecognition.points} <span className="text-xs font-normal text-slate-500">/ 60 pts</span>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                    <div className="p-3.5 rounded-xl bg-blue-50/70 border border-blue-200">
                      <div className="font-bold text-slate-600">Published Contribution</div>
                      <div className="text-xl font-extrabold text-[#002180] mt-1">
                        +{myRecognition.contributionPoints} / 20 pts
                      </div>
                      <div className="text-slate-500 mt-0.5">Once per week after human review & publication</div>
                    </div>
                    <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-600">Give Appreciation (+5)</div>
                      <div className="text-xl font-extrabold text-slate-900 mt-1">
                        +{myRecognition.givingPoints} / 15 pts
                      </div>
                      <div className="text-slate-500 mt-0.5">
                        {myRecognition.givenCount} stories appreciated ({myRecognition.distinctTeamsSupported} teams)
                      </div>
                    </div>
                    <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-600">Receive Appreciation (+5)</div>
                      <div className="text-xl font-extrabold text-slate-900 mt-1">
                        +{myRecognition.receivingPoints} / 25 pts
                      </div>
                      <div className="text-slate-500 mt-0.5">
                        From {myRecognition.receivedDistinctCount} distinct colleagues
                      </div>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center justify-between gap-3 pt-2 border-t border-slate-100">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-xs font-bold text-slate-600">Unlocked Milestones:</span>
                      {myRecognition.badges.length === 0 ? (
                        <span className="text-xs text-slate-500">
                          None yet (Publish a story for First Spark; appreciate 3 teams for Connection Maker; receive 5
                          colleague appreciations for Community Favourite)
                        </span>
                      ) : (
                        myRecognition.badges.map((b) => (
                          <span
                            key={b}
                            className="bg-amber-100 text-amber-900 border border-amber-300 px-3 py-1 rounded-full text-xs font-bold"
                          >
                            ★ {b}
                          </span>
                        ))
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* Community Champions Leaderboard Card (Page 17 · CF-007) */}
              {leaderboard && (
                <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
                  <div className="flex items-center justify-between flex-wrap gap-3">
                    <div>
                      <h2 className="text-xl font-bold text-[#002180]">
                        11 / Community Champions (Top 5 Optional Leaderboard)
                      </h2>
                      <p className="text-xs text-slate-600">
                        Audience: {leaderboard.audienceId} · Week starting {leaderboard.weekStart} ({leaderboard.timezone}
                        ). Tied scores share a rank.
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={handleToggleLeaderboardVisibility}
                      className="px-3.5 py-2 rounded-lg border border-slate-300 hover:bg-slate-100 text-xs font-semibold flex items-center gap-1.5"
                    >
                      {myRecognition?.showOnLeaderboard ? (
                        <>
                          <EyeOff className="w-3.5 h-3.5 text-slate-700" /> Hide my public ranking
                        </>
                      ) : (
                        <>
                          <Eye className="w-3.5 h-3.5 text-[#002180]" /> Show my public ranking
                        </>
                      )}
                    </button>
                  </div>

                  <div className="space-y-2">
                    {leaderboard.entries.map((row) => (
                      <div
                        key={row.userId}
                        className={`p-3.5 rounded-xl border flex items-center justify-between gap-3 text-sm ${
                          row.userId === currentUser.id
                            ? 'bg-blue-50/80 border-[#002180] font-semibold'
                            : 'bg-slate-50 border-slate-200'
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <span className="w-7 h-7 rounded-full bg-[#002180] text-white text-xs font-bold flex items-center justify-center">
                            #{row.rank}
                          </span>
                          <div>
                            <div className="font-bold text-slate-900">
                              {row.displayName}{' '}
                              <span className="text-xs font-normal text-slate-500">
                                ({row.functionName} · {row.region})
                              </span>
                            </div>
                            {row.badges.length > 0 && (
                              <div className="flex flex-wrap gap-1.5 mt-1">
                                {row.badges.map((b) => (
                                  <span
                                    key={b}
                                    className="text-[11px] bg-amber-100 text-amber-900 px-2 py-0.5 rounded-full font-semibold"
                                  >
                                    ★ {b}
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                        <div className="text-base font-extrabold text-[#002180]">{row.points} pts</div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* =====================================================================
              TAB 1: YOUR CAMPFIRE (Wireframes 01, 02, 03 & Page 10 Employee Web)
             ===================================================================== */}
          {activeNav === 'PULSE' && !selectedStory && !composingInviteFor && (
            <>
              {/* Compact Weekly Challenge Callout Card */}
              {challenge && (
                <div className="bg-amber-50/90 border border-amber-300 rounded-xl p-4 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div>
                    <div className="text-xs font-bold uppercase tracking-wider text-amber-900 flex items-center gap-1">
                      <Flame className="w-4 h-4 text-amber-600" /> Weekly Challenge: {challenge.title}
                    </div>
                    <p className="text-xs text-amber-950 mt-0.5">
                      {challenge.myEntry
                        ? `Your entry "${challenge.myEntry.title}" is ${challenge.myEntry.status} (+${challenge.myEntry.pointsAwarded} pts).`
                        : 'Share a story beyond your job title! Publishing earns +20 pts & First Spark.'}{' '}
                      Your weekly score: <strong>{myRecognition?.points ?? 0}/60 pts</strong>.
                    </p>
                  </div>
                  <button
                    onClick={() => setActiveNav('CHAMPIONS')}
                    className="bg-[#002180] hover:bg-blue-900 text-white px-3.5 py-2 rounded-lg text-xs font-bold shrink-0"
                  >
                    Open Challenge & Leaderboard →
                  </button>
                </div>
              )}

              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div>
                    <h1 className="text-2xl font-bold text-[#002180]">
                      Good morning, {currentUser.displayName.split(' ')[0]}
                    </h1>
                    <p className="text-sm text-slate-600 mt-0.5">
                      Your interests:{' '}
                      <span className="font-medium text-slate-800">{currentUser.interests.join(' / ')}</span>
                    </p>
                  </div>
                  <button
                    onClick={() => setActiveNav('PROFILE')}
                    className="text-xs font-semibold text-[#002180] bg-blue-50 hover:bg-blue-100 px-3 py-2 rounded-lg border border-blue-200 self-start"
                  >
                    Manage interests & frequency
                  </button>
                </div>

                {/* Search & Category Tabs (Wireframe 01 - Page 6) */}
                <div className="mt-4 flex flex-col sm:flex-row gap-3">
                  <input
                    type="search"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Search stories or interests (e.g., retrospective, Java 21, WCAG)..."
                    aria-label="Search stories or interests"
                    className="flex-1 rounded-lg border border-slate-300 px-3.5 py-2 text-sm focus:border-[#002180]"
                  />
                </div>

                <div className="mt-3 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
                  {(
                    [
                      { id: 'FOR_YOU', label: 'For you' },
                      { id: 'LEARNING', label: 'Learning' },
                      { id: 'COMMUNITY', label: 'Community' },
                      { id: 'UPDATES', label: 'Bite-sized updates' },
                    ] as const
                  ).map((cat) => (
                    <button
                      key={cat.id}
                      onClick={() => setCategoryFilter(cat.id)}
                      className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-colors ${
                        categoryFilter === cat.id
                          ? 'bg-[#002180] text-white'
                          : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                      }`}
                    >
                      {cat.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Empty Feed Key State (Proposal Page 6: "Empty feed: change interests or search") */}
              {filteredFeed.length === 0 ? (
                <div className="bg-white rounded-xl border border-slate-200 p-8 text-center">
                  <p className="text-base font-semibold text-slate-800">No stories match your current filter.</p>
                  <p className="text-sm text-slate-600 mt-1">
                    Try clearing your search query or updating your explicit interests in Profile.
                  </p>
                  <button
                    onClick={() => {
                      setSearchQuery('');
                      setCategoryFilter('FOR_YOU');
                    }}
                    className="mt-4 bg-[#002180] text-white px-4 py-2 rounded-lg text-xs font-semibold"
                  >
                    Reset filters
                  </button>
                </div>
              ) : (
                filteredFeed.map((card) => (
                  <article
                    key={card.id}
                    className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm hover:border-blue-300 transition-all"
                  >
                    <div className="flex items-center justify-between text-xs font-semibold text-slate-500 uppercase tracking-wider">
                      <span>
                        {card.contentType === 'UPDATE'
                          ? `COMPANY UPDATE ${card.durationSeconds ? `/ ${Math.round(card.durationSeconds / 60)} MIN` : ''}`
                          : `COLLEAGUE STORY · ${card.authorName} / ${card.authorFunction} / ${card.authorRegion}`}
                      </span>
                      <span className="bg-slate-100 text-slate-700 px-2 py-0.5 rounded text-[11px] normal-case">
                        Audience: {card.audienceName}
                      </span>
                    </div>

                    <h2 className="text-xl font-bold text-slate-900 mt-2">{card.title}</h2>
                    <p className="text-sm text-slate-700 mt-1.5 leading-relaxed">{card.summary}</p>

                    {/* Grounded Factual Explanation Badge (Page 4 & Page 6: "WHY THIS? Your interest: team learning") */}
                    <div className="mt-3 bg-blue-50/80 border border-blue-200 rounded-lg px-3 py-2 text-xs text-[#002180] font-medium flex items-center justify-between flex-wrap gap-2">
                      <span>{card.factualExplanation}</span>
                      {card.invitation && (
                        <span
                          className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${
                            card.invitation.statusLabel === 'OPEN'
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-amber-100 text-amber-800'
                          }`}
                        >
                          {card.invitation.statusLabel === 'OPEN'
                            ? `${card.invitation.durationMinutes} min conversation / ${card.invitation.remainingPlaces} places available`
                            : `Invitation ${card.invitation.statusLabel}`}
                        </span>
                      )}
                    </div>

                    {/* Optional AI Summary box for Company Updates */}
                    {card.contentType === 'UPDATE' && updateSummaryMap[card.id] && (
                      <div className="mt-3 bg-slate-50 border border-slate-200 rounded-lg p-3.5 text-xs space-y-2">
                        <div className="font-semibold text-[#002180] flex items-center gap-1.5">
                          <Sparkles className="w-4 h-4" />
                          <span>{updateSummaryMap[card.id].aiLabel}</span>
                        </div>
                        <p className="whitespace-pre-line text-slate-800">
                          {updateSummaryMap[card.id].aiAvailable && updateSummaryMap[card.id].aiSuggestion
                            ? updateSummaryMap[card.id].aiSuggestion
                            : updateSummaryMap[card.id].templateFallback}
                        </p>
                      </div>
                    )}

                    <div className="mt-4 flex flex-wrap items-center gap-3">
                      <button
                        onClick={() => handleOpenStory(card)}
                        className="bg-[#002180] hover:bg-blue-900 text-white px-4 py-2 rounded-lg text-sm font-semibold transition-colors"
                      >
                        {card.contentType === 'UPDATE' ? 'Read the brief >' : 'Read story >'}
                      </button>

                      {card.contentType === 'STORY' && (
                        <button
                          onClick={() => handleAppreciateStory(card)}
                          disabled={card.authorId === currentUser.id}
                          title={
                            card.authorId === currentUser.id
                              ? 'Self-appreciation is disallowed'
                              : 'Give colleague appreciation (+5 pts up to 3 stories/week)'
                          }
                          className="bg-amber-50 hover:bg-amber-100 disabled:opacity-50 text-amber-900 border border-amber-300 px-3 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5"
                        >
                          <Heart className="w-3.5 h-3.5 text-amber-600" />
                          {card.authorId === currentUser.id ? 'Your story' : 'Appreciate (+5)'}
                        </button>
                      )}

                      {card.contentType === 'UPDATE' && (
                        <button
                          onClick={() => handleSummarizeUpdate(card)}
                          className="bg-slate-100 hover:bg-slate-200 text-slate-800 px-3 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5"
                        >
                          <Sparkles className="w-3.5 h-3.5 text-[#002180]" />
                          Summarize with AI (or template)
                        </button>
                      )}

                      {card.officialSource && (
                        <span className="text-xs text-slate-500 ml-auto">{card.officialSource}</span>
                      )}
                    </div>
                  </article>
                ))
              )}
            </>
          )}

          {/* =====================================================================
              WIREFRAME 02: STORY DETAIL VIEW (Proposal Page 6)
             ===================================================================== */}
          {activeNav === 'PULSE' && selectedStory && !composingInviteFor && (
            <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-5">
              <button
                onClick={() => setSelectedStory(null)}
                className="text-sm font-semibold text-[#002180] flex items-center gap-1.5 hover:underline"
              >
                <ArrowLeft className="w-4 h-4" /> &lt; Story (Back to Your Campfire)
              </button>

              <div className="border-b border-slate-200 pb-4">
                <div className="text-xs font-semibold text-slate-500">
                  {selectedStory.authorName} / {selectedStory.authorFunction} / {selectedStory.authorRegion}
                </div>
                <h1 className="text-2xl font-bold text-slate-900 mt-1">{selectedStory.title}</h1>
                <div className="mt-2 text-xs text-[#002180] bg-blue-50 inline-block px-2.5 py-1 rounded font-medium">
                  {selectedStory.factualExplanation}
                </div>
              </div>

              <div className="prose prose-slate max-w-none text-slate-800 whitespace-pre-line text-sm leading-relaxed">
                {selectedStory.bodyText}
              </div>

              {/* Optional Audio & Transcript Block (Wireframe 02: "Optional audio [ play ] / Transcript") */}
              {selectedStory.hasAudio && (
                <div className="bg-slate-50 rounded-xl border border-slate-200 p-4 space-y-3">
                  <div className="flex items-center justify-between flex-wrap gap-2">
                    <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                      <Volume2 className="w-4 h-4 text-[#002180]" />
                      <span>Optional audio ({selectedStory.durationSeconds}s)</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() =>
                          onStartAudio({
                            id: selectedStory.id,
                            contentType: 'CULTURE_CAST',
                            title: selectedStory.title,
                            summary: selectedStory.summary,
                            bodyText: selectedStory.bodyText,
                            hostId: selectedStory.authorId,
                            hostName: selectedStory.authorName,
                            durationSeconds: selectedStory.durationSeconds || 95,
                            resumePositionSeconds: 0,
                            transcriptText: selectedStory.transcriptText || selectedStory.bodyText,
                            chaptersJson: '[]',
                            signedAudioUrl: selectedStory.signedAudioUrl || '',
                            publishedVersion: 1,
                          })
                        }
                        className="bg-[#002180] text-white px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1"
                      >
                        <Play className="w-3.5 h-3.5" /> Play Audio
                      </button>
                      <button
                        onClick={() => setShowStoryTranscript(!showStoryTranscript)}
                        className="bg-white border border-slate-300 text-slate-700 px-3 py-1.5 rounded-lg text-xs font-semibold"
                      >
                        {showStoryTranscript ? 'Hide Transcript' : 'Transcript'}
                      </button>
                    </div>
                  </div>
                  {showStoryTranscript && (
                    <div className="text-xs text-slate-700 bg-white p-3 rounded-lg border border-slate-200">
                      <strong>Verified Transcript:</strong> {selectedStory.transcriptText}
                    </div>
                  )}
                </div>
              )}

              {/* Invitation Offer Card (Wireframe 02: "Ask me about this — 15 minutes / 2 places this week") */}
              {selectedStory.invitation && (
                <div className="bg-blue-50/70 rounded-xl border border-blue-200 p-5 space-y-3">
                  <h2 className="text-lg font-bold text-[#002180]">Ask me about this</h2>
                  <p className="text-sm text-slate-800 font-medium">{selectedStory.invitation.topicLabel}</p>
                  <div className="text-xs text-slate-600 space-y-1">
                    <div>
                      <strong>Duration & Capacity:</strong> {selectedStory.invitation.durationMinutes} minutes /{' '}
                      {selectedStory.invitation.remainingPlaces} places available this week (Total capacity:{' '}
                      {selectedStory.invitation.capacity})
                    </div>
                    <div>
                      <strong>Audience:</strong> {selectedStory.audienceName}
                    </div>
                  </div>

                  {/* Key State (Page 6): "A full or expired offer disables the invitation action and explains why." */}
                  {selectedStory.invitation.statusLabel !== 'OPEN' ? (
                    <div className="bg-amber-50 border border-amber-300 text-amber-900 rounded-lg p-3 text-xs font-medium flex items-center gap-2">
                      <AlertCircle className="w-4 h-4 shrink-0" />
                      <span>
                        Invitation action disabled: This offer is currently{' '}
                        <strong>{selectedStory.invitation.statusLabel}</strong> (all{' '}
                        {selectedStory.invitation.capacity} places for this week are filled or the host has paused
                        availability).
                      </span>
                    </div>
                  ) : selectedStory.authorId === currentUser.id ? (
                    <div className="bg-slate-100 text-slate-700 rounded-lg p-3 text-xs">
                      You are the host of this story. Switch to <strong>Asha Patel</strong> to send an invitation, or
                      open <strong>Connect</strong> to view incoming requests.
                    </div>
                  ) : (
                    <button
                      onClick={() => handleStartInviteComposer(selectedStory)}
                      className="w-full sm:w-auto bg-[#002180] hover:bg-blue-900 text-white px-5 py-2.5 rounded-lg text-sm font-semibold shadow-sm"
                    >
                      Write an invitation
                    </button>
                  )}
                </div>
              )}
            </div>
          )}

          {/* =====================================================================
              WIREFRAME 03: INVITATION COMPOSER (Proposal Page 7)
             ===================================================================== */}
          {activeNav === 'PULSE' && composingInviteFor && (
            <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-5">
              <button
                onClick={() => setComposingInviteFor(null)}
                className="text-sm font-semibold text-[#002180] flex items-center gap-1.5 hover:underline"
              >
                <ArrowLeft className="w-4 h-4" /> &lt; Invite {composingInviteFor.authorName.split(' ')[0]}
              </button>

              <div>
                <h1 className="text-xl font-bold text-slate-900">
                  Invite {composingInviteFor.authorName} ({composingInviteFor.authorFunction} /{' '}
                  {composingInviteFor.authorRegion})
                </h1>
                <p className="text-sm font-semibold text-[#002180] mt-1">
                  About: {composingInviteFor.invitation?.topicLabel || composingInviteFor.title}
                </p>
              </div>

              <div>
                <label htmlFor="invite-msg" className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-2">
                  YOUR MESSAGE
                </label>
                <textarea
                  id="invite-msg"
                  rows={4}
                  value={inviteMessage}
                  onChange={(e) => setInviteMessage(e.target.value)}
                  className="w-full rounded-xl border border-slate-300 p-3.5 text-sm text-slate-900 focus:border-[#002180]"
                />
                {inviteAiAssisted && (
                  <span className="inline-flex items-center gap-1 mt-1.5 text-[11px] font-semibold text-[#002180] bg-blue-50 px-2.5 py-0.5 rounded-full border border-blue-200">
                    <Sparkles className="w-3 h-3" /> Labeled AI-assisted draft (Human review required)
                  </span>
                )}
              </div>

              {/* Optional AI Drafting + Explicit Non-AI Template Fallback (Supporting Journey C) */}
              <div className="flex flex-wrap gap-3">
                <button
                  type="button"
                  onClick={handleSuggestInviteWithAi}
                  className="bg-blue-50 hover:bg-blue-100 text-[#002180] border border-blue-200 px-4 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5"
                >
                  <Sparkles className="w-4 h-4" /> Suggest a draft with AI
                </button>
                <button
                  type="button"
                  onClick={handleUseNonAiTemplateForInvite}
                  className="bg-slate-100 hover:bg-slate-200 text-slate-700 px-4 py-2 rounded-lg text-xs font-semibold"
                >
                  Use non-AI standard template
                </button>
              </div>

              {inviteAiResponse && (
                <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs text-slate-700 space-y-1">
                  <div className="font-semibold text-[#002180]">{inviteAiResponse.aiLabel}</div>
                  <div>
                    AI Budget Gate: <strong>{inviteAiResponse.budgetStatus.actionsUsed}</strong> /{' '}
                    {inviteAiResponse.budgetStatus.actionCap} actions ({inviteAiResponse.budgetStatus.percentUsed}% ·{' '}
                    {inviteAiResponse.budgetStatus.alertLevel})
                  </div>
                </div>
              )}

              <p className="text-xs text-slate-600">
                Review and edit before sending. {composingInviteFor.authorName.split(' ')[0]} can accept or decline.
                No calendar meeting is automatically booked.
              </p>

              <button
                type="button"
                disabled={sendingInvite}
                onClick={handleSendInvitation}
                className="w-full bg-[#002180] hover:bg-blue-900 disabled:opacity-50 text-white py-3 rounded-xl text-sm font-semibold flex items-center justify-center gap-2 shadow-sm"
              >
                <Send className="w-4 h-4" />
                {sendingInvite ? 'Sending (Idempotent)...' : 'Send invitation'}
              </button>

              <p className="text-xs text-slate-500 text-center">
                After sending: Pending until {composingInviteFor.authorName.split(' ')[0]} replies. Safe retries use a
                caller-scoped Idempotency-Key.
              </p>
            </div>
          )}

          {/* =====================================================================
              TAB 2: LISTEN — MASTEK RADIO & CULTURE CAST (Wireframes 07 & 08 - Page 9)
             ===================================================================== */}
          {activeNav === 'LISTEN' && (
            <div className="space-y-5">
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
                <div className="flex items-center justify-between flex-wrap gap-3">
                  <div>
                    <h1 className="text-2xl font-bold text-[#002180]">Listen wherever you are.</h1>
                    <p className="text-sm text-slate-600">
                      Prerecorded pilot audio with transcripts, chapters, and cross-device playback resume. Playback
                      never starts automatically.
                    </p>
                  </div>
                  <div className="flex gap-2 bg-slate-100 p-1 rounded-lg">
                    <button
                      onClick={() => setListenSubTab('RADIO')}
                      className={`px-3.5 py-1.5 rounded-md text-xs font-semibold ${
                        listenSubTab === 'RADIO' ? 'bg-[#002180] text-white' : 'text-slate-700'
                      }`}
                    >
                      07 / Mastek Radio
                    </button>
                    <button
                      onClick={() => setListenSubTab('CULTURE_CAST')}
                      className={`px-3.5 py-1.5 rounded-md text-xs font-semibold ${
                        listenSubTab === 'CULTURE_CAST' ? 'bg-[#002180] text-white' : 'text-slate-700'
                      }`}
                    >
                      08 / Culture Cast ({cultureCasts.length})
                    </button>
                  </div>
                </div>
              </div>

              {/* WIREFRAME 07: MASTEK RADIO (Proposal Page 9) */}
              {listenSubTab === 'RADIO' &&
                radioQueues.map((radio) => {
                  const chapters: AudioChapter[] = (() => {
                    try {
                      return JSON.parse(radio.chaptersJson);
                    } catch {
                      return [];
                    }
                  })();
                  return (
                    <div key={radio.id} className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-5">
                      <div className="bg-blue-50/70 border border-blue-200 rounded-xl p-5 space-y-3">
                        <div className="text-xs font-bold uppercase tracking-wider text-[#002180]">
                          YOUR COMMUTE EDITION
                        </div>
                        <h2 className="text-2xl font-bold text-slate-900">{radio.title}</h2>
                        <p className="text-sm text-slate-700">{radio.summary}</p>
                        <div className="pt-2 flex flex-wrap gap-3 items-center">
                          <button
                            onClick={() => onStartAudio(radio, radio.resumePositionSeconds)}
                            className="bg-[#002180] hover:bg-blue-900 text-white px-5 py-2.5 rounded-lg text-sm font-semibold flex items-center gap-2 shadow-sm"
                          >
                            <Play className="w-4 h-4" /> Play Mastek Radio
                          </button>
                          {radio.resumePositionSeconds > 0 && (
                            <span className="text-xs text-[#002180] font-semibold">
                              Saved resume position: {formatTime(radio.resumePositionSeconds)}
                            </span>
                          )}
                        </div>
                      </div>

                      <div>
                        <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3">UP NEXT</h3>
                        <div className="space-y-2">
                          {chapters.map((ch) => (
                            <button
                              key={ch.index}
                              onClick={() => onStartAudio(radio, ch.startSeconds)}
                              className="w-full text-left p-3 rounded-lg border border-slate-200 hover:bg-blue-50 flex items-center justify-between text-sm"
                            >
                              <span className="font-semibold text-slate-800">{ch.title}</span>
                              <span className="text-xs text-slate-500">{ch.durationMinutes} min</span>
                            </button>
                          ))}
                        </div>
                      </div>

                      <div className="border-t border-slate-200 pt-4">
                        <div className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
                          Verified Commute Queue Transcript
                        </div>
                        <pre className="text-xs text-slate-700 bg-slate-50 p-4 rounded-lg border border-slate-200 whitespace-pre-wrap font-sans leading-relaxed">
                          {radio.transcriptText}
                        </pre>
                      </div>
                    </div>
                  );
                })}

              {/* WIREFRAME 08: CULTURE CAST ON DEMAND (Proposal Page 9) */}
              {listenSubTab === 'CULTURE_CAST' && (
                <div className="space-y-4">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    {cultureCasts.map((ep) => (
                      <button
                        key={ep.id}
                        onClick={() => setSelectedEpisode(ep)}
                        className={`text-left p-4 rounded-xl border transition-all ${
                          selectedEpisode?.id === ep.id
                            ? 'bg-blue-50 border-[#002180] shadow-sm'
                            : 'bg-white border-slate-200 hover:border-blue-300'
                        }`}
                      >
                        <div className="text-[11px] font-bold uppercase text-[#002180]">
                          {Math.round(ep.durationSeconds / 60)} MIN · ON DEMAND
                        </div>
                        <div className="font-bold text-sm text-slate-900 mt-1 line-clamp-2">{ep.title}</div>
                        <div className="text-xs text-slate-500 mt-1">
                          Resume: {formatTime(ep.resumePositionSeconds)}
                        </div>
                      </button>
                    ))}
                  </div>

                  {selectedEpisode && (
                    <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-5">
                      <div>
                        <div className="text-xs font-bold uppercase tracking-wider text-slate-500">
                          ON DEMAND / CULTURE CAST
                        </div>
                        <h2 className="text-2xl font-bold text-slate-900 mt-1">{selectedEpisode.title}</h2>
                        <p className="text-sm text-slate-700 mt-1">{selectedEpisode.summary}</p>
                      </div>

                      {/* Player Scrub & Controls Box (Wireframe 08: "02:10 ---- 08:00 Back 15s [ Play ] Forward 15s") */}
                      <div className="bg-slate-50 rounded-xl border border-slate-200 p-4 space-y-3">
                        <div className="flex items-center justify-between text-xs font-mono text-slate-700">
                          <span>
                            {activeAudio?.id === selectedEpisode.id
                              ? formatTime(audioPositionSec)
                              : formatTime(selectedEpisode.resumePositionSeconds)}
                          </span>
                          <div className="flex-1 mx-4 h-2 bg-slate-200 rounded-full overflow-hidden">
                            <div
                              className="h-full bg-[#002180]"
                              style={{
                                width: `${Math.min(
                                  100,
                                  Math.round(
                                    (((activeAudio?.id === selectedEpisode.id
                                      ? audioPositionSec
                                      : selectedEpisode.resumePositionSeconds) || 0) /
                                      selectedEpisode.durationSeconds) *
                                      100
                                  )
                                )}%`,
                              }}
                            />
                          </div>
                          <span>{formatTime(selectedEpisode.durationSeconds)}</span>
                        </div>

                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => onSeekAudio(-15)}
                              className="px-3 py-1.5 rounded-lg border border-slate-300 bg-white text-xs font-semibold flex items-center gap-1"
                            >
                              <RotateCcw className="w-3.5 h-3.5" /> Back 15s
                            </button>
                            <button
                              onClick={() =>
                                activeAudio?.id === selectedEpisode.id
                                  ? onTogglePlayPause()
                                  : onStartAudio(selectedEpisode, selectedEpisode.resumePositionSeconds)
                              }
                              className="px-5 py-2 rounded-lg bg-[#002180] text-white text-xs font-semibold flex items-center gap-1.5"
                            >
                              {activeAudio?.id === selectedEpisode.id && isPlayingAudio ? (
                                <>
                                  <Pause className="w-4 h-4" /> Pause
                                </>
                              ) : (
                                <>
                                  <Play className="w-4 h-4" /> Play / Resume (
                                  {formatTime(selectedEpisode.resumePositionSeconds)})
                                </>
                              )}
                            </button>
                            <button
                              onClick={() => onSeekAudio(15)}
                              className="px-3 py-1.5 rounded-lg border border-slate-300 bg-white text-xs font-semibold flex items-center gap-1"
                            >
                              Forward 15s <FastForward className="w-3.5 h-3.5" />
                            </button>
                          </div>

                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => setShowEpisodeTranscript(!showEpisodeTranscript)}
                              className="px-3 py-1.5 rounded-lg bg-white border border-slate-300 text-xs font-semibold"
                            >
                              Transcript
                            </button>
                            <button
                              onClick={() =>
                                setPlaybackSpeed((prev) => (prev === 1 ? 1.25 : prev === 1.25 ? 1.5 : 1))
                              }
                              className="px-3 py-1.5 rounded-lg bg-white border border-slate-300 text-xs font-semibold"
                            >
                              Speed: {playbackSpeed}x
                            </button>
                          </div>
                        </div>
                      </div>

                      {/* Chapters */}
                      <div>
                        <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">Chapters</h3>
                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                          {(() => {
                            try {
                              return (JSON.parse(selectedEpisode.chaptersJson) as AudioChapter[]).map((ch) => (
                                <button
                                  key={ch.index}
                                  onClick={() => onStartAudio(selectedEpisode, ch.startSeconds)}
                                  className="text-left p-2.5 rounded-lg border border-slate-200 hover:bg-blue-50 text-xs"
                                >
                                  <div className="font-semibold text-slate-800">{ch.title}</div>
                                  <div className="text-slate-500 mt-0.5">Starts at {formatTime(ch.startSeconds)}</div>
                                </button>
                              ));
                            } catch {
                              return null;
                            }
                          })()}
                        </div>
                      </div>

                      {showEpisodeTranscript && (
                        <div>
                          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
                            Episode Transcript
                          </h3>
                          <pre className="text-xs text-slate-700 bg-slate-50 p-4 rounded-lg border border-slate-200 whitespace-pre-wrap font-sans leading-relaxed">
                            {selectedEpisode.transcriptText}
                          </pre>
                        </div>
                      )}

                      {/* Related Story Link (Wireframe 08: "Continue the conversation through a related story or voluntary invitation -> Explore the related story") */}
                      {selectedEpisode.relatedStoryId && (
                        <div className="border-t border-slate-200 pt-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                          <p className="text-xs text-slate-600">
                            Continue the conversation through a related story or voluntary invitation.
                          </p>
                          <button
                            onClick={() => {
                              const st = feed.find((f) => f.id === selectedEpisode.relatedStoryId) || feed[0];
                              if (st) {
                                setActiveNav('PULSE');
                                handleOpenStory(st);
                              }
                            }}
                            className="bg-[#002180] hover:bg-blue-900 text-white px-4 py-2.5 rounded-lg text-xs font-semibold shrink-0"
                          >
                            Explore the related story
                          </button>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* =====================================================================
              TAB 3: CONNECT — HOST REQUEST INBOX & PRIVATE CHECK-IN (Wireframes 04 & 06)
             ===================================================================== */}
          {activeNav === 'CONNECT' && (
            <div className="space-y-5">
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div>
                    <h1 className="text-2xl font-bold text-[#002180]">Connections</h1>
                    <p className="text-sm text-slate-600">
                      Voluntary 15-minute introductions across boundaries. After acceptance, agree a time using your
                      existing workplace tools.
                    </p>
                  </div>
                  <div className="flex gap-2 bg-slate-100 p-1 rounded-lg self-start">
                    <button
                      onClick={() => setConnectSubTab('RECEIVED')}
                      className={`px-3 py-1.5 rounded-md text-xs font-semibold ${
                        connectSubTab === 'RECEIVED' ? 'bg-[#002180] text-white' : 'text-slate-700'
                      }`}
                    >
                      Received ({receivedPending.length})
                    </button>
                    <button
                      onClick={() => setConnectSubTab('SENT')}
                      className={`px-3 py-1.5 rounded-md text-xs font-semibold ${
                        connectSubTab === 'SENT' ? 'bg-[#002180] text-white' : 'text-slate-700'
                      }`}
                    >
                      Sent ({sentRequests.length})
                    </button>
                    <button
                      onClick={() => setConnectSubTab('ACCEPTED')}
                      className={`px-3 py-1.5 rounded-md text-xs font-semibold ${
                        connectSubTab === 'ACCEPTED' ? 'bg-[#002180] text-white' : 'text-slate-700'
                      }`}
                    >
                      Accepted ({acceptedRequests.length})
                    </button>
                  </div>
                </div>
              </div>

              {/* WIREFRAME 06: PRIVATE FOLLOW-UP CHECK-IN MODAL/PANEL (Proposal Page 8) */}
              {checkInRequest && (
                <div className="bg-blue-50/90 rounded-xl border-2 border-[#002180] p-6 shadow-md space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-[#002180]">
                      06 / PRIVATE FOLLOW-UP CHECK-IN
                    </span>
                    <button
                      onClick={() => setCheckInRequest(null)}
                      className="text-xs font-semibold text-slate-600 underline"
                    >
                      Skip for now
                    </button>
                  </div>

                  <h2 className="text-xl font-bold text-slate-900">
                    Your conversation with{' '}
                    {checkInRequest.requesterId === currentUser.id
                      ? checkInRequest.hostName
                      : checkInRequest.requesterName}
                  </h2>
                  <p className="text-xs text-slate-600">
                    Only you and{' '}
                    {checkInRequest.requesterId === currentUser.id
                      ? checkInRequest.hostName
                      : checkInRequest.requesterName}{' '}
                    can access this feedback. You can skip it. Count usefulness only after both participants confirm;
                    skipped or missing feedback stays unknown.
                  </p>

                  <div className="space-y-2">
                    <label className="block text-sm font-bold text-slate-800">Did the conversation happen?</label>
                    <div className="flex gap-3">
                      {(['YES', 'NO', 'NOT_YET'] as const).map((opt) => (
                        <button
                          key={opt}
                          type="button"
                          onClick={() => setCheckInHappened(opt)}
                          className={`px-4 py-2 rounded-lg text-xs font-semibold border ${
                            checkInHappened === opt
                              ? 'bg-[#002180] text-white border-[#002180]'
                              : 'bg-white text-slate-700 border-slate-300'
                          }`}
                        >
                          {opt === 'YES' ? '(•) Yes' : opt === 'NO' ? '( ) No' : '( ) Not yet'}
                        </button>
                      ))}
                    </div>
                  </div>

                  {checkInHappened === 'YES' && (
                    <div className="space-y-2">
                      <label className="block text-sm font-bold text-slate-800">Was it useful?</label>
                      <div className="flex gap-3">
                        <button
                          type="button"
                          onClick={() => setCheckInUseful(true)}
                          className={`px-4 py-2 rounded-lg text-xs font-semibold border ${
                            checkInUseful
                              ? 'bg-[#002180] text-white border-[#002180]'
                              : 'bg-white text-slate-700 border-slate-300'
                          }`}
                        >
                          (•) Yes, it was useful
                        </button>
                        <button
                          type="button"
                          onClick={() => setCheckInUseful(false)}
                          className={`px-4 py-2 rounded-lg text-xs font-semibold border ${
                            !checkInUseful
                              ? 'bg-[#002180] text-white border-[#002180]'
                              : 'bg-white text-slate-700 border-slate-300'
                          }`}
                        >
                          ( ) No
                        </button>
                      </div>
                    </div>
                  )}

                  <div className="pt-2 flex items-center gap-4">
                    <button
                      type="button"
                      onClick={handleSaveConfirmation}
                      className="bg-[#002180] hover:bg-blue-900 text-white px-5 py-2.5 rounded-lg text-sm font-semibold"
                    >
                      Save feedback
                    </button>
                    <button
                      type="button"
                      onClick={() => setCheckInRequest(null)}
                      className="text-xs font-semibold text-slate-600 hover:underline"
                    >
                      Skip for now
                    </button>
                  </div>
                </div>
              )}

              {/* Request Cards List */}
              {(() => {
                const listToRender =
                  connectSubTab === 'RECEIVED'
                    ? requests.filter((r) => r.hostId === currentUser.id)
                    : connectSubTab === 'SENT'
                    ? sentRequests
                    : acceptedRequests;

                if (listToRender.length === 0) {
                  return (
                    <div className="bg-white rounded-xl border border-slate-200 p-8 text-center space-y-2">
                      <p className="text-base font-semibold text-slate-800">
                        No {connectSubTab.toLowerCase()} connection requests yet.
                      </p>
                      <p className="text-xs text-slate-500">
                        {connectSubTab === 'RECEIVED'
                          ? 'Tip: If Asha sent an invitation to Daniel, switch active persona to Daniel Wright in the top bar to see it in Received!'
                          : 'Explore a story on Your Campfire and click "Write an invitation" to connect.'}
                      </p>
                    </div>
                  );
                }

                return listToRender.map((req) => (
                  <div key={req.id} className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm space-y-3">
                    <div className="flex items-center justify-between flex-wrap gap-2">
                      <span className="text-xs font-bold uppercase tracking-wider text-[#002180]">
                        {req.state} / From {req.requesterName} ({req.requesterRegion}) → To {req.hostName} (
                        {req.hostRegion})
                      </span>
                      <span className="text-xs bg-slate-100 text-slate-700 px-2.5 py-0.5 rounded-full font-semibold">
                        {req.remainingPlaces} places remaining this week · v{req.version}
                      </span>
                    </div>

                    <h3 className="text-lg font-bold text-slate-900">About: {req.storyTitle}</h3>
                    <p className="text-sm text-slate-700 bg-slate-50 p-3.5 rounded-lg border border-slate-200">
                      "{req.messageText}"
                    </p>

                    {/* Host Accept / Decline Controls (Wireframe 04 - Page 7) */}
                    {req.state === 'PENDING' && req.hostId === currentUser.id && (
                      <div className="pt-2 flex items-center gap-3">
                        <button
                          onClick={() => handleHostDecision(req, 'ACCEPTED')}
                          className="bg-[#002180] hover:bg-blue-900 text-white px-5 py-2 rounded-lg text-sm font-semibold shadow-sm"
                        >
                          Accept
                        </button>
                        <button
                          onClick={() => handleHostDecision(req, 'DECLINED')}
                          className="bg-slate-100 hover:bg-slate-200 text-slate-800 px-5 py-2 rounded-lg text-sm font-semibold"
                        >
                          Decline
                        </button>
                      </div>
                    )}

                    {/* Requester Cancel Control */}
                    {req.state === 'PENDING' && req.requesterId === currentUser.id && (
                      <div className="pt-2 flex items-center justify-between">
                        <span className="text-xs text-slate-500">
                          Pending until {req.hostName.split(' ')[0]} replies.
                        </span>
                        <button
                          onClick={() => handleHostDecision(req, 'CANCELLED')}
                          className="text-xs font-semibold text-red-700 hover:underline"
                        >
                          Cancel request
                        </button>
                      </div>
                    )}

                    {/* Accepted State & Bilateral Feedback Status */}
                    {req.state === 'ACCEPTED' && (
                      <div className="pt-2 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                        <div className="space-y-1">
                          <div className="text-xs text-slate-600">
                            After acceptance, agree a time using your existing workplace tools.
                          </div>
                          <div className="text-xs font-semibold">
                            Bilateral Verification Status:{' '}
                            <span
                              className={`px-2 py-0.5 rounded-full ${
                                req.bilateralOutcome === 'USEFUL_CONFIRMED'
                                  ? 'bg-emerald-100 text-emerald-800'
                                  : 'bg-amber-100 text-amber-800'
                              }`}
                            >
                              {req.bilateralOutcome === 'USEFUL_CONFIRMED'
                                ? '✓ USEFUL CONNECTION CONFIRMED BY BOTH'
                                : `${req.bilateralOutcome} (${req.confirmationsCount}/2 confirmed)`}
                            </span>
                          </div>
                        </div>

                        <button
                          onClick={() => {
                            setCheckInRequest(req);
                            setCheckInHappened(req.myConfirmation?.happened || 'YES');
                            setCheckInUseful(req.myConfirmation?.useful ?? true);
                          }}
                          className="bg-[#002180] hover:bg-blue-900 text-white px-4 py-2 rounded-lg text-xs font-semibold shrink-0"
                        >
                          {req.myConfirmation ? 'Update private check-in' : 'Complete quick check-in'}
                        </button>
                      </div>
                    )}
                  </div>
                ));
              })()}
            </div>
          )}

          {/* =====================================================================
              TAB 4: CONTRIBUTE / PUBLISH A STORY (Wireframe 05 - Page 8)
             ===================================================================== */}
          {activeNav === 'CONTRIBUTE' && (
            <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-5">
              <div className="flex items-center justify-between border-b border-slate-200 pb-4">
                <div>
                  <h1 className="text-2xl font-bold text-[#002180]">Publish a story</h1>
                  <p className="text-xs text-emerald-700 font-semibold mt-0.5">
                    ✓ Draft automatically saved on this device (localStorage offline resilience)
                  </p>
                </div>
                <span className="text-xs bg-blue-50 text-[#002180] px-3 py-1 rounded-full font-semibold">
                  Revision 1 Preview
                </span>
              </div>

              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1.5">
                  STORY TITLE
                </label>
                <input
                  type="text"
                  value={draftTitle}
                  onChange={(e) => saveDraftLocally(e.target.value, draftBody)}
                  className="w-full rounded-lg border border-slate-300 px-3.5 py-2 text-sm font-semibold"
                />
              </div>

              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1.5">
                  YOUR STORY
                </label>
                <textarea
                  rows={5}
                  value={draftBody}
                  onChange={(e) => saveDraftLocally(draftTitle, e.target.value)}
                  placeholder="What changed? What did you learn? Share a useful, specific moment."
                  className="w-full rounded-lg border border-slate-300 p-3.5 text-sm"
                />
              </div>

              {/* Optional AI Writing Help with Fallback (Wireframe 05: "+ Optional media / AI writing help") */}
              <div className="bg-slate-50 rounded-xl border border-slate-200 p-4 space-y-3">
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <span className="text-xs font-bold text-slate-700">+ Optional media / AI writing help</span>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={async () => {
                        try {
                          const res = await pulseApi.requestAiAssist(
                            currentUser.id,
                            'STORY_POLISH',
                            'story_01',
                            draftBody
                          );
                          setDraftAiHelp(res);
                        } catch (e: any) {
                          showToast('error', e.message);
                        }
                      }}
                      className="bg-blue-50 hover:bg-blue-100 text-[#002180] border border-blue-200 px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1"
                    >
                      <Sparkles className="w-3.5 h-3.5" /> Polish draft with AI
                    </button>
                    <button
                      type="button"
                      onClick={() =>
                        saveDraftLocally(
                          draftTitle,
                          'What changed?\nWe tried silent note-taking for 4 minutes at the start of our retro.\n\nWhat did we learn?\nQuieter team members shared concrete deployment fixes right away.'
                        )
                      }
                      className="bg-white border border-slate-300 text-slate-700 px-3 py-1.5 rounded-lg text-xs font-semibold"
                    >
                      Insert non-AI structure template
                    </button>
                  </div>
                </div>

                {draftAiHelp && (
                  <div className="bg-white rounded-lg border border-blue-200 p-3 text-xs space-y-2">
                    <div className="font-semibold text-[#002180]">{draftAiHelp.aiLabel}</div>
                    <p className="whitespace-pre-line text-slate-700">
                      {draftAiHelp.aiAvailable && draftAiHelp.aiSuggestion
                        ? draftAiHelp.aiSuggestion
                        : draftAiHelp.templateFallback}
                    </p>
                    <div className="flex gap-2 pt-1">
                      <button
                        type="button"
                        onClick={() => {
                          saveDraftLocally(
                            draftTitle,
                            (draftAiHelp.aiAvailable && draftAiHelp.aiSuggestion) || draftAiHelp.templateFallback
                          );
                          setDraftAiHelp(null);
                        }}
                        className="bg-[#002180] text-white px-3 py-1 rounded text-xs font-semibold"
                      >
                        Apply suggestion to editor
                      </button>
                      <button
                        type="button"
                        onClick={() => setDraftAiHelp(null)}
                        className="text-slate-600 underline text-xs"
                      >
                        Keep my original text
                      </button>
                    </div>
                  </div>
                )}
              </div>

              {/* Audience & Voluntary Invitation Settings (Wireframe 05 - Page 8) */}
              <div className="bg-blue-50/60 rounded-xl border border-blue-200 p-4 grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Audience</label>
                  <select
                    value={draftAudience}
                    onChange={(e) => setDraftAudience(e.target.value)}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs bg-white font-medium"
                  >
                    <option value="aud_pilot">Pilot colleagues (aud_pilot)</option>
                    <option value="aud_all">All employees (aud_all)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Voluntary Invitation</label>
                  <select
                    value={draftIncludeInvite ? 'YES' : 'NO'}
                    onChange={(e) => setDraftIncludeInvite(e.target.value === 'YES')}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs bg-white font-medium"
                  >
                    <option value="YES">Ask me about this (15 min)</option>
                    <option value="NO">No invitation attached</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Capacity & Expiry</label>
                  <div className="flex gap-2">
                    <select
                      value={draftCapacity}
                      onChange={(e) => setDraftCapacity(Number(e.target.value))}
                      className="w-1/2 rounded-lg border border-slate-300 px-2 py-2 text-xs bg-white font-medium"
                    >
                      <option value={1}>Cap: 1</option>
                      <option value={2}>Cap: 2</option>
                      <option value={3}>Cap: 3</option>
                    </select>
                    <select
                      value={draftExpiresDays}
                      onChange={(e) => setDraftExpiresDays(Number(e.target.value))}
                      className="w-1/2 rounded-lg border border-slate-300 px-2 py-2 text-xs bg-white font-medium"
                    >
                      <option value={7}>7 days</option>
                      <option value={14}>14 days</option>
                    </select>
                  </div>
                </div>
              </div>

              {/* Exact Revision Review Preview Gate (Supporting Journey B: "Submit a contribution -> review the exact revision") */}
              {showRevisionPreview && (
                <div className="bg-slate-50 border-2 border-[#002180] rounded-xl p-4 space-y-2 text-xs">
                  <div className="font-bold text-[#002180] uppercase">
                    Exact Revision 1 Submission Preview (Read Before Confirming)
                  </div>
                  <div>
                    <strong>Title:</strong> {draftTitle}
                  </div>
                  <div>
                    <strong>Target Audience:</strong>{' '}
                    {draftAudience === 'aud_all' ? 'All employees' : 'Colleagues in the pilot'}
                  </div>
                  <div>
                    <strong>Invitation Offer:</strong>{' '}
                    {draftIncludeInvite
                      ? `Ask me about this · Capacity: ${draftCapacity} places / Expires: ${draftExpiresDays} days`
                      : 'None'}
                  </div>
                  <p className="text-slate-700 whitespace-pre-line pt-1 border-t border-slate-200">{draftBody}</p>
                </div>
              )}

              <div className="flex flex-wrap gap-3 pt-2">
                {!showRevisionPreview ? (
                  <button
                    type="button"
                    onClick={() => setShowRevisionPreview(true)}
                    className="bg-[#002180] hover:bg-blue-900 text-white px-5 py-2.5 rounded-lg text-sm font-semibold"
                  >
                    Review exact revision before submitting
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => handleSubmitContribution(true)}
                    className="bg-emerald-700 hover:bg-emerald-800 text-white px-5 py-2.5 rounded-lg text-sm font-semibold"
                  >
                    Confirm & Submit Revision 1 for Review
                  </button>
                )}

                <button
                  type="button"
                  onClick={() => handleSubmitContribution(false)}
                  className="bg-slate-100 hover:bg-slate-200 text-slate-800 px-4 py-2.5 rounded-lg text-sm font-semibold"
                >
                  Save as Draft
                </button>
              </div>
            </div>
          )}

          {/* =====================================================================
              TAB 5: PROFILE & PARTICIPANT CONTROLS (Proposal Page 4 & 12)
             ===================================================================== */}
          {activeNav === 'PROFILE' && (
            <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-5">
              <h1 className="text-2xl font-bold text-[#002180]">Profile & Participant Controls</h1>
              <p className="text-sm text-slate-600">
                Minimum collection: Opt-in profile fields, explicit interests, notification frequency, and host capacity
                pause controls. No employee appraisal scores or public popularity rankings.
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200 text-sm">
                <div>
                  <span className="text-xs text-slate-500 block">Name & Function</span>
                  <strong>
                    {currentUser.displayName} · {currentUser.functionName} ({currentUser.region})
                  </strong>
                </div>
                <div>
                  <span className="text-xs text-slate-500 block">Scoped Roles</span>
                  <strong>{currentUser.roles.join(', ')}</strong>
                </div>
                <div className="sm:col-span-2">
                  <span className="text-xs text-slate-500 block">Permitted Audiences (Enforced on API & DB)</span>
                  <strong>{currentUser.audiences.join(', ')}</strong>
                </div>
              </div>

              <div className="space-y-2">
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-600">
                  Explicit Interests (Used for transparent rules-based recommendations)
                </label>
                <div className="flex flex-wrap gap-2">
                  {[
                    'team learning',
                    'innovation',
                    'culture',
                    'delivery excellence',
                    'cloud architecture',
                    'accessibility',
                    'security',
                    'inclusion',
                    'onboarding',
                  ].map((tag) => {
                    const active = currentUser.interests.includes(tag);
                    return (
                      <button
                        key={tag}
                        type="button"
                        onClick={async () => {
                          const next = active
                            ? currentUser.interests.filter((i) => i !== tag)
                            : [...currentUser.interests, tag];
                          await pulseApi.updatePreferences(currentUser.id, { interests: next });
                          await onRefreshAll();
                        }}
                        className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${
                          active
                            ? 'bg-[#002180] text-white border-[#002180]'
                            : 'bg-white text-slate-700 border-slate-300'
                        }`}
                      >
                        {active ? `✓ ${tag}` : `+ ${tag}`}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="pt-3 border-t border-slate-200 flex items-center justify-between flex-wrap gap-3">
                <div>
                  <div className="text-sm font-bold text-slate-800">Host Availability Control (Proposal Page 12)</div>
                  <div className="text-xs text-slate-600">
                    Pause new incoming colleague invitations whenever your weekly workload is full.
                  </div>
                </div>
                <button
                  type="button"
                  onClick={async () => {
                    await pulseApi.updatePreferences(currentUser.id, { hostPaused: !currentUser.hostPaused });
                    await onRefreshAll();
                    showToast(
                      'info',
                      !currentUser.hostPaused
                        ? 'Your host availability is now PAUSED.'
                        : 'Your host availability is now OPEN.'
                    );
                  }}
                  className={`px-4 py-2 rounded-lg text-xs font-semibold ${
                    currentUser.hostPaused ? 'bg-amber-600 text-white' : 'bg-slate-200 text-slate-800'
                  }`}
                >
                  {currentUser.hostPaused ? 'Host Status: PAUSED (Click to Resume)' : 'Pause Host Availability'}
                </button>
              </div>
            </div>
          )}
        </main>

        {/* Right Column: Dedicated "Listen" Commute & Culture Cast Card (Matches Proposal Page 10 Wireframe!) */}
        <aside className="lg:col-span-3 space-y-5">
          <div className="bg-[#002180] text-white rounded-xl p-5 shadow-md space-y-5">
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-bold">Listen</h2>
              <Headphones className="w-5 h-5 text-blue-200" />
            </div>

            {radioQueues[0] && (
              <div className="bg-blue-900/60 border border-blue-400/30 rounded-lg p-3.5 space-y-2">
                <div className="text-xs font-bold uppercase tracking-wider text-blue-200">Mastek Radio</div>
                <div className="text-sm font-semibold">Your commute queue (12 min)</div>
                <p className="text-xs text-blue-100 line-clamp-2">{radioQueues[0].title}</p>
                <button
                  onClick={() => {
                    onStartAudio(radioQueues[0], radioQueues[0].resumePositionSeconds);
                    setActiveNav('LISTEN');
                    setListenSubTab('RADIO');
                  }}
                  className="w-full bg-white text-[#002180] hover:bg-blue-50 py-2 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5"
                >
                  <Play className="w-3.5 h-3.5" /> Play Mastek Radio
                </button>
              </div>
            )}

            {cultureCasts[0] && (
              <div className="bg-blue-900/60 border border-blue-400/30 rounded-lg p-3.5 space-y-2">
                <div className="text-xs font-bold uppercase tracking-wider text-blue-200">Culture Cast</div>
                <div className="text-sm font-semibold">
                  Episode 03 / {Math.round(cultureCasts[0].durationSeconds / 60)} min
                </div>
                <p className="text-xs text-blue-100">{cultureCasts[0].title}</p>
                <div className="text-[11px] text-blue-200">
                  Saved position: {formatTime(cultureCasts[0].resumePositionSeconds)} of{' '}
                  {formatTime(cultureCasts[0].durationSeconds)}
                </div>
                <button
                  onClick={() => {
                    onStartAudio(cultureCasts[0], cultureCasts[0].resumePositionSeconds);
                    setActiveNav('LISTEN');
                    setListenSubTab('CULTURE_CAST');
                  }}
                  className="w-full bg-blue-600 hover:bg-blue-500 text-white py-2 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 border border-blue-300/40"
                >
                  <Play className="w-3.5 h-3.5" /> Choose an episode and press Play
                </button>
              </div>
            )}
          </div>

          {/* Trust & Privacy Guardrail Card (Proposal Page 4) */}
          <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm space-y-2 text-xs text-slate-600">
            <div className="font-bold text-slate-900 flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4 text-[#002180]" /> Trust & Privacy Boundaries
            </div>
            <p>
              • Recommendations show verified reasons grounded in your explicit interests and host capacity—never a
              compatibility score.
            </p>
            <p>
              • Feedback is private to the two participants and counted as useful only when both confirm{' '}
              <code>Happened = Yes</code> and <code>Useful = Yes</code>.
            </p>
          </div>
        </aside>
      </div>
    </div>
  );
};
