package com.tiketi.ticketservice.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OpsController(DataSource dataSource,
                         StringRedisTemplate redisTemplate,
                         KafkaTemplate<String, Object> kafkaTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "ticket-service");

        Map<String, String> deps = new LinkedHashMap<>();
        boolean healthy = true;

        try (var conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
            deps.put("postgresql", "ok");
        } catch (Exception e) {
            deps.put("postgresql", "error: " + e.getMessage());
            healthy = false;
        }

        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            deps.put("redis", pong != null ? "ok" : "error: no response");
            if (pong == null) healthy = false;
        } catch (Exception e) {
            deps.put("redis", "error: " + e.getMessage());
            healthy = false;
        }

        try {
            var metrics = kafkaTemplate.metrics();
            deps.put("kafka", metrics != null ? "ok" : "error: no metrics");
            if (metrics == null) healthy = false;
        } catch (Exception e) {
            deps.put("kafka", "error: " + e.getMessage());
            healthy = false;
        }

        result.put("status", healthy ? "ok" : "degraded");
        result.put("dependencies", deps);
        return healthy ? ResponseEntity.ok(result) : ResponseEntity.status(503).body(result);
    }
}
