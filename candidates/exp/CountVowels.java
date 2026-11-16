package exp;

public final class CountVowels {
    private CountVowels() {}

    /**
     * Counts the vowels a, e, i, o, u in s, case-insensitively. 'y' is not a vowel.
     * A null or empty string has 0 vowels.
     */
    public static int countVowels(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (char c : s.toCharArray()) {
            switch (Character.toLowerCase(c)) {
                case 'a': case 'e': case 'i': case 'o': case 'u':
                    n++;
                    break;
                default:
                    break;
            }
        }
        return n;
    }
}
