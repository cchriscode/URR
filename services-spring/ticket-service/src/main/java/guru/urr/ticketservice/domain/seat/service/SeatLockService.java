package guru.urr.ticketservice.domain.seat.service;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class SeatLockService {

    private static final Logger log = LoggerFactory.getLogger(SeatLockService.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> seatLockAcquireScript;
    private final DefaultRedisScript<Long> seatLockReleaseScript;
    private final DefaultRedisScript<Long> paymentVerifyScript;
    private final int seatLockTtlSeconds;

    public record SeatLockResult(boolean success, long fencingToken) {}

    public SeatLockService(
        StringRedisTemplate redisTemplate,
        DefaultRedisScript<List> seatLockAcquireScript,
        DefaultRedisScript<Long> seatLockReleaseScript,
        DefaultRedisScript<Long> paymentVerifyScript,
        @Value("${SEAT_LOCK_TTL_SECONDS:300}") int seatLockTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.seatLockAcquireScript = seatLockAcquireScript;
        this.seatLockReleaseScript = seatLockReleaseScript;
        this.paymentVerifyScript = paymentVerifyScript;
        this.seatLockTtlSeconds = seatLockTtlSeconds;
    }

    public SeatLockResult acquireLock(UUID eventId, UUID seatId, String userId) {
        String seatKey = seatKey(eventId, seatId);
        String tokenSeqKey = seatKey + ":token_seq";
        try {
            @SuppressWarnings("unchecked")
            List<Object> result = redisTemplate.execute(
                seatLockAcquireScript,
                List.of(seatKey, tokenSeqKey),
                userId,
                String.valueOf(seatLockTtlSeconds)
            );

            if (result == null || result.size() < 2) {
                log.warn("Seat lock acquire returned null for seat {}", seatKey);
                return new SeatLockResult(false, -1);
            }

            long success = Long.parseLong(result.get(0).toString());
            long token = Long.parseLong(result.get(1).toString());
            return new SeatLockResult(success == 1, token);
        } catch (Exception ex) {
            log.error("Redis seat lock failed for {}: {}", seatKey, ex.getMessage());
            return new SeatLockResult(false, -1);
        }
    }

    public boolean releaseLock(UUID eventId, UUID seatId, String userId, long token) {
        String seatKey = seatKey(eventId, seatId);
        try {
            Long result = redisTemplate.execute(
                seatLockReleaseScript,
                List.of(seatKey),
                userId,
                String.valueOf(token)
            );
            return result != null && result == 1;
        } catch (Exception ex) {
            log.warn("Redis seat lock release failed for {}: {}", seatKey, ex.getMessage());
            return false;
        }
    }

    public boolean verifyForPayment(UUID eventId, UUID seatId, String userId, long token) {
        if (token == -1) {
            log.error("Cannot verify payment: seat was locked without Redis (fencingToken=-1)");
            return false;
        }
        String seatKey = seatKey(eventId, seatId);
        try {
            Long result = redisTemplate.execute(
                paymentVerifyScript,
                List.of(seatKey),
                userId,
                String.valueOf(token)
            );
            return result != null && result == 1;
        } catch (Exception ex) {
            log.error("Redis payment verify failed for {}: {}", seatKey, ex.getMessage());
            return false;
        }
    }

    public void cleanupLock(UUID eventId, UUID seatId) {
        String seatKey = seatKey(eventId, seatId);
        try {
            redisTemplate.delete(seatKey);
        } catch (Exception ex) {
            log.debug("Redis seat lock cleanup failed for {}: {}", seatKey, ex.getMessage());
        }
    }

    private String seatKey(UUID eventId, UUID seatId) {
        return "seat:" + eventId + ":" + seatId;
    }
}
