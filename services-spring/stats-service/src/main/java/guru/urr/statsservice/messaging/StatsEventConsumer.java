package guru.urr.statsservice.messaging;

import guru.urr.statsservice.service.StatsWriteService;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StatsEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatsEventConsumer.class);

    private final StatsWriteService statsWriteService;
    private final JdbcTemplate jdbcTemplate;

    public StatsEventConsumer(StatsWriteService statsWriteService, JdbcTemplate jdbcTemplate) {
        this.statsWriteService = statsWriteService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @KafkaListener(topics = "payment-events", groupId = "stats-service-group")
    public void handlePaymentEvent(Map<String, Object> event) {
        try {
            String eventKey = buildEventKey(event);
            if (eventKey != null && isDuplicate(eventKey)) {
                log.info("Stats: skipping duplicate payment event: {}", eventKey);
                return;
            }

            // C3: Check explicit type field first, fallback to duck-typing
            String type = str(event.get("type"));
            int amount = integer(event.get("amount"));

            if ("PAYMENT_REFUNDED".equals(type)) {
                log.info("Stats: recording payment refund amount={}", amount);
                statsWriteService.recordPaymentRefunded(amount);
            } else if ("PAYMENT_CONFIRMED".equals(type)) {
                String paymentType = str(event.get("paymentType"));
                if ("transfer".equals(paymentType)) {
                    log.info("Stats: recording transfer payment amount={}", amount);
                    statsWriteService.recordTransferCompleted(amount);
                }
            } else {
                // Fallback: duck-typing for backward compatibility
                boolean isRefund = event.containsKey("reason");
                if (isRefund) {
                    log.info("Stats: recording payment refund amount={}", amount);
                    statsWriteService.recordPaymentRefunded(amount);
                } else {
                    String paymentType = str(event.get("paymentType"));
                    if ("transfer".equals(paymentType)) {
                        log.info("Stats: recording transfer payment amount={}", amount);
                        statsWriteService.recordTransferCompleted(amount);
                    }
                }
            }

            if (eventKey != null) {
                markProcessed(eventKey);
            }
        } catch (Exception e) {
            log.error("Stats: failed to process payment event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "reservation-events", groupId = "stats-service-group")
    public void handleReservationEvent(Map<String, Object> event) {
        try {
            String eventKey = buildEventKey(event);
            if (eventKey != null && isDuplicate(eventKey)) {
                log.info("Stats: skipping duplicate reservation event: {}", eventKey);
                return;
            }

            UUID eventId = uuid(event.get("eventId"));
            int amount = integer(event.get("totalAmount"));

            // C3: Check explicit type field first, fallback to duck-typing
            String type = str(event.get("type"));

            if ("RESERVATION_CONFIRMED".equals(type)) {
                log.info("Stats: recording reservation confirmed eventId={} amount={}", eventId, amount);
                statsWriteService.recordReservationConfirmed(eventId, amount);
            } else if ("RESERVATION_CANCELLED".equals(type)) {
                log.info("Stats: recording reservation cancelled eventId={}", eventId);
                statsWriteService.recordReservationCancelled(eventId);
            } else if ("RESERVATION_CREATED".equals(type)) {
                log.info("Stats: recording reservation created eventId={}", eventId);
                statsWriteService.recordReservationCreated(eventId);
            } else {
                // Fallback: duck-typing for backward compatibility
                if (event.containsKey("paymentMethod") && !event.containsKey("reason")) {
                    log.info("Stats: recording reservation confirmed eventId={} amount={}", eventId, amount);
                    statsWriteService.recordReservationConfirmed(eventId, amount);
                } else if (event.containsKey("reason")) {
                    log.info("Stats: recording reservation cancelled eventId={}", eventId);
                    statsWriteService.recordReservationCancelled(eventId);
                } else {
                    log.info("Stats: recording reservation created eventId={}", eventId);
                    statsWriteService.recordReservationCreated(eventId);
                }
            }

            if (eventKey != null) {
                markProcessed(eventKey);
            }
        } catch (Exception e) {
            log.error("Stats: failed to process reservation event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "membership-events", groupId = "stats-service-group")
    public void handleMembershipEvent(Map<String, Object> event) {
        try {
            String eventKey = buildEventKey(event);
            if (eventKey != null && isDuplicate(eventKey)) {
                log.info("Stats: skipping duplicate membership event: {}", eventKey);
                return;
            }

            log.info("Stats: recording membership activated");
            statsWriteService.recordMembershipActivated();

            if (eventKey != null) {
                markProcessed(eventKey);
            }
        } catch (Exception e) {
            log.error("Stats: failed to process membership event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "transfer-events", groupId = "stats-service-group")
    public void handleTransferEvent(Map<String, Object> event) {
        try {
            String eventKey = buildEventKey(event);
            if (eventKey != null && isDuplicate(eventKey)) {
                log.info("Stats: skipping duplicate transfer event: {}", eventKey);
                return;
            }

            String type = str(event.get("type"));
            log.info("Received transfer event: type={}", type);

            if ("TRANSFER_COMPLETED".equals(type)) {
                jdbcTemplate.update(
                    "INSERT INTO daily_stats (stat_date, stat_type, stat_value) VALUES (CURRENT_DATE, 'transfer_completed', 1) " +
                    "ON CONFLICT (stat_date, stat_type) DO UPDATE SET stat_value = daily_stats.stat_value + 1"
                );
            } else if ("TRANSFER_CANCELLED".equals(type)) {
                jdbcTemplate.update(
                    "INSERT INTO daily_stats (stat_date, stat_type, stat_value) VALUES (CURRENT_DATE, 'transfer_cancelled', 1) " +
                    "ON CONFLICT (stat_date, stat_type) DO UPDATE SET stat_value = daily_stats.stat_value + 1"
                );
            }

            if (eventKey != null) {
                markProcessed(eventKey);
            }
        } catch (Exception e) {
            log.error("Stats: failed to process transfer event: {}", e.getMessage(), e);
        }
    }

    // H7: Deduplication helpers

    private String buildEventKey(Map<String, Object> event) {
        String type = str(event.get("type"));
        String timestamp = str(event.get("timestamp"));
        String id = str(event.get("reservationId"));
        if (id == null) id = str(event.get("paymentId"));
        if (id == null) id = str(event.get("membershipId"));
        if (id == null) id = str(event.get("transferId"));

        if (type != null && id != null && timestamp != null) {
            return type + ":" + id + ":" + timestamp;
        }
        return null;
    }

    private boolean isDuplicate(String eventKey) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_key = ?", Integer.class, eventKey);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Stats: deduplication check failed for key {}: {}", eventKey, e.getMessage());
            return false;
        }
    }

    private void markProcessed(String eventKey) {
        try {
            jdbcTemplate.update(
                "INSERT INTO processed_events (event_key, processed_at) VALUES (?, NOW()) ON CONFLICT (event_key) DO NOTHING",
                eventKey);
        } catch (Exception e) {
            log.warn("Stats: failed to mark event as processed: {}", eventKey, e);
        }
    }

    private String str(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof String s && !s.isBlank()) {
            try { return UUID.fromString(s); } catch (Exception e) { return null; }
        }
        return null;
    }

    private int integer(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
        }
        return 0;
    }
}
