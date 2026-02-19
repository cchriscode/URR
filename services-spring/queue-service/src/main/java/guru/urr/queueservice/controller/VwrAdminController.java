package guru.urr.queueservice.controller;

import guru.urr.common.security.JwtTokenParser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Admin API for VWR Tier 1 management.
 * Controls VWR activation/deactivation and counter advancement.
 * All endpoints require admin authentication.
 * Only active when vwr.dynamodb.enabled=true.
 */
@RestController
@RequestMapping("/api/admin/vwr")
@ConditionalOnProperty(name = "vwr.dynamodb.enabled", havingValue = "true")
public class VwrAdminController {

    private static final int DEFAULT_ADVANCE_BATCH_SIZE = 500;

    private final JwtTokenParser jwtTokenParser;
    private final DynamoDbClient dynamoDbClient;
    private final String countersTableName;

    public VwrAdminController(
            JwtTokenParser jwtTokenParser,
            DynamoDbClient dynamoDbClient,
            @Value("${vwr.dynamodb.counters-table:}") String countersTableName) {
        this.jwtTokenParser = jwtTokenParser;
        this.dynamoDbClient = dynamoDbClient;
        this.countersTableName = countersTableName;
    }

    /**
     * Activate VWR for an event. Initializes counters in DynamoDB.
     */
    @PostMapping("/activate/{eventId}")
    public Map<String, Object> activate(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);

        if (countersTableName == null || countersTableName.isBlank()) {
            return Map.of("error", "VWR DynamoDB not configured");
        }

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(countersTableName)
                .item(Map.of(
                        "eventId", AttributeValue.fromS(eventId.toString()),
                        "nextPosition", AttributeValue.fromN("0"),
                        "servingCounter", AttributeValue.fromN("0"),
                        "isActive", AttributeValue.fromBool(true),
                        "updatedAt", AttributeValue.fromN(String.valueOf(System.currentTimeMillis()))
                ))
                .build());

        return Map.of(
                "eventId", eventId.toString(),
                "status", "activated",
                "message", "VWR activated. Update vwr-active.json and redeploy Lambda@Edge."
        );
    }

    /**
     * Deactivate VWR for an event. Sets isActive=false.
     */
    @PostMapping("/deactivate/{eventId}")
    public Map<String, Object> deactivate(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);

        if (countersTableName == null || countersTableName.isBlank()) {
            return Map.of("error", "VWR DynamoDB not configured");
        }

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(countersTableName)
                .key(Map.of("eventId", AttributeValue.fromS(eventId.toString())))
                .updateExpression("SET isActive = :false, updatedAt = :now")
                .expressionAttributeValues(Map.of(
                        ":false", AttributeValue.fromBool(false),
                        ":now", AttributeValue.fromN(String.valueOf(System.currentTimeMillis()))
                ))
                .build());

        return Map.of(
                "eventId", eventId.toString(),
                "status", "deactivated"
        );
    }

    /**
     * Get VWR status for an event.
     */
    @GetMapping("/status/{eventId}")
    public Map<String, Object> status(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);

        if (countersTableName == null || countersTableName.isBlank()) {
            return Map.of("error", "VWR DynamoDB not configured");
        }

        GetItemResponse result = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(countersTableName)
                .key(Map.of("eventId", AttributeValue.fromS(eventId.toString())))
                .build());

        if (!result.hasItem() || result.item().isEmpty()) {
            return Map.of("eventId", eventId.toString(), "status", "not_found");
        }

        var item = result.item();
        return Map.of(
                "eventId", eventId.toString(),
                "isActive", item.getOrDefault("isActive", AttributeValue.fromBool(false)).bool(),
                "nextPosition", Long.parseLong(item.getOrDefault("nextPosition", AttributeValue.fromN("0")).n()),
                "servingCounter", Long.parseLong(item.getOrDefault("servingCounter", AttributeValue.fromN("0")).n()),
                "waiting", Math.max(0,
                        Long.parseLong(item.getOrDefault("nextPosition", AttributeValue.fromN("0")).n())
                        - Long.parseLong(item.getOrDefault("servingCounter", AttributeValue.fromN("0")).n()))
        );
    }

    /**
     * Manually advance the serving counter for an event.
     */
    @PostMapping("/advance/{eventId}")
    public Map<String, Object> advance(
            @PathVariable UUID eventId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);

        if (countersTableName == null || countersTableName.isBlank()) {
            return Map.of("error", "VWR DynamoDB not configured");
        }

        int batchSize = DEFAULT_ADVANCE_BATCH_SIZE;
        if (body != null && body.containsKey("batchSize")) {
            batchSize = ((Number) body.get("batchSize")).intValue();
        }

        try {
            var result = dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(countersTableName)
                    .key(Map.of("eventId", AttributeValue.fromS(eventId.toString())))
                    .updateExpression("ADD servingCounter :batch SET updatedAt = :now")
                    .conditionExpression("attribute_exists(eventId) AND servingCounter < nextPosition")
                    .expressionAttributeValues(Map.of(
                            ":batch", AttributeValue.fromN(String.valueOf(batchSize)),
                            ":now", AttributeValue.fromN(String.valueOf(System.currentTimeMillis()))
                    ))
                    .returnValues("UPDATED_NEW")
                    .build());

            return Map.of(
                    "eventId", eventId.toString(),
                    "servingCounter", Long.parseLong(result.attributes().get("servingCounter").n()),
                    "advanced", batchSize
            );
        } catch (software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException e) {
            return Map.of(
                    "eventId", eventId.toString(),
                    "status", "caught_up",
                    "message", "servingCounter already >= nextPosition"
            );
        }
    }
}
