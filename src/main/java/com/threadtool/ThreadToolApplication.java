package com.threadtool;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class ThreadToolApplication {
    private static final int DEFAULT_PORT = 8080;

    private ThreadToolApplication() {
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ApiHandler(new ApplicationState()));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.printf("Thread draft tool is running on http://localhost:%d%n", port);
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Integer.parseInt(args[0]);
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }
        return DEFAULT_PORT;
    }

    static final class ApiHandler implements HttpHandler {
        private final ApplicationState state;

        ApiHandler(ApplicationState state) {
            this.state = state;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                addCorsHeaders(exchange);
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 204, Map.of());
                    return;
                }

                String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
                String path = normalizePath(exchange.getRequestURI());
                route(exchange, method, path);
            } catch (ApiException exception) {
                sendJson(exchange, exception.statusCode, Map.of(
                        "error", exception.code,
                        "message", exception.getMessage()
                ));
            } catch (RuntimeException exception) {
                sendJson(exchange, 500, Map.of(
                        "error", "INTERNAL_ERROR",
                        "message", "Unexpected server error"
                ));
            }
        }

        private void route(HttpExchange exchange, String method, String path) throws IOException {
            if ("GET".equals(method) && "/health".equals(path)) {
                sendJson(exchange, 200, Map.of(
                        "status", "ok",
                        "service", "thread-affiliate-draft-tool"
                ));
                return;
            }

            if ("GET".equals(method) && "/api/config".equals(path)) {
                sendJson(exchange, 200, state.snapshotConfig());
                return;
            }

            if ("PUT".equals(method) && "/api/config/template".equals(path)) {
                Map<String, Object> body = readJsonObject(exchange);
                String template = requiredString(body, "template");
                sendJson(exchange, 200, state.updateTemplate(template));
                return;
            }

            if ("GET".equals(method) && "/api/config/affiliate-links".equals(path)) {
                sendJson(exchange, 200, Map.of("affiliateLinks", state.listAffiliateLinks()));
                return;
            }

            if ("POST".equals(method) && "/api/config/affiliate-links".equals(path)) {
                Map<String, Object> body = readJsonObject(exchange);
                String label = requiredString(body, "label");
                String url = requiredString(body, "url");
                boolean active = optionalBoolean(body, "active").orElse(true);
                sendJson(exchange, 201, state.upsertAffiliateLink(label, url, active));
                return;
            }

            if ("GET".equals(method) && "/api/drafts".equals(path)) {
                sendJson(exchange, 200, Map.of("drafts", state.listDrafts()));
                return;
            }

            if ("POST".equals(method) && "/api/drafts".equals(path)) {
                Map<String, Object> body = readJsonObject(exchange);
                String postUrl = optionalString(body, "postUrl").orElse("");
                String context = requiredString(body, "context");
                Optional<String> affiliateLabel = optionalString(body, "affiliateLabel");
                sendJson(exchange, 201, state.createDraft(postUrl, context, affiliateLabel));
                return;
            }

            if ("POST".equals(method) && "/api/export".equals(path)) {
                Map<String, Object> body = readJsonObject(exchange);
                String status = optionalString(body, "status").orElse("APPROVED");
                sendJson(exchange, 200, state.exportDrafts(status));
                return;
            }

            if ("POST".equals(method) && "/api/threads/comment".equals(path)) {
                throw new ApiException(
                        403,
                        "AUTOMATED_POSTING_DISABLED",
                        "This backend creates reviewable draft comments only. Automated unsolicited posting to Threads is not supported."
                );
            }

            if (path.startsWith("/api/drafts/")) {
                handleDraftById(exchange, method, path);
                return;
            }

            throw new ApiException(404, "NOT_FOUND", "Endpoint not found");
        }

        private void handleDraftById(HttpExchange exchange, String method, String path) throws IOException {
            String[] parts = path.split("/");
            if (parts.length < 4) {
                throw new ApiException(404, "NOT_FOUND", "Draft endpoint not found");
            }

            long draftId = parseId(parts[3]);
            if (parts.length == 4 && "GET".equals(method)) {
                sendJson(exchange, 200, state.getDraft(draftId));
                return;
            }

            if (parts.length == 5 && "POST".equals(method) && "approve".equals(parts[4])) {
                sendJson(exchange, 200, state.setDraftStatus(draftId, DraftStatus.APPROVED));
                return;
            }

            if (parts.length == 5 && "POST".equals(method) && "reject".equals(parts[4])) {
                sendJson(exchange, 200, state.setDraftStatus(draftId, DraftStatus.REJECTED));
                return;
            }

            throw new ApiException(404, "NOT_FOUND", "Draft endpoint not found");
        }

        private static long parseId(String rawId) {
            try {
                return Long.parseLong(rawId);
            } catch (NumberFormatException exception) {
                throw new ApiException(400, "INVALID_ID", "Draft id must be a number");
            }
        }

        private static String normalizePath(URI uri) {
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return "/";
            }
            return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        }
    }

    static final class ApplicationState {
        private static final String DEFAULT_TEMPLATE = "[context] [link_affiliate]";

        private final AtomicLong affiliateSequence = new AtomicLong(1);
        private final AtomicLong draftSequence = new AtomicLong(1);
        private final Map<String, AffiliateLink> affiliateLinksByLabel = new LinkedHashMap<>();
        private final Map<Long, DraftComment> draftsById = new LinkedHashMap<>();
        private String template = DEFAULT_TEMPLATE;

        synchronized Map<String, Object> snapshotConfig() {
            return Map.of(
                    "template", template,
                    "affiliateLinks", listAffiliateLinks(),
                    "postingMode", "manual_review_only"
            );
        }

        synchronized Map<String, Object> updateTemplate(String newTemplate) {
            validateTemplate(newTemplate);
            template = newTemplate.trim();
            return Map.of("template", template);
        }

        synchronized Map<String, Object> upsertAffiliateLink(String label, String url, boolean active) {
            String normalizedLabel = normalizeLabel(label);
            String normalizedUrl = validateUrl(url);
            AffiliateLink existing = affiliateLinksByLabel.get(normalizedLabel);
            long id = existing == null ? affiliateSequence.getAndIncrement() : existing.id();
            AffiliateLink affiliateLink = new AffiliateLink(id, normalizedLabel, normalizedUrl, active, Instant.now());
            affiliateLinksByLabel.put(normalizedLabel, affiliateLink);
            return affiliateLink.toMap();
        }

        synchronized List<Map<String, Object>> listAffiliateLinks() {
            return affiliateLinksByLabel.values().stream()
                    .map(AffiliateLink::toMap)
                    .toList();
        }

        synchronized Map<String, Object> createDraft(String postUrl, String context, Optional<String> affiliateLabel) {
            String normalizedContext = requireNotBlank(context, "context");
            AffiliateLink affiliateLink = resolveAffiliateLink(affiliateLabel);
            String commentText = renderTemplate(template, normalizedContext, affiliateLink.url());
            DraftComment draft = new DraftComment(
                    draftSequence.getAndIncrement(),
                    postUrl == null ? "" : postUrl.trim(),
                    normalizedContext,
                    affiliateLink.label(),
                    commentText,
                    DraftStatus.PENDING_REVIEW,
                    Instant.now()
            );
            draftsById.put(draft.id(), draft);
            return draft.toMap();
        }

        synchronized List<Map<String, Object>> listDrafts() {
            return draftsById.values().stream()
                    .map(DraftComment::toMap)
                    .toList();
        }

        synchronized Map<String, Object> getDraft(long draftId) {
            return findDraft(draftId).toMap();
        }

        synchronized Map<String, Object> setDraftStatus(long draftId, DraftStatus status) {
            DraftComment draft = findDraft(draftId);
            DraftComment updated = draft.withStatus(status);
            draftsById.put(draftId, updated);
            return updated.toMap();
        }

        synchronized Map<String, Object> exportDrafts(String rawStatus) {
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
            return Map.of(
                    "status", status.name(),
                    "count", matchingDrafts.size(),
                    "content", content,
                    "drafts", matchingDrafts
            );
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
                String label = normalizeLabel(affiliateLabel.get());
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

        private static void validateTemplate(String template) {
            String value = requireNotBlank(template, "template");
            if (!value.contains("[context]") || !value.contains("[link_affiliate]")) {
                throw new ApiException(400, "INVALID_TEMPLATE", "Template must contain [context] and [link_affiliate]");
            }
        }

        private static String renderTemplate(String template, String context, String affiliateUrl) {
            return template
                    .replace("[context]", context)
                    .replace("[link_affiliate]", affiliateUrl)
                    .trim();
        }

        private static String normalizeLabel(String label) {
            return requireNotBlank(label, "label")
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9._-]+", "-")
                    .replaceAll("(^-+|-+$)", "");
        }

        private static String validateUrl(String url) {
            String value = requireNotBlank(url, "url");
            if (!value.startsWith("https://") && !value.startsWith("http://")) {
                throw new ApiException(400, "INVALID_URL", "Affiliate URL must start with http:// or https://");
            }
            return value;
        }

        private static String requireNotBlank(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new ApiException(400, "MISSING_FIELD", fieldName + " is required");
            }
            return value.trim();
        }
    }

    enum DraftStatus {
        PENDING_REVIEW,
        APPROVED,
        REJECTED;

        static DraftStatus from(String value) {
            try {
                return DraftStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new ApiException(400, "INVALID_STATUS", "Status must be PENDING_REVIEW, APPROVED, or REJECTED");
            }
        }
    }

    record AffiliateLink(long id, String label, String url, boolean active, Instant updatedAt) {
        Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "label", label,
                    "url", url,
                    "active", active,
                    "updatedAt", updatedAt.toString()
            );
        }
    }

    record DraftComment(
            long id,
            String postUrl,
            String context,
            String affiliateLabel,
            String commentText,
            DraftStatus status,
            Instant createdAt
    ) {
        DraftComment withStatus(DraftStatus newStatus) {
            return new DraftComment(id, postUrl, context, affiliateLabel, commentText, newStatus, createdAt);
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "postUrl", postUrl,
                    "context", context,
                    "affiliateLabel", affiliateLabel,
                    "commentText", commentText,
                    "status", status.name(),
                    "createdAt", createdAt.toString()
            );
        }
    }

    static final class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final int statusCode;
        private final String code;

        ApiException(int statusCode, String code, String message) {
            super(message);
            this.statusCode = statusCode;
            this.code = code;
        }
    }

    private static Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return Map.of();
        }
        return Json.parseObject(body);
    }

    private static String requiredString(Map<String, Object> body, String fieldName) {
        return optionalString(body, fieldName)
                .orElseThrow(() -> new ApiException(400, "MISSING_FIELD", fieldName + " is required"));
    }

    private static Optional<String> optionalString(Map<String, Object> body, String fieldName) {
        Object value = body.get(fieldName);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String stringValue)) {
            throw new ApiException(400, "INVALID_FIELD", fieldName + " must be a string");
        }
        return Optional.of(stringValue);
    }

    private static Optional<Boolean> optionalBoolean(Map<String, Object> body, String fieldName) {
        Object value = body.get(fieldName);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Boolean booleanValue)) {
            throw new ApiException(400, "INVALID_FIELD", fieldName + " must be a boolean");
        }
        return Optional.of(booleanValue);
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] bytes = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    static final class Json {
        private Json() {
        }

        static Map<String, Object> parseObject(String json) {
            Object value = new Parser(json).parse();
            if (!(value instanceof Map<?, ?> rawMap)) {
                throw new ApiException(400, "INVALID_JSON", "Request body must be a JSON object");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }

        static String stringify(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof String stringValue) {
                return quote(stringValue);
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            if (value instanceof Map<?, ?> map) {
                List<String> entries = new ArrayList<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    entries.add(quote(String.valueOf(entry.getKey())) + ":" + stringify(entry.getValue()));
                }
                return "{" + String.join(",", entries) + "}";
            }
            if (value instanceof Iterable<?> iterable) {
                List<String> entries = new ArrayList<>();
                for (Object item : iterable) {
                    entries.add(stringify(item));
                }
                return "[" + String.join(",", entries) + "]";
            }
            return quote(String.valueOf(value));
        }

        private static String quote(String value) {
            StringBuilder builder = new StringBuilder(value.length() + 2);
            builder.append('"');
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                switch (character) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            builder.append(String.format("\\u%04x", (int) character));
                        } else {
                            builder.append(character);
                        }
                    }
                }
            }
            builder.append('"');
            return builder.toString();
        }

        private static final class Parser {
            private final String json;
            private int index;

            Parser(String json) {
                this.json = json;
            }

            Object parse() {
                Object value = parseValue();
                skipWhitespace();
                if (index != json.length()) {
                    throw invalidJson("Unexpected trailing content");
                }
                return value;
            }

            private Object parseValue() {
                skipWhitespace();
                if (index >= json.length()) {
                    throw invalidJson("Unexpected end of JSON");
                }
                char character = json.charAt(index);
                return switch (character) {
                    case '{' -> parseObject();
                    case '[' -> parseArray();
                    case '"' -> parseString();
                    case 't' -> parseLiteral("true", Boolean.TRUE);
                    case 'f' -> parseLiteral("false", Boolean.FALSE);
                    case 'n' -> parseLiteral("null", null);
                    default -> {
                        if (character == '-' || Character.isDigit(character)) {
                            yield parseNumber();
                        }
                        throw invalidJson("Unexpected character: " + character);
                    }
                };
            }

            private Map<String, Object> parseObject() {
                expect('{');
                Map<String, Object> map = new LinkedHashMap<>();
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return map;
                }
                while (true) {
                    skipWhitespace();
                    String key = parseString();
                    skipWhitespace();
                    expect(':');
                    Object value = parseValue();
                    map.put(key, value);
                    skipWhitespace();
                    if (peek('}')) {
                        expect('}');
                        return map;
                    }
                    expect(',');
                }
            }

            private List<Object> parseArray() {
                expect('[');
                List<Object> list = new ArrayList<>();
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return list;
                }
                while (true) {
                    list.add(parseValue());
                    skipWhitespace();
                    if (peek(']')) {
                        expect(']');
                        return list;
                    }
                    expect(',');
                }
            }

            private String parseString() {
                expect('"');
                StringBuilder builder = new StringBuilder();
                while (index < json.length()) {
                    char character = json.charAt(index++);
                    if (character == '"') {
                        return builder.toString();
                    }
                    if (character == '\\') {
                        builder.append(parseEscape());
                    } else {
                        builder.append(character);
                    }
                }
                throw invalidJson("Unterminated string");
            }

            private char parseEscape() {
                if (index >= json.length()) {
                    throw invalidJson("Unterminated escape sequence");
                }
                char escaped = json.charAt(index++);
                return switch (escaped) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> parseUnicodeEscape();
                    default -> throw invalidJson("Unsupported escape sequence");
                };
            }

            private char parseUnicodeEscape() {
                if (index + 4 > json.length()) {
                    throw invalidJson("Invalid unicode escape");
                }
                String hex = json.substring(index, index + 4);
                index += 4;
                try {
                    return (char) Integer.parseInt(hex, 16);
                } catch (NumberFormatException exception) {
                    throw invalidJson("Invalid unicode escape");
                }
            }

            private Object parseNumber() {
                int start = index;
                if (peek('-')) {
                    index++;
                }
                while (index < json.length() && Character.isDigit(json.charAt(index))) {
                    index++;
                }
                if (peek('.')) {
                    index++;
                    while (index < json.length() && Character.isDigit(json.charAt(index))) {
                        index++;
                    }
                }
                if (peek('e') || peek('E')) {
                    index++;
                    if (peek('+') || peek('-')) {
                        index++;
                    }
                    while (index < json.length() && Character.isDigit(json.charAt(index))) {
                        index++;
                    }
                }
                String rawNumber = json.substring(start, index);
                try {
                    if (rawNumber.contains(".") || rawNumber.contains("e") || rawNumber.contains("E")) {
                        return Double.parseDouble(rawNumber);
                    }
                    return Long.parseLong(rawNumber);
                } catch (NumberFormatException exception) {
                    throw invalidJson("Invalid number");
                }
            }

            private Object parseLiteral(String literal, Object value) {
                if (!json.startsWith(literal, index)) {
                    throw invalidJson("Invalid literal");
                }
                index += literal.length();
                return value;
            }

            private void skipWhitespace() {
                while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                    index++;
                }
            }

            private boolean peek(char expected) {
                return index < json.length() && json.charAt(index) == expected;
            }

            private void expect(char expected) {
                if (!peek(expected)) {
                    throw invalidJson("Expected '" + expected + "'");
                }
                index++;
            }

            private ApiException invalidJson(String message) {
                return new ApiException(400, "INVALID_JSON", message);
            }
        }
    }
}
