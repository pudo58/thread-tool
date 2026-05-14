package com.threadtool;

import com.threadtool.domain.DraftStatus;
import com.threadtool.domain.ThreadsCredentials;
import com.threadtool.error.ApiException;
import com.threadtool.service.ApplicationState;
import com.threadtool.service.ThreadsGraphClient;
import com.threadtool.service.ThreadsPublishService;
import com.threadtool.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ThreadToolApplicationTest {
    private ThreadToolApplicationTest() {
    }

    public static void main(String[] args) {
        createsDraftWithConfiguredShopeeLink();
        requiresAffiliateLinkBeforeDrafting();
        validatesTemplatePlaceholders();
        selectsTemplateByLanguageForCandidates();
        configuresThreadsGraphCredentialsSafely();
        publishesTextWithOfficialGraphApiFlow();
        publishesCarouselWithChildContainers();
        parsesAndStringifiesJson();
    }

    private static void createsDraftWithConfiguredShopeeLink() {
        ApplicationState state = new ApplicationState();
        state.upsertAffiliateLink("Shopee Main", "https://s.shopee.vn/example-affiliate", true);

        Map<String, Object> draft = state.createDraft(
                "https://www.threads.net/@brand/post/123",
                "Deal nay dang hot",
                Optional.of("shopee-main")
        );
        assertEquals("Deal nay dang hot https://s.shopee.vn/example-affiliate", draft.get("commentText"));
        assertEquals("PENDING_REVIEW", draft.get("status"));

        state.setDraftStatus((Long) draft.get("id"), DraftStatus.APPROVED);
        Map<String, Object> exported = state.exportDrafts("approved");

        assertEquals(1, exported.get("count"));
        assertEquals("Deal nay dang hot https://s.shopee.vn/example-affiliate", exported.get("content"));
    }

    private static void requiresAffiliateLinkBeforeDrafting() {
        ApplicationState state = new ApplicationState();
        expectApiException(() -> state.createDraft("", "Can mua thi xem link nay", Optional.empty()));
    }

    private static void validatesTemplatePlaceholders() {
        ApplicationState state = new ApplicationState();
        expectApiException(() -> state.updateTemplate("[context]"));
        state.updateTemplate("[context]\n[link_affiliate]");
    }

    private static void selectsTemplateByLanguageForCandidates() {
        ApplicationState state = new ApplicationState();
        state.upsertAffiliateLink("Shopee Main", "https://s.shopee.vn/example-affiliate", true);
        state.upsertCommentTemplate(
                "vietnamese-soft",
                "vi",
                "[context] Link minh de day nha: [link_affiliate]",
                true
        );

        Map<String, Object> result = state.importCandidateAndCreateDraft(
                "https://www.threads.net/@brand/post/456",
                "Bai nay dang viral",
                "vi",
                12000,
                Optional.of("shopee-main"),
                Optional.empty()
        );
        Map<?, ?> draft = (Map<?, ?>) result.get("draft");

        assertEquals("vietnamese-soft", draft.get("templateLabel"));
        assertEquals("vi", draft.get("language"));
        assertEquals("Bai nay dang viral Link minh de day nha: https://s.shopee.vn/example-affiliate", draft.get("commentText"));
    }

    private static void configuresThreadsGraphCredentialsSafely() {
        ApplicationState state = new ApplicationState();
        Map<String, Object> config = state.configureThreadsGraph("9213298915445740", "abc123456789xyz");

        assertEquals("9213298915445740", config.get("threadsUserId"));
        assertEquals(true, config.get("accessTokenConfigured"));
        assertEquals("abc1...9xyz", config.get("accessTokenPreview"));
        assertEquals("abc123456789xyz", state.resolveThreadsCredentials(Optional.empty(), Optional.empty()).accessToken());
    }

    private static void publishesTextWithOfficialGraphApiFlow() {
        FakeThreadsGraphClient graphClient = new FakeThreadsGraphClient();
        ThreadsPublishService service = new ThreadsPublishService(graphClient);

        Map<String, Object> result = service.publishText(
                new ThreadsCredentials("user-1", "token-1"),
                "Hello Threads"
        );

        assertEquals("TEXT", result.get("mediaType"));
        assertEquals("container-1", result.get("creationId"));
        assertEquals("post-1", result.get("postId"));
        assertEquals("TEXT", graphClient.createdParameters.getFirst().get("media_type"));
        assertEquals("Hello Threads", graphClient.createdParameters.getFirst().get("text"));
    }

    private static void publishesCarouselWithChildContainers() {
        FakeThreadsGraphClient graphClient = new FakeThreadsGraphClient();
        ThreadsPublishService service = new ThreadsPublishService(graphClient);

        Map<String, Object> result = service.publishCarousel(
                new ThreadsCredentials("user-1", "token-1"),
                "Carousel caption",
                List.of("https://example.com/1.jpg", "https://example.com/2.jpg")
        );

        assertEquals("CAROUSEL", result.get("mediaType"));
        assertEquals(3, graphClient.createdParameters.size());
        assertEquals("true", graphClient.createdParameters.get(0).get("is_carousel_item"));
        assertEquals("container-1,container-2", graphClient.createdParameters.get(2).get("children"));
        assertEquals("post-1", result.get("postId"));
    }

    private static void parsesAndStringifiesJson() {
        Map<String, Object> parsed = Json.parseObject("""
                {"label":"Shopee","active":true,"count":2}
                """);

        assertEquals("Shopee", parsed.get("label"));
        assertEquals(true, parsed.get("active"));
        assertEquals(2L, parsed.get("count"));
        assertEquals("{\"message\":\"hello\\nworld\"}", Json.stringify(Map.of("message", "hello\nworld")));
    }

    private static void expectApiException(Runnable runnable) {
        try {
            runnable.run();
        } catch (ApiException exception) {
            return;
        }
        throw new AssertionError("Expected ApiException");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static final class FakeThreadsGraphClient implements ThreadsGraphClient {
        private final List<Map<String, String>> createdParameters = new ArrayList<>();
        private int containerSequence = 1;
        private int postSequence = 1;

        @Override
        public Map<String, Object> createContainer(ThreadsCredentials credentials, Map<String, String> parameters) {
            createdParameters.add(new LinkedHashMap<>(parameters));
            return Map.of("id", "container-" + containerSequence++);
        }

        @Override
        public Map<String, Object> publishContainer(ThreadsCredentials credentials, String creationId) {
            return Map.of("id", "post-" + postSequence++, "creation_id", creationId);
        }

        @Override
        public Map<String, Object> getContainerStatus(ThreadsCredentials credentials, String creationId) {
            return Map.of("id", creationId, "status", "FINISHED");
        }
    }
}
