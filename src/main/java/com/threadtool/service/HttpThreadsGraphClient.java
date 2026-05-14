package com.threadtool.service;

import com.threadtool.domain.ThreadsCredentials;
import com.threadtool.error.ApiException;
import com.threadtool.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpThreadsGraphClient implements ThreadsGraphClient {
    private static final String BASE_URL = "https://graph.threads.net/v1.0";

    private final HttpClient httpClient;

    public HttpThreadsGraphClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    HttpThreadsGraphClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Map<String, Object> createContainer(ThreadsCredentials credentials, Map<String, String> parameters) {
        Map<String, String> form = new LinkedHashMap<>(parameters);
        form.put("access_token", credentials.accessToken());
        return postForm(BASE_URL + "/" + encodePath(credentials.threadsUserId()) + "/threads", form);
    }

    @Override
    public Map<String, Object> publishContainer(ThreadsCredentials credentials, String creationId) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("creation_id", TextNormalizer.requireNotBlank(creationId, "creationId"));
        form.put("access_token", credentials.accessToken());
        return postForm(BASE_URL + "/" + encodePath(credentials.threadsUserId()) + "/threads_publish", form);
    }

    @Override
    public Map<String, Object> getContainerStatus(ThreadsCredentials credentials, String creationId) {
        String url = BASE_URL
                + "/"
                + encodePath(TextNormalizer.requireNotBlank(creationId, "creationId"))
                + "?access_token="
                + encode(credentials.accessToken());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return send(request);
    }

    private Map<String, Object> postForm(String url, Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                .build();
        return send(request);
    }

    private Map<String, Object> send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> body = response.body().isBlank()
                    ? new LinkedHashMap<>()
                    : Json.parseObject(response.body());
            if (response.statusCode() >= 400) {
                throw new ApiException(
                        502,
                        "THREADS_GRAPH_API_ERROR",
                        "Threads Graph API returned HTTP " + response.statusCode() + ": " + response.body()
                );
            }
            return body;
        } catch (IOException exception) {
            throw new ApiException(502, "THREADS_GRAPH_API_IO_ERROR", "Could not call Threads Graph API");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "THREADS_GRAPH_API_INTERRUPTED", "Threads Graph API request was interrupted");
        }
    }

    private static String formEncode(Map<String, String> parameters) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey()));
            builder.append('=');
            builder.append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
