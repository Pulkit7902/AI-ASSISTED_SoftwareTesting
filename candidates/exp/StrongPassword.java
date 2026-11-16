package exp;

public final class StrongPassword {
    private StrongPassword() {}

    /**
     * A password is strong when it has at least 8 characters, contains at least one
     * upper-case letter, one lower-case letter, and one digit, and contains no space
     * characters. Null is not strong.
     */
    public static boolean isStrongPassword(String s) {
        if (s == null || s.length() < 8) {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                return false;
            }
            if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        return upper && lower && digit;
    }
}
