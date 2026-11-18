/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/api/contracts.ts
 * Journey: Shared TypeScript Data Contracts across Employee Web, Publishing
 *          Portal, Campfire Recognition Engine, and React Native Mobile
 *          (`docs/aidlc/openapi.yaml` & `docs/aidlc/02-domain-model-and-contracts.md`).
 *
 * Why it is designed this way:
 *   1. SINGLE SOURCE OF TRUTH ACROSS STACKS:
 *      - Every interface in this file mirrors a Java 21 record/DTO in the
 *        Spring Boot backend (`backend/src/main/java/com/mastek/pulse/api/dto/`)
 *        and an OpenAPI 3.1 schema in `docs/aidlc/openapi.yaml`.
 *   2. STRICT DOMAIN GUARDRAIL ENFORCEMENT AT COMPILE TIME:
 *      - Explicit union types (`ScopedRole`, `ContentStatus`, `RequestState`,
 *        `HappenedAnswer`, `BilateralOutcome`, `AppMode`) prevent magic strings
 *        and make invalid state transitions impossible to express in UI code.
 *   3. EXPLICIT LIVE ERROR CONTRACT (`PulseHttpError` & `ProblemDetailDto`):
 *      - When running in `live` mode (`VITE_APP_MODE=live`), HTTP 401/403/404/
 *        409/422/500 responses from Spring Boot are surfaced as typed
 *        `PulseHttpError` instances with machine-readable `code` and `status`,
 *        preventing silent fallback to local demo state.
 * ============================================================================
 */

/**
 * Geographic operating regions represented in the Mastek Campfire 4-week pilot.
 * Used for cross-border story discovery and regional filtering.
 */
export type RegionCode = 'INDIA' | 'UK' | 'US';

/**
 * Role-based access control (RBAC) identifiers enforced by both the backend
 * `PulsePrincipal` security filter and the frontend workspace gates:
 * - `EMPLOYEE`: Standard colleague who can browse permitted feeds, listen to audio,
 *   send 15-minute invitations, appreciate stories, and complete private check-ins.
 * - `CREATOR`: Can draft and submit stories or weekly challenge entries for review.
 * - `REVIEWER`: Can approve or request changes on `IN_REVIEW` revisions in the
 *   Publishing Portal, subject to the `approved_by != author_id` (no self-approval) rule.
 * - `PUBLISHER`: Can publish or schedule `APPROVED` revisions and withdraw live items.
 * - `MODERATOR`: Can withdraw published content for trust/safety moderation.
 * - `ADMIN`: Manages system configuration and audiences; intentionally holds NO
 *   automatic editorial approval right (`REVIEWER` is still required to approve).
 */
export type ScopedRole = 'EMPLOYEE' | 'CREATOR' | 'REVIEWER' | 'PUBLISHER' | 'MODERATOR' | 'ADMIN';

/**
 * Supported content formats across the Employee Feed, Listen tab, and Publishing Portal:
 * - `STORY`: Colleague-authored story or Weekly Challenge entry.
 * - `UPDATE`: Bite-sized official company update with source attribution.
 * - `CULTURE_CAST`: On-demand colleague podcast episode with chapters and transcript.
 * - `RADIO_QUEUE`: Curated 12-minute commute audio edition combining multiple highlights.
 */
export type ContentType = 'STORY' | 'UPDATE' | 'CULTURE_CAST' | 'RADIO_QUEUE';

/**
 * Controlled editorial lifecycle states (Proposal Page 11):
 * `DRAFT` -> `IN_REVIEW` -> `APPROVED` -> `SCHEDULED` / `PUBLISHED` -> `WITHDRAWN`.
 * Editing content or audience after `APPROVED` creates `Revision N+1` and resets
 * the status back to `IN_REVIEW`.
 */
export type ContentStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'SCHEDULED' | 'PUBLISHED' | 'WITHDRAWN';

/**
 * Computed availability badge for a host's voluntary 15-minute conversation offer:
 * - `OPEN`: Places remain (`acceptedCount < capacity`) and host has not paused availability.
 * - `FULL`: All weekly places are taken (`acceptedCount >= capacity`).
 * - `PAUSED`: Host temporarily paused incoming requests from their Profile settings.
 * - `EXPIRED`: The invitation offer window (`expiresAt`) has passed.
 */
export type InvitationStatusLabel = 'OPEN' | 'FULL' | 'PAUSED' | 'EXPIRED';

/**
 * State machine for a 15-minute colleague connection request:
 * - `PENDING`: Sent by requester; awaiting host decision.
 * - `ACCEPTED`: Host accepted (locked via `SELECT ... FOR UPDATE` so capacity is never exceeded).
 * - `DECLINED`: Host politely declined.
 * - `CANCELLED`: Requester cancelled their own pending request.
 * - `EXPIRED`: Invitation expired before a decision was made.
 */
export type RequestState = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED';

/**
 * Participant's answer to "Did the conversation happen?" in the Private Follow-Up Check-in.
 */
export type HappenedAnswer = 'YES' | 'NO' | 'NOT_YET';

/**
 * Computed bilateral verification outcome for an accepted connection request:
 * - `USEFUL_CONFIRMED`: Counted as a verified Useful Connection ONLY when BOTH
 *   requester and host independently submit `happened === 'YES'` and `useful === true`.
 * - `NOT_USEFUL`: Both confirmed the chat happened, but at least one marked `useful === false`.
 * - `NOT_HAPPENED`: At least one participant reported `happened === 'NO'`.
 * - `UNKNOWN`: Missing, skipped, or `NOT_YET` feedback from either participant.
 */
export type BilateralOutcome = 'USEFUL_CONFIRMED' | 'NOT_USEFUL' | 'NOT_HAPPENED' | 'UNKNOWN';

/**
 * Represents an authenticated Mastek employee persona (`/api/v1/me` and `/api/v1/personas`).
 */
export interface UserProfileDto {
  /** Canonical server-side user identifier (e.g., `'usr_asha'`, `'usr_daniel'`). */
  id: string;
  /** Full human-readable name displayed in story bylines and headers. */
  displayName: string;
  /** Corporate email address for the employee persona. */
  email: string;
  /** Geographic region code (`'INDIA' | 'UK' | 'US'`). */
  region: RegionCode;
  /** Organizational practice or department name (e.g., `'Engineering'`, `'Delivery'`). */
  functionName: string;
  /** Array of scoped RBAC roles granted to this user. */
  roles: ScopedRole[];
  /** Explicit opt-in topic tags used for transparent rules-based feed ranking. */
  interests: string[];
  /** Audience group IDs this user belongs to (enforced *before* ranking). */
  audiences: string[];
  /** Preferred digest cadence (`'IMMEDIATE' | 'DAILY_DIGEST' | 'WEEKLY_SUMMARY'`). */
  notificationFrequency: string;
  /** Whether the user has paused incoming 15-minute host invitations for the week. */
  hostPaused: boolean;
}

/**
 * Summary of a voluntary 15-minute "Ask me about this" conversation offer attached to a story.
 */
export interface InvitationSummaryDto {
  /** Unique identifier of the invitation offer (e.g., `'inv_story_01'`). */
  id: string;
  /** User ID of the story author / host offering the 15-minute chat. */
  hostId: string;
  /** Display name of the host colleague. */
  hostName: string;
  /** Geographic region of the host colleague. */
  hostRegion: string;
  /** Department / function of the host colleague. */
  hostFunction: string;
  /** Specific conversation prompt (e.g., `'Ask me about this: inclusive team retrospectives'`). */
  topicLabel: string;
  /** Fixed conversation length in minutes (standard pilot duration is 15 minutes). */
  durationMinutes: number;
  /** Maximum number of requests the host is willing to accept this week (1–3). */
  capacity: number;
  /** Number of requests already accepted by the host under pessimistic lock. */
  acceptedCount: number;
  /** Remaining open slots (`Math.max(0, capacity - acceptedCount)`). */
  remainingPlaces: number;
  /** True if either the invitation itself or the host's profile availability is paused. */
  paused: boolean;
  /** ISO-8601 UTC timestamp when this invitation offer expires. */
  expiresAt: string;
  /** Computed status badge (`'OPEN' | 'FULL' | 'PAUSED' | 'EXPIRED'`). */
  statusLabel: InvitationStatusLabel;
}

/**
 * A published Story or Company Update card rendered in the Employee Feed (`/api/v1/feed`).
 */
export interface ContentCardDto {
  /** Unique content identifier (e.g., `'story_01'`, `'upd_01'`). */
  id: string;
  /** Content classification (`'STORY'` or `'UPDATE'`, etc.). */
  contentType: ContentType;
  /** Headline title shown on the feed card and detail view. */
  title: string;
  /** Short 1–2 sentence preview summary shown on the feed card. */
  summary: string;
  /** Full multi-paragraph body text shown in the Story Detail view. */
  bodyText: string;
  /** Optional source attribution label for official company updates. */
  officialSource?: string | null;
  /** Optional external intranet link for official company updates. */
  externalUrl?: string | null;
  /** User ID of the colleague who authored the content. */
  authorId: string;
  /** Display name of the author. */
  authorName: string;
  /** Department/function of the author (used for cross-team appreciation tracking). */
  authorFunction: string;
  /** Geographic region of the author. */
  authorRegion: string;
  /** Target audience group ID (`'aud_all'`, `'aud_pilot'`, `'aud_uk_restricted'`). */
  audienceId: string;
  /** Human-readable name of the target audience group. */
  audienceName: string;
  /** Topic tags attached to the content for rules-based interest matching. */
  tags: string[];
  /** Current lifecycle status (only `'PUBLISHED'` items appear in `/api/v1/feed`). */
  status: ContentStatus;
  /** Latest revision number created for this content item. */
  currentVersion: number;
  /** Revision number currently live in employee feeds, if published. */
  publishedVersion?: number | null;
  /** ISO-8601 UTC timestamp when the active revision was published. */
  publishedAt?: string | null;
  /** Grounded, factual explanation badge (`WHY THIS? Your interest: ...`) — never an opaque score. */
  factualExplanation: string;
  /** True if prerecorded audio and a verified transcript are attached to this item. */
  hasAudio: boolean;
  /** Audio duration in seconds when `hasAudio` is true. */
  durationSeconds?: number | null;
  /** Human-verified text transcript for WCAG 2.2 AA accessibility when `hasAudio` is true. */
  transcriptText?: string | null;
  /** Short-lived signed URL (or playable data URI in demo mode) for the audio asset. */
  signedAudioUrl?: string | null;
  /** Optional voluntary 15-minute conversation offer attached by the author. */
  invitation?: InvitationSummaryDto | null;
}

/**
 * Single chapter marker inside a `RADIO_QUEUE` or `CULTURE_CAST` audio item.
 */
export interface AudioChapter {
  /** 1-based sequential chapter number (e.g., `1`, `2`, `3`). */
  index: number;
  /** Chapter heading displayed in the interactive chapter list. */
  title: string;
  /** Offset in seconds from the start of the audio track where this chapter begins. */
  startSeconds: number;
  /** Optional chapter length in minutes displayed in the Mastek Radio queue. */
  durationMinutes?: number;
}

/**
 * Audio item returned by `/api/v1/listen/radio` or `/api/v1/listen/culture-cast`.
 */
export interface AudioListenItemDto {
  /** Unique audio content identifier (e.g., `'rad_commute_01'`, `'cc_ep_03'`). */
  id: string;
  /** Discriminator between curated commute radio queues and on-demand podcast episodes. */
  contentType: 'RADIO_QUEUE' | 'CULTURE_CAST';
  /** Episode or commute queue title. */
  title: string;
  /** Subtitle / summary describing the hosts and runtime. */
  summary: string;
  /** Extended show notes for the episode. */
  bodyText: string;
  /** User ID of the primary host or editorial owner. */
  hostId: string;
  /** Display name(s) of the episode host(s). */
  hostName: string;
  /** Total track duration in seconds. */
  durationSeconds: number;
  /** User's last saved playback position in seconds for cross-device resume. */
  resumePositionSeconds: number;
  /** Full human-verified transcript text displayed alongside the audio player. */
  transcriptText: string;
  /** Serialized JSON array of `AudioChapter` objects (`JSON.stringify`). */
  chaptersJson: string;
  /** Optional ID of a related feed story so listeners can continue the conversation. */
  relatedStoryId?: string | null;
  /** Short-lived signed audio URL (or playable WAV data URI in demo mode). */
  signedAudioUrl: string;
  /** Published revision number of this audio episode. */
  publishedVersion: number;
}

/**
 * Individual participant's private check-in submission for an accepted connection.
 */
export interface ParticipantConfirmationDto {
  /** Whether the 15-minute conversation has taken place (`'YES' | 'NO' | 'NOT_YET'`). */
  happened: HappenedAnswer;
  /** Whether the participant found the conversation useful (`true | false | null`). */
  useful?: boolean | null;
}

/**
 * A 15-minute connection request between a requester and a story host (`/api/v1/requests`).
 */
export interface ConnectionRequestDto {
  /** Unique connection request ID. */
  id: string;
  /** ID of the target `InvitationSummaryDto` offer. */
  invitationId: string;
  /** ID of the story that prompted this connection request. */
  storyId: string;
  /** Title of the story that prompted this connection request. */
  storyTitle: string;
  /** Specific topic prompt from the host's invitation offer. */
  topicLabel: string;
  /** User ID of the colleague requesting the 15-minute chat. */
  requesterId: string;
  /** Display name of the requesting colleague. */
  requesterName: string;
  /** Geographic region of the requesting colleague. */
  requesterRegion: string;
  /** Department/function of the requesting colleague. */
  requesterFunction: string;
  /** User ID of the story host receiving the request. */
  hostId: string;
  /** Display name of the story host. */
  hostName: string;
  /** Geographic region of the story host. */
  hostRegion: string;
  /** Department/function of the story host. */
  hostFunction: string;
  /** Personal introduction message reviewed and sent by the requester. */
  messageText: string;
  /** True if the requester used optional AI assistance when drafting `messageText`. */
  aiAssisted: boolean;
  /** Current state of the request (`'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED'`). */
  state: RequestState;
  /** Optimistic concurrency version number incremented on state transitions. */
  version: number;
  /** Total weekly capacity of the underlying invitation offer. */
  capacity: number;
  /** Current number of accepted requests on the underlying invitation offer. */
  acceptedCount: number;
  /** Remaining open slots on the underlying invitation offer. */
  remainingPlaces: number;
  /** ISO-8601 UTC timestamp when the request was created. */
  createdAt: string;
  /** The active caller's own private check-in response, if submitted. */
  myConfirmation?: ParticipantConfirmationDto | null;
  /** Number of participants (0, 1, or 2) who have submitted check-in feedback so far. */
  confirmationsCount: number;
  /** Computed bilateral outcome (`'USEFUL_CONFIRMED'` only when both confirm Yes + Yes). */
  bilateralOutcome: BilateralOutcome;
}

/**
 * Editorial item surfaced in the Role-Based Publishing Portal (`/api/v1/portal/queue`).
 */
export interface PortalQueueItemDto {
  /** Content item ID shared across all revisions of this story/episode. */
  id: string;
  /** Unique identifier of the specific revision currently in the queue. */
  revisionId: string;
  /** Content classification (`'STORY' | 'UPDATE' | 'CULTURE_CAST' | 'RADIO_QUEUE'`). */
  contentType: ContentType;
  /** Headline title of the current revision. */
  title: string;
  /** Short summary preview of the current revision. */
  summary: string;
  /** Full body text of the current revision inspected by the Reviewer/Publisher. */
  bodyText: string;
  /** User ID of the creator who submitted the revision (enforces `approvedBy != authorId`). */
  authorId: string;
  /** Display name of the creator. */
  authorName: string;
  /** Target audience ID that will see the content once published. */
  audienceId: string;
  /** Human-readable name of the target audience. */
  audienceName: string;
  /** Editorial workflow status (`'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'SCHEDULED' | 'PUBLISHED' | 'WITHDRAWN'`). */
  status: ContentStatus;
  /** Monotonically increasing revision number (`1`, `2`, `3`...). */
  currentVersion: number;
  /** Version number currently live in the employee feed, or `null` if unpublished. */
  publishedVersion?: number | null;
  /** True if an audio media asset is attached to this revision. */
  hasAudio: boolean;
  /** True if the human transcript check gate has been satisfied. */
  transcriptChecked: boolean;
  /** True if the object storage malware/media scan gate has passed (`READY`). */
  mediaReady: boolean;
  /** User ID of the `REVIEWER` who approved `currentVersion`, or `null` if unapproved/invalidated. */
  approvedBy?: string | null;
  /** Display name of the approving `REVIEWER`, or `null`. */
  approvedByName?: string | null;
  /** Reviewer feedback notes recorded during approval or change request. */
  reviewerNotes?: string | null;
  /** ISO-8601 UTC timestamp for scheduled release when `status === 'SCHEDULED'`. */
  scheduledFor?: string | null;
  /** ISO-8601 UTC timestamp when this revision was last modified. */
  updatedAt: string;
}

/**
 * Tracks the 4-week pilot AI workload & hard budget cap (Proposal Page 5: 720 actions cap).
 */
export interface AiBudgetStatusDto {
  /** Total optional AI drafting/polishing/summarization actions consumed so far. */
  actionsUsed: number;
  /** Hard pilot cap on total AI actions (720 actions = 60 people × 4 weeks × 3 actions). */
  actionCap: number;
  /** Percentage of the AI action budget consumed (`0` to `100`). */
  percentUsed: number;
  /** Estimated cumulative prompt/input tokens consumed (~1,000 tokens per action). */
  estimatedInputTokens: number;
  /** Estimated cumulative completion/output tokens consumed (~500 tokens per action). */
  estimatedOutputTokens: number;
  /** Threshold alert state (`'NORMAL'` < 50%, `'ALERT_50'`, `'ALERT_80'`, `'CAPPED_100'`). */
  alertLevel: 'NORMAL' | 'ALERT_50' | 'ALERT_80' | 'CAPPED_100';
  /** True when `actionsUsed >= actionCap`; automatically disables AI and uses deterministic templates. */
  hardCapReached: boolean;
}

/**
 * Response returned by `POST /api/v1/ai/assist` pairing optional AI output with a mandatory
 * deterministic non-AI template fallback.
 */
export interface AiAssistanceResponseDto {
  /** Which AI assistance workflow was requested. */
  assistType: 'INVITATION_DRAFT' | 'STORY_POLISH' | 'UPDATE_SUMMARY';
  /** False when the hard budget cap (`CAPPED_100`) is reached or AI provider is disabled. */
  aiAvailable: boolean;
  /** Grounded AI text suggestion when `aiAvailable` is true, or `null` when capped. */
  aiSuggestion: string | null;
  /** Deterministic, non-AI template text that is ALWAYS populated as a reliable fallback. */
  templateFallback: string;
  /** Link to the underlying source story or official update for grounding verification. */
  sourceLink: string;
  /** Explicit disclosure banner explaining whether AI or the non-AI fallback was used. */
  aiLabel: string;
  /** Updated pilot AI budget telemetry after processing this request. */
  budgetStatus: AiBudgetStatusDto;
}

/**
 * Aggregate 4-week pilot threshold gate metrics displayed in the Publishing Portal Dashboard
 * (Proposal Page 12 — never tracks individual employee appraisal scores).
 */
export interface PilotMetricsDto {
  /** Number of connection pairs where BOTH participants confirmed `Happened=Yes` & `Useful=Yes`. */
  usefulConnectionsCount: number;
  /** Number of accepted connections where check-in feedback is still missing or `NOT_YET`. */
  unknownFeedbackCount: number;
  /** Percentage of pilot participants in a verified useful connection. */
  usefulConnectionsPct: number;
  /** Pilot threshold gate target for useful connections (`20%`). */
  usefulConnectionsTargetPct: number;
  /** Percentage of decided connection requests that were `ACCEPTED` by the host. */
  acceptanceRatePct: number;
  /** Pilot threshold gate target for host acceptance rate (`50%`). */
  acceptanceTargetPct: number;
  /** Total count of `ACCEPTED` connection requests. */
  acceptedRequestsCount: number;
  /** Total count of currently `PENDING` connection requests. */
  pendingRequestsCount: number;
  /** Percentage of Week 1 active participants who remain active in Week 4. */
  returnParticipationPct: number;
  /** Pilot threshold gate target for return participation (`40%`). */
  returnParticipationTargetPct: number;
  /** Median weekly time spent by pilot hosts in minutes. */
  medianHostWorkloadMinutes: number;
  /** Maximum target host workload in minutes per week (`45` minutes). */
  hostWorkloadTargetMinutes: number;
  /** Count of material privacy, trust, or security incidents (gate requires `0`). */
  materialTrustIncidents: number;
}

/**
 * Immutable editorial or security audit log entry (`/api/v1/portal/audit-log`).
 */
export interface AuditEventDto {
  /** Unique audit event identifier. */
  id: string;
  /** User ID of the actor who performed the action. */
  actorId: string;
  /** Machine-readable action type (e.g., `'REVISION_APPROVED'`, `'CONTENT_PUBLISHED'`). */
  actionType: string;
  /** Domain entity type affected (e.g., `'CONTENT_REVISION'`, `'CHALLENGE_ENTRY'`). */
  targetType: string;
  /** Identifier of the affected domain entity. */
  targetId: string;
  /** Human-readable description of the state transition and guardrails verified. */
  details: string;
  /** ISO-8601 UTC timestamp when the audit event was recorded. */
  createdAt: string;
}

/**
 * ============================================================================
 * CAMPFIRE ENGAGEMENT & RECOGNITION CONTRACTS (17 November 2026 — CF-002..CF-007)
 * ============================================================================
 */

/**
 * Explicit runtime transport mode for the web application:
 * - `'demo'`: Uses the deterministic in-browser `localStorage` simulation engine.
 * - `'live'`: Sends authoritative HTTP requests to the Spring Boot `/api/v1` backend
 *   with zero silent fallback on error.
 */
export type AppMode = 'demo' | 'live';

/**
 * Standard JSON error payload returned by Spring Boot `ApiExceptionHandler` on HTTP 4xx/5xx.
 */
export interface ProblemDetailDto {
  /** HTTP status code (e.g., `401`, `403`, `404`, `409`, `422`, `500`). */
  status: number;
  /** Machine-readable domain error code (e.g., `'SELF_APPROVAL_DENIED'`, `'FORBIDDEN_ACCESS'`). */
  code: string;
  /** Human-readable explanation of why the request was rejected. */
  message: string;
  /** ISO-8601 UTC timestamp when the error occurred on the server. */
  timestamp: string;
}

/**
 * Custom Error subclass thrown by `apiClient.ts` whenever an operation fails in
 * `live` or `demo` mode, preserving the HTTP `status` and domain `code` so the UI
 * can render explicit error banners and retry controls.
 */
export class PulseHttpError extends Error {
  /** HTTP status code (`401`, `403`, `404`, `409`, `422`, `500`, or `0` for network failure). */
  public readonly status: number;
  /** Machine-readable error code (e.g., `'FORBIDDEN_ACCESS'`, `'NETWORK_ERROR'`). */
  public readonly code: string;

  /**
   * Constructs a typed `PulseHttpError`.
   * @param status HTTP status code (or `0` if `fetch` failed at the network layer).
   * @param code Machine-readable error identifier from the backend contract.
   * @param message Human-readable error details displayed in the alert banner.
   */
  constructor(status: number, code: string, message: string) {
    // Pass the human-readable message to the parent JavaScript `Error` constructor.
    super(message);
    // Set the error name so `instanceof` and stack traces identify `PulseHttpError`.
    this.name = 'PulseHttpError';
    // Store the HTTP status code for conditional UI rendering (e.g., 403 role gates).
    this.status = status;
    // Store the machine-readable error code for structured diagnostics.
    this.code = code;
  }
}

/**
 * A user's weekly Campfire Challenge story submission (`/api/v1/challenges/{id}/entries`).
 */
export interface ChallengeEntryDto {
  /** Unique identifier of the challenge entry record. */
  entryId: string;
  /** Identifier of the parent weekly challenge (e.g., `'ch_beyond_01'`). */
  challengeId: string;
  /** Identifier of the backing `content_items` record in the editorial pipeline. */
  contentId: string;
  /** Current revision number (`1`, `2`...) of the submitted story. */
  revisionNumber: number;
  /** Editorial lifecycle status (`'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'PUBLISHED' | 'WITHDRAWN'`). */
  status: ContentStatus;
  /** Story headline title (5–100 characters). */
  title: string;
  /** Short auto-generated or author-supplied summary preview. */
  summary: string;
  /** Full story body text (30–1,200 characters). */
  bodyText: string;
  /** Target audience ID (e.g., `'aud_pilot'`). */
  audienceId: string;
  /** Optional reviewer feedback when changes are requested or approved. */
  reviewerNotes?: string | null;
  /** Points awarded for this entry (`0` while in review/approved; `20` ONLY when `PUBLISHED`). */
  pointsAwarded: number;
}

/**
 * Active Weekly Campfire Challenge prompt and metadata (`/api/v1/challenges/current`).
 */
export interface ChallengeDto {
  /** Unique weekly challenge identifier (e.g., `'ch_beyond_01'`). */
  id: string;
  /** Challenge theme title (e.g., `'Beyond My Job Title'`). */
  title: string;
  /** Prompt instructions encouraging colleagues to share a story beyond their daily role. */
  prompt: string;
  /** ISO-8601 UTC timestamp when the weekly challenge window opens. */
  startsAt: string;
  /** ISO-8601 UTC timestamp when the weekly challenge window closes. */
  endsAt: string;
  /** Authoritative timezone for weekly point boundaries (`'UTC'`). */
  timezone: string;
  /** Monday `YYYY-MM-DD` UTC week key (e.g., `'2026-11-16'`). */
  weekStart: string;
  /** Audience ID eligible to participate in this challenge. */
  audienceId: string;
  /** Human-readable name of the eligible audience. */
  audienceName: string;
  /** Whether the challenge window is currently `'ACTIVE'` or `'CLOSED'`. */
  status: 'ACTIVE' | 'CLOSED';
  /** The current caller's own submission for this challenge, if one exists. */
  myEntry?: ChallengeEntryDto | null;
}

/**
 * Weekly recognition points and milestone badges for a colleague (`/api/v1/recognition/me`).
 * Enforces the 60-point weekly cap: +20 contribution, up to +15 giving, up to +25 receiving.
 */
export interface RecognitionProgressDto {
  /** User ID of the colleague whose progress is represented. */
  userId: string;
  /** Display name of the colleague. */
  displayName: string;
  /** Monday `YYYY-MM-DD` UTC week key for this recognition period. */
  weekStart: string;
  /** Timezone used to compute the weekly boundary (`'UTC'`). */
  timezone: string;
  /** Total weekly points capped at 60 (`contributionPoints + givingPoints + receivingPoints`). */
  points: number;
  /** Points earned from a published story this week (`20` if published, else `0`). */
  contributionPoints: number;
  /** Total number of published colleague stories appreciated by this user this week. */
  givenCount: number;
  /** Points earned from giving appreciations (`Math.min(3, givenCount) * 5`, max `15`). */
  givingPoints: number;
  /** Count of distinct colleagues who appreciated this user's published stories this week. */
  receivedDistinctCount: number;
  /** Points earned from receiving appreciations (`Math.min(5, receivedDistinctCount) * 5`, max `25`). */
  receivingPoints: number;
  /** Count of distinct colleague teams/functions supported via appreciations (unlocks `Connection Maker` at 3). */
  distinctTeamsSupported: number;
  /** Unlocked milestone badge names (`'First Spark'`, `'Connection Maker'`, `'Community Favourite'`). */
  badges: string[];
  /** True if the user allows their name to appear on the optional Top 5 leaderboard; false if opted out. */
  showOnLeaderboard: boolean;
}

/**
 * Result returned when a colleague appreciates a published story (`POST /api/v1/content/{id}/appreciations`).
 */
export interface AppreciationResultDto {
  /** Unique identifier of the appreciation record. */
  appreciationId: string;
  /** ID of the published story that was appreciated. */
  contentId: string;
  /** User ID of the colleague giving the appreciation. */
  actorId: string;
  /** User ID of the story author receiving the appreciation. */
  recipientId: string;
  /** Monday `YYYY-MM-DD` UTC week key when the appreciation was recorded. */
  weekStart: string;
  /** True if this call was an idempotent duplicate (adds `0` points on repeat clicks). */
  alreadyAppreciated: boolean;
  /** Points added to the giver's weekly total (`5` if under the 3-story weekly cap, else `0`). */
  giverPointsDelta: number;
  /** Points added to the receiver's weekly total (`5` if under the 5-colleague weekly cap, else `0`). */
  receiverPointsDelta: number;
  /** Updated recognition progress snapshot for the caller (`actorId`). */
  actorProgress: RecognitionProgressDto;
}

/**
 * Single ranked entry on the optional Top 5 Community Champions leaderboard.
 */
export interface LeaderboardRowDto {
  /** Competition rank (`1`..`5`); tied scores share the same rank number. */
  rank: number;
  /** User ID of the ranked colleague. */
  userId: string;
  /** Display name of the ranked colleague. */
  displayName: string;
  /** Department/function of the ranked colleague. */
  functionName: string;
  /** Geographic region of the ranked colleague. */
  region: string;
  /** Total weekly recognition points (`0`..`60`). */
  points: number;
  /** Milestone badges unlocked by this colleague during the week. */
  badges: string[];
}

/**
 * Response payload for `GET /api/v1/recognition/leaderboard`.
 */
export interface LeaderboardResponseDto {
  /** Monday `YYYY-MM-DD` UTC week key for the leaderboard period. */
  weekStart: string;
  /** Authoritative timezone (`'UTC'`). */
  timezone: string;
  /** Audience ID whose opted-in members are ranked. */
  audienceId: string;
  /** Top 5 opted-in colleagues ordered by points descending, then display name ascending. */
  entries: LeaderboardRowDto[];
}

// ============================================================================
// SECTION 14: MAKE ME PRODUCTIVE CONTRACTS (MP-03, MP-04, MP-05)
// ============================================================================

export type HabitSchedule = 'daily' | 'weekdays' | 'weekends';
export type ProductivitySessionKind = 'focus' | 'short' | 'long' | 'breath' | 'mind';

export interface PersonalHabitDto {
  id: string;
  name: string;
  schedule: HabitSchedule;
  timezone: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface HabitCheckinDto {
  id: string;
  habitId: string;
  localDate: string;
  completed: boolean;
  updatedAt: string;
}

export interface ProductivityPreferencesDto {
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  resetMinutes: number;
  timezone: string;
  completedFocusSessions: number;
  updatedAt: string;
}

export interface FocusSessionDto {
  id: string;
  clientSessionId: string;
  kind: ProductivitySessionKind;
  plannedSeconds: number;
  completedAt: string;
  completedFocusSessions: number;
}
