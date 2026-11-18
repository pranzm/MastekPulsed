package com.mastek.pulse.content;

import com.mastek.pulse.common.AuditEventEntity;
import com.mastek.pulse.common.IdentityService;
import com.mastek.pulse.common.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ContentController.java
 * Journey: Discovery Feed, Story Details, Owner/Editor Preview & Scoped
 *          Publishing Portal (Proposal Pages 6, 8, 10, 11 + Campfire CF-010 / D2).
 *
 * Why it is designed this way:
 *   1. `/api/v1/stories/{id}` enforces published visibility (`PUBLISHED` only)
 *      and reads ONLY `publishedVersion` + `publishedAudienceId`.
 *   2. `/api/v1/content/{id}/preview` allows owners and scoped editors to preview
 *      unpublished drafts or revisions in review (`currentVersion`).
 *   3. `/api/v1/portal/queue` and `/api/v1/portal/audit-log` pass `caller` to
 *      enforce portal role (`403` for ordinary employees) and audience scoping!
 *   4. All editorial workflow mutations (`createContent`, `editContentAndCreateRevision`,
 *      `submitForReview`, `reviewRevision`, `publishOrSchedule`, `withdrawContent`)
 *      delegate to `PublishingWorkflowService` where pessimistic locking and
 *      revision-bound approval invariants are enforced.
 * ============================================================================
 */
// @RestController marks this class as a REST endpoint handler that serializes all return values to JSON.
@RestController
// @RequestMapping("/api/v1") prefixes all routes in this controller with `/api/v1`.
@RequestMapping("/api/v1")
public class ContentController {

    // Service that resolves and authenticates the caller from `X-Pulse-User-Id`.
    private final IdentityService identityService;

    // Service implementing the 3-stage personalized Discovery Feed pipeline and story/preview reads.
    private final FeedService feedService;

    // Service implementing the revision-bound editorial state machine and synchronous recognition awards.
    private final PublishingWorkflowService publishingWorkflowService;

    /**
     * Constructor injection used by Spring to wire the identity, feed, and publishing services.
     *
     * @param identityService           authenticates caller headers and enforces role/audience permissions
     * @param feedService               serves audience-filtered feeds, published story details, and draft previews
     * @param publishingWorkflowService executes draft creation, revision edits, reviews, publishing, and withdrawals
     */
    public ContentController(IdentityService identityService,
                             FeedService feedService,
                             PublishingWorkflowService publishingWorkflowService) {
        this.identityService = identityService;
        this.feedService = feedService;
        this.publishingWorkflowService = publishingWorkflowService;
    }

    /**
     * Returns the caller's personalized, audience-filtered Discovery Feed.
     * Enforces Proposal Page 4: filters by `published_audience_id` BEFORE ranking by explicit interests.
     *
     * HTTP Method: GET /api/v1/feed
     * HTTP Status: 200 OK
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the employee
     * @param category     optional category filter (`ALL`, `LEARNING`, `COMMUNITY`, `UPDATES`)
     * @param query        optional keyword search query matching title, summary, or tags
     * @return ranked list of content card DTO maps with grounded `"WHY THIS?"` explanations
     */
    // Maps HTTP GET requests on `/api/v1/feed` to this handler method.
    @GetMapping("/feed")
    public List<Map<String, Object>> getFeed(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts optional `?category=...` query parameter.
            @RequestParam(value = "category", required = false) String category,
            // Extracts optional `?q=...` search query parameter.
            @RequestParam(value = "q", required = false) String query) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Run the 3-stage Filter -> Rank -> Explain feed pipeline for this caller.
        return feedService.getPersonalizedFeed(caller, category, query);
    }

    /**
     * Returns the full story detail for a published story.
     * Strictly requires `PUBLISHED` status and audience access to `publishedAudienceId`.
     * Unpublished drafts return 404 Not Found here.
     *
     * HTTP Method: GET /api/v1/stories/{id}
     * HTTP Status: 200 OK (or 403 Forbidden if outside audience, 404 Not Found if unpublished/withdrawn)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the employee
     * @param storyId      path variable `{id}` of the story
     * @return published story card DTO map
     */
    // Maps HTTP GET requests on `/api/v1/stories/{id}` to this handler method.
    @GetMapping("/stories/{id}")
    public Map<String, Object> getStoryDetail(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts `{id}` from `/api/v1/stories/{id}`.
            @PathVariable("id") String storyId) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Fetch the published story snapshot via FeedService.
        return feedService.getStoryDetail(caller, storyId);
    }

    /**
     * Returns a preview of the latest working revision (`currentVersion`) of a content item
     * for its author or a scoped portal editor (`REVIEWER`, `PUBLISHER`, `MODERATOR`).
     *
     * HTTP Method: GET /api/v1/content/{id}/preview
     * HTTP Status: 200 OK (or 403 Forbidden if neither author nor scoped editor, 404 Not Found)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the author or editor
     * @param contentId    path variable `{id}` of the content item
     * @return preview DTO map for `currentVersion`
     */
    // Maps HTTP GET requests on `/api/v1/content/{id}/preview` to this handler method.
    @GetMapping("/content/{id}/preview")
    public Map<String, Object> getContentPreview(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts `{id}` from `/api/v1/content/{id}/preview`.
            @PathVariable("id") String contentId) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Verify preview authorization and return the working revision preview.
        return feedService.getContentPreview(caller, contentId);
    }

    /**
     * Creates a new contribution (Revision 1) in `DRAFT` or `IN_REVIEW` state, optionally attaching
     * a voluntary colleague conversation invitation.
     *
     * HTTP Method: POST /api/v1/content
     * HTTP Status: 201 Created (or 403 if missing CREATOR role / audience access, 422 on validation errors)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the creator
     * @param payload      JSON body with `title`, `bodyText`, `audienceId`, `submitForReview`, and optional invitation fields
     * @return ResponseEntity with HTTP 201 Created and the created content card DTO
     */
    // Maps HTTP POST requests on `/api/v1/content` to this handler method.
    @PostMapping("/content")
    public ResponseEntity<Map<String, Object>> createContent(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Deserializes the contribution creation JSON body.
            @RequestBody Map<String, Object> payload) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Create the content item, Revision 1, and optional invitation in PublishingWorkflowService.
        Map<String, Object> created = publishingWorkflowService.createOrSaveContribution(caller, payload);
        // Step 3: Return HTTP 201 Created with the newly created content DTO.
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Edits an existing content item's text or target audience by creating `Revision (N+1)`,
     * automatically invalidating any prior approval on the working revision while leaving any
     * already-published snapshot untouched.
     *
     * HTTP Method: PUT /api/v1/content/{id}/revisions
     * HTTP Status: 200 OK (or 403 Forbidden, 404 Not Found, 422 Unprocessable Entity)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the author or scoped editor
     * @param contentId    path variable `{id}` of the content item being edited
     * @param payload      JSON body with updated `title`, `summary`, `bodyText`, `audienceId`, or `tagsCsv`
     * @return updated portal queue item DTO for the newly created revision
     */
    // Maps HTTP PUT requests on `/api/v1/content/{id}/revisions` to this handler method.
    @PutMapping("/content/{id}/revisions")
    public Map<String, Object> editContentAndCreateRevision(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts `{id}` of the content item.
            @PathVariable("id") String contentId,
            // Deserializes the edit fields from the JSON request body.
            @RequestBody Map<String, Object> payload) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Create Revision (N+1) and invalidate approval in PublishingWorkflowService.
        return publishingWorkflowService.editContentAndCreateNewRevision(caller, contentId, payload);
    }

    /**
     * Submits a `DRAFT` revision for editorial review (`DRAFT -> IN_REVIEW`).
     * Requires `CREATOR` role and author ownership of the content item.
     *
     * HTTP Method: POST /api/v1/content/{id}/submit
     * HTTP Status: 200 OK (or 403 Forbidden if not author, 409 Conflict if not in DRAFT state)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the creator
     * @param contentId    path variable `{id}` of the content item to submit
     * @return updated portal queue item DTO
     */
    // Maps HTTP POST requests on `/api/v1/content/{id}/submit` to this handler method.
    @PostMapping("/content/{id}/submit")
    public Map<String, Object> submitForReview(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts `{id}` of the content item.
            @PathVariable("id") String contentId) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Transition the draft revision to `IN_REVIEW`.
        return publishingWorkflowService.submitForReview(caller, contentId);
    }

    /**
     * Records a reviewer's decision (`APPROVE` or `REQUEST_CHANGES`) on an explicit `revisionNumber`.
     * Enforces `REVIEWER` role, audience access, no self-approval (`approved_by != author_id`),
     * and exact revision matching.
     *
     * HTTP Method: POST /api/v1/content/{id}/review
     * HTTP Status: 200 OK (or 403 on self-approval / missing role, 409 on stale revision, 422 on missing revisionNumber)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the reviewer
     * @param contentId    path variable `{id}` of the content item under review
     * @param payload      JSON body containing `revisionNumber`, `decision` (`APPROVE`/`REQUEST_CHANGES`), and `notes`
     * @return updated portal queue item DTO
     */
    // Maps HTTP POST requests on `/api/v1/content/{id}/review` to this handler method.
    @PostMapping("/content/{id}/review")
    public Map<String, Object> reviewRevision(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts `{id}` of the content item.
            @PathVariable("id") String contentId,
            // Deserializes the review decision payload.
            @RequestBody Map<String, Object> payload) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Validate reviewer guardrails and apply the review decision in PublishingWorkflowService.
        return publishingWorkflowService.reviewRevision(caller, contentId, payload);
    }

    /**
     * Publishes an `APPROVED` revision immediately (`IMMEDIATE`) or schedules it (`SCHEDULED`).
     * Requires `PUBLISHER` role and audience access. When published immediately, awards +20
     * Campfire recognition points to the author inside the same database transaction!
     *
     * HTTP Method: POST /api/v1/content/{id}/publish
     * HTTP Status: 200 OK (or 403 Forbidden, 409 Conflict if revision is not APPROVED)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the publisher
     * @param contentId    path variable `{id}` of the content item to publish
     * @param payload      JSON body with optional `mode` (`IMMEDIATE` / `SCHEDULED`), `scheduledFor`, and `revisionNumber`
     * @return updated portal queue item DTO
     */
    // Maps HTTP POST requests on `/api/v1/content/{id}/publish` to this handler method.
    @PostMapping("/content/{id}/publish")
    public Map<String, Object> publishOrSchedule(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts `{id}` of the content item.
            @PathVariable("id") String contentId,
            // Deserializes the publish/schedule parameters.
            @RequestBody Map<String, Object> payload) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Execute immediate publication (with synchronous recognition award) or schedule it.
        return publishingWorkflowService.publishOrSchedule(caller, contentId, payload);
    }

    /**
     * Withdraws a published content item from the employee feed (`WITHDRAWN`) and reverses
     * any recognition points awarded for that content item inside the same transaction.
     *
     * HTTP Method: POST /api/v1/content/{id}/withdraw
     * HTTP Status: 200 OK (or 403 Forbidden if caller lacks PUBLISHER/MODERATOR role or audience access)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the publisher or moderator
     * @param contentId    path variable `{id}` of the content item to withdraw
     * @param payload      optional JSON body containing `reason` for withdrawal
     * @return updated portal queue item DTO
     */
    // Maps HTTP POST requests on `/api/v1/content/{id}/withdraw` to this handler method.
    @PostMapping("/content/{id}/withdraw")
    public Map<String, Object> withdrawContent(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts `{id}` of the content item.
            @PathVariable("id") String contentId,
            // Deserializes the optional withdrawal reason body (`required = false`).
            @RequestBody(required = false) Map<String, Object> payload) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Extract the withdrawal reason or default to `"Editorial withdrawal"`.
        String reason = payload != null ? (String) payload.get("reason") : "Editorial withdrawal";
        // Step 3: Withdraw the content item and reverse recognition points atomically.
        return publishingWorkflowService.withdrawContent(caller, contentId, reason);
    }

    /**
     * Lists all content items in the Role-Based Publishing Portal queue that belong to the caller's
     * permitted audiences. Requires a portal role (`REVIEWER`, `PUBLISHER`, `MODERATOR`, or `ADMIN`).
     *
     * HTTP Method: GET /api/v1/portal/queue
     * HTTP Status: 200 OK (or 403 Forbidden for non-portal employees)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the portal user
     * @return audience-scoped list of portal queue item DTOs
     */
    // Maps HTTP GET requests on `/api/v1/portal/queue` to this handler method.
    @GetMapping("/portal/queue")
    public List<Map<String, Object>> getPortalReviewQueue(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Enforce portal access and return the caller's audience-scoped review queue.
        return publishingWorkflowService.listPortalQueue(caller);
    }

    /**
     * Lists the 50 most recent audit log events visible to the portal caller, scoped to their permitted audiences.
     *
     * HTTP Method: GET /api/v1/portal/audit-log
     * HTTP Status: 200 OK (or 403 Forbidden for non-portal employees)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the portal user
     * @return audience-scoped list of {@link AuditEventEntity} records
     */
    // Maps HTTP GET requests on `/api/v1/portal/audit-log` to this handler method.
    @GetMapping("/portal/audit-log")
    public List<AuditEventEntity> getAuditLog(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Enforce portal access and return the audience-filtered audit log.
        return publishingWorkflowService.listScopedAuditLog(caller);
    }
}
