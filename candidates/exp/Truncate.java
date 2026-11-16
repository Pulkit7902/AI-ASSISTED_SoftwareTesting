package exp;

public final class Truncate {
    private Truncate() {}

    /**
     * Shortens s to at most max characters. If s is longer than max, the result is the
     * first (max - 3) characters followed by "..." so the total length equals max.
     * If max < 3 and s is longer than max, the result is the first max characters with no ellipsis.
     * Strings with length <= max are returned unchanged. Returns null for null input.
     * @throws IllegalArgumentException if max < 0
     */
    public static String truncate(String s, int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max must be >= 0");
        }
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        if (max < 3) {
            return s.substring(0, max);
        }
        return s.substring(0, max - 3) + "...";
    }
}
