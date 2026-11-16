package pipeline;

import java.nio.file.*;
import java.util.*;

/** Orchestration smoke test with a scripted LLM and a fake `mvn` on PATH (see README). */
public final class SmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Env.root();
        Path tmp = Files.createTempDirectory("smoke_");
        int[] calls = {0};
        Llm llm = new Llm() {
            public Completion complete(String prompt) {
                calls[0]++;
                String body = calls[0] == 1 ? "BROKEN class ClampTest {}" : "class ClampTest {}";
                return new Completion("<code>" + body + "</code>", 100, 50, 1.5);
            }
            public String resolvedModel() { return "fake-model"; }
        };
        RunExperiment.Options a = new RunExperiment.Options();
        a.artifacts = tmp.resolve("artifacts"); a.work = tmp.resolve("work"); a.pv = "v1-test";
        Map<String, String> m = Map.of("method_id", "Clamp", "category", "Boundary logic",
                "source_file", "candidates/exp/Clamp.java");
        List<Trial> rows = RunExperiment.runPair(root, m, 1, llm, new RunExperiment.Runner(false, 60), a);
        for (Trial t : rows) System.out.println(String.join(",", t.toRow()));
        Trial A = rows.get(0), B = rows.get(1);
        check(A.compileFinal == 0 && A.requests == 1 && A.repairs == 0, "A: one-shot, compile fails");
        check(B.compileInitial == 0 && B.compileFinal == 1 && B.execCompleted == 1 && B.allPass == 1, "B: repaired");
        check(B.repairs == 1 && B.requests == 2 && calls[0] == 2, "B: exactly one repair request");
        check(B.stmtCov == 75.0 && B.branchCov == 50.0, "coverage parsed");
        check(B.mutKilled == 1 && B.mutEligible == 2 && B.mutSurvived == 1, "mutation counts parsed");
        check(A.mutEligible == 2, "A mutation-evaluated even though its suite did not compile");
        check(Files.exists(a.artifacts.resolve("Clamp/r1/B/round1/build.log")), "artifacts archived");
        check(Files.exists(a.artifacts.resolve("Clamp/r1/B/mutations.json")), "mutations.json written");
        check(!Files.exists(a.work.resolve("Clamp_r1")), "work dir cleaned");
        System.out.println("SMOKE OK");
    }

    private static void check(boolean c, String what) {
        if (!c) { System.out.println("FAIL: " + what); System.exit(1); }
        System.out.println("ok    " + what);
    }
}
