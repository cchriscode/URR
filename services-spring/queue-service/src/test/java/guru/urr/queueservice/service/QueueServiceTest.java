package guru.urr.queueservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import guru.urr.queueservice.shared.client.TicketInternalClient;
import guru.urr.queueservice.shared.metrics.QueueMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private TicketInternalClient ticketInternalClient;
    @Mock private SqsPublisher sqsPublisher;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private SetOperations<String, String> setOperations;
    @SuppressWarnings("rawtypes")
    @Mock private DefaultRedisScript<List> queueCheckScript;
    @Mock private QueueMetrics queueMetrics;
    @Mock private EntryTokenGenerator entryTokenGenerator;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(
                redisTemplate,
                ticketInternalClient,
                sqsPublisher,
                queueCheckScript,
                queueMetrics,
                entryTokenGenerator,
                1000,
                600
        );
    }

    // Redis key helpers matching QueueService key format
    private static String queueKey(UUID eventId) { return "{" + eventId + "}:queue"; }
    private static String activeKey(UUID eventId) { return "{" + eventId + "}:active"; }
    private static String seenKey(UUID eventId) { return "{" + eventId + "}:seen"; }
    private static String activeSeenKey(UUID eventId) { return "{" + eventId + "}:active-seen"; }

    private void stubRedisOperations() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(entryTokenGenerator.generate(anyString(), anyString())).thenReturn("test-entry-token");
    }

    @SuppressWarnings("unchecked")
    private void stubLuaScript(long inQueue, long inActive, long position,
                               long queueSize, long activeCount, long threshold) {
        doReturn(Arrays.asList(inQueue, inActive, position, queueSize, activeCount, threshold))
                .when(redisTemplate).execute(any(DefaultRedisScript.class), anyList(),
                        any(), any(), any(), any());
    }

    @Test
    void check_belowThreshold_immediateEntry() {
        stubRedisOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-1";

        Map<String, Object> eventInfo = Map.of("title", "Concert", "queueEnabled", true);
        when(ticketInternalClient.getEventQueueInfo(eventId)).thenReturn(eventInfo);

        stubLuaScript(0, 0, 0, 0, 0, 1000);

        Map<String, Object> result = queueService.check(eventId, userId);

        assertEquals("active", result.get("status"));
        assertEquals(false, result.get("queued"));
        assertNotNull(result.get("entryToken"));
        assertEquals("test-entry-token", result.get("entryToken"));
        verify(zSetOperations, atLeast(1)).add(eq(activeKey(eventId)), eq(userId), anyDouble());
        verify(queueMetrics).recordQueueAdmitted();
    }

    @Test
    void check_aboveThreshold_queued() {
        stubRedisOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-2";

        Map<String, Object> eventInfo = Map.of("title", "Concert", "queueEnabled", true);
        when(ticketInternalClient.getEventQueueInfo(eventId)).thenReturn(eventInfo);

        stubLuaScript(0, 0, 0, 0, 1000, 1000);

        when(zSetOperations.rank(queueKey(eventId), userId)).thenReturn(0L);
        when(zSetOperations.size(queueKey(eventId))).thenReturn(1L);

        Map<String, Object> result = queueService.check(eventId, userId);

        assertEquals("queued", result.get("status"));
        assertEquals(true, result.get("queued"));
        assertEquals(1, result.get("position"));
        verify(zSetOperations).add(eq(queueKey(eventId)), eq(userId), anyDouble());
        verify(queueMetrics).recordQueueJoined();
    }

    @Test
    void status_notInQueue_returnsNotInQueue() {
        stubRedisOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-3";

        stubLuaScript(0, 0, 0, 0, 50, 1000);

        Map<String, Object> result = queueService.status(eventId, userId);

        assertEquals("not_in_queue", result.get("status"));
        assertEquals(false, result.get("queued"));
        assertEquals("Not in queue", result.get("message"));
    }

    @Test
    void heartbeat_activeUser_returnsActive() {
        stubRedisOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-4";

        when(zSetOperations.score(eq(queueKey(eventId)), eq(userId))).thenReturn(null);
        long futureExpiry = System.currentTimeMillis() + 300_000;
        when(zSetOperations.score(eq(activeKey(eventId)), eq(userId))).thenReturn((double) futureExpiry);

        Map<String, Object> result = queueService.heartbeat(eventId, userId);

        assertEquals("active", result.get("status"));
        assertEquals(false, result.get("queued"));
        verify(zSetOperations).add(eq(activeSeenKey(eventId)), eq(userId), anyDouble());
        verify(zSetOperations).add(eq(activeKey(eventId)), eq(userId), anyDouble());
    }

    @Test
    void leave_success_removesFromBoth() {
        stubRedisOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-5";

        Map<String, Object> result = queueService.leave(eventId, userId);

        assertEquals("Left queue", result.get("message"));
        verify(zSetOperations).remove(queueKey(eventId), userId);
        verify(zSetOperations).remove(seenKey(eventId), userId);
        verify(zSetOperations).remove(activeKey(eventId), userId);
        verify(zSetOperations).remove(activeSeenKey(eventId), userId);
        verify(queueMetrics).recordQueueLeft();
    }

    @Test
    void check_belowThreshold_publishesSqsAdmission() {
        stubRedisOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-sqs-1";

        Map<String, Object> eventInfo = Map.of("title", "Concert", "queueEnabled", true);
        when(ticketInternalClient.getEventQueueInfo(eventId)).thenReturn(eventInfo);

        stubLuaScript(0, 0, 0, 0, 0, 1000);

        Map<String, Object> result = queueService.check(eventId, userId);

        assertEquals("active", result.get("status"));
        verify(sqsPublisher).publishAdmission(eq(eventId), eq(userId), eq("test-entry-token"));
    }

    @Test
    void check_aboveThreshold_doesNotPublishSqsAdmission() {
        stubRedisOperations();
        UUID eventId = UUID.randomUUID();
        String userId = "user-sqs-2";

        Map<String, Object> eventInfo = Map.of("title", "Concert", "queueEnabled", true);
        when(ticketInternalClient.getEventQueueInfo(eventId)).thenReturn(eventInfo);

        stubLuaScript(0, 0, 0, 0, 1000, 1000);

        when(zSetOperations.rank(queueKey(eventId), userId)).thenReturn(0L);
        when(zSetOperations.size(queueKey(eventId))).thenReturn(1L);

        Map<String, Object> result = queueService.check(eventId, userId);

        assertEquals("queued", result.get("status"));
        verify(sqsPublisher, never()).publishAdmission(any(), any(), any());
    }
}
