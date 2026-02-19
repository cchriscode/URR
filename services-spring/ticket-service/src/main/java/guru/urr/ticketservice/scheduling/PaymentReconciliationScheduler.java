package guru.urr.ticketservice.scheduling;

import guru.urr.ticketservice.domain.reservation.service.ReservationPaymentHandler;
import guru.urr.ticketservice.shared.client.PaymentInternalClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final PaymentInternalClient paymentInternalClient;
    private final ReservationPaymentHandler reservationPaymentHandler;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter reconciliationMismatchCounter;

    public PaymentReconciliationScheduler(JdbcTemplate jdbcTemplate,
                                           PaymentInternalClient paymentInternalClient,
                                           ReservationPaymentHandler reservationPaymentHandler,
                                           KafkaTemplate<String, Object> kafkaTemplate,
                                           MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.paymentInternalClient = paymentInternalClient;
        this.reservationPaymentHandler = reservationPaymentHandler;
        this.kafkaTemplate = kafkaTemplate;
        this.reconciliationMismatchCounter = Counter.builder("reconciliation.mismatch")
            .description("Payment-reservation mismatches detected during reconciliation")
            .register(meterRegistry);
    }

    /**
     * H3: Reconciliation scheduler that catches cases where Kafka was down
     * and payment was confirmed but reservation stayed pending.
     * Runs every 5 minutes by default.
     */
    @Scheduled(fixedRateString = "${reservation.reconciliation.interval-ms:300000}")
    public void reconcilePendingReservations() {
        try {
            List<Map<String, Object>> pendingReservations = jdbcTemplate.queryForList("""
                SELECT id, payment_method
                FROM reservations
                WHERE status = 'pending'
                  AND payment_status = 'pending'
                  AND created_at < NOW() - INTERVAL '5 minutes'
                  AND expires_at > NOW()
                ORDER BY created_at ASC
                LIMIT 50
                """);

            if (pendingReservations.isEmpty()) {
                return;
            }

            log.info("Reconciliation: found {} pending reservations older than 5 minutes", pendingReservations.size());

            for (Map<String, Object> reservation : pendingReservations) {
                UUID reservationId = (UUID) reservation.get("id");
                try {
                    Map<String, Object> paymentInfo = paymentInternalClient.getPaymentByReservation(reservationId);
                    if (paymentInfo == null) {
                        continue;
                    }

                    boolean found = Boolean.TRUE.equals(paymentInfo.get("found"));
                    String status = String.valueOf(paymentInfo.get("status"));

                    if (found && "confirmed".equals(status)) {
                        String method = paymentInfo.get("method") != null
                            ? String.valueOf(paymentInfo.get("method")) : "unknown";

                        log.warn("Payment-Reservation mismatch detected: reservationId={}, paymentStatus={}, reservationStatus=pending",
                            reservationId, status);
                        reconciliationMismatchCounter.increment();

                        // Re-publish Kafka event to trigger downstream processing
                        kafkaTemplate.send("payment-events", reservationId.toString(), Map.of(
                            "type", "PAYMENT_CONFIRMED",
                            "reservationId", reservationId.toString(),
                            "paymentMethod", method,
                            "source", "reconciliation"
                        ));

                        log.info("Reconciliation: confirming reservation {} (payment confirmed in payment-service)", reservationId);
                        reservationPaymentHandler.confirmReservationPayment(reservationId, method);
                    }
                } catch (Exception e) {
                    log.warn("Reconciliation: failed to check reservation {}: {}", reservationId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Reconciliation scheduler failed: {}", e.getMessage(), e);
        }
    }
}
