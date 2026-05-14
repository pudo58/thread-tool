package com.threadtool.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record DraftComment(
        long id,
        long candidateId,
        String postUrl,
        String context,
        String affiliateLabel,
        String templateLabel,
        String language,
        String commentText,
        DraftStatus status,
        Instant createdAt
) {
    public DraftComment withStatus(DraftStatus newStatus) {
        return new DraftComment(
                id,
                candidateId,
                postUrl,
                context,
                affiliateLabel,
                templateLabel,
                language,
                commentText,
                newStatus,
                createdAt
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        if (candidateId > 0) {
            values.put("candidateId", candidateId);
        }
        values.put("postUrl", postUrl);
        values.put("context", context);
        values.put("affiliateLabel", affiliateLabel);
        values.put("templateLabel", templateLabel);
        values.put("language", language);
        values.put("commentText", commentText);
        values.put("status", status.name());
        values.put("createdAt", createdAt.toString());
        return values;
    }
}
