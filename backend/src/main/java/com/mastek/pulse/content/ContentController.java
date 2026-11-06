package com.mastek.pulse.content;

import com.mastek.pulse.common.AuditEventEntity;
import com.mastek.pulse.common.AuditEventRepository;
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
 * Journey: Discovery Feed, Story Details & Role-Based Publishing Portal
 *          (Proposal Pages 6, 8, 10, 11).
 * Why it is designed this way:
 *   Exposes the REST endpoints for reading the personalized Pulse feed (`/api/v1/feed`),
 *   viewing a specific story (`/api/v1/stories/{id}`), creating/editing drafts,
 *   and performing role-enforced review, publishing, and withdrawal actions.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private final IdentityService identityService;
    private final FeedService feedService;
    private final PublishingWorkflowService publishingWorkflowService;
    private final AuditEventRepository auditEventRepository;

    public ContentController(IdentityService identityService,
                             FeedService feedService,
                             PublishingWorkflowService publishingWorkflowService,
                             AuditEventRepository auditEventRepository) {
        this.identityService = identityService;
        this.feedService = feedService;
        this.publishingWorkflowService = publishingWorkflowService;
        this.auditEventRepository = auditEventRepository;
    }

    @GetMapping("/feed")
    public List<Map<String, Object>> getFeed(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "q", required = false) String query) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return feedService.getPersonalizedFeed(caller, category, query);
    }

    @GetMapping("/stories/{id}")
    public Map<String, Object> getStoryDetail(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String storyId) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return feedService.getStoryDetail(caller, storyId);
    }

    @PostMapping("/content")
    public ResponseEntity<Map<String, Object>> createContent(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        Map<String, Object> created = publishingWorkflowService.createOrSaveContribution(caller, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/content/{id}/revisions")
    public Map<String, Object> editContentAndCreateRevision(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String contentId,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return publishingWorkflowService.editContentAndCreateNewRevision(caller, contentId, payload);
    }

    @PostMapping("/content/{id}/submit")
    public Map<String, Object> submitForReview(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String contentId) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return publishingWorkflowService.submitForReview(caller, contentId);
    }

    @PostMapping("/content/{id}/review")
    public Map<String, Object> reviewRevision(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String contentId,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return publishingWorkflowService.reviewRevision(caller, contentId, payload);
    }

    @PostMapping("/content/{id}/publish")
    public Map<String, Object> publishOrSchedule(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String contentId,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return publishingWorkflowService.publishOrSchedule(caller, contentId, payload);
    }

    @PostMapping("/content/{id}/withdraw")
    public Map<String, Object> withdrawContent(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String contentId,
            @RequestBody(required = false) Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        String reason = payload != null ? (String) payload.get("reason") : "Editorial withdrawal";
        return publishingWorkflowService.withdrawContent(caller, contentId, reason);
    }

    @GetMapping("/portal/queue")
    public List<Map<String, Object>> getPortalReviewQueue(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return publishingWorkflowService.listPortalQueue();
    }

    @GetMapping("/portal/audit-log")
    public List<AuditEventEntity> getAuditLog(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        identityService.authenticate(userIdHeader);
        return auditEventRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
