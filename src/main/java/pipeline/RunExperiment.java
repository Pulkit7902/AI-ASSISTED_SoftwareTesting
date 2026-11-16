package pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Run Cond-A (one-shot) and Cond-B (bounded execution feedback) for every method and repeat.
 *
 * Per (method, repeat): ONE first request is made; Cond-A's final suite is that first output;
 * Cond-B starts from the SAME first output and may repair it up to MAX_REPAIRS times (Algorithm 1).
 * Both final suites go through the identical evaluation (JaCoCo + PIT). One row per trial goes to
 * results.csv; every prompt, response, build log and PIT result is archived under artifacts/.
 */
final class RunExperiment {
    private RunExperiment() {}

    static final int MAX_REPAIRS = 2;

    // ------------------------------------------------------------------ sandboxed Maven
    record MvnResult(int rc, String output, double seconds) {}

    static final class Runner {
        private final boolean useDocker;
        private final long timeoutSeconds;

        Runner(boolean useDocker, long timeoutSeconds) { this.useDocker = useDocker; this.timeoutSeconds = timeoutSeconds; }

        /** Run Maven in workdir. */
        MvnResult mvn(Path workdir, List<String> args) throws Exception {
            long t0 = System.nanoTime();
            String name = "exp-" + ProcessHandle.current().pid() + "-" + (System.currentTimeMillis() % 1_000_000_000L);
            List<String> cmd = new ArrayList<>();
            if (useDocker) {
                cmd.addAll(List.of("docker", "run", "--rm", "--name", name, "--network", "none", "--user", "10001:10001",
                        "--tmpfs", "/tmp:rw,size=512m", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
                        "--cpus", "2", "--memory", "2g", "--pids-limit", "512", "-e", "HOME=/home/runner",
                        "-e", "MAVEN_CONFIG=", "-e", "MAVEN_OPTS=-Xmx1g",
                        "-v", workdir.toAbsolutePath().normalize() + ":/work:rw", "-w", "/work",
                        "--entrypoint", "mvn", "exp-sandbox:1", "-B", "-o",
                        "-Dmaven.repo.local=/home/runner/.m2/repository"));
            } else {
                cmd.addAll(List.of("mvn", "-B"));
            }
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(workdir.toFile()).redirectErrorStream(true);
            Process p = pb.start();
            CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> {
                try { return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8); }
                catch (IOException e) { return ""; }
            });
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                if (useDocker) new ProcessBuilder("docker", "kill", name).redirectErrorStream(true).start().waitFor();
                String partial;
                try { partial = out.get(10, TimeUnit.SECONDS); } catch (Exception e) { partial = ""; }
                return new MvnResult(124, partial + "\nTIMEOUT", (System.nanoTime() - t0) / 1e9);
            }
            return new MvnResult(p.exitValue(), out.get(), (System.nanoTime() - t0) / 1e9);
        }
    }

    static void deleteTree(Path p) {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(x -> { try { Files.delete(x); } catch (IOException ignored) { } });
        } catch (IOException ignored) { }
    }

    static void prepare(Path root, Path workdir, String cls, String testCode) throws IOException {
        deleteTree(workdir);
        Files.createDirectories(workdir.resolve("src/main/java/exp"));
        Files.createDirectories(workdir.resolve("src/test/java/exp"));
        Files.copy(root.resolve("template/pom.xml"), workdir.resolve("pom.xml"));
        Files.copy(root.resolve("candidates/exp/" + cls + ".java"), workdir.resolve("src/main/java/exp/" + cls + ".java"));
        if (testCode != null)
            Files.writeString(workdir.resolve("src/test/java/exp/" + cls + "Test.java"), testCode, StandardCharsets.UTF_8);
        // the container user (uid 10001) must be able to write into the mounted directory
        try (Stream<Path> s = Files.walk(workdir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                try {
                    Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(
                            Files.isDirectory(p) ? "rwxrwxrwx" : "rw-rw-rw-"));
                } catch (UnsupportedOperationException | IOException ignored) { }
            }
        }
    }

    // ------------------------------------------------------------------ one Maven test run
    static final class CompileResult {
        boolean compileOk;
        String execStatus = "NotExecuted", assertion = "NotApplicable", diag, log = "";
        List<String> failing = new ArrayList<>();
        int testsTotal;
        double seconds;
        ExpCore.Coverage cov = new ExpCore.Coverage(0.0, 0.0, false);
    }

    /** One Maven `test` run: compile + surefire + JaCoCo. */
    static CompileResult compileAndRun(Path root, Runner runner, Path workdir, String cls, String testCode) throws Exception {
        prepare(root, workdir, cls, testCode);
        CompileResult res = new CompileResult();
        if (testCode == null) {
            res.diag = "the response contained no <code> block with a Java test class";
            res.execStatus = "TestCodeError";
            return res;
        }
        MvnResult m = runner.mvn(workdir, List.of("test", "-Dcls=" + cls));
        res.log = m.output();
        res.seconds = m.seconds();
        Path classFile = workdir.resolve("target/test-classes/exp/" + cls + "Test.class");
        if (m.output().contains("COMPILATION ERROR") || !Files.exists(classFile)) {
            res.execStatus = "NotExecuted";
            res.diag = ExpCore.compileDiagnostics(m.output());
            return res;
        }
        res.compileOk = true;
        Path rep = workdir.resolve("target/surefire-reports");
        if (!Files.exists(rep)) {
            res.execStatus = "InfrastructureError";
            List<String> ls = m.output().lines().toList();
            StringBuilder sb = new StringBuilder("the test runner produced no report:\n");
            List<String> last = ls.subList(Math.max(0, ls.size() - 15), ls.size());
            for (int i = 0; i < last.size(); i++) { if (i > 0) sb.append('\n'); sb.append(ExpCore.cleanPath(last.get(i))); }
            res.diag = sb.toString();
            return res;
        }
        List<ExpCore.Case> cases = ExpCore.parseSurefire(rep);
        ExpCore.Classification c = ExpCore.classifyExecution(cases);
        res.execStatus = c.status(); res.assertion = c.assertion(); res.failing = new ArrayList<>(c.failing());
        res.diag = c.diag();
        res.testsTotal = (int) cases.stream().filter(x -> !x.kind.equals("skipped")).count();
        res.cov = ExpCore.parseJacoco(workdir.resolve("target/site/jacoco/jacoco.xml"), cls);
        return res;
    }

    record PitRun(ExpCore.Pit pit, double seconds) {}

    /** Mutation analysis on the QUARANTINED suite (invalid tests removed). */
    static PitRun runPit(Path root, Runner runner, Path workdir, String cls, String testCode,
                         CompileResult cr, Path artDir) throws Exception {
        String code = testCode;
        int nInvalid = 0;
        boolean usable = cr.compileOk && cr.execStatus.equals("ExecutionCompleted");
        if (usable && !cr.failing.isEmpty()) {
            ExpCore.Quarantined q = ExpCore.quarantine(testCode, cr.failing);
            code = q.code(); nInvalid = q.disabled();
            if (!q.ok() || nInvalid >= cr.testsTotal) {
                code = null;
                nInvalid = ExpCore.distinctBaseNames(cr.failing).size();
            }
        } else if (!usable) {
            code = null;
        }
        if (code == null) code = ExpCore.placeholder(cls); // keeps the mutant universe identical
        prepare(root, workdir, cls, code);
        MvnResult m = runner.mvn(workdir, List.of("test-compile", "org.pitest:pitest-maven:mutationCoverage",
                "-Dcls=" + cls, "-Djacoco.skip=true"));
        ExpCore.Pit pit = ExpCore.parsePit(workdir.resolve("target/pit-reports/mutations.xml"));
        Files.createDirectories(artDir);
        Files.writeString(artDir.resolve("pit.log"), Env.tail(m.output(), 20000));
        if (pit == null) {
            System.err.println("   !! PIT produced no report for " + cls + "; row will carry mut_eligible=0 (rerun it)");
            pit = ExpCore.Pit.empty();
        }
        Files.writeString(artDir.resolve("mutations.json"), ExpCore.mutantsJson(pit.mutants));
        pit.invalidOracleTests = usable ? nInvalid : 0;
        return new PitRun(pit, m.seconds());
    }

    // ------------------------------------------------------------------ helpers
    static String fill(String template, String... kv) {
        String out = template;
        for (int i = 0; i < kv.length; i += 2) out = out.replace("{" + kv[i] + "}", kv[i + 1]);
        return out;
    }

    static String promptVersion(Path root) throws Exception {
        MessageDigest h = MessageDigest.getInstance("SHA-256");
        for (String n : List.of("initial.txt", "repair.txt", "VERSION")) h.update(Files.readAllBytes(root.resolve("prompts/" + n)));
        StringBuilder hex = new StringBuilder();
        for (byte b : h.digest()) hex.append(String.format("%02x", b));
        return Files.readString(root.resolve("prompts/VERSION")).strip() + "-" + hex.substring(0, 8);
    }

    static void archiveRound(Path art, int k, String prompt, String response, String code, String log) throws IOException {
        Path d = art.resolve("round" + k);
        Files.createDirectories(d);
        Files.writeString(d.resolve("prompt.txt"), prompt);
        Files.writeString(d.resolve("response.txt"), response);
        Files.writeString(d.resolve("Test.java"), code == null ? "" : code);
        Files.writeString(d.resolve("build.log"), Env.tail(log, 20000));
    }

    static final class Options {
        Path dataset, out, artifacts, work;
        int repeats, maxTokens;
        String model, pv;
        Double temperature;
        boolean docker;
    }

    private static Trial row(Options a, Llm llm, Map<String, String> m, int repeat, String cond, CompileResult cr0,
                             CompileResult c, ExpCore.Pit pit, int repairs, double elapsed, int req, int tin, int tout) {
        Trial t = new Trial();
        String mid = m.get("method_id");
        t.trialId = mid + "__" + cond + "__r" + repeat; t.methodId = mid; t.category = m.get("category");
        t.condition = cond; t.repeat = repeat; t.modelId = llm.resolvedModel(); t.promptVersion = a.pv;
        t.compileInitial = cr0.compileOk ? 1 : 0; t.compileFinal = c.compileOk ? 1 : 0;
        t.execCompleted = c.execStatus.equals("ExecutionCompleted") ? 1 : 0;
        t.allPass = c.assertion.equals("AllAssertionsPass") ? 1 : 0; t.repairs = repairs;
        t.stmtCov = c.cov.stmtCov(); t.branchCov = c.cov.branchCov();
        t.mutKilled = pit.killed; t.mutEligible = pit.eligible; t.mutSurvived = pit.survived;
        t.mutTimeout = pit.timeout; t.mutExcluded = pit.excluded; t.invalidOracleTests = pit.invalidOracleTests;
        t.elapsedS = elapsed; t.requests = req; t.inTokens = tin; t.outTokens = tout; t.testsTotal = c.testsTotal;
        return t;
    }

    static List<Trial> runPair(Path root, Map<String, String> m, int repeat, Llm llm, Runner runner, Options a) throws Exception {
        String cls = m.get("method_id"), mid = cls;
        String src = Files.readString(root.resolve(m.get("source_file")));
        Path artRoot = a.artifacts.resolve(mid).resolve("r" + repeat);
        Path work = a.work.resolve(mid + "_r" + repeat);
        String initial = fill(Files.readString(root.resolve("prompts/initial.txt")), "source", src, "cls", cls);

        // ---- the single shared first request
        Llm.Completion g0 = llm.complete(initial);
        String code0 = ExpCore.extractCode(g0.text());
        CompileResult cr0 = compileAndRun(root, runner, work, cls, code0);
        archiveRound(artRoot.resolve("shared"), 0, initial, g0.text(), code0, cr0.log);
        Path artA = artRoot.resolve("A");
        Files.createDirectories(artA);
        PitRun p0 = runPit(root, runner, work, cls, code0, cr0, artA);

        // ---- Cond-B: Algorithm 1 (repair only L1/L2 diagnostics; assertion failures are NOT fed back)
        String code = code0;
        CompileResult cr = cr0;
        int r = 0, inB = g0.inTokens(), outB = g0.outTokens();
        double tLoop = 0.0;
        String repairTpl = Files.readString(root.resolve("prompts/repair.txt"));
        while (r < MAX_REPAIRS && !cr.execStatus.equals("ExecutionCompleted")) {
            String rp = fill(repairTpl, "diagnostics", cr.diag == null ? "(none)" : cr.diag,
                    "suite", code == null ? "" : code, "source", src, "cls", cls);
            Llm.Completion g = llm.complete(rp);
            r++;
            code = ExpCore.extractCode(g.text());
            cr = compileAndRun(root, runner, work, cls, code);
            archiveRound(artRoot.resolve("B"), r, rp, g.text(), code, cr.log);
            tLoop += g.seconds() + cr.seconds;
            inB += g.inTokens(); outB += g.outTokens();
        }
        Path artB = artRoot.resolve("B");
        Files.createDirectories(artB);
        PitRun pb;
        if (r == 0) {
            pb = p0;
            Files.writeString(artB.resolve("mutations.json"), ExpCore.mutantsJson(p0.pit().mutants));
        } else {
            pb = runPit(root, runner, work, cls, code, cr, artB);
        }

        // time = first request .. end of evaluation (Sec. V-D); B adds the repair loop and re-evaluation
        Trial ra = row(a, llm, m, repeat, "A", cr0, cr0, p0.pit(), 0,
                g0.seconds() + cr0.seconds + p0.seconds(), 1, g0.inTokens(), g0.outTokens());
        Trial rb = row(a, llm, m, repeat, "B", cr0, cr, pb.pit(), r,
                g0.seconds() + cr0.seconds + tLoop + pb.seconds(), 1 + r, inB, outB);
        deleteTree(work);
        return List.of(ra, rb);
    }

    static int run(String[] args) throws Exception {
        Path root = Env.root();
        Cli cli = Cli.parse(args, Set.of("no-docker", "pilot"));
        Options a = new Options();
        a.dataset = Path.of(cli.get("dataset", root.resolve("data/methods.csv").toString()));
        a.out = Path.of(cli.get("out", root.resolve("data/results.csv").toString()));
        a.artifacts = Path.of(cli.get("artifacts", root.resolve("artifacts").toString()));
        a.work = Path.of(cli.get("work", root.resolve("work").toString()));
        a.repeats = Integer.parseInt(cli.get("repeats", "3"));
        a.model = cli.get("model", System.getenv("MODEL_ID"));
        a.maxTokens = Integer.parseInt(cli.get("max-tokens", "4096"));
        String temp = cli.get("temperature", "0.0");
        a.temperature = temp.equalsIgnoreCase("none") ? null : Double.valueOf(temp);
        a.docker = !cli.flag("no-docker");
        if (cli.flag("pilot")) {
            a.dataset = root.resolve("data/pilot_methods.csv");
            a.out = root.resolve("data/results_pilot.csv");
            a.artifacts = root.resolve("artifacts_pilot");
            a.repeats = 1;
        }
        if (a.model == null || a.model.isBlank())
            throw new Fatal("set MODEL_ID (exact model identifier) or pass --model; it is recorded in every row");
        if (!a.docker) System.out.println("WARNING: --no-docker executes model-generated code on this machine without isolation.");
        a.pv = promptVersion(root);

        List<Map<String, String>> methods = Csv.read(a.dataset);
        String only = cli.get("only", null);
        if (only != null) {
            Set<String> keep = new HashSet<>(Arrays.asList(only.split(",")));
            methods = methods.stream().filter(m -> keep.contains(m.get("method_id"))).toList();
        }
        Llm llm = new Llm.Anthropic(a.model, a.maxTokens, a.temperature);
        Runner runner = new Runner(a.docker, 600);

        Files.createDirectories(a.out.toAbsolutePath().getParent());
        Set<String> done = new HashSet<>();
        if (Files.exists(a.out)) for (Map<String, String> r : Csv.read(a.out)) done.add(r.get("trial_id"));
        boolean newFile = !Files.exists(a.out) || Files.size(a.out) == 0;
        for (int rep = 1; rep <= a.repeats; rep++) {
            for (Map<String, String> m : methods) {
                String id = m.get("method_id");
                if (done.contains(id + "__A__r" + rep) && done.contains(id + "__B__r" + rep)) continue;
                System.out.println("[" + id + "] repeat " + rep + " ...");
                List<Trial> rows;
                try {
                    rows = runPair(root, m, rep, llm, runner, a);
                } catch (Exception e) { // keep going; the pair is simply re-run on resume
                    System.err.println("   FAILED " + id + " r" + rep + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    continue;
                }
                List<List<String>> out = new ArrayList<>();
                for (Trial t : rows) out.add(t.toRow());
                Csv.append(a.out, Trial.COLUMNS, out, newFile);
                newFile = false;
                Trial x = rows.get(0), y = rows.get(1);
                System.out.println("   A: compile=" + x.compileFinal + " exec=" + x.execCompleted + " pass=" + x.allPass
                        + " branch=" + x.branchCov + " | B: compile=" + y.compileFinal + " exec=" + y.execCompleted
                        + " pass=" + y.allPass + " branch=" + y.branchCov + " repairs=" + y.repairs);
            }
        }
        System.out.println("done -> " + a.out);
        return 0;
    }
}
