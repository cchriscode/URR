package guru.urr.queueservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@ExtendWith(MockitoExtension.class)
class SqsPublisherTest {

    @Mock private SqsClient sqsClient;

    @Test
    void disabled_publishAdmission_doesNothing() {
        SqsPublisher publisher = new SqsPublisher(sqsClient, "https://sqs.example.com/queue", false);

        assertFalse(publisher.isEnabled());

        publisher.publishAdmission(UUID.randomUUID(), "user-1", "token-abc");

        verifyNoInteractions(sqsClient);
    }

    @Test
    void enabled_publishAdmission_sendsMessageWithCorrectGroupId() {
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("sqs-msg-1").build());

        SqsPublisher publisher = new SqsPublisher(sqsClient, "https://sqs.example.com/queue.fifo", true);

        assertTrue(publisher.isEnabled());

        UUID eventId = UUID.randomUUID();
        String userId = "user-2";
        String entryToken = "token-xyz";

        publisher.publishAdmission(eventId, userId, entryToken);

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());

        SendMessageRequest captured = captor.getValue();
        assertEquals("https://sqs.example.com/queue.fifo", captured.queueUrl());
        assertEquals(eventId.toString(), captured.messageGroupId());
        assertNotNull(captured.messageDeduplicationId());

        // Verify message body contains expected fields
        String body = captured.messageBody();
        assertTrue(body.contains("\"action\":\"admitted\""));
        assertTrue(body.contains("\"eventId\":\"" + eventId + "\""));
        assertTrue(body.contains("\"userId\":\"" + userId + "\""));
        assertTrue(body.contains("\"entryToken\":\"" + entryToken + "\""));
        assertTrue(body.contains("\"timestamp\":"));
    }

    @Test
    void enabled_sqsThrowsException_doesNotPropagate() {
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder().message("Service unavailable").build());

        SqsPublisher publisher = new SqsPublisher(sqsClient, "https://sqs.example.com/queue.fifo", true);

        UUID eventId = UUID.randomUUID();

        // Should not throw -- fire-and-forget behavior
        assertDoesNotThrow(() -> publisher.publishAdmission(eventId, "user-3", "token-err"));

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void nullSqsClient_disablesPublisher() {
        SqsPublisher publisher = new SqsPublisher(null, "https://sqs.example.com/queue.fifo", true);

        // enabled=true but sqsClient is null, so effective enabled should be false
        assertFalse(publisher.isEnabled());
    }

    @Test
    void blankQueueUrl_disablesPublisher() {
        SqsPublisher publisher = new SqsPublisher(sqsClient, "", true);

        // enabled=true but queueUrl is blank, so effective enabled should be false
        assertFalse(publisher.isEnabled());
    }
}
