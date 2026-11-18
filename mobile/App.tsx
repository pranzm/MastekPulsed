// @refresh reset
import React, { useEffect, useRef, useState } from 'react';
import {
  Animated,
  AppState,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: mobile/App.tsx
 * Journey: Android & iOS Native Mobile App (React Native + Expo SDK 58 + TS)
 *          with:
 *   1. Pre-Filled Demo Sign-In Flow (`Campfire@2026!` password stored, 6 persona
 *      cards with custom native portrait avatars, corporate email, and roles).
 *   2. Rich Persona Portrait Avatars across Login, Header, Feed, Leaderboard,
 *      Connections, Publishing Portal, and Profile.
 *   3. Full 8-Tab Parity with Web (`Home/Campfire`, `Points`, `Focus`, `Listen`,
 *      `Connect`, `Publish`, `Portal`, `Profile`) + Persistent Bottom Audio Mini-Player,
 *      dedicated Header `Home` & `Logout` controls, and Proposal Wireframes (01–14).
 * ============================================================================
 */

type BottomTab =
  | 'PULSE'
  | 'CHAMPIONS'
  | 'PRODUCTIVE'
  | 'LISTEN'
  | 'CONNECT'
  | 'CONTRIBUTE'
  | 'PORTAL'
  | 'PROFILE';

type PersonaId =
  | 'usr_asha'
  | 'usr_daniel'
  | 'usr_priya'
  | 'usr_marcus'
  | 'usr_elena'
  | 'usr_rohan';

interface PersonaMeta {
  id: PersonaId;
  displayName: string;
  email: string;
  jobTitle: string;
  functionName: string;
  region: string;
  roles: string[];
  audiences: string[];
  bio: string;
  bg: string;
  skin: string;
  hair: string;
  shirt: string;
  glasses?: boolean;
  beard?: boolean;
}

const DEMO_PASSWORD = 'Campfire@2026!';

const PERSONAS: Record<PersonaId, PersonaMeta> = {
  usr_asha: {
    id: 'usr_asha',
    displayName: 'Asha Patel',
    email: 'asha.patel@mastek.com',
    jobTitle: 'Senior Full-Stack Engineer',
    functionName: 'Engineering',
    region: 'INDIA',
    roles: ['EMPLOYEE', 'CREATOR'],
    audiences: ['aud_all', 'aud_pilot'],
    bio: 'Builds cloud-native delivery pipelines in Pune. Passionate about sprint pairing, test automation, and inclusive team retrospectives.',
    bg: '#1d4ed8',
    skin: '#d99b72',
    hair: '#1e1b18',
    shirt: '#0f766e',
  },
  usr_daniel: {
    id: 'usr_daniel',
    displayName: 'Daniel Wright',
    email: 'daniel.wright@mastek.com',
    jobTitle: 'Principal Delivery Lead',
    functionName: 'Delivery',
    region: 'UK',
    roles: ['EMPLOYEE', 'CREATOR'],
    audiences: ['aud_all', 'aud_pilot', 'aud_uk_delivery'],
    bio: 'Leads NHS & UK public-sector delivery squads in Leeds. Hosts 15-minute Campfire chats on psychological safety and agile ceremonies.',
    bg: '#0369a1',
    skin: '#f1c29b',
    hair: '#5c3a21',
    shirt: '#1e293b',
    glasses: true,
    beard: true,
  },
  usr_priya: {
    id: 'usr_priya',
    displayName: 'Priya Nair',
    email: 'priya.nair@mastek.com',
    jobTitle: 'Engineering Practice Reviewer',
    functionName: 'People & Culture',
    region: 'INDIA',
    roles: ['EMPLOYEE', 'CREATOR', 'REVIEWER'],
    audiences: ['aud_all', 'aud_pilot'],
    bio: 'Stewards editorial quality and revision accuracy in the Mastek Campfire Publishing Portal across India and Global pilot cohorts.',
    bg: '#6d28d9',
    skin: '#c98860',
    hair: '#171717',
    shirt: '#9333ea',
    glasses: true,
  },
  usr_marcus: {
    id: 'usr_marcus',
    displayName: 'Marcus Vance',
    email: 'marcus.vance@mastek.com',
    jobTitle: 'Global Internal Comms Publisher',
    functionName: 'Communications',
    region: 'US',
    roles: ['EMPLOYEE', 'PUBLISHER', 'MODERATOR'],
    audiences: ['aud_all', 'aud_pilot', 'aud_uk_delivery'],
    bio: 'Curates Mastek Radio, Culture Cast episodes, and weekly Campfire challenge publications from Dallas.',
    bg: '#047857',
    skin: '#8d5524',
    hair: '#111827',
    shirt: '#0f172a',
    beard: true,
  },
  usr_elena: {
    id: 'usr_elena',
    displayName: 'Elena Rostova',
    email: 'elena.rostova@mastek.com',
    jobTitle: 'Cloud Platform Architect & Admin',
    functionName: 'Architecture',
    region: 'UK',
    roles: ['EMPLOYEE', 'CREATOR', 'ADMIN'],
    audiences: ['aud_all', 'aud_pilot', 'aud_uk_delivery'],
    bio: 'Oversees platform trust boundaries, AI budget guardrails, and audience scoping for the UK and Global engineering teams.',
    bg: '#b45309',
    skin: '#f5cfa8',
    hair: '#92400e',
    shirt: '#1e3a8a',
  },
  usr_rohan: {
    id: 'usr_rohan',
    displayName: 'Rohan Deshmukh',
    email: 'rohan.deshmukh@mastek.com',
    jobTitle: 'Customer Success Lead',
    functionName: 'Customer Success',
    region: 'INDIA',
    roles: ['EMPLOYEE', 'CREATOR'],
    audiences: ['aud_all', 'aud_pilot'],
    bio: 'Partners with enterprise clients in Mumbai and connects cross-regional delivery teams through Campfire stories.',
    bg: '#0284c7',
    skin: '#d29166',
    hair: '#1f2937',
    shirt: '#0369a1',
  },
};

/**
 * Native Portrait Avatar component built with pure React Native primitives so
 * every persona renders an illustrated portrait photo (face, hair, attire,
 * glasses/beard) with zero external network dependencies in Expo Go.
 */
function PersonaPortrait({
  personaId,
  size = 44,
}: {
  personaId: PersonaId | string;
  size?: number;
}) {
  const p = PERSONAS[(personaId as PersonaId) || 'usr_asha'] || PERSONAS.usr_asha;
  const headW = size * 0.44;
  const headH = size * 0.48;
  const shoulderW = size * 0.76;
  const shoulderH = size * 0.34;

  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        backgroundColor: p.bg,
        overflow: 'hidden',
        alignItems: 'center',
        justifyContent: 'flex-end',
        borderWidth: 2,
        borderColor: '#ffffff',
      }}
    >
      {/* Subtle highlight */}
      <View
        style={{
          position: 'absolute',
          top: -size * 0.1,
          right: -size * 0.1,
          width: size * 0.45,
          height: size * 0.45,
          borderRadius: size * 0.25,
          backgroundColor: 'rgba(255,255,255,0.14)',
        }}
      />
      {/* Hair back */}
      <View
        style={{
          position: 'absolute',
          top: size * 0.14,
          width: headW * 1.18,
          height: headH * 0.95,
          borderRadius: headW * 0.55,
          backgroundColor: p.hair,
        }}
      />
      {/* Head / Face */}
      <View
        style={{
          position: 'absolute',
          top: size * 0.2,
          width: headW,
          height: headH,
          borderRadius: headW / 2,
          backgroundColor: p.skin,
          alignItems: 'center',
        }}
      >
        {/* Hair front fringe */}
        <View
          style={{
            width: headW * 1.04,
            height: headH * 0.32,
            borderTopLeftRadius: headW / 2,
            borderTopRightRadius: headW / 2,
            backgroundColor: p.hair,
          }}
        />
        {/* Eyes */}
        <View
          style={{
            flexDirection: 'row',
            justifyContent: 'space-between',
            width: headW * 0.52,
            marginTop: headH * 0.12,
          }}
        >
          <View
            style={{
              width: Math.max(3, size * 0.06),
              height: Math.max(3, size * 0.06),
              borderRadius: 4,
              backgroundColor: '#0f172a',
            }}
          />
          <View
            style={{
              width: Math.max(3, size * 0.06),
              height: Math.max(3, size * 0.06),
              borderRadius: 4,
              backgroundColor: '#0f172a',
            }}
          />
        </View>
        {/* Optional Glasses */}
        {p.glasses && (
          <View
            style={{
              position: 'absolute',
              top: headH * 0.38,
              flexDirection: 'row',
              width: headW * 0.76,
              justifyContent: 'space-between',
            }}
          >
            <View
              style={{
                width: headW * 0.33,
                height: headH * 0.22,
                borderWidth: 1.2,
                borderColor: '#1e293b',
                borderRadius: 3,
              }}
            />
            <View
              style={{
                width: headW * 0.33,
                height: headH * 0.22,
                borderWidth: 1.2,
                borderColor: '#1e293b',
                borderRadius: 3,
              }}
            />
          </View>
        )}
        {/* Beard or Smile */}
        {p.beard ? (
          <View
            style={{
              position: 'absolute',
              bottom: 0,
              width: headW * 0.86,
              height: headH * 0.3,
              borderBottomLeftRadius: headW / 2,
              borderBottomRightRadius: headW / 2,
              backgroundColor: p.hair,
              opacity: 0.85,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <View
              style={{
                width: headW * 0.3,
                height: 2,
                backgroundColor: '#ffffff',
                borderRadius: 2,
              }}
            />
          </View>
        ) : (
          <View
            style={{
              marginTop: headH * 0.14,
              width: headW * 0.34,
              height: Math.max(2, size * 0.045),
              borderRadius: 3,
              backgroundColor: '#991b1b',
            }}
          />
        )}
      </View>
      {/* Shoulders / Attire */}
      <View
        style={{
          width: shoulderW,
          height: shoulderH,
          borderTopLeftRadius: shoulderW / 2,
          borderTopRightRadius: shoulderW / 2,
          backgroundColor: p.shirt,
          alignItems: 'center',
        }}
      >
        <View
          style={{
            width: shoulderW * 0.26,
            height: shoulderH * 0.45,
            backgroundColor: '#f8fafc',
            borderBottomLeftRadius: 8,
            borderBottomRightRadius: 8,
          }}
        />
      </View>
    </View>
  );
}

interface StoryItem {
  id: string;
  kind: 'STORY' | 'UPDATE';
  authorId: PersonaId;
  authorName: string;
  authorFunction: string;
  authorRegion: string;
  audienceName: string;
  category: 'FOR_YOU' | 'LEARNING' | 'COMMUNITY' | 'UPDATES';
  title: string;
  summary: string;
  bodyText: string;
  whyThis: string;
  hasAudio?: boolean;
  durationSec?: number;
  transcriptText?: string;
  inviteOpen?: boolean;
  invitePlaces?: number;
  status: 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'PUBLISHED' | 'WITHDRAWN';
  revisionNumber: number;
  approvedBy?: PersonaId | null;
  appreciatedBy: PersonaId[];
  aiSummary?: string;
}

const INITIAL_STORIES: StoryItem[] = [
  {
    id: 'story_01',
    kind: 'STORY',
    authorId: 'usr_daniel',
    authorName: 'Daniel Wright',
    authorFunction: 'Delivery',
    authorRegion: 'UK',
    audienceName: 'Colleagues in the Pilot',
    category: 'LEARNING',
    title: 'A retrospective everyone can contribute to',
    summary:
      'Starting with 4 minutes of silent writing helped quieter engineers shape our sprint actions before the loudest voices took over.',
    bodyText:
      'In our Leeds delivery squad, sprint retrospectives used to be dominated by the two most vocal people on the call.\n\nLast month we changed one habit: every retrospective starts with 4 minutes of silent writing on three prompts. Quieter engineers immediately surfaced our best test-automation fix of the quarter.',
    whyThis: 'WHY THIS? Your interest: team learning · 15 min conversation / 2 places available',
    hasAudio: true,
    durationSec: 95,
    transcriptText:
      '00:00 Daniel Wright: When we switched to four minutes of silent writing at the start of retro, the quality of sprint actions changed overnight...',
    inviteOpen: true,
    invitePlaces: 2,
    status: 'PUBLISHED',
    revisionNumber: 1,
    approvedBy: 'usr_priya',
    appreciatedBy: [],
  },
  {
    id: 'story_02',
    kind: 'STORY',
    authorId: 'usr_rohan',
    authorName: 'Rohan Deshmukh',
    authorFunction: 'Customer Success',
    authorRegion: 'INDIA',
    audienceName: 'Colleagues in the Pilot',
    category: 'COMMUNITY',
    title: 'How a 10-minute shadow call unblocked our Mumbai handoff',
    summary:
      'Inviting one QA engineer to listen in on customer onboarding cut our environment setup back-and-forth by half.',
    bodyText:
      'Instead of passing ticket notes between Customer Success and Engineering, we invited one QA colleague to shadow a 10-minute onboarding check-in. Seeing the workflow live eliminated four days of ticket ping-pong.',
    whyThis: 'WHY THIS? Shared with Colleagues in the Pilot · Cross-team learning',
    hasAudio: false,
    inviteOpen: true,
    invitePlaces: 1,
    status: 'PUBLISHED',
    revisionNumber: 1,
    approvedBy: 'usr_priya',
    appreciatedBy: [],
  },
  {
    id: 'upd_01',
    kind: 'UPDATE',
    authorId: 'usr_marcus',
    authorName: 'Global Internal Comms',
    authorFunction: 'Communications',
    authorRegion: 'GLOBAL',
    audienceName: 'All Employees',
    category: 'UPDATES',
    title: 'Engineering Guild: Java 21 Virtual Threads & Accessibility Baseline',
    summary:
      'Official 2-minute briefing on our Q4 engineering standards, keyboard navigation checklist, and cloud cost guardrails.',
    bodyText:
      'Key points from the Q4 Engineering Guild:\n1. All new Spring Boot services enable Java 21 virtual threads.\n2. Every internal web and mobile workflow must pass WCAG 2.1 AA contrast and focus checks.\n3. Optional AI helpers use strict pilot action budgets and deterministic templates.',
    whyThis: 'WHY THIS? Official update for All Employees · Matches your interest: innovation',
    durationSec: 120,
    status: 'PUBLISHED',
    revisionNumber: 1,
    approvedBy: 'usr_priya',
    appreciatedBy: [],
  },
];

type MobileHabit = {
  id: string;
  ownerId: PersonaId;
  name: string;
  schedule: 'daily' | 'weekdays' | 'weekends';
  days: string[];
};

const INITIAL_PROD_HABITS: MobileHabit[] = [
  {
    id: 'hab_demo_1',
    ownerId: 'usr_asha',
    name: '10-min morning stretch',
    schedule: 'weekdays',
    days: [],
  },
];

const INITIAL_AUDIT_LOGS: string[] = [
  '17 Nov · System seeded 6 pilot personas, 1 Radio queue, 3 Culture Cast episodes.',
  '17 Nov · Daniel Wright published "A retrospective everyone can contribute to" (Rev #1).',
];

export default function App() {
  // ---------------------------------------------------------------------------
  // 1. Pre-Filled Demo Login State (Epic 8 / TASK-803)
  // ---------------------------------------------------------------------------
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [persona, setPersona] = useState<PersonaId>('usr_asha');
  const [passwordInput, setPasswordInput] = useState<string>(DEMO_PASSWORD);
  const [showPassword, setShowPassword] = useState<boolean>(false);

  // ---------------------------------------------------------------------------
  // 2. Navigation, Animated Splash & Guided Walkthrough State
  // ---------------------------------------------------------------------------
  const [activeTab, setActiveTab] = useState<BottomTab>('PULSE');
  const [bannerMsg, setBannerMsg] = useState<string | null>(null);
  const [isSplashBooting, setIsSplashBooting] = useState<boolean>(false);
  const [showWalkthrough, setShowWalkthrough] = useState<boolean>(false);
  const [walkthroughStep, setWalkthroughStep] = useState<number>(0);
  const pulseAnim = useRef(new Animated.Value(1)).current;
  const progressAnim = useRef(new Animated.Value(0.15)).current;

  useEffect(() => {
    if (!isSplashBooting) return;
    pulseAnim.setValue(0.92);
    progressAnim.setValue(0.15);
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, {
          toValue: 1.08,
          duration: 500,
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnim, {
          toValue: 0.94,
          duration: 500,
          useNativeDriver: true,
        }),
      ])
    );
    loop.start();
    Animated.timing(progressAnim, {
      toValue: 1,
      duration: 1150,
      useNativeDriver: false,
    }).start();
    return () => loop.stop();
  }, [isSplashBooting]);

  // ---------------------------------------------------------------------------
  // 3. Feed, Stories, Challenge & Recognition State (Full Parity with Web!)
  // ---------------------------------------------------------------------------
  const [stories, setStories] = useState<StoryItem[]>(INITIAL_STORIES);
  const [categoryFilter, setCategoryFilter] = useState<'FOR_YOU' | 'LEARNING' | 'COMMUNITY' | 'UPDATES'>('FOR_YOU');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedStoryId, setSelectedStoryId] = useState<string | null>(null);
  const [showStoryTranscript, setShowStoryTranscript] = useState<boolean>(false);
  const [composingInviteStoryId, setComposingInviteStoryId] = useState<string | null>(null);
  const [inviteMessage, setInviteMessage] = useState<string>(
    'Hi Daniel, I would love to learn how you made retrospectives more inclusive. Could we compare notes?'
  );
  const [inviteAiLabel, setInviteAiLabel] = useState<string | null>(null);

  // Weekly Campfire Challenge & Recognition Ledger State
  const [challengeTitle, setChallengeTitle] = useState<string>(
    'What weekend badminton taught me about sprint pairing'
  );
  const [challengeBody, setChallengeBody] = useState<string>(
    'Playing mixed doubles every Saturday taught me to communicate early and cover gaps without waiting to be asked. Our Pune engineering pod now uses the same habit during code reviews.'
  );
  const [optInLeaderboard, setOptInLeaderboard] = useState<Record<PersonaId, boolean>>({
    usr_asha: true,
    usr_daniel: true,
    usr_priya: true,
    usr_marcus: true,
    usr_elena: true,
    usr_rohan: true,
  });

  // ---------------------------------------------------------------------------
  // 4. Listen Tab & Persistent Bottom Audio Player State (Wireframes 07 & 08)
  // ---------------------------------------------------------------------------
  const [listenMode, setListenMode] = useState<'RADIO' | 'CULTURE_CAST'>('RADIO');
  const [isPlayingAudio, setIsPlayingAudio] = useState<boolean>(false);
  const [activeAudioTitle, setActiveAudioTitle] = useState<string>(
    'Culture Cast Ep 03 — Quiet Leadership in Hybrid Squads'
  );
  const [castPositionSec, setCastPositionSec] = useState<number>(130); // 02:10
  const [castDurationSec] = useState<number>(480); // 08:00
  const [playbackSpeed, setPlaybackSpeed] = useState<'1x' | '1.25x' | '1.5x'>('1x');
  const [showCastTranscript, setShowCastTranscript] = useState<boolean>(true);

  // ---------------------------------------------------------------------------
  // 5. Connect Tab & Private Bilateral Check-In State (Wireframes 04 & 06)
  // ---------------------------------------------------------------------------
  const [connectSubTab, setConnectSubTab] = useState<'RECEIVED' | 'SENT' | 'ACCEPTED'>('RECEIVED');
  const [inviteSenderId, setInviteSenderId] = useState<PersonaId>('usr_asha');
  const [inviteHostId, setInviteHostId] = useState<PersonaId>('usr_daniel');
  const [inviteTopic, setInviteTopic] = useState<string>(
    'A retrospective everyone can contribute to'
  );
  const [remainingPlaces, setRemainingPlaces] = useState<number>(2);
  const [requestState, setRequestState] = useState<'NONE' | 'PENDING' | 'ACCEPTED' | 'DECLINED'>('PENDING');
  const [senderConfirmed, setSenderConfirmed] = useState<boolean>(false);
  const [hostConfirmed, setHostConfirmed] = useState<boolean>(false);
  const [showCheckInScreen, setShowCheckInScreen] = useState<boolean>(false);
  const [happenedChoice, setHappenedChoice] = useState<'YES' | 'NO' | 'NOT_YET'>('YES');
  const [usefulChoice, setUsefulChoice] = useState<boolean>(true);

  // ---------------------------------------------------------------------------
  // 6. Contribute Tab State (Wireframe 05)
  // ---------------------------------------------------------------------------
  const [draftTitle, setDraftTitle] = useState<string>('A better retrospective checklist');
  const [draftStory, setDraftStory] = useState<string>(
    'What changed? We started our retro with 4 minutes of silent writing. What did we learn? Quieter voices shared concrete test automation ideas right away.'
  );
  const [draftAudience, setDraftAudience] = useState<'Colleagues in the Pilot' | 'All Employees'>(
    'Colleagues in the Pilot'
  );
  const [showRevisionPreview, setShowRevisionPreview] = useState<boolean>(false);

  // ---------------------------------------------------------------------------
  // 7. Profile & Portal Controls State
  // ---------------------------------------------------------------------------
  const [portalSubTab, setPortalSubTab] = useState<'QUEUE' | 'METRICS' | 'AUDIT'>('QUEUE');
  const [interests, setInterests] = useState<string[]>([
    'team learning',
    'innovation',
    'culture',
    'delivery excellence',
  ]);
  const [hostPaused, setHostPaused] = useState<boolean>(false);
  const [aiActionsUsed, setAiActionsUsed] = useState<number>(4);
  const [auditLogs, setAuditLogs] = useState<string[]>(INITIAL_AUDIT_LOGS);

  // ---------------------------------------------------------------------------
  // 8. Make me Productive State (Section 14 — Habits, Pomodoro, Reset & Breathing)
  // ---------------------------------------------------------------------------
  const [prodHabits, setProdHabits] = useState<MobileHabit[]>(INITIAL_PROD_HABITS);
  const [editingHabitId, setEditingHabitId] = useState<string | null>(null);
  const [newHabitName, setNewHabitName] = useState<string>('');
  const [newHabitSchedule, setNewHabitSchedule] = useState<'daily' | 'weekdays' | 'weekends'>('daily');
  const [prodFocusMin, setProdFocusMin] = useState<string>('25');
  const [prodShortMin, setProdShortMin] = useState<string>('5');
  const [prodLongMin, setProdLongMin] = useState<string>('15');
  const [prodResetMin, setProdResetMin] = useState<string>('2');
  const [prodTimerMode, setProdTimerMode] = useState<'focus' | 'short' | 'long' | 'breath' | 'mind'>('focus');
  const [prodTotalSec, setProdTotalSec] = useState<number>(25 * 60);
  const [prodRemSec, setProdRemSec] = useState<number>(25 * 60);
  const [prodRunning, setProdRunning] = useState<boolean>(false);
  const [prodDone, setProdDone] = useState<boolean>(false);
  const [prodCompletedCount, setProdCompletedCount] = useState<number>(0);
  const prodDeadlineRef = useRef<number | null>(null);

  useEffect(() => {
    if (!prodRunning) return;
    const syncTimer = () => {
      if (prodDeadlineRef.current === null) return;
      const left = Math.max(0, Math.ceil((prodDeadlineRef.current - Date.now()) / 1000));
      setProdRemSec(left);
      if (left === 0) {
        prodDeadlineRef.current = null;
        setProdRunning(false);
        setProdDone(true);
        if (prodTimerMode === 'focus') {
          setProdCompletedCount((c) => c + 1);
        }
      }
    };
    const interval = setInterval(syncTimer, 250);
    const sub = AppState.addEventListener('change', (nextState) => {
      if (nextState === 'active') syncTimer();
    });
    return () => {
      clearInterval(interval);
      sub.remove();
    };
  }, [prodRunning, prodTimerMode]);

  // Live audio playhead advancement when Mastek Radio / Culture Cast is playing
  useEffect(() => {
    if (!isPlayingAudio) return;
    const step = playbackSpeed === '1.5x' ? 1.5 : playbackSpeed === '1.25x' ? 1.25 : 1;
    const interval = setInterval(() => {
      setCastPositionSec((prev) => {
        const next = Math.min(castDurationSec, prev + step);
        if (next >= castDurationSec) {
          setIsPlayingAudio(false);
          setBannerMsg('🎧 Finished playing audio episode.');
          return castDurationSec;
        }
        return next;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [isPlayingAudio, playbackSpeed, castDurationSec]);

  const currentUser = PERSONAS[persona];
  const inviteSender = PERSONAS[inviteSenderId];
  const inviteHost = PERSONAS[inviteHostId];
  const hasPortalRole =
    currentUser.roles.includes('REVIEWER') ||
    currentUser.roles.includes('PUBLISHER') ||
    currentUser.roles.includes('MODERATOR') ||
    currentUser.roles.includes('ADMIN');

  // ---------------------------------------------------------------------------
  // Dynamic Recognition Score & Badge Calculator (Matches Backend Ledger Rules!)
  // ---------------------------------------------------------------------------
  const computeUserRecognition = (uid: PersonaId) => {
    const publishedStories = stories.filter((s) => s.kind === 'STORY' && s.status === 'PUBLISHED');
    const hasPublishedContrib = publishedStories.some(
      (s) => s.authorId === uid && s.id !== 'story_01' && s.id !== 'story_02'
    );
    const contributionPoints = hasPublishedContrib ? 20 : 0;

    let givenCount = 0;
    const supportedTeams = new Set<string>();
    for (const s of publishedStories) {
      if (s.appreciatedBy.includes(uid)) {
        givenCount++;
        supportedTeams.add(s.authorFunction);
      }
    }
    const givingPoints = Math.min(15, givenCount * 5);

    const distinctSenders = new Set<PersonaId>();
    for (const s of publishedStories) {
      if (s.authorId === uid) {
        s.appreciatedBy.forEach((giver) => distinctSenders.add(giver));
      }
    }
    const receivedDistinctCount = distinctSenders.size;
    const receivingPoints = Math.min(25, receivedDistinctCount * 5);
    const totalPoints = Math.min(60, contributionPoints + givingPoints + receivingPoints);

    const badges: string[] = [];
    if (contributionPoints >= 20) badges.push('First Spark');
    if (
      supportedTeams.size >= 1 &&
      publishedStories.some(
        (s) =>
          (s.appreciatedBy.includes(uid) && s.authorFunction !== PERSONAS[uid].functionName) ||
          (s.authorId === uid &&
            s.appreciatedBy.some((g) => PERSONAS[g].functionName !== PERSONAS[uid].functionName))
      )
    ) {
      badges.push('Connection Maker');
    }
    if (receivedDistinctCount >= 5) badges.push('Community Favourite');

    return {
      totalPoints,
      contributionPoints,
      givenCount,
      givingPoints,
      receivedDistinctCount,
      receivingPoints,
      distinctTeamsSupported: supportedTeams.size,
      badges,
    };
  };

  const myRec = computeUserRecognition(persona);
  const bilateralUsefulConfirmed = senderConfirmed && hostConfirmed;
  const challengeEntry =
    stories.find((s) => s.id === `story_ch_${persona}`) ||
    stories.find(
      (s) => s.authorId === persona && s.kind === 'STORY' && s.id !== 'story_01' && s.id !== 'story_02'
    ) ||
    null;
  const selectedStory = stories.find((s) => s.id === selectedStoryId) || null;
  const composingInviteStory = stories.find((s) => s.id === composingInviteStoryId) || null;

  const formatTime = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  // ---------------------------------------------------------------------------
  // Centralized Navigation, Home, Logout, Audio & Reset Helpers
  // ---------------------------------------------------------------------------
  const handleSelectTab = (tab: BottomTab) => {
    setActiveTab(tab);
    setSelectedStoryId(null);
    setComposingInviteStoryId(null);
    setShowCheckInScreen(false);
  };

  const handleGoHome = () => {
    setActiveTab('PULSE');
    setSelectedStoryId(null);
    setComposingInviteStoryId(null);
    setShowCheckInScreen(false);
    setShowWalkthrough(false);
    setBannerMsg('🏠 Returned to Campfire Home feed.');
  };

  const handleLogout = () => {
    prodDeadlineRef.current = null;
    setProdRunning(false);
    setIsPlayingAudio(false);
    setSelectedStoryId(null);
    setComposingInviteStoryId(null);
    setShowCheckInScreen(false);
    setShowWalkthrough(false);
    setBannerMsg(null);
    setActiveTab('PULSE');
    setIsAuthenticated(false);
  };

  const handleResetDemoState = () => {
    prodDeadlineRef.current = null;
    setProdRunning(false);
    setProdDone(false);
    setIsPlayingAudio(false);
    setCastPositionSec(130);
    setStories(INITIAL_STORIES);
    setCategoryFilter('FOR_YOU');
    setSearchQuery('');
    setSelectedStoryId(null);
    setComposingInviteStoryId(null);
    setShowCheckInScreen(false);
    setInviteSenderId('usr_asha');
    setInviteHostId('usr_daniel');
    setInviteTopic('A retrospective everyone can contribute to');
    setInviteMessage(
      'Hi Daniel, I would love to learn how you made retrospectives more inclusive. Could we compare notes?'
    );
    setRemainingPlaces(2);
    setRequestState('PENDING');
    setSenderConfirmed(false);
    setHostConfirmed(false);
    setHostPaused(false);
    setAiActionsUsed(4);
    setAuditLogs(INITIAL_AUDIT_LOGS);
    setProdHabits(INITIAL_PROD_HABITS);
    setEditingHabitId(null);
    setNewHabitName('');
    setProdFocusMin('25');
    setProdShortMin('5');
    setProdLongMin('15');
    setProdResetMin('2');
    setProdTimerMode('focus');
    setProdTotalSec(25 * 60);
    setProdRemSec(25 * 60);
    setProdCompletedCount(0);
    setActiveTab('PULSE');
    setBannerMsg('🔄 Demo workspace reset to clean initial pilot state.');
  };

  const handleToggleAudio = (titleOverride?: string) => {
    if (titleOverride) {
      setActiveAudioTitle(titleOverride);
    }
    if (!isPlayingAudio && castPositionSec >= castDurationSec) {
      setCastPositionSec(0);
    }
    setIsPlayingAudio((prev) => !prev);
  };

  // ---------------------------------------------------------------------------
  // Handlers for Full Interactive Walkthrough
  // ---------------------------------------------------------------------------
  const handleSubmitChallenge = () => {
    if (challengeTitle.trim().length < 5 || challengeBody.trim().length < 20) {
      setBannerMsg('422 Validation: Please enter a title (5+ chars) and story (20+ chars).');
      return;
    }
    const newId = challengeEntry?.id || `story_ch_${persona}`;
    const nextRev = challengeEntry ? challengeEntry.revisionNumber + 1 : 1;
    const newItem: StoryItem = {
      id: newId,
      kind: 'STORY',
      authorId: persona,
      authorName: currentUser.displayName,
      authorFunction: currentUser.functionName,
      authorRegion: currentUser.region,
      audienceName: 'Colleagues in the Pilot',
      category: 'FOR_YOU',
      title: challengeTitle.trim(),
      summary: challengeBody.trim().slice(0, 120) + '...',
      bodyText: challengeBody.trim(),
      whyThis: `WHY THIS? Weekly Campfire Challenge story by ${currentUser.displayName}`,
      inviteOpen: true,
      invitePlaces: 2,
      status: 'IN_REVIEW',
      revisionNumber: nextRev,
      approvedBy: null,
      appreciatedBy: [],
    };
    setStories((prev) => [newItem, ...prev.filter((s) => s.id !== newId)]);
    setAuditLogs((prev) => [
      `17 Nov · ${currentUser.displayName} submitted Challenge Rev #${nextRev} "${newItem.title}" (IN_REVIEW, 0 pts).`,
      ...prev,
    ]);
    setBannerMsg(
      `Submitted Rev #${nextRev} to Review Queue for ${currentUser.displayName}! Points now: 0 (+20 unlocks after Priya approves & Marcus publishes in Portal tab).`
    );
  };

  const handleAppreciate = (story: StoryItem) => {
    if (story.authorId === persona) {
      setBannerMsg('422 Guardrail: Self-appreciation is disallowed — appreciate a colleague’s story.');
      return;
    }
    if (story.appreciatedBy.includes(persona)) {
      setBannerMsg(`You already appreciated "${story.title}" (duplicate clicks add 0 points).`);
      return;
    }
    setStories((prev) =>
      prev.map((s) =>
        s.id === story.id ? { ...s, appreciatedBy: [...s.appreciatedBy, persona] } : s
      )
    );
    setAuditLogs((prev) => [
      `17 Nov · ${currentUser.displayName} appreciated ${story.authorName}'s story "${story.title}" (+5/+5 pts).`,
      ...prev,
    ]);
    setBannerMsg(
      `Appreciated "${story.title}"! +5 pts to you and +5 pts to ${story.authorName.split(' ')[0]}.`
    );
  };

  const handlePortalReview = (story: StoryItem, decision: 'APPROVE' | 'REQUEST_CHANGES') => {
    if (!currentUser.roles.includes('REVIEWER')) {
      setBannerMsg('403 Forbidden: Only REVIEWER role (Priya Nair) can approve or return revisions.');
      return;
    }
    if (story.authorId === persona) {
      setBannerMsg('403 Self-Approval Blocked: Authors cannot approve their own revision.');
      return;
    }
    const nextStatus = decision === 'APPROVE' ? 'APPROVED' : 'DRAFT';
    setStories((prev) =>
      prev.map((s) =>
        s.id === story.id
          ? { ...s, status: nextStatus, approvedBy: decision === 'APPROVE' ? persona : null }
          : s
      )
    );
    setAuditLogs((prev) => [
      `17 Nov · Reviewer ${currentUser.displayName} marked "${story.title}" Rev #${story.revisionNumber} as ${nextStatus} (0 pts awarded on approval).`,
      ...prev,
    ]);
    setBannerMsg(
      decision === 'APPROVE'
        ? `Approved Rev #${story.revisionNumber} of "${story.title}" (0 pts on approval). Switch to Marcus (Publisher) to publish!`
        : `Returned "${story.title}" to DRAFT for author revisions.`
    );
  };

  const handlePortalPublish = (story: StoryItem) => {
    if (!currentUser.roles.includes('PUBLISHER')) {
      setBannerMsg('403 Forbidden: Only PUBLISHER role (Marcus Vance) can publish approved revisions.');
      return;
    }
    if (story.status !== 'APPROVED' || !story.approvedBy) {
      setBannerMsg('409 Conflict: Revision must be APPROVED by a Reviewer before publication.');
      return;
    }
    setStories((prev) =>
      prev.map((s) => (s.id === story.id ? { ...s, status: 'PUBLISHED' } : s))
    );
    setAuditLogs((prev) => [
      `17 Nov · Publisher ${currentUser.displayName} PUBLISHED "${story.title}" -> Awarded +20 pts & First Spark to ${story.authorName}!`,
      ...prev,
    ]);
    setBannerMsg(
      `Published "${story.title}"! Author ${story.authorName} synchronously earned +20 pts & the First Spark badge!`
    );
  };

  const handlePortalWithdraw = (story: StoryItem) => {
    if (!currentUser.roles.includes('PUBLISHER') && !currentUser.roles.includes('MODERATOR')) {
      setBannerMsg('403 Forbidden: Only PUBLISHER or MODERATOR can withdraw published stories.');
      return;
    }
    setStories((prev) =>
      prev.map((s) => (s.id === story.id ? { ...s, status: 'WITHDRAWN' } : s))
    );
    setAuditLogs((prev) => [
      `17 Nov · ${currentUser.displayName} WITHDREW "${story.title}" -> Reversed -20 pts (REVERSAL_WITHDRAWN).`,
      ...prev,
    ]);
    setBannerMsg(
      `Withdrew "${story.title}" from employee feeds and recorded -20 pts REVERSAL_WITHDRAWN.`
    );
  };

  // ===========================================================================
  // SCREEN 0: PRE-FILLED DEMO SIGN-IN FLOW (`isAuthenticated !== true`)
  // ===========================================================================
  if (isAuthenticated !== true) {
    return (
      <SafeAreaView style={styles.loginSafe}>
        <ScrollView contentContainerStyle={styles.loginContainer}>
          {/* Brand Header */}
          <View style={styles.loginBrandCard}>
            <View style={styles.loginBrandRow}>
              <View style={styles.flameBadge}>
                <Text style={styles.flameIcon}>🔥</Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.loginBrandTitle}>mastek campfire</Text>
                <Text style={styles.loginBrandSub}>
                  Small stories · Meaningful connections · Weekly recognition
                </Text>
              </View>
            </View>
            <View style={styles.storedBadge}>
              <Text style={styles.storedBadgeText}>✓ Demo Credentials Stored</Text>
            </View>
          </View>

          {/* Selected Persona Hero Card */}
          <View style={styles.loginFormCard}>
            <Text style={styles.sectionLabel}>1. SELECTED DEMO ACCOUNT</Text>
            <View style={styles.selectedHeroRow}>
              <PersonaPortrait personaId={persona} size={60} />
              <View style={{ flex: 1, marginLeft: 12 }}>
                <Text style={styles.selectedHeroName}>{currentUser.displayName}</Text>
                <Text style={styles.selectedHeroRole}>
                  {currentUser.jobTitle} · {currentUser.region}
                </Text>
                <View style={styles.roleChipRow}>
                  {currentUser.roles.map((r) => (
                    <View key={r} style={styles.roleChip}>
                      <Text style={styles.roleChipText}>{r}</Text>
                    </View>
                  ))}
                </View>
              </View>
            </View>

            {/* Pre-filled Email & Stored Demo Password */}
            <Text style={[styles.sectionLabel, { marginTop: 14 }]}>
              2. MASTEK CORPORATE SSO EMAIL
            </Text>
            <View style={styles.readOnlyInput}>
              <Text style={styles.readOnlyInputText}>{currentUser.email}</Text>
            </View>

            <View style={styles.passwordHeaderRow}>
              <Text style={styles.sectionLabel}>3. STORED DEMO PASSWORD</Text>
              <Text style={styles.autoFilledTag}>🔒 Auto-filled for POC</Text>
            </View>
            <View style={styles.passwordRow}>
              <TextInput
                style={[styles.textInput, { flex: 1, marginTop: 0 }]}
                value={passwordInput}
                onChangeText={setPasswordInput}
                secureTextEntry={!showPassword}
              />
              <TouchableOpacity
                style={styles.showHideBtn}
                onPress={() => setShowPassword(!showPassword)}
              >
                <Text style={styles.showHideBtnText}>{showPassword ? 'Hide' : 'Show'}</Text>
              </TouchableOpacity>
            </View>

            <TouchableOpacity
              style={styles.signInPrimaryBtn}
              onPress={() => {
                setIsSplashBooting(true);
                setIsAuthenticated(true);
                setTimeout(() => {
                  setIsSplashBooting(false);
                  setWalkthroughStep(0);
                  setShowWalkthrough(true);
                  setBannerMsg(
                    `Signed in as ${currentUser.displayName} (${currentUser.region}). Use the bottom tabs to explore all Campfire journeys!`
                  );
                }, 1200);
              }}
            >
              <Text style={styles.signInPrimaryBtnText}>
                Sign In as {currentUser.displayName} →
              </Text>
            </TouchableOpacity>
          </View>

          {/* Persona Photo Grid Selector */}
          <View style={styles.loginFormCard}>
            <Text style={styles.sectionLabel}>
              SWITCH DEMO PERSONA (TAP ANY COLLEAGUE PHOTO)
            </Text>
            {(Object.keys(PERSONAS) as PersonaId[]).map((pid) => {
              const p = PERSONAS[pid];
              const isSelected = pid === persona;
              return (
                <TouchableOpacity
                  key={pid}
                  style={[
                    styles.personaCardRow,
                    isSelected && styles.personaCardRowActive,
                  ]}
                  onPress={() => {
                    setPersona(pid);
                    setPasswordInput(DEMO_PASSWORD);
                  }}
                >
                  <PersonaPortrait personaId={pid} size={46} />
                  <View style={{ flex: 1, marginLeft: 10 }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                      <Text style={styles.personaCardName}>{p.displayName}</Text>
                      {isSelected && (
                        <View style={styles.activePill}>
                          <Text style={styles.activePillText}>Selected</Text>
                        </View>
                      )}
                    </View>
                    <Text style={styles.personaCardSub}>
                      {p.jobTitle} ({p.region})
                    </Text>
                    <Text style={styles.personaCardRoles}>
                      Roles: {p.roles.join(' · ')}
                    </Text>
                  </View>
                </TouchableOpacity>
              );
            })}
          </View>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // Filter feed items for Tab 1 (`PULSE`)
  const publishedFeed = stories.filter((s) => {
    if (s.status !== 'PUBLISHED') return false;
    if (categoryFilter === 'LEARNING' && s.category !== 'LEARNING') return false;
    if (categoryFilter === 'COMMUNITY' && s.category !== 'COMMUNITY') return false;
    if (categoryFilter === 'UPDATES' && s.kind !== 'UPDATE') return false;
    if (
      searchQuery.trim() &&
      !`${s.title} ${s.summary} ${s.authorName}`
        .toLowerCase()
        .includes(searchQuery.trim().toLowerCase())
    ) {
      return false;
    }
    return true;
  });

  // Compute Leaderboard rows for Tab 2 (`CHAMPIONS`)
  const leaderboardRows = (Object.keys(PERSONAS) as PersonaId[])
    .filter((uid) => optInLeaderboard[uid])
    .map((uid) => {
      const p = PERSONAS[uid];
      const rec = computeUserRecognition(uid);
      return {
        userId: uid,
        displayName: p.displayName,
        functionName: p.functionName,
        region: p.region,
        points: rec.totalPoints,
        badges: rec.badges,
      };
    })
    .sort((a, b) => b.points - a.points || a.displayName.localeCompare(b.displayName))
    .slice(0, 5);

  // ===========================================================================
  // ANIMATED SPLASH SCREEN WHILE IGNITING CAMPFIRE WORKSPACE
  // ===========================================================================
  if (isSplashBooting) {
    const barWidth = progressAnim.interpolate({
      inputRange: [0, 1],
      outputRange: ['15%', '100%'],
    });
    return (
      <SafeAreaView style={styles.loginSafe}>
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 }}>
          <View style={styles.loginBrandCard}>
            <View style={{ alignItems: 'center', paddingVertical: 12 }}>
              <Animated.View
                style={[
                  styles.flameBadge,
                  {
                    width: 78,
                    height: 78,
                    borderRadius: 39,
                    borderWidth: 2,
                    borderColor: '#fbbf24',
                    marginRight: 0,
                    transform: [{ scale: pulseAnim }],
                  },
                ]}
              >
                <Text style={{ fontSize: 36 }}>🔥</Text>
              </Animated.View>
              <Text style={[styles.challengeBannerTag, { color: '#fde68a', marginTop: 14 }]}>
                IGNITING MASTEK CAMPFIRE
              </Text>
              <Text style={[styles.loginBrandTitle, { marginTop: 4, textAlign: 'center' }]}>
                Welcome, {currentUser.displayName}
              </Text>
              <Text style={[styles.loginBrandSub, { textAlign: 'center' }]}>
                {currentUser.jobTitle} ({currentUser.region})
              </Text>

              <View
                style={{
                  width: 240,
                  height: 10,
                  backgroundColor: '#1e3a8a',
                  borderRadius: 6,
                  overflow: 'hidden',
                  marginTop: 18,
                }}
              >
                <Animated.View
                  style={{
                    width: barWidth,
                    height: '100%',
                    backgroundColor: '#fbbf24',
                    borderRadius: 6,
                  }}
                />
              </View>

              <View style={{ marginTop: 16, alignSelf: 'stretch' }}>
                <Text style={[styles.loginBrandSub, { marginTop: 4 }]}>
                  ✓ Filtering stories &amp; audio by your audiences
                </Text>
                <Text style={[styles.loginBrandSub, { marginTop: 4 }]}>
                  ✓ Loading Weekly Challenge &amp; Recognition Ledger
                </Text>
                <Text style={[styles.loginBrandSub, { marginTop: 4 }]}>
                  ✓ Preparing private Make me Productive tools
                </Text>
              </View>
            </View>
          </View>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      {/* =====================================================================
          TOP HEADER BAR: Brand, Home, Tour, Logout & Quick Persona Switcher
         ===================================================================== */}
      <View style={styles.header}>
        <View style={styles.headerTopRow}>
          <TouchableOpacity
            onPress={handleGoHome}
            activeOpacity={0.8}
            accessibilityLabel="Mastek Campfire Home"
            style={{ flexDirection: 'row', alignItems: 'center', flex: 1, marginRight: 6 }}
          >
            <PersonaPortrait personaId={persona} size={36} />
            <View style={{ marginLeft: 8, flex: 1 }}>
              <Text style={styles.brandTitle} numberOfLines={1}>
                mastek campfire
              </Text>
              <Text style={styles.brandSub} numberOfLines={1}>
                {currentUser.displayName} · {myRec.totalPoints}/60 pts
              </Text>
            </View>
          </TouchableOpacity>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <TouchableOpacity
              accessibilityLabel="Home"
              style={[
                styles.signOutBtn,
                activeTab === 'PULSE' && !selectedStoryId && !composingInviteStoryId
                  ? { backgroundColor: '#ffffff', borderColor: '#ffffff', marginRight: 4 }
                  : { backgroundColor: '#1d4ed8', borderColor: '#93c5fd', marginRight: 4 },
              ]}
              onPress={handleGoHome}
            >
              <Text
                style={[
                  styles.signOutBtnText,
                  activeTab === 'PULSE' && !selectedStoryId && !composingInviteStoryId
                    ? { color: '#002180' }
                    : null,
                ]}
              >
                🏠 Home
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              accessibilityLabel="Guided Tour"
              style={[styles.signOutBtn, { backgroundColor: '#fbbf24', borderColor: '#f59e0b', marginRight: 4 }]}
              onPress={() => {
                setWalkthroughStep(0);
                setShowWalkthrough(true);
              }}
            >
              <Text style={[styles.signOutBtnText, { color: '#0f172a' }]}>🎯 Tour</Text>
            </TouchableOpacity>
            <TouchableOpacity
              accessibilityLabel="Logout"
              style={[styles.signOutBtn, { backgroundColor: '#991b1b', borderColor: '#fca5a5' }]}
              onPress={handleLogout}
            >
              <Text style={styles.signOutBtnText}>🚪 Logout</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Quick Persona Switcher Strip */}
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={{ marginTop: 8 }}
        >
          {(Object.keys(PERSONAS) as PersonaId[]).map((pid) => {
            const p = PERSONAS[pid];
            const active = pid === persona;
            return (
              <TouchableOpacity
                key={pid}
                onPress={() => {
                  setPersona(pid);
                  setEditingHabitId(null);
                  setNewHabitName('');
                  setBannerMsg(
                    `Switched active persona to ${p.displayName} (${p.roles.filter((r) => r !== 'EMPLOYEE').join('/') || 'Employee'}).`
                  );
                }}
                style={[styles.personaChip, active && styles.personaChipActive]}
              >
                <Text
                  style={[styles.personaChipText, active && styles.personaChipTextActive]}
                >
                  {p.displayName.split(' ')[0]} ({p.roles.includes('REVIEWER') ? 'Rev' : p.roles.includes('PUBLISHER') ? 'Pub' : p.roles.includes('ADMIN') ? 'Admin' : 'Emp'})
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      </View>

      {/* Interactive Feedback Toast Banner */}
      {bannerMsg && (
        <View style={styles.toastBanner}>
          <Text style={styles.toastBannerText}>{bannerMsg}</Text>
          <TouchableOpacity onPress={() => setBannerMsg(null)}>
            <Text style={styles.toastDismissText}>Dismiss</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Interactive 5-Step Guided Walkthrough for New Users */}
      {showWalkthrough && (() => {
        const steps = [
          {
            badge: 'STEP 1 OF 5 · DISCOVER & CONNECT',
            title: 'Small stories that spark real 15-min conversations',
            body: 'Browse stories filtered by your audience & explicit interests with factual "WHY THIS?" badges. Open any story to send a 15-minute invitation and complete a private bilateral check-in afterwards.',
            actionLabel: 'Explore Campfire Feed →',
            onJump: () => {
              handleSelectTab('PULSE');
              setShowWalkthrough(false);
            },
          },
          {
            badge: 'STEP 2 OF 5 · CHALLENGE & CHAMPIONS',
            title: 'Submit a story, appreciate colleagues, earn up to 60 pts/wk',
            body: 'Submit to "Beyond My Job Title" (0 pts on submit; +20 pts & First Spark after Priya approves & Marcus publishes). Appreciate colleagues (+5/+5) and opt in/out of the Top-5 leaderboard.',
            actionLabel: 'Open Challenge & Points →',
            onJump: () => {
              handleSelectTab('CHAMPIONS');
              setShowWalkthrough(false);
            },
          },
          {
            badge: 'STEP 3 OF 5 · MASTEK RADIO & CULTURE CAST',
            title: 'Commute listening with chapters, transcripts & resume',
            body: 'Play the 12-minute Mastek Radio commute queue or Culture Cast episodes with interactive chapters, verified transcripts, and cross-device playback resume.',
            actionLabel: 'Open Listen Tab →',
            onJump: () => {
              handleSelectTab('LISTEN');
              setShowWalkthrough(false);
            },
          },
          {
            badge: 'STEP 4 OF 5 · MAKE ME PRODUCTIVE (FOCUS TAB)',
            title: 'Private habits, Pomodoro focus & 4-4-4-4 Box Breathing',
            body: 'Build your daily/weekday/weekend habit routine, run a deadline-based Pomodoro focus timer, try the 5-minute starter, or take a Box Breathing / Mindfulness pause. 100% owner-private.',
            actionLabel: 'Open Focus (Make me Productive) →',
            onJump: () => {
              handleSelectTab('PRODUCTIVE');
              setShowWalkthrough(false);
            },
          },
          {
            badge: 'STEP 5 OF 5 · PUBLISHING PORTAL & TRUST GATES',
            title: 'Role-based human review, 720-action AI cap & audit log',
            body: 'Switch personas in the top bar (Priya Nair for Reviewer or Marcus Vance for Publisher) and open the Portal tab to approve/publish revisions and inspect Pilot & AI Budget Gates.',
            actionLabel: 'Switch to Priya & Open Portal →',
            onJump: () => {
              setPersona('usr_priya');
              handleSelectTab('PORTAL');
              setShowWalkthrough(false);
            },
          },
        ];
        const cur = steps[walkthroughStep] || steps[0];
        return (
          <View
            style={{
              margin: 12,
              marginBottom: 0,
              backgroundColor: '#ffffff',
              borderRadius: 14,
              borderWidth: 2,
              borderColor: '#002180',
              overflow: 'hidden',
            }}
          >
            <View
              style={{
                backgroundColor: '#002180',
                paddingHorizontal: 14,
                paddingVertical: 10,
              }}
            >
              <View style={styles.rowBetween}>
                <Text style={{ color: '#fbbf24', fontSize: 10, fontWeight: '800' }}>
                  🎯 {cur.badge}
                </Text>
                <TouchableOpacity onPress={() => setShowWalkthrough(false)}>
                  <Text style={{ color: '#bfdbfe', fontSize: 11, fontWeight: '700' }}>
                    Skip Tour ✕
                  </Text>
                </TouchableOpacity>
              </View>
              <Text style={{ color: '#ffffff', fontSize: 15, fontWeight: '800', marginTop: 4 }}>
                {cur.title}
              </Text>
            </View>
            <View style={{ padding: 12 }}>
              <Text style={styles.bodyText}>{cur.body}</Text>
              <TouchableOpacity
                style={[styles.secondaryActionBtn, { alignSelf: 'stretch', alignItems: 'center' }]}
                onPress={cur.onJump}
              >
                <Text style={styles.secondaryActionBtnText}>{cur.actionLabel}</Text>
              </TouchableOpacity>
              <View style={[styles.rowBetween, { marginTop: 10 }]}>
                <Text style={styles.metaText}>
                  Step {walkthroughStep + 1} of {steps.length}
                </Text>
                <View style={{ flexDirection: 'row' }}>
                  {walkthroughStep > 0 && (
                    <TouchableOpacity
                      style={[styles.outlineBtnSmall, { marginBottom: 0 }]}
                      onPress={() => setWalkthroughStep((s) => Math.max(0, s - 1))}
                    >
                      <Text style={styles.outlineBtnText}>← Back</Text>
                    </TouchableOpacity>
                  )}
                  {walkthroughStep < steps.length - 1 ? (
                    <TouchableOpacity
                      style={[styles.primaryBtnSmall, { marginBottom: 0, marginRight: 0 }]}
                      onPress={() => setWalkthroughStep((s) => s + 1)}
                    >
                      <Text style={styles.primaryBtnText}>Next →</Text>
                    </TouchableOpacity>
                  ) : (
                    <TouchableOpacity
                      style={[
                        styles.primaryBtnSmall,
                        { backgroundColor: '#047857', marginBottom: 0, marginRight: 0 },
                      ]}
                      onPress={() => setShowWalkthrough(false)}
                    >
                      <Text style={styles.primaryBtnText}>Done ✓</Text>
                    </TouchableOpacity>
                  )}
                </View>
              </View>
            </View>
          </View>
        );
      })()}

      {/* =====================================================================
          MAIN SCROLLABLE WORKSPACE AREA
         ===================================================================== */}
      <ScrollView contentContainerStyle={styles.scrollBody}>
        {/* ===================================================================
            TAB 1: PULSE ("Your Campfire" — Wireframes 01, 02, 03)
           =================================================================== */}
        {activeTab === 'PULSE' && !selectedStory && !composingInviteStory && (
          <View>
            {/* Compact Weekly Challenge Callout */}
            <View style={styles.challengeBannerCard}>
              <Text style={styles.challengeBannerTag}>
                🔥 WEEKLY CAMPFIRE CHALLENGE · 2026-11-16 UTC
              </Text>
              <Text style={styles.challengeBannerTitle}>Beyond My Job Title</Text>
              <Text style={styles.challengeBannerSub}>
                {challengeEntry
                  ? `Your entry "${challengeEntry.title}" is ${challengeEntry.status} (Rev #${challengeEntry.revisionNumber}).`
                  : 'Share a story beyond your job title! Publishing earns +20 pts & First Spark.'}{' '}
                Your score: {myRec.totalPoints}/60 pts.
              </Text>
              <TouchableOpacity
                style={styles.secondaryActionBtn}
                onPress={() => handleSelectTab('CHAMPIONS')}
              >
                <Text style={styles.secondaryActionBtnText}>
                  Open Challenge & Leaderboard →
                </Text>
              </TouchableOpacity>
            </View>

            {/* Greeting & Search / Category Filter Card (Wireframe 01) */}
            <View style={styles.card}>
              <Text style={styles.wireframeTag}>01 / YOUR CAMPFIRE (DISCOVER)</Text>
              <Text style={styles.h1}>
                Good morning, {currentUser.displayName.split(' ')[0]}
              </Text>
              <Text style={styles.metaText}>
                Interests: {interests.join(' / ')}
              </Text>
              <TextInput
                style={[styles.textInput, { marginTop: 10 }]}
                value={searchQuery}
                onChangeText={setSearchQuery}
                placeholder="Search stories, authors, or interests..."
              />
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                style={{ marginTop: 10 }}
              >
                {(
                  [
                    { id: 'FOR_YOU', label: 'For you' },
                    { id: 'LEARNING', label: 'Learning' },
                    { id: 'COMMUNITY', label: 'Community' },
                    { id: 'UPDATES', label: 'Updates' },
                  ] as const
                ).map((cat) => (
                  <TouchableOpacity
                    key={cat.id}
                    onPress={() => setCategoryFilter(cat.id)}
                    style={[
                      styles.filterPill,
                      categoryFilter === cat.id && styles.filterPillActive,
                    ]}
                  >
                    <Text
                      style={[
                        styles.filterPillText,
                        categoryFilter === cat.id && styles.filterPillTextActive,
                      ]}
                    >
                      {cat.label}
                    </Text>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            </View>

            {/* Feed Cards with Author Portrait Photos */}
            {publishedFeed.map((item) => (
              <View key={item.id} style={styles.card}>
                <View style={styles.authorHeaderRow}>
                  <PersonaPortrait personaId={item.authorId} size={40} />
                  <View style={{ flex: 1, marginLeft: 10 }}>
                    <Text style={styles.authorNameText}>
                      {item.authorName} · {item.authorFunction} ({item.authorRegion})
                    </Text>
                    <Text style={styles.audienceSubText}>
                      {item.kind === 'UPDATE' ? 'OFFICIAL UPDATE' : 'COLLEAGUE STORY'} · Audience:{' '}
                      {item.audienceName}
                    </Text>
                  </View>
                </View>

                <Text style={styles.h2}>{item.title}</Text>
                <Text style={styles.bodyText}>{item.summary}</Text>

                <View style={styles.whyBadge}>
                  <Text style={styles.whyBadgeText}>{item.whyThis}</Text>
                </View>

                {item.aiSummary && (
                  <View style={styles.aiSummaryBox}>
                    <Text style={styles.aiSummaryTitle}>
                      ✨ Saathi Grounded Summary (Published Snapshot Only)
                    </Text>
                    <Text style={styles.bodyText}>{item.aiSummary}</Text>
                  </View>
                )}

                <View style={styles.actionRowWrap}>
                  <TouchableOpacity
                    style={styles.primaryBtnSmall}
                    onPress={() => {
                      setSelectedStoryId(item.id);
                      setShowStoryTranscript(false);
                    }}
                  >
                    <Text style={styles.primaryBtnText}>
                      {item.kind === 'UPDATE' ? 'Read brief >' : 'Read story >'}
                    </Text>
                  </TouchableOpacity>

                  {item.kind === 'STORY' && (
                    <TouchableOpacity
                      style={[
                        styles.amberBtnSmall,
                        item.authorId === persona && { opacity: 0.5 },
                      ]}
                      onPress={() => handleAppreciate(item)}
                    >
                      <Text style={styles.amberBtnText}>
                        {item.authorId === persona
                          ? 'Your Story'
                          : item.appreciatedBy.includes(persona)
                          ? `♥ Appreciated (${item.appreciatedBy.length})`
                          : `♥ Appreciate (+5)`}
                      </Text>
                    </TouchableOpacity>
                  )}

                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => {
                      setAiActionsUsed((c) => Math.min(720, c + 1));
                      setStories((prev) =>
                        prev.map((s) =>
                          s.id === item.id
                            ? {
                                ...s,
                                aiSummary: `Grounded Summary (${s.title}): ${s.summary} [Verified source: ${s.authorName}, Rev #${s.revisionNumber}]`,
                              }
                            : s
                        )
                      );
                    }}
                  >
                    <Text style={styles.outlineBtnText}>✨ Saathi Summary</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))}
          </View>
        )}

        {/* WIREFRAME 02: STORY DETAIL VIEW */}
        {activeTab === 'PULSE' && selectedStory && !composingInviteStory && (
          <View style={styles.card}>
            <TouchableOpacity onPress={() => setSelectedStoryId(null)}>
              <Text style={styles.backLink}>{'< Back to Your Campfire'}</Text>
            </TouchableOpacity>
            <Text style={styles.wireframeTag}>02 / STORY DETAIL</Text>

            <View style={[styles.authorHeaderRow, { marginTop: 8 }]}>
              <PersonaPortrait personaId={selectedStory.authorId} size={48} />
              <View style={{ flex: 1, marginLeft: 10 }}>
                <Text style={styles.authorNameText}>
                  {selectedStory.authorName} · {selectedStory.authorFunction} (
                  {selectedStory.authorRegion})
                </Text>
                <Text style={styles.audienceSubText}>
                  Published Rev #{selectedStory.revisionNumber} · {selectedStory.audienceName}
                </Text>
              </View>
            </View>

            <Text style={styles.h1}>{selectedStory.title}</Text>
            <View style={styles.whyBadge}>
              <Text style={styles.whyBadgeText}>{selectedStory.whyThis}</Text>
            </View>
            <Text style={[styles.bodyText, { marginTop: 10 }]}>
              {selectedStory.bodyText}
            </Text>

            {selectedStory.hasAudio && (
              <View style={styles.audioBox}>
                <View style={styles.rowBetween}>
                  <Text style={styles.boldText}>
                    Optional Audio ({selectedStory.durationSec}s)
                  </Text>
                  <View style={{ flexDirection: 'row' }}>
                    <TouchableOpacity
                      style={[styles.primaryBtnSmall, { marginRight: 6 }]}
                      onPress={() => handleToggleAudio(selectedStory.title)}
                    >
                      <Text style={styles.primaryBtnText}>
                        {isPlayingAudio && activeAudioTitle === selectedStory.title
                          ? '⏸ Pause'
                          : '▶ Play'}
                      </Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.outlineBtnSmall}
                      onPress={() => setShowStoryTranscript(!showStoryTranscript)}
                    >
                      <Text style={styles.outlineBtnText}>Transcript</Text>
                    </TouchableOpacity>
                  </View>
                </View>
                {showStoryTranscript && (
                  <Text style={[styles.metaText, { marginTop: 8 }]}>
                    {selectedStory.transcriptText}
                  </Text>
                )}
              </View>
            )}

            {selectedStory.inviteOpen && (
              <View style={styles.inviteOfferBox}>
                <Text style={styles.h2}>Ask me about this</Text>
                <Text style={styles.bodyText}>
                  15 minutes · {remainingPlaces} places available this week · Voluntary
                </Text>
                {selectedStory.authorId === persona ? (
                  <View style={styles.disabledNotice}>
                    <Text style={styles.disabledNoticeText}>
                      This is your published story. Switch persona in the top strip to send a 15-minute invitation.
                    </Text>
                  </View>
                ) : remainingPlaces > 0 && !hostPaused ? (
                  <TouchableOpacity
                    style={styles.primaryBtn}
                    onPress={() => setComposingInviteStoryId(selectedStory.id)}
                  >
                    <Text style={styles.primaryBtnText}>
                      Send a 15-minute invitation
                    </Text>
                  </TouchableOpacity>
                ) : (
                  <View style={styles.disabledNotice}>
                    <Text style={styles.disabledNoticeText}>
                      Offer closed: Host availability is paused or all places are filled.
                    </Text>
                  </View>
                )}
              </View>
            )}
          </View>
        )}

        {/* WIREFRAME 03: INVITATION COMPOSER */}
        {activeTab === 'PULSE' && composingInviteStory && (
          <View style={styles.card}>
            <TouchableOpacity onPress={() => setComposingInviteStoryId(null)}>
              <Text style={styles.backLink}>{'< Back to Story'}</Text>
            </TouchableOpacity>
            <Text style={styles.wireframeTag}>03 / SEND AN INVITATION</Text>
            <View style={[styles.authorHeaderRow, { marginTop: 8 }]}>
              <PersonaPortrait personaId={composingInviteStory.authorId} size={44} />
              <View style={{ flex: 1, marginLeft: 10 }}>
                <Text style={styles.h2}>
                  To: {composingInviteStory.authorName} ({composingInviteStory.authorRegion})
                </Text>
                <Text style={styles.metaText}>
                  Topic: {composingInviteStory.title} (15 min)
                </Text>
              </View>
            </View>

            <TextInput
              style={[styles.textInput, { height: 95, marginTop: 10 }]}
              multiline
              value={inviteMessage}
              onChangeText={setInviteMessage}
            />
            {inviteAiLabel && (
              <Text style={[styles.whyBadgeText, { marginTop: 6 }]}>
                ✨ {inviteAiLabel}
              </Text>
            )}

            <View style={styles.actionRowWrap}>
              <TouchableOpacity
                style={styles.outlineBtnSmall}
                onPress={() => {
                  setAiActionsUsed((c) => Math.min(720, c + 1));
                  setInviteMessage(
                    `Hi ${composingInviteStory.authorName.split(' ')[0]}, your story on "${composingInviteStory.title}" matched my team learning interest. Would you have 15 minutes this week to share how you started it?`
                  );
                  setInviteAiLabel(
                    `AI-Assisted Draft (Pilot Budget: ${aiActionsUsed + 1}/720 actions used)`
                  );
                }}
              >
                <Text style={styles.outlineBtnText}>✨ Suggest with AI</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.outlineBtnSmall}
                onPress={() => {
                  setInviteMessage(
                    `Hi ${composingInviteStory.authorName.split(' ')[0]}, I read your Campfire story and would appreciate a 15-minute chat when convenient.`
                  );
                  setInviteAiLabel('Deterministic Non-AI Template Fallback ($0 cost)');
                }}
              >
                <Text style={styles.outlineBtnText}>Use Non-AI Template</Text>
              </TouchableOpacity>
            </View>

            <TouchableOpacity
              style={styles.primaryBtn}
              onPress={() => {
                setInviteSenderId(persona);
                setInviteHostId(composingInviteStory.authorId);
                setInviteTopic(composingInviteStory.title);
                setRemainingPlaces(composingInviteStory.invitePlaces ?? 2);
                setSenderConfirmed(false);
                setHostConfirmed(false);
                setRequestState('PENDING');
                handleSelectTab('CONNECT');
                setConnectSubTab('SENT');
                setBannerMsg(
                  `Invitation sent to ${composingInviteStory.authorName} (Idempotency-Key verified)! Switch to ${composingInviteStory.authorName.split(' ')[0]} to Accept.`
                );
              }}
            >
              <Text style={styles.primaryBtnText}>Send Invitation</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* ===================================================================
            TAB 2: CHAMPIONS ("Challenge & Points" — Wireframes 10 & 11)
           =================================================================== */}
        {activeTab === 'CHAMPIONS' && (
          <View>
            {/* 10 / Weekly Campfire Challenge Card */}
            <View style={styles.card}>
              <Text style={styles.wireframeTag}>
                10 / WEEKLY CAMPFIRE CHALLENGE (2026-11-16 UTC)
              </Text>
              <Text style={styles.h1}>Beyond My Job Title</Text>
              <Text style={styles.bodyText}>
                Share a hobby, a lesson, or someone who helped you beyond your immediate role.
                Submitting earns 0 pts; human review + publication unlocks +20 pts & First Spark.
              </Text>

              {challengeEntry && (
                <View style={styles.whyBadge}>
                  <Text style={styles.boldText}>
                    Your Entry: "{challengeEntry.title}" (Rev #{challengeEntry.revisionNumber})
                  </Text>
                  <Text style={styles.whyBadgeText}>
                    Status: {challengeEntry.status} · Points Awarded:{' '}
                    {challengeEntry.status === 'PUBLISHED' ? '+20 pts' : '0 pts'}
                  </Text>
                  {challengeEntry.status !== 'PUBLISHED' && (
                    <View style={styles.actionRowWrap}>
                      <TouchableOpacity
                        style={styles.primaryBtnSmall}
                        onPress={() => {
                          setPersona('usr_priya');
                          handleSelectTab('PORTAL');
                          setBannerMsg(
                            'Switched to Priya Nair (Reviewer). Approve Revision in the Portal Queue!'
                          );
                        }}
                      >
                        <Text style={styles.primaryBtnText}>
                          1. Switch to Priya (Approve) →
                        </Text>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={[styles.primaryBtnSmall, { backgroundColor: '#047857' }]}
                        onPress={() => {
                          setPersona('usr_marcus');
                          handleSelectTab('PORTAL');
                          setBannerMsg(
                            'Switched to Marcus Vance (Publisher). Publish the Approved Revision for +20 pts!'
                          );
                        }}
                      >
                        <Text style={styles.primaryBtnText}>
                          2. Switch to Marcus (Publish +20) →
                        </Text>
                      </TouchableOpacity>
                    </View>
                  )}
                </View>
              )}

              {(!challengeEntry ||
                challengeEntry.status === 'DRAFT' ||
                challengeEntry.status === 'WITHDRAWN') && (
                <View style={{ marginTop: 10 }}>
                  <Text style={styles.sectionLabel}>CHALLENGE STORY TITLE</Text>
                  <TextInput
                    style={styles.textInput}
                    value={challengeTitle}
                    onChangeText={setChallengeTitle}
                  />
                  <Text style={[styles.sectionLabel, { marginTop: 10 }]}>
                    CHALLENGE STORY BODY
                  </Text>
                  <TextInput
                    style={[styles.textInput, { height: 85 }]}
                    multiline
                    value={challengeBody}
                    onChangeText={setChallengeBody}
                  />
                  <TouchableOpacity
                    style={styles.primaryBtn}
                    onPress={handleSubmitChallenge}
                  >
                    <Text style={styles.primaryBtnText}>
                      Submit Challenge Story for Review (0 pts now, +20 on Publish)
                    </Text>
                  </TouchableOpacity>
                </View>
              )}
            </View>

            {/* Personal Weekly Progress & Badges Card */}
            <View style={styles.card}>
              <View style={styles.rowBetween}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.wireframeTag}>WEEKLY RECOGNITION LEDGER</Text>
                  <Text style={styles.h2}>
                    Your Progress ({currentUser.displayName})
                  </Text>
                </View>
                <Text style={styles.bigScoreText}>{myRec.totalPoints} / 60 pts</Text>
              </View>

              <View style={styles.scoreGridRow}>
                <View style={styles.scoreStatBox}>
                  <Text style={styles.scoreStatLabel}>Published Story</Text>
                  <Text style={styles.scoreStatValue}>
                    +{myRec.contributionPoints}/20
                  </Text>
                </View>
                <View style={styles.scoreStatBox}>
                  <Text style={styles.scoreStatLabel}>Give Apprec.</Text>
                  <Text style={styles.scoreStatValue}>+{myRec.givingPoints}/15</Text>
                </View>
                <View style={styles.scoreStatBox}>
                  <Text style={styles.scoreStatLabel}>Receive Apprec.</Text>
                  <Text style={styles.scoreStatValue}>
                    +{myRec.receivingPoints}/25
                  </Text>
                </View>
              </View>

              <Text style={[styles.sectionLabel, { marginTop: 12 }]}>
                UNLOCKED MILESTONE BADGES
              </Text>
              <View style={styles.roleChipRow}>
                {myRec.badges.length === 0 ? (
                  <Text style={styles.metaText}>
                    No badges yet — publish a challenge story for ★ First Spark or appreciate cross-team stories for ★ Connection Maker!
                  </Text>
                ) : (
                  myRec.badges.map((b) => (
                    <View key={b} style={styles.goldBadgeChip}>
                      <Text style={styles.goldBadgeText}>★ {b}</Text>
                    </View>
                  ))
                )}
              </View>
            </View>

            {/* 11 / Community Champions Top-5 Opt-In Leaderboard */}
            <View style={styles.card}>
              <View style={styles.rowBetween}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.wireframeTag}>
                    11 / COMMUNITY CHAMPIONS (TOP 5 OPT-IN)
                  </Text>
                  <Text style={styles.h2}>Weekly Champions</Text>
                </View>
                <TouchableOpacity
                  style={styles.outlineBtnSmall}
                  onPress={() => {
                    const nextVal = !optInLeaderboard[persona];
                    setOptInLeaderboard((prev) => ({ ...prev, [persona]: nextVal }));
                    setBannerMsg(
                      nextVal
                        ? 'Your ranking is now visible on the Community Champions leaderboard.'
                        : 'Opted out of public leaderboard — your private points remain intact!'
                    );
                  }}
                >
                  <Text style={styles.outlineBtnText}>
                    {optInLeaderboard[persona] ? 'Hide Me' : 'Show Me'}
                  </Text>
                </TouchableOpacity>
              </View>

              {leaderboardRows.map((row, idx) => (
                <View
                  key={row.userId}
                  style={[
                    styles.leaderboardRow,
                    row.userId === persona && styles.leaderboardRowSelf,
                  ]}
                >
                  <View style={styles.rankCircle}>
                    <Text style={styles.rankCircleText}>#{idx + 1}</Text>
                  </View>
                  <PersonaPortrait personaId={row.userId} size={38} />
                  <View style={{ flex: 1, marginLeft: 10 }}>
                    <Text style={styles.authorNameText}>
                      {row.displayName} ({row.region})
                    </Text>
                    <Text style={styles.audienceSubText}>
                      {row.functionName}
                      {row.badges.length > 0 ? ` · ★ ${row.badges.join(', ')}` : ''}
                    </Text>
                  </View>
                  <Text style={styles.leaderboardPts}>{row.points} pts</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        {/* ===================================================================
            TAB 3: LISTEN ("Mastek Radio & Culture Cast" — Wireframes 07 & 08)
           =================================================================== */}
        {activeTab === 'LISTEN' && (
          <View>
            <View style={styles.subTabRow}>
              <TouchableOpacity
                style={[styles.subTabBtn, listenMode === 'RADIO' && styles.subTabBtnActive]}
                onPress={() => setListenMode('RADIO')}
              >
                <Text
                  style={[
                    styles.subTabText,
                    listenMode === 'RADIO' && styles.subTabTextActive,
                  ]}
                >
                  07 / Mastek Radio (12m)
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  styles.subTabBtn,
                  listenMode === 'CULTURE_CAST' && styles.subTabBtnActive,
                ]}
                onPress={() => setListenMode('CULTURE_CAST')}
              >
                <Text
                  style={[
                    styles.subTabText,
                    listenMode === 'CULTURE_CAST' && styles.subTabTextActive,
                  ]}
                >
                  08 / Culture Cast
                </Text>
              </TouchableOpacity>
            </View>

            {listenMode === 'RADIO' ? (
              <View style={styles.card}>
                <Text style={styles.wireframeTag}>07 / MASTEK RADIO · COMMUTE EDITION</Text>
                <Text style={styles.h1}>Now Playing: Two-Minute Product & Delivery Brief</Text>
                <Text style={styles.metaText}>
                  Curated 12-minute queue · Resumes across Web & Mobile · No auto-play
                </Text>

                <View style={styles.actionRowWrap}>
                  <TouchableOpacity
                    style={styles.primaryBtnSmall}
                    onPress={() =>
                      handleToggleAudio('Mastek Radio — Morning Commute Queue (12m)')
                    }
                  >
                    <Text style={styles.primaryBtnText}>
                      {isPlayingAudio ? '⏸ Pause Radio' : '▶ Play Mastek Radio'}
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => setListenMode('CULTURE_CAST')}
                  >
                    <Text style={styles.outlineBtnText}>Switch to Culture Cast →</Text>
                  </TouchableOpacity>
                </View>

                <View style={[styles.audioBox, { marginTop: 12 }]}>
                  <Text style={styles.boldText}>Up Next in Your 12-Minute Queue:</Text>
                  <Text style={styles.bodyText}>
                    1. Morning Tech & Delivery Brief (2m){'\n'}
                    2. Culture Cast Ep 03 — Quiet Leadership (8m){'\n'}
                    3. Story Spotlight: Daniel Wright on Inclusive Retros (2m)
                  </Text>
                </View>
              </View>
            ) : (
              <View style={styles.card}>
                <Text style={styles.wireframeTag}>08 / CULTURE CAST EPISODE PLAYER</Text>
                <View style={[styles.authorHeaderRow, { marginTop: 6 }]}>
                  <PersonaPortrait personaId="usr_marcus" size={42} />
                  <View style={{ flex: 1, marginLeft: 10 }}>
                    <Text style={styles.h2}>
                      Ep 03: Quiet Leadership in Hybrid Squads
                    </Text>
                    <Text style={styles.metaText}>
                      Hosted by Marcus Vance & Daniel Wright · Published Rev #2
                    </Text>
                  </View>
                </View>

                <View style={styles.scrubberBox}>
                  <Text style={styles.scrubberText}>
                    {formatTime(castPositionSec)} ━━━━━━●─────────{' '}
                    {formatTime(castDurationSec)} ({playbackSpeed})
                  </Text>
                  <View style={styles.actionRowWrap}>
                    <TouchableOpacity
                      style={styles.outlineBtnSmall}
                      onPress={() => setCastPositionSec(Math.max(0, castPositionSec - 15))}
                    >
                      <Text style={styles.outlineBtnText}>-15s</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.primaryBtnSmall}
                      onPress={() =>
                        handleToggleAudio('Culture Cast Ep 03 — Quiet Leadership in Hybrid Squads')
                      }
                    >
                      <Text style={styles.primaryBtnText}>
                        {isPlayingAudio ? '⏸ Pause' : '▶ Play / Resume'}
                      </Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.outlineBtnSmall}
                      onPress={() =>
                        setCastPositionSec(Math.min(castDurationSec, castPositionSec + 15))
                      }
                    >
                      <Text style={styles.outlineBtnText}>+15s</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.outlineBtnSmall}
                      onPress={() =>
                        setPlaybackSpeed(
                          playbackSpeed === '1x'
                            ? '1.25x'
                            : playbackSpeed === '1.25x'
                            ? '1.5x'
                            : '1x'
                        )
                      }
                    >
                      <Text style={styles.outlineBtnText}>Speed: {playbackSpeed}</Text>
                    </TouchableOpacity>
                  </View>
                </View>

                <Text style={[styles.sectionLabel, { marginTop: 12 }]}>
                  EPISODE CHAPTERS (TAP TO JUMP)
                </Text>
                {[
                  { label: '00:00 · Why hybrid retros go quiet', sec: 0 },
                  { label: '02:10 · The 4-minute silent writing habit', sec: 130 },
                  { label: '05:30 · Takeaways for engineering leads', sec: 330 },
                ].map((ch) => (
                  <TouchableOpacity
                    key={ch.label}
                    style={styles.chapterBtn}
                    onPress={() => {
                      setActiveAudioTitle('Culture Cast Ep 03 — Quiet Leadership in Hybrid Squads');
                      setCastPositionSec(ch.sec);
                      setIsPlayingAudio(true);
                    }}
                  >
                    <Text style={styles.chapterBtnText}>▶ {ch.label}</Text>
                  </TouchableOpacity>
                ))}

                <TouchableOpacity
                  style={[styles.outlineBtnSmall, { marginTop: 10, alignSelf: 'flex-start' }]}
                  onPress={() => setShowCastTranscript(!showCastTranscript)}
                >
                  <Text style={styles.outlineBtnText}>
                    {showCastTranscript ? 'Hide Transcript' : 'Show Verified Transcript'}
                  </Text>
                </TouchableOpacity>
                {showCastTranscript && (
                  <View style={styles.audioBox}>
                    <Text style={styles.metaText}>
                      [02:10] Daniel Wright: Giving everyone four quiet minutes to write notes before anyone speaks completely levels the playing field between remote and in-room engineers...
                    </Text>
                  </View>
                )}

                <TouchableOpacity
                  style={styles.primaryBtn}
                  onPress={() => {
                    handleSelectTab('PULSE');
                    setSelectedStoryId('story_01');
                  }}
                >
                  <Text style={styles.primaryBtnText}>
                    Explore the Related Story (Daniel Wright) →
                  </Text>
                </TouchableOpacity>
              </View>
            )}
          </View>
        )}

        {/* ===================================================================
            TAB 4: CONNECT ("Connections & Check-In" — Wireframes 04 & 06)
           =================================================================== */}
        {activeTab === 'CONNECT' && (
          <View>
            <View style={styles.subTabRow}>
              {(['RECEIVED', 'SENT', 'ACCEPTED'] as const).map((st) => (
                <TouchableOpacity
                  key={st}
                  style={[styles.subTabBtn, connectSubTab === st && styles.subTabBtnActive]}
                  onPress={() => {
                    setConnectSubTab(st);
                    setShowCheckInScreen(false);
                  }}
                >
                  <Text
                    style={[
                      styles.subTabText,
                      connectSubTab === st && styles.subTabTextActive,
                    ]}
                  >
                    {st}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            {showCheckInScreen ? (
              /* WIREFRAME 06: PRIVATE FOLLOW-UP CHECK-IN */
              <View style={styles.card}>
                <TouchableOpacity onPress={() => setShowCheckInScreen(false)}>
                  <Text style={styles.backLink}>{'< Back to Connections'}</Text>
                </TouchableOpacity>
                <Text style={styles.wireframeTag}>06 / PRIVATE FOLLOW-UP CHECK-IN</Text>
                <Text style={styles.h1}>
                  Recording as {currentUser.displayName}
                </Text>
                <Text style={styles.metaText}>
                  Private to participants · Counted as useful ONLY when both {inviteSender.displayName.split(' ')[0]} and {inviteHost.displayName.split(' ')[0]} confirm Happened = Yes & Useful = Yes.
                </Text>

                <Text style={[styles.sectionLabel, { marginTop: 12 }]}>
                  1. DID THE 15-MINUTE CONVERSATION HAPPEN?
                </Text>
                <View style={styles.actionRowWrap}>
                  {(['YES', 'NO', 'NOT_YET'] as const).map((opt) => (
                    <TouchableOpacity
                      key={opt}
                      style={[
                        styles.filterPill,
                        happenedChoice === opt && styles.filterPillActive,
                      ]}
                      onPress={() => setHappenedChoice(opt)}
                    >
                      <Text
                        style={[
                          styles.filterPillText,
                          happenedChoice === opt && styles.filterPillTextActive,
                        ]}
                      >
                        {opt}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>

                {happenedChoice === 'YES' && (
                  <View style={{ marginTop: 10 }}>
                    <Text style={styles.sectionLabel}>2. WAS IT USEFUL?</Text>
                    <View style={styles.actionRowWrap}>
                      {[
                        { label: 'Yes, useful', val: true },
                        { label: 'No', val: false },
                      ].map((u) => (
                        <TouchableOpacity
                          key={u.label}
                          style={[
                            styles.filterPill,
                            usefulChoice === u.val && styles.filterPillActive,
                          ]}
                          onPress={() => setUsefulChoice(u.val)}
                        >
                          <Text
                            style={[
                              styles.filterPillText,
                              usefulChoice === u.val && styles.filterPillTextActive,
                            ]}
                          >
                            {u.label}
                          </Text>
                        </TouchableOpacity>
                      ))}
                    </View>
                  </View>
                )}

                <View style={styles.actionRowWrap}>
                  <TouchableOpacity
                    style={styles.primaryBtnSmall}
                    onPress={() => {
                      const ok = happenedChoice === 'YES' && usefulChoice;
                      setSenderConfirmed(ok);
                      setShowCheckInScreen(false);
                      setBannerMsg(
                        ok && hostConfirmed
                          ? `Both ${inviteSender.displayName.split(' ')[0]} & ${inviteHost.displayName.split(' ')[0]} confirmed! Counted as 1 USEFUL_CONFIRMED connection!`
                          : `Saved ${inviteSender.displayName.split(' ')[0]}’s private confirmation. Confirm as ${inviteHost.displayName.split(' ')[0]} next!`
                      );
                    }}
                  >
                    <Text style={styles.primaryBtnText}>
                      Save as {inviteSender.displayName.split(' ')[0]}
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => {
                      const ok = happenedChoice === 'YES' && usefulChoice;
                      setHostConfirmed(ok);
                      setShowCheckInScreen(false);
                      setBannerMsg(
                        senderConfirmed && ok
                          ? `Both ${inviteSender.displayName.split(' ')[0]} & ${inviteHost.displayName.split(' ')[0]} confirmed! Counted as 1 USEFUL_CONFIRMED connection!`
                          : `Saved ${inviteHost.displayName.split(' ')[0]}’s private confirmation.`
                      );
                    }}
                  >
                    <Text style={styles.outlineBtnText}>
                      Save as {inviteHost.displayName.split(' ')[0]}
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.primaryBtnSmall, { backgroundColor: '#047857' }]}
                    onPress={() => {
                      setSenderConfirmed(true);
                      setHostConfirmed(true);
                      setShowCheckInScreen(false);
                      setBannerMsg(
                        `Both ${inviteSender.displayName.split(' ')[0]} & ${inviteHost.displayName.split(' ')[0]} confirmed Happened=Yes & Useful=Yes! Counted as 1 verified Useful Connection!`
                      );
                    }}
                  >
                    <Text style={styles.primaryBtnText}>
                      Confirm Both ({inviteSender.displayName.split(' ')[0]} +{' '}
                      {inviteHost.displayName.split(' ')[0]}) ✓
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>
            ) : (
              <View style={styles.card}>
                <Text style={styles.wireframeTag}>
                  04 / CONNECTIONS ({connectSubTab})
                </Text>

                {connectSubTab === 'RECEIVED' && requestState !== 'PENDING' && (
                  <View style={{ marginTop: 8 }}>
                    <Text style={styles.h2}>No pending invitations received</Text>
                    <Text style={styles.bodyText}>
                      Current request status between {inviteSender.displayName} and{' '}
                      {inviteHost.displayName} is {requestState}.
                    </Text>
                    <View style={styles.actionRowWrap}>
                      {requestState === 'ACCEPTED' && (
                        <TouchableOpacity
                          style={styles.primaryBtnSmall}
                          onPress={() => setConnectSubTab('ACCEPTED')}
                        >
                          <Text style={styles.primaryBtnText}>
                            View Accepted Connection →
                          </Text>
                        </TouchableOpacity>
                      )}
                      <TouchableOpacity
                        style={styles.outlineBtnSmall}
                        onPress={() => handleSelectTab('PULSE')}
                      >
                        <Text style={styles.outlineBtnText}>Browse Campfire Feed</Text>
                      </TouchableOpacity>
                    </View>
                  </View>
                )}

                {connectSubTab === 'RECEIVED' && requestState === 'PENDING' && (
                  <View>
                    <View style={[styles.authorHeaderRow, { marginTop: 8 }]}>
                      <PersonaPortrait personaId={inviteSenderId} size={46} />
                      <View style={{ flex: 1, marginLeft: 10 }}>
                        <Text style={styles.h2}>
                          {inviteSender.displayName} → {inviteHost.displayName} (15 min)
                        </Text>
                        <Text style={styles.metaText}>
                          Topic: {inviteTopic} · Status: {requestState} ({remainingPlaces}{' '}
                          places left)
                        </Text>
                      </View>
                    </View>

                    <View style={styles.audioBox}>
                      <Text style={styles.bodyText}>"{inviteMessage}"</Text>
                    </View>

                    {persona === inviteHostId || currentUser.roles.includes('ADMIN') ? (
                      <View style={styles.actionRowWrap}>
                        <TouchableOpacity
                          style={styles.primaryBtnSmall}
                          onPress={() => {
                            if (remainingPlaces <= 0) {
                              setBannerMsg('409 Conflict: Host invitation is at full capacity.');
                              return;
                            }
                            setRemainingPlaces((p) => Math.max(0, p - 1));
                            setRequestState('ACCEPTED');
                            setConnectSubTab('ACCEPTED');
                            setAuditLogs((prev) => [
                              `17 Nov · ${inviteHost.displayName} ACCEPTED ${inviteSender.displayName}’s 15-min invitation (1-TX SELECT FOR UPDATE; remaining capacity = ${Math.max(0, remainingPlaces - 1)}).`,
                              ...prev,
                            ]);
                            setBannerMsg(
                              'Accepted in 1-Transaction Pessimistic Lock! Next: Record Private Follow-Up Check-in.'
                            );
                          }}
                        >
                          <Text style={styles.primaryBtnText}>Accept (1-TX Lock)</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                          style={styles.outlineBtnSmall}
                          onPress={() => {
                            setRequestState('DECLINED');
                            setBannerMsg(
                              `Declined invitation from ${inviteSender.displayName} (capacity preserved).`
                            );
                          }}
                        >
                          <Text style={styles.outlineBtnText}>Decline</Text>
                        </TouchableOpacity>
                      </View>
                    ) : (
                      <View style={styles.disabledNotice}>
                        <Text style={styles.disabledNoticeText}>
                          Viewing as {currentUser.displayName}. Only the host (
                          {inviteHost.displayName}) can accept or decline this received invite.
                        </Text>
                        <TouchableOpacity
                          style={[styles.primaryBtnSmall, { marginTop: 8, alignSelf: 'flex-start' }]}
                          onPress={() => {
                            setPersona(inviteHostId);
                            setBannerMsg(
                              `Switched to ${inviteHost.displayName} (Host) — ready to Accept or Decline!`
                            );
                          }}
                        >
                          <Text style={styles.primaryBtnText}>
                            Switch to {inviteHost.displayName.split(' ')[0]} (Host) →
                          </Text>
                        </TouchableOpacity>
                      </View>
                    )}
                  </View>
                )}

                {connectSubTab === 'SENT' &&
                  (requestState === 'NONE' || requestState === 'ACCEPTED') && (
                    <View style={{ marginTop: 8 }}>
                      <Text style={styles.h2}>
                        {requestState === 'ACCEPTED'
                          ? `Invitation Accepted by ${inviteHost.displayName}`
                          : 'No active sent invitations'}
                      </Text>
                      <Text style={styles.bodyText}>
                        {requestState === 'ACCEPTED'
                          ? `Your 15-minute request with ${inviteHost.displayName} was accepted! Head to Accepted to record your follow-up check-in.`
                          : 'Open any published story in Campfire Home and tap "Send a 15-minute invitation".'}
                      </Text>
                      <View style={styles.actionRowWrap}>
                        {requestState === 'ACCEPTED' && (
                          <TouchableOpacity
                            style={styles.primaryBtnSmall}
                            onPress={() => setConnectSubTab('ACCEPTED')}
                          >
                            <Text style={styles.primaryBtnText}>
                              Go to Accepted Connection →
                            </Text>
                          </TouchableOpacity>
                        )}
                        <TouchableOpacity
                          style={styles.outlineBtnSmall}
                          onPress={() => handleSelectTab('PULSE')}
                        >
                          <Text style={styles.outlineBtnText}>Open Campfire Feed</Text>
                        </TouchableOpacity>
                      </View>
                    </View>
                  )}

                {connectSubTab === 'SENT' &&
                  (requestState === 'PENDING' || requestState === 'DECLINED') && (
                    <View>
                      <View style={[styles.authorHeaderRow, { marginTop: 8 }]}>
                        <PersonaPortrait personaId={inviteHostId} size={46} />
                        <View style={{ flex: 1, marginLeft: 10 }}>
                          <Text style={styles.h2}>
                            To: {inviteHost.displayName} (from {inviteSender.displayName})
                          </Text>
                          <Text style={styles.metaText}>
                            Topic: {inviteTopic} · Status: {requestState}
                          </Text>
                        </View>
                      </View>
                      <View style={styles.audioBox}>
                        <Text style={styles.bodyText}>"{inviteMessage}"</Text>
                      </View>
                      <View style={styles.actionRowWrap}>
                        {requestState === 'PENDING' && (
                          <>
                            <TouchableOpacity
                              style={styles.outlineBtnSmall}
                              onPress={() => {
                                setRequestState('NONE');
                                setBannerMsg('Withdrew outgoing 15-minute invitation.');
                              }}
                            >
                              <Text style={[styles.outlineBtnText, { color: '#b91c1c' }]}>
                                Withdraw Request
                              </Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                              style={styles.primaryBtnSmall}
                              onPress={() => {
                                setPersona(inviteHostId);
                                setConnectSubTab('RECEIVED');
                                setBannerMsg(
                                  `Switched to ${inviteHost.displayName} (Host) in Received tab.`
                                );
                              }}
                            >
                              <Text style={styles.primaryBtnText}>
                                Switch to {inviteHost.displayName.split(' ')[0]} (Accept) →
                              </Text>
                            </TouchableOpacity>
                          </>
                        )}
                        {requestState === 'DECLINED' && (
                          <TouchableOpacity
                            style={styles.primaryBtnSmall}
                            onPress={() => handleSelectTab('PULSE')}
                          >
                            <Text style={styles.primaryBtnText}>
                              Send New Request from Stories →
                            </Text>
                          </TouchableOpacity>
                        )}
                      </View>
                    </View>
                  )}

                {connectSubTab === 'ACCEPTED' && requestState !== 'ACCEPTED' && (
                  <View style={{ marginTop: 8 }}>
                    <Text style={styles.h2}>No accepted connections yet</Text>
                    <Text style={styles.bodyText}>
                      Current request status is {requestState}. Switch to the host (
                      {inviteHost.displayName}) in the Received tab to accept the pending request.
                    </Text>
                    <TouchableOpacity
                      style={[styles.primaryBtnSmall, { marginTop: 10, alignSelf: 'flex-start' }]}
                      onPress={() => setConnectSubTab('RECEIVED')}
                    >
                      <Text style={styles.primaryBtnText}>Review Received Requests →</Text>
                    </TouchableOpacity>
                  </View>
                )}

                {connectSubTab === 'ACCEPTED' && requestState === 'ACCEPTED' && (
                  <View>
                    <View style={[styles.authorHeaderRow, { marginTop: 8 }]}>
                      <PersonaPortrait personaId={inviteHostId} size={46} />
                      <View style={{ flex: 1, marginLeft: 10 }}>
                        <Text style={styles.h2}>
                          {inviteSender.displayName} ↔ {inviteHost.displayName} (15 min)
                        </Text>
                        <Text style={styles.metaText}>
                          Topic: {inviteTopic} · Status: ACCEPTED ({remainingPlaces} places left)
                        </Text>
                      </View>
                    </View>
                    <View style={styles.audioBox}>
                      <Text style={styles.bodyText}>"{inviteMessage}"</Text>
                    </View>
                    <View style={styles.whyBadge}>
                      <Text style={styles.whyBadgeText}>
                        Bilateral Outcome:{' '}
                        {bilateralUsefulConfirmed
                          ? '✓ USEFUL_CONFIRMED (Counted as 1 verified Useful Connection!)'
                          : `UNKNOWN (${inviteSender.displayName.split(' ')[0]} confirmed: ${
                              senderConfirmed ? 'Yes' : 'Pending'
                            } · ${inviteHost.displayName.split(' ')[0]} confirmed: ${
                              hostConfirmed ? 'Yes' : 'Pending'
                            })`}
                      </Text>
                    </View>
                    <TouchableOpacity
                      style={styles.primaryBtn}
                      onPress={() => setShowCheckInScreen(true)}
                    >
                      <Text style={styles.primaryBtnText}>
                        Complete Private Follow-Up Check-In (Wireframe 06) →
                      </Text>
                    </TouchableOpacity>
                  </View>
                )}
              </View>
            )}
          </View>
        )}

        {/* ===================================================================
            TAB 5: CONTRIBUTE ("Share a Story" — Wireframe 05)
           =================================================================== */}
        {activeTab === 'CONTRIBUTE' && (
          <View style={styles.card}>
            <Text style={styles.wireframeTag}>
              05 / CONTRIBUTE A STORY · LOCAL DRAFT AUTO-SAVED
            </Text>
            <Text style={styles.h1}>Share a 2-Minute Story</Text>
            <Text style={styles.metaText}>
              Author: {currentUser.displayName} ({currentUser.region}) · Approvals bind to exact revision numbers.
            </Text>

            <Text style={[styles.sectionLabel, { marginTop: 12 }]}>HEADLINE TITLE</Text>
            <TextInput
              style={styles.textInput}
              value={draftTitle}
              onChangeText={setDraftTitle}
            />

            <Text style={[styles.sectionLabel, { marginTop: 12 }]}>STORY BODY</Text>
            <TextInput
              style={[styles.textInput, { height: 95 }]}
              multiline
              value={draftStory}
              onChangeText={setDraftStory}
            />

            <Text style={[styles.sectionLabel, { marginTop: 12 }]}>TARGET AUDIENCE</Text>
            <View style={styles.actionRowWrap}>
              {(['Colleagues in the Pilot', 'All Employees'] as const).map((aud) => (
                <TouchableOpacity
                  key={aud}
                  style={[
                    styles.filterPill,
                    draftAudience === aud && styles.filterPillActive,
                  ]}
                  onPress={() => setDraftAudience(aud)}
                >
                  <Text
                    style={[
                      styles.filterPillText,
                      draftAudience === aud && styles.filterPillTextActive,
                    ]}
                  >
                    {aud}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <View style={styles.actionRowWrap}>
              <TouchableOpacity
                style={styles.outlineBtnSmall}
                onPress={() => {
                  setAiActionsUsed((c) => Math.min(720, c + 1));
                  setDraftStory(
                    '1. What changed: We started our sprint retro with 4 minutes of silent writing.\n2. What we learned: Quieter engineers surfaced concrete test automation fixes immediately.'
                  );
                }}
              >
                <Text style={styles.outlineBtnText}>✨ Polish with AI</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.outlineBtnSmall}
                onPress={() => setShowRevisionPreview(!showRevisionPreview)}
              >
                <Text style={styles.outlineBtnText}>
                  {showRevisionPreview ? 'Hide Preview' : 'Preview Exact Revision 1'}
                </Text>
              </TouchableOpacity>
            </View>

            {showRevisionPreview && (
              <View style={styles.whyBadge}>
                <Text style={styles.boldText}>
                  Exact Revision 1 Preview ({draftAudience})
                </Text>
                <Text style={styles.whyBadgeText}>
                  Title: {draftTitle}
                  {'\n'}
                  {draftStory}
                </Text>
              </View>
            )}

            <TouchableOpacity
              style={styles.primaryBtn}
              onPress={() => {
                if (draftTitle.trim().length < 5 || draftStory.trim().length < 20) {
                  setBannerMsg(
                    '422 Validation: Story title must be 5+ chars and body must be 20+ chars.'
                  );
                  return;
                }
                const newStory: StoryItem = {
                  id: `story_${Date.now().toString().slice(-4)}`,
                  kind: 'STORY',
                  authorId: persona,
                  authorName: currentUser.displayName,
                  authorFunction: currentUser.functionName,
                  authorRegion: currentUser.region,
                  audienceName: draftAudience,
                  category: 'FOR_YOU',
                  title: draftTitle.trim(),
                  summary: draftStory.trim().slice(0, 120) + '...',
                  bodyText: draftStory.trim(),
                  whyThis: `WHY THIS? Shared with ${draftAudience} by ${currentUser.displayName}`,
                  inviteOpen: true,
                  invitePlaces: 2,
                  status: 'IN_REVIEW',
                  revisionNumber: 1,
                  approvedBy: null,
                  appreciatedBy: [],
                };
                setStories((prev) => [newStory, ...prev]);
                setAuditLogs((prev) => [
                  `17 Nov · ${currentUser.displayName} submitted "${newStory.title}" Rev #1 to Publishing Portal Queue.`,
                  ...prev,
                ]);
                setBannerMsg(
                  `Submitted "${newStory.title}" Rev #1 to the Publishing Portal Queue! Switch to Priya (Reviewer) in Portal tab.`
                );
                handleSelectTab('PORTAL');
              }}
            >
              <Text style={styles.primaryBtnText}>
                Confirm & Submit Revision 1 for Review →
              </Text>
            </TouchableOpacity>
          </View>
        )}

        {/* ===================================================================
            TAB 6: PORTAL ("Publishing Portal" — Role-Based Queue, Metrics & Audit)
           =================================================================== */}
        {activeTab === 'PORTAL' && (
          <View>
            {!hasPortalRole ? (
              <View style={styles.card}>
                <Text style={[styles.wireframeTag, { color: '#b45309' }]}>
                  403 FORBIDDEN (SCOPED PORTAL ROLE REQUIRED)
                </Text>
                <Text style={styles.h1}>Publishing Portal Access Denied</Text>
                <Text style={styles.bodyText}>
                  Current persona {currentUser.displayName} holds roles [
                  {currentUser.roles.join(', ')}]. Accessing `/api/v1/portal/*` requires
                  REVIEWER, PUBLISHER, MODERATOR, or ADMIN.
                </Text>
                <View style={styles.actionRowWrap}>
                  <TouchableOpacity
                    style={styles.primaryBtnSmall}
                    onPress={() => setPersona('usr_priya')}
                  >
                    <Text style={styles.primaryBtnText}>
                      Switch to Priya Nair (Reviewer)
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.primaryBtnSmall, { backgroundColor: '#047857' }]}
                    onPress={() => setPersona('usr_marcus')}
                  >
                    <Text style={styles.primaryBtnText}>
                      Switch to Marcus Vance (Publisher)
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>
            ) : (
              <View>
                <View style={styles.subTabRow}>
                  {(['QUEUE', 'METRICS', 'AUDIT'] as const).map((pt) => (
                    <TouchableOpacity
                      key={pt}
                      style={[
                        styles.subTabBtn,
                        portalSubTab === pt && styles.subTabBtnActive,
                      ]}
                      onPress={() => setPortalSubTab(pt)}
                    >
                      <Text
                        style={[
                          styles.subTabText,
                          portalSubTab === pt && styles.subTabTextActive,
                        ]}
                      >
                        {pt === 'QUEUE'
                          ? 'Review Queue'
                          : pt === 'METRICS'
                          ? 'Pilot & AI Gates'
                          : 'Audit Log'}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>

                {portalSubTab === 'QUEUE' &&
                  stories.map((item) => (
                    <View key={item.id} style={styles.card}>
                      <View style={styles.authorHeaderRow}>
                        <PersonaPortrait personaId={item.authorId} size={40} />
                        <View style={{ flex: 1, marginLeft: 10 }}>
                          <Text style={styles.authorNameText}>
                            {item.title} (Rev #{item.revisionNumber})
                          </Text>
                          <Text style={styles.audienceSubText}>
                            Author: {item.authorName} · Status: {item.status} · Audience:{' '}
                            {item.audienceName}
                          </Text>
                        </View>
                      </View>
                      <Text style={styles.bodyText}>{item.bodyText}</Text>

                      <View style={styles.actionRowWrap}>
                        {item.status === 'IN_REVIEW' && (
                          <>
                            <TouchableOpacity
                              style={styles.primaryBtnSmall}
                              onPress={() => handlePortalReview(item, 'APPROVE')}
                            >
                              <Text style={styles.primaryBtnText}>
                                ✓ Approve Rev #{item.revisionNumber}
                              </Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                              style={styles.outlineBtnSmall}
                              onPress={() => handlePortalReview(item, 'REQUEST_CHANGES')}
                            >
                              <Text style={styles.outlineBtnText}>Request Changes</Text>
                            </TouchableOpacity>
                          </>
                        )}

                        {item.status === 'APPROVED' && (
                          <TouchableOpacity
                            style={[
                              styles.primaryBtnSmall,
                              { backgroundColor: '#047857' },
                            ]}
                            onPress={() => handlePortalPublish(item)}
                          >
                            <Text style={styles.primaryBtnText}>
                              🚀 Publish Now (+20 pts)
                            </Text>
                          </TouchableOpacity>
                        )}

                        {item.status === 'PUBLISHED' && (
                          <TouchableOpacity
                            style={styles.outlineBtnSmall}
                            onPress={() => handlePortalWithdraw(item)}
                          >
                            <Text style={[styles.outlineBtnText, { color: '#b91c1c' }]}>
                              Withdraw (-20 reversal)
                            </Text>
                          </TouchableOpacity>
                        )}
                      </View>
                    </View>
                  ))}

                {portalSubTab === 'METRICS' && (
                  <View style={styles.card}>
                    <Text style={styles.wireframeTag}>
                      PILOT OUTCOME GATES & AI 720-ACTION BUDGET
                    </Text>
                    <Text style={styles.h2}>4-Week Pilot Health</Text>
                    <Text style={styles.bodyText}>
                      • Verified Useful Connections: {bilateralUsefulConfirmed ? 2 : 1} (Target ≥ 20%){'\n'}
                      • Host Acceptance Rate: 100% (Target ≥ 50%){'\n'}
                      • AI Budget Ledger: {aiActionsUsed} / 720 actions used (Alert level: NORMAL){'\n'}
                      • 30-Case AI Trust Gate: 30/30 passed (0 unauthorized disclosures)
                    </Text>
                  </View>
                )}

                {portalSubTab === 'AUDIT' && (
                  <View style={styles.card}>
                    <Text style={styles.wireframeTag}>IMMUTABLE EDITORIAL & SECURITY AUDIT LOG</Text>
                    {auditLogs.map((log, i) => (
                      <View key={i} style={styles.auditRow}>
                        <Text style={styles.metaText}>{log}</Text>
                      </View>
                    ))}
                  </View>
                )}
              </View>
            )}
          </View>
        )}

        {/* ===================================================================
            TAB 2B: MAKE ME PRODUCTIVE (Section 14 · Proposal Pages 20–23)
           =================================================================== */}
        {activeTab === 'PRODUCTIVE' && (() => {
          const toIso = (d: Date) =>
            `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
              d.getDate()
            ).padStart(2, '0')}`;
          const nowDate = new Date();
          const todayIso = toIso(nowDate);
          const mon = new Date(nowDate);
          mon.setHours(12, 0, 0, 0);
          mon.setDate(mon.getDate() - ((mon.getDay() + 6) % 7));
          const weekArr = Array.from({ length: 7 }, (_, i) => {
            const x = new Date(mon);
            x.setDate(mon.getDate() + i);
            return x;
          });
          const myHabits = prodHabits.filter((h) => h.ownerId === persona);
          const isSched = (sch: 'daily' | 'weekdays' | 'weekends', d: Date) =>
            sch === 'daily' ||
            (sch === 'weekdays' ? d.getDay() > 0 && d.getDay() < 6 : d.getDay() === 0 || d.getDay() === 6);

          let dueThisWeek = 0;
          let checkedThisWeek = 0;
          for (const h of myHabits) {
            for (const d of weekArr) {
              const iso = toIso(d);
              if (isSched(h.schedule, d) && iso <= todayIso) {
                dueThisWeek++;
                if (h.days.includes(iso)) checkedThisWeek++;
              }
            }
          }

          const clampDur = (val: string, maxMin: number, fallback: number) => {
            const n = Number(val);
            if (!Number.isFinite(n) || n < 1) return fallback;
            return Math.min(maxMin, Math.floor(n));
          };

          const updateDurField = (
            field: 'focus' | 'short' | 'long' | 'reset',
            rawText: string
          ) => {
            const cleaned = rawText.replace(/[^0-9]/g, '');
            const maxMap = { focus: 120, short: 60, long: 60, reset: 30 };
            const setField =
              field === 'focus'
                ? setProdFocusMin
                : field === 'short'
                ? setProdShortMin
                : field === 'long'
                ? setProdLongMin
                : setProdResetMin;
            if (!cleaned) {
              setField('');
              return;
            }
            const clamped = Math.min(maxMap[field], Math.max(1, parseInt(cleaned, 10)));
            setField(String(clamped));
            const matchesMode =
              prodTimerMode === field ||
              (field === 'reset' && (prodTimerMode === 'breath' || prodTimerMode === 'mind'));
            if (!prodRunning && matchesMode) {
              setProdDone(false);
              setProdTotalSec(clamped * 60);
              setProdRemSec(clamped * 60);
            }
          };

          const chooseProdMode = (
            nextMode: 'focus' | 'short' | 'long' | 'breath' | 'mind',
            overrideMin?: number
          ) => {
            prodDeadlineRef.current = null;
            setProdRunning(false);
            setProdDone(false);
            setProdTimerMode(nextMode);
            const safeMin =
              overrideMin ??
              (nextMode === 'focus'
                ? clampDur(prodFocusMin, 120, 25)
                : nextMode === 'short'
                ? clampDur(prodShortMin, 60, 5)
                : nextMode === 'long'
                ? clampDur(prodLongMin, 60, 15)
                : clampDur(prodResetMin, 30, 2));
            setProdTotalSec(safeMin * 60);
            setProdRemSec(safeMin * 60);
          };

          const breathPhase = ['Breathe in (4s)', 'Hold gently (4s)', 'Breathe out (4s)', 'Hold gently (4s)'][
            Math.floor((prodTotalSec - prodRemSec) / 4) % 4
          ];
          const phaseText = prodDone
            ? 'Session complete. Choose your next step.'
            : !prodRunning
            ? prodRemSec < prodTotalSec
              ? 'Paused — resume when ready'
              : 'Ready when you are'
            : prodTimerMode === 'breath'
            ? breathPhase
            : prodTimerMode === 'mind'
            ? 'Notice your breath or nearby sounds. Gently return.'
            : 'One small step at a time';

          return (
            <View>
              <View style={styles.card}>
                <Text style={styles.wireframeTag}>
                  14 / MAKE ME PRODUCTIVE · PRIVATE PERSONAL SPACE
                </Text>
                <Text style={styles.h1}>Small habits. Time to focus.</Text>
                <Text style={styles.bodyText}>
                  Owner-only space for {currentUser.displayName}. No manager view, no recognition points, no leaderboard, and no AI analysis.
                </Text>
                <View style={styles.actionRowWrap}>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => {
                      setProdHabits((prev) => prev.filter((h) => h.ownerId !== persona));
                      setEditingHabitId(null);
                      setNewHabitName('');
                      setProdCompletedCount(0);
                      setBannerMsg(
                        `Cleared personal habits & completed counter for ${currentUser.displayName}.`
                      );
                    }}
                  >
                    <Text style={[styles.outlineBtnText, { color: '#b91c1c' }]}>
                      Clear My Demo Data
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => {
                      const alreadyHasDemo = prodHabits.some(
                        (h) => h.ownerId === persona && h.id === `hab_demo_${persona}`
                      );
                      if (!alreadyHasDemo) {
                        setProdHabits((prev) => [
                          ...prev,
                          {
                            id: `hab_demo_${persona}`,
                            ownerId: persona,
                            name: '10-min morning stretch',
                            schedule: 'weekdays',
                            days: [],
                          },
                        ]);
                      }
                      setBannerMsg(`Restored sample habit for ${currentUser.displayName}.`);
                    }}
                  >
                    <Text style={styles.outlineBtnText}>Restore Sample Habit</Text>
                  </TouchableOpacity>
                </View>
              </View>

              {/* 01 / MY HABITS */}
              <View style={styles.card}>
                <Text style={styles.wireframeTag}>01 / BUILD YOUR ROUTINE (MY HABITS)</Text>
                <View style={styles.rowBetween}>
                  <Text style={styles.h2}>Weekly Routine ({todayIso})</Text>
                  <View style={styles.goldBadgeChip}>
                    <Text style={styles.goldBadgeText}>
                      {checkedThisWeek} / {dueThisWeek} due this week
                    </Text>
                  </View>
                </View>

                {myHabits.length === 0 ? (
                  <Text style={styles.metaText}>
                    No habits added for {currentUser.displayName} yet. Add one below!
                  </Text>
                ) : (
                  myHabits.map((h) => (
                    <View key={h.id} style={styles.audioBox}>
                      <View style={styles.rowBetween}>
                        <View style={{ flex: 1 }}>
                          <Text style={styles.boldText}>{h.name}</Text>
                          <Text style={styles.metaText}>Schedule: {h.schedule}</Text>
                        </View>
                        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                          <TouchableOpacity
                            style={{ marginRight: 12 }}
                            onPress={() => {
                              setEditingHabitId(h.id);
                              setNewHabitName(h.name);
                              setNewHabitSchedule(h.schedule);
                            }}
                          >
                            <Text style={[styles.toastDismissText, { color: '#002180' }]}>
                              Edit
                            </Text>
                          </TouchableOpacity>
                          <TouchableOpacity
                            onPress={() => {
                              if (editingHabitId === h.id) {
                                setEditingHabitId(null);
                                setNewHabitName('');
                              }
                              setProdHabits((prev) => prev.filter((item) => item.id !== h.id));
                            }}
                          >
                            <Text style={[styles.toastDismissText, { color: '#b91c1c' }]}>
                              Delete
                            </Text>
                          </TouchableOpacity>
                        </View>
                      </View>
                      <ScrollView
                        horizontal
                        showsHorizontalScrollIndicator={false}
                        style={{ marginTop: 8 }}
                      >
                        {weekArr.map((d) => {
                          const iso = toIso(d);
                          const eligible = isSched(h.schedule, d) && iso <= todayIso;
                          const checked = h.days.includes(iso);
                          const dayLabel = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'][d.getDay()];
                          return (
                            <TouchableOpacity
                              key={iso}
                              disabled={!eligible}
                              onPress={() =>
                                setProdHabits((prev) =>
                                  prev.map((item) =>
                                    item.id !== h.id
                                      ? item
                                      : {
                                          ...item,
                                          days: checked
                                            ? item.days.filter((x) => x !== iso)
                                            : [...item.days, iso],
                                        }
                                  )
                                )
                              }
                              style={[
                                styles.filterPill,
                                checked && styles.filterPillActive,
                                !eligible && { opacity: 0.35 },
                              ]}
                            >
                              <Text
                                style={[
                                  styles.filterPillText,
                                  checked && styles.filterPillTextActive,
                                ]}
                              >
                                {checked ? `✓ ${dayLabel}` : dayLabel}
                              </Text>
                            </TouchableOpacity>
                          );
                        })}
                      </ScrollView>
                    </View>
                  ))
                )}

                <Text style={[styles.sectionLabel, { marginTop: 12 }]}>
                  {editingHabitId ? 'EDIT HABIT' : 'ADD HABIT'}
                </Text>
                <TextInput
                  style={styles.textInput}
                  value={newHabitName}
                  onChangeText={setNewHabitName}
                  placeholder="A small habit you want to build..."
                />
                <View style={styles.actionRowWrap}>
                  {(['daily', 'weekdays', 'weekends'] as const).map((sch) => (
                    <TouchableOpacity
                      key={sch}
                      style={[
                        styles.filterPill,
                        newHabitSchedule === sch && styles.filterPillActive,
                      ]}
                      onPress={() => setNewHabitSchedule(sch)}
                    >
                      <Text
                        style={[
                          styles.filterPillText,
                          newHabitSchedule === sch && styles.filterPillTextActive,
                        ]}
                      >
                        {sch}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
                <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 4 }}>
                  <TouchableOpacity
                    style={[styles.primaryBtn, { flex: 1, marginTop: 0 }]}
                    onPress={() => {
                      if (!newHabitName.trim()) {
                        setBannerMsg('Please enter a habit name first.');
                        return;
                      }
                      if (editingHabitId) {
                        setProdHabits((prev) =>
                          prev.map((item) =>
                            item.id === editingHabitId
                              ? { ...item, name: newHabitName.trim(), schedule: newHabitSchedule }
                              : item
                          )
                        );
                        setEditingHabitId(null);
                        setNewHabitName('');
                        setBannerMsg('Updated habit details.');
                        return;
                      }
                      setProdHabits((prev) => [
                        ...prev,
                        {
                          id: `hab_${Date.now()}`,
                          ownerId: persona,
                          name: newHabitName.trim(),
                          schedule: newHabitSchedule,
                          days: [],
                        },
                      ]);
                      setNewHabitName('');
                    }}
                  >
                    <Text style={styles.primaryBtnText}>
                      {editingHabitId ? 'Save Habit Changes' : 'Add Habit'}
                    </Text>
                  </TouchableOpacity>
                  {editingHabitId && (
                    <TouchableOpacity
                      style={[styles.outlineBtnSmall, { marginLeft: 8, marginBottom: 0, marginRight: 0 }]}
                      onPress={() => {
                        setEditingHabitId(null);
                        setNewHabitName('');
                      }}
                    >
                      <Text style={styles.outlineBtnText}>Cancel</Text>
                    </TouchableOpacity>
                  )}
                </View>
              </View>

              {/* 02 / POMODORO FOCUS & 03 / GET UNSTUCK */}
              <View style={styles.card}>
                <Text style={styles.wireframeTag}>02 / POMODORO FOCUS & PAUSE/RESET</Text>
                <View style={styles.rowBetween}>
                  <Text style={styles.h2}>
                    {
                      {
                        focus: 'Focus Timer',
                        short: 'Short Break',
                        long: 'Long Break',
                        breath: 'Box Breathing (4-4-4-4)',
                        mind: 'Mindfulness Pause',
                      }[prodTimerMode]
                    }
                  </Text>
                  <Text style={styles.bigScoreText}>{formatTime(prodRemSec)}</Text>
                </View>
                <Text style={[styles.whyBadgeText, { marginTop: 4 }]}>{phaseText}</Text>

                <View style={styles.actionRowWrap}>
                  <TouchableOpacity
                    style={styles.primaryBtnSmall}
                    onPress={() => {
                      if (prodRunning) {
                        if (prodDeadlineRef.current !== null) {
                          setProdRemSec(
                            Math.max(0, Math.ceil((prodDeadlineRef.current - Date.now()) / 1000))
                          );
                        }
                        prodDeadlineRef.current = null;
                        setProdRunning(false);
                      } else {
                        let startFrom = prodRemSec;
                        if (prodDone || startFrom <= 0) {
                          startFrom = prodTotalSec;
                          setProdRemSec(prodTotalSec);
                          setProdDone(false);
                        }
                        prodDeadlineRef.current = Date.now() + startFrom * 1000;
                        setProdRunning(true);
                      }
                    }}
                  >
                    <Text style={styles.primaryBtnText}>
                      {prodRunning
                        ? '⏸ Pause'
                        : prodDone
                        ? '▶ Start Again'
                        : prodRemSec < prodTotalSec
                        ? '▶ Resume'
                        : '▶ Start'}
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => {
                      prodDeadlineRef.current = null;
                      setProdRunning(false);
                      setProdDone(false);
                      setProdRemSec(prodTotalSec);
                    }}
                  >
                    <Text style={styles.outlineBtnText}>Reset</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.primaryBtnSmall, { backgroundColor: '#047857' }]}
                    onPress={() => chooseProdMode('focus', 5)}
                  >
                    <Text style={styles.primaryBtnText}>Try 5-Min Start</Text>
                  </TouchableOpacity>
                </View>

                <View style={styles.actionRowWrap}>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => chooseProdMode('focus')}
                  >
                    <Text style={styles.outlineBtnText}>Focus ({prodFocusMin || '25'}m)</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => chooseProdMode('short')}
                  >
                    <Text style={styles.outlineBtnText}>Short ({prodShortMin || '5'}m)</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => chooseProdMode('long')}
                  >
                    <Text style={styles.outlineBtnText}>Long ({prodLongMin || '15'}m)</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => chooseProdMode('breath')}
                  >
                    <Text style={styles.outlineBtnText}>Box Breathing ({prodResetMin || '2'}m)</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.outlineBtnSmall}
                    onPress={() => chooseProdMode('mind')}
                  >
                    <Text style={styles.outlineBtnText}>Mindfulness ({prodResetMin || '2'}m)</Text>
                  </TouchableOpacity>
                </View>

                <Text style={[styles.metaText, { marginTop: 6 }]}>
                  {prodCompletedCount} focus sessions completed · Custom timer durations (min):
                </Text>
                <View style={styles.scoreGridRow}>
                  <View style={{ flex: 1, marginRight: 6 }}>
                    <Text style={styles.scoreStatLabel}>Focus (1-120)</Text>
                    <TextInput
                      style={styles.textInput}
                      keyboardType="numeric"
                      value={prodFocusMin}
                      onChangeText={(txt) => updateDurField('focus', txt)}
                      onEndEditing={() => {
                        if (!prodFocusMin) updateDurField('focus', '25');
                      }}
                    />
                  </View>
                  <View style={{ flex: 1, marginRight: 6 }}>
                    <Text style={styles.scoreStatLabel}>Short (1-60)</Text>
                    <TextInput
                      style={styles.textInput}
                      keyboardType="numeric"
                      value={prodShortMin}
                      onChangeText={(txt) => updateDurField('short', txt)}
                      onEndEditing={() => {
                        if (!prodShortMin) updateDurField('short', '5');
                      }}
                    />
                  </View>
                  <View style={{ flex: 1, marginRight: 6 }}>
                    <Text style={styles.scoreStatLabel}>Long (1-60)</Text>
                    <TextInput
                      style={styles.textInput}
                      keyboardType="numeric"
                      value={prodLongMin}
                      onChangeText={(txt) => updateDurField('long', txt)}
                      onEndEditing={() => {
                        if (!prodLongMin) updateDurField('long', '15');
                      }}
                    />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.scoreStatLabel}>Reset (1-30)</Text>
                    <TextInput
                      style={styles.textInput}
                      keyboardType="numeric"
                      value={prodResetMin}
                      onChangeText={(txt) => updateDurField('reset', txt)}
                      onEndEditing={() => {
                        if (!prodResetMin) updateDurField('reset', '2');
                      }}
                    />
                  </View>
                </View>
              </View>
            </View>
          );
        })()}

        {/* ===================================================================
            TAB 7: PROFILE ("Profile & Participant Controls")
           =================================================================== */}
        {activeTab === 'PROFILE' && (
          <View style={styles.card}>
            <View style={styles.selectedHeroRow}>
              <PersonaPortrait personaId={persona} size={68} />
              <View style={{ flex: 1, marginLeft: 12 }}>
                <Text style={styles.h1}>{currentUser.displayName}</Text>
                <Text style={styles.authorNameText}>
                  {currentUser.jobTitle} · {currentUser.region}
                </Text>
                <Text style={styles.metaText}>{currentUser.email}</Text>
              </View>
            </View>

            <Text style={[styles.bodyText, { marginTop: 10 }]}>{currentUser.bio}</Text>

            <View style={[styles.whyBadge, { marginTop: 12 }]}>
              <Text style={styles.whyBadgeText}>
                Scoped Roles: {currentUser.roles.join(', ')}{'\n'}
                Permitted Audiences: {currentUser.audiences.join(', ')}
              </Text>
            </View>

            <Text style={[styles.sectionLabel, { marginTop: 14 }]}>
              EXPLICIT INTERESTS (TAP TO TOGGLE)
            </Text>
            <View style={styles.actionRowWrap}>
              {[
                'team learning',
                'innovation',
                'culture',
                'delivery excellence',
                'cloud architecture',
                'accessibility',
              ].map((tag) => {
                const active = interests.includes(tag);
                return (
                  <TouchableOpacity
                    key={tag}
                    style={[styles.filterPill, active && styles.filterPillActive]}
                    onPress={() =>
                      setInterests((prev) =>
                        active ? prev.filter((t) => t !== tag) : [...prev, tag]
                      )
                    }
                  >
                    <Text
                      style={[
                        styles.filterPillText,
                        active && styles.filterPillTextActive,
                      ]}
                    >
                      {active ? `✓ ${tag}` : `+ ${tag}`}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>

            <View style={styles.actionRowWrap}>
              <TouchableOpacity
                style={styles.primaryBtnSmall}
                onPress={handleGoHome}
              >
                <Text style={styles.primaryBtnText}>🏠 Go to Home Feed</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.outlineBtnSmall}
                onPress={() => setHostPaused(!hostPaused)}
              >
                <Text style={styles.outlineBtnText}>
                  {hostPaused
                    ? 'Host Status: PAUSED (Tap to Open)'
                    : 'Pause Host Availability'}
                </Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.outlineBtnSmall}
                onPress={handleResetDemoState}
              >
                <Text style={styles.outlineBtnText}>🔄 Reset All Demo Data</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[styles.outlineBtnSmall, { borderColor: '#fca5a5', backgroundColor: '#fef2f2' }]}
                onPress={handleLogout}
              >
                <Text style={[styles.outlineBtnText, { color: '#b91c1c' }]}>
                  🚪 Logout to Login Screen
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      </ScrollView>

      {/* =====================================================================
          PERSISTENT BOTTOM AUDIO MINI-PLAYER BAR (Matches Web Shell!)
         ===================================================================== */}
      <View style={styles.miniPlayerBar}>
        <TouchableOpacity
          style={{ flex: 1, marginRight: 8 }}
          onPress={() => handleSelectTab('LISTEN')}
        >
          <Text style={styles.miniPlayerText} numberOfLines={1}>
            {isPlayingAudio ? '🔊 Playing:' : '⏸ Paused:'} {activeAudioTitle}
          </Text>
          <Text style={styles.miniPlayerSub}>
            {formatTime(castPositionSec)} of {formatTime(castDurationSec)} ({playbackSpeed}) · Tap Listen tab
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.miniPlayBtn}
          onPress={() => handleToggleAudio()}
        >
          <Text style={styles.miniPlayBtnText}>{isPlayingAudio ? 'Pause' : 'Play'}</Text>
        </TouchableOpacity>
      </View>

      {/* =====================================================================
          8-TAB BOTTOM NAVIGATION BAR (Full Parity with Web!)
         ===================================================================== */}
      <View style={styles.tabBar}>
        {(
          [
            { id: 'PULSE', icon: '🏠', label: 'Home' },
            { id: 'CHAMPIONS', icon: '🏆', label: 'Points' },
            { id: 'PRODUCTIVE', icon: '⏱', label: 'Focus' },
            { id: 'LISTEN', icon: '🎧', label: 'Listen' },
            { id: 'CONNECT', icon: '🤝', label: 'Connect' },
            { id: 'CONTRIBUTE', icon: '✍️', label: 'Publish' },
            { id: 'PORTAL', icon: '🛡', label: 'Portal' },
            { id: 'PROFILE', icon: '👤', label: 'Profile' },
          ] as const
        ).map((t) => {
          const isCurrent = activeTab === t.id;
          return (
            <TouchableOpacity
              key={t.id}
              style={[styles.tabItem, isCurrent && styles.tabItemActive]}
              onPress={() => handleSelectTab(t.id)}
            >
              <Text style={{ fontSize: 13 }}>{t.icon}</Text>
              <Text
                style={[styles.tabLabel, isCurrent && styles.tabLabelActive]}
                numberOfLines={1}
                adjustsFontSizeToFit
                minimumFontScale={0.75}
              >
                {t.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  loginSafe: {
    flex: 1,
    backgroundColor: '#001a66',
  },
  loginContainer: {
    padding: 16,
    paddingBottom: 40,
  },
  loginBrandCard: {
    backgroundColor: '#002180',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#3b82f6',
    marginBottom: 14,
  },
  loginBrandRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  flameBadge: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: '#1e3a8a',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  flameIcon: {
    fontSize: 22,
  },
  loginBrandTitle: {
    color: '#ffffff',
    fontSize: 22,
    fontWeight: '800',
  },
  loginBrandSub: {
    color: '#bfdbfe',
    fontSize: 12,
    marginTop: 2,
  },
  storedBadge: {
    marginTop: 10,
    alignSelf: 'flex-start',
    backgroundColor: '#d1fae5',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  storedBadgeText: {
    color: '#065f46',
    fontSize: 11,
    fontWeight: '700',
  },
  loginFormCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    marginBottom: 14,
  },
  selectedHeroRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#eff6ff',
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#bfdbfe',
    marginTop: 6,
  },
  selectedHeroName: {
    fontSize: 17,
    fontWeight: '800',
    color: '#0f172a',
  },
  selectedHeroRole: {
    fontSize: 12,
    color: '#475569',
    marginTop: 2,
  },
  roleChipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginTop: 6,
  },
  roleChip: {
    backgroundColor: '#dbeafe',
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: 6,
    marginRight: 5,
    marginBottom: 4,
  },
  roleChipText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#002180',
  },
  readOnlyInput: {
    backgroundColor: '#f1f5f9',
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginTop: 4,
  },
  readOnlyInputText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#1e293b',
  },
  passwordHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 12,
  },
  autoFilledTag: {
    fontSize: 11,
    fontWeight: '700',
    color: '#047857',
  },
  passwordRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
  },
  showHideBtn: {
    marginLeft: 8,
    backgroundColor: '#e2e8f0',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 10,
  },
  showHideBtnText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#0f172a',
  },
  signInPrimaryBtn: {
    backgroundColor: '#002180',
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 16,
  },
  signInPrimaryBtnText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '800',
  },
  personaCardRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 10,
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: '#e2e8f0',
    marginTop: 8,
    backgroundColor: '#f8fafc',
  },
  personaCardRowActive: {
    borderColor: '#002180',
    backgroundColor: '#eff6ff',
  },
  personaCardName: {
    fontSize: 14,
    fontWeight: '800',
    color: '#0f172a',
  },
  personaCardSub: {
    fontSize: 11,
    color: '#475569',
    marginTop: 1,
  },
  personaCardRoles: {
    fontSize: 10,
    fontWeight: '700',
    color: '#002180',
    marginTop: 2,
  },
  activePill: {
    marginLeft: 8,
    backgroundColor: '#002180',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
  },
  activePillText: {
    color: '#ffffff',
    fontSize: 9,
    fontWeight: '800',
  },
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  header: {
    backgroundColor: '#002180',
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  headerTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  brandTitle: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '800',
  },
  brandSub: {
    color: '#bfdbfe',
    fontSize: 10.5,
  },
  signOutBtn: {
    backgroundColor: '#1e3a8a',
    paddingHorizontal: 7,
    paddingVertical: 5,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#60a5fa',
  },
  signOutBtnText: {
    color: '#ffffff',
    fontSize: 10.5,
    fontWeight: '700',
  },
  personaChip: {
    backgroundColor: '#1e3a8a',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 8,
    marginRight: 6,
  },
  personaChipActive: {
    backgroundColor: '#ffffff',
  },
  personaChipText: {
    color: '#bfdbfe',
    fontSize: 11,
    fontWeight: '700',
  },
  personaChipTextActive: {
    color: '#002180',
  },
  toastBanner: {
    backgroundColor: '#ecfdf5',
    borderBottomWidth: 1,
    borderBottomColor: '#6ee7b7',
    paddingHorizontal: 14,
    paddingVertical: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  toastBannerText: {
    color: '#065f46',
    fontSize: 11,
    fontWeight: '700',
    flex: 1,
    marginRight: 8,
  },
  toastDismissText: {
    color: '#047857',
    fontSize: 11,
    fontWeight: '800',
    textDecorationLine: 'underline',
  },
  scrollBody: {
    padding: 12,
    paddingBottom: 24,
  },
  challengeBannerCard: {
    backgroundColor: '#fffbeb',
    borderRadius: 12,
    padding: 12,
    borderWidth: 1,
    borderColor: '#fcd34d',
    marginBottom: 12,
  },
  challengeBannerTag: {
    fontSize: 10,
    fontWeight: '800',
    color: '#b45309',
  },
  challengeBannerTitle: {
    fontSize: 16,
    fontWeight: '800',
    color: '#78350f',
    marginTop: 2,
  },
  challengeBannerSub: {
    fontSize: 12,
    color: '#92400e',
    marginTop: 4,
  },
  secondaryActionBtn: {
    marginTop: 8,
    backgroundColor: '#002180',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    alignSelf: 'flex-start',
  },
  secondaryActionBtnText: {
    color: '#ffffff',
    fontSize: 11,
    fontWeight: '700',
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 14,
    padding: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  wireframeTag: {
    fontSize: 10,
    fontWeight: '800',
    color: '#64748b',
    letterSpacing: 0.4,
    marginBottom: 4,
  },
  h1: {
    fontSize: 18,
    fontWeight: '800',
    color: '#002180',
    marginBottom: 4,
  },
  h2: {
    fontSize: 15,
    fontWeight: '700',
    color: '#0f172a',
    marginTop: 4,
    marginBottom: 4,
  },
  bodyText: {
    fontSize: 13,
    color: '#334155',
    lineHeight: 19,
  },
  metaText: {
    fontSize: 11,
    color: '#64748b',
  },
  boldText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#0f172a',
  },
  sectionLabel: {
    fontSize: 10,
    fontWeight: '800',
    color: '#475569',
    letterSpacing: 0.4,
  },
  authorHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 6,
  },
  authorNameText: {
    fontSize: 13,
    fontWeight: '800',
    color: '#0f172a',
  },
  audienceSubText: {
    fontSize: 11,
    color: '#64748b',
  },
  whyBadge: {
    marginTop: 8,
    backgroundColor: '#eff6ff',
    borderWidth: 1,
    borderColor: '#bfdbfe',
    borderRadius: 8,
    padding: 9,
  },
  whyBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#002180',
    lineHeight: 16,
  },
  aiSummaryBox: {
    marginTop: 8,
    backgroundColor: '#f8fafc',
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 8,
    padding: 9,
  },
  aiSummaryTitle: {
    fontSize: 11,
    fontWeight: '800',
    color: '#002180',
    marginBottom: 3,
  },
  actionRowWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginTop: 10,
  },
  primaryBtn: {
    marginTop: 12,
    backgroundColor: '#002180',
    paddingVertical: 11,
    paddingHorizontal: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  primaryBtnSmall: {
    backgroundColor: '#002180',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginRight: 8,
    marginBottom: 6,
  },
  primaryBtnText: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
  amberBtnSmall: {
    backgroundColor: '#fef3c7',
    borderWidth: 1,
    borderColor: '#f59e0b',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginRight: 8,
    marginBottom: 6,
  },
  amberBtnText: {
    color: '#92400e',
    fontSize: 12,
    fontWeight: '700',
  },
  outlineBtnSmall: {
    backgroundColor: '#f1f5f9',
    borderWidth: 1,
    borderColor: '#cbd5e1',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginRight: 8,
    marginBottom: 6,
  },
  outlineBtnText: {
    color: '#1e293b',
    fontSize: 12,
    fontWeight: '700',
  },
  filterPill: {
    backgroundColor: '#f1f5f9',
    borderWidth: 1,
    borderColor: '#cbd5e1',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    marginRight: 6,
    marginBottom: 6,
  },
  filterPillActive: {
    backgroundColor: '#002180',
    borderColor: '#002180',
  },
  filterPillText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#334155',
  },
  filterPillTextActive: {
    color: '#ffffff',
  },
  textInput: {
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 10,
    padding: 10,
    fontSize: 13,
    color: '#0f172a',
    backgroundColor: '#ffffff',
    marginTop: 4,
  },
  backLink: {
    fontSize: 12,
    fontWeight: '700',
    color: '#002180',
    marginBottom: 6,
  },
  audioBox: {
    marginTop: 10,
    backgroundColor: '#f8fafc',
    borderRadius: 10,
    padding: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  inviteOfferBox: {
    marginTop: 12,
    backgroundColor: '#eff6ff',
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: '#bfdbfe',
  },
  disabledNotice: {
    marginTop: 8,
    backgroundColor: '#fef3c7',
    padding: 8,
    borderRadius: 8,
  },
  disabledNoticeText: {
    color: '#92400e',
    fontSize: 11,
    fontWeight: '700',
  },
  rowBetween: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  bigScoreText: {
    fontSize: 20,
    fontWeight: '800',
    color: '#002180',
  },
  scoreGridRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10,
  },
  scoreStatBox: {
    flex: 1,
    backgroundColor: '#f8fafc',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 10,
    padding: 8,
    marginRight: 6,
  },
  scoreStatLabel: {
    fontSize: 10,
    fontWeight: '700',
    color: '#64748b',
  },
  scoreStatValue: {
    fontSize: 15,
    fontWeight: '800',
    color: '#002180',
    marginTop: 2,
  },
  goldBadgeChip: {
    backgroundColor: '#fef3c7',
    borderWidth: 1,
    borderColor: '#f59e0b',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    marginRight: 6,
    marginBottom: 4,
  },
  goldBadgeText: {
    fontSize: 11,
    fontWeight: '800',
    color: '#92400e',
  },
  leaderboardRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 10,
    borderRadius: 10,
    backgroundColor: '#f8fafc',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    marginTop: 8,
  },
  leaderboardRowSelf: {
    backgroundColor: '#eff6ff',
    borderColor: '#002180',
  },
  rankCircle: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: '#002180',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 8,
  },
  rankCircleText: {
    color: '#ffffff',
    fontSize: 11,
    fontWeight: '800',
  },
  leaderboardPts: {
    fontSize: 14,
    fontWeight: '800',
    color: '#002180',
  },
  subTabRow: {
    flexDirection: 'row',
    backgroundColor: '#e2e8f0',
    borderRadius: 10,
    padding: 3,
    marginBottom: 10,
  },
  subTabBtn: {
    flex: 1,
    paddingVertical: 7,
    alignItems: 'center',
    borderRadius: 8,
  },
  subTabBtnActive: {
    backgroundColor: '#ffffff',
  },
  subTabText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#475569',
  },
  subTabTextActive: {
    color: '#002180',
  },
  scrubberBox: {
    marginTop: 10,
    backgroundColor: '#f8fafc',
    padding: 10,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  scrubberText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#002180',
  },
  chapterBtn: {
    paddingVertical: 7,
    paddingHorizontal: 10,
    backgroundColor: '#eff6ff',
    borderRadius: 8,
    marginTop: 6,
  },
  chapterBtnText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#002180',
  },
  auditRow: {
    paddingVertical: 7,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  miniPlayerBar: {
    backgroundColor: '#e2e8f0',
    borderTopWidth: 1,
    borderTopColor: '#cbd5e1',
    paddingHorizontal: 12,
    paddingVertical: 7,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  miniPlayerText: {
    fontSize: 11,
    fontWeight: '800',
    color: '#0f172a',
  },
  miniPlayerSub: {
    fontSize: 10,
    color: '#475569',
  },
  miniPlayBtn: {
    backgroundColor: '#002180',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
  },
  miniPlayBtnText: {
    color: '#ffffff',
    fontSize: 11,
    fontWeight: '700',
  },
  tabBar: {
    flexDirection: 'row',
    borderTopWidth: 1,
    borderTopColor: '#cbd5e1',
    backgroundColor: '#ffffff',
    paddingVertical: 6,
    paddingHorizontal: 2,
  },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 2,
    paddingHorizontal: 1,
  },
  tabItemActive: {
    borderBottomWidth: 2,
    borderBottomColor: '#002180',
  },
  tabLabel: {
    fontSize: 9,
    fontWeight: '600',
    color: '#64748b',
    marginTop: 1,
  },
  tabLabelActive: {
    color: '#002180',
    fontWeight: '800',
  },
});
