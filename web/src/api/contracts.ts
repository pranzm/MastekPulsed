/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/api/contracts.ts
 * Journey: Shared TypeScript Data Contracts across Employee Web, Publishing
 *          Portal, and React Native Mobile (`docs/aidlc/openapi.yaml`).
 * Why it is designed this way:
 *   In AI-DLC, keeping TypeScript interfaces 100% identical to the Java 21
 *   Spring Boot DTOs (`ContentCardDto`, `ConnectionRequestDto`, `UserProfileDto`,
 *   `AiAssistanceResponse`, `PortalQueueItemDto`) guarantees compile-time safety
 *   whether a developer uses Claude Code, Codex, or GitHub Copilot.
 * ============================================================================
 */

export type RegionCode = 'INDIA' | 'UK' | 'US';
export type ScopedRole = 'EMPLOYEE' | 'CREATOR' | 'REVIEWER' | 'PUBLISHER' | 'MODERATOR' | 'ADMIN';
export type ContentType = 'STORY' | 'UPDATE' | 'CULTURE_CAST' | 'RADIO_QUEUE';
export type ContentStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'SCHEDULED' | 'PUBLISHED' | 'WITHDRAWN';
export type InvitationStatusLabel = 'OPEN' | 'FULL' | 'PAUSED' | 'EXPIRED';
export type RequestState = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED';
export type HappenedAnswer = 'YES' | 'NO' | 'NOT_YET';
export type BilateralOutcome = 'USEFUL_CONFIRMED' | 'NOT_USEFUL' | 'NOT_HAPPENED' | 'UNKNOWN';

export interface UserProfileDto {
  id: string;
  displayName: string;
  email: string;
  region: RegionCode;
  functionName: string;
  roles: ScopedRole[];
  interests: string[];
  audiences: string[];
  notificationFrequency: string;
  hostPaused: boolean;
}

export interface InvitationSummaryDto {
  id: string;
  hostId: string;
  hostName: string;
  hostRegion: string;
  hostFunction: string;
  topicLabel: string;
  durationMinutes: number;
  capacity: number;
  acceptedCount: number;
  remainingPlaces: number;
  paused: boolean;
  expiresAt: string;
  statusLabel: InvitationStatusLabel;
}

export interface ContentCardDto {
  id: string;
  contentType: ContentType;
  title: string;
  summary: string;
  bodyText: string;
  officialSource?: string | null;
  externalUrl?: string | null;
  authorId: string;
  authorName: string;
  authorFunction: string;
  authorRegion: string;
  audienceId: string;
  audienceName: string;
  tags: string[];
  status: ContentStatus;
  currentVersion: number;
  publishedVersion?: number | null;
  publishedAt?: string | null;
  factualExplanation: string;
  hasAudio: boolean;
  durationSeconds?: number | null;
  transcriptText?: string | null;
  signedAudioUrl?: string | null;
  invitation?: InvitationSummaryDto | null;
}

export interface AudioChapter {
  index: number;
  title: string;
  startSeconds: number;
  durationMinutes?: number;
}

export interface AudioListenItemDto {
  id: string;
  contentType: 'RADIO_QUEUE' | 'CULTURE_CAST';
  title: string;
  summary: string;
  bodyText: string;
  hostId: string;
  hostName: string;
  durationSeconds: number;
  resumePositionSeconds: number;
  transcriptText: string;
  chaptersJson: string;
  relatedStoryId?: string | null;
  signedAudioUrl: string;
  publishedVersion: number;
}

export interface ParticipantConfirmationDto {
  happened: HappenedAnswer;
  useful?: boolean | null;
}

export interface ConnectionRequestDto {
  id: string;
  invitationId: string;
  storyId: string;
  storyTitle: string;
  topicLabel: string;
  requesterId: string;
  requesterName: string;
  requesterRegion: string;
  requesterFunction: string;
  hostId: string;
  hostName: string;
  hostRegion: string;
  hostFunction: string;
  messageText: string;
  aiAssisted: boolean;
  state: RequestState;
  version: number;
  capacity: number;
  acceptedCount: number;
  remainingPlaces: number;
  createdAt: string;
  myConfirmation?: ParticipantConfirmationDto | null;
  confirmationsCount: number;
  bilateralOutcome: BilateralOutcome;
}

export interface PortalQueueItemDto {
  id: string;
  revisionId: string;
  contentType: ContentType;
  title: string;
  summary: string;
  bodyText: string;
  authorId: string;
  authorName: string;
  audienceId: string;
  audienceName: string;
  status: ContentStatus;
  currentVersion: number;
  publishedVersion?: number | null;
  hasAudio: boolean;
  transcriptChecked: boolean;
  mediaReady: boolean;
  approvedBy?: string | null;
  approvedByName?: string | null;
  reviewerNotes?: string | null;
  scheduledFor?: string | null;
  updatedAt: string;
}

export interface AiBudgetStatusDto {
  actionsUsed: number;
  actionCap: number;
  percentUsed: number;
  estimatedInputTokens: number;
  estimatedOutputTokens: number;
  alertLevel: 'NORMAL' | 'ALERT_50' | 'ALERT_80' | 'CAPPED_100';
  hardCapReached: boolean;
}

export interface AiAssistanceResponseDto {
  assistType: 'INVITATION_DRAFT' | 'STORY_POLISH' | 'UPDATE_SUMMARY';
  aiAvailable: boolean;
  aiSuggestion: string | null;
  templateFallback: string;
  sourceLink: string;
  aiLabel: string;
  budgetStatus: AiBudgetStatusDto;
}

export interface PilotMetricsDto {
  usefulConnectionsCount: number;
  unknownFeedbackCount: number;
  usefulConnectionsPct: number;
  usefulConnectionsTargetPct: number;
  acceptanceRatePct: number;
  acceptanceTargetPct: number;
  acceptedRequestsCount: number;
  pendingRequestsCount: number;
  returnParticipationPct: number;
  returnParticipationTargetPct: number;
  medianHostWorkloadMinutes: number;
  hostWorkloadTargetMinutes: number;
  materialTrustIncidents: number;
}

export interface AuditEventDto {
  id: string;
  actorId: string;
  actionType: string;
  targetType: string;
  targetId: string;
  details: string;
  createdAt: string;
}

/**
 * ============================================================================
 * CAMPFIRE ENGAGEMENT & RECOGNITION CONTRACTS (17 November 2026 — CF-002..CF-007)
 * ============================================================================
 */
export type AppMode = 'demo' | 'live';

export interface ProblemDetailDto {
  status: number;
  code: string;
  message: string;
  timestamp: string;
}

export class PulseHttpError extends Error {
  public readonly status: number;
  public readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'PulseHttpError';
    this.status = status;
    this.code = code;
  }
}

export interface ChallengeEntryDto {
  entryId: string;
  challengeId: string;
  contentId: string;
  revisionNumber: number;
  status: ContentStatus;
  title: string;
  summary: string;
  bodyText: string;
  audienceId: string;
  reviewerNotes?: string | null;
  pointsAwarded: number;
}

export interface ChallengeDto {
  id: string;
  title: string;
  prompt: string;
  startsAt: string;
  endsAt: string;
  timezone: string;
  weekStart: string;
  audienceId: string;
  audienceName: string;
  status: 'ACTIVE' | 'CLOSED';
  myEntry?: ChallengeEntryDto | null;
}

export interface RecognitionProgressDto {
  userId: string;
  displayName: string;
  weekStart: string;
  timezone: string;
  points: number;
  contributionPoints: number;
  givenCount: number;
  givingPoints: number;
  receivedDistinctCount: number;
  receivingPoints: number;
  distinctTeamsSupported: number;
  badges: string[];
  showOnLeaderboard: boolean;
}

export interface AppreciationResultDto {
  appreciationId: string;
  contentId: string;
  actorId: string;
  recipientId: string;
  weekStart: string;
  alreadyAppreciated: boolean;
  giverPointsDelta: number;
  receiverPointsDelta: number;
  actorProgress: RecognitionProgressDto;
}

export interface LeaderboardRowDto {
  rank: number;
  userId: string;
  displayName: string;
  functionName: string;
  region: string;
  points: number;
  badges: string[];
}

export interface LeaderboardResponseDto {
  weekStart: string;
  timezone: string;
  audienceId: string;
  entries: LeaderboardRowDto[];
}
