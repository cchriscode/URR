package guru.urr.queueservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import guru.urr.queueservice.shared.client.TicketInternalClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private TicketInternalClient ticketInternalClient;
    @Mock private SqsPublisher sqsPublisher;
    @Mock private ZSetOperations<String, String> zSetOperations;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(
                redisTemplate,
                ticketInternalClient,
                sqsPublisher,
                1000,
                600,
                "test-queue-entry-token-secret-minimum-32-chars-long",
                600
        );
    }

    private void stubZSetOperations() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void check_belowThreshold_immediateEntry() {
        stubZSetOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-1";

        Map<String, Object> eventInfo = Map.of("title", "Concert", "queueEnabled", true);
        when(ticketInternalClient.getEventQueueInfo(eventId)).thenReturn(eventInfo);

        // User is not in queue
        when(zSetOperations.score(eq("queue:" + eventId), eq(userId))).thenReturn(null);
        // User is not active
        when(zSetOperations.score(eq("active:" + eventId), eq(userId))).thenReturn(null);
        // Current active users below threshold (0 active)
        when(zSetOperations.count(eq("active:" + eventId), anyDouble(), eq(Double.POSITIVE_INFINITY)))
                .thenReturn(0L);
        // Queue is empty
        when(zSetOperations.size("queue:" + eventId)).thenReturn(0L);

        Map<String, Object> result = queueService.check(eventId, userId);

        assertEquals("active", result.get("status"));
        assertEquals(false, result.get("queued"));
        assertNotNull(result.get("entryToken"));
        // Verify user was added as active
        verify(zSetOperations).add(eq("active:" + eventId), eq(userId), anyDouble());
    }

    @Test
    void check_aboveThreshold_queued() {
        stubZSetOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-2";

        Map<String, Object> eventInfo = Map.of("title", "Concert", "queueEnabled", true);
        when(ticketInternalClient.getEventQueueInfo(eventId)).thenReturn(eventInfo);

        // User is not in queue
        when(zSetOperations.score(eq("queue:" + eventId), eq(userId))).thenReturn(null);
        // User is not active
        when(zSetOperations.score(eq("active:" + eventId), eq(userId))).thenReturn(null);
        // Current active users at threshold
        when(zSetOperations.count(eq("active:" + eventId), anyDouble(), eq(Double.POSITIVE_INFINITY)))
                .thenReturn(1000L);
        // Queue size starts at 0 then 1 after add
        when(zSetOperations.size("queue:" + eventId)).thenReturn(0L).thenReturn(1L);
        // Position after being added
        when(zSetOperations.rank("queue:" + eventId, userId)).thenReturn(0L);

        Map<String, Object> result = queueService.check(eventId, userId);

        assertEquals("queued", result.get("status"));
        assertEquals(true, result.get("queued"));
        assertEquals(1, result.get("position"));
        // Verify user was added to queue
        verify(zSetOperations).add(eq("queue:" + eventId), eq(userId), anyDouble());
    }

    @Test
    void status_notInQueue_returnsNotInQueue() {
        stubZSetOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-3";

        // User is not in queue
        when(zSetOperations.score(eq("queue:" + eventId), eq(userId))).thenReturn(null);
        // User is not active
        when(zSetOperations.score(eq("active:" + eventId), eq(userId))).thenReturn(null);
        // Current active users count for response
        when(zSetOperations.count(eq("active:" + eventId), anyDouble(), eq(Double.POSITIVE_INFINITY)))
                .thenReturn(50L);

        Map<String, Object> result = queueService.status(eventId, userId);

        assertEquals("not_in_queue", result.get("status"));
        assertEquals(false, result.get("queued"));
        assertEquals("Not in queue", result.get("message"));
    }

    @Test
    void heartbeat_activeUser_returnsActive() {
        stubZSetOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-4";

        // User is not in queue
        when(zSetOperations.score(eq("queue:" + eventId), eq(userId))).thenReturn(null);
        // User is active (score is future expiry timestamp)
        long futureExpiry = System.currentTimeMillis() + 300_000;
        when(zSetOperations.score(eq("active:" + eventId), eq(userId))).thenReturn((double) futureExpiry);

        Map<String, Object> result = queueService.heartbeat(eventId, userId);

        assertEquals("active", result.get("status"));
        assertEquals(false, result.get("queued"));
        // Verify TTL was extended via touchActiveUser
        verify(zSetOperations).add(eq("active:seen:" + eventId), eq(userId), anyDouble());
        verify(zSetOperations).add(eq("active:" + eventId), eq(userId), anyDouble());
    }

    @Test
    void leave_success_removesFromBoth() {
        stubZSetOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-5";

        Map<String, Object> result = queueService.leave(eventId, userId);

        assertEquals("Left queue", result.get("message"));
        // Verify removed from queue
        verify(zSetOperations).remove("queue:" + eventId, userId);
        verify(zSetOperations).remove("queue:seen:" + eventId, userId);
        // Verify removed from active
        verify(zSetOperations).remove("active:" + eventId, userId);
        verify(zSetOperations).remove("active:seen:" + eventId, userId);
    }

    @Test
    void check_belowThreshold_publishesSqsAdmission() {
        stubZSetOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-sqs-1";

        Map<String, Object> eventInfo = Map.of("title", "Concert", "queueEnabled", true);
        when(ticketInternalClient.getEventQueueInfo(eventId)).thenReturn(eventInfo);

        // User is not in queue or active
        when(zSetOperations.score(eq("queue:" + eventId), eq(userId))).thenReturn(null);
        when(zSetOperations.score(eq("active:" + eventId), eq(userId))).thenReturn(null);
        // Below threshold
        when(zSetOperations.count(eq("active:" + eventId), anyDouble(), eq(Double.POSITIVE_INFINITY)))
                .thenReturn(0L);
        when(zSetOperations.size("queue:" + eventId)).thenReturn(0L);

        Map<String, Object> result = queueService.check(eventId, userId);

        assertEquals("active", result.get("status"));
        // Verify SQS admission was published
        verify(sqsPublisher).publishAdmission(eq(eventId), eq(userId), anyString());
    }

    @Test
    void check_aboveThreshold_doesNotPublishSqsAdmission() {
        stubZSetOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-sqs-2";

        Map<String, Object> eventInfo = Map.of("title", "Concert", "queueEnabled", true);
        when(ticketInternalClient.getEventQueueInfo(eventId)).thenReturn(eventInfo);

        // User is not in queue or active
        when(zSetOperations.score(eq("queue:" + eventId), eq(userId))).thenReturn(null);
        when(zSetOperations.score(eq("active:" + eventId), eq(userId))).thenReturn(null);
        // At threshold -- user gets queued
        when(zSetOperations.count(eq("active:" + eventId), anyDouble(), eq(Double.POSITIVE_INFINITY)))
                .thenReturn(1000L);
        when(zSetOperations.size("queue:" + eventId)).thenReturn(0L).thenReturn(1L);
        when(zSetOperations.rank("queue:" + eventId, userId)).thenReturn(0L);

        Map<String, Object> result = queueService.check(eventId, userId);

        assertEquals("queued", result.get("status"));
        // Verify SQS admission was NOT published
        verify(sqsPublisher, never()).publishAdmission(any(), any(), any());
    }
}
