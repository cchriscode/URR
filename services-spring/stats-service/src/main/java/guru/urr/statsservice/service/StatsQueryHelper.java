package guru.urr.statsservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

final class StatsQueryHelper {

    private static final int BUDGET_THRESHOLD = 50_000;
    private static final int STANDARD_THRESHOLD = 100_000;
    private static final int PREMIUM_THRESHOLD = 150_000;
    static final float HOURS_IN_DAY = 24.0f;

    private StatsQueryHelper() {}

    static Map<String, Object> firstRow(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    static int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Long longValue) {
            return longValue.intValue();
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.intValue();
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    static double toRate(int total, int part) {
        if (total <= 0) {
            return 0;
        }
        return round2((part * 100.0) / total);
    }

    static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    static String toPriceTier(int averagePrice) {
        if (averagePrice < BUDGET_THRESHOLD) {
            return "budget";
        }
        if (averagePrice < STANDARD_THRESHOLD) {
            return "standard";
        }
        if (averagePrice < PREMIUM_THRESHOLD) {
            return "premium";
        }
        return "vip";
    }
}
