package com.threadtool.service;

import com.threadtool.error.ApiException;

public final class ThreadsAutomationGuard {
    private ThreadsAutomationGuard() {
    }

    public static ApiException blocked(String operation) {
        return new ApiException(
                403,
                "THREADS_AUTOMATION_DISABLED",
                operation + " is not supported. This backend only prepares reviewable comment drafts."
        );
    }
}
