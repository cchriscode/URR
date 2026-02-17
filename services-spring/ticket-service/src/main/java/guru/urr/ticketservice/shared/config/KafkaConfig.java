package guru.urr.ticketservice.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.replication-factor:1}")
    private int replicationFactor;

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events").partitions(3).replicas(replicationFactor).build();
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
}
