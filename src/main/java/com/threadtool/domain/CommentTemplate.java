package com.threadtool.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record CommentTemplate(
        long id,
        String label,
        String language,
        String template,
        boolean active,
        Instant updatedAt
) {
    public static final String DEFAULT_LABEL = "default";
    public static final String ANY_LANGUAGE = "any";
    public static final String CONTEXT_TOKEN = "[context]";
    public static final String AFFILIATE_TOKEN = "[link_affiliate]";

    public String render(String context, String affiliateUrl) {
        return template
                .replace(CONTEXT_TOKEN, context)
                .replace(AFFILIATE_TOKEN, affiliateUrl)
                .trim();
    }

    public boolean matchesLanguage(String requestedLanguage) {
        String normalized = normalizeLanguage(requestedLanguage);
        return ANY_LANGUAGE.equals(language) || language.equals(normalized);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("label", label);
        values.put("language", language);
        values.put("template", template);
        values.put("active", active);
        values.put("updatedAt", updatedAt.toString());
        return values;
    }

    public static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return ANY_LANGUAGE;
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }
}
