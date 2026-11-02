package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный JSON-парсер и сериализатор без внешних зависимостей.
 * Объект - {@code LinkedHashMap<String,Object>}, массив - {@code ArrayList<Object>},
 * числа - {@code Long} или {@code Double}.
 */
public final class Json {

    private Json() {
    }

    public static class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ parse

    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("пустой JSON");
        }
        Parser p = new Parser(text);
        p.skipWs();
        Object value = p.value();
        p.skipWs();
        if (!p.eof()) {
            throw new JsonException("лишние данные в позиции " + p.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        if (!(o instanceof Map)) {
            throw new JsonException("ожидался объект JSON");
        }
        return (Map<String, Object>) o;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean eof() {
            return pos >= s.length();
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\uFEFF') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object value() {
            if (eof()) {
                throw new JsonException("неожиданный конец JSON");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return number();
            }
        }

        private void expect(String word) {
            if (!s.startsWith(word, pos)) {
                throw new JsonException("ожидалось '" + word + "' в позиции " + pos);
            }
            pos += word.length();
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (!eof() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (eof() || s.charAt(pos) != '"') {
                    throw new JsonException("ожидался ключ объекта в позиции " + pos);
                }
                String key = string();
                skipWs();
                if (eof() || s.charAt(pos) != ':') {
                    throw new JsonException("ожидалось ':' в позиции " + pos);
                }
                pos++;
                skipWs();
                map.put(key, value());
                skipWs();
                if (eof()) {
                    throw new JsonException("незакрытый объект");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return map;
                }
                throw new JsonException("ожидалось ',' или '}' в позиции " + pos);
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (!eof() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(value());
                skipWs();
                if (eof()) {
                    throw new JsonException("незакрытый массив");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return list;
                }
                throw new JsonException("ожидалось ',' или ']' в позиции " + pos);
            }
        }

        private String string() {
            pos++; // "
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) {
                    throw new JsonException("незакрытая строка");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (eof()) {
                    throw new JsonException("незакрытая escape-последовательность");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > s.length()) {
                            throw new JsonException("обрезанный \\u escape");
                        }
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw new JsonException("неверный escape: \\" + e);
                }
            }
        }

        private Object number() {
            int start = pos;
            if (!eof() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) {
                pos++;
            }
            boolean dot = false;
            boolean exp = false;
            while (!eof()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' && !dot && !exp) {
                    dot = true;
                    pos++;
                } else if ((c == 'e' || c == 'E') && !exp) {
                    exp = true;
                    pos++;
                    if (!eof() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) {
                        pos++;
                    }
                } else {
                    break;
                }
            }
            String raw = s.substring(start, pos);
            if (raw.isEmpty() || raw.equals("-")) {
                throw new JsonException("неверное число в позиции " + start);
            }
            if (!dot && !exp) {
                try {
                    return Long.parseLong(raw);
                } catch (NumberFormatException ignored) {
                    // слишком длинное - падаем в double
                }
            }
            return Double.parseDouble(raw);
        }
    }

    // ------------------------------------------------------------------ write

    public static String write(Object value) {
        return write(value, true);
    }

    public static String write(Object value, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, pretty, 0);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v, boolean pretty, int depth) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String str) {
            quote(sb, str);
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, pretty, depth + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                writeValue(sb, e.getValue(), pretty, depth + 1);
            }
            newline(sb, pretty, depth);
            sb.append('}');
        } else if (v instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                newline(sb, pretty, depth + 1);
                writeValue(sb, list.get(i), pretty, depth + 1);
            }
            newline(sb, pretty, depth);
            sb.append(']');
        } else if (v instanceof Object[] arr) {
            writeValue(sb, List.of(arr), pretty, depth);
        } else {
            quote(sb, String.valueOf(v));
        }
    }

    private static void newline(StringBuilder sb, boolean pretty, int depth) {
        if (!pretty) {
            return;
        }
        sb.append('\n');
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ files

    public static Map<String, Object> readFile(Path path) throws IOException {
        return parseObject(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static Map<String, Object> readFileQuiet(Path path) {
        try {
            return readFile(path);
        } catch (IOException | JsonException e) {
            return null;
        }
    }

    public static void writeFile(Path path, Object value) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, write(value), StandardCharsets.UTF_8);
    }

    public static void writeFileQuiet(Path path, Object value) {
        try {
            writeFile(path, value);
        } catch (IOException ignored) {
            // молча
        }
    }

    // ------------------------------------------------------------------ access

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    public static String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return fallback;
    }

    public static long lng(Object value, long fallback) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static int integer(Object value, int fallback) {
        return (int) lng(value, fallback);
    }

    public static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        if (value instanceof String s) {
            return "true".equalsIgnoreCase(s) || "1".equals(s);
        }
        return fallback;
    }

    /** Достать значение по цепочке ключей: {@code path(root, "a", "b")}. */
    public static Object path(Object root, String... keys) {
        Object cur = root;
        for (String key : keys) {
            if (cur instanceof Map<?, ?> map) {
                cur = map.get(key);
            } else if (cur instanceof List<?> list) {
                try {
                    int idx = Integer.parseInt(key);
                    cur = idx >= 0 && idx < list.size() ? list.get(idx) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return cur;
    }
}
