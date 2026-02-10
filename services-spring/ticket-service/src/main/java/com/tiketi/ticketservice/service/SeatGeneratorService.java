package com.tiketi.ticketservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SeatGeneratorService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SeatGeneratorService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int generateSeatsForEvent(UUID eventId, UUID seatLayoutId) {
        Map<String, Object> layoutRow = jdbcTemplate.queryForList(
            "SELECT layout_config FROM seat_layouts WHERE id = ?", seatLayoutId).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat layout not found"));

        JsonNode layoutConfig = toJson(layoutRow.get("layout_config"));
        JsonNode sections = layoutConfig.path("sections");
        if (!sections.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid seat layout config");
        }

        int created = 0;
        for (JsonNode section : sections) {
            String sectionName = section.path("name").asText(null);
            int rows = section.path("rows").asInt(0);
            int seatsPerRow = section.path("seatsPerRow").asInt(0);
            int price = section.path("price").asInt(0);
            int startRow = section.path("startRow").asInt(1);

            if (sectionName == null || sectionName.isBlank() || rows <= 0 || seatsPerRow <= 0) {
                continue;
            }

            for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
                int rowNumber = startRow + rowIndex;
                for (int seatNumber = 1; seatNumber <= seatsPerRow; seatNumber++) {
                    String seatLabel = sectionName + "-" + rowNumber + "-" + seatNumber;
                    jdbcTemplate.update("""
                        INSERT INTO seats (event_id, section, row_number, seat_number, seat_label, price, status)
                        VALUES (?, ?, ?, ?, ?, ?, 'available')
                        """, eventId, sectionName, rowNumber, seatNumber, seatLabel, price);
                    created++;
                }
            }
        }
        return created;
    }

    public int countSeats(UUID eventId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seats WHERE event_id = ?", Integer.class, eventId);
        return count != null ? count : 0;
    }

    @Transactional
    public int deleteSeatsForEvent(UUID eventId) {
        return jdbcTemplate.update("DELETE FROM seats WHERE event_id = ?", eventId);
    }

    @SuppressWarnings("unchecked")
    private JsonNode toJson(Object value) {
        try {
            if (value == null) {
                return objectMapper.createObjectNode();
            }
            if (value instanceof String stringValue) {
                return objectMapper.readTree(stringValue);
            }
            // Handle JSONB wrapper: {"type":"jsonb","value":"{...}"}
            if (value instanceof Map) {
                Object inner = ((Map<String, Object>) value).get("value");
                if (inner instanceof String innerStr) {
                    return objectMapper.readTree(innerStr);
                }
            }
            // Handle PGobject or other types with toString()
            String str = value.toString();
            return objectMapper.readTree(str);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid seat layout config");
        }
    }
}
