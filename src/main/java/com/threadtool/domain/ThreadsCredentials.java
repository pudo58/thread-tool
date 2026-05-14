package com.threadtool.domain;

import com.threadtool.error.ApiException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record ThreadsCredentials(String threadsUserId, String accessToken) {
    public ThreadsCredentials {
        if (threadsUserId == null || threadsUserId.isBlank()) {
            throw new ApiException(400, "THREADS_USER_ID_REQUIRED", "threadsUserId is required");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new ApiException(400, "THREADS_ACCESS_TOKEN_REQUIRED", "accessToken is required");
        }
        threadsUserId = threadsUserId.trim();
        accessToken = accessToken.trim();
    }

    public static Optional<ThreadsCredentials> fromEnvironment() {
        String userId = System.getenv("THREADS_USER_ID");
        String token = System.getenv("THREADS_ACCESS_TOKEN");
        if (userId == null || userId.isBlank() || token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ThreadsCredentials(userId, token));
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("threadsUserId", threadsUserId);
        values.put("accessTokenConfigured", true);
        values.put("accessTokenPreview", redact(accessToken));
        return values;
    }

    private static String redact(String value) {
        if (value.length() <= 8) {
            return "********";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
