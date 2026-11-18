package com.mastek.pulse.listen;

import com.mastek.pulse.common.IdentityService;
import com.mastek.pulse.common.PulseException;
import com.mastek.pulse.common.UserEntity;
import com.mastek.pulse.common.UserRepository;
import com.mastek.pulse.content.ContentItemEntity;
import com.mastek.pulse.content.ContentItemRepository;
import com.mastek.pulse.content.ContentRevisionEntity;
import com.mastek.pulse.content.ContentRevisionRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.*;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ListenController.java
 * Journey: Supporting Journey A ("Listen to Mastek Radio and Culture Cast,
 *          with transcripts and playback resume" - Proposal Pages 3, 5, 9, 10, 14).
 *
 * Why it is designed this way:
 *   1. Enforces "Audio access follows the source audience" (Page 5): before returning
 *      any Mastek Radio queue or Culture Cast episode, we filter by the caller's
 *      permitted `audience_id`.
 *   2. Returns short-lived signed audio URLs (`/api/v1/media/stream/{id}?sig=...`)
 *      alongside full readable transcripts, chapter markers, and related story IDs.
 *   3. Persists cross-device playback progress via `PUT /api/v1/listen/progress`
 *      so pausing on Mobile resumes at the exact second on Employee Web.
 *   4. Synthesizes a clean, valid PCM WAV audio stream on `/api/v1/media/stream/{id}`
 *      so HTML5 `<audio>` and React Native `expo-av` play real audio out-of-the-box!
 * ============================================================================
 */
// @RestController marks this class as a Spring MVC REST controller returning JSON (or binary WAV bytes).
@RestController
// @RequestMapping("/api/v1") prefixes all routes in this controller with `/api/v1`.
@RequestMapping("/api/v1")
public class ListenController {

    // Service for authenticating the caller and checking audience membership.
    private final IdentityService identityService;

    // Repository for querying `PUBLISHED` audio items (`RADIO_QUEUE` and `CULTURE_CAST`).
    private final ContentItemRepository contentItemRepository;

    // Repository for loading the published revision's audio metadata, transcript, and chapters.
    private final ContentRevisionRepository contentRevisionRepository;

    // Repository for reading and persisting per-user cross-device playback progress (`playback_progress`).
    private final PlaybackProgressRepository playbackProgressRepository;

    // Repository for resolving the audio host's display name.
    private final UserRepository userRepository;

    /**
     * Constructor injection of all required collaborators for the Listen experience.
     */
    public ListenController(IdentityService identityService,
                            ContentItemRepository contentItemRepository,
                            ContentRevisionRepository contentRevisionRepository,
                            PlaybackProgressRepository playbackProgressRepository,
                            UserRepository userRepository) {
        this.identityService = identityService;
        this.contentItemRepository = contentItemRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.playbackProgressRepository = playbackProgressRepository;
        this.userRepository = userRepository;
    }

    /**
     * Lists all published Mastek Radio queues (`contentType == "RADIO_QUEUE"`) permitted for the caller's audiences,
     * enriched with the caller's saved resume position (`resumePositionSeconds`).
     *
     * HTTP Method: GET /api/v1/listen/radio
     * HTTP Status: 200 OK
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the listener
     * @return list of Mastek Radio queue DTO maps
     */
    @GetMapping("/listen/radio")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMastekRadioQueues(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Query published `RADIO_QUEUE` items filtered by the caller's permitted audiences.
        return getAudioItemsByType(caller, "RADIO_QUEUE");
    }

    /**
     * Lists all published Culture Cast episodes (`contentType == "CULTURE_CAST"`) permitted for the caller's audiences,
     * enriched with the caller's saved resume position, full transcript, chapters, and linked `relatedStoryId`.
     *
     * HTTP Method: GET /api/v1/listen/culture-cast
     * HTTP Status: 200 OK
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the listener
     * @return list of Culture Cast episode DTO maps
     */
    @GetMapping("/listen/culture-cast")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCultureCastEpisodes(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Query published `CULTURE_CAST` episodes filtered by the caller's permitted audiences.
        return getAudioItemsByType(caller, "CULTURE_CAST");
    }

    /**
     * Saves the caller's current listening position (`positionSeconds`) for a Mastek Radio queue
     * or Culture Cast episode so they can resume seamlessly across Mobile and Employee Web.
     *
     * HTTP Method: PUT /api/v1/listen/progress
     * HTTP Status: 200 OK (or 403 Forbidden if outside audience, 404 Not Found, 422 Unprocessable Entity)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the listener
     * @param payload      JSON body containing `contentId`, `positionSeconds`, and `durationSeconds`
     * @return saved playback progress summary map
     */
    @PutMapping("/listen/progress")
    @Transactional
    public Map<String, Object> savePlaybackProgress(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Validate that `contentId` is present in the JSON body.
        String contentId = (String) payload.get("contentId");
        if (contentId == null || contentId.isBlank()) {
            throw PulseException.unprocessable("422: contentId is required.");
        }
        // Step 3: Load the audio content item and verify the caller has audience access to it.
        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Audio item not found: " + contentId));

        identityService.requireAudienceAccess(caller, item.getAudienceId());

        // Step 4: Clamp `positionSeconds >= 0` and `durationSeconds >= 1`.
        int positionSeconds = payload.get("positionSeconds") instanceof Number n ? Math.max(0, n.intValue()) : 0;
        int durationSeconds = payload.get("durationSeconds") instanceof Number n ? Math.max(1, n.intValue()) : 480;

        // Step 5: Upsert the composite-keyed `PlaybackProgressEntity(userId, contentId)` row.
        PlaybackProgressEntity progress = playbackProgressRepository
                .findById(new PlaybackProgressEntity.PlaybackProgressId(caller.getId(), contentId))
                .orElseGet(() -> {
                    PlaybackProgressEntity p = new PlaybackProgressEntity();
                    p.setUserId(caller.getId());
                    p.setContentId(contentId);
                    return p;
                });

        progress.setPositionSeconds(positionSeconds);
        progress.setDurationSeconds(durationSeconds);
        progress.setUpdatedAt(Instant.now());
        playbackProgressRepository.save(progress);

        // Step 6: Return the saved playback progress metadata.
        return Map.of(
                "userId", caller.getId(),
                "contentId", contentId,
                "positionSeconds", progress.getPositionSeconds(),
                "durationSeconds", progress.getDurationSeconds(),
                "updatedAt", progress.getUpdatedAt().toString()
        );
    }

    /**
     * Authorizes and streams audio for a short-lived signed URL.
     * Generates a valid PCM WAV ambient chord stream in memory so both browser
     * `<audio>` and React Native `expo-av` play real audio with zero external binary dependencies.
     *
     * HTTP Method: GET /api/v1/media/stream/{contentId}
     * HTTP Status: 200 OK (`audio/wav`) or 401 Unauthorized if `sig=expired`, 403 Forbidden if outside audience
     *
     * @param contentId    path variable `{contentId}` of the audio item
     * @param sig          query parameter `?sig=...` representing the short-lived playback signature
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the listener
     * @return binary WAV audio stream with private 15-minute cache headers
     */
    @GetMapping(value = "/media/stream/{contentId}", produces = "audio/wav")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> streamSignedAudio(
            @PathVariable("contentId") String contentId,
            @RequestParam(value = "sig", required = false) String sig,
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Guardrail 1: Simulate short-lived signed playback URL expiration (Proposal Page 5 & 9).
        if ("expired".equalsIgnoreCase(sig)) {
            throw PulseException.unauthorized("401: Signed media playback link has expired. Reauthorize from API.");
        }
        // Guardrail 2: Authenticate the caller and verify they belong to the audio item's audience.
        UserEntity caller = identityService.authenticate(userIdHeader);
        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Media item not found: " + contentId));
        identityService.requireAudienceAccess(caller, item.getAudienceId());

        // Synthesize an 8-second pleasant A-major ambient chord WAV file in memory and stream it with HTTP 200.
        byte[] wavBytes = generatePleasantDemoWav(8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=900")
                .body(wavBytes);
    }

    /**
     * Internal helper that fetches published audio items of the requested `contentType` (`RADIO_QUEUE` or `CULTURE_CAST`),
     * filters them by the caller's permitted audiences, and joins them with the caller's saved `PlaybackProgressEntity`.
     */
    private List<Map<String, Object>> getAudioItemsByType(UserEntity caller, String contentType) {
        // Step 1: Resolve the caller's permitted audience IDs and query matching `PUBLISHED` audio items.
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());
        List<ContentItemEntity> items = contentItemRepository
                .findByAudienceIdInAndContentTypeAndStatusOrderByPublishedAtDesc(permittedAudiences, contentType, "PUBLISHED");

        // Step 2: Load all saved playback progress rows for this caller into an O(1) lookup map keyed by `contentId`.
        Map<String, PlaybackProgressEntity> progressMap = new HashMap<>();
        playbackProgressRepository.findByUserIdOrderByUpdatedAtDesc(caller.getId())
                .forEach(p -> progressMap.put(p.getContentId(), p));

        // Step 3: Build the response DTO list combining item metadata, published revision transcript/chapters, and resume position.
        List<Map<String, Object>> result = new ArrayList<>();
        for (ContentItemEntity item : items) {
            int ver = item.getPublishedVersion() != null ? item.getPublishedVersion() : item.getCurrentVersion();
            Optional<ContentRevisionEntity> revOpt = contentRevisionRepository.findByContentIdAndVersionNumber(item.getId(), ver);
            if (revOpt.isEmpty()) continue;
            ContentRevisionEntity rev = revOpt.get();
            UserEntity host = userRepository.findById(item.getAuthorId()).orElse(null);
            PlaybackProgressEntity prog = progressMap.get(item.getId());

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", item.getId());
            dto.put("contentType", item.getContentType());
            dto.put("title", rev.getTitle());
            dto.put("summary", rev.getSummary());
            dto.put("bodyText", rev.getBodyText());
            dto.put("hostId", item.getAuthorId());
            dto.put("hostName", host != null ? host.getDisplayName() : item.getAuthorId());
            dto.put("durationSeconds", rev.getDurationSeconds() != null ? rev.getDurationSeconds() : 480);
            dto.put("resumePositionSeconds", prog != null ? prog.getPositionSeconds() : 0);
            dto.put("transcriptText", rev.getTranscriptText());
            dto.put("chaptersJson", rev.getChaptersJson());
            dto.put("relatedStoryId", rev.getRelatedStoryId());
            dto.put("signedAudioUrl", "/api/v1/media/stream/" + item.getId() + "?sig=signed-demo-token&exp=900");
            dto.put("publishedVersion", ver);
            result.add(dto);
        }
        return result;
    }

    /**
     * Synthesizes a valid 16-bit mono 8000 Hz RIFF/WAVE audio file in memory containing a warm
     * A-major triad (`440 Hz`, `554.37 Hz`, `659.25 Hz`) with a smooth attack/decay envelope.
     *
     * @param seconds duration of the synthesized WAV audio in seconds
     * @return complete byte array of the `.wav` file (44-byte RIFF header + PCM samples)
     */
    private byte[] generatePleasantDemoWav(int seconds) {
        int sampleRate = 8000;
        int numSamples = sampleRate * seconds;
        int byteRate = sampleRate * 2;
        int dataSize = numSamples * 2;

        // Allocate a little-endian ByteBuffer for the 44-byte standard RIFF/WAVE header + 16-bit PCM sample payload.
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1); // PCM format = 1
        buffer.putShort((short) 1); // Mono channels = 1
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) 2); // Block align = 2 bytes per sample
        buffer.putShort((short) 16); // Bits per sample = 16
        buffer.put("data".getBytes());
        buffer.putInt(dataSize);

        // Generate 16-bit signed PCM samples for a warm A4-C#5-E5 chord with soft fade-in and fade-out.
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            double envelope = Math.min(1.0, i / 800.0) * Math.max(0.15, 1.0 - (t / (seconds + 2.0)));
            double wave = 0.45 * Math.sin(2.0 * Math.PI * 440.0 * t)
                        + 0.30 * Math.sin(2.0 * Math.PI * 554.37 * t)
                        + 0.25 * Math.sin(2.0 * Math.PI * 659.25 * t);
            short sample = (short) (wave * envelope * 6000);
            buffer.putShort(sample);
        }
        return buffer.array();
    }
}
