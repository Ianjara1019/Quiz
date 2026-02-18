package data;

import java.util.*;

/**
 * Parseur et sérialiseur JSON minimal — aucune dépendance externe.
 */
public class SimpleJson {

    // ======================== PARSER ========================

    private final String src;
    private int pos;

    private SimpleJson(String src) {
        this.src = src;
        this.pos = 0;
    }

    public static Object parse(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        return new SimpleJson(json).readValue();
    }

    private Object readValue() {
        ws();
        char c = peek();
        if (c == '{') return readObject();
        if (c == '[') return readArray();
        if (c == '"') return readString();
        if (c == 't' || c == 'f') return readBool();
        if (c == 'n') return readNull();
        return readNumber();
    }

    private Map<String, Object> readObject() {
        advance(); // {
        ws();
        Map<String, Object> map = new LinkedHashMap<>();
        if (peek() == '}') { advance(); return map; }
        while (true) {
            ws();
            String key = readString();
            ws();
            expect(':');
            Object val = readValue();
            map.put(key, val);
            ws();
            if (peek() == ',') { advance(); continue; }
            expect('}');
            break;
        }
        return map;
    }

    private List<Object> readArray() {
        advance(); // [
        ws();
        List<Object> list = new ArrayList<>();
        if (peek() == ']') { advance(); return list; }
        while (true) {
            list.add(readValue());
            ws();
            if (peek() == ',') { advance(); continue; }
            expect(']');
            break;
        }
        return list;
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = src.charAt(pos++);
                switch (e) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Number readNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        boolean fp = false;
        if (pos < src.length() && src.charAt(pos) == '.') {
            fp = true;
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            fp = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        String s = src.substring(start, pos);
        if (fp) return Double.parseDouble(s);
        long l = Long.parseLong(s);
        return (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) ? (int) l : l;
    }

    private boolean readBool() {
        if (src.startsWith("true", pos))  { pos += 4; return true; }
        if (src.startsWith("false", pos)) { pos += 5; return false; }
        throw err("boolean");
    }

    private Object readNull() {
        if (src.startsWith("null", pos)) { pos += 4; return null; }
        throw err("null");
    }

    private void ws() { while (pos < src.length() && src.charAt(pos) <= ' ') pos++; }
    private char peek() { return pos < src.length() ? src.charAt(pos) : 0; }
    private void advance() { pos++; }

    private void expect(char c) {
        ws();
        if (pos >= src.length() || src.charAt(pos) != c)
            throw err("'" + c + "'");
        pos++;
    }

    private RuntimeException err(String expected) {
        return new RuntimeException("JSON: attendu " + expected + " à pos " + pos);
    }

    // ======================== WRITER ========================

    @SuppressWarnings("unchecked")
    public static String stringify(Object obj) {
        return write(obj, 0);
    }

    @SuppressWarnings("unchecked")
    private static String write(Object o, int d) {
        if (o == null)            return "null";
        if (o instanceof Boolean) return o.toString();
        if (o instanceof Number)  return numStr((Number) o);
        if (o instanceof String)  return esc((String) o);
        if (o instanceof Map)     return writeMap((Map<String, Object>) o, d);
        if (o instanceof List)    return writeList((List<Object>) o, d);
        return esc(o.toString());
    }

    private static String numStr(Number n) {
        if (n instanceof Double || n instanceof Float) {
            double v = n.doubleValue();
            if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
            return n.toString();
        }
        return n.toString();
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String pad(int d) { return "  ".repeat(d); }

    private static String writeMap(Map<String, Object> m, int d) {
        if (m.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        Iterator<Map.Entry<String, Object>> it = m.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> e = it.next();
            sb.append(pad(d + 1)).append(esc(e.getKey())).append(": ")
              .append(write(e.getValue(), d + 1));
            if (it.hasNext()) sb.append(',');
            sb.append('\n');
        }
        return sb.append(pad(d)).append('}').toString();
    }

    private static String writeList(List<Object> l, int d) {
        if (l.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < l.size(); i++) {
            sb.append(pad(d + 1)).append(write(l.get(i), d + 1));
            if (i < l.size() - 1) sb.append(',');
            sb.append('\n');
        }
        return sb.append(pad(d)).append(']').toString();
    }

    // ======================== HELPERS ========================

    public static int toInt(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception e) { /* ignore */ }
        }
        return def;
    }

    public static long toLong(Object v, long def) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try { return Long.parseLong((String) v); } catch (Exception e) { /* ignore */ }
        }
        return def;
    }

    public static String toStr(Object v, String def) {
        return v instanceof String ? (String) v : def;
    }

    public static boolean toBool(Object v, boolean def) {
        return v instanceof Boolean ? (Boolean) v : def;
    }
}
