package com.tiketi.catalogservice.shared.util;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PreSaleSchedule {

    private PreSaleSchedule() {}

    public static Map<String, Object> compute(OffsetDateTime saleStartDate) {
        if (saleStartDate == null) return Map.of();
        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("diamond", saleStartDate.toString());
        schedule.put("gold", saleStartDate.plusHours(1).toString());
        schedule.put("silver", saleStartDate.plusHours(24).toString());
        schedule.put("bronze", saleStartDate.plusHours(48).toString());
        return schedule;
    }

    public static String getAccessibleTier(OffsetDateTime saleStartDate, OffsetDateTime now) {
        if (saleStartDate == null || now.isBefore(saleStartDate)) return null;
        if (now.isBefore(saleStartDate.plusHours(1))) return "DIAMOND";
        if (now.isBefore(saleStartDate.plusHours(24))) return "GOLD";
        if (now.isBefore(saleStartDate.plusHours(48))) return "SILVER";
        return "BRONZE";
    }
}
