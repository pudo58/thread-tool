package com.threadtool.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record AffiliateLink(long id, String label, String url, boolean active, Instant updatedAt) {
    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("label", label);
        values.put("url", url);
        values.put("active", active);
        values.put("updatedAt", updatedAt.toString());
        return values;
    }
}
