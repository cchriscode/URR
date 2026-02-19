package guru.urr.ticketservice.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);
    private static final long KAFKA_BACKOFF_INITIAL_MS = 1000L;
    private static final double KAFKA_BACKOFF_MULTIPLIER = 2.0;
    private static final long KAFKA_BACKOFF_MAX_ELAPSED_MS = 10_000L;

    @Value("${kafka.topic.replication-factor:1}")
    private int replicationFactor;

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events").partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic paymentEventsDlqTopic() {
        return TopicBuilder.name("payment-events-dlq").partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic reservationEventsTopic() {
        return TopicBuilder.name("reservation-events").partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic transferEventsTopic() {
        return TopicBuilder.name("transfer-events").partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic membershipEventsTopic() {
        return TopicBuilder.name("membership-events").partitions(3).replicas(replicationFactor).build();
    }

    /**
     * Kafka consumer error handler: retry 3 times with exponential backoff (1s, 2s, 4s),
     * then send failed messages to DLQ topic ({original-topic}-dlq).
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> {
                log.error("Sending failed message to DLQ: topic={}, key={}, error={}",
                    record.topic(), record.key(), ex.getMessage());
                return new TopicPartition(record.topic() + "-dlq", record.partition());
            });

        ExponentialBackOff backOff = new ExponentialBackOff(KAFKA_BACKOFF_INITIAL_MS, KAFKA_BACKOFF_MULTIPLIER);
        backOff.setMaxElapsedTime(KAFKA_BACKOFF_MAX_ELAPSED_MS); // 3 retries: 1s + 2s + 4s = 7s, cap at 10s

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
