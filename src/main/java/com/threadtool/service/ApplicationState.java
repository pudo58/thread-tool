package com.threadtool.service;

import com.threadtool.domain.AffiliateLink;
import com.threadtool.domain.CommentTemplate;
import com.threadtool.domain.DraftComment;
import com.threadtool.domain.DraftStatus;
import com.threadtool.domain.ViralPostCandidate;
import com.threadtool.error.ApiException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class ApplicationState {
    private static final String DEFAULT_TEMPLATE = "[context] [link_affiliate]";

    private final AtomicLong affiliateSequence = new AtomicLong(1);
    private final AtomicLong templateSequence = new AtomicLong(1);
    private final AtomicLong draftSequence = new AtomicLong(1);
    private final AtomicLong candidateSequence = new AtomicLong(1);
    private final Map<String, AffiliateLink> affiliateLinksByLabel = new LinkedHashMap<>();
    private final Map<String, CommentTemplate> templatesByLabel = new LinkedHashMap<>();
    private final Map<Long, DraftComment> draftsById = new LinkedHashMap<>();
    private final Map<Long, ViralPostCandidate> candidatesById = new LinkedHashMap<>();

    public ApplicationState() {
        upsertCommentTemplate(CommentTemplate.DEFAULT_LABEL, CommentTemplate.ANY_LANGUAGE, DEFAULT_TEMPLATE, true);
    }

    public synchronized Map<String, Object> snapshotConfig() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("template", templatesByLabel.get(CommentTemplate.DEFAULT_LABEL).template());
        values.put("templates", listCommentTemplates());
        values.put("affiliateLinks", listAffiliateLinks());
        values.put("postingMode", "manual_review_only");
        values.put("threadsAutomation", Map.of(
                "loginCredentials", false,
                "viralDiscovery", false,
                "autoComment", false
        ));
        return values;
    }

    public synchronized Map<String, Object> updateTemplate(String newTemplate) {
        return upsertCommentTemplate(
                CommentTemplate.DEFAULT_LABEL,
                CommentTemplate.ANY_LANGUAGE,
                newTemplate,
                true
        );
    }

    public synchronized Map<String, Object> upsertCommentTemplate(
            String label,
            String language,
            String template,
            boolean active
    ) {
        String normalizedLabel = TextNormalizer.normalizeLabel(label);
        String normalizedLanguage = CommentTemplate.normalizeLanguage(language);
        validateTemplate(template);
        CommentTemplate existing = templatesByLabel.get(normalizedLabel);
        long id = existing == null ? templateSequence.getAndIncrement() : existing.id();
        CommentTemplate commentTemplate = new CommentTemplate(
                id,
                normalizedLabel,
                normalizedLanguage,
                template.trim(),
                active,
                Instant.now()
        );
        templatesByLabel.put(normalizedLabel, commentTemplate);
        return commentTemplate.toMap();
    }

    public synchronized List<Map<String, Object>> listCommentTemplates() {
        return templatesByLabel.values().stream()
                .map(CommentTemplate::toMap)
                .toList();
    }

    public synchronized Map<String, Object> upsertAffiliateLink(String label, String url, boolean active) {
        String normalizedLabel = TextNormalizer.normalizeLabel(label);
        String normalizedUrl = TextNormalizer.validateUrl(url);
        AffiliateLink existing = affiliateLinksByLabel.get(normalizedLabel);
        long id = existing == null ? affiliateSequence.getAndIncrement() : existing.id();
        AffiliateLink affiliateLink = new AffiliateLink(id, normalizedLabel, normalizedUrl, active, Instant.now());
        affiliateLinksByLabel.put(normalizedLabel, affiliateLink);
        return affiliateLink.toMap();
    }

    public synchronized List<Map<String, Object>> listAffiliateLinks() {
        return affiliateLinksByLabel.values().stream()
                .map(AffiliateLink::toMap)
                .toList();
    }

    public synchronized Map<String, Object> createDraft(
            String postUrl,
            String context,
            Optional<String> affiliateLabel
    ) {
        return createDraft(postUrl, context, affiliateLabel, Optional.empty(), Optional.empty(), 0);
    }

    public synchronized Map<String, Object> createDraft(
            String postUrl,
            String context,
            Optional<String> affiliateLabel,
            Optional<String> templateLabel,
            Optional<String> language,
            long candidateId
    ) {
        String normalizedContext = TextNormalizer.requireNotBlank(context, "context");
        String normalizedLanguage = CommentTemplate.normalizeLanguage(language.orElse(CommentTemplate.ANY_LANGUAGE));
        AffiliateLink affiliateLink = resolveAffiliateLink(affiliateLabel);
        CommentTemplate template = resolveTemplate(templateLabel, normalizedLanguage);
        String commentText = template.render(normalizedContext, affiliateLink.url());
        DraftComment draft = new DraftComment(
                draftSequence.getAndIncrement(),
                candidateId,
                postUrl == null ? "" : postUrl.trim(),
                normalizedContext,
                affiliateLink.label(),
                template.label(),
                normalizedLanguage,
                commentText,
                DraftStatus.PENDING_REVIEW,
                Instant.now()
        );
        draftsById.put(draft.id(), draft);
        return draft.toMap();
    }

    public synchronized Map<String, Object> importCandidateAndCreateDraft(
            String postUrl,
            String context,
            String language,
            long engagementScore,
            Optional<String> affiliateLabel,
            Optional<String> templateLabel
    ) {
        ViralPostCandidate candidate = addCandidate(postUrl, context, language, engagementScore);
        Map<String, Object> draft = createDraft(
                candidate.postUrl(),
                candidate.context(),
                affiliateLabel,
                templateLabel,
                Optional.of(candidate.language()),
                candidate.id()
        );
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("candidate", candidate.toMap());
        values.put("draft", draft);
        return values;
    }

    public synchronized List<Map<String, Object>> listCandidates() {
        return candidatesById.values().stream()
                .map(ViralPostCandidate::toMap)
                .toList();
    }

    public synchronized List<Map<String, Object>> listDrafts() {
        return draftsById.values().stream()
                .map(DraftComment::toMap)
                .toList();
    }

    public synchronized Map<String, Object> getDraft(long draftId) {
        return findDraft(draftId).toMap();
    }

    public synchronized Map<String, Object> setDraftStatus(long draftId, DraftStatus status) {
        DraftComment draft = findDraft(draftId);
        DraftComment updated = draft.withStatus(status);
        draftsById.put(draftId, updated);
        return updated.toMap();
    }

    public synchronized Map<String, Object> exportDrafts(String rawStatus) {
        DraftStatus status = DraftStatus.from(rawStatus);
        List<Map<String, Object>> matchingDrafts = draftsById.values().stream()
                .filter(draft -> draft.status() == status)
                .map(DraftComment::toMap)
                .toList();
        String content = draftsById.values().stream()
                .filter(draft -> draft.status() == status)
                .map(DraftComment::commentText)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", status.name());
        values.put("count", matchingDrafts.size());
        values.put("content", content);
        values.put("drafts", matchingDrafts);
        return values;
    }

    private ViralPostCandidate addCandidate(String postUrl, String context, String language, long engagementScore) {
        ViralPostCandidate candidate = new ViralPostCandidate(
                candidateSequence.getAndIncrement(),
                TextNormalizer.validateUrl(postUrl),
                TextNormalizer.requireNotBlank(context, "context"),
                CommentTemplate.normalizeLanguage(language),
                Math.max(0, engagementScore),
                Instant.now()
        );
        candidatesById.put(candidate.id(), candidate);
        return candidate;
    }

    private DraftComment findDraft(long draftId) {
        DraftComment draft = draftsById.get(draftId);
        if (draft == null) {
            throw new ApiException(404, "DRAFT_NOT_FOUND", "Draft comment not found");
        }
        return draft;
    }

    private AffiliateLink resolveAffiliateLink(Optional<String> affiliateLabel) {
        if (affiliateLinksByLabel.isEmpty()) {
            throw new ApiException(400, "AFFILIATE_LINK_REQUIRED", "Configure at least one affiliate link before creating drafts");
        }

        if (affiliateLabel.isPresent() && !affiliateLabel.get().isBlank()) {
            String label = TextNormalizer.normalizeLabel(affiliateLabel.get());
            AffiliateLink link = affiliateLinksByLabel.get(label);
            if (link == null) {
                throw new ApiException(404, "AFFILIATE_LINK_NOT_FOUND", "Affiliate link label was not found");
            }
            if (!link.active()) {
                throw new ApiException(400, "AFFILIATE_LINK_INACTIVE", "Affiliate link is inactive");
            }
            return link;
        }

        return affiliateLinksByLabel.values().stream()
                .filter(AffiliateLink::active)
                .findFirst()
                .orElseThrow(() -> new ApiException(400, "AFFILIATE_LINK_REQUIRED", "No active affiliate link is configured"));
    }

    private CommentTemplate resolveTemplate(Optional<String> templateLabel, String language) {
        if (templateLabel.isPresent() && !templateLabel.get().isBlank()) {
            String label = TextNormalizer.normalizeLabel(templateLabel.get());
            CommentTemplate template = templatesByLabel.get(label);
            if (template == null) {
                throw new ApiException(404, "TEMPLATE_NOT_FOUND", "Template label was not found");
            }
            if (!template.active()) {
                throw new ApiException(400, "TEMPLATE_INACTIVE", "Template is inactive");
            }
            return template;
        }

        Optional<CommentTemplate> languageTemplate = templatesByLabel.values().stream()
                .filter(CommentTemplate::active)
                .filter(template -> template.language().equals(language))
                .findFirst();
        if (languageTemplate.isPresent()) {
            return languageTemplate.get();
        }

        return templatesByLabel.values().stream()
                .filter(CommentTemplate::active)
                .filter(template -> template.matchesLanguage(language))
                .findFirst()
                .orElseThrow(() -> new ApiException(400, "TEMPLATE_REQUIRED", "No active template is configured"));
    }

    private static void validateTemplate(String template) {
        String value = TextNormalizer.requireNotBlank(template, "template");
        if (!value.contains(CommentTemplate.CONTEXT_TOKEN) || !value.contains(CommentTemplate.AFFILIATE_TOKEN)) {
            throw new ApiException(400, "INVALID_TEMPLATE", "Template must contain [context] and [link_affiliate]");
        }
    }
}
