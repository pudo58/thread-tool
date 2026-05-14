package com.threadtool.domain;

import com.threadtool.error.ApiException;

import java.util.Locale;

public enum DraftStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED;

    public static DraftStatus from(String value) {
        try {
            return DraftStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ApiException(400, "INVALID_STATUS", "Status must be PENDING_REVIEW, APPROVED, or REJECTED");
        }
    }
}
