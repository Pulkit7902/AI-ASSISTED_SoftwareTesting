package pipeline;

import java.nio.file.Path;

/** Project root: the current directory, or -Dpipeline.root=... */
final class Env {
    private Env() {}

    static Path root() {
        return Path.of(System.getProperty("pipeline.root", "")).toAbsolutePath().normalize();
    }

    static String tail(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }
}
