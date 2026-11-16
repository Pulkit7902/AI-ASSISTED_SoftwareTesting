package pipeline;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/**
 * Reproducible, outcome-blind method selection (paper Sec. V-B). SHA-256 of each fully qualified
 * signature; within each stratum the first K qualifying candidates in hash order are selected.
 * Writes data/candidates.csv, data/methods.csv (the frozen dataset), data/pilot_methods.csv.
 */
final class SelectMethods {
    private SelectMethods() {}

    static final int K = 3;
    static final List<String> STRATA = List.of("Arithmetic", "String processing", "Collections",
            "Input validation", "Boundary logic", "Exception handling");

    /** class -> {stratum, fully qualified signature used for hashing} (insertion-ordered). */
    static final Map<String, String[]> CANDIDATES = new LinkedHashMap<>();
    static {
        c("SafeAdd", "Arithmetic", "exp.SafeAdd#safeAdd(int,int)");
        c("CeilDiv", "Arithmetic", "exp.CeilDiv#ceilDiv(int,int)");
        c("GcdOf", "Arithmetic", "exp.GcdOf#gcd(int,int)");
        c("RoundToMultiple", "Arithmetic", "exp.RoundToMultiple#roundToMultiple(int,int)");
        c("IsPalindrome", "String processing", "exp.IsPalindrome#isPalindrome(java.lang.String)");
        c("CountVowels", "String processing", "exp.CountVowels#countVowels(java.lang.String)");
        c("TitleCase", "String processing", "exp.TitleCase#titleCase(java.lang.String)");
        c("Truncate", "String processing", "exp.Truncate#truncate(java.lang.String,int)");
        c("SecondLargest", "Collections", "exp.SecondLargest#secondLargest(int[])");
        c("DedupKeepOrder", "Collections", "exp.DedupKeepOrder#dedup(java.util.List)");
        c("MaxRunLength", "Collections", "exp.MaxRunLength#maxRunLength(int[])");
        c("IsSortedAscending", "Collections", "exp.IsSortedAscending#isSortedAscending(int[])");
        c("ValidUsername", "Input validation", "exp.ValidUsername#isValidUsername(java.lang.String)");
        c("ValidHexColor", "Input validation", "exp.ValidHexColor#isValidHexColor(java.lang.String)");
        c("ValidIpv4", "Input validation", "exp.ValidIpv4#isValidIpv4(java.lang.String)");
        c("StrongPassword", "Input validation", "exp.StrongPassword#isStrongPassword(java.lang.String)");
        c("Clamp", "Boundary logic", "exp.Clamp#clamp(int,int,int)");
        c("LetterGrade", "Boundary logic", "exp.LetterGrade#letterGrade(int)");
        c("IsLeapYear", "Boundary logic", "exp.IsLeapYear#isLeapYear(int)");
        c("TicketPrice", "Boundary logic", "exp.TicketPrice#ticketPriceCents(int)");
        c("ParsePositiveInt", "Exception handling", "exp.ParsePositiveInt#parsePositiveInt(java.lang.String)");
        c("IntSqrt", "Exception handling", "exp.IntSqrt#intSqrt(int)");
        c("Withdraw", "Exception handling", "exp.Withdraw#withdraw(int,int)");
        c("ParseClock", "Exception handling", "exp.ParseClock#parseClock(java.lang.String)");
    }

    private static void c(String cls, String stratum, String sig) { CANDIDATES.put(cls, new String[]{stratum, sig}); }

    static final class Cand {
        String id, stratum, signature, sha256, sourceFile, reason = "";
        boolean qualifies;
    }

    static String sha256Hex(String s) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Inclusion criteria that can be checked mechanically: {qualifies, reason}. */
    static Object[] qualifies(Path src, String cls) throws Exception {
        Path f = src.resolve(cls + ".java");
        if (!Files.exists(f)) return new Object[]{false, "source file missing"};
        String text = Files.readString(f);
        if (!text.contains("/**")) return new Object[]{false, "no Javadoc contract"};
        if (text.lines().filter(l -> !l.isBlank()).count() > 60) return new Object[]{false, ">60 lines"};
        if (!Pattern.compile("\\b(if|switch|while|for)\\b").matcher(text).find())
            return new Object[]{false, "no branch"};
        if (Pattern.compile("System\\.|Random|LocalDate|Thread|File").matcher(text).find())
            return new Object[]{false, "non-deterministic or I/O"};
        return new Object[]{true, ""};
    }

    private static String stripChars(String s, String chars) {
        int a = 0, b = s.length();
        while (a < b && chars.indexOf(s.charAt(a)) >= 0) a++;
        while (b > a && chars.indexOf(s.charAt(b - 1)) >= 0) b--;
        return s.substring(a, b);
    }

    private static String summary(Path root, String sourceFile) throws Exception {
        Matcher m = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL).matcher(Files.readString(root.resolve(sourceFile)));
        if (!m.find()) throw new Fatal("no Javadoc in " + sourceFile);
        List<String> parts = new ArrayList<>();
        m.group(1).strip().lines().forEach(l -> {
            String t = stripChars(l, " *");
            if (!t.isEmpty()) parts.add(t);
        });
        String s = String.join(" ", parts);
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    static int run(String[] args) throws Exception {
        Path root = Env.root();
        Path src = root.resolve("candidates/exp");
        List<Cand> rows = new ArrayList<>();
        for (Map.Entry<String, String[]> e : CANDIDATES.entrySet()) {
            Cand c = new Cand();
            c.id = e.getKey(); c.stratum = e.getValue()[0]; c.signature = e.getValue()[1];
            c.sha256 = sha256Hex(c.signature);
            Object[] q = qualifies(src, c.id);
            c.qualifies = (Boolean) q[0]; c.reason = (String) q[1];
            c.sourceFile = "candidates/exp/" + c.id + ".java";
            rows.add(c);
        }
        List<List<String>> cand = new ArrayList<>();
        for (Cand c : rows)
            cand.add(List.of(c.id, c.stratum, c.signature, c.sha256, c.sourceFile, c.qualifies ? "True" : "False", c.reason));
        Csv.write(root.resolve("data/candidates.csv"),
                List.of("method_id", "stratum", "signature", "sha256", "source_file", "qualifies", "reason"), cand);

        List<Cand> selected = new ArrayList<>();
        List<String> log = new ArrayList<>();
        for (String stratum : STRATA) {
            List<Cand> pool = new ArrayList<>(rows.stream().filter(r -> r.stratum.equals(stratum)).toList());
            pool.sort(Comparator.comparing(r -> r.sha256));
            int taken = 0;
            for (Cand r : pool) {
                if (!r.qualifies) { log.add("EXCLUDED " + r.id + ": " + r.reason); continue; }
                if (taken < K) { selected.add(r); taken++; }
                else log.add("not selected (hash order) " + r.id);
            }
            if (taken < K) throw new Fatal("stratum " + stratum + ": only " + taken + " qualifying candidates (need " + K + ")");
        }

        List<String> hdr = List.of("method_id", "category", "signature", "sha256", "source_file",
                "contract_summary", "inclusion_reason");
        List<List<String>> methods = new ArrayList<>();
        for (Cand r : selected)
            methods.add(List.of(r.id, r.stratum, r.signature, r.sha256, r.sourceFile, summary(root, r.sourceFile),
                    "first " + K + " qualifying in SHA-256 order within stratum"));
        Csv.write(root.resolve("data/methods.csv"), hdr, methods);
        Files.writeString(root.resolve("data/selection_log.txt"), String.join("\n", log) + "\n");

        // Pilot uses 2 HELD-OUT methods (not in the final dataset) so pilot output never leaks into results.
        Set<String> chosen = new HashSet<>();
        for (Cand r : selected) chosen.add(r.id);
        List<Cand> held = new ArrayList<>(rows.stream().filter(r -> !chosen.contains(r.id) && r.qualifies).toList());
        held.sort(Comparator.comparing(r -> r.sha256));
        List<List<String>> pilot = new ArrayList<>();
        for (Cand r : held.subList(0, Math.min(2, held.size())))
            pilot.add(List.of(r.id, r.stratum, r.signature, r.sha256, r.sourceFile, "",
                    "pilot (held out of the final dataset)"));
        Csv.write(root.resolve("data/pilot_methods.csv"), hdr, pilot);

        System.out.println("selected " + selected.size() + " methods -> data/methods.csv (freeze this file before the run)");
        for (String l : log) System.out.println("   " + l);
        return 0;
    }
}
