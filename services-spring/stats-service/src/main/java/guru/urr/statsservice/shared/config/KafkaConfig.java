package guru.urr.statsservice.shared.config;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);
    private static final long KAFKA_BACKOFF_INITIAL_MS = 1000L;
    private static final double KAFKA_BACKOFF_MULTIPLIER = 2.0;
    private static final long KAFKA_BACKOFF_MAX_ELAPSED_MS = 10_000L;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Kafka consumer error handler: retry 3 times with exponential backoff (1s, 2s, 4s),
     * then send failed messages to DLQ topic ({original-topic}-dlq).
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> {
                log.error("Stats: sending failed message to DLQ: topic={}, key={}, error={}",
                    record.topic(), record.key(), ex.getMessage());
                return new TopicPartition(record.topic() + "-dlq", record.partition());
            });

        ExponentialBackOff backOff = new ExponentialBackOff(KAFKA_BACKOFF_INITIAL_MS, KAFKA_BACKOFF_MULTIPLIER);
        backOff.setMaxElapsedTime(KAFKA_BACKOFF_MAX_ELAPSED_MS);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
