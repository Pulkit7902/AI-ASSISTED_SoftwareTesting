package pipeline;

import java.nio.file.*;
import java.util.*;

/** Pipeline self-test on SYNTHETIC data (random numbers, NOT results). Writes only to a temp dir.
 *  Respects the design constraints: both conditions share the first output; only non-executable first
 *  outputs are repaired (<=2 rounds); B can only improve compile/executable; mutation needs a valid suite. */
final class SelfTest {
    private SelfTest() {}

    private static double uni(Random r, double a, double b) { return a + (b - a) * r.nextDouble(); }

    static int run(String[] args) throws Exception {
        Path root = Env.root();
        Random rnd = new Random(7);
        List<Map<String, String>> methods = Csv.read(root.resolve("data/methods.csv"));
        List<Trial> rows = new ArrayList<>();
        for (Map<String, String> m : methods) {
            for (int rep = 1; rep <= 3; rep++) {
                boolean c0 = rnd.nextDouble() < 0.65;
                boolean e0 = c0 && rnd.nextDouble() < 0.9;
                boolean p0 = e0 && rnd.nextDouble() < 0.8;
                double baseCov = e0 ? uni(rnd, 40, 80) : 0.0;
                int repairs = 0;
                boolean c = c0, e = e0, p = p0;
                while (!e && repairs < 2) {
                    repairs++;
                    c = c || rnd.nextDouble() < 0.7;
                    e = c && rnd.nextDouble() < 0.8;
                    p = e && rnd.nextDouble() < 0.75;
                }
                int elig = 8 + rnd.nextInt(13);
                double tg = uni(rnd, 5, 12), tr = uni(rnd, 3, 8);
                for (String cond : List.of("A", "B")) {
                    boolean ce = cond.equals("A") ? c0 : c, ee = cond.equals("A") ? e0 : e, pp = cond.equals("A") ? p0 : p;
                    int rr = cond.equals("A") ? 0 : repairs;
                    double cv = cond.equals("A") ? baseCov : (rr > 0 ? (ee ? uni(rnd, 40, 85) : 0.0) : baseCov);
                    int killed = ee ? (int) (elig * uni(rnd, 0.2, 0.7)) : 0;
                    Trial t = new Trial();
                    t.trialId = m.get("method_id") + "__" + cond + "__r" + rep; t.methodId = m.get("method_id");
                    t.category = m.get("category"); t.condition = cond; t.repeat = rep; t.modelId = "SYNTHETIC";
                    t.promptVersion = "SYNTHETIC"; t.compileInitial = c0 ? 1 : 0; t.compileFinal = ce ? 1 : 0;
                    t.execCompleted = ee ? 1 : 0; t.allPass = pp ? 1 : 0; t.repairs = rr; t.stmtCov = cv;
                    t.branchCov = cv * 0.8; t.mutKilled = killed; t.mutEligible = elig; t.mutSurvived = elig - killed;
                    t.invalidOracleTests = pp ? 0 : (ee ? 1 : 0); t.elapsedS = tg + rr * tr; t.requests = 1 + rr;
                    t.inTokens = 900 + 700 * rr; t.outTokens = 500 + 400 * rr; t.testsTotal = ce ? 4 + rnd.nextInt(7) : 0;
                    rows.add(t);
                }
            }
        }
        Path tmp = Files.createTempDirectory("selftest_");
        List<List<String>> out = new ArrayList<>();
        for (Trial t : rows) out.add(t.toRow());
        Csv.write(tmp.resolve("results.csv"), Trial.COLUMNS, out);
        return MakeTables.run(new String[]{"--results", tmp.resolve("results.csv").toString(), "--artifacts", "",
                "--equivalent", tmp.resolve("none.csv").toString(), "--out", tmp.resolve("tables").toString(),
                "--banner", "*** SYNTHETIC SELF-TEST DATA -- RANDOM NUMBERS, NOT EXPERIMENTAL RESULTS ***"});
    }
}
