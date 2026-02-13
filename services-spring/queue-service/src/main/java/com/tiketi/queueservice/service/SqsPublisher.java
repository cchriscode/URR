package com.tiketi.queueservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;
import java.util.UUID;

@Service
public class SqsPublisher {

    private static final Logger log = LoggerFactory.getLogger(SqsPublisher.class);

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public SqsPublisher(
            @org.springframework.lang.Nullable SqsClient sqsClient,
            @Value("${aws.sqs.queue-url:}") String queueUrl,
            @Value("${aws.sqs.enabled:false}") boolean enabled) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
        this.enabled = enabled && sqsClient != null && !queueUrl.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void publishAdmission(UUID eventId, String userId, String entryToken) {
        if (!enabled) {
            return;
        }

        try {
            Map<String, Object> body = Map.of(
                    "action", "admitted",
                    "eventId", eventId.toString(),
                    "userId", userId,
                    "entryToken", entryToken,
                    "timestamp", System.currentTimeMillis()
            );

            String messageBody = objectMapper.writeValueAsString(body);
            // Use only userId + eventId so SQS FIFO dedup window (5min) prevents duplicate admissions
            String deduplicationId = userId + ":" + eventId;

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageGroupId(eventId.toString())
                    .messageDeduplicationId(deduplicationId)
                    .build());

            log.info("SQS admission published: user={} event={}", userId, eventId);
        } catch (Exception e) {
            log.error("SQS publish failed (fallback to Redis-only): user={} event={} error={}",
                    userId, eventId, e.getMessage());
        }
    }
}
