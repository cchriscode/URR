package com.tiketi.ticketservice.service;

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

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> admissionScript;
    private final DefaultRedisScript<List> staleCleanupScript;
    private final QueueService queueService;
    private final int threshold;
    private final int activeTtlSeconds;
    private final int admitBatchSize;

    public AdmissionWorkerService(
        StringRedisTemplate redisTemplate,
        DefaultRedisScript<List> admissionScript,
        DefaultRedisScript<List> staleCleanupScript,
        QueueService queueService,
        @Value("${QUEUE_THRESHOLD:1000}") int threshold,
        @Value("${QUEUE_ACTIVE_TTL_SECONDS:600}") int activeTtlSeconds,
        @Value("${queue.admission.batch-size:100}") int admitBatchSize
    ) {
        this.redisTemplate = redisTemplate;
        this.admissionScript = admissionScript;
        this.staleCleanupScript = staleCleanupScript;
        this.queueService = queueService;
        this.threshold = threshold;
        this.activeTtlSeconds = activeTtlSeconds;
        this.admitBatchSize = admitBatchSize;
    }

    @Scheduled(fixedDelayString = "${queue.admission.interval-ms:1000}")
    public void admitUsers() {
        Set<String> activeEvents = null;
        try {
            activeEvents = redisTemplate.opsForSet().members("queue:active-events");
        } catch (Exception ex) {
            log.debug("Failed to get active events set: {}", ex.getMessage());
            return;
        }

        if (activeEvents == null || activeEvents.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long activeTtlMs = activeTtlSeconds * 1000L;

        for (String eventId : activeEvents) {
            try {
                @SuppressWarnings("unchecked")
                List<Object> result = redisTemplate.execute(
                    admissionScript,
                    List.of(
                        "queue:" + eventId,
                        "active:" + eventId,
                        "queue:seen:" + eventId
                    ),
                    String.valueOf(admitBatchSize),
                    String.valueOf(now),
                    String.valueOf(activeTtlMs),
                    String.valueOf(threshold)
                );

                if (result != null && !result.isEmpty()) {
                    int admitted = ((Number) result.get(0)).intValue();
                    if (admitted > 0) {
                        queueService.recordAdmissions(admitted);
                        log.info("Admitted {} users for event {}", admitted, eventId);
                    }
                }

                // Check if queue is empty, if so remove from active-events
                Long queueSize = redisTemplate.opsForZSet().size("queue:" + eventId);
                if (queueSize != null && queueSize == 0) {
                    Long activeSize = redisTemplate.opsForZSet().count("active:" + eventId, now, Double.POSITIVE_INFINITY);
                    if (activeSize != null && activeSize == 0) {
                        redisTemplate.opsForSet().remove("queue:active-events", eventId);
                    }
                }
            } catch (Exception ex) {
                log.warn("Admission failed for event {}: {}", eventId, ex.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${queue.stale-cleanup.interval-ms:30000}")
    public void cleanupStaleUsers() {
        Set<String> activeEvents = null;
        try {
            activeEvents = redisTemplate.opsForSet().members("queue:active-events");
        } catch (Exception ex) {
            return;
        }

        if (activeEvents == null || activeEvents.isEmpty()) {
            return;
        }

        long cutoff = System.currentTimeMillis() - (activeTtlSeconds * 1000L);

        for (String eventId : activeEvents) {
            boolean hasMore = true;
            int totalRemoved = 0;

            while (hasMore) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Object> result = redisTemplate.execute(
                        staleCleanupScript,
                        List.of(
                            "queue:seen:" + eventId,
                            "queue:" + eventId
                        ),
                        String.valueOf(cutoff),
                        String.valueOf(1000)
                    );

                    int removed = (result != null && !result.isEmpty())
                        ? ((Number) result.get(0)).intValue() : 0;
                    totalRemoved += removed;
                    hasMore = removed == 1000;

                    if (hasMore) {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    hasMore = false;
                } catch (Exception ex) {
                    hasMore = false;
                }
            }

            if (totalRemoved > 0) {
                log.info("Cleaned up {} stale users from queue for event {}", totalRemoved, eventId);
            }
        }
    }
}
