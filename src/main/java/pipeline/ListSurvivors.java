package pipeline;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * List mutants that survive EVERY final suite in BOTH conditions (candidates for equivalence review).
 * Writes data/equivalence_review.csv with empty reviewer columns; two reviewers fill reviewer1/reviewer2
 * (equivalent / not_equivalent), blind to condition; `final` records the agreed outcome.
 */
final class ListSurvivors {
    private ListSurvivors() {}

    static int run(String[] args) throws Exception {
        Path root = Env.root();
        Path art = root.resolve("artifacts");
        Map<String, Set<String>> surv = new TreeMap<>();
        if (Files.isDirectory(art)) {
            List<Path> files = new ArrayList<>();
            try (Stream<Path> s = Files.walk(art, 4)) { // artifacts/<method>/r<k>/<cond>/mutations.json
                s.filter(p -> p.getFileName().toString().equals("mutations.json")
                        && art.relativize(p).getNameCount() == 4
                        && art.relativize(p).getName(1).toString().startsWith("r")).sorted().forEach(files::add);
            }
            for (Path mj : files) {
                String method = art.relativize(mj).getName(0).toString();
                Set<String> s = new TreeSet<>();
                for (Object o : Json.list(Json.parse(Files.readString(mj)))) {
                    Map<String, Object> m = Json.obj(o);
                    String st = (String) m.get("status");
                    if (st.equals("SURVIVED") || st.equals("NO_COVERAGE")) s.add((String) m.get("key"));
                }
                Set<String> prev = surv.get(method);
                if (prev == null) surv.put(method, s); else prev.retainAll(s);
            }
        }
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : surv.entrySet())
            for (String k : e.getValue()) rows.add(List.of(e.getKey(), k, "", "", ""));
        Path out = root.resolve("data/equivalence_review.csv");
        Csv.write(out, List.of("method_id", "mutant_key", "reviewer1", "reviewer2", "final"), rows);
        System.out.println("wrote " + out);
        return 0;
    }
}
