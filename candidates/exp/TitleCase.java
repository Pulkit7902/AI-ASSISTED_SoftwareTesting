package exp;

public final class TitleCase {
    private TitleCase() {}

    /**
     * Upper-cases the first letter of each space-separated word and lower-cases the rest.
     * Spaces are preserved exactly (including repeated or leading/trailing spaces).
     * Returns null for null input and "" for empty input.
     * Example: titleCase("hELLO wORLD") = "Hello World".
     */
    public static String titleCase(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean startOfWord = true;
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                sb.append(c);
                startOfWord = true;
            } else if (startOfWord) {
                sb.append(Character.toUpperCase(c));
                startOfWord = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
