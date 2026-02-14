package com.tiketi.queueservice.service;

import com.tiketi.queueservice.shared.client.TicketInternalClient;
import com.tiketi.queueservice.shared.metrics.QueueMetrics;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final StringRedisTemplate redisTemplate;
    private final TicketInternalClient ticketInternalClient;
    private final SqsPublisher sqsPublisher;
    private final int threshold;
    private final int activeTtlSeconds;
    private final SecretKey entryTokenKey;
    private final int entryTokenTtlSeconds;
    private final QueueMetrics queueMetrics;

    // Throughput tracking for wait estimation
    private final AtomicLong recentAdmissions = new AtomicLong(0);
    private final AtomicLong throughputWindowStart = new AtomicLong(System.currentTimeMillis());
    private static final long THROUGHPUT_WINDOW_MS = 60_000; // 1-minute window

    public QueueService(
        StringRedisTemplate redisTemplate,
        TicketInternalClient ticketInternalClient,
        SqsPublisher sqsPublisher,
        QueueMetrics queueMetrics,
        @Value("${QUEUE_THRESHOLD:1000}") int threshold,
        @Value("${QUEUE_ACTIVE_TTL_SECONDS:600}") int activeTtlSeconds,
        @Value("${queue.entry-token.secret}") String entryTokenSecret,
        @Value("${queue.entry-token.ttl-seconds:600}") int entryTokenTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.ticketInternalClient = ticketInternalClient;
        this.sqsPublisher = sqsPublisher;
        this.queueMetrics = queueMetrics;
        this.threshold = threshold;
        this.activeTtlSeconds = activeTtlSeconds;
        this.entryTokenKey = Keys.hmacShaKeyFor(entryTokenSecret.getBytes(StandardCharsets.UTF_8));
        this.entryTokenTtlSeconds = entryTokenTtlSeconds;
    }

    public Map<String, Object> check(UUID eventId, String userId) {
        Map<String, Object> eventInfo = ticketInternalClient.getEventQueueInfo(eventId);

        if (isInQueue(eventId, userId)) {
            touchQueueUser(eventId, userId);
            int position = getQueuePosition(eventId, userId);
            int queueSize = getQueueSize(eventId);
            return buildQueuedResponse(position, queueSize, eventInfo, eventId);
        }

        if (isActiveUser(eventId, userId)) {
            touchActiveUser(eventId, userId);
            return buildActiveResponse(eventInfo, eventId, userId);
        }

        int currentUsers = getCurrentUsers(eventId);
        int queueSize = getQueueSize(eventId);

        if (queueSize > 0 || currentUsers >= threshold) {
            addToQueue(eventId, userId);
            trackActiveEvent(eventId);
            queueMetrics.recordQueueJoined();
            int position = getQueuePosition(eventId, userId);
            queueSize = getQueueSize(eventId);
            return buildQueuedResponse(position, queueSize, eventInfo, eventId);
        }

        addActiveUser(eventId, userId);
        trackActiveEvent(eventId);
        queueMetrics.recordQueueAdmitted();
        return buildActiveResponse(eventInfo, eventId, userId);
    }

    public Map<String, Object> status(UUID eventId, String userId) {
        if (isInQueue(eventId, userId)) {
            touchQueueUser(eventId, userId);
            int position = getQueuePosition(eventId, userId);
            int queueSize = getQueueSize(eventId);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "queued");
            result.put("queued", true);
            result.put("currentUsers", getCurrentUsers(eventId));
            result.put("position", position);
            result.put("peopleAhead", Math.max(0, position - 1));
            result.put("peopleBehind", Math.max(0, queueSize - position));
            result.put("estimatedWait", estimateWait(position));
            result.put("nextPoll", calculateNextPoll(position));
            result.put("queueSize", queueSize);
            return result;
        }

        if (isActiveUser(eventId, userId)) {
            touchActiveUser(eventId, userId);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "active");
            result.put("queued", false);
            result.put("currentUsers", getCurrentUsers(eventId));
            result.put("nextPoll", 3);
            result.put("message", "Entry allowed");
            return result;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "not_in_queue");
        result.put("queued", false);
        result.put("currentUsers", getCurrentUsers(eventId));
        result.put("nextPoll", 3);
        result.put("message", "Not in queue");
        return result;
    }

    public Map<String, Object> heartbeat(UUID eventId, String userId) {
        if (isInQueue(eventId, userId)) {
            touchQueueUser(eventId, userId);
            return Map.of("status", "queued", "queued", true);
        }
        if (isActiveUser(eventId, userId)) {
            touchActiveUser(eventId, userId);
            return Map.of("status", "active", "queued", false);
        }
        return Map.of("status", "not_in_queue", "queued", false);
    }

    public Map<String, Object> leave(UUID eventId, String userId) {
        removeFromQueue(eventId, userId);
        removeActiveUser(eventId, userId);
        queueMetrics.recordQueueLeft();
        return Map.of("message", "Left queue");
    }

    public Map<String, Object> admin(UUID eventId) {
        int currentUsers = getCurrentUsers(eventId);
        int queueSize = getQueueSize(eventId);
        return Map.of(
            "eventId", eventId,
            "queueSize", queueSize,
            "currentUsers", currentUsers,
            "threshold", threshold,
            "available", Math.max(0, threshold - currentUsers)
        );
    }

    public Map<String, Object> clear(UUID eventId) {
        clearQueue(eventId);
        return Map.of("message", "Queue cleared");
    }

    /** Called by AdmissionWorkerService to record throughput */
    public synchronized void recordAdmissions(int count) {
        long now = System.currentTimeMillis();
        long windowStart = throughputWindowStart.get();
        if (now - windowStart > THROUGHPUT_WINDOW_MS) {
            recentAdmissions.set(count);
            throughputWindowStart.set(now);
        } else {
            recentAdmissions.addAndGet(count);
        }
    }

    // -- Response builders --

    private Map<String, Object> buildQueuedResponse(int position, int queueSize,
                                                     Map<String, Object> eventInfo, UUID eventId) {
        Map<String, Object> result = new HashMap<>();
        result.put("queued", true);
        result.put("status", "queued");
        result.put("position", position);
        result.put("peopleAhead", Math.max(0, position - 1));
        result.put("peopleBehind", Math.max(0, queueSize - position));
        result.put("estimatedWait", estimateWait(position));
        result.put("nextPoll", calculateNextPoll(position));
        result.put("threshold", threshold);
        result.put("currentUsers", getCurrentUsers(eventId));
        result.put("eventInfo", eventInfo);
        return result;
    }

    private Map<String, Object> buildActiveResponse(Map<String, Object> eventInfo, UUID eventId, String userId) {
        String entryToken = generateEntryToken(eventId.toString(), userId);

        Map<String, Object> result = new HashMap<>();
        result.put("queued", false);
        result.put("status", "active");
        result.put("currentUsers", getCurrentUsers(eventId));
        result.put("threshold", threshold);
        result.put("nextPoll", 3);
        result.put("eventInfo", eventInfo);
        result.put("entryToken", entryToken);

        // Publish admission to SQS FIFO (fire-and-forget, fallback on failure)
        sqsPublisher.publishAdmission(eventId, userId, entryToken);

        return result;
    }

    private String generateEntryToken(String eventId, String userId) {
        long nowMs = System.currentTimeMillis();
        Date issuedAt = new Date(nowMs);
        Date expiration = new Date(nowMs + (entryTokenTtlSeconds * 1000L));

        return Jwts.builder()
            .subject(eventId)
            .claim("uid", userId)
            .issuedAt(issuedAt)
            .expiration(expiration)
            .signWith(entryTokenKey)
            .compact();
    }

    // -- Dynamic polling interval --

    private int calculateNextPoll(int position) {
        if (position <= 0) return 3;
        if (position <= 1000) return 1;
        if (position <= 5000) return 5;
        if (position <= 10000) return 10;
        if (position <= 100000) return 30;
        return 60;
    }

    // -- Throughput-based wait estimation --

    private int estimateWait(int position) {
        if (position <= 0) return 0;

        long now = System.currentTimeMillis();
        long elapsed = now - throughputWindowStart.get();
        long admissions = recentAdmissions.get();

        if (elapsed < 5000 || admissions <= 0) {
            return Math.max(position * 30, 0);
        }

        double throughputPerSecond = (admissions * 1000.0) / elapsed;
        if (throughputPerSecond <= 0) {
            return Math.max(position * 30, 0);
        }
        return (int) Math.ceil(position / throughputPerSecond);
    }

    // -- Active event tracking (avoid KEYS command) --

    private void trackActiveEvent(UUID eventId) {
        try {
            redisTemplate.opsForSet().add("queue:active-events", eventId.toString());
        } catch (Exception ignored) {
        }
    }

    // -- Redis key helpers --

    private String queueKey(UUID eventId) {
        return "queue:" + eventId;
    }

    private String activeKey(UUID eventId) {
        return "active:" + eventId;
    }

    private String queueSeenKey(UUID eventId) {
        return "queue:seen:" + eventId;
    }

    private String activeSeenKey(UUID eventId) {
        return "active:seen:" + eventId;
    }

    // -- Active users: ZSET with expiry-timestamp scores --

    private boolean isActiveUser(UUID eventId, String userId) {
        Double score = redisTemplate.opsForZSet().score(activeKey(eventId), userId);
        if (score == null) return false;
        return score > System.currentTimeMillis();
    }

    private int getCurrentUsers(UUID eventId) {
        Long count = redisTemplate.opsForZSet().count(activeKey(eventId),
            System.currentTimeMillis(), Double.POSITIVE_INFINITY);
        return count == null ? 0 : count.intValue();
    }

    private void addActiveUser(UUID eventId, String userId) {
        long expiryScore = System.currentTimeMillis() + (activeTtlSeconds * 1000L);
        redisTemplate.opsForZSet().add(activeKey(eventId), userId, expiryScore);
        touchActiveUser(eventId, userId);
    }

    private void removeActiveUser(UUID eventId, String userId) {
        redisTemplate.opsForZSet().remove(activeKey(eventId), userId);
        redisTemplate.opsForZSet().remove(activeSeenKey(eventId), userId);
    }

    // -- Queue operations (ZSET) --

    private boolean isInQueue(UUID eventId, String userId) {
        Double score = redisTemplate.opsForZSet().score(queueKey(eventId), userId);
        return score != null;
    }

    private int getQueuePosition(UUID eventId, String userId) {
        Long rank = redisTemplate.opsForZSet().rank(queueKey(eventId), userId);
        return rank == null ? 0 : rank.intValue() + 1;
    }

    private int getQueueSize(UUID eventId) {
        Long size = redisTemplate.opsForZSet().size(queueKey(eventId));
        return size == null ? 0 : size.intValue();
    }

    private void addToQueue(UUID eventId, String userId) {
        redisTemplate.opsForZSet().add(queueKey(eventId), userId, System.currentTimeMillis());
        touchQueueUser(eventId, userId);
    }

    private void removeFromQueue(UUID eventId, String userId) {
        redisTemplate.opsForZSet().remove(queueKey(eventId), userId);
        redisTemplate.opsForZSet().remove(queueSeenKey(eventId), userId);
    }

    // -- Heartbeat touch --

    private void touchQueueUser(UUID eventId, String userId) {
        try {
            redisTemplate.opsForZSet().add(queueSeenKey(eventId), userId, System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    private void touchActiveUser(UUID eventId, String userId) {
        try {
            redisTemplate.opsForZSet().add(activeSeenKey(eventId), userId, System.currentTimeMillis());
            long newExpiry = System.currentTimeMillis() + (activeTtlSeconds * 1000L);
            redisTemplate.opsForZSet().add(activeKey(eventId), userId, newExpiry);
        } catch (Exception ignored) {
        }
    }

    // -- Clear --

    private void clearQueue(UUID eventId) {
        redisTemplate.delete(List.of(
            queueKey(eventId), activeKey(eventId),
            queueSeenKey(eventId), activeSeenKey(eventId)));
        redisTemplate.opsForSet().remove("queue:active-events", eventId.toString());
    }
}
