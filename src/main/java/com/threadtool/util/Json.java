package com.threadtool.util;

import com.threadtool.error.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static Map<String, Object> parseObject(String json) {
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

    public static String stringify(Object value) {
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
