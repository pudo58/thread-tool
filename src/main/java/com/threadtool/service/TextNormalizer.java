package com.threadtool.service;

import com.threadtool.error.ApiException;

import java.util.Locale;

final class TextNormalizer {
    private TextNormalizer() {
    }

    static String normalizeLabel(String label) {
        String normalized = requireNotBlank(label, "label")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank()) {
            throw new ApiException(400, "INVALID_LABEL", "label must contain at least one letter or number");
        }
        return normalized;
    }

    static String validateUrl(String url) {
        String value = requireNotBlank(url, "url");
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            throw new ApiException(400, "INVALID_URL", "URL must start with http:// or https://");
        }
        return value;
    }

    static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "MISSING_FIELD", fieldName + " is required");
        }
        return value.trim();
    }
}
