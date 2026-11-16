package pipeline;

import java.util.*;

/** Tiny "--key value" / "--flag" parser. */
final class Cli {
    private final Map<String, String> opts = new HashMap<>();
    private final Set<String> flags = new HashSet<>();

    static Cli parse(String[] args, Set<String> boolFlags) {
        Cli c = new Cli();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) throw new Fatal("unexpected argument: " + a);
            String k = a.substring(2);
            if (boolFlags.contains(k)) {
                c.flags.add(k);
            } else {
                if (i + 1 >= args.length) throw new Fatal("missing value for " + a);
                c.opts.put(k, args[++i]);
            }
        }
        return c;
    }

    String get(String key, String def) { return opts.getOrDefault(key, def); }
    boolean flag(String key) { return flags.contains(key); }
}
