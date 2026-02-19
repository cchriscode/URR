package guru.urr.common.util;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PreSaleSchedule {

    public static final int GOLD_DELAY_HOURS = 1;
    public static final int SILVER_DELAY_HOURS = 24;
    public static final int BRONZE_DELAY_HOURS = 48;

    private PreSaleSchedule() {}

    /**
     * Compute tier-based pre-sale schedule from the event's sale_start_date.
     * sale_start_date is treated as the Diamond (earliest) pre-sale time.
     */
    public static Map<String, Object> compute(OffsetDateTime saleStartDate) {
        if (saleStartDate == null) return Map.of();
        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("diamond", saleStartDate.toString());
        schedule.put("gold", saleStartDate.plusHours(GOLD_DELAY_HOURS).toString());
        schedule.put("silver", saleStartDate.plusHours(SILVER_DELAY_HOURS).toString());
        schedule.put("bronze", saleStartDate.plusHours(BRONZE_DELAY_HOURS).toString());
        return schedule;
    }

    /**
     * Determine which tier can currently access ticket purchase.
     * Returns the minimum tier that has access at the given time.
     * Returns null if no tier has access yet.
     */
    public static String getAccessibleTier(OffsetDateTime saleStartDate, OffsetDateTime now) {
        if (saleStartDate == null || now.isBefore(saleStartDate)) return null;
        if (now.isBefore(saleStartDate.plusHours(GOLD_DELAY_HOURS))) return "DIAMOND";
        if (now.isBefore(saleStartDate.plusHours(SILVER_DELAY_HOURS))) return "GOLD";
        if (now.isBefore(saleStartDate.plusHours(BRONZE_DELAY_HOURS))) return "SILVER";
        return "BRONZE";
    }
}
