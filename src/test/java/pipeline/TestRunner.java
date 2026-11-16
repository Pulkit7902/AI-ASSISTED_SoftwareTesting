package pipeline;

import java.nio.file.*;
import java.util.*;

/** Dependency-free unit tests (port of tests/test_core.py and tests/test_stats.py).
 *  Run: java -cp out/classes:out/test-classes pipeline.TestRunner   (must be in package pipeline). */
public final class TestRunner {
    private static int passed, failed;

    private static final String JAVAC = """
            [INFO] Compiling 1 source file
            [ERROR] COMPILATION ERROR :
            [ERROR] /work/src/test/java/exp/ClampTest.java:[9,35] cannot find symbol
            [ERROR]   symbol:   method clampInt(int,int,int)
            [ERROR]   location: class exp.Clamp
            [ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:testCompile on project trial: Compilation failure
            """;
    private static final String SUREFIRE_OK = """
            <testsuite name="exp.ClampTest" tests="3"><testcase classname="exp.ClampTest" name="a"/>
            <testcase classname="exp.ClampTest" name="b"><failure type="org.opentest4j.AssertionFailedError" message="expected: 1 but was: 2">trace</failure></testcase>
            <testcase classname="exp.ClampTest" name="c(int)[1]"><error type="java.lang.IllegalStateException" message="boom">trace</error></testcase></testsuite>""";
    private static final String SUREFIRE_BAD = """
            <testsuite name="exp.XTest" tests="1"><testcase classname="exp.XTest" name="initializationError"><error type="org.junit.platform.commons.JUnitException" message="no ctor">at /work/src/test/java/exp/XTest.java:3</error></testcase></testsuite>""";
    private static final String JACOCO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
            <report name="t"><package name="exp"><class name="exp/Clamp"><counter type="INSTRUCTION" missed="10" covered="30"/>
            <counter type="BRANCH" missed="2" covered="2"/></class></package></report>""";
    private static final String PIT = """
            <mutations>
            <mutation detected="true" status="KILLED"><mutatedMethod>clamp</mutatedMethod><lineNumber>10</lineNumber><mutator>org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator</mutator><index>3</index></mutation>
            <mutation detected="false" status="SURVIVED"><mutatedMethod>clamp</mutatedMethod><lineNumber>11</lineNumber><mutator>x.NegateConditionalsMutator</mutator><index>4</index></mutation>
            <mutation detected="false" status="NO_COVERAGE"><mutatedMethod>clamp</mutatedMethod><lineNumber>12</lineNumber><mutator>x.MathMutator</mutator><index>5</index></mutation>
            <mutation detected="true" status="TIMED_OUT"><mutatedMethod>clamp</mutatedMethod><lineNumber>13</lineNumber><mutator>x.M</mutator><index>6</index></mutation>
            <mutation detected="false" status="NON_VIABLE"><mutatedMethod>clamp</mutatedMethod><lineNumber>14</lineNumber><mutator>x.M</mutator><index>7</index></mutation>
            </mutations>""";

    interface Body { void run() throws Exception; }

    static void test(String name, Body b) {
        try { b.run(); passed++; System.out.println("ok    " + name); }
        catch (Throwable t) { failed++; System.out.println("FAIL  " + name + ": " + t); t.printStackTrace(System.out); }
    }

    static void eq(Object exp, Object act) { if (!Objects.equals(exp, act)) throw new AssertionError("expected <" + exp + "> but was <" + act + ">"); }
    static void near(double exp, double act, double tol) { if (Math.abs(exp - act) > tol) throw new AssertionError("expected " + exp + " but was " + act); }
    static void yes(boolean c) { if (!c) throw new AssertionError("expected true"); }

    static double[] ones(int n) { double[] d = new double[n]; Arrays.fill(d, 1); return d; }

    static double[] concat(double[] a, double[] b) {
        double[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    static double[] rep(double v, int n) { double[] d = new double[n]; Arrays.fill(d, v); return d; }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("tests_");

        test("extract_code", () -> {
            eq("class A {}", ExpCore.extractCode("hi <code>\n```java\nclass A {}\n```\n</code> bye"));
            eq(null, ExpCore.extractCode("no tags here"));
            eq(null, ExpCore.extractCode("<code>  </code>"));
        });
        test("compile_diagnostics_strip_paths_and_noise", () -> {
            String d = ExpCore.compileDiagnostics(JAVAC);
            yes(d.contains("ClampTest.java:[9,35] cannot find symbol"));
            yes(d.contains("symbol:   method clampInt"));
            yes(!d.contains("/work")); yes(!d.contains("Failed to execute goal"));
        });
        test("classify_and_quarantine", () -> {
            Path d = Files.createTempDirectory(tmp, "sf");
            Files.writeString(d.resolve("TEST-exp.ClampTest.xml"), SUREFIRE_OK);
            ExpCore.Classification c = ExpCore.classifyExecution(ExpCore.parseSurefire(d));
            eq("ExecutionCompleted", c.status()); eq("AssertionFailure", c.assertion());
            eq(List.of("b", "c(int)[1]"), c.failing());
            String code = "class T {\n @Test void a(){}\n @Test public void b(){}\n @ParameterizedTest void c(int x){}\n}";
            ExpCore.Quarantined q = ExpCore.quarantine(code, c.failing());
            yes(q.ok()); eq(2, q.disabled());
            yes(q.code().contains("public @org.junit.jupiter.api.Disabled void b()"));
            eq(2, q.code().split("Disabled", -1).length - 1);
            yes(!q.code().contains("Disabled void a"));
            yes(!ExpCore.quarantine(code, List.of("ghost")).ok());
        });
        test("test_code_error_is_not_an_assertion_failure", () -> {
            Path d = Files.createTempDirectory(tmp, "sf");
            Files.writeString(d.resolve("TEST-exp.XTest.xml"), SUREFIRE_BAD);
            ExpCore.Classification c = ExpCore.classifyExecution(ExpCore.parseSurefire(d));
            eq("TestCodeError", c.status()); yes(!c.diag().contains("/work"));
            eq("TestCodeError", ExpCore.classifyExecution(List.of()).status());
        });
        test("jacoco_and_pit", () -> {
            Path d = Files.createTempDirectory(tmp, "rep");
            Files.writeString(d.resolve("j.xml"), JACOCO); Files.writeString(d.resolve("m.xml"), PIT);
            ExpCore.Coverage c = ExpCore.parseJacoco(d.resolve("j.xml"), "Clamp");
            near(75.0, c.stmtCov(), 1e-9); near(50.0, c.branchCov(), 1e-9);
            ExpCore.Pit p = ExpCore.parsePit(d.resolve("m.xml"));
            eq(List.of(2, 4, 2, 1, 1), List.of(p.killed, p.eligible, p.survived, p.timeout, p.excluded));
            eq("clamp:11:NegateConditionalsMutator:4", p.mutants.get(1).key());
            near(0.0, ExpCore.parseJacoco(Path.of("/nonexistent.xml"), "Clamp").stmtCov(), 0);
            eq(null, ExpCore.parsePit(Path.of("/nonexistent.xml")));
        });

        test("min_p_table_iv", () -> {
            int[] ns = {5, 6, 8, 10, 12};
            double[] ps = {0.0625, 0.03125, 0.0078125, 0.001953125, 0.00048828125};
            for (int i = 0; i < ns.length; i++) near(ps[i], Stats.exactWilcoxon(ones(ns[i])).p(), 1e-12);
            near(2.0 / Math.pow(2, 18), Stats.exactWilcoxon(ones(18)).p(), 1e-15);
        });
        test("matches_brute_force_without_ties", () -> {
            Random rng = new Random(3);
            for (int t = 0; t < 25; t++) {
                double[] d = new double[12];
                for (int i = 0; i < d.length; i++) d[i] = 0.4 + rng.nextGaussian();
                near(bruteForce(d), Stats.exactWilcoxon(d).p(), 1e-10);
            }
        });
        test("binary_net_plus4_cannot_reach_0_016", () -> {
            Stats.Wilcoxon w = Stats.exactWilcoxon(concat(ones(4), rep(0, 14)));
            near(0.125, w.p(), 1e-12); eq(4, w.nNonzero());
            int[][] cases = {{5, 1}, {6, 2}, {7, 3}};
            double[] expect = {0.21875, 0.2890625, 0.34375}; // sign test, since all |d| are tied
            for (int i = 0; i < cases.length; i++) {
                double[] d = concat(concat(ones(cases[i][0]), rep(-1, cases[i][1])), rep(0, 18 - cases[i][0] - cases[i][1]));
                near(expect[i], Stats.exactWilcoxon(d).p(), 1e-9);
            }
            near(2.0 / 128, Stats.exactWilcoxon(ones(7)).p(), 1e-12);
        });
        test("a12_binary_closed_form", () -> {
            double[] a = concat(ones(11), rep(0, 7)), b = concat(ones(15), rep(0, 3));
            near(0.5 + (15 / 18.0 - 11 / 18.0) / 2, Stats.a12(a, b), 1e-12);
            near(0.6111111, Stats.a12(a, b), 1e-6);
        });
        test("bootstrap_binary_values_are_multiples_of_5_56", () -> {
            double[] d = concat(rep(100, 4), rep(0, 14));
            double[] ci = Stats.bootCi(d, new Random(1), 10_000);
            for (double v : ci) { double q = v / (100.0 / 18); near(0, q - Math.rint(q), 1e-6); }
            near(100.0 / 18, ci[0], 1e-6);
        });
        test("holm", () -> {
            double[] a = Stats.holm(new double[]{0.003, 0.021, 0.125}), e = {0.009, 0.042, 0.125};
            for (int i = 0; i < 3; i++) near(e[i], a[i], 1e-12);
            eq(List.of(1.0, 1.0, 1.0), Arrays.stream(Stats.holm(new double[]{0.5, 0.5, 0.5})).boxed().toList());
            double[] h = Stats.holm(new double[]{0.04, 0.01, 0.03});
            near(0.03, h[1], 1e-12); near(0.06, h[2], 1e-12); near(0.06, h[0], 1e-12);
        });
        test("ties_in_magnitude_match_brute_force", () -> {
            double[] d = {1, 1, 2, -1, 2, -2, 3, 0.5};
            near(bruteForce(d), Stats.exactWilcoxon(d).p(), 1e-12);
        });
        test("kappa", () -> {
            near(1.0, Stats.cohenKappa(List.of("y", "y", "n", "n"), List.of("y", "y", "n", "n")), 1e-12);
            near(0.0, Stats.cohenKappa(List.of("y", "y", "n", "n"), List.of("y", "n", "y", "n")), 1e-12);
        });
        test("csv_roundtrip", () -> {
            Path f = tmp.resolve("t.csv");
            Csv.write(f, List.of("a", "b"), List.of(List.of("x,y", "q\"z"), List.of("", "line\nbreak")));
            List<Map<String, String>> r = Csv.read(f);
            eq("x,y", r.get(0).get("a")); eq("q\"z", r.get(0).get("b")); eq("line\nbreak", r.get(1).get("b"));
        });
        test("json_roundtrip", () -> {
            Object o = Json.parse("{\"a\": [1, 2.5, \"x\\n\\u0041\"], \"b\": null, \"c\": true}");
            eq("{\"a\": [1, 2.5, \"x\\nA\"], \"b\": null, \"c\": true}", Json.write(o));
        });

        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Brute-force exact two-sided p over all 2^n sign assignments (average ranks of |d|, zeros dropped). */
    static double bruteForce(double[] dIn) {
        double[] d = Arrays.stream(dIn).filter(x -> Stats.round9(x) != 0).toArray();
        int n = d.length;
        double[] abs = Arrays.stream(d).map(Math::abs).toArray();
        double[] r = Stats.rankAverage(abs);
        double tot = Arrays.stream(r).sum(), obs = 0;
        for (int i = 0; i < n; i++) if (d[i] > 0) obs += r[i];
        double dev = Math.abs(obs - tot / 2);
        long cnt = 0;
        for (int mask = 0; mask < (1 << n); mask++) {
            double w = 0;
            for (int i = 0; i < n; i++) if ((mask >> i & 1) != 0) w += r[i];
            if (Math.abs(w - tot / 2) >= dev - 1e-12) cnt++;
        }
        return cnt / Math.pow(2, n);
    }
}
