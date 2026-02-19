package guru.urr.queueservice.shared.exception;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Queue-service-specific exception handler for Redis failures.
 * Generic exception handling is provided by urr-common's GlobalExceptionHandler.
 */
@RestControllerAdvice
@Order(1)
public class QueueExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(QueueExceptionHandler.class);

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<Map<String, String>> handleRedisUnavailable(RedisConnectionFailureException ex) {
        log.error("Redis unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Queue service temporarily unavailable. Please retry."));
    }
}
