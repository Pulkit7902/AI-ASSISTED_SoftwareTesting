package pipeline;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Entry point:  java -cp out/classes pipeline.Main <select|run|tables|survivors|selftest> [options] */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        if (args.length == 0) {
            System.err.println("usage: pipeline.Main <select|run|tables|survivors|selftest> [options]");
            System.exit(2);
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            int rc = switch (args[0]) {
                case "select" -> SelectMethods.run(rest);
                case "run" -> RunExperiment.run(rest);
                case "tables" -> MakeTables.run(rest);
                case "survivors" -> ListSurvivors.run(rest);
                case "selftest" -> SelfTest.run(rest);
                default -> throw new Fatal("unknown command: " + args[0]);
            };
            System.exit(rc);
        } catch (Fatal f) {
            System.err.println(f.getMessage());
            System.exit(1);
        }
    }
}
