package com.threadtool.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.threadtool.domain.DraftStatus;
import com.threadtool.error.ApiException;
import com.threadtool.service.ApplicationState;
import com.threadtool.service.ThreadsAutomationGuard;
import com.threadtool.util.Json;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ApiHandler implements HttpHandler {
    private final ApplicationState state;

    public ApiHandler(ApplicationState state) {
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
            sendJson(exchange, exception.statusCode(), Map.of(
                    "error", exception.code(),
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
            sendJson(exchange, 200, state.updateTemplate(requiredString(body, "template")));
            return;
        }

        if ("GET".equals(method) && "/api/config/templates".equals(path)) {
            sendJson(exchange, 200, Map.of("templates", state.listCommentTemplates()));
            return;
        }

        if ("POST".equals(method) && "/api/config/templates".equals(path)) {
            Map<String, Object> body = readJsonObject(exchange);
            sendJson(exchange, 201, state.upsertCommentTemplate(
                    requiredString(body, "label"),
                    optionalString(body, "language").orElse("any"),
                    requiredString(body, "template"),
                    optionalBoolean(body, "active").orElse(true)
            ));
            return;
        }

        if ("GET".equals(method) && "/api/config/affiliate-links".equals(path)) {
            sendJson(exchange, 200, Map.of("affiliateLinks", state.listAffiliateLinks()));
            return;
        }

        if ("POST".equals(method) && "/api/config/affiliate-links".equals(path)) {
            Map<String, Object> body = readJsonObject(exchange);
            sendJson(exchange, 201, state.upsertAffiliateLink(
                    requiredString(body, "label"),
                    requiredString(body, "url"),
                    optionalBoolean(body, "active").orElse(true)
            ));
            return;
        }

        if ("GET".equals(method) && "/api/drafts".equals(path)) {
            sendJson(exchange, 200, Map.of("drafts", state.listDrafts()));
            return;
        }

        if ("POST".equals(method) && "/api/drafts".equals(path)) {
            Map<String, Object> body = readJsonObject(exchange);
            sendJson(exchange, 201, state.createDraft(
                    optionalString(body, "postUrl").orElse(""),
                    requiredString(body, "context"),
                    optionalString(body, "affiliateLabel"),
                    optionalString(body, "templateLabel"),
                    optionalString(body, "language"),
                    0
            ));
            return;
        }

        if ("GET".equals(method) && "/api/candidates".equals(path)) {
            sendJson(exchange, 200, Map.of("candidates", state.listCandidates()));
            return;
        }

        if ("POST".equals(method) && "/api/candidates".equals(path)) {
            Map<String, Object> body = readJsonObject(exchange);
            sendJson(exchange, 201, state.importCandidateAndCreateDraft(
                    requiredString(body, "postUrl"),
                    requiredString(body, "context"),
                    optionalString(body, "language").orElse("any"),
                    optionalLong(body, "engagementScore").orElse(0L),
                    optionalString(body, "affiliateLabel"),
                    optionalString(body, "templateLabel")
            ));
            return;
        }

        if ("POST".equals(method) && "/api/export".equals(path)) {
            Map<String, Object> body = readJsonObject(exchange);
            sendJson(exchange, 200, state.exportDrafts(optionalString(body, "status").orElse("APPROVED")));
            return;
        }

        if ("POST".equals(method) && isBlockedThreadsAutomationPath(path)) {
            throw ThreadsAutomationGuard.blocked("Threads login, viral discovery, and automated commenting");
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

    private static boolean isBlockedThreadsAutomationPath(String path) {
        return "/api/threads/comment".equals(path)
                || "/api/threads/login".equals(path)
                || "/api/threads/search".equals(path)
                || "/api/threads/search-viral".equals(path)
                || "/api/config/threads-credentials".equals(path);
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

    private static Optional<Long> optionalLong(Map<String, Object> body, String fieldName) {
        Object value = body.get(fieldName);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Long longValue) {
            return Optional.of(longValue);
        }
        if (value instanceof Integer integerValue) {
            return Optional.of(integerValue.longValue());
        }
        throw new ApiException(400, "INVALID_FIELD", fieldName + " must be a number");
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (statusCode == 204) {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }

        byte[] bytes = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
