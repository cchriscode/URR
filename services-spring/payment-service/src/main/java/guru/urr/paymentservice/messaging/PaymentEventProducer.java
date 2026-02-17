package guru.urr.paymentservice.messaging;

import guru.urr.paymentservice.messaging.event.PaymentConfirmedEvent;
import guru.urr.paymentservice.messaging.event.PaymentRefundedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);
    private static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PaymentConfirmedEvent event) {
        kafkaTemplate.send(TOPIC, event.orderId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish PaymentConfirmedEvent orderId={}: {}", event.orderId(), ex.getMessage());
                } else {
                    log.info("Published PaymentConfirmedEvent orderId={} type={}", event.orderId(), event.paymentType());
                }
            });
    }

    public void publishRefund(PaymentRefundedEvent event) {
        kafkaTemplate.send(TOPIC, event.orderId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish PaymentRefundedEvent orderId={}: {}", event.orderId(), ex.getMessage());
                } else {
                    log.info("Published PaymentRefundedEvent orderId={}", event.orderId());
                }
            });
    }
}
