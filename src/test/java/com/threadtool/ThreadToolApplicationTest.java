package com.threadtool;

import com.threadtool.domain.DraftStatus;
import com.threadtool.error.ApiException;
import com.threadtool.service.ApplicationState;
import com.threadtool.util.Json;

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
}
