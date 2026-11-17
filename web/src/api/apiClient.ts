import {
  AiAssistanceResponseDto,
  AiBudgetStatusDto,
  AppMode,
  AppreciationResultDto,
  AudioListenItemDto,
  AuditEventDto,
  BilateralOutcome,
  ChallengeDto,
  ChallengeEntryDto,
  ConnectionRequestDto,
  ContentCardDto,
  HappenedAnswer,
  LeaderboardResponseDto,
  LeaderboardRowDto,
  PilotMetricsDto,
  PortalQueueItemDto,
  PulseHttpError,
  RecognitionProgressDto,
  UserProfileDto,
} from './contracts';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/api/apiClient.ts
 * Journey: Explicit Transport Layer (`VITE_APP_MODE=demo|live`) for Mastek Campfire
 *          (Stories CF-002..CF-007, CF-010, CF-011 & Plan Step D1).
 * Why it is designed this way:
 *   1. EXPLICIT MODE SELECTION (No Silent Fallback After Live Failure!):
 *      - Configured via `VITE_APP_MODE=demo|live` (defaulting to `'demo'` for
 *        static/offline builds) or explicit presenter toggle (`setAppModeOverride`).
 *      - If `VITE_APP_MODE` is set to an unknown value, throws `500 INVALID_APP_MODE`.
 *      - In `'live'` mode, ONLY real Spring Boot + PostgreSQL API responses establish
 *        success. Any HTTP `401/403/404/409/422/500` or network failure throws a
 *        typed `PulseHttpError(status, code, message)` and NEVER mutates local demo state!
 *   2. CANONICAL SERVER PERSONA IDs (`usr_asha`, `usr_daniel`, `usr_priya`,
 *      `usr_marcus`, `usr_elena`, `usr_rohan`):
 *      - Both `live` and `demo` adapters use the exact same 6+ canonical server IDs
 *        so five distinct colleagues can appreciate Asha's story to unlock
 *        `Community Favourite` without mapping by display name.
 * ============================================================================
 */

const STORAGE_KEY = 'mastek_pulse_state_v1';
const MODE_OVERRIDE_KEY = 'mastek_campfire_mode_override';

let runtimeModeOverride: AppMode | null = (() => {
  try {
    const saved = sessionStorage.getItem(MODE_OVERRIDE_KEY);
    if (saved === 'demo' || saved === 'live') return saved;
  } catch {
    // ignore
  }
  return null;
})();

export function getAppMode(): AppMode {
  if (runtimeModeOverride) {
    return runtimeModeOverride;
  }
  const rawEnv = (import.meta as any).env?.VITE_APP_MODE;
  if (!rawEnv || rawEnv === '') {
    return 'demo';
  }
  if (rawEnv !== 'demo' && rawEnv !== 'live') {
    throw new PulseHttpError(
      500,
      'INVALID_APP_MODE',
      `Invalid VITE_APP_MODE="${rawEnv}". Expected "demo" or "live".`
    );
  }
  return rawEnv;
}

export function setAppModeOverride(mode: AppMode | null): void {
  runtimeModeOverride = mode;
  try {
    if (mode) sessionStorage.setItem(MODE_OVERRIDE_KEY, mode);
    else sessionStorage.removeItem(MODE_OVERRIDE_KEY);
  } catch {
    // ignore
  }
}

/**
 * Performs an authoritative HTTP call to `/api/v1` in `live` mode.
 * Never falls back to local simulation on error!
 */
async function liveRequest<T>(
  path: string,
  activeUserId: string,
  init?: RequestInit
): Promise<T> {
  const headers: Record<string, string> = {
    ...(init?.headers as Record<string, string>),
  };
  if (activeUserId) {
    headers['X-Pulse-User-Id'] = activeUserId;
  }
  if (init?.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  let res: Response;
  try {
    res = await fetch(`/api/v1${path}`, {
      ...init,
      headers,
    });
  } catch (err: any) {
    throw new PulseHttpError(
      0,
      'NETWORK_ERROR',
      `Live API request to /api/v1${path} failed (${err?.message || 'network unreachable'}). No local fallback was executed.`
    );
  }

  if (!res.ok) {
    let code = `HTTP_${res.status}`;
    let message = `HTTP ${res.status} ${res.statusText}`;
    try {
      const contentType = res.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        const body = await res.json();
        if (body && typeof body === 'object') {
          code = body.code || code;
          message = body.message || message;
        }
      } else {
        const text = await res.text();
        if (text && text.trim().length > 0 && text.length < 300) {
          message = text.trim();
        }
      }
    } catch {
      // keep default status message
    }
    throw new PulseHttpError(res.status, code, message);
  }

  return (await res.json()) as T;
}

interface DemoAppreciationRecord {
  id: string;
  actorId: string;
  contentId: string;
  recipientId: string;
  weekStart: string;
  actorTeamSnapshot: string;
  recipientTeamSnapshot: string;
}

interface LocalStoreState {
  users: UserProfileDto[];
  storiesAndUpdates: ContentCardDto[];
  radioQueues: AudioListenItemDto[];
  cultureCasts: AudioListenItemDto[];
  portalItems: PortalQueueItemDto[];
  requests: ConnectionRequestDto[];
  confirmationsByRequest: Record<string, Record<string, { happened: HappenedAnswer; useful: boolean | null }>>;
  idempotencyMap: Record<string, string>;
  aiActionsUsed: number;
  aiActionCap: number;
  auditLog: AuditEventDto[];
  challengeEntriesByUser: Record<string, ChallengeEntryDto>;
  challengeIdempotencyHashes: Record<string, { entryUserId: string; payloadHash: string }>;
  appreciations: DemoAppreciationRecord[];
  leaderboardOptOut: Record<string, boolean>;
}

function createPlayableWavDataUri(seconds = 6): string {
  const sampleRate = 8000;
  const numSamples = sampleRate * seconds;
  const dataSize = numSamples * 2;
  const buffer = new ArrayBuffer(44 + dataSize);
  const view = new DataView(buffer);

  const writeStr = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i));
  };

  writeStr(0, 'RIFF');
  view.setUint32(4, 36 + dataSize, true);
  writeStr(8, 'WAVE');
  writeStr(12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeStr(36, 'data');
  view.setUint32(40, dataSize, true);

  for (let i = 0; i < numSamples; i++) {
    const t = i / sampleRate;
    const env = Math.min(1, i / 600) * Math.max(0.2, 1 - t / (seconds + 1.5));
    const wave =
      0.45 * Math.sin(2 * Math.PI * 440 * t) +
      0.3 * Math.sin(2 * Math.PI * 554.37 * t) +
      0.25 * Math.sin(2 * Math.PI * 659.25 * t);
    view.setInt16(44 + i * 2, Math.floor(wave * env * 5500), true);
  }

  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return 'data:audio/wav;base64,' + btoa(binary);
}

const DEMO_WAV_URI = createPlayableWavDataUri(8);
const DEMO_WEEK_START = '2026-11-16';

function getInitialSeedState(): LocalStoreState {
  const users: UserProfileDto[] = [
    {
      id: 'usr_asha',
      displayName: 'Asha Patel',
      email: 'asha.patel@mastek-pulse.demo',
      region: 'INDIA',
      functionName: 'Engineering',
      roles: ['EMPLOYEE', 'CREATOR'],
      interests: ['team learning', 'innovation', 'culture'],
      audiences: ['aud_all', 'aud_pilot'],
      notificationFrequency: 'DAILY_DIGEST',
      hostPaused: false,
    },
    {
      id: 'usr_daniel',
      displayName: 'Daniel Wright',
      email: 'daniel.wright@mastek-pulse.demo',
      region: 'UK',
      functionName: 'Delivery',
      roles: ['EMPLOYEE', 'CREATOR'],
      interests: ['team learning', 'delivery excellence', 'agile'],
      audiences: ['aud_all', 'aud_pilot', 'aud_uk_restricted'],
      notificationFrequency: 'DAILY_DIGEST',
      hostPaused: false,
    },
    {
      id: 'usr_priya',
      displayName: 'Priya Nair',
      email: 'priya.nair@mastek-pulse.demo',
      region: 'INDIA',
      functionName: 'People & Culture',
      roles: ['EMPLOYEE', 'CREATOR', 'REVIEWER'],
      interests: ['culture', 'inclusion', 'onboarding'],
      audiences: ['aud_all', 'aud_pilot'],
      notificationFrequency: 'IMMEDIATE',
      hostPaused: false,
    },
    {
      id: 'usr_marcus',
      displayName: 'Marcus Vance',
      email: 'marcus.vance@mastek-pulse.demo',
      region: 'US',
      functionName: 'Operations',
      roles: ['EMPLOYEE', 'PUBLISHER', 'MODERATOR'],
      interests: ['innovation', 'cloud architecture', 'operations'],
      audiences: ['aud_all', 'aud_pilot'],
      notificationFrequency: 'DAILY_DIGEST',
      hostPaused: false,
    },
    {
      id: 'usr_elena',
      displayName: 'Elena Rostova',
      email: 'elena.rostova@mastek-pulse.demo',
      region: 'UK',
      functionName: 'Platform IT',
      roles: ['EMPLOYEE', 'CREATOR', 'ADMIN'],
      interests: ['security', 'accessibility', 'cloud architecture'],
      audiences: ['aud_all', 'aud_pilot', 'aud_uk_restricted'],
      notificationFrequency: 'WEEKLY_SUMMARY',
      hostPaused: false,
    },
    {
      id: 'usr_rohan',
      displayName: 'Rohan Deshmukh',
      email: 'rohan.deshmukh@mastek-pulse.demo',
      region: 'INDIA',
      functionName: 'Cloud Practice',
      roles: ['EMPLOYEE', 'CREATOR'],
      interests: ['innovation', 'cloud architecture', 'team learning'],
      audiences: ['aud_all', 'aud_pilot'],
      notificationFrequency: 'DAILY_DIGEST',
      hostPaused: false,
    },
  ];

  const storiesAndUpdates: ContentCardDto[] = [
    {
      id: 'story_01',
      contentType: 'STORY',
      title: 'A retrospective everyone can contribute to',
      summary: 'One small change helped quieter voices join our team retrospective.',
      bodyText:
        'We asked everyone to write first, then share. During our UK-India sprint retrospectives, we noticed that the loudest or most senior voices often set the anchor in the first two minutes.\n\nSo we tried one small change: we spent the first four minutes in silent writing where everyone added two concrete observations before anyone spoke out loud. We also rotated who facilitated each prompt.\n\nThis gave quieter colleagues a comfortable way to contribute, surfaced two deployment bottlenecks we had missed for months, and kept our retrospective under 30 minutes.',
      authorId: 'usr_daniel',
      authorName: 'Daniel Wright',
      authorFunction: 'Delivery',
      authorRegion: 'UK',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      tags: ['team learning', 'delivery excellence', 'culture'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-26T09:00:00Z',
      factualExplanation: 'WHY THIS? Your interest: team learning · 15 min conversation / 2 places available',
      hasAudio: true,
      durationSeconds: 95,
      transcriptText:
        'Daniel Wright (Delivery, UK): We asked everyone to write first, then share. This gave quieter colleagues a comfortable way to contribute and surfaced practical improvements across our distributed team.',
      signedAudioUrl: DEMO_WAV_URI,
      invitation: {
        id: 'inv_story_01',
        hostId: 'usr_daniel',
        hostName: 'Daniel Wright',
        hostRegion: 'UK',
        hostFunction: 'Delivery',
        topicLabel: 'Ask me about this: inclusive team retrospectives',
        durationMinutes: 15,
        capacity: 2,
        acceptedCount: 0,
        remainingPlaces: 2,
        paused: false,
        expiresAt: '2026-11-25T23:59:59Z',
        statusLabel: 'OPEN',
      },
    },
    {
      id: 'upd_01',
      contentType: 'UPDATE',
      title: 'Three ideas worth knowing today',
      summary: 'Company Update / 2 min · Quarterly engineering guilds, responsible AI guardrails, and cross-region learning hours.',
      bodyText:
        '1. Global Engineering Guilds open registration this Friday.\n2. Our Responsible AI checklist now includes mandatory human review and source links for internal summaries.\n3. Pilot teams can allocate two 15-minute cross-team learning slots per week.',
      officialSource: 'Official source / Today',
      externalUrl: 'https://intranet.mastek.demo/updates/three-ideas',
      authorId: 'usr_marcus',
      authorName: 'Marcus Vance',
      authorFunction: 'Operations',
      authorRegion: 'US',
      audienceId: 'aud_all',
      audienceName: 'All Employees',
      tags: ['innovation', 'culture', 'team learning'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-26T08:00:00Z',
      factualExplanation: 'WHY THIS? Your interest: innovation, culture, team learning · Official company update',
      hasAudio: true,
      durationSeconds: 120,
      transcriptText:
        'Three ideas worth knowing today: Global Engineering Guild registration opens Friday; Responsible AI guardrails emphasize human choice; and pilot volunteers can host two 15-minute conversations per week.',
      signedAudioUrl: DEMO_WAV_URI,
      invitation: null,
    },
    {
      id: 'story_02',
      contentType: 'STORY',
      title: 'Cutting API tail latency with Java 21 Virtual Threads',
      summary: 'What we learned migrating a connection-heavy Spring Boot service without rewriting to reactive.',
      bodyText:
        'When our morning traffic spiked, thread-pool exhaustion caused queueing delays. Enabling Java 21 virtual threads alongside carefully scoped PostgreSQL row locks dropped p95 latency by 42% while keeping our code simple enough for new interns to debug.',
      authorId: 'usr_rohan',
      authorName: 'Rohan Deshmukh',
      authorFunction: 'Cloud Practice',
      authorRegion: 'INDIA',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      tags: ['innovation', 'cloud architecture', 'team learning'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-25T14:00:00Z',
      factualExplanation: 'WHY THIS? Your interest: innovation, team learning · 15 min conversation / 1 place available',
      hasAudio: false,
      invitation: {
        id: 'inv_story_02',
        hostId: 'usr_rohan',
        hostName: 'Rohan Deshmukh',
        hostRegion: 'INDIA',
        hostFunction: 'Cloud Practice',
        topicLabel: 'Ask me about this: Java 21 virtual threads in Spring Boot',
        durationMinutes: 15,
        capacity: 2,
        acceptedCount: 1,
        remainingPlaces: 1,
        paused: false,
        expiresAt: '2026-11-25T23:59:59Z',
        statusLabel: 'OPEN',
      },
    },
    {
      id: 'story_06',
      contentType: 'STORY',
      title: 'Testing denied access in PostgreSQL row policies',
      summary: 'Why database owners can accidentally bypass RLS—and how we test least-privilege roles in CI.',
      bodyText:
        'PostgreSQL table owners and roles with BYPASSRLS ignore row security policies by default. We added automated integration tests that run as a non-privileged application role and assert 403 Forbidden on restricted audiences.',
      authorId: 'usr_elena',
      authorName: 'Elena Rostova',
      authorFunction: 'Platform IT',
      authorRegion: 'UK',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      tags: ['security', 'cloud architecture', 'delivery excellence'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-23T11:00:00Z',
      factualExplanation: 'WHY THIS? Shared with colleagues in the pilot',
      hasAudio: false,
      invitation: null,
    },
    {
      id: 'story_05',
      contentType: 'STORY',
      title: 'Making cross-office coffee chats less awkward',
      summary: 'Why "Ask me about this specific topic for 15 minutes" works better than open-ended networking.',
      bodyText:
        'In our baseline interviews, employees told us they wanted to meet colleagues in other regions but felt awkward sending an unstructured cold message. Attaching a 15-minute topic prompt to a concrete story removes the guesswork.',
      authorId: 'usr_priya',
      authorName: 'Priya Nair',
      authorFunction: 'People & Culture',
      authorRegion: 'INDIA',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      tags: ['culture', 'inclusion', 'onboarding'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-23T15:00:00Z',
      factualExplanation: 'WHY THIS? Your interest: culture · 15 min conversation / 2 places available',
      hasAudio: false,
      invitation: {
        id: 'inv_story_05',
        hostId: 'usr_priya',
        hostName: 'Priya Nair',
        hostRegion: 'INDIA',
        hostFunction: 'People & Culture',
        topicLabel: 'Ask me about this: cross-office onboarding buddies',
        durationMinutes: 15,
        capacity: 3,
        acceptedCount: 1,
        remainingPlaces: 2,
        paused: false,
        expiresAt: '2026-11-25T23:59:59Z',
        statusLabel: 'OPEN',
      },
    },
    {
      id: 'story_08',
      contentType: 'STORY',
      title: 'UK Delivery Commercial Risk Register Template',
      summary: 'Scoped exclusively to the UK Delivery Governance audience (aud_uk_restricted).',
      bodyText:
        'This story is restricted to members of aud_uk_restricted (Daniel and Elena). Employees outside that audience (like Asha) never see this item in their feed.',
      authorId: 'usr_daniel',
      authorName: 'Daniel Wright',
      authorFunction: 'Delivery',
      authorRegion: 'UK',
      audienceId: 'aud_uk_restricted',
      audienceName: 'UK Delivery Governance',
      tags: ['delivery excellence', 'security'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-22T09:00:00Z',
      factualExplanation: 'WHY THIS? Scoped to UK Delivery Governance',
      hasAudio: false,
      invitation: null,
    },
  ];

  const radioQueues: AudioListenItemDto[] = [
    {
      id: 'rad_commute_01',
      contentType: 'RADIO_QUEUE',
      title: 'Your Commute Edition: A little closer to Mastek',
      summary: 'Company news + colleague stories · Curated queue / 12 min remaining',
      bodyText:
        'Curated 12-minute commute edition combining three approved highlights: (01) Company pulse (2 min), (02) Innovation spotlight (4 min), and (03) A story across borders (6 min).',
      hostId: 'usr_marcus',
      hostName: 'Mastek Radio Editorial',
      durationSeconds: 720,
      resumePositionSeconds: 0,
      transcriptText:
        '[00:00] Welcome to Mastek Radio — Your Commute Edition. Today we bring you three approved highlights in twelve minutes.\n[00:15] 01 / Company pulse (2 min): Three ideas worth knowing today across our global delivery centers.\n[02:15] 02 / Innovation spotlight (4 min): How teams are using structured templates and human-reviewed AI to cut documentation handoff time.\n[06:15] 03 / A story across borders (6 min): Daniel in UK Delivery and Asha in India Engineering share how asynchronous writing transformed their team retrospectives.',
      chaptersJson: JSON.stringify([
        { index: 1, title: '01 Company pulse', durationMinutes: 2, startSeconds: 0 },
        { index: 2, title: '02 Innovation spotlight', durationMinutes: 4, startSeconds: 120 },
        { index: 3, title: '03 A story across borders', durationMinutes: 6, startSeconds: 360 },
      ]),
      relatedStoryId: 'story_01',
      signedAudioUrl: DEMO_WAV_URI,
      publishedVersion: 1,
    },
  ];

  const cultureCasts: AudioListenItemDto[] = [
    {
      id: 'cc_ep_03',
      contentType: 'CULTURE_CAST',
      title: 'Episode 03: Different places. Shared possibilities.',
      summary: 'A conversation about ideas and culture across geographies and functions · 8 min / Hosted by a colleague',
      bodyText:
        'Daniel Wright and Priya Nair discuss how quiet writing before open discussion helps distributed teams across India, the UK, and the US contribute on equal footing.',
      hostId: 'usr_daniel',
      hostName: 'Daniel Wright & Priya Nair',
      durationSeconds: 480,
      resumePositionSeconds: 130,
      transcriptText:
        '[00:00] Daniel: Welcome to Culture Cast Episode 04. Today we are looking at how different offices collaborate across time zones.\n[02:10] Priya: When we gave people five minutes to write notes silently before speaking, participation from quieter colleagues doubled.\n[05:30] Daniel: Continue the conversation through the related retrospective story or voluntary invitation right below this episode.',
      chaptersJson: JSON.stringify([
        { index: 1, title: '01 Bridging time zones', startSeconds: 0 },
        { index: 2, title: '02 Write first, then share (02:10)', startSeconds: 130 },
        { index: 3, title: '03 How to try it this week', startSeconds: 330 },
      ]),
      relatedStoryId: 'story_01',
      signedAudioUrl: DEMO_WAV_URI,
      publishedVersion: 2,
    },
  ];

  const portalItems: PortalQueueItemDto[] = [
    {
      id: 'cc_ep_03',
      revisionId: 'rev_cc_03_v3',
      contentType: 'CULTURE_CAST',
      title: 'Culture Cast / Episode 04',
      summary: 'Audio + transcript · Different places. Shared possibilities. (Revision 3)',
      bodyText:
        'Revision 3 adds the downloadable retrospective checklist link and verified chapter timestamps for all employees.',
      authorId: 'usr_daniel',
      authorName: 'Daniel Wright',
      audienceId: 'aud_all',
      audienceName: 'All employees',
      status: 'IN_REVIEW',
      currentVersion: 3,
      publishedVersion: 2,
      hasAudio: true,
      transcriptChecked: true,
      mediaReady: true,
      approvedBy: null,
      approvedByName: null,
      reviewerNotes: null,
      scheduledFor: null,
      updatedAt: '2026-10-26T09:15:00Z',
    },
    {
      id: 'story_10',
      revisionId: 'rev_story_10',
      contentType: 'STORY',
      title: 'Structured handover notes between UK and India shifts',
      summary: 'Colleague Story · Approved by Priya Nair, ready for Publisher release.',
      bodyText:
        'A simple 4-bullet markdown template at the end of the UK afternoon reduced morning blocker messages for the Pune engineering team.',
      authorId: 'usr_rohan',
      authorName: 'Rohan Deshmukh',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      status: 'APPROVED',
      currentVersion: 1,
      publishedVersion: null,
      hasAudio: false,
      transcriptChecked: true,
      mediaReady: true,
      approvedBy: 'usr_priya',
      approvedByName: 'Priya Nair',
      reviewerNotes: 'Approved for pilot release.',
      scheduledFor: null,
      updatedAt: '2026-10-26T07:00:00Z',
    },
  ];

  const requests: ConnectionRequestDto[] = [
    {
      id: 'req_seed_01',
      invitationId: 'inv_story_05',
      storyId: 'story_05',
      storyTitle: 'Making cross-office coffee chats less awkward',
      topicLabel: 'Ask me about this: cross-office onboarding buddies',
      requesterId: 'usr_asha',
      requesterName: 'Asha Patel',
      requesterRegion: 'INDIA',
      requesterFunction: 'Engineering',
      hostId: 'usr_priya',
      hostName: 'Priya Nair',
      hostRegion: 'INDIA',
      hostFunction: 'People & Culture',
      messageText: 'Hi Priya, I would love to learn how your onboarding buddy prompts helped new engineers in Pune.',
      aiAssisted: false,
      state: 'ACCEPTED',
      version: 2,
      capacity: 3,
      acceptedCount: 1,
      remainingPlaces: 2,
      createdAt: '2026-10-24T10:00:00Z',
      myConfirmation: { happened: 'YES', useful: true },
      confirmationsCount: 2,
      bilateralOutcome: 'USEFUL_CONFIRMED',
    },
  ];

  const confirmationsByRequest: Record<string, Record<string, { happened: HappenedAnswer; useful: boolean | null }>> = {
    req_seed_01: {
      usr_asha: { happened: 'YES', useful: true },
      usr_priya: { happened: 'YES', useful: true },
    },
  };

  const challengeEntriesByUser: Record<string, ChallengeEntryDto> = {
    usr_daniel: {
      entryId: 'che_seed_daniel',
      challengeId: 'ch_beyond_01',
      contentId: 'story_01',
      revisionNumber: 1,
      status: 'PUBLISHED',
      title: 'A retrospective everyone can contribute to',
      summary: 'One small change helped quieter voices join our team retrospective.',
      bodyText: 'We asked everyone to write first, then share.',
      audienceId: 'aud_pilot',
      pointsAwarded: 20,
    },
    usr_elena: {
      entryId: 'che_seed_elena',
      challengeId: 'ch_beyond_01',
      contentId: 'story_06',
      revisionNumber: 1,
      status: 'PUBLISHED',
      title: 'Testing denied access in PostgreSQL row policies',
      summary: 'Why database owners can accidentally bypass RLS.',
      bodyText: 'We added automated integration tests that run as a non-privileged application role.',
      audienceId: 'aud_pilot',
      pointsAwarded: 20,
    },
    usr_rohan: {
      entryId: 'che_seed_rohan',
      challengeId: 'ch_beyond_01',
      contentId: 'story_02',
      revisionNumber: 1,
      status: 'PUBLISHED',
      title: 'Cutting API tail latency with Java 21 Virtual Threads',
      summary: 'What we learned migrating a connection-heavy Spring Boot service.',
      bodyText: 'Enabling Java 21 virtual threads alongside PostgreSQL row locks dropped tail latency.',
      audienceId: 'aud_pilot',
      pointsAwarded: 20,
    },
  };

  const auditLog: AuditEventDto[] = [
    {
      id: 'aud_ev_01',
      actorId: 'usr_priya',
      actionType: 'REVISION_APPROVED',
      targetType: 'CONTENT_REVISION',
      targetId: 'rev_story_01',
      details: 'Reviewer Priya Nair approved Revision 1 of story_01 (Author: Daniel Wright). Audience: Colleagues in the pilot.',
      createdAt: '2026-10-26T08:30:00Z',
    },
  ];

  return {
    users,
    storiesAndUpdates,
    radioQueues,
    cultureCasts,
    portalItems,
    requests,
    confirmationsByRequest,
    idempotencyMap: {},
    aiActionsUsed: 18,
    aiActionCap: 720,
    auditLog,
    challengeEntriesByUser,
    challengeIdempotencyHashes: {},
    appreciations: [],
    leaderboardOptOut: {},
  };
}

function loadStore(): LocalStoreState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as LocalStoreState;
      if (parsed.challengeEntriesByUser && parsed.appreciations) {
        return parsed;
      }
    }
  } catch {
    // ignore
  }
  const initial = getInitialSeedState();
  saveStore(initial);
  return initial;
}

function saveStore(state: LocalStoreState) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // ignore
  }
}

export function resetDemoState(): LocalStoreState {
  const fresh = getInitialSeedState();
  saveStore(fresh);
  return fresh;
}

function computeBilateralOutcome(
  confs: Record<string, { happened: HappenedAnswer; useful: boolean | null }> | undefined,
  requesterId: string,
  hostId: string
): BilateralOutcome {
  if (!confs) return 'UNKNOWN';
  const r = confs[requesterId];
  const h = confs[hostId];
  if (!r || !h) return 'UNKNOWN';
  if (r.happened === 'NOT_YET' || h.happened === 'NOT_YET') return 'UNKNOWN';
  if (r.happened === 'NO' || h.happened === 'NO') return 'NOT_HAPPENED';
  if (r.happened === 'YES' && r.useful === true && h.happened === 'YES' && h.useful === true) {
    return 'USEFUL_CONFIRMED';
  }
  return 'NOT_USEFUL';
}

function computeDemoRecognitionProgress(store: LocalStoreState, userId: string): RecognitionProgressDto {
  const user = store.users.find((u) => u.id === userId) || store.users[0];
  const publishedStoryIds = new Set(
    store.storiesAndUpdates
      .filter((s) => s.status === 'PUBLISHED' && s.contentType === 'STORY' && s.audienceId === 'aud_pilot')
      .map((s) => s.id)
  );

  const entry = store.challengeEntriesByUser[userId];
  const hasPublishedChallengeStory =
    (entry && entry.status === 'PUBLISHED' && publishedStoryIds.has(entry.contentId)) ||
    store.storiesAndUpdates.some(
      (s) => s.authorId === userId && s.status === 'PUBLISHED' && s.contentType === 'STORY' && publishedStoryIds.has(s.id)
    );
  const contributionPoints = hasPublishedChallengeStory ? 20 : 0;

  const given = store.appreciations.filter(
    (a) => a.actorId === userId && publishedStoryIds.has(a.contentId)
  );
  const givingPoints = Math.min(3, given.length) * 5;
  const distinctTeamsSupported = new Set(given.map((a) => a.recipientTeamSnapshot)).size;

  const received = store.appreciations.filter(
    (a) => a.recipientId === userId && publishedStoryIds.has(a.contentId)
  );
  const receivedDistinctCount = new Set(received.map((a) => a.actorId)).size;
  const receivingPoints = Math.min(5, receivedDistinctCount) * 5;

  const badges: string[] = [];
  if (contributionPoints >= 20) badges.push('First Spark');
  if (distinctTeamsSupported >= 3) badges.push('Connection Maker');
  if (receivedDistinctCount >= 5) badges.push('Community Favourite');

  return {
    userId: user.id,
    displayName: user.displayName,
    weekStart: DEMO_WEEK_START,
    timezone: 'UTC',
    points: Math.min(60, contributionPoints + givingPoints + receivingPoints),
    contributionPoints,
    givenCount: given.length,
    givingPoints,
    receivedDistinctCount,
    receivingPoints,
    distinctTeamsSupported,
    badges,
    showOnLeaderboard: !store.leaderboardOptOut[user.id],
  };
}

export const pulseApi = {
  async getPersonas(activeUserId: string): Promise<UserProfileDto[]> {
    if (getAppMode() === 'live') {
      return liveRequest<UserProfileDto[]>('/personas', activeUserId);
    }
    return loadStore().users;
  },

  async getCurrentUser(activeUserId: string): Promise<UserProfileDto> {
    if (getAppMode() === 'live') {
      return liveRequest<UserProfileDto>('/me', activeUserId);
    }
    const store = loadStore();
    const found = store.users.find((u) => u.id === activeUserId);
    if (!found) {
      throw new PulseHttpError(401, 'UNAUTHORIZED', `401: Unknown employee identity: ${activeUserId}`);
    }
    return found;
  },

  async updatePreferences(
    activeUserId: string,
    prefs: { interests?: string[]; notificationFrequency?: string; hostPaused?: boolean }
  ): Promise<UserProfileDto> {
    if (getAppMode() === 'live') {
      return liveRequest<UserProfileDto>('/me/preferences', activeUserId, {
        method: 'PATCH',
        body: JSON.stringify(prefs),
      });
    }
    const store = loadStore();
    const user = store.users.find((u) => u.id === activeUserId) || store.users[0];
    if (prefs.interests) user.interests = prefs.interests;
    if (prefs.notificationFrequency) user.notificationFrequency = prefs.notificationFrequency;
    if (typeof prefs.hostPaused === 'boolean') user.hostPaused = prefs.hostPaused;
    saveStore(store);
    return user;
  },

  async getFeed(activeUserId: string, category = 'FOR_YOU', query = ''): Promise<ContentCardDto[]> {
    if (getAppMode() === 'live') {
      const params = new URLSearchParams();
      if (category) params.set('category', category);
      if (query) params.set('q', query);
      return liveRequest<ContentCardDto[]>(`/feed?${params.toString()}`, activeUserId);
    }
    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];

    let items = store.storiesAndUpdates.filter(
      (item) => item.status === 'PUBLISHED' && caller.audiences.includes(item.audienceId)
    );

    if (category === 'LEARNING') {
      items = items.filter(
        (i) => i.contentType === 'STORY' && (i.tags.includes('team learning') || i.tags.includes('delivery excellence'))
      );
    } else if (category === 'COMMUNITY') {
      items = items.filter(
        (i) => i.contentType === 'STORY' && (i.tags.includes('culture') || i.tags.includes('inclusion') || i.tags.includes('onboarding'))
      );
    } else if (category === 'UPDATES') {
      items = items.filter((i) => i.contentType === 'UPDATE');
    }

    if (query.trim()) {
      const q = query.trim().toLowerCase();
      items = items.filter(
        (i) =>
          i.title.toLowerCase().includes(q) ||
          i.summary.toLowerCase().includes(q) ||
          i.tags.some((t) => t.toLowerCase().includes(q))
      );
    }

    return items
      .map((item) => {
        const matched = item.tags.filter((t) =>
          caller.interests.map((x) => x.toLowerCase()).includes(t.toLowerCase())
        );
        const host = store.users.find((u) => u.id === item.authorId);
        const hostPaused = host?.hostPaused ?? false;
        let inv = item.invitation ? { ...item.invitation } : null;
        if (inv) {
          inv.paused = inv.paused || hostPaused;
          inv.remainingPlaces = Math.max(0, inv.capacity - inv.acceptedCount);
          inv.statusLabel = inv.paused ? 'PAUSED' : inv.remainingPlaces === 0 ? 'FULL' : 'OPEN';
        }
        let reason = 'WHY THIS? ';
        if (matched.length > 0) {
          reason += `Your interest: ${matched.join(', ')}`;
        } else if (item.contentType === 'UPDATE') {
          reason += 'Approved company update for your audience';
        } else {
          reason += `Shared with ${item.audienceName}`;
        }
        if (inv) {
          if (inv.statusLabel === 'OPEN') {
            reason += ` · ${inv.durationMinutes} min conversation / ${inv.remainingPlaces} ${
              inv.remainingPlaces === 1 ? 'place' : 'places'
            } available`;
          } else if (inv.statusLabel === 'FULL') {
            reason += ` · Invitation full (${inv.capacity} places filled)`;
          } else if (inv.statusLabel === 'PAUSED') {
            reason += ' · Host has paused availability this week';
          }
        }
        const score =
          matched.length * 20 + (inv && inv.statusLabel === 'OPEN' ? 25 : 0) + (item.authorId !== caller.id ? 10 : 0);
        return { card: { ...item, invitation: inv, factualExplanation: reason }, score };
      })
      .sort((a, b) => b.score - a.score)
      .map((x) => x.card);
  },

  async getCurrentChallenge(activeUserId: string): Promise<ChallengeDto> {
    if (getAppMode() === 'live') {
      return liveRequest<ChallengeDto>('/challenges/current', activeUserId);
    }
    const store = loadStore();
    return {
      id: 'ch_beyond_01',
      title: 'Beyond My Job Title',
      prompt: 'Share a hobby, a lesson, or someone who helped you beyond your immediate role. Participation is voluntary.',
      startsAt: '2026-11-16T00:00:00Z',
      endsAt: '2026-11-23T00:00:00Z',
      timezone: 'UTC',
      weekStart: DEMO_WEEK_START,
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the Pilot',
      status: 'ACTIVE',
      myEntry: store.challengeEntriesByUser[activeUserId] || null,
    };
  },

  async submitChallengeEntry(
    activeUserId: string,
    challengeId: string,
    idempotencyKey: string,
    payload: { title: string; bodyText: string; submitForReview?: boolean }
  ): Promise<ChallengeEntryDto> {
    if (getAppMode() === 'live') {
      return liveRequest<ChallengeEntryDto>(`/challenges/${challengeId}/entries`, activeUserId, {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify(payload),
      });
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId);
    if (!caller || !caller.roles.includes('CREATOR')) {
      throw new PulseHttpError(403, 'FORBIDDEN_ACCESS', '403: Requires CREATOR role to submit a challenge entry.');
    }
    const title = (payload.title || '').trim();
    const bodyText = (payload.bodyText || '').trim();
    if (title.length < 5 || title.length > 100) {
      throw new PulseHttpError(422, 'VALIDATION_FAILED', '422: Title must be between 5 and 100 characters.');
    }
    if (bodyText.length < 30 || bodyText.length > 1200) {
      throw new PulseHttpError(422, 'VALIDATION_FAILED', '422: Story body must be between 30 and 1,200 characters.');
    }

    const scopedKey = `${activeUserId}:${idempotencyKey}`;
    const payloadHash = `${title}|${bodyText}|${payload.submitForReview ?? true}`;
    const prevIdem = store.challengeIdempotencyHashes[scopedKey];
    if (prevIdem) {
      if (prevIdem.payloadHash !== payloadHash) {
        throw new PulseHttpError(
          409,
          'IDEMPOTENCY_CONFLICT',
          '409: Idempotency-Key was already used with a different submission payload.'
        );
      }
      return store.challengeEntriesByUser[activeUserId];
    }

    const existing = store.challengeEntriesByUser[activeUserId];
    if (existing && existing.status !== 'DRAFT') {
      throw new PulseHttpError(
        409,
        'DUPLICATE_ENTRY',
        `409: One challenge story per person each week (current status: ${existing.status}).`
      );
    }

    const nextRevision = existing ? existing.revisionNumber + 1 : 1;
    const contentId = existing ? existing.contentId : `story_ch_${activeUserId}`;
    const summary = bodyText.length > 130 ? bodyText.slice(0, 130) + '...' : bodyText;
    const status = payload.submitForReview === false ? 'DRAFT' : 'IN_REVIEW';

    const entry: ChallengeEntryDto = {
      entryId: existing ? existing.entryId : `che_${activeUserId}`,
      challengeId,
      contentId,
      revisionNumber: nextRevision,
      status,
      title,
      summary,
      bodyText,
      audienceId: 'aud_pilot',
      reviewerNotes: null,
      pointsAwarded: 0,
    };

    store.challengeEntriesByUser[activeUserId] = entry;
    store.challengeIdempotencyHashes[scopedKey] = { entryUserId: activeUserId, payloadHash };

    const portalItem: PortalQueueItemDto = {
      id: contentId,
      revisionId: `rev_${contentId}_v${nextRevision}`,
      contentType: 'STORY',
      title,
      summary,
      bodyText,
      authorId: caller.id,
      authorName: caller.displayName,
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      status,
      currentVersion: nextRevision,
      publishedVersion: null,
      hasAudio: false,
      transcriptChecked: true,
      mediaReady: true,
      approvedBy: null,
      approvedByName: null,
      reviewerNotes: null,
      scheduledFor: null,
      updatedAt: new Date().toISOString(),
    };

    const pIdx = store.portalItems.findIndex((p) => p.id === contentId);
    if (pIdx >= 0) store.portalItems[pIdx] = portalItem;
    else store.portalItems.unshift(portalItem);

    saveStore(store);
    return entry;
  },

  async appreciateStory(activeUserId: string, contentId: string): Promise<AppreciationResultDto> {
    if (getAppMode() === 'live') {
      return liveRequest<AppreciationResultDto>(`/content/${contentId}/appreciations`, activeUserId, {
        method: 'POST',
      });
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId);
    if (!caller) throw new PulseHttpError(401, 'UNAUTHORIZED', '401: Unknown employee identity.');

    const story = store.storiesAndUpdates.find((s) => s.id === contentId && s.status === 'PUBLISHED');
    if (!story) {
      throw new PulseHttpError(404, 'RESOURCE_NOT_FOUND', '404: Only published stories can receive appreciation.');
    }
    if (!caller.audiences.includes(story.audienceId)) {
      throw new PulseHttpError(403, 'FORBIDDEN_ACCESS', '403: Access denied to restricted story audience.');
    }
    if (story.authorId === caller.id) {
      throw new PulseHttpError(422, 'VALIDATION_FAILED', '422: Self-appreciation is disallowed.');
    }

    const existing = store.appreciations.find((a) => a.actorId === caller.id && a.contentId === contentId);
    if (existing) {
      return {
        appreciationId: existing.id,
        contentId,
        actorId: caller.id,
        recipientId: story.authorId,
        weekStart: DEMO_WEEK_START,
        alreadyAppreciated: true,
        giverPointsDelta: 0,
        receiverPointsDelta: 0,
        actorProgress: computeDemoRecognitionProgress(store, caller.id),
      };
    }

    const beforeGiver = computeDemoRecognitionProgress(store, caller.id);
    const beforeReceiver = computeDemoRecognitionProgress(store, story.authorId);

    const newApp: DemoAppreciationRecord = {
      id: 'app_' + Math.random().toString(36).slice(2, 8),
      actorId: caller.id,
      contentId,
      recipientId: story.authorId,
      weekStart: DEMO_WEEK_START,
      actorTeamSnapshot: caller.functionName,
      recipientTeamSnapshot: story.authorFunction,
    };
    store.appreciations.push(newApp);
    saveStore(store);

    const afterGiver = computeDemoRecognitionProgress(store, caller.id);
    const afterReceiver = computeDemoRecognitionProgress(store, story.authorId);

    return {
      appreciationId: newApp.id,
      contentId,
      actorId: caller.id,
      recipientId: story.authorId,
      weekStart: DEMO_WEEK_START,
      alreadyAppreciated: false,
      giverPointsDelta: afterGiver.givingPoints - beforeGiver.givingPoints,
      receiverPointsDelta: afterReceiver.receivingPoints - beforeReceiver.receivingPoints,
      actorProgress: afterGiver,
    };
  },

  async getMyRecognitionProgress(activeUserId: string, week?: string): Promise<RecognitionProgressDto> {
    if (getAppMode() === 'live') {
      const qs = week ? `?week=${encodeURIComponent(week)}` : '';
      return liveRequest<RecognitionProgressDto>(`/recognition/me${qs}`, activeUserId);
    }
    return computeDemoRecognitionProgress(loadStore(), activeUserId);
  },

  async getLeaderboard(activeUserId: string, audienceId = 'aud_pilot', week?: string): Promise<LeaderboardResponseDto> {
    if (getAppMode() === 'live') {
      const params = new URLSearchParams({ audienceId });
      if (week) params.set('week', week);
      return liveRequest<LeaderboardResponseDto>(`/recognition/leaderboard?${params.toString()}`, activeUserId);
    }
    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId);
    if (!caller || !caller.audiences.includes(audienceId)) {
      throw new PulseHttpError(403, 'FORBIDDEN_ACCESS', `403: Access denied to audience ${audienceId}`);
    }

    const candidates = store.users
      .filter((u) => u.audiences.includes(audienceId) && !store.leaderboardOptOut[u.id])
      .map((u) => {
        const prog = computeDemoRecognitionProgress(store, u.id);
        return { user: u, points: prog.points, badges: prog.badges };
      })
      .sort((a, b) => b.points - a.points || a.user.displayName.localeCompare(b.user.displayName));

    const entries: LeaderboardRowDto[] = [];
    let currentRank = 1;
    for (let i = 0; i < Math.min(5, candidates.length); i++) {
      if (i > 0 && candidates[i].points < candidates[i - 1].points) {
        currentRank = i + 1;
      }
      entries.push({
        rank: currentRank,
        userId: candidates[i].user.id,
        displayName: candidates[i].user.displayName,
        functionName: candidates[i].user.functionName,
        region: candidates[i].user.region,
        points: candidates[i].points,
        badges: candidates[i].badges,
      });
    }

    return {
      weekStart: week || DEMO_WEEK_START,
      timezone: 'UTC',
      audienceId,
      entries,
    };
  },

  async updateRecognitionPreferences(
    activeUserId: string,
    showOnLeaderboard: boolean
  ): Promise<RecognitionProgressDto> {
    if (getAppMode() === 'live') {
      return liveRequest<RecognitionProgressDto>('/recognition/preferences', activeUserId, {
        method: 'PUT',
        body: JSON.stringify({ showOnLeaderboard }),
      });
    }
    const store = loadStore();
    store.leaderboardOptOut[activeUserId] = !showOnLeaderboard;
    saveStore(store);
    return computeDemoRecognitionProgress(store, activeUserId);
  },

  async getRadioQueues(activeUserId: string): Promise<AudioListenItemDto[]> {
    if (getAppMode() === 'live') {
      return liveRequest<AudioListenItemDto[]>('/listen/radio', activeUserId);
    }
    return loadStore().radioQueues;
  },

  async getCultureCasts(activeUserId: string): Promise<AudioListenItemDto[]> {
    if (getAppMode() === 'live') {
      return liveRequest<AudioListenItemDto[]>('/listen/culture-cast', activeUserId);
    }
    return loadStore().cultureCasts;
  },

  async savePlaybackProgress(
    activeUserId: string,
    contentId: string,
    positionSeconds: number,
    durationSeconds: number
  ): Promise<void> {
    if (getAppMode() === 'live') {
      await liveRequest<unknown>('/listen/progress', activeUserId, {
        method: 'PUT',
        body: JSON.stringify({ contentId, positionSeconds, durationSeconds }),
      });
      return;
    }
    const store = loadStore();
    const r = store.radioQueues.find((x) => x.id === contentId);
    if (r) r.resumePositionSeconds = positionSeconds;
    const c = store.cultureCasts.find((x) => x.id === contentId);
    if (c) c.resumePositionSeconds = positionSeconds;
    saveStore(store);
  },

  async listRequests(activeUserId: string): Promise<ConnectionRequestDto[]> {
    if (getAppMode() === 'live') {
      return liveRequest<ConnectionRequestDto[]>('/requests', activeUserId);
    }
    const store = loadStore();
    return store.requests
      .filter((r) => r.requesterId === activeUserId || r.hostId === activeUserId)
      .map((r) => {
        const confs = store.confirmationsByRequest[r.id] || {};
        return {
          ...r,
          myConfirmation: confs[activeUserId] || null,
          confirmationsCount: Object.keys(confs).length,
          bilateralOutcome: computeBilateralOutcome(confs, r.requesterId, r.hostId),
        };
      });
  },

  async sendInvitationRequest(
    activeUserId: string,
    idempotencyKey: string,
    payload: { invitationId: string; messageText: string; aiAssisted: boolean }
  ): Promise<ConnectionRequestDto> {
    if (getAppMode() === 'live') {
      return liveRequest<ConnectionRequestDto>('/requests', activeUserId, {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify(payload),
      });
    }

    const store = loadStore();
    const idemScopedKey = `${activeUserId}:${idempotencyKey}`;
    if (store.idempotencyMap[idemScopedKey]) {
      const existing = store.requests.find((r) => r.id === store.idempotencyMap[idemScopedKey]);
      if (existing) return existing;
    }

    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    const story = store.storiesAndUpdates.find((s) => s.invitation?.id === payload.invitationId);
    if (!story || !story.invitation) {
      throw new PulseHttpError(404, 'RESOURCE_NOT_FOUND', '404: Invitation not found.');
    }
    const inv = story.invitation;
    if (inv.hostId === caller.id) {
      throw new PulseHttpError(422, 'VALIDATION_FAILED', '422: Hosts cannot send an invitation request to themselves.');
    }
    if (inv.acceptedCount >= inv.capacity || inv.statusLabel !== 'OPEN') {
      throw new PulseHttpError(409, 'CAPACITY_FULL', '409: Invitation is already at full capacity or paused.');
    }
    const duplicate = store.requests.find(
      (r) => r.invitationId === inv.id && r.requesterId === caller.id && r.state !== 'CANCELLED'
    );
    if (duplicate) {
      throw new PulseHttpError(409, 'CAPACITY_FULL', '409: You already have an active request for this invitation.');
    }

    const newReq: ConnectionRequestDto = {
      id: 'req_' + Math.random().toString(36).slice(2, 9),
      invitationId: inv.id,
      storyId: story.id,
      storyTitle: story.title,
      topicLabel: inv.topicLabel,
      requesterId: caller.id,
      requesterName: caller.displayName,
      requesterRegion: caller.region,
      requesterFunction: caller.functionName,
      hostId: inv.hostId,
      hostName: inv.hostName,
      hostRegion: inv.hostRegion,
      hostFunction: inv.hostFunction,
      messageText: payload.messageText.trim(),
      aiAssisted: payload.aiAssisted,
      state: 'PENDING',
      version: 1,
      capacity: inv.capacity,
      acceptedCount: inv.acceptedCount,
      remainingPlaces: inv.remainingPlaces,
      createdAt: new Date().toISOString(),
      myConfirmation: null,
      confirmationsCount: 0,
      bilateralOutcome: 'UNKNOWN',
    };

    store.requests.unshift(newReq);
    store.idempotencyMap[idemScopedKey] = newReq.id;
    saveStore(store);
    return newReq;
  },

  async updateRequestState(
    activeUserId: string,
    requestId: string,
    targetState: 'ACCEPTED' | 'DECLINED' | 'CANCELLED',
    expectedVersion: number
  ): Promise<ConnectionRequestDto> {
    if (getAppMode() === 'live') {
      return liveRequest<ConnectionRequestDto>(`/requests/${requestId}`, activeUserId, {
        method: 'PATCH',
        body: JSON.stringify({ targetState, expectedVersion }),
      });
    }

    const store = loadStore();
    const req = store.requests.find((r) => r.id === requestId);
    if (!req) throw new PulseHttpError(404, 'RESOURCE_NOT_FOUND', '404: Request not found.');
    if (req.version !== expectedVersion || req.state !== 'PENDING') {
      throw new PulseHttpError(
        409,
        'STALE_VERSION',
        `409: Stale version or request is no longer PENDING (current state: ${req.state}).`
      );
    }

    const story = store.storiesAndUpdates.find((s) => s.id === req.storyId);
    const inv = story?.invitation;

    if (targetState === 'ACCEPTED') {
      if (req.hostId !== activeUserId) {
        throw new PulseHttpError(403, 'FORBIDDEN_ACCESS', '403: Only the invitation host can accept this request.');
      }
      if (!inv || inv.acceptedCount >= inv.capacity) {
        throw new PulseHttpError(409, 'CAPACITY_FULL', '409: Full capacity reached — no remaining places this week.');
      }
      inv.acceptedCount += 1;
      inv.remainingPlaces = Math.max(0, inv.capacity - inv.acceptedCount);
      inv.statusLabel = inv.remainingPlaces === 0 ? 'FULL' : 'OPEN';

      req.state = 'ACCEPTED';
      req.version += 1;
      req.acceptedCount = inv.acceptedCount;
      req.remainingPlaces = inv.remainingPlaces;
    } else if (targetState === 'DECLINED') {
      if (req.hostId !== activeUserId) {
        throw new PulseHttpError(403, 'FORBIDDEN_ACCESS', '403: Only the invitation host can decline this request.');
      }
      req.state = 'DECLINED';
      req.version += 1;
    } else if (targetState === 'CANCELLED') {
      if (req.requesterId !== activeUserId) {
        throw new PulseHttpError(403, 'FORBIDDEN_ACCESS', '403: Only the requester can cancel their pending request.');
      }
      req.state = 'CANCELLED';
      req.version += 1;
    }

    saveStore(store);
    return req;
  },

  async submitConfirmation(
    activeUserId: string,
    requestId: string,
    happened: HappenedAnswer,
    useful: boolean | null
  ): Promise<ConnectionRequestDto> {
    if (getAppMode() === 'live') {
      return liveRequest<ConnectionRequestDto>(`/requests/${requestId}/confirmations`, activeUserId, {
        method: 'POST',
        body: JSON.stringify({ happened, useful }),
      });
    }

    const store = loadStore();
    const req = store.requests.find((r) => r.id === requestId);
    if (!req) throw new PulseHttpError(404, 'RESOURCE_NOT_FOUND', '404: Request not found.');
    if (req.requesterId !== activeUserId && req.hostId !== activeUserId) {
      throw new PulseHttpError(403, 'FORBIDDEN_ACCESS', '403: Only the two participants can submit private feedback.');
    }

    if (!store.confirmationsByRequest[requestId]) {
      store.confirmationsByRequest[requestId] = {};
    }
    store.confirmationsByRequest[requestId][activeUserId] = { happened, useful: happened === 'YES' ? useful : null };

    const confs = store.confirmationsByRequest[requestId];
    req.myConfirmation = confs[activeUserId];
    req.confirmationsCount = Object.keys(confs).length;
    req.bilateralOutcome = computeBilateralOutcome(confs, req.requesterId, req.hostId);

    saveStore(store);
    return req;
  },

  async requestAiAssist(
    activeUserId: string,
    assistType: 'INVITATION_DRAFT' | 'STORY_POLISH' | 'UPDATE_SUMMARY',
    contextId: string,
    userDraftText = ''
  ): Promise<AiAssistanceResponseDto> {
    if (getAppMode() === 'live') {
      return liveRequest<AiAssistanceResponseDto>('/ai/assist', activeUserId, {
        method: 'POST',
        body: JSON.stringify({ assistType, contextId, userDraftText }),
      });
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    const story = store.storiesAndUpdates.find((s) => s.id === contextId);

    const lower = userDraftText.toLowerCase();
    if (
      lower.includes('appraisal score') ||
      lower.includes('rank employees') ||
      lower.includes('private message') ||
      lower.includes('salary')
    ) {
      throw new PulseHttpError(
        422,
        'VALIDATION_FAILED',
        '422: Refused by Mastek Campfire AI Trust Policy — cannot infer sensitive attributes, private messages, or employee appraisal scores.'
      );
    }

    if (story && !caller.audiences.includes(story.audienceId)) {
      throw new PulseHttpError(403, 'FORBIDDEN_ACCESS', '403: Access denied to restricted audience content.');
    }

    const hostFirstName = story ? story.authorName.split(' ')[0] : 'Daniel';
    const title = story ? story.title : 'A retrospective everyone can contribute to';
    const summary = story ? story.summary : 'One small change helped quieter voices join our team retrospective.';

    let templateFallback = '';
    let aiSuggestion = '';

    if (assistType === 'INVITATION_DRAFT') {
      templateFallback = `Hi ${hostFirstName}, I read your story "${title}" and would love to have a 15-minute conversation to learn from your experience. Could we compare notes this week?`;
      aiSuggestion = `Hi ${hostFirstName}, I would love to learn how you made retrospectives more inclusive in "${title}". As a colleague interested in ${caller.interests
        .slice(0, 2)
        .join(' and ')}, could we compare notes for 15 minutes this week?`;
    } else if (assistType === 'UPDATE_SUMMARY') {
      templateFallback = `Official Update Brief: ${title} — ${summary} (Source: ${story?.officialSource || 'Internal Comms'})`;
      aiSuggestion = `Source-Linked Summary for "${title}":\n• ${summary}\n• Verified Source: ${
        story?.officialSource || 'Official Internal Comms'
      }\n• Next Step: Review the full brief link before sharing with your pod.`;
    } else {
      templateFallback = `What changed?\n${
        userDraftText || 'We tried five minutes of silent note-taking before open discussion.'
      }\n\nWhat did we learn?\nQuieter colleagues had a comfortable opening to share specific ideas.`;
      aiSuggestion = `Structured Story Draft (Human review required):\n\nWhat changed: ${
        userDraftText || 'After connecting across teams, we started our retrospective with four minutes of silent writing.'
      }\n\nWhat we learned: Quieter voices contributed concrete ideas immediately, and our retrospective stayed focused and actionable.`;
    }

    const capReached = store.aiActionsUsed >= store.aiActionCap;
    if (!capReached) {
      store.aiActionsUsed += 1;
      saveStore(store);
    }

    const pct = Math.round((store.aiActionsUsed / store.aiActionCap) * 1000) / 10;
    const alertLevel =
      store.aiActionsUsed >= store.aiActionCap
        ? 'CAPPED_100'
        : pct >= 80
        ? 'ALERT_80'
        : pct >= 50
        ? 'ALERT_50'
        : 'NORMAL';

    return {
      assistType,
      aiAvailable: !capReached,
      aiSuggestion: capReached ? null : aiSuggestion,
      templateFallback,
      sourceLink: story?.externalUrl || `/stories/${contextId}`,
      aiLabel: capReached
        ? 'AI budget hard cap reached (100%) — Optional AI disabled automatically. Using verified non-AI template fallback.'
        : 'AI-assisted suggestion (grounded in permitted fields) — Review and edit before choosing your next action.',
      budgetStatus: {
        actionsUsed: store.aiActionsUsed,
        actionCap: store.aiActionCap,
        percentUsed: pct,
        estimatedInputTokens: store.aiActionsUsed * 1000,
        estimatedOutputTokens: store.aiActionsUsed * 500,
        alertLevel,
        hardCapReached: capReached,
      },
    };
  },

  async createContribution(
    activeUserId: string,
    payload: {
      title: string;
      bodyText: string;
      audienceId: string;
      includeInvitation: boolean;
      invitationTopic: string;
      invitationCapacity: number;
      invitationExpiresDays: number;
      submitForReview: boolean;
    }
  ): Promise<PortalQueueItemDto> {
    if (getAppMode() === 'live') {
      return liveRequest<PortalQueueItemDto>('/content', activeUserId, {
        method: 'POST',
        body: JSON.stringify(payload),
      });
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    const id = 'story_' + Math.random().toString(36).slice(2, 8);
    const revId = 'rev_' + Math.random().toString(36).slice(2, 8);
    const status = payload.submitForReview ? 'IN_REVIEW' : 'DRAFT';

    const newItem: PortalQueueItemDto = {
      id,
      revisionId: revId,
      contentType: 'STORY',
      title: payload.title.trim(),
      summary: payload.bodyText.trim().slice(0, 130) + (payload.bodyText.length > 130 ? '...' : ''),
      bodyText: payload.bodyText.trim(),
      authorId: caller.id,
      authorName: caller.displayName,
      audienceId: payload.audienceId,
      audienceName: payload.audienceId === 'aud_all' ? 'All employees' : 'Colleagues in the pilot',
      status,
      currentVersion: 1,
      publishedVersion: null,
      hasAudio: false,
      transcriptChecked: true,
      mediaReady: true,
      approvedBy: null,
      approvedByName: null,
      reviewerNotes: null,
      scheduledFor: null,
      updatedAt: new Date().toISOString(),
    };

    store.portalItems.unshift(newItem);
    saveStore(store);
    return newItem;
  },

  async getPortalQueue(activeUserId: string): Promise<PortalQueueItemDto[]> {
    if (getAppMode() === 'live') {
      return liveRequest<PortalQueueItemDto[]>('/portal/queue', activeUserId);
    }
    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId);
    if (
      !caller ||
      (!caller.roles.includes('REVIEWER') &&
        !caller.roles.includes('PUBLISHER') &&
        !caller.roles.includes('MODERATOR') &&
        !caller.roles.includes('ADMIN'))
    ) {
      throw new PulseHttpError(
        403,
        'FORBIDDEN_ACCESS',
        `403: User '${caller?.displayName || activeUserId}' lacks Publishing Portal role.`
      );
    }
    return store.portalItems.filter((item) => caller.audiences.includes(item.audienceId));
  },

  async reviewRevision(
    activeUserId: string,
    contentId: string,
    revisionNumber: number,
    decision: 'APPROVE' | 'REQUEST_CHANGES',
    notes: string
  ): Promise<PortalQueueItemDto> {
    if (getAppMode() === 'live') {
      return liveRequest<PortalQueueItemDto>(`/content/${contentId}/review`, activeUserId, {
        method: 'POST',
        body: JSON.stringify({ revisionNumber, decision, notes }),
      });
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    if (!caller.roles.includes('REVIEWER')) {
      throw new PulseHttpError(
        403,
        'FORBIDDEN_ACCESS',
        `403: User '${caller.displayName}' lacks required scoped role: REVIEWER.`
      );
    }

    const item = store.portalItems.find((i) => i.id === contentId);
    if (!item) throw new PulseHttpError(404, 'RESOURCE_NOT_FOUND', '404: Content item not found.');

    if (item.authorId === caller.id) {
      throw new PulseHttpError(
        403,
        'SELF_APPROVAL_DENIED',
        '403: Self-Approval Denied — Reviewers cannot approve their own submissions.'
      );
    }
    if (item.currentVersion !== revisionNumber || item.status !== 'IN_REVIEW') {
      throw new PulseHttpError(409, 'STALE_VERSION', `409: Revision ${revisionNumber} is not awaiting review.`);
    }

    if (decision === 'APPROVE') {
      item.status = 'APPROVED';
      item.approvedBy = caller.id;
      item.approvedByName = caller.displayName;
      item.reviewerNotes = notes || 'Approved after source, audience, and transcript check.';
    } else {
      item.status = 'DRAFT';
      item.approvedBy = null;
      item.approvedByName = null;
      item.reviewerNotes = notes || 'Changes requested by Reviewer.';
    }
    item.updatedAt = new Date().toISOString();

    const chEntry = store.challengeEntriesByUser[item.authorId];
    if (chEntry && chEntry.contentId === item.id) {
      chEntry.status = item.status;
      chEntry.reviewerNotes = item.reviewerNotes;
    }

    saveStore(store);
    return item;
  },

  async publishOrSchedule(
    activeUserId: string,
    contentId: string,
    mode: 'IMMEDIATE' | 'SCHEDULED',
    scheduledFor?: string
  ): Promise<PortalQueueItemDto> {
    if (getAppMode() === 'live') {
      return liveRequest<PortalQueueItemDto>(`/content/${contentId}/publish`, activeUserId, {
        method: 'POST',
        body: JSON.stringify({ mode, scheduledFor }),
      });
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    if (!caller.roles.includes('PUBLISHER')) {
      throw new PulseHttpError(
        403,
        'FORBIDDEN_ACCESS',
        `403: User '${caller.displayName}' lacks required scoped role: PUBLISHER.`
      );
    }

    const item = store.portalItems.find((i) => i.id === contentId);
    if (!item) throw new PulseHttpError(404, 'RESOURCE_NOT_FOUND', '404: Content item not found.');
    if (item.status !== 'APPROVED' || !item.approvedBy) {
      throw new PulseHttpError(
        409,
        'STALE_VERSION',
        `409: Revision ${item.currentVersion} cannot be published because it is not APPROVED.`
      );
    }

    if (mode === 'SCHEDULED') {
      item.status = 'SCHEDULED';
      item.scheduledFor = scheduledFor || new Date(Date.now() + 86400000).toISOString();
    } else {
      item.status = 'PUBLISHED';
      item.publishedVersion = item.currentVersion;
      item.scheduledFor = null;

      const author = store.users.find((u) => u.id === item.authorId);
      const liveCard: ContentCardDto = {
        id: item.id,
        contentType: item.contentType,
        title: item.title,
        summary: item.summary,
        bodyText: item.bodyText,
        authorId: item.authorId,
        authorName: item.authorName,
        authorFunction: author?.functionName || 'Colleague',
        authorRegion: author?.region || 'INDIA',
        audienceId: item.audienceId,
        audienceName: item.audienceName,
        tags: ['team learning', 'culture', 'innovation'],
        status: 'PUBLISHED',
        currentVersion: item.currentVersion,
        publishedVersion: item.currentVersion,
        publishedAt: new Date().toISOString(),
        factualExplanation: `WHY THIS? Newly published to ${item.audienceName}`,
        hasAudio: item.hasAudio,
        invitation: null,
      };
      const existingFeedIdx = store.storiesAndUpdates.findIndex((s) => s.id === item.id);
      if (existingFeedIdx >= 0) store.storiesAndUpdates[existingFeedIdx] = liveCard;
      else if (item.contentType === 'STORY' || item.contentType === 'UPDATE') store.storiesAndUpdates.unshift(liveCard);

      const chEntry = store.challengeEntriesByUser[item.authorId];
      if (chEntry && chEntry.contentId === item.id) {
        chEntry.status = 'PUBLISHED';
        chEntry.pointsAwarded = 20;
      }
    }

    item.updatedAt = new Date().toISOString();
    saveStore(store);
    return item;
  },

  async editPortalItemAndInvalidateApproval(
    activeUserId: string,
    contentId: string,
    newTitle: string,
    newBody: string,
    newAudienceId: string
  ): Promise<PortalQueueItemDto> {
    if (getAppMode() === 'live') {
      return liveRequest<PortalQueueItemDto>(`/content/${contentId}/revisions`, activeUserId, {
        method: 'PUT',
        body: JSON.stringify({ title: newTitle, bodyText: newBody, audienceId: newAudienceId }),
      });
    }

    const store = loadStore();
    const item = store.portalItems.find((i) => i.id === contentId);
    if (!item) throw new PulseHttpError(404, 'RESOURCE_NOT_FOUND', '404: Content item not found.');

    item.currentVersion += 1;
    item.revisionId = `rev_${contentId}_v${item.currentVersion}`;
    item.title = newTitle.trim();
    item.bodyText = newBody.trim();
    item.summary = newBody.trim().slice(0, 120) + (newBody.length > 120 ? '...' : '');
    item.audienceId = newAudienceId;
    item.audienceName = newAudienceId === 'aud_all' ? 'All employees' : 'Colleagues in the pilot';
    item.approvedBy = null;
    item.approvedByName = null;
    item.status = 'IN_REVIEW';
    item.updatedAt = new Date().toISOString();

    saveStore(store);
    return item;
  },

  async withdrawContent(activeUserId: string, contentId: string, reason: string): Promise<PortalQueueItemDto> {
    if (getAppMode() === 'live') {
      return liveRequest<PortalQueueItemDto>(`/content/${contentId}/withdraw`, activeUserId, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      });
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    if (!caller.roles.includes('PUBLISHER') && !caller.roles.includes('MODERATOR')) {
      throw new PulseHttpError(
        403,
        'FORBIDDEN_ACCESS',
        '403: Requires PUBLISHER or MODERATOR role to withdraw content.'
      );
    }

    const item = store.portalItems.find((i) => i.id === contentId);
    if (!item) throw new PulseHttpError(404, 'RESOURCE_NOT_FOUND', '404: Item not found.');

    item.status = 'WITHDRAWN';
    item.publishedVersion = null;
    item.updatedAt = new Date().toISOString();

    const feedItem = store.storiesAndUpdates.find((s) => s.id === contentId);
    if (feedItem) feedItem.status = 'WITHDRAWN';

    const chEntry = store.challengeEntriesByUser[item.authorId];
    if (chEntry && chEntry.contentId === item.id) {
      chEntry.status = 'WITHDRAWN';
      chEntry.pointsAwarded = 0;
    }

    saveStore(store);
    return item;
  },

  async getAuditLog(activeUserId: string): Promise<AuditEventDto[]> {
    if (getAppMode() === 'live') {
      return liveRequest<AuditEventDto[]>('/portal/audit-log', activeUserId);
    }
    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId);
    if (
      !caller ||
      (!caller.roles.includes('REVIEWER') &&
        !caller.roles.includes('PUBLISHER') &&
        !caller.roles.includes('MODERATOR') &&
        !caller.roles.includes('ADMIN'))
    ) {
      throw new PulseHttpError(403, 'FORBIDDEN_ACCESS', '403: Portal role required to view audit log.');
    }
    return store.auditLog;
  },

  async getPilotMetricsAndBudget(
    activeUserId: string
  ): Promise<{ metrics: PilotMetricsDto; budget: AiBudgetStatusDto }> {
    if (getAppMode() === 'live') {
      const [metrics, budget] = await Promise.all([
        liveRequest<PilotMetricsDto>('/pilot/metrics', activeUserId),
        liveRequest<AiBudgetStatusDto>('/ai/budget', activeUserId),
      ]);
      return { metrics, budget };
    }

    const store = loadStore();
    const accepted = store.requests.filter((r) => r.state === 'ACCEPTED');
    const declined = store.requests.filter((r) => r.state === 'DECLINED');
    const pending = store.requests.filter((r) => r.state === 'PENDING');
    const decided = accepted.length + declined.length;

    let usefulConfirmed = 0;
    let unknownCount = 0;
    for (const r of accepted) {
      const out = computeBilateralOutcome(store.confirmationsByRequest[r.id], r.requesterId, r.hostId);
      if (out === 'USEFUL_CONFIRMED') usefulConfirmed++;
      else if (out === 'UNKNOWN') unknownCount++;
    }

    const pct = Math.round((store.aiActionsUsed / store.aiActionCap) * 1000) / 10;
    const alertLevel =
      store.aiActionsUsed >= store.aiActionCap
        ? 'CAPPED_100'
        : pct >= 80
        ? 'ALERT_80'
        : pct >= 50
        ? 'ALERT_50'
        : 'NORMAL';

    return {
      metrics: {
        usefulConnectionsCount: usefulConfirmed,
        unknownFeedbackCount: unknownCount,
        usefulConnectionsPct: Math.min(100, Math.round(((usefulConfirmed * 2) / store.users.length) * 1000) / 10),
        usefulConnectionsTargetPct: 20.0,
        acceptanceRatePct: decided > 0 ? Math.round((accepted.length / decided) * 1000) / 10 : 100.0,
        acceptanceTargetPct: 50.0,
        acceptedRequestsCount: accepted.length,
        pendingRequestsCount: pending.length,
        returnParticipationPct: 48.0,
        returnParticipationTargetPct: 40.0,
        medianHostWorkloadMinutes: 30,
        hostWorkloadTargetMinutes: 45,
        materialTrustIncidents: 0,
      },
      budget: {
        actionsUsed: store.aiActionsUsed,
        actionCap: store.aiActionCap,
        percentUsed: pct,
        estimatedInputTokens: store.aiActionsUsed * 1000,
        estimatedOutputTokens: store.aiActionsUsed * 500,
        alertLevel,
        hardCapReached: store.aiActionsUsed >= store.aiActionCap,
      },
    };
  },

  async simulateAiBudgetLevel(
    activeUserId: string,
    level: 'NORMAL' | 'ALERT_50' | 'ALERT_80' | 'CAPPED_100'
  ): Promise<void> {
    if (getAppMode() === 'live') {
      const capMap = { NORMAL: 720, ALERT_50: 6, ALERT_80: 4, CAPPED_100: 1 };
      await liveRequest<unknown>('/ai/budget/simulate-cap', activeUserId, {
        method: 'POST',
        body: JSON.stringify({ actionCap: capMap[level] }),
      });
      return;
    }
    const store = loadStore();
    if (level === 'NORMAL') store.aiActionsUsed = 18;
    else if (level === 'ALERT_50') store.aiActionsUsed = 365;
    else if (level === 'ALERT_80') store.aiActionsUsed = 585;
    else if (level === 'CAPPED_100') store.aiActionsUsed = 720;
    saveStore(store);
  },
};
