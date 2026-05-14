package com.threadtool.service;

import com.threadtool.domain.ThreadsCredentials;
import com.threadtool.domain.ThreadsMediaType;
import com.threadtool.error.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ThreadsPublishService {
    private static final int DEFAULT_VIDEO_STATUS_CHECKS = 6;
    private static final long DEFAULT_VIDEO_STATUS_INTERVAL_MILLIS = 10_000L;

    private final ThreadsGraphClient graphClient;

    public ThreadsPublishService(ThreadsGraphClient graphClient) {
        this.graphClient = graphClient;
    }

    public Map<String, Object> publishText(ThreadsCredentials credentials, String text) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("media_type", ThreadsMediaType.TEXT.name());
        parameters.put("text", TextNormalizer.requireNotBlank(text, "text"));
        return createAndPublish(credentials, ThreadsMediaType.TEXT, parameters);
    }

    public Map<String, Object> publishImage(ThreadsCredentials credentials, String text, String imageUrl) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("media_type", ThreadsMediaType.IMAGE.name());
        parameters.put("image_url", TextNormalizer.validateUrl(imageUrl));
        parameters.put("text", TextNormalizer.requireNotBlank(text, "text"));
        return createAndPublish(credentials, ThreadsMediaType.IMAGE, parameters);
    }

    public Map<String, Object> publishCarousel(ThreadsCredentials credentials, String text, List<String> imageUrls) {
        List<String> normalizedUrls = validateImageUrls(imageUrls);
        List<String> childIds = new ArrayList<>();
        List<Map<String, Object>> childContainers = new ArrayList<>();
        for (String imageUrl : normalizedUrls) {
            Map<String, String> childParameters = new LinkedHashMap<>();
            childParameters.put("media_type", ThreadsMediaType.IMAGE.name());
            childParameters.put("image_url", imageUrl);
            childParameters.put("is_carousel_item", "true");
            Map<String, Object> childContainer = graphClient.createContainer(credentials, childParameters);
            childContainers.add(childContainer);
            childIds.add(extractId(childContainer, "carousel child creation"));
        }

        Map<String, String> parentParameters = new LinkedHashMap<>();
        parentParameters.put("media_type", ThreadsMediaType.CAROUSEL.name());
        parentParameters.put("children", String.join(",", childIds));
        parentParameters.put("text", TextNormalizer.requireNotBlank(text, "text"));
        Map<String, Object> parentContainer = graphClient.createContainer(credentials, parentParameters);
        Map<String, Object> published = graphClient.publishContainer(credentials, extractId(parentContainer, "carousel creation"));

        Map<String, Object> result = baseResult(ThreadsMediaType.CAROUSEL, parentContainer, published);
        result.put("children", childContainers);
        result.put("childCreationIds", childIds);
        return result;
    }

    public Map<String, Object> publishVideo(
            ThreadsCredentials credentials,
            String text,
            String videoUrl,
            boolean waitForReady,
            int maxStatusChecks,
            long statusCheckIntervalMillis
    ) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("media_type", ThreadsMediaType.VIDEO.name());
        parameters.put("video_url", TextNormalizer.validateUrl(videoUrl));
        parameters.put("text", TextNormalizer.requireNotBlank(text, "text"));
        Map<String, Object> created = graphClient.createContainer(credentials, parameters);
        String creationId = extractId(created, "video creation");
        List<Map<String, Object>> statusChecks = waitForReady
                ? waitForVideoReady(credentials, creationId, maxStatusChecks, statusCheckIntervalMillis)
                : List.of();
        Map<String, Object> published = graphClient.publishContainer(credentials, creationId);

        Map<String, Object> result = baseResult(ThreadsMediaType.VIDEO, created, published);
        result.put("statusChecks", statusChecks);
        return result;
    }

    public Map<String, Object> publishExistingContainer(ThreadsCredentials credentials, String creationId) {
        Map<String, Object> published = graphClient.publishContainer(credentials, creationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("creationId", creationId);
        result.put("published", published);
        result.put("postId", extractId(published, "publish"));
        return result;
    }

    public Map<String, Object> getContainerStatus(ThreadsCredentials credentials, String creationId) {
        return graphClient.getContainerStatus(credentials, creationId);
    }

    public static int defaultVideoStatusChecks() {
        return DEFAULT_VIDEO_STATUS_CHECKS;
    }

    public static long defaultVideoStatusIntervalMillis() {
        return DEFAULT_VIDEO_STATUS_INTERVAL_MILLIS;
    }

    private Map<String, Object> createAndPublish(
            ThreadsCredentials credentials,
            ThreadsMediaType mediaType,
            Map<String, String> parameters
    ) {
        Map<String, Object> created = graphClient.createContainer(credentials, parameters);
        Map<String, Object> published = graphClient.publishContainer(credentials, extractId(created, mediaType.name().toLowerCase() + " creation"));
        return baseResult(mediaType, created, published);
    }

    private List<Map<String, Object>> waitForVideoReady(
            ThreadsCredentials credentials,
            String creationId,
            int maxStatusChecks,
            long statusCheckIntervalMillis
    ) {
        int checks = Math.max(1, maxStatusChecks);
        long interval = Math.max(0, statusCheckIntervalMillis);
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (int index = 0; index < checks; index++) {
            Map<String, Object> status = graphClient.getContainerStatus(credentials, creationId);
            statuses.add(status);
            if (!"IN_PROGRESS".equals(String.valueOf(status.getOrDefault("status", "")))) {
                return statuses;
            }
            sleep(interval);
        }
        throw new ApiException(409, "THREADS_VIDEO_NOT_READY", "Video container is still IN_PROGRESS");
    }

    private static Map<String, Object> baseResult(
            ThreadsMediaType mediaType,
            Map<String, Object> created,
            Map<String, Object> published
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mediaType", mediaType.name());
        result.put("creationId", extractId(created, mediaType.name().toLowerCase() + " creation"));
        result.put("postId", extractId(published, "publish"));
        result.put("created", created);
        result.put("published", published);
        return result;
    }

    private static String extractId(Map<String, Object> response, String operation) {
        Object id = response.get("id");
        if (id instanceof String stringId && !stringId.isBlank()) {
            return stringId;
        }
        throw new ApiException(502, "THREADS_GRAPH_API_MISSING_ID", "Threads Graph API response for " + operation + " did not include id");
    }

    private static List<String> validateImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new ApiException(400, "IMAGE_URLS_REQUIRED", "imageUrls must contain at least one image URL");
        }
        List<String> normalized = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            normalized.add(TextNormalizer.validateUrl(imageUrl));
        }
        return normalized;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "THREADS_VIDEO_WAIT_INTERRUPTED", "Interrupted while waiting for video processing");
        }
    }
}
