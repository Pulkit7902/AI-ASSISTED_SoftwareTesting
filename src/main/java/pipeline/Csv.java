package pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** RFC-4180-style CSV (quotes, embedded commas/newlines), CRLF on output like Python's csv module. */
final class Csv {
    private Csv() {}

    static List<Map<String, String>> read(Path p) throws IOException {
        List<List<String>> raw = parse(Files.readString(p, StandardCharsets.UTF_8));
        List<Map<String, String>> out = new ArrayList<>();
        if (raw.isEmpty()) return out;
        List<String> header = raw.get(0);
        for (int r = 1; r < raw.size(); r++) {
            List<String> row = raw.get(r);
            Map<String, String> m = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) m.put(header.get(c), c < row.size() ? row.get(c) : "");
            out.add(m);
        }
        return out;
    }

    static List<List<String>> parse(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder f = new StringBuilder();
        boolean q = false, any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { f.append('"'); i++; } else q = false;
                } else f.append(c);
            } else if (c == '"') { q = true; any = true; }
            else if (c == ',') { row.add(f.toString()); f.setLength(0); any = true; }
            else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                if (any || f.length() > 0) { row.add(f.toString()); rows.add(row); }
                row = new ArrayList<>(); f.setLength(0); any = false;
            } else { f.append(c); any = true; }
        }
        if (any || f.length() > 0) { row.add(f.toString()); rows.add(row); }
        return rows;
    }

    static String line(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            String s = fields.get(i) == null ? "" : fields.get(i);
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r"))
                sb.append('"').append(s.replace("\"", "\"\"")).append('"');
            else sb.append(s);
        }
        return sb.append("\r\n").toString();
    }

    static void write(Path p, List<String> header, List<List<String>> rows) throws IOException {
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        StringBuilder sb = new StringBuilder(line(header));
        for (List<String> r : rows) sb.append(line(r));
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
    }

    static void append(Path p, List<String> header, List<List<String>> rows, boolean writeHeader) throws IOException {
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        StringBuilder sb = new StringBuilder();
        if (writeHeader) sb.append(line(header));
        for (List<String> r : rows) sb.append(line(r));
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
