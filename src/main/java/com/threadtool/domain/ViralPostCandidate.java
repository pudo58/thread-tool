package com.threadtool.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ViralPostCandidate(
        long id,
        String postUrl,
        String context,
        String language,
        long engagementScore,
        Instant createdAt
) {
    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("postUrl", postUrl);
        values.put("context", context);
        values.put("language", language);
        values.put("engagementScore", engagementScore);
        values.put("createdAt", createdAt.toString());
        return values;
    }
}
