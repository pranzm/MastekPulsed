import React, { useState } from 'react';
import {
  AiBudgetStatusDto,
  AuditEventDto,
  PilotMetricsDto,
  PortalQueueItemDto,
  UserProfileDto,
} from '../api/contracts';
import { pulseApi } from '../api/apiClient';
import {
  CheckCircle2,
  AlertTriangle,
  ShieldAlert,
  Calendar,
  FileCheck2,
  History,
  BarChart3,
  Edit3,
  Trash2,
  Send,
  Users,
  Radio,
} from 'lucide-react';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/portal/PublishingPortalWorkspace.tsx
 * Journey: Supporting Journey B ("Submit a contribution -> review the exact
 *          revision -> publish to the intended audience") + Page 11 Publishing
 *          Portal Wireframe + Page 5 & 12 Pilot Thresholds & AI Budget Gate.
 *
 * Why it is designed this way:
 *   Matches the exact layout & navigation of Proposal Page 11 ("05C / PUBLISHING
 *   PORTAL WIREFRAME: Create. Review. Publish with confidence."):
 *   - Left Nav: `Dashboard`, `Content`, `Review queue`, `Schedule`, `Media library`,
 *     `Audiences`, `Audit log`.
 *   - Enforces the controlled workflow:
 *     `Draft > In review > Approved > Scheduled / Published > Withdrawn`
 *   - Demonstrates the 3 critical security/editorial guardrails live:
 *     1. Reviewer (`usr_priya`) can approve or request changes on a revision,
 *        but CANNOT self-approve her own story (`403 Self-Approval Denied`).
 *     2. Admin (`usr_elena`) manages role/audience configuration and has NO
 *        automatic editorial approval right (`403 Forbidden`).
 *     3. Publisher (`usr_marcus`) releases `APPROVED` revisions immediately or
 *        schedules them, and any edit to content or audience after approval
 *        automatically creates `Revision N+1` and returns it to `IN_REVIEW`!
 * ============================================================================
 */

/**
 * Props accepted by the `PublishingPortalWorkspace` component.
 */
interface PublishingPortalProps {
  /** Currently authenticated portal user (`REVIEWER`, `PUBLISHER`, `MODERATOR`, or `ADMIN`). */
  currentUser: UserProfileDto;
  /** List of content revisions in the editorial queue scoped to `currentUser.audiences`. */
  portalQueue: PortalQueueItemDto[];
  /** Immutable editorial and security audit log events (`/api/v1/portal/audit-log`). */
  auditLog: AuditEventDto[];
  /** Aggregate 4-week pilot threshold gate metrics (`/api/v1/pilot/metrics`). */
  pilotMetrics: PilotMetricsDto;
  /** Current 4-week pilot AI action budget and hard-cap telemetry (`/api/v1/ai/budget`). */
  aiBudget: AiBudgetStatusDto;
  /** Callback to reload all portal and employee data after an editorial state change. */
  onRefreshAll: () => Promise<void>;
  /** Callback to switch the active persona (e.g., Priya Reviewer -> Marcus Publisher). */
  onSwitchPersona: (userId: string) => void;
}

/**
 * Role-Based Publishing Portal component implementing Proposal Page 11 (`05C`),
 * the 4-week Pilot Threshold Gate dashboard (Proposal Page 12), and the 720-action
 * AI Budget Hard-Cap simulator (Proposal Page 5).
 */
export const PublishingPortalWorkspace: React.FC<PublishingPortalProps> = ({
  currentUser,
  portalQueue,
  auditLog,
  pilotMetrics,
  aiBudget,
  onRefreshAll,
  onSwitchPersona,
}) => {
  // Currently selected left-sidebar navigation section (defaults to 'REVIEW_QUEUE').
  const [activeSection, setActiveSection] = useState<
    'REVIEW_QUEUE' | 'DASHBOARD' | 'CONTENT' | 'SCHEDULE' | 'MEDIA' | 'AUDIENCES' | 'AUDIT_LOG'
  >('REVIEW_QUEUE');

  // ID of the queue item currently inspected in the right-hand Revision Inspector panel.
  const [selectedItemId, setSelectedItemId] = useState<string>(portalQueue[0]?.id || 'cc_ep_03');
  // Optional reviewer notes entered before clicking "Approve revision" or "Request changes".
  const [reviewerNotes, setReviewerNotes] = useState<string>('');
  // Target ISO-8601 UTC timestamp when scheduling an approved revision for future release.
  const [scheduleIso, setScheduleIso] = useState<string>('2026-10-28T09:00:00Z');

  // Edit-Invalidates-Approval Editor State (Proposal Page 11 invariant)
  // True when the user is editing a queue item's title, body, or audience.
  const [editingItem, setEditingItem] = useState<boolean>(false);
  // Controlled input state for the edited headline title.
  const [editTitle, setEditTitle] = useState<string>('');
  // Controlled textarea state for the edited body text.
  const [editBody, setEditBody] = useState<string>('');
  // Controlled select state for the edited target audience ID.
  const [editAudience, setEditAudience] = useState<string>('aud_pilot');

  // Banner alert message shown after Reviewer, Publisher, or Budget Simulator actions.
  const [portalAlert, setPortalAlert] = useState<{ type: 'success' | 'error' | 'info'; text: string } | null>(null);

  // Resolve the currently selected queue item (falling back to the first item in the queue).
  const selectedItem = portalQueue.find((i) => i.id === selectedItemId) || portalQueue[0];

  /**
   * Displays a temporary status/error alert banner at the top of the Publishing Portal
   * and automatically dismisses it after 6 seconds.
   * @param type Visual severity style (`'success' | 'error' | 'info'`).
   * @param text Human-readable alert message.
   */
  const showAlert = (type: 'success' | 'error' | 'info', text: string) => {
    setPortalAlert({ type, text });
    setTimeout(() => setPortalAlert(null), 6000);
  };

  /**
   * Submits a Reviewer decision (`'APPROVE'` or `'REQUEST_CHANGES'`) for the exact
   * `selectedItem.currentVersion`. Enforces `REVIEWER` role and `approved_by != author_id`
   * (no self-approval).
   * @param decision `'APPROVE'` to transition to `APPROVED`, or `'REQUEST_CHANGES'` to return to `DRAFT`.
   */
  const handleReviewDecision = async (decision: 'APPROVE' | 'REQUEST_CHANGES') => {
    if (!selectedItem) return;
    try {
      await pulseApi.reviewRevision(
        currentUser.id,
        selectedItem.id,
        selectedItem.currentVersion,
        decision,
        reviewerNotes
      );
      await onRefreshAll();
      setReviewerNotes('');
      showAlert(
        'success',
        decision === 'APPROVE'
          ? `Revision ${selectedItem.currentVersion} of "${selectedItem.title}" APPROVED by ${currentUser.displayName}. Switch to Publisher Marcus Vance to release or schedule it!`
          : `Requested changes on Revision ${selectedItem.currentVersion} of "${selectedItem.title}". Returned to DRAFT.`
      );
    } catch (e: any) {
      showAlert('error', e.message || 'Review action denied');
    }
  };

  /**
   * Releases an `APPROVED` revision immediately (`'IMMEDIATE'` -> `PUBLISHED`) or
   * schedules it (`'SCHEDULED'` -> `SCHEDULED`). Requires the `PUBLISHER` role.
   * When a Weekly Challenge story transitions to `PUBLISHED`, its author is
   * awarded +20 contribution points and the `First Spark` badge.
   * @param mode `'IMMEDIATE'` or `'SCHEDULED'`.
   */
  const handlePublishAction = async (mode: 'IMMEDIATE' | 'SCHEDULED') => {
    if (!selectedItem) return;
    try {
      await pulseApi.publishOrSchedule(currentUser.id, selectedItem.id, mode, scheduleIso);
      await onRefreshAll();
      showAlert(
        'success',
        mode === 'IMMEDIATE'
          ? `Published Revision ${selectedItem.currentVersion} of "${selectedItem.title}" to ${selectedItem.audienceName}!`
          : `Scheduled Revision ${selectedItem.currentVersion} of "${selectedItem.title}" for ${scheduleIso}.`
      );
    } catch (e: any) {
      showAlert('error', e.message || 'Publish action denied');
    }
  };

  /**
   * Saves edits to a portal item's title, body, or audience.
   * Enforces the Proposal Page 11 invariant: editing content or audience after
   * approval increments `currentVersion` (`Revision N+1`), clears `approvedBy`,
   * and resets `status` back to `IN_REVIEW`.
   */
  const handleEditInvalidateApproval = async () => {
    if (!selectedItem) return;
    try {
      const updated = await pulseApi.editPortalItemAndInvalidateApproval(
        currentUser.id,
        selectedItem.id,
        editTitle,
        editBody,
        editAudience
      );
      await onRefreshAll();
      setEditingItem(false);
      showAlert(
        'info',
        `Edited content/audience! Created Revision ${updated.currentVersion}, invalidated prior approval, and returned status to IN_REVIEW (Page 11 invariant).`
      );
    } catch (e: any) {
      showAlert('error', e.message || 'Edit failed');
    }
  };

  /**
   * Withdraws a published content item from employee feeds (`status` -> `'WITHDRAWN'`).
   * Requires `PUBLISHER` or `MODERATOR` role and reverses any Weekly Challenge
   * recognition points tied to that item.
   */
  const handleWithdraw = async () => {
    if (!selectedItem) return;
    try {
      await pulseApi.withdrawContent(currentUser.id, selectedItem.id, 'Withdrawn via Publishing Portal');
      await onRefreshAll();
      showAlert('info', `Withdrew "${selectedItem.title}" from employee feeds.`);
    } catch (e: any) {
      showAlert('error', e.message || 'Withdraw denied');
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 pb-28">
      {/* Role Guidance Banner for Publishing Portal */}
      <div className="mb-6 bg-slate-900 text-white rounded-xl p-4 shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="text-xs font-bold uppercase tracking-wider text-blue-300">
            05C / Scoped Roles & Controlled Workflow (Proposal Page 11)
          </div>
          <p className="text-xs sm:text-sm text-slate-200 mt-1">
            <strong>Draft &gt; In review &gt; Approved &gt; Scheduled / Published &gt; Withdrawn.</strong> Switch
            persona quickly:{' '}
            <button
              onClick={() => onSwitchPersona('usr_priya')}
              className="underline font-bold text-emerald-300 hover:text-white mr-2"
            >
              Priya Nair (Reviewer)
            </button>
            <button
              onClick={() => onSwitchPersona('usr_marcus')}
              className="underline font-bold text-blue-300 hover:text-white mr-2"
            >
              Marcus Vance (Publisher/Moderator)
            </button>
            <button
              onClick={() => onSwitchPersona('usr_elena')}
              className="underline font-bold text-amber-300 hover:text-white"
            >
              Elena Rostova (Admin — no editorial approval right)
            </button>
          </p>
        </div>
        <div className="text-xs bg-slate-800 border border-slate-700 px-3 py-1.5 rounded-lg shrink-0">
          Current Role: <strong>{currentUser.roles.join(', ')}</strong>
        </div>
      </div>

      {/* Portal Feedback Alert Banner */}
      {portalAlert && (
        <div
          role="alert"
          className={`mb-5 p-4 rounded-xl border flex items-center justify-between shadow-sm ${
            portalAlert.type === 'success'
              ? 'bg-emerald-50 border-emerald-300 text-emerald-900'
              : portalAlert.type === 'error'
              ? 'bg-red-50 border-red-300 text-red-900'
              : 'bg-blue-50 border-blue-300 text-blue-900'
          }`}
        >
          <span className="text-sm font-medium">{portalAlert.text}</span>
          <button onClick={() => setPortalAlert(null)} className="text-xs font-bold underline ml-4">
            Dismiss
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Sidebar matching Page 11 Wireframe */}
        <aside className="lg:col-span-2">
          <nav aria-label="Publishing portal navigation" className="bg-white rounded-xl border border-slate-200 p-2 shadow-sm">
            <div className="px-3 py-2 text-xs font-bold uppercase tracking-wider text-slate-400">
              Publishing Portal
            </div>
            {(
              [
                { id: 'DASHBOARD', label: 'Dashboard (Gates)' },
                { id: 'CONTENT', label: 'Content' },
                { id: 'REVIEW_QUEUE', label: 'Review queue' },
                { id: 'SCHEDULE', label: 'Schedule' },
                { id: 'MEDIA', label: 'Media library' },
                { id: 'AUDIENCES', label: 'Audiences' },
                { id: 'AUDIT_LOG', label: 'Audit log' },
              ] as const
            ).map((nav) => (
              <button
                key={nav.id}
                onClick={() => setActiveSection(nav.id)}
                className={`w-full text-left px-3 py-2.5 rounded-lg text-sm font-medium mb-1 transition-colors ${
                  activeSection === nav.id
                    ? 'bg-[#002180] text-white font-semibold'
                    : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                {nav.label}
              </button>
            ))}
          </nav>
        </aside>

        {/* Main Portal Area */}
        <main className="lg:col-span-10 space-y-6">
          {/* =====================================================================
              SECTION 1: REVIEW QUEUE & REVISION INSPECTOR (Page 11 Wireframe)
             ===================================================================== */}
          {(activeSection === 'REVIEW_QUEUE' || activeSection === 'CONTENT' || activeSection === 'SCHEDULE') && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: Editorial Queue List */}
              <div className="lg:col-span-6 bg-white rounded-xl border border-slate-200 p-5 shadow-sm space-y-4">
                <div>
                  <h1 className="text-2xl font-bold text-[#002180]">Review queue</h1>
                  <p className="text-xs text-slate-500 mt-0.5">
                    All content types · Region: All permitted · Click an item to inspect its exact revision
                  </p>
                </div>

                <div className="space-y-3">
                  {portalQueue.map((item) => (
                    <button
                      key={item.id}
                      onClick={() => {
                        setSelectedItemId(item.id);
                        setEditingItem(false);
                      }}
                      className={`w-full text-left p-4 rounded-xl border transition-all flex items-center justify-between gap-3 ${
                        selectedItem?.id === item.id
                          ? 'bg-blue-50/90 border-2 border-[#002180] shadow-sm'
                          : 'bg-slate-50/70 border-slate-200 hover:border-blue-300'
                      }`}
                    >
                      <div>
                        <div className="font-bold text-slate-900 text-base">{item.title}</div>
                        <div className="text-xs text-slate-600 mt-0.5">
                          {item.summary} · <strong>Revision {item.currentVersion}</strong>
                        </div>
                        <div className="text-[11px] text-slate-500 mt-1">
                          Author: {item.authorName} · Audience: {item.audienceName}
                        </div>
                      </div>
                      <span
                        className={`px-2.5 py-1 rounded-full text-xs font-bold shrink-0 ${
                          item.status === 'IN_REVIEW'
                            ? 'bg-blue-100 text-[#002180]'
                            : item.status === 'APPROVED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : item.status === 'PUBLISHED'
                            ? 'bg-indigo-100 text-indigo-900'
                            : item.status === 'WITHDRAWN'
                            ? 'bg-red-100 text-red-800'
                            : 'bg-amber-100 text-amber-900'
                        }`}
                      >
                        {item.status === 'IN_REVIEW'
                          ? 'In review'
                          : item.status === 'DRAFT' && item.reviewerNotes
                          ? 'Changes needed'
                          : item.status}
                      </span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Right Column: Selected Revision Inspector & Role-Enforced Action Box (Page 11 Bottom Box) */}
              {selectedItem && (
                <div className="lg:col-span-6 bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-5">
                  <div className="border-b border-slate-200 pb-4">
                    <div className="text-xs font-bold uppercase tracking-wider text-[#002180]">
                      Selected: {selectedItem.title} / Revision {selectedItem.currentVersion}
                    </div>
                    <h2 className="text-xl font-bold text-slate-900 mt-1">{selectedItem.title}</h2>
                    <div className="text-xs text-slate-600 mt-2 flex flex-wrap gap-2">
                      <span className="bg-slate-100 px-2.5 py-1 rounded font-semibold">
                        Audience: {selectedItem.audienceName}
                      </span>
                      <span className="bg-emerald-50 text-emerald-800 px-2.5 py-1 rounded font-semibold">
                        {selectedItem.transcriptChecked ? '✓ Transcript checked' : 'Transcript pending'}
                      </span>
                      <span className="bg-emerald-50 text-emerald-800 px-2.5 py-1 rounded font-semibold">
                        {selectedItem.mediaReady ? '✓ Media ready' : 'Media scanning'}
                      </span>
                    </div>
                  </div>

                  {!editingItem ? (
                    <>
                      {/* Full Revision Body Text Preview */}
                      <div className="bg-slate-50 rounded-xl border border-slate-200 p-4 text-sm text-slate-800 whitespace-pre-line">
                        {selectedItem.bodyText}
                      </div>

                      {/* Revision Provenance & Approval Metadata */}
                      <div className="text-xs text-slate-600 space-y-1">
                        <div>
                          <strong>Creator:</strong> {selectedItem.authorName} ({selectedItem.authorId})
                        </div>
                        <div>
                          <strong>Current Status:</strong> {selectedItem.status} (Current Revision:{' '}
                          {selectedItem.currentVersion} · Live Published Revision:{' '}
                          {selectedItem.publishedVersion ?? 'None'})
                        </div>
                        {selectedItem.approvedByName && (
                          <div className="text-emerald-700 font-semibold">
                            ✓ Approved by Reviewer: {selectedItem.approvedByName}
                          </div>
                        )}
                        {selectedItem.reviewerNotes && (
                          <div className="text-amber-800 bg-amber-50 p-2 rounded border border-amber-200">
                            <strong>Reviewer Notes:</strong> {selectedItem.reviewerNotes}
                          </div>
                        )}
                      </div>

                      {/* Reviewer Controls (Page 11: "Approve revision" / "Request changes") */}
                      <div className="border-t border-slate-200 pt-4 space-y-3">
                        <div className="text-xs font-bold uppercase tracking-wider text-slate-500">
                          1. Reviewer Decision (Requires REVIEWER role; No self-approval)
                        </div>
                        <input
                          type="text"
                          value={reviewerNotes}
                          onChange={(e) => setReviewerNotes(e.target.value)}
                          placeholder="Optional reviewer notes (e.g., Source & transcript verified)..."
                          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs"
                        />
                        <div className="flex flex-wrap gap-3">
                          <button
                            onClick={() => handleReviewDecision('APPROVE')}
                            className="bg-[#002180] hover:bg-blue-900 text-white px-4 py-2 rounded-lg text-xs font-semibold shadow-sm"
                          >
                            Approve revision {selectedItem.currentVersion}
                          </button>
                          <button
                            onClick={() => handleReviewDecision('REQUEST_CHANGES')}
                            className="bg-slate-100 hover:bg-slate-200 text-slate-800 px-4 py-2 rounded-lg text-xs font-semibold"
                          >
                            Request changes
                          </button>
                        </div>
                      </div>

                      {/* Publisher Controls (Immediate Release, Scheduled Release, Withdraw) */}
                      <div className="border-t border-slate-200 pt-4 space-y-3">
                        <div className="text-xs font-bold uppercase tracking-wider text-slate-500">
                          2. Publisher Release & Scheduling (Requires PUBLISHER role; Revision must be APPROVED)
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                          <button
                            onClick={() => handlePublishAction('IMMEDIATE')}
                            className="bg-emerald-700 hover:bg-emerald-800 text-white px-4 py-2 rounded-lg text-xs font-semibold"
                          >
                            Publish Revision {selectedItem.currentVersion} Now
                          </button>
                          <button
                            onClick={() => handlePublishAction('SCHEDULED')}
                            className="bg-blue-50 hover:bg-blue-100 text-[#002180] border border-blue-200 px-3 py-2 rounded-lg text-xs font-semibold"
                          >
                            Schedule Release
                          </button>
                          <button
                            onClick={handleWithdraw}
                            className="bg-red-50 hover:bg-red-100 text-red-700 border border-red-200 px-3 py-2 rounded-lg text-xs font-semibold ml-auto"
                          >
                            Withdraw
                          </button>
                        </div>
                      </div>

                      {/* Edit Content/Audience Button -> Demonstrates Approval Invalidation! */}
                      <div className="border-t border-slate-200 pt-4 flex items-center justify-between">
                        <span className="text-xs text-slate-500">
                          Test Page 11 rule: Editing content or audience invalidates approval and creates Revision{' '}
                          {selectedItem.currentVersion + 1}.
                        </span>
                        <button
                          onClick={() => {
                            setEditTitle(selectedItem.title);
                            setEditBody(selectedItem.bodyText);
                            setEditAudience(selectedItem.audienceId);
                            setEditingItem(true);
                          }}
                          className="text-xs font-semibold text-[#002180] underline shrink-0 ml-2"
                        >
                          Edit content / audience
                        </button>
                      </div>
                    </>
                  ) : (
                    /* Inline Editor Form that creates Revision N+1 and resets status to IN_REVIEW */
                    <div className="space-y-3 bg-amber-50/60 p-4 rounded-xl border border-amber-300">
                      <div className="text-xs font-bold text-amber-900">
                        Editing "{selectedItem.title}" — Saving will create Revision {selectedItem.currentVersion + 1}{' '}
                        and reset status to IN_REVIEW!
                      </div>
                      <input
                        type="text"
                        value={editTitle}
                        onChange={(e) => setEditTitle(e.target.value)}
                        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold bg-white"
                      />
                      <textarea
                        rows={4}
                        value={editBody}
                        onChange={(e) => setEditBody(e.target.value)}
                        className="w-full rounded-lg border border-slate-300 p-3 text-xs bg-white"
                      />
                      <select
                        value={editAudience}
                        onChange={(e) => setEditAudience(e.target.value)}
                        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs bg-white"
                      >
                        <option value="aud_pilot">Colleagues in the pilot (aud_pilot)</option>
                        <option value="aud_all">All employees (aud_all)</option>
                      </select>
                      <div className="flex gap-2">
                        <button
                          onClick={handleEditInvalidateApproval}
                          className="bg-[#002180] text-white px-4 py-2 rounded-lg text-xs font-semibold"
                        >
                          Save Edit (Create Revision {selectedItem.currentVersion + 1} in Review)
                        </button>
                        <button
                          onClick={() => setEditingItem(false)}
                          className="bg-white border border-slate-300 px-3 py-2 rounded-lg text-xs"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* =====================================================================
              SECTION 2: DASHBOARD — PILOT THRESHOLDS (Page 12) & AI BUDGET GATE (Page 5)
             ===================================================================== */}
          {activeSection === 'DASHBOARD' && (
            <div className="space-y-6">
              {/* Five Pilot Threshold Gates Card */}
              <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
                <h2 className="text-xl font-bold text-[#002180]">
                  06 / The Learning Plan — Four-Week Pilot Threshold Gates (Proposal Page 12)
                </h2>
                <p className="text-xs text-slate-600">
                  Track review time, overdue content, and aggregate reach — never employee appraisal scores or public
                  popularity rankings.
                </p>

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
                  <div className="p-4 rounded-xl bg-blue-50/70 border border-blue-200">
                    <div className="text-xs font-bold text-slate-500">1. Useful Connections</div>
                    <div className="text-2xl font-bold text-[#002180] mt-1">
                      {pilotMetrics.usefulConnectionsPct}% ({pilotMetrics.usefulConnectionsCount} pairs)
                    </div>
                    <div className="text-[11px] text-slate-600 mt-1">
                      Gate: At least {pilotMetrics.usefulConnectionsTargetPct}% (Both confirm Yes+Yes;{' '}
                      {pilotMetrics.unknownFeedbackCount} unknown)
                    </div>
                  </div>

                  <div className="p-4 rounded-xl bg-emerald-50/70 border border-emerald-200">
                    <div className="text-xs font-bold text-slate-500">2. Acceptance Rate</div>
                    <div className="text-2xl font-bold text-emerald-800 mt-1">{pilotMetrics.acceptanceRatePct}%</div>
                    <div className="text-[11px] text-slate-600 mt-1">
                      Gate: At least {pilotMetrics.acceptanceTargetPct}% ({pilotMetrics.pendingRequestsCount} pending)
                    </div>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                    <div className="text-xs font-bold text-slate-500">3. Return Participation</div>
                    <div className="text-2xl font-bold text-slate-900 mt-1">{pilotMetrics.returnParticipationPct}%</div>
                    <div className="text-[11px] text-slate-600 mt-1">
                      Gate: At least {pilotMetrics.returnParticipationTargetPct}% (Week 1 active in Week 4)
                    </div>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                    <div className="text-xs font-bold text-slate-500">4. Host Workload</div>
                    <div className="text-2xl font-bold text-slate-900 mt-1">
                      {pilotMetrics.medianHostWorkloadMinutes} min/wk
                    </div>
                    <div className="text-[11px] text-slate-600 mt-1">
                      Gate: Median {pilotMetrics.hostWorkloadTargetMinutes} minutes or less
                    </div>
                  </div>

                  <div className="p-4 rounded-xl bg-emerald-50/70 border border-emerald-200">
                    <div className="text-xs font-bold text-slate-500">5. Trust & Usability</div>
                    <div className="text-2xl font-bold text-emerald-800 mt-1">
                      {pilotMetrics.materialTrustIncidents} incidents
                    </div>
                    <div className="text-[11px] text-slate-600 mt-1">
                      Gate: Zero material incidents; within budget cap
                    </div>
                  </div>
                </div>
              </div>

              {/* AI Workload & Budget Hard-Cap Simulator (Proposal Page 5: "Alert at 50%, 80%, and 100%; disable optional AI at the cap") */}
              <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
                <div className="flex items-center justify-between flex-wrap gap-3">
                  <div>
                    <h2 className="text-xl font-bold text-[#002180]">
                      04 / Illustrative Four-Week AI Workload & Hard Budget Cap (Proposal Page 5)
                    </h2>
                    <p className="text-xs text-slate-600 mt-0.5">
                      720 AI actions cap (60 people × 4 weeks × 3 actions · 0.72M input + 0.36M output tokens). Alert at
                      50%, 80%, and 100%; automatically disable optional AI at 100% cap and use non-AI templates.
                    </p>
                  </div>
                  <span
                    className={`px-3 py-1 rounded-full text-xs font-bold ${
                      aiBudget.hardCapReached
                        ? 'bg-red-100 text-red-800'
                        : aiBudget.alertLevel !== 'NORMAL'
                        ? 'bg-amber-100 text-amber-900'
                        : 'bg-emerald-100 text-emerald-800'
                    }`}
                  >
                    Status: {aiBudget.alertLevel} ({aiBudget.actionsUsed} / {aiBudget.actionCap} actions ·{' '}
                    {aiBudget.percentUsed}%)
                  </span>
                </div>

                {/* Progress bar visualizing percentage of the 720-action AI budget consumed */}
                <div className="w-full h-3 bg-slate-200 rounded-full overflow-hidden">
                  <div
                    className={`h-full transition-all ${
                      aiBudget.hardCapReached
                        ? 'bg-red-600'
                        : aiBudget.percentUsed >= 50
                        ? 'bg-amber-500'
                        : 'bg-[#002180]'
                    }`}
                    style={{ width: `${Math.min(100, aiBudget.percentUsed)}%` }}
                  />
                </div>

                {/* Interactive Simulator Buttons for 50%, 80%, and 100% Hard Cap */}
                <div className="flex flex-wrap items-center gap-2 pt-2">
                  <span className="text-xs font-semibold text-slate-600 mr-2">Simulate Budget Thresholds:</span>
                  {(
                    [
                      { level: 'NORMAL', label: 'Normal (2.5%)' },
                      { level: 'ALERT_50', label: '50% Alert (365/720)' },
                      { level: 'ALERT_80', label: '80% Alert (585/720)' },
                      { level: 'CAPPED_100', label: '100% Hard Cap (720/720 — Disables AI)' },
                    ] as const
                  ).map((btn) => (
                    <button
                      key={btn.level}
                      onClick={async () => {
                        await pulseApi.simulateAiBudgetLevel(currentUser.id, btn.level);
                        await onRefreshAll();
                        showAlert('info', `Simulated AI Budget Level: ${btn.label}`);
                      }}
                      className="px-3 py-1.5 rounded-lg border border-slate-300 hover:bg-slate-100 text-xs font-semibold"
                    >
                      {btn.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* =====================================================================
              SECTION 3: MEDIA LIBRARY & AUDIENCES & AUDIT LOG
             ===================================================================== */}
          {activeSection === 'MEDIA' && (
            <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
              <h2 className="text-xl font-bold text-[#002180]">Private Object Storage & Media Scan Gate</h2>
              <p className="text-xs text-slate-600">
                12 hosts × 2 assets/week × 4 weeks × 2 MB = 192 MB illustrative pilot upload budget. Quarantine &gt;
                Scan &gt; Ready pipeline with short-lived signed URLs.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
                <div className="p-4 rounded-xl border border-slate-200 bg-slate-50">
                  <div className="font-bold text-slate-900">audio/mastek-radio-commute-01.mp3</div>
                  <div className="text-emerald-700 font-semibold mt-1">✓ Scan status: READY · Transcript verified</div>
                </div>
                <div className="p-4 rounded-xl border border-slate-200 bg-slate-50">
                  <div className="font-bold text-slate-900">audio/culture-cast-ep03.mp3</div>
                  <div className="text-emerald-700 font-semibold mt-1">✓ Scan status: READY · Signed URL TTL: 15m</div>
                </div>
                <div className="p-4 rounded-xl border border-slate-200 bg-slate-50">
                  <div className="font-bold text-slate-900">audio/story-01-daniel-retro.mp3</div>
                  <div className="text-emerald-700 font-semibold mt-1">✓ Scan status: READY · Audience: aud_pilot</div>
                </div>
              </div>
            </div>
          )}

          {activeSection === 'AUDIENCES' && (
            <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
              <h2 className="text-xl font-bold text-[#002180]">Scoped Audience Groups (Filter Before Rank)</h2>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
                <div className="p-4 rounded-xl border border-slate-200">
                  <div className="font-bold text-sm text-slate-900">All Employees (aud_all)</div>
                  <p className="text-slate-600 mt-1">Company updates, Mastek Radio, and Culture Cast episodes.</p>
                </div>
                <div className="p-4 rounded-xl border border-slate-200">
                  <div className="font-bold text-sm text-slate-900">Colleagues in the Pilot (aud_pilot)</div>
                  <p className="text-slate-600 mt-1">60 volunteers & 12 hosts across India, UK, and US.</p>
                </div>
                <div className="p-4 rounded-xl border border-amber-300 bg-amber-50/50">
                  <div className="font-bold text-sm text-amber-900">UK Delivery Governance (aud_uk_restricted)</div>
                  <p className="text-amber-800 mt-1">
                    Restricted to Daniel & Elena. Verified 403 Forbidden for non-members (e.g., Asha).
                  </p>
                </div>
              </div>
            </div>
          )}

          {activeSection === 'AUDIT_LOG' && (
            <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
              <h2 className="text-xl font-bold text-[#002180]">Immutable Editorial & Security Audit Log</h2>
              <div className="space-y-2">
                {auditLog.map((ev) => (
                  <div
                    key={ev.id}
                    className="p-3 rounded-lg border border-slate-200 bg-slate-50 text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-2"
                  >
                    <div>
                      <span className="font-bold text-[#002180] mr-2">[{ev.actionType}]</span>
                      <span className="text-slate-800">{ev.details}</span>
                    </div>
                    <span className="text-slate-400 font-mono shrink-0">Actor: {ev.actorId}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};
