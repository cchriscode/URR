package guru.urr.statsservice.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventsCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventsCleanupScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventsCleanupScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Periodically removes processed_events entries older than 24 hours
     * to prevent unbounded table growth while still protecting against
     * Kafka redeliveries within the retention window.
     */
    @Scheduled(fixedRateString = "${stats.dedup.cleanup-interval-ms:3600000}")
    public void cleanupOldProcessedEvents() {
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM processed_events WHERE processed_at < NOW() - INTERVAL '24 hours'");
            if (deleted > 0) {
                log.info("Cleaned up {} old processed_events entries", deleted);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup processed_events: {}", e.getMessage(), e);
        }
    }
}
