package pipeline;

import java.util.*;

/** One row of results.csv (Appendix A columns + tests_total). */
final class Trial {
    static final List<String> COLUMNS = List.of("trial_id", "method_id", "category", "condition", "repeat",
            "model_id", "prompt_version", "compile_initial", "compile_final", "exec_completed", "all_pass",
            "repairs", "stmt_cov", "branch_cov", "mut_killed", "mut_eligible", "mut_survived", "mut_timeout",
            "mut_excluded", "invalid_oracle_tests", "elapsed_s", "requests", "in_tokens", "out_tokens",
            "tests_total");

    String trialId, methodId, category, condition, modelId, promptVersion;
    int repeat, compileInitial, compileFinal, execCompleted, allPass, repairs;
    double stmtCov, branchCov;
    int mutKilled, mutEligible, mutSurvived, mutTimeout, mutExcluded, invalidOracleTests;
    double elapsedS;
    int requests, inTokens, outTokens, testsTotal;

    private static double round2(double x) { return Math.round(x * 100.0) / 100.0; }

    List<String> toRow() {
        return List.of(trialId, methodId, category, condition, String.valueOf(repeat), modelId, promptVersion,
                String.valueOf(compileInitial), String.valueOf(compileFinal), String.valueOf(execCompleted),
                String.valueOf(allPass), String.valueOf(repairs), String.valueOf(round2(stmtCov)),
                String.valueOf(round2(branchCov)), String.valueOf(mutKilled), String.valueOf(mutEligible),
                String.valueOf(mutSurvived), String.valueOf(mutTimeout), String.valueOf(mutExcluded),
                String.valueOf(invalidOracleTests), String.valueOf(round2(elapsedS)), String.valueOf(requests),
                String.valueOf(inTokens), String.valueOf(outTokens), String.valueOf(testsTotal));
    }

    private static int i(Map<String, String> m, String k) { return (int) Math.round(Double.parseDouble(m.get(k))); }
    private static double d(Map<String, String> m, String k) { return Double.parseDouble(m.get(k)); }

    static Trial fromMap(Map<String, String> m) {
        Trial t = new Trial();
        t.trialId = m.get("trial_id"); t.methodId = m.get("method_id"); t.category = m.get("category");
        t.condition = m.get("condition"); t.modelId = m.get("model_id"); t.promptVersion = m.get("prompt_version");
        t.repeat = i(m, "repeat"); t.compileInitial = i(m, "compile_initial"); t.compileFinal = i(m, "compile_final");
        t.execCompleted = i(m, "exec_completed"); t.allPass = i(m, "all_pass"); t.repairs = i(m, "repairs");
        t.stmtCov = d(m, "stmt_cov"); t.branchCov = d(m, "branch_cov");
        t.mutKilled = i(m, "mut_killed"); t.mutEligible = i(m, "mut_eligible"); t.mutSurvived = i(m, "mut_survived");
        t.mutTimeout = i(m, "mut_timeout"); t.mutExcluded = i(m, "mut_excluded");
        t.invalidOracleTests = i(m, "invalid_oracle_tests"); t.elapsedS = d(m, "elapsed_s");
        t.requests = i(m, "requests"); t.inTokens = i(m, "in_tokens"); t.outTokens = i(m, "out_tokens");
        t.testsTotal = i(m, "tests_total");
        return t;
    }
}
