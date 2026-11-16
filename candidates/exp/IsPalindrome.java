package exp;

public final class IsPalindrome {
    private IsPalindrome() {}

    /**
     * Returns true if s reads the same forwards and backwards, considering only
     * letters and digits and ignoring case. The empty string and strings with no
     * letters or digits are palindromes. A null argument returns false.
     */
    public static boolean isPalindrome(String s) {
        if (s == null) {
            return false;
        }
        int i = 0;
        int j = s.length() - 1;
        while (i < j) {
            if (!Character.isLetterOrDigit(s.charAt(i))) {
                i++;
            } else if (!Character.isLetterOrDigit(s.charAt(j))) {
                j--;
            } else {
                if (Character.toLowerCase(s.charAt(i)) != Character.toLowerCase(s.charAt(j))) {
                    return false;
                }
                i++;
                j--;
            }
        }
        return true;
    }
}
