package guru.urr.queueservice.service;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AdmissionWorkerService {

    private static final Logger log = LoggerFactory.getLogger(AdmissionWorkerService.class);
    private static final int CLEANUP_BATCH_SIZE = 1000;
    private static final int CLEANUP_BATCH_DELAY_MS = 100;
    private static final int ADMISSION_LOCK_TIMEOUT_SECONDS = 4;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> admissionScript;
    private final DefaultRedisScript<List> staleCleanupScript;
    private final QueueService queueService;
    private final int defaultThreshold;
    private final int activeTtlSeconds;
    private final int seenTtlSeconds;
    private final int admitBatchSize;

    public AdmissionWorkerService(
        StringRedisTemplate redisTemplate,
        DefaultRedisScript<List> admissionScript,
        DefaultRedisScript<List> staleCleanupScript,
        QueueService queueService,
        @Value("${QUEUE_THRESHOLD:1000}") int threshold,
        @Value("${QUEUE_ACTIVE_TTL_SECONDS:600}") int activeTtlSeconds,
        @Value("${QUEUE_SEEN_TTL_SECONDS:600}") int seenTtlSeconds,
        @Value("${queue.admission.batch-size:100}") int admitBatchSize
    ) {
        this.redisTemplate = redisTemplate;
        this.admissionScript = admissionScript;
        this.staleCleanupScript = staleCleanupScript;
        this.queueService = queueService;
        this.defaultThreshold = threshold;
        this.activeTtlSeconds = activeTtlSeconds;
        this.seenTtlSeconds = seenTtlSeconds;
        this.admitBatchSize = admitBatchSize;
    }

    @Scheduled(fixedDelayString = "${queue.admission.interval-ms:1000}")
    public void admitUsers() {
        Set<String> activeEvents = null;
        try {
            activeEvents = redisTemplate.opsForSet().members("queue:active-events");
        } catch (Exception ex) {
            log.error("Failed to get active events from Redis, skipping admission cycle", ex);
            return;
        }

        if (activeEvents == null || activeEvents.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long activeTtlMs = activeTtlSeconds * 1000L;

        for (String eventId : activeEvents) {
            String lockKey = "admission:lock:" + eventId;
            Boolean acquired = false;

            try {
                acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                    java.time.Duration.ofSeconds(ADMISSION_LOCK_TIMEOUT_SECONDS));

                if (acquired == null || !acquired) {
                    log.debug("Skipping event {} - lock held by another worker", eventId);
                    continue;
                }

                int eventThreshold = getThreshold(eventId);

                @SuppressWarnings("unchecked")
                List<Object> result = redisTemplate.execute(
                    admissionScript,
                    List.of(
                        "{" + eventId + "}:queue",
                        "{" + eventId + "}:active",
                        "{" + eventId + "}:seen"
                    ),
                    String.valueOf(admitBatchSize),
                    String.valueOf(now),
                    String.valueOf(activeTtlMs),
                    String.valueOf(eventThreshold)
                );

                if (result != null && !result.isEmpty()) {
                    int admitted = ((Number) result.get(0)).intValue();
                    if (admitted > 0) {
                        queueService.recordAdmissions(admitted);
                        log.info("Admitted {} users for event {}", admitted, eventId);
                    }
                }

                Long queueSize = redisTemplate.opsForZSet().size("{" + eventId + "}:queue");
                if (queueSize != null && queueSize == 0) {
                    Long activeSize = redisTemplate.opsForZSet().count("{" + eventId + "}:active", now, Double.POSITIVE_INFINITY);
                    if (activeSize != null && activeSize == 0) {
                        redisTemplate.opsForSet().remove("queue:active-events", eventId);
                    }
                }
            } catch (Exception ex) {
                log.error("Admission script failed for event {}", eventId, ex);
            } finally {
                if (acquired != null && acquired) {
                    try {
                        redisTemplate.delete(lockKey);
                    } catch (Exception ex) {
                        log.error("Failed to release admission lock for event {}, may block future admissions", eventId, ex);
                    }
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${queue.stale-cleanup.interval-ms:30000}")
    public void cleanupStaleUsers() {
        Set<String> activeEvents = null;
        try {
            activeEvents = redisTemplate.opsForSet().members("queue:active-events");
        } catch (Exception ex) {
            log.error("Failed to get active events from Redis for cleanup", ex);
            return;
        }

        if (activeEvents == null || activeEvents.isEmpty()) {
            return;
        }

        long cutoff = System.currentTimeMillis() - (seenTtlSeconds * 1000L);
        long activeSeenCutoff = System.currentTimeMillis() - (activeTtlSeconds * 1000L);

        for (String eventId : activeEvents) {
            boolean hasMore = true;
            int totalRemoved = 0;

            while (hasMore) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Object> result = redisTemplate.execute(
                        staleCleanupScript,
                        List.of(
                            "{" + eventId + "}:seen",
                            "{" + eventId + "}:queue"
                        ),
                        String.valueOf(cutoff),
                        String.valueOf(CLEANUP_BATCH_SIZE)
                    );

                    int removed = (result != null && !result.isEmpty())
                        ? ((Number) result.get(0)).intValue() : 0;
                    totalRemoved += removed;
                    hasMore = removed == CLEANUP_BATCH_SIZE;

                    if (hasMore) {
                        Thread.sleep(CLEANUP_BATCH_DELAY_MS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    hasMore = false;
                } catch (Exception ex) {
                    log.warn("Stale user cleanup failed for event {}", eventId, ex);
                    hasMore = false;
                }
            }

            if (totalRemoved > 0) {
                log.info("Cleaned up {} stale users from queue for event {}", totalRemoved, eventId);
            }

            try {
                Long activeSeenRemoved = redisTemplate.opsForZSet()
                    .removeRangeByScore("{" + eventId + "}:active-seen", Double.NEGATIVE_INFINITY, activeSeenCutoff);
                if (activeSeenRemoved != null && activeSeenRemoved > 0) {
                    log.debug("Cleaned up {} stale entries from active:seen for event {}", activeSeenRemoved, eventId);
                }
            } catch (Exception ex) {
                log.warn("Active-seen cleanup failed for event {}", eventId, ex);
            }
        }
    }

    private int getThreshold(String eventId) {
        String custom = redisTemplate.opsForValue().get("{" + eventId + "}:threshold");
        return custom != null ? Integer.parseInt(custom) : defaultThreshold;
    }
}
