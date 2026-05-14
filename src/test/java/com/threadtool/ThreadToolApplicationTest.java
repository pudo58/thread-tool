package com.threadtool;

import java.util.Map;
import java.util.Optional;

public final class ThreadToolApplicationTest {
    private ThreadToolApplicationTest() {
    }

    public static void main(String[] args) {
        createsDraftWithConfiguredShopeeLink();
        requiresAffiliateLinkBeforeDrafting();
        validatesTemplatePlaceholders();
        parsesAndStringifiesJson();
    }

    private static void createsDraftWithConfiguredShopeeLink() {
        ThreadToolApplication.ApplicationState state = new ThreadToolApplication.ApplicationState();
        state.upsertAffiliateLink("Shopee Main", "https://s.shopee.vn/example-affiliate", true);

        Map<String, Object> draft = state.createDraft(
                "https://www.threads.net/@brand/post/123",
                "Deal nay dang hot",
                Optional.of("shopee-main")
        );
        assertEquals("Deal nay dang hot https://s.shopee.vn/example-affiliate", draft.get("commentText"));
        assertEquals("PENDING_REVIEW", draft.get("status"));

        state.setDraftStatus((Long) draft.get("id"), ThreadToolApplication.DraftStatus.APPROVED);
        Map<String, Object> exported = state.exportDrafts("approved");

        assertEquals(1, exported.get("count"));
        assertEquals("Deal nay dang hot https://s.shopee.vn/example-affiliate", exported.get("content"));
    }

    private static void requiresAffiliateLinkBeforeDrafting() {
        ThreadToolApplication.ApplicationState state = new ThreadToolApplication.ApplicationState();
        expectApiException(() -> state.createDraft("", "Can mua thi xem link nay", Optional.empty()));
    }

    private static void validatesTemplatePlaceholders() {
        ThreadToolApplication.ApplicationState state = new ThreadToolApplication.ApplicationState();
        expectApiException(() -> state.updateTemplate("[context]"));
        state.updateTemplate("[context]\n[link_affiliate]");
    }

    private static void parsesAndStringifiesJson() {
        Map<String, Object> parsed = ThreadToolApplication.Json.parseObject("""
                {"label":"Shopee","active":true,"count":2}
                """);

        assertEquals("Shopee", parsed.get("label"));
        assertEquals(true, parsed.get("active"));
        assertEquals(2L, parsed.get("count"));
        assertEquals("{\"message\":\"hello\\nworld\"}", ThreadToolApplication.Json.stringify(Map.of("message", "hello\nworld")));
    }

    private static void expectApiException(Runnable runnable) {
        try {
            runnable.run();
        } catch (ThreadToolApplication.ApiException exception) {
            return;
        }
        throw new AssertionError("Expected ApiException");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
