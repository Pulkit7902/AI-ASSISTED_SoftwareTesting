package pipeline;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

/** Pure, testable helpers: code extraction, diagnostics, quarantine, and report parsers.
 *  No network, Docker or Maven calls live here. */
final class ExpCore {
    private ExpCore() {}

    static final int DIAG_MAX_LINES = 20; // the "first N lines" of paper Sec. VI-B

    /** Exception types that mean the TEST CODE or infrastructure is broken, not that an assertion failed. */
    static final Set<String> TEST_CODE_ERROR_TYPES = Set.of(
            "org.junit.jupiter.api.extension.ParameterResolutionException",
            "org.junit.platform.commons.PreconditionViolationException",
            "org.junit.platform.commons.JUnitException",
            "org.junit.jupiter.engine.descriptor.TestInstantiationException",
            "java.lang.NoSuchMethodError", "java.lang.NoClassDefFoundError",
            "java.lang.ExceptionInInitializerError", "java.lang.IllegalAccessError",
            "java.lang.InstantiationException", "java.lang.NoSuchFieldError");

    static String placeholder(String cls) {
        return "package exp;\nimport org.junit.jupiter.api.Test;\n"
                + "class " + cls + "Test { @Test void placeholder() {} }\n";
    }

    // ------------------------------------------------------------------ result types
    static final class Case {
        String cls = "", name = "", kind = "pass", type = "", message = "", trace = "";
    }

    record Classification(String status, String assertion, List<String> failing, String diag) {}

    record Quarantined(String code, int disabled, boolean ok) {}

    record Coverage(double stmtCov, double branchCov, boolean haveReport) {}

    record Mutant(String key, String status) {}

    static final class Pit {
        int killed, eligible, survived, timeout, excluded;
        List<Mutant> mutants = new ArrayList<>();
        int invalidOracleTests;

        static Pit empty() { return new Pit(); }
    }

    // ------------------------------------------------------------------ text helpers
    private static final Pattern CODE_BLOCK = Pattern.compile("<code>(.*?)</code>", Pattern.DOTALL);

    /** Java source inside &lt;code&gt;..&lt;/code&gt; (markdown fences stripped) or null. */
    static String extractCode(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = CODE_BLOCK.matcher(text);
        if (!m.find()) return null;
        String code = m.group(1).strip();
        code = code.replaceFirst("^```(?:java)?\\s*\\n", "");
        code = code.replaceFirst("\\n```\\s*$", "");
        code = code.strip();
        return code.isEmpty() ? null : code;
    }

    /** Strip absolute paths / environment details from diagnostics. */
    static String cleanPath(String line) {
        line = line.replaceAll("(/[\\w.\\-]+)+/src/(test|main)/java/", "");
        line = line.replaceAll("/work/\\S*", "<path>");
        line = line.replaceAll("/home/\\w+/\\S*", "<path>");
        return line.replaceAll("\\s+$", "");
    }

    private static final Pattern JAVA_POS = Pattern.compile("\\.java:\\[\\d+,\\d+]");
    private static final Pattern DIAG_HEAD = Pattern.compile("^(symbol|location|required|found|reason):");

    /** First N compiler-error lines (file, line, message, symbol) from Maven/javac output. */
    static String compileDiagnostics(String output) { return compileDiagnostics(output, DIAG_MAX_LINES); }

    static String compileDiagnostics(String output, int limit) {
        List<String> keep = new ArrayList<>();
        for (String raw : (Iterable<String>) output.lines()::iterator) {
            if (!raw.startsWith("[ERROR]")) continue;
            String body = raw.substring("[ERROR]".length()).strip();
            if (JAVA_POS.matcher(raw).find() || DIAG_HEAD.matcher(body).find())
                keep.add(cleanPath("[ERROR] " + body));
            if (keep.size() >= limit) break;
        }
        return keep.isEmpty() ? "compilation failed (no diagnostic lines captured)" : String.join("\n", keep);
    }

    // ------------------------------------------------------------------ XML
    static Document parseXml(Path p) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // JaCoCo reports carry a DOCTYPE pointing at report.dtd: never try to load it (offline sandbox, XXE).
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setEntityResolver((pub, sys) -> new InputSource(new StringReader("")));
        return b.parse(p.toFile());
    }

    private static List<Element> childElements(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e && e.getTagName().equals(name)) out.add(e);
        return out;
    }

    private static Element firstChild(Element parent, String name) {
        List<Element> l = childElements(parent, name);
        return l.isEmpty() ? null : l.get(0);
    }

    private static String childText(Element parent, String name) {
        Element e = firstChild(parent, name);
        return e == null ? "" : e.getTextContent();
    }

    private static List<Element> descendants(Document d, String name) {
        NodeList nl = d.getElementsByTagName(name);
        List<Element> out = new ArrayList<>();
        for (int i = 0; i < nl.getLength(); i++) out.add((Element) nl.item(i));
        return out;
    }

    // ------------------------------------------------------------------ Surefire
    /** Cases from TEST-*.xml: kind in {pass, failure, error, skipped}. */
    static List<Case> parseSurefire(Path reportDir) throws Exception {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(reportDir)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("TEST-") && n.endsWith(".xml");
            }).sorted().forEach(files::add);
        }
        List<Case> cases = new ArrayList<>();
        for (Path f : files) {
            for (Element tc : descendants(parseXml(f), "testcase")) {
                Case c = new Case();
                c.cls = tc.getAttribute("classname");
                c.name = tc.getAttribute("name");
                for (String kind : new String[]{"failure", "error", "skipped"}) {
                    Element el = firstChild(tc, kind);
                    if (el != null) {
                        c.kind = kind;
                        c.type = el.getAttribute("type");
                        c.message = el.getAttribute("message");
                        c.trace = el.getTextContent();
                        break;
                    }
                }
                cases.add(c);
            }
        }
        return cases;
    }

    /** -> (execution_status, assertion_outcome, failing_names, diagnostics_or_null). */
    static Classification classifyExecution(List<Case> cases) {
        if (cases.isEmpty())
            return new Classification("TestCodeError", "NotApplicable", List.of(),
                    "no tests were found or run in the test class");
        List<Case> codeErrs = new ArrayList<>();
        for (Case c : cases)
            if ((c.kind.equals("failure") || c.kind.equals("error"))
                    && (TEST_CODE_ERROR_TYPES.contains(c.type) || c.name.equals("initializationError")))
                codeErrs.add(c);
        if (!codeErrs.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (Case c : codeErrs.subList(0, Math.min(5, codeErrs.size()))) {
                StringBuilder head = new StringBuilder();
                c.trace.lines().limit(6).forEach(l -> {
                    if (head.length() > 0) head.append('\n');
                    head.append(cleanPath(l));
                });
                String shortCls = c.cls.substring(c.cls.lastIndexOf('.') + 1);
                lines.add(shortCls + "." + c.name + ": " + c.type + ": " + c.message + "\n" + head);
            }
            return new Classification("TestCodeError", "NotApplicable", List.of(), String.join("\n", lines));
        }
        List<String> failing = new ArrayList<>();
        for (Case c : cases) if (c.kind.equals("failure") || c.kind.equals("error")) failing.add(c.name);
        return new Classification("ExecutionCompleted",
                failing.isEmpty() ? "AllAssertionsPass" : "AssertionFailure", failing, null);
    }

    /** 'testFoo(int)[2]' -> 'testFoo'; 'testFoo()' -> 'testFoo'. */
    static String baseTestName(String name) {
        int cut = name.length();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '(' || c == '[') { cut = i; break; }
        }
        return name.substring(0, cut).strip();
    }

    static Set<String> distinctBaseNames(List<String> names) {
        Set<String> s = new TreeSet<>();
        for (String n : names) s.add(baseTestName(n));
        return s;
    }

    /** Disable individual failing @Test methods. ok=false: a name could not be located, so the caller
     *  should quarantine the whole suite. */
    static Quarantined quarantine(String code, List<String> failingNames) {
        Set<String> names = distinctBaseNames(failingNames);
        for (String n : names) {
            Matcher m = Pattern.compile("\\bvoid\\s+" + Pattern.quote(n) + "\\s*\\(").matcher(code);
            if (!m.find()) return new Quarantined(code, 0, false);
            code = code.substring(0, m.start()) + "@org.junit.jupiter.api.Disabled " + code.substring(m.start());
        }
        return new Quarantined(code, names.size(), true);
    }

    // ------------------------------------------------------------------ JaCoCo / PIT
    /** Percent coverage for class exp/&lt;cls&gt;; zeros if there is no report. */
    static Coverage parseJacoco(Path xml, String cls) throws Exception {
        if (!Files.exists(xml)) return new Coverage(0.0, 0.0, false);
        double stmt = 0.0, branch = 0.0;
        for (Element c : descendants(parseXml(xml), "class")) {
            if (!c.getAttribute("name").equals("exp/" + cls)) continue;
            for (Element ctr : childElements(c, "counter")) {
                long missed = Long.parseLong(ctr.getAttribute("missed"));
                long covered = Long.parseLong(ctr.getAttribute("covered"));
                long tot = missed + covered;
                double pct = tot > 0 ? 100.0 * covered / tot : 0.0;
                if (ctr.getAttribute("type").equals("INSTRUCTION")) stmt = pct;
                else if (ctr.getAttribute("type").equals("BRANCH")) branch = pct;
            }
        }
        return new Coverage(stmt, branch, true);
    }

    /** killed/eligible/... counts and the mutant list, or null if there is no report. */
    static Pit parsePit(Path xml) throws Exception {
        if (!Files.exists(xml)) return null;
        Map<String, Integer> c = new LinkedHashMap<>();
        for (String k : List.of("KILLED", "TIMED_OUT", "MEMORY_ERROR", "SURVIVED", "NO_COVERAGE",
                "NON_VIABLE", "RUN_ERROR")) c.put(k, 0);
        Pit pit = new Pit();
        for (Element m : descendants(parseXml(xml), "mutation")) {
            String st = m.getAttribute("status");
            c.merge(st, 1, Integer::sum);
            String mutator = childText(m, "mutator");
            String key = childText(m, "mutatedMethod") + ":" + childText(m, "lineNumber") + ":"
                    + mutator.substring(mutator.lastIndexOf('.') + 1) + ":" + childText(m, "index");
            pit.mutants.add(new Mutant(key, st));
        }
        int total = c.values().stream().mapToInt(Integer::intValue).sum();
        int excluded = c.get("NON_VIABLE") + c.get("RUN_ERROR");
        pit.killed = c.get("KILLED") + c.get("TIMED_OUT") + c.get("MEMORY_ERROR");
        pit.eligible = total - excluded;
        pit.survived = c.get("SURVIVED") + c.get("NO_COVERAGE");
        pit.timeout = c.get("TIMED_OUT");
        pit.excluded = excluded;
        return pit;
    }

    static String mutantsJson(List<Mutant> mutants) {
        List<Object> l = new ArrayList<>();
        for (Mutant m : mutants) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("key", m.key());
            o.put("status", m.status());
            l.add(o);
        }
        return Json.write(l);
    }

    static void ignore(IOException e) {}
}
