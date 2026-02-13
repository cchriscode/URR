package com.tiketi.ticketservice.messaging;

import com.tiketi.ticketservice.messaging.event.MembershipActivatedEvent;
import com.tiketi.ticketservice.messaging.event.ReservationCancelledEvent;
import com.tiketi.ticketservice.messaging.event.ReservationConfirmedEvent;
import com.tiketi.ticketservice.messaging.event.ReservationCreatedEvent;
import com.tiketi.ticketservice.messaging.event.TransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TicketEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TicketEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReservationCreated(ReservationCreatedEvent event) {
        kafkaTemplate.send("reservation-events", event.reservationId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish ReservationCreatedEvent: {}", ex.getMessage());
                } else {
                    log.info("Published ReservationCreatedEvent reservationId={}", event.reservationId());
                }
            });
    }

    public void publishReservationConfirmed(ReservationConfirmedEvent event) {
        kafkaTemplate.send("reservation-events", event.reservationId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish ReservationConfirmedEvent: {}", ex.getMessage());
                } else {
                    log.info("Published ReservationConfirmedEvent reservationId={}", event.reservationId());
                }
            });
    }

    public void publishReservationCancelled(ReservationCancelledEvent event) {
        kafkaTemplate.send("reservation-events", event.reservationId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish ReservationCancelledEvent: {}", ex.getMessage());
                } else {
                    log.info("Published ReservationCancelledEvent reservationId={}", event.reservationId());
                }
            });
    }

    public void publishTransferCompleted(TransferCompletedEvent event) {
        kafkaTemplate.send("transfer-events", event.transferId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish TransferCompletedEvent: {}", ex.getMessage());
                } else {
                    log.info("Published TransferCompletedEvent transferId={}", event.transferId());
                }
            });
    }

    public void publishMembershipActivated(MembershipActivatedEvent event) {
        kafkaTemplate.send("membership-events", event.membershipId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish MembershipActivatedEvent: {}", ex.getMessage());
                } else {
                    log.info("Published MembershipActivatedEvent membershipId={}", event.membershipId());
                }
            });
    }
}
