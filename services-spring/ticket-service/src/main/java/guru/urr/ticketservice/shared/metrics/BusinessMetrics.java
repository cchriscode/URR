package guru.urr.ticketservice.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final Counter reservationCreated;
    private final Counter reservationConfirmed;
    private final Counter reservationCancelled;
    private final Counter reservationExpired;
    private final Counter paymentProcessed;
    private final Counter paymentFailed;
    private final Counter transferCompleted;
    private final Counter membershipActivated;
    private final Timer reservationCreateTimer;

    public BusinessMetrics(MeterRegistry registry) {
        this.reservationCreated = Counter.builder("business.reservation.created.total")
            .description("Total reservations created")
            .register(registry);
        this.reservationConfirmed = Counter.builder("business.reservation.confirmed.total")
            .description("Total reservations confirmed via payment")
            .register(registry);
        this.reservationCancelled = Counter.builder("business.reservation.cancelled.total")
            .description("Total reservations cancelled")
            .register(registry);
        this.reservationExpired = Counter.builder("business.reservation.expired.total")
            .description("Total reservations expired")
            .register(registry);
        this.paymentProcessed = Counter.builder("business.payment.processed.total")
            .description("Total payments processed successfully")
            .register(registry);
        this.paymentFailed = Counter.builder("business.payment.failed.total")
            .description("Total payment processing failures")
            .register(registry);
        this.transferCompleted = Counter.builder("business.transfer.completed.total")
            .description("Total ticket transfers completed")
            .register(registry);
        this.membershipActivated = Counter.builder("business.membership.activated.total")
            .description("Total memberships activated")
            .register(registry);
        this.reservationCreateTimer = Timer.builder("business.reservation.create.duration")
            .description("Time to create a reservation")
            .register(registry);
    }

    public void recordReservationCreated() { reservationCreated.increment(); }
    public void recordReservationConfirmed() { reservationConfirmed.increment(); }
    public void recordReservationCancelled() { reservationCancelled.increment(); }
    public void recordReservationExpired() { reservationExpired.increment(); }
    public void recordPaymentProcessed() { paymentProcessed.increment(); }
    public void recordPaymentFailed() { paymentFailed.increment(); }
    public void recordTransferCompleted() { transferCompleted.increment(); }
    public void recordMembershipActivated() { membershipActivated.increment(); }
    public Timer getReservationCreateTimer() { return reservationCreateTimer; }
}
