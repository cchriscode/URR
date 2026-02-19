package guru.urr.queueservice.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {

    private final StringRedisTemplate redisTemplate;

    public OpsController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "queue-service");

        Map<String, String> deps = new LinkedHashMap<>();
        boolean healthy = true;

        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            deps.put("redis", pong != null ? "ok" : "error: no response");
            if (pong == null) healthy = false;
        } catch (Exception e) {
            deps.put("redis", "error: " + e.getMessage());
            healthy = false;
        }

        result.put("status", healthy ? "ok" : "degraded");
        result.put("dependencies", deps);
        return healthy ? ResponseEntity.ok(result) : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }
}
