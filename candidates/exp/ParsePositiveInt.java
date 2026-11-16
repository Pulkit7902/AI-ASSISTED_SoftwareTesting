package exp;

public final class ParsePositiveInt {
    private ParsePositiveInt() {}

    /**
     * Parses a base-10 string into a strictly positive int. Surrounding whitespace is
     * ignored. A leading '+' is allowed.
     * @throws IllegalArgumentException if s is null, blank, not a valid int,
     *         or the parsed value is <= 0
     */
    public static int parsePositiveInt(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("input is null or blank");
        }
        int v;
        try {
            v = Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not an integer: " + s, e);
        }
        if (v <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }
        return v;
    }
}
