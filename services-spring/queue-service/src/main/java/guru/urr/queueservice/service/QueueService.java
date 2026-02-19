package guru.urr.queueservice.service;

import guru.urr.queueservice.shared.client.TicketInternalClient;
import guru.urr.queueservice.shared.metrics.QueueMetrics;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final StringRedisTemplate redisTemplate;
    private final TicketInternalClient ticketInternalClient;
    private final SqsPublisher sqsPublisher;
    private final DefaultRedisScript<List> queueCheckScript;
    private final int defaultThreshold;
    private final int activeTtlSeconds;
    private final EntryTokenGenerator entryTokenGenerator;
    private final QueueMetrics queueMetrics;

    // Throughput tracking for wait estimation
    private final AtomicLong recentAdmissions = new AtomicLong(0);
    private final AtomicLong throughputWindowStart = new AtomicLong(System.currentTimeMillis());
    private static final long THROUGHPUT_WINDOW_MS = 60_000; // 1-minute window
    private static final int DEFAULT_THROUGHPUT_PER_SECOND = 50;
    private static final int MINIMUM_WAIT_SECONDS = 5;
    private static final long THROUGHPUT_MIN_DATA_MS = 5000;
    private static final int POLL_SECONDS_DEFAULT = 3;
    private static final int POLL_SECONDS_NEAR = 1;
    private static final int POLL_SECONDS_MEDIUM = 5;
    private static final int POLL_SECONDS_FAR = 10;
    private static final int POLL_SECONDS_VERY_FAR = 30;
    private static final int POLL_SECONDS_DISTANT = 60;
    private static final int POSITION_THRESHOLD_NEAR = 1_000;
    private static final int POSITION_THRESHOLD_MEDIUM = 5_000;
    private static final int POSITION_THRESHOLD_FAR = 10_000;
    private static final int POSITION_THRESHOLD_VERY_FAR = 100_000;

    public QueueService(
        StringRedisTemplate redisTemplate,
        TicketInternalClient ticketInternalClient,
        SqsPublisher sqsPublisher,
        DefaultRedisScript<List> queueCheckScript,
        QueueMetrics queueMetrics,
        EntryTokenGenerator entryTokenGenerator,
        @Value("${QUEUE_THRESHOLD:1000}") int threshold,
        @Value("${QUEUE_ACTIVE_TTL_SECONDS:600}") int activeTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.ticketInternalClient = ticketInternalClient;
        this.sqsPublisher = sqsPublisher;
        this.queueCheckScript = queueCheckScript;
        this.queueMetrics = queueMetrics;
        this.entryTokenGenerator = entryTokenGenerator;
        this.defaultThreshold = threshold;
        this.activeTtlSeconds = activeTtlSeconds;
    }

    public Map<String, Object> check(UUID eventId, String userId) {
        return check(eventId, userId, null);
    }

    public Map<String, Object> check(UUID eventId, String userId, Integer vwrPosition) {
        Map<String, Object> eventInfo = ticketInternalClient.getEventQueueInfo(eventId);

        // Single Lua call: [inQueue, inActive, position, queueSize, activeCount, threshold]
        long[] state = executeQueueCheck(eventId, userId);
        boolean inQueue = state[0] == 1;
        boolean inActive = state[1] == 1;
        int position = (int) state[2];
        int queueSize = (int) state[3];
        int activeCount = (int) state[4];
        int threshold = (int) state[5];

        if (inQueue) {
            return buildQueuedResponse(position, queueSize, eventInfo, activeCount, threshold);
        }

        if (inActive) {
            return buildActiveResponse(eventInfo, eventId, userId, activeCount, threshold);
        }

        if (queueSize > 0 || activeCount >= threshold) {
            double score = vwrPosition != null ? (double) vwrPosition : System.currentTimeMillis();
            addToQueue(eventId, userId, score);
            trackActiveEvent(eventId);
            queueMetrics.recordQueueJoined();
            position = getQueuePosition(eventId, userId);
            queueSize = getQueueSize(eventId);
            return buildQueuedResponse(position, queueSize, eventInfo, activeCount, threshold);
        }

        addActiveUser(eventId, userId);
        trackActiveEvent(eventId);
        queueMetrics.recordQueueAdmitted();
        return buildActiveResponse(eventInfo, eventId, userId, activeCount, threshold);
    }

    public Map<String, Object> status(UUID eventId, String userId) {
        // Single Lua call: [inQueue, inActive, position, queueSize, activeCount, threshold]
        long[] state = executeQueueCheck(eventId, userId);
        boolean inQueue = state[0] == 1;
        boolean inActive = state[1] == 1;
        int position = (int) state[2];
        int queueSize = (int) state[3];
        int activeCount = (int) state[4];

        if (inQueue) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "queued");
            result.put("queued", true);
            result.put("currentUsers", activeCount);
            result.put("position", position);
            result.put("peopleAhead", Math.max(0, position - 1));
            result.put("peopleBehind", Math.max(0, queueSize - position));
            result.put("estimatedWait", estimateWait(position));
            result.put("nextPoll", calculateNextPoll(position));
            result.put("queueSize", queueSize);
            return result;
        }

        if (inActive) {
            String entryToken = entryTokenGenerator.generate(eventId.toString(), userId);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "active");
            result.put("queued", false);
            result.put("currentUsers", activeCount);
            result.put("nextPoll", POLL_SECONDS_DEFAULT);
            result.put("message", "Entry allowed");
            result.put("entryToken", entryToken);
            return result;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "not_in_queue");
        result.put("queued", false);
        result.put("currentUsers", activeCount);
        result.put("nextPoll", POLL_SECONDS_DEFAULT);
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
        int eventThreshold = getThreshold(eventId);
        return Map.of(
            "eventId", eventId,
            "queueSize", queueSize,
            "currentUsers", currentUsers,
            "threshold", eventThreshold,
            "available", Math.max(0, eventThreshold - currentUsers)
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
                                                     Map<String, Object> eventInfo,
                                                     int activeCount, int threshold) {
        Map<String, Object> result = new HashMap<>();
        result.put("queued", true);
        result.put("status", "queued");
        result.put("position", position);
        result.put("peopleAhead", Math.max(0, position - 1));
        result.put("peopleBehind", Math.max(0, queueSize - position));
        result.put("estimatedWait", estimateWait(position));
        result.put("nextPoll", calculateNextPoll(position));
        result.put("threshold", threshold);
        result.put("currentUsers", activeCount);
        result.put("eventInfo", eventInfo);
        return result;
    }

    private Map<String, Object> buildActiveResponse(Map<String, Object> eventInfo, UUID eventId,
                                                     String userId, int activeCount, int threshold) {
        String entryToken = entryTokenGenerator.generate(eventId.toString(), userId);

        Map<String, Object> result = new HashMap<>();
        result.put("queued", false);
        result.put("status", "active");
        result.put("currentUsers", activeCount);
        result.put("threshold", threshold);
        result.put("nextPoll", POLL_SECONDS_DEFAULT);
        result.put("eventInfo", eventInfo);
        result.put("entryToken", entryToken);

        // Publish admission to SQS FIFO (fire-and-forget, fallback on failure)
        sqsPublisher.publishAdmission(eventId, userId, entryToken);

        return result;
    }

    // -- Lua script execution --

    @SuppressWarnings("unchecked")
    private long[] executeQueueCheck(UUID eventId, String userId) {
        List<String> keys = Arrays.asList(
            queueKey(eventId),
            activeKey(eventId),
            queueSeenKey(eventId),
            activeSeenKey(eventId),
            "{" + eventId + "}:threshold"
        );
        long now = System.currentTimeMillis();
        long activeTtlMs = activeTtlSeconds * 1000L;

        List<Long> result = redisTemplate.execute(
            queueCheckScript,
            keys,
            userId,
            String.valueOf(now),
            String.valueOf(defaultThreshold),
            String.valueOf(activeTtlMs)
        );

        if (result == null || result.size() < 6) {
            return new long[]{0, 0, 0, 0, 0, defaultThreshold};
        }
        return new long[]{
            result.get(0), result.get(1), result.get(2),
            result.get(3), result.get(4), result.get(5)
        };
    }

    // -- Dynamic polling interval --

    private int calculateNextPoll(int position) {
        if (position <= 0) return POLL_SECONDS_DEFAULT;
        if (position <= POSITION_THRESHOLD_NEAR) return POLL_SECONDS_NEAR;
        if (position <= POSITION_THRESHOLD_MEDIUM) return POLL_SECONDS_MEDIUM;
        if (position <= POSITION_THRESHOLD_FAR) return POLL_SECONDS_FAR;
        if (position <= POSITION_THRESHOLD_VERY_FAR) return POLL_SECONDS_VERY_FAR;
        return POLL_SECONDS_DISTANT;
    }

    // -- Throughput-based wait estimation --

    private int estimateWait(int position) {
        if (position <= 0) return 0;

        long now = System.currentTimeMillis();
        long elapsed = now - throughputWindowStart.get();
        long admissions = recentAdmissions.get();

        if (elapsed < THROUGHPUT_MIN_DATA_MS || admissions <= 0) {
            // Before throughput data is available, assume default processing rate
            // and cap at a reasonable maximum to avoid alarming overestimates
            return Math.max(position / DEFAULT_THROUGHPUT_PER_SECOND, MINIMUM_WAIT_SECONDS);
        }

        double throughputPerSecond = (admissions * 1000.0) / elapsed;
        if (throughputPerSecond <= 0) {
            return Math.max(position / DEFAULT_THROUGHPUT_PER_SECOND, MINIMUM_WAIT_SECONDS);
        }
        return (int) Math.ceil(position / throughputPerSecond);
    }

    // -- Active event tracking (avoid KEYS command) --

    private void trackActiveEvent(UUID eventId) {
        try {
            redisTemplate.opsForSet().add("queue:active-events", eventId.toString());
        } catch (Exception e) {
            log.debug("Failed to track active event {}: {}", eventId, e.getMessage());
        }
    }

    // -- Per-event threshold (#25) --

    private int getThreshold(UUID eventId) {
        String custom = redisTemplate.opsForValue().get("{" + eventId + "}:threshold");
        return custom != null ? Integer.parseInt(custom) : defaultThreshold;
    }

    public void setThreshold(UUID eventId, int threshold) {
        redisTemplate.opsForValue().set("{" + eventId + "}:threshold", String.valueOf(threshold));
    }

    // -- Redis key helpers (hash-tagged for Redis Cluster slot affinity) --

    private String queueKey(UUID eventId) {
        return "{" + eventId + "}:queue";
    }

    private String activeKey(UUID eventId) {
        return "{" + eventId + "}:active";
    }

    private String queueSeenKey(UUID eventId) {
        return "{" + eventId + "}:seen";
    }

    private String activeSeenKey(UUID eventId) {
        return "{" + eventId + "}:active-seen";
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
        addToQueue(eventId, userId, System.currentTimeMillis());
    }

    private void addToQueue(UUID eventId, String userId, double score) {
        redisTemplate.opsForZSet().add(queueKey(eventId), userId, score);
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
        } catch (Exception e) {
            log.warn("Failed to touch queue heartbeat for user {} on event {}: {}", userId, eventId, e.getMessage());
        }
    }

    private void touchActiveUser(UUID eventId, String userId) {
        try {
            redisTemplate.opsForZSet().add(activeSeenKey(eventId), userId, System.currentTimeMillis());
            long newExpiry = System.currentTimeMillis() + (activeTtlSeconds * 1000L);
            redisTemplate.opsForZSet().add(activeKey(eventId), userId, newExpiry);
        } catch (Exception e) {
            log.warn("Failed to refresh active session for user {} on event {}: {}", userId, eventId, e.getMessage());
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
