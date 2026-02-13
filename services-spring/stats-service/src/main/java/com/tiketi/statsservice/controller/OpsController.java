package com.tiketi.statsservice.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {

    private final DataSource dataSource;
    private final KafkaAdmin kafkaAdmin;

    public OpsController(DataSource dataSource, KafkaAdmin kafkaAdmin) {
        this.dataSource = dataSource;
        this.kafkaAdmin = kafkaAdmin;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "stats-service");

        Map<String, String> deps = new LinkedHashMap<>();
        boolean healthy = true;

        try (var conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
            deps.put("postgresql", "ok");
        } catch (Exception e) {
            deps.put("postgresql", "error: " + e.getMessage());
            healthy = false;
        }

        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            client.describeCluster().clusterId().get(3, TimeUnit.SECONDS);
            deps.put("kafka", "ok");
        } catch (Exception e) {
            deps.put("kafka", "error: " + e.getMessage());
            healthy = false;
        }

        result.put("status", healthy ? "ok" : "degraded");
        result.put("dependencies", deps);
        return healthy ? ResponseEntity.ok(result) : ResponseEntity.status(503).body(result);
    }
}
