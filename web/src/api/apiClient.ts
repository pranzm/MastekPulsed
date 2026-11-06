import {
  AiAssistanceResponseDto,
  AiBudgetStatusDto,
  AudioListenItemDto,
  AuditEventDto,
  BilateralOutcome,
  ConnectionRequestDto,
  ContentCardDto,
  HappenedAnswer,
  PilotMetricsDto,
  PortalQueueItemDto,
  UserProfileDto,
} from './contracts';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/api/apiClient.ts
 * Journey: Unified API Client + Dual-Mode Contract Engine for Mastek Pulse.
 * Why it is designed this way:
 *   1. By default, every method calls our Java 21 + Spring Boot 3.3+ REST API
 *      (`/api/v1/...`) passing `X-Pulse-User-Id` and `Idempotency-Key`.
 *   2. What if a frontend developer is working on a laptop where Java/Docker
 *      isn't running yet? This client automatically falls back to an exact,
 *      stateful in-memory mirror of `V2__seed_pilot_data.sql` and all Java
 *      business rules (Audience Filter Before Rank, 1-Transaction Capacity Lock,
 *      Bilateral Usefulness Confirmation, No Self-Approval, Edit Invalidates
 *      Approval, and the 720-Action AI Budget Gate)!
 *   3. Also synthesizes a real playable Web Audio WAV data URI so clicking
 *      "Play Mastek Radio" or "Play Culture Cast" plays real audio in the browser!
 * ============================================================================
 */

const STORAGE_KEY = 'mastek_pulse_state_v1';

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
      roles: ['EMPLOYEE', 'ADMIN'],
      interests: ['security', 'accessibility', 'cloud architecture'],
      audiences: ['aud_all', 'aud_pilot', 'aud_uk_restricted'],
      notificationFrequency: 'WEEKLY_SUMMARY',
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
        expiresAt: '2026-11-15T23:59:59Z',
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
      authorFunction: 'Internal Comms',
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
        expiresAt: '2026-11-15T23:59:59Z',
        statusLabel: 'OPEN',
      },
    },
    {
      id: 'story_03',
      contentType: 'STORY',
      title: 'Three screen-reader bugs we caught in 20 minutes',
      summary: 'Testing our clinical intake flow with VoiceOver and TalkBack before sprint sign-off.',
      bodyText:
        'Instead of waiting for an external audit, our developers ran a 20-minute keyboard-only and VoiceOver drill every Wednesday. Happy to walk any team through our 5-point accessibility checklist.',
      authorId: 'usr_sophie',
      authorName: 'Sophie Clarke',
      authorFunction: 'Healthcare Delivery',
      authorRegion: 'UK',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      tags: ['accessibility', 'delivery excellence', 'customer stories'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-24T11:00:00Z',
      factualExplanation: 'WHY THIS? Shared with colleagues in the pilot · 15 min conversation / 2 places available',
      hasAudio: false,
      invitation: {
        id: 'inv_story_03',
        hostId: 'usr_sophie',
        hostName: 'Sophie Clarke',
        hostRegion: 'UK',
        hostFunction: 'Healthcare Delivery',
        topicLabel: 'Ask me about this: 20-minute WCAG screen-reader checks',
        durationMinutes: 15,
        capacity: 2,
        acceptedCount: 0,
        remainingPlaces: 2,
        paused: false,
        expiresAt: '2026-11-15T23:59:59Z',
        statusLabel: 'OPEN',
      },
    },
    {
      id: 'story_04',
      contentType: 'STORY',
      title: 'How we built a 30-case evaluation gate for internal AI helpers',
      summary: 'Testing permission boundaries, refusal behavior, and faithfulness before enabling AI drafting.',
      bodyText:
        'Before turning on optional AI summarization, we created 30 synthetic test cases covering unauthorized audience leakage, hallucinated numbers, and template fallback behavior. Zero unauthorized disclosures is our non-negotiable release gate.',
      authorId: 'usr_carlos',
      authorName: 'Carlos Mendez',
      authorFunction: 'Data & AI',
      authorRegion: 'US',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      tags: ['innovation', 'ai engineering', 'security'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-24T09:30:00Z',
      factualExplanation: 'WHY THIS? Your interest: innovation · 15 min conversation / 2 places available',
      hasAudio: false,
      invitation: {
        id: 'inv_story_04',
        hostId: 'usr_carlos',
        hostName: 'Carlos Mendez',
        hostRegion: 'US',
        hostFunction: 'Data & AI',
        topicLabel: 'Ask me about this: 30-case AI evaluation harness',
        durationMinutes: 15,
        capacity: 2,
        acceptedCount: 0,
        remainingPlaces: 2,
        paused: false,
        expiresAt: '2026-11-15T23:59:59Z',
        statusLabel: 'OPEN',
      },
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
        expiresAt: '2026-11-15T23:59:59Z',
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
      id: 'story_07',
      contentType: 'STORY',
      title: 'Keeping React, React Native, and Spring Boot in sync with OpenAPI',
      summary: 'How a single OpenAPI contract stopped frontend-backend integration bugs during our sprint.',
      bodyText:
        'When three developers worked in different AI coding tools, agreeing on the OpenAPI schema and state machine upfront meant our mobile and web screens connected to the Java backend on the first try.',
      authorId: 'usr_asha',
      authorName: 'Asha Patel',
      authorFunction: 'Engineering',
      authorRegion: 'INDIA',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      tags: ['team learning', 'innovation'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-22T10:00:00Z',
      factualExplanation: 'WHY THIS? Your interest: team learning, innovation',
      hasAudio: false,
      invitation: null,
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
    {
      id: 'story_09',
      contentType: 'STORY',
      title: 'Zero-downtime Flyway migrations for 10,000+ user tables',
      summary: 'How to add columns and constraints without locking production traffic.',
      bodyText:
        'We split expand-and-contract migrations into separate releases so active readers never block. (Note: The invitation on this story is already at full capacity to demonstrate the disabled Full Capacity UI state!)',
      authorId: 'usr_rohan',
      authorName: 'Rohan Deshmukh',
      authorFunction: 'Cloud Practice',
      authorRegion: 'INDIA',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      tags: ['cloud architecture', 'delivery excellence'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-21T16:00:00Z',
      factualExplanation: 'WHY THIS? Invitation full (2 places filled)',
      hasAudio: false,
      invitation: {
        id: 'inv_story_09',
        hostId: 'usr_rohan',
        hostName: 'Rohan Deshmukh',
        hostRegion: 'INDIA',
        hostFunction: 'Cloud Practice',
        topicLabel: 'Ask me about this: zero-downtime Flyway migrations',
        durationMinutes: 15,
        capacity: 2,
        acceptedCount: 2,
        remainingPlaces: 0,
        paused: false,
        expiresAt: '2026-11-15T23:59:59Z',
        statusLabel: 'FULL',
      },
    },
    {
      id: 'upd_02',
      contentType: 'UPDATE',
      title: 'Mastek Pulse 4-Week Pilot Kicks Off Across 3 Regions',
      summary: '60 volunteers and 12 hosts across India, UK, and US are testing low-friction colleague connections.',
      bodyText:
        'Participation is completely optional. Over the next four weeks we are measuring whether specific, capacity-capped invitations help colleagues find willing expertise beyond their immediate team.',
      officialSource: 'People & Culture Desk',
      externalUrl: 'https://intranet.mastek.demo/updates/pulse-pilot',
      authorId: 'usr_priya',
      authorName: 'Priya Nair',
      authorFunction: 'People & Culture',
      authorRegion: 'INDIA',
      audienceId: 'aud_all',
      audienceName: 'All Employees',
      tags: ['culture', 'team learning', 'inclusion'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-25T09:00:00Z',
      factualExplanation: 'WHY THIS? Your interest: culture, team learning',
      hasAudio: false,
      invitation: null,
    },
    {
      id: 'upd_03',
      contentType: 'UPDATE',
      title: 'WCAG 2.1 AA Accessibility Checklist for Web & Mobile Releases',
      summary: 'Keyboard navigation, screen-reader labels, captions, and transcripts are now release gates for all internal apps.',
      bodyText:
        'Every audio clip on Mastek Pulse includes a readable transcript, and playback never starts automatically. Review the updated engineering verification guide.',
      officialSource: 'Platform Engineering Standards',
      externalUrl: 'https://intranet.mastek.demo/updates/wcag-checklist',
      authorId: 'usr_elena',
      authorName: 'Elena Rostova',
      authorFunction: 'Platform IT',
      authorRegion: 'UK',
      audienceId: 'aud_all',
      audienceName: 'All Employees',
      tags: ['accessibility', 'security', 'delivery excellence'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-24T08:00:00Z',
      factualExplanation: 'WHY THIS? Approved company update for your audience',
      hasAudio: false,
      invitation: null,
    },
    {
      id: 'upd_04',
      contentType: 'UPDATE',
      title: 'Cloud Migration Playbook v2.4 Published',
      summary: 'Updated database row-level security templates and zero-downtime PostgreSQL cutover checklists.',
      bodyText:
        'The Cloud Practice has published reusable Terraform and Flyway templates featuring least-privilege application roles and transactional outbox tables.',
      officialSource: 'Global Cloud Practice',
      externalUrl: 'https://intranet.mastek.demo/updates/cloud-playbook',
      authorId: 'usr_rohan',
      authorName: 'Rohan Deshmukh',
      authorFunction: 'Cloud Practice',
      authorRegion: 'INDIA',
      audienceId: 'aud_all',
      audienceName: 'All Employees',
      tags: ['cloud architecture', 'innovation', 'security'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-23T08:00:00Z',
      factualExplanation: 'WHY THIS? Your interest: innovation',
      hasAudio: false,
      invitation: null,
    },
    {
      id: 'upd_05',
      contentType: 'UPDATE',
      title: 'Q4 Healthcare Delivery Highlights & Client Feedback',
      summary: 'How cross-functional pairing reduced incident triage times by 28% across UK & India pods.',
      bodyText:
        'Read the brief on how structured handover notes between UK and India shifts improved release confidence.',
      officialSource: 'UK & India Delivery Office',
      externalUrl: 'https://intranet.mastek.demo/updates/q4-healthcare',
      authorId: 'usr_sophie',
      authorName: 'Sophie Clarke',
      authorFunction: 'Healthcare Delivery',
      authorRegion: 'UK',
      audienceId: 'aud_all',
      audienceName: 'All Employees',
      tags: ['customer stories', 'delivery excellence'],
      status: 'PUBLISHED',
      currentVersion: 1,
      publishedVersion: 1,
      publishedAt: '2026-10-22T08:00:00Z',
      factualExplanation: 'WHY THIS? Approved company update for your audience',
      hasAudio: false,
      invitation: null,
    },
  ];

  const radioQueues: AudioListenItemDto[] = [
    {
      id: 'rad_commute_01',
      contentType: 'RADIO_QUEUE',
      title: 'A little closer to Mastek.',
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
      title: 'Different places. Shared possibilities.',
      summary: 'A conversation about ideas and culture across geographies and functions · 8 min / Hosted by a colleague',
      bodyText:
        'Daniel Wright and Priya Nair discuss how quiet writing before open discussion helps distributed teams across India, the UK, and the US contribute on equal footing.',
      hostId: 'usr_daniel',
      hostName: 'Daniel Wright & Priya Nair',
      durationSeconds: 480,
      resumePositionSeconds: 130, // Matches Wireframe 08 (02:10) & Web Bar (04:12)!
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
    {
      id: 'cc_ep_02',
      contentType: 'CULTURE_CAST',
      title: 'Episode 02: Designing Accessible Healthcare Portals',
      summary: 'Lessons from UK and India delivery pods on building WCAG-first patient experiences · 10 min',
      bodyText:
        'Sophie Clarke hosts a conversation on why accessibility checks belong at wireframe stage rather than pre-release QA.',
      hostId: 'usr_sophie',
      hostName: 'Sophie Clarke',
      durationSeconds: 600,
      resumePositionSeconds: 0,
      transcriptText:
        '[00:00] Sophie: In Episode 2 of Culture Cast, we explore how our healthcare engineering pods bake screen-reader and keyboard navigation into every component.\n[03:10] Engineer: Testing with TalkBack and VoiceOver early saved us three weeks of rework.',
      chaptersJson: JSON.stringify([
        { index: 1, title: '01 Why WCAG matters early', startSeconds: 0 },
        { index: 2, title: '02 Keyboard & screen-reader patterns', startSeconds: 190 },
        { index: 3, title: '03 Sharing checklists across teams', startSeconds: 420 },
      ]),
      relatedStoryId: 'story_03',
      signedAudioUrl: DEMO_WAV_URI,
      publishedVersion: 1,
    },
    {
      id: 'cc_ep_01',
      contentType: 'CULTURE_CAST',
      title: 'Episode 01: First 90 Days Across Continents',
      summary: 'How new Mastek colleagues find mentors beyond their immediate project team · 9 min',
      bodyText:
        'Two engineers in Pune and Reading reflect on the small questions they hesitated to ask in their first month—and how structured 15-minute introductions changed their onboarding.',
      hostId: 'usr_priya',
      hostName: 'Priya Nair',
      durationSeconds: 540,
      resumePositionSeconds: 0,
      transcriptText:
        '[00:00] Priya: Welcome to Culture Cast Episode 1. Today we are talking about the first 90 days at Mastek.\n[02:30] Guest: The biggest unlock for me was realizing people in other regions are happy to share a 15-minute walkthrough if the topic is specific.',
      chaptersJson: JSON.stringify([
        { index: 1, title: '01 Introduction', startSeconds: 0 },
        { index: 2, title: '02 The hesitation to reach out', startSeconds: 150 },
        { index: 3, title: '03 Making asks specific', startSeconds: 340 },
      ]),
      relatedStoryId: 'story_05',
      signedAudioUrl: DEMO_WAV_URI,
      publishedVersion: 1,
    },
  ];

  // Matches Page 11 Wireframe ("Review queue": Culture Cast / Episode 04 In review, Innovation highlights Changes needed, This week on Radio Approved)
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
      id: 'upd_06',
      revisionId: 'rev_upd_06',
      contentType: 'UPDATE',
      title: 'Innovation highlights',
      summary: 'Company update · AI-Assisted Documentation Patterns',
      bodyText:
        'Summary of how teams use contract-first OpenAPI specs and markdown guardrails to align Claude Code, Codex, and GitHub Copilot.',
      authorId: 'usr_carlos',
      authorName: 'Carlos Mendez',
      audienceId: 'aud_all',
      audienceName: 'All employees',
      status: 'DRAFT',
      currentVersion: 1,
      publishedVersion: null,
      hasAudio: false,
      transcriptChecked: true,
      mediaReady: true,
      approvedBy: null,
      approvedByName: null,
      reviewerNotes: 'Changes needed: Please include the link to the official CTO office template repository.',
      scheduledFor: null,
      updatedAt: '2026-10-26T08:40:00Z',
    },
    {
      id: 'rad_commute_01',
      revisionId: 'rev_rad_01',
      contentType: 'RADIO_QUEUE',
      title: 'This week on Radio',
      summary: 'Curated queue · Your Commute Edition (12 min)',
      bodyText:
        'Curated 12-minute commute edition combining Company pulse, Innovation spotlight, and A story across borders.',
      authorId: 'usr_marcus',
      authorName: 'Marcus Vance',
      audienceId: 'aud_all',
      audienceName: 'All employees',
      status: 'APPROVED',
      currentVersion: 1,
      publishedVersion: 1,
      hasAudio: true,
      transcriptChecked: true,
      mediaReady: true,
      approvedBy: 'usr_priya',
      approvedByName: 'Priya Nair',
      reviewerNotes: 'Transcript checked and audio normalized.',
      scheduledFor: null,
      updatedAt: '2026-10-26T07:30:00Z',
    },
    {
      id: 'story_10',
      revisionId: 'rev_story_10',
      contentType: 'STORY',
      title: 'Structured handover notes between UK and India shifts',
      summary: 'Colleague Story · Approved by Priya Nair, ready for Publisher release.',
      bodyText:
        'A simple 4-bullet markdown template at the end of the UK afternoon reduced morning blocker messages for the Pune engineering team.',
      authorId: 'usr_sophie',
      authorName: 'Sophie Clarke',
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
    {
      id: 'story_11',
      revisionId: 'rev_story_11',
      contentType: 'STORY',
      title: 'Prompt engineering guardrails for multi-agent teams',
      summary: 'Colleague Story · Submitted by Carlos Mendez for review.',
      bodyText:
        'How our team uses CLAUDE.md, AGENTS.md, and .github/copilot-instructions.md pointing to a shared docs/aidlc/ specification hub.',
      authorId: 'usr_carlos',
      authorName: 'Carlos Mendez',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      status: 'IN_REVIEW',
      currentVersion: 1,
      publishedVersion: null,
      hasAudio: false,
      transcriptChecked: true,
      mediaReady: true,
      approvedBy: null,
      approvedByName: null,
      reviewerNotes: null,
      scheduledFor: null,
      updatedAt: '2026-10-26T06:45:00Z',
    },
    {
      id: 'story_12',
      revisionId: 'rev_story_12',
      contentType: 'STORY',
      title: 'A better retrospective',
      summary: 'Draft saved by Asha Patel — ready to edit and submit for review (Wireframe 05 - Page 8).',
      bodyText:
        'What changed? After chatting with Daniel, we tried five minutes of silent note-taking at the start of our sprint retro in Pune. What did we learn? Three junior developers shared test automation ideas that saved us two hours every release.',
      authorId: 'usr_asha',
      authorName: 'Asha Patel',
      audienceId: 'aud_pilot',
      audienceName: 'Colleagues in the pilot',
      status: 'DRAFT',
      currentVersion: 1,
      publishedVersion: null,
      hasAudio: false,
      transcriptChecked: true,
      mediaReady: true,
      approvedBy: null,
      approvedByName: null,
      reviewerNotes: null,
      scheduledFor: null,
      updatedAt: '2026-10-26T06:15:00Z',
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
    {
      id: 'aud_ev_02',
      actorId: 'usr_marcus',
      actionType: 'CONTENT_PUBLISHED',
      targetType: 'CONTENT_ITEM',
      targetId: 'story_01',
      details: 'Publisher Marcus Vance released Revision 1 of story_01 to Colleagues in the pilot.',
      createdAt: '2026-10-26T09:00:00Z',
    },
    {
      id: 'aud_ev_03',
      actorId: 'usr_priya',
      actionType: 'REVISION_APPROVED',
      targetType: 'CONTENT_REVISION',
      targetId: 'rev_story_10',
      details: 'Reviewer Priya Nair approved Revision 1 of story_10 (Author: Sophie Clarke). Ready for Publisher release.',
      createdAt: '2026-10-26T09:10:00Z',
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
  };
}

function loadStore(): LocalStoreState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      return JSON.parse(raw) as LocalStoreState;
    }
  } catch {
    // ignore storage errors
  }
  const initial = getInitialSeedState();
  saveStore(initial);
  return initial;
}

function saveStore(state: LocalStoreState) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // ignore storage errors
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

export const pulseApi = {
  async getPersonas(activeUserId: string): Promise<UserProfileDto[]> {
    try {
      const res = await fetch('/api/v1/personas', { headers: { 'X-Pulse-User-Id': activeUserId } });
      if (res.ok) return await res.json();
    } catch {
      // Fallback to in-browser contract engine
    }
    return loadStore().users;
  },

  async getCurrentUser(activeUserId: string): Promise<UserProfileDto> {
    try {
      const res = await fetch('/api/v1/me', { headers: { 'X-Pulse-User-Id': activeUserId } });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    const store = loadStore();
    return store.users.find((u) => u.id === activeUserId) || store.users[0];
  },

  async updatePreferences(
    activeUserId: string,
    prefs: { interests?: string[]; notificationFrequency?: string; hostPaused?: boolean }
  ): Promise<UserProfileDto> {
    try {
      const res = await fetch('/api/v1/me/preferences', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify(prefs),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
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
    try {
      const params = new URLSearchParams();
      if (category) params.set('category', category);
      if (query) params.set('q', query);
      const res = await fetch(`/api/v1/feed?${params.toString()}`, {
        headers: { 'X-Pulse-User-Id': activeUserId },
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];

    // STEP 1: Filter by caller's permitted audience_id BEFORE ranking!
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

    // STEP 2 & 3: Rank by explicit interests + open host capacity and attach factual explanation
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

  async getRadioQueues(activeUserId: string): Promise<AudioListenItemDto[]> {
    try {
      const res = await fetch('/api/v1/listen/radio', { headers: { 'X-Pulse-User-Id': activeUserId } });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return loadStore().radioQueues;
  },

  async getCultureCasts(activeUserId: string): Promise<AudioListenItemDto[]> {
    try {
      const res = await fetch('/api/v1/listen/culture-cast', { headers: { 'X-Pulse-User-Id': activeUserId } });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return loadStore().cultureCasts;
  },

  async savePlaybackProgress(
    activeUserId: string,
    contentId: string,
    positionSeconds: number,
    durationSeconds: number
  ): Promise<void> {
    try {
      const res = await fetch('/api/v1/listen/progress', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify({ contentId, positionSeconds, durationSeconds }),
      });
      if (res.ok) return;
    } catch {
      // Fallback
    }
    const store = loadStore();
    const r = store.radioQueues.find((x) => x.id === contentId);
    if (r) r.resumePositionSeconds = positionSeconds;
    const c = store.cultureCasts.find((x) => x.id === contentId);
    if (c) c.resumePositionSeconds = positionSeconds;
    saveStore(store);
  },

  async listRequests(activeUserId: string): Promise<ConnectionRequestDto[]> {
    try {
      const res = await fetch('/api/v1/requests', { headers: { 'X-Pulse-User-Id': activeUserId } });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
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
    try {
      const res = await fetch('/api/v1/requests', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Pulse-User-Id': activeUserId,
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify(payload),
      });
      if (res.ok) return await res.json();
      if (res.status >= 400 && res.status < 500) {
        const err = await res.json();
        throw new Error(err.message || 'Request failed');
      }
    } catch (e) {
      if (e instanceof Error && e.message.startsWith('4')) throw e;
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
      throw new Error('404: Invitation not found.');
    }
    const inv = story.invitation;
    if (inv.hostId === caller.id) {
      throw new Error('422: Hosts cannot send an invitation request to themselves.');
    }
    if (inv.acceptedCount >= inv.capacity || inv.statusLabel !== 'OPEN') {
      throw new Error('409: Invitation is already at full capacity or paused.');
    }
    const duplicate = store.requests.find(
      (r) => r.invitationId === inv.id && r.requesterId === caller.id && r.state !== 'CANCELLED'
    );
    if (duplicate) {
      throw new Error('409: You already have an active request for this invitation.');
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
    try {
      const res = await fetch(`/api/v1/requests/${requestId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify({ targetState, expectedVersion }),
      });
      if (res.ok) return await res.json();
      if (res.status >= 400 && res.status < 500) {
        const err = await res.json();
        throw new Error(err.message || 'State transition failed');
      }
    } catch (e) {
      if (e instanceof Error && e.message.startsWith('4')) throw e;
    }

    // In-Browser 1-Transaction Pessimistic Lock Mirror (Proposal Page 15)
    const store = loadStore();
    const req = store.requests.find((r) => r.id === requestId);
    if (!req) throw new Error('404: Request not found.');
    if (req.version !== expectedVersion || req.state !== 'PENDING') {
      throw new Error(`409: Stale version or request is no longer PENDING (current state: ${req.state}).`);
    }

    const story = store.storiesAndUpdates.find((s) => s.id === req.storyId);
    const inv = story?.invitation;

    if (targetState === 'ACCEPTED') {
      if (req.hostId !== activeUserId) {
        throw new Error('403: Only the invitation host can accept this request.');
      }
      if (!inv || inv.acceptedCount >= inv.capacity) {
        throw new Error('409: Full capacity reached — no remaining places this week.');
      }
      inv.acceptedCount += 1;
      inv.remainingPlaces = Math.max(0, inv.capacity - inv.acceptedCount);
      inv.statusLabel = inv.remainingPlaces === 0 ? 'FULL' : 'OPEN';

      req.state = 'ACCEPTED';
      req.version += 1;
      req.acceptedCount = inv.acceptedCount;
      req.remainingPlaces = inv.remainingPlaces;

      store.auditLog.unshift({
        id: 'aud_' + Math.random().toString(36).slice(2, 8),
        actorId: activeUserId,
        actionType: 'REQUEST_ACCEPTED',
        targetType: 'CONNECTION_REQUEST',
        targetId: req.id,
        details: `Host ${req.hostName} accepted request from ${req.requesterName} (${inv.acceptedCount}/${inv.capacity} places used). Outbox event emitted.`,
        createdAt: new Date().toISOString(),
      });
    } else if (targetState === 'DECLINED') {
      if (req.hostId !== activeUserId) {
        throw new Error('403: Only the invitation host can decline this request.');
      }
      req.state = 'DECLINED';
      req.version += 1;
    } else if (targetState === 'CANCELLED') {
      if (req.requesterId !== activeUserId) {
        throw new Error('403: Only the requester can cancel their pending request.');
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
    try {
      const res = await fetch(`/api/v1/requests/${requestId}/confirmations`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify({ happened, useful }),
      });
      if (res.ok) return await res.json();
      if (res.status >= 400 && res.status < 500) {
        const err = await res.json();
        throw new Error(err.message || 'Feedback submission failed');
      }
    } catch (e) {
      if (e instanceof Error && e.message.startsWith('4')) throw e;
    }

    const store = loadStore();
    const req = store.requests.find((r) => r.id === requestId);
    if (!req) throw new Error('404: Request not found.');
    if (req.requesterId !== activeUserId && req.hostId !== activeUserId) {
      throw new Error('403: Only the two participants can submit private feedback.');
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
    try {
      const res = await fetch('/api/v1/ai/assist', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify({ assistType, contextId, userDraftText }),
      });
      if (res.ok) return await res.json();
      if (res.status >= 400 && res.status < 500) {
        const err = await res.json();
        throw new Error(err.message || 'AI Assist refused');
      }
    } catch (e) {
      if (e instanceof Error && e.message.startsWith('4')) throw e;
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
      throw new Error(
        '422: Refused by Mastek Pulse AI Trust Policy — cannot infer sensitive attributes, private messages, or employee appraisal scores.'
      );
    }

    if (story && !caller.audiences.includes(story.audienceId)) {
      throw new Error('403: Access denied to restricted audience content.');
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
    try {
      const res = await fetch('/api/v1/content', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify(payload),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
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
    store.auditLog.unshift({
      id: 'aud_' + Math.random().toString(36).slice(2, 8),
      actorId: caller.id,
      actionType: payload.submitForReview ? 'CONTENT_SUBMITTED' : 'DRAFT_SAVED',
      targetType: 'CONTENT_REVISION',
      targetId: revId,
      details: `${caller.displayName} ${
        payload.submitForReview ? 'submitted Revision 1 for review' : 'saved Draft Revision 1'
      } ("${newItem.title}") for audience ${newItem.audienceName}.`,
      createdAt: new Date().toISOString(),
    });
    saveStore(store);
    return newItem;
  },

  async getPortalQueue(activeUserId: string): Promise<PortalQueueItemDto[]> {
    try {
      const res = await fetch('/api/v1/portal/queue', { headers: { 'X-Pulse-User-Id': activeUserId } });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return loadStore().portalItems;
  },

  async reviewRevision(
    activeUserId: string,
    contentId: string,
    revisionNumber: number,
    decision: 'APPROVE' | 'REQUEST_CHANGES',
    notes: string
  ): Promise<PortalQueueItemDto> {
    try {
      const res = await fetch(`/api/v1/content/${contentId}/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify({ revisionNumber, decision, notes }),
      });
      if (res.ok) return await res.json();
      if (res.status >= 400 && res.status < 500) {
        const err = await res.json();
        throw new Error(err.message || 'Review failed');
      }
    } catch (e) {
      if (e instanceof Error && e.message.startsWith('4')) throw e;
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    if (!caller.roles.includes('REVIEWER')) {
      throw new Error(`403: User '${caller.displayName}' lacks required scoped role: REVIEWER (Admin has no automatic editorial approval right).`);
    }

    const item = store.portalItems.find((i) => i.id === contentId);
    if (!item) throw new Error('404: Content item not found.');

    if (item.authorId === caller.id) {
      throw new Error('403: Self-Approval Denied — Reviewers cannot approve their own submissions (author == reviewer).');
    }
    if (item.currentVersion !== revisionNumber) {
      throw new Error(`409: Revision ${revisionNumber} is stale; current version is ${item.currentVersion}.`);
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

    store.auditLog.unshift({
      id: 'aud_' + Math.random().toString(36).slice(2, 8),
      actorId: caller.id,
      actionType: decision === 'APPROVE' ? 'REVISION_APPROVED' : 'CHANGES_REQUESTED',
      targetType: 'CONTENT_REVISION',
      targetId: item.revisionId,
      details: `Reviewer ${caller.displayName} ${
        decision === 'APPROVE' ? 'approved' : 'requested changes on'
      } Revision ${revisionNumber} of "${item.title}".`,
      createdAt: new Date().toISOString(),
    });

    saveStore(store);
    return item;
  },

  async publishOrSchedule(
    activeUserId: string,
    contentId: string,
    mode: 'IMMEDIATE' | 'SCHEDULED',
    scheduledFor?: string
  ): Promise<PortalQueueItemDto> {
    try {
      const res = await fetch(`/api/v1/content/${contentId}/publish`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify({ mode, scheduledFor }),
      });
      if (res.ok) return await res.json();
      if (res.status >= 400 && res.status < 500) {
        const err = await res.json();
        throw new Error(err.message || 'Publish failed');
      }
    } catch (e) {
      if (e instanceof Error && e.message.startsWith('4')) throw e;
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    if (!caller.roles.includes('PUBLISHER')) {
      throw new Error(`403: User '${caller.displayName}' lacks required scoped role: PUBLISHER.`);
    }

    const item = store.portalItems.find((i) => i.id === contentId);
    if (!item) throw new Error('404: Content item not found.');
    if (item.status !== 'APPROVED' || !item.approvedBy) {
      throw new Error(`409: Revision ${item.currentVersion} cannot be published because it is not APPROVED.`);
    }

    if (mode === 'SCHEDULED') {
      item.status = 'SCHEDULED';
      item.scheduledFor = scheduledFor || new Date(Date.now() + 86400000).toISOString();
    } else {
      item.status = 'PUBLISHED';
      item.publishedVersion = item.currentVersion;
      item.scheduledFor = null;

      // Also push to live Employee Feed if it's a STORY or UPDATE
      const existingFeedIdx = store.storiesAndUpdates.findIndex((s) => s.id === item.id);
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
        invitation: {
          id: 'inv_' + item.id,
          hostId: item.authorId,
          hostName: item.authorName,
          hostRegion: author?.region || 'INDIA',
          hostFunction: author?.functionName || 'Colleague',
          topicLabel: 'Ask me about this: ' + item.title,
          durationMinutes: 15,
          capacity: 2,
          acceptedCount: 0,
          remainingPlaces: 2,
          paused: false,
          expiresAt: '2026-11-20T23:59:59Z',
          statusLabel: 'OPEN',
        },
      };
      if (existingFeedIdx >= 0) {
        store.storiesAndUpdates[existingFeedIdx] = liveCard;
      } else if (item.contentType === 'STORY' || item.contentType === 'UPDATE') {
        store.storiesAndUpdates.unshift(liveCard);
      }
    }

    item.updatedAt = new Date().toISOString();
    store.auditLog.unshift({
      id: 'aud_' + Math.random().toString(36).slice(2, 8),
      actorId: caller.id,
      actionType: mode === 'SCHEDULED' ? 'CONTENT_SCHEDULED' : 'CONTENT_PUBLISHED',
      targetType: 'CONTENT_ITEM',
      targetId: item.id,
      details: `Publisher ${caller.displayName} ${
        mode === 'SCHEDULED' ? 'scheduled' : 'published'
      } Revision ${item.currentVersion} of "${item.title}" to ${item.audienceName}.`,
      createdAt: new Date().toISOString(),
    });

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
    try {
      const res = await fetch(`/api/v1/content/${contentId}/revisions`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify({ title: newTitle, bodyText: newBody, audienceId: newAudienceId }),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    const item = store.portalItems.find((i) => i.id === contentId);
    if (!item) throw new Error('404: Content item not found.');

    const prevStatus = item.status;
    item.currentVersion += 1;
    item.revisionId = `rev_${contentId}_v${item.currentVersion}`;
    item.title = newTitle.trim();
    item.bodyText = newBody.trim();
    item.summary = newBody.trim().slice(0, 120) + (newBody.length > 120 ? '...' : '');
    item.audienceId = newAudienceId;
    item.audienceName = newAudienceId === 'aud_all' ? 'All employees' : 'Colleagues in the pilot';
    // Critical Invariant (Proposal Page 11): "Any content or audience edit invalidates approval and returns the new revision to review."
    item.approvedBy = null;
    item.approvedByName = null;
    item.status = 'IN_REVIEW';
    item.updatedAt = new Date().toISOString();

    store.auditLog.unshift({
      id: 'aud_' + Math.random().toString(36).slice(2, 8),
      actorId: caller.id,
      actionType: 'EDIT_INVALIDATED_APPROVAL',
      targetType: 'CONTENT_REVISION',
      targetId: item.revisionId,
      details: `${caller.displayName} edited content/audience on "${item.title}" -> created Revision ${item.currentVersion}, invalidated prior approval (${prevStatus}), and returned to IN_REVIEW.`,
      createdAt: new Date().toISOString(),
    });

    saveStore(store);
    return item;
  },

  async withdrawContent(activeUserId: string, contentId: string, reason: string): Promise<PortalQueueItemDto> {
    try {
      const res = await fetch(`/api/v1/content/${contentId}/withdraw`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Pulse-User-Id': activeUserId },
        body: JSON.stringify({ reason }),
      });
      if (res.ok) return await res.json();
      if (res.status >= 400 && res.status < 500) {
        const err = await res.json();
        throw new Error(err.message || 'Withdraw failed');
      }
    } catch (e) {
      if (e instanceof Error && e.message.startsWith('4')) throw e;
    }

    const store = loadStore();
    const caller = store.users.find((u) => u.id === activeUserId) || store.users[0];
    if (!caller.roles.includes('PUBLISHER') && !caller.roles.includes('MODERATOR')) {
      throw new Error(`403: Requires PUBLISHER or MODERATOR role to withdraw content.`);
    }

    const item = store.portalItems.find((i) => i.id === contentId);
    if (!item) throw new Error('404: Item not found.');

    item.status = 'WITHDRAWN';
    item.publishedVersion = null;
    item.updatedAt = new Date().toISOString();

    const feedItem = store.storiesAndUpdates.find((s) => s.id === contentId);
    if (feedItem) feedItem.status = 'WITHDRAWN';

    store.auditLog.unshift({
      id: 'aud_' + Math.random().toString(36).slice(2, 8),
      actorId: caller.id,
      actionType: 'CONTENT_WITHDRAWN',
      targetType: 'CONTENT_ITEM',
      targetId: item.id,
      details: `${caller.displayName} withdrew "${item.title}". Reason: ${reason}`,
      createdAt: new Date().toISOString(),
    });

    saveStore(store);
    return item;
  },

  async getAuditLog(activeUserId: string): Promise<AuditEventDto[]> {
    try {
      const res = await fetch('/api/v1/portal/audit-log', { headers: { 'X-Pulse-User-Id': activeUserId } });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return loadStore().auditLog;
  },

  async getPilotMetricsAndBudget(activeUserId: string): Promise<{ metrics: PilotMetricsDto; budget: AiBudgetStatusDto }> {
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

  async simulateAiBudgetLevel(level: 'NORMAL' | 'ALERT_50' | 'ALERT_80' | 'CAPPED_100'): Promise<void> {
    const store = loadStore();
    if (level === 'NORMAL') store.aiActionsUsed = 18;
    else if (level === 'ALERT_50') store.aiActionsUsed = 365;
    else if (level === 'ALERT_80') store.aiActionsUsed = 585;
    else if (level === 'CAPPED_100') store.aiActionsUsed = 720;
    saveStore(store);
  },
};
