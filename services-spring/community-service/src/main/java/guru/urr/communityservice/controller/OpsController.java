package guru.urr.communityservice.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {

    private final DataSource dataSource;

    public OpsController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "community-service");

        Map<String, String> deps = new LinkedHashMap<>();
        boolean healthy = true;

        try (var conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
            deps.put("postgresql", "ok");
        } catch (Exception e) {
            deps.put("postgresql", "error: " + e.getMessage());
            healthy = false;
        }

        result.put("status", healthy ? "ok" : "degraded");
        result.put("dependencies", deps);
        return healthy ? ResponseEntity.ok(result) : ResponseEntity.status(503).body(result);
    }
}
