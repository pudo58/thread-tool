package com.threadtool.service;

import com.threadtool.domain.ThreadsCredentials;

import java.util.Map;

public interface ThreadsGraphClient {
    Map<String, Object> createContainer(ThreadsCredentials credentials, Map<String, String> parameters);

    Map<String, Object> publishContainer(ThreadsCredentials credentials, String creationId);

    Map<String, Object> getContainerStatus(ThreadsCredentials credentials, String creationId);
}
