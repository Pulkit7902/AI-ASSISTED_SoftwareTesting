package pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Regenerate every result table from results.csv (paper Sec. V-E, VII).
 * Per-method statistics (repeats averaged first; the METHOD is the unit of inference):
 * paired difference, 95% percentile bootstrap CI (10,000 resamples, fixed seed), exact tie-aware
 * Wilcoxon, Vargha-Delaney A12, Holm over the three primary endpoints.
 */
final class MakeTables {
    private MakeTables() {}

    static final long SEED = 20260101L;
    static final int N_BOOT = 10_000;
    static final List<String> PRIMARY = List.of("compile", "branch_cov", "mutation");

    record Label(String key, String text, String unit) {}

    static final List<Label> LABELS = List.of(
            new Label("compile", "Compilation, final (%)", "pp"),
            new Label("exec", "Executable suites (%)", "pp"),
            new Label("pass", "Passing suites (%)", "pp"),
            new Label("stmt_cov", "Instruction coverage (%)", "pp"),
            new Label("branch_cov", "Branch coverage (%)", "pp"),
            new Label("mutation", "Mutation score, primary (%)", "pp"),
            new Label("mutation_strict", "Mutation score, strict (%)", "pp"),
            new Label("elapsed", "Elapsed time (s)", "s"),
            new Label("requests", "Model requests", "req"));

    record Loaded(List<Trial> rows, int nAdjusted) {}

    /** metric -> per-method means for A and B (methods in sorted order). */
    record PerMethod(List<String> methods, Map<String, double[]> a, Map<String, double[]> b) {}

    static String fmtP(double p) {
        return p >= 0.001 ? String.format(Locale.ROOT, "%.3f", p) : String.format(Locale.ROOT, "%.1e", p);
    }

    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    private static String f2(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    // ------------------------------------------------------------------ data
    static Loaded load(Path results, String artifacts, Path equivPath) throws Exception {
        List<Trial> rows = new ArrayList<>();
        for (Map<String, String> m : Csv.read(results)) rows.add(Trial.fromMap(m));
        if (rows.isEmpty()) throw new Fatal("results.csv is empty");
        Map<String, Set<String>> equiv = new HashMap<>();
        if (equivPath != null && Files.exists(equivPath)) {
            for (Map<String, String> e : Csv.read(equivPath)) {
                String fin = e.getOrDefault("final", "").strip().toLowerCase(Locale.ROOT);
                if (Set.of("equivalent", "yes", "1", "true").contains(fin))
                    equiv.computeIfAbsent(e.get("method_id"), k -> new HashSet<>()).add(e.get("mutant_key"));
            }
        }
        int nAdj = 0;
        for (Trial r : rows) { // remove reviewed-equivalent mutants from the eligible set
            Set<String> eq = equiv.get(r.methodId);
            if (eq == null || eq.isEmpty() || artifacts == null || artifacts.isEmpty()) continue;
            Path mj = Path.of(artifacts, r.methodId, "r" + r.repeat, r.condition, "mutations.json");
            if (!Files.exists(mj)) continue;
            int surv = 0;
            for (Object o : Json.list(Json.parse(Files.readString(mj)))) {
                Map<String, Object> m = Json.obj(o);
                String st = (String) m.get("status");
                if ((st.equals("SURVIVED") || st.equals("NO_COVERAGE")) && eq.contains((String) m.get("key"))) surv++;
            }
            r.mutEligible -= surv; r.mutExcluded += surv; r.mutSurvived -= surv; nAdj += surv;
        }
        List<String> bad = new ArrayList<>();
        for (Trial r : rows) if (r.mutEligible <= 0) bad.add(r.trialId);
        if (!bad.isEmpty())
            throw new Fatal("mut_eligible == 0 (PIT failed) for: "
                    + String.join(", ", bad.subList(0, Math.min(8, bad.size()))) + " ... rerun these trials");
        return new Loaded(rows, nAdj);
    }

    static Map<String, ToDoubleFunction<Trial>> metrics() {
        ToDoubleFunction<Trial> ms = r -> 100.0 * r.mutKilled / r.mutEligible;
        Map<String, ToDoubleFunction<Trial>> m = new LinkedHashMap<>();
        m.put("compile", r -> 100.0 * r.compileFinal);
        m.put("exec", r -> 100.0 * r.execCompleted);
        m.put("pass", r -> 100.0 * r.allPass);
        m.put("stmt_cov", r -> r.stmtCov);
        m.put("branch_cov", r -> r.branchCov);
        m.put("mutation", ms);
        m.put("mutation_strict", r -> r.allPass != 0 ? ms.applyAsDouble(r) : 0.0);
        m.put("elapsed", r -> r.elapsedS);
        m.put("requests", r -> r.requests);
        return m;
    }

    static PerMethod perMethod(List<Trial> rows) {
        Map<String, List<Trial>> by = new HashMap<>();
        for (Trial r : rows) by.computeIfAbsent(r.methodId + "\u0000" + r.condition, k -> new ArrayList<>()).add(r);
        List<String> methods = new ArrayList<>(new TreeSet<>(rows.stream().map(r -> r.methodId).toList()));
        for (String m : methods)
            for (String c : List.of("A", "B"))
                if (!by.containsKey(m + "\u0000" + c)) throw new Fatal("method " + m + " has no rows for condition " + c);
        Map<String, double[]> a = new LinkedHashMap<>(), b = new LinkedHashMap<>();
        for (Map.Entry<String, ToDoubleFunction<Trial>> e : metrics().entrySet()) {
            double[] xa = new double[methods.size()], xb = new double[methods.size()];
            for (int i = 0; i < methods.size(); i++) {
                xa[i] = by.get(methods.get(i) + "\u0000A").stream().mapToDouble(e.getValue()).average().orElse(Double.NaN);
                xb[i] = by.get(methods.get(i) + "\u0000B").stream().mapToDouble(e.getValue()).average().orElse(Double.NaN);
            }
            a.put(e.getKey(), xa);
            b.put(e.getKey(), xb);
        }
        return new PerMethod(methods, a, b);
    }

    private static double meanOf(Collection<Trial> rows, ToDoubleFunction<Trial> f) {
        return rows.stream().mapToDouble(f).average().orElse(Double.NaN);
    }

    // ------------------------------------------------------------------ main
    static int run(String[] args) throws Exception {
        Path root = Env.root();
        Cli a = Cli.parse(args, Set.of());
        Path results = Path.of(a.get("results", root.resolve("data/results.csv").toString()));
        String artifacts = a.get("artifacts", root.resolve("artifacts").toString());
        Path equivalent = Path.of(a.get("equivalent", root.resolve("data/equivalence_review.csv").toString()));
        Path out = Path.of(a.get("out", root.resolve("tables").toString()));
        String banner = a.get("banner", "");
        Files.createDirectories(out);

        Loaded loaded = load(results, artifacts, equivalent);
        List<Trial> rows = loaded.rows();
        PerMethod pm = perMethod(rows);
        List<String> methods = pm.methods();
        Random rng = new Random(SEED);

        Map<String, Stats.Summary> stats = new LinkedHashMap<>();
        for (Label l : LABELS)
            stats.put(l.key(), Stats.summarize(l.text(), pm.a().get(l.key()), pm.b().get(l.key()), rng, l.unit(), N_BOOT));
        double[] rawP = PRIMARY.stream().mapToDouble(k -> stats.get(k).p()).toArray();
        double[] holm = Stats.holm(rawP);
        Map<String, Double> adj = new LinkedHashMap<>();
        for (int i = 0; i < PRIMARY.size(); i++) adj.put(PRIMARY.get(i), holm[i]);
        Map<String, String> labelText = new HashMap<>();
        for (Label l : LABELS) labelText.put(l.key(), l.text());

        List<String> lines = new ArrayList<>();
        if (!banner.isEmpty()) { lines.add(banner); lines.add(""); }
        TreeSet<Integer> reps = new TreeSet<>();
        TreeSet<String> models = new TreeSet<>(), prompts = new TreeSet<>();
        for (Trial r : rows) { reps.add(r.repeat); models.add(r.modelId); prompts.add(r.promptVersion); }
        lines.add(methods.size() + " methods, repeats " + reps + ", " + rows.size() + " trial rows; model(s): "
                + String.join(", ", models) + "; prompt: " + String.join(", ", prompts));
        lines.add("");
        lines.add("## Table VII (paired B - A; CI = 95% bootstrap of the mean difference; p = exact Wilcoxon, unadjusted)");
        lines.add("");
        lines.add("| Metric | Cond. A | Cond. B | \u0394 (B\u2212A) | 95% CI | p / A12 | \u0394>0 / =0 / <0 |");
        lines.add("|---|---|---|---|---|---|---|");
        for (Label l : LABELS) {
            Stats.Summary s = stats.get(l.key());
            boolean req = l.unit().equals("req");
            java.util.function.DoubleFunction<String> f = req ? MakeTables::f2 : MakeTables::f1;
            lines.add("| " + l.text() + " | " + f.apply(s.meanA()) + " | " + f.apply(s.meanB()) + " | "
                    + String.format(Locale.ROOT, "%+.1f", s.delta()) + " | [" + f1(s.ciLo()) + ", " + f1(s.ciHi())
                    + "] | " + fmtP(s.p()) + " / " + f2(s.a12()) + " | " + s.nPos() + " / " + s.nZero() + " / " + s.nNeg() + " |");
        }
        lines.add("");
        lines.add("## Holm correction (primary endpoints: compilation, branch coverage, mutation score)");
        lines.add("");
        lines.add("| Endpoint | p (exact) | Holm-adjusted p | effective n (non-tied pairs) |");
        lines.add("|---|---|---|---|");
        for (String k : PRIMARY)
            lines.add("| " + labelText.get(k) + " | " + fmtP(stats.get(k).p()) + " | " + fmtP(adj.get(k)) + " | "
                    + stats.get(k).nEffective() + " |");
        StringBuilder eff = new StringBuilder("Effective n for all endpoints: ");
        for (int i = 0; i < LABELS.size(); i++) {
            if (i > 0) eff.append(", ");
            eff.append(LABELS.get(i).key()).append('=').append(stats.get(LABELS.get(i).key()).nEffective());
        }
        lines.add("");
        lines.add(eff.toString());

        // ---- repair / cost / oracle quality (RQ1 detail, RQ4)
        List<Trial> A = rows.stream().filter(r -> r.condition.equals("A")).toList();
        List<Trial> B = rows.stream().filter(r -> r.condition.equals("B")).toList();
        Map<String, Trial> ad = new HashMap<>();
        for (Trial r : A) ad.put(r.methodId + "\u0000" + r.repeat, r);
        List<Trial> initNonexec = new ArrayList<>();
        for (Trial b : B) if (ad.get(b.methodId + "\u0000" + b.repeat).execCompleted == 0) initNonexec.add(b);
        double repSucc = initNonexec.isEmpty() ? Double.NaN
                : initNonexec.stream().mapToInt(t -> t.execCompleted).sum() / (double) initNonexec.size();
        int uA = A.stream().mapToInt(r -> r.execCompleted).sum(), uB = B.stream().mapToInt(r -> r.execCompleted).sum();
        int dq = B.stream().mapToInt(r -> r.requests).sum() - A.stream().mapToInt(r -> r.requests).sum();
        String cps = uB > uA ? f2((double) dq / (uB - uA)) + " requests per additional executable suite"
                : "no gain (U_B <= U_A): undefined";
        double meanRep = meanOf(B, r -> r.repairs);
        lines.add("");
        lines.add("## Repair, cost, and oracle quality");
        lines.add("");
        lines.add("- Initial (pre-repair) compile rate, Cond-B first output: " + f1(100 * meanOf(B, r -> r.compileInitial))
                + "%  | final: " + f1(100 * meanOf(B, r -> r.compileFinal)) + "%");
        lines.add("- Repair success (initially non-executable suites executable after <=2 rounds): "
                + (initNonexec.isEmpty() ? "n/a (no initially non-executable suites)"
                : f1(100 * repSucc) + "% of " + initNonexec.size() + " trials"));
        lines.add("- Eq. 4  mean repair rounds per trial = " + f2(meanRep) + "; mean extra requests (B-A) = "
                + f2(stats.get("requests").delta()) + "  [must be equal]");
        lines.add("- Eq. 5  cost per additional usable suite: " + cps);
        lines.add("- Invalid-oracle tests per suite: A " + f2(meanOf(A, r -> r.invalidOracleTests)) + ", B "
                + f2(meanOf(B, r -> r.invalidOracleTests)) + "; suites with >=1 invalid test: A "
                + f1(100 * meanOf(A, r -> r.invalidOracleTests > 0 ? 1 : 0)) + "%, B "
                + f1(100 * meanOf(B, r -> r.invalidOracleTests > 0 ? 1 : 0)) + "%");
        lines.add("- Tokens per method (mean): A in/out " + String.format(Locale.ROOT, "%.0f", meanOf(A, r -> r.inTokens))
                + "/" + String.format(Locale.ROOT, "%.0f", meanOf(A, r -> r.outTokens)) + "; B in/out "
                + String.format(Locale.ROOT, "%.0f", meanOf(B, r -> r.inTokens)) + "/"
                + String.format(Locale.ROOT, "%.0f", meanOf(B, r -> r.outTokens)));
        if (loaded.nAdjusted() > 0)
            lines.add("- " + loaded.nAdjusted() + " surviving mutants removed as reviewed-equivalent (data/equivalence_review.csv)");
        if (Files.exists(equivalent)) {
            List<String> r1 = new ArrayList<>(), r2 = new ArrayList<>();
            for (Map<String, String> e : Csv.read(equivalent)) {
                String x = e.getOrDefault("reviewer1", ""), y = e.getOrDefault("reviewer2", "");
                if (!x.isEmpty() && !y.isEmpty()) { r1.add(x); r2.add(y); }
            }
            if (!r1.isEmpty())
                lines.add("- Equivalent-mutant review: Cohen's kappa = " + f2(Stats.cohenKappa(r1, r2)) + " over " + r1.size() + " mutants");
        }
        if (Math.abs(meanRep - stats.get("requests").delta()) > 1e-9)
            lines.add("!! CHECK: Eq. 4 violated (requests != 1 + repairs) -- inspect results.csv");

        // ---- per-stratum
        lines.add("");
        lines.add("## Per stratum (mean over methods)");
        lines.add("");
        lines.add("| Stratum | n | Compile A\u2192B | Branch A\u2192B | Mutation A\u2192B |");
        lines.add("|---|---|---|---|---|");
        Map<String, String> cat = new HashMap<>();
        for (Trial r : rows) cat.put(r.methodId, r.category);
        for (String c : new TreeSet<>(cat.values())) {
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < methods.size(); i++) if (cat.get(methods.get(i)).equals(c)) idx.add(i);
            lines.add("| " + c + " | " + idx.size() + " | " + g(pm, "compile", idx) + " | " + g(pm, "branch_cov", idx)
                    + " | " + g(pm, "mutation", idx) + " |");
        }
        lines.add("");
        lines.add("Notes: per-method rates are fractions of repeats (so values are not strictly 0/1 when repeats>1). "
                + "Coverage is measured on the unmodified final suite; mutation score on the quarantined suite "
                + "(failing tests disabled); 'strict' scores 0 for any suite with a failing test. "
                + "Elapsed time for A and B is identical when no repair was needed (same work).");

        String text = String.join("\n", lines) + "\n";
        Files.writeString(out.resolve("tables.md"), text, StandardCharsets.UTF_8);

        List<List<String>> t7 = new ArrayList<>();
        for (Label l : LABELS) {
            Stats.Summary s = stats.get(l.key());
            t7.add(List.of(l.text(), String.valueOf(s.meanA()), String.valueOf(s.meanB()), String.valueOf(s.delta()),
                    String.valueOf(s.ciLo()), String.valueOf(s.ciHi()), String.valueOf(s.p()),
                    adj.containsKey(l.key()) ? String.valueOf(adj.get(l.key())) : "", String.valueOf(s.a12()),
                    String.valueOf(s.nPos()), String.valueOf(s.nZero()), String.valueOf(s.nNeg()),
                    String.valueOf(s.nEffective()), String.valueOf(s.sd()), String.valueOf(s.median()),
                    String.valueOf(s.iqr())));
        }
        Csv.write(out.resolve("table_vii.csv"), List.of("metric", "mean_A", "mean_B", "delta", "ci_lo", "ci_hi",
                "p_exact", "holm_p", "A12", "n_pos", "n_zero", "n_neg", "n_effective", "sd", "median", "iqr"), t7);

        List<String> hdr = new ArrayList<>(List.of("method_id", "category"));
        for (Label l : LABELS) { hdr.add(l.key() + "_A"); hdr.add(l.key() + "_B"); }
        List<List<String>> pmRows = new ArrayList<>();
        for (int i = 0; i < methods.size(); i++) {
            List<String> row = new ArrayList<>(List.of(methods.get(i), cat.get(methods.get(i))));
            for (Label l : LABELS) {
                row.add(String.valueOf(Math.round(pm.a().get(l.key())[i] * 1e4) / 1e4));
                row.add(String.valueOf(Math.round(pm.b().get(l.key())[i] * 1e4) / 1e4));
            }
            pmRows.add(row);
        }
        Csv.write(out.resolve("per_method.csv"), hdr, pmRows);
        System.out.print(text);
        return 0;
    }

    private static String g(PerMethod pm, String key, List<Integer> idx) {
        double sa = 0, sb = 0;
        for (int i : idx) { sa += pm.a().get(key)[i]; sb += pm.b().get(key)[i]; }
        return f1(sa / idx.size()) + "\u2192" + f1(sb / idx.size());
    }
}
