package pipeline;

/** Fatal, user-facing error (the Java equivalent of Python's sys.exit("message")). */
final class Fatal extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Fatal(String message) { super(message); }
}
