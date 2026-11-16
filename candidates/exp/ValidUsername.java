package exp;

public final class ValidUsername {
    private ValidUsername() {}

    /**
     * A username is valid when it has 3 to 16 characters inclusive, consists only of
     * ASCII letters, ASCII digits, and underscores, and does not start with a digit.
     * Null is invalid.
     */
    public static boolean isValidUsername(String s) {
        if (s == null || s.length() < 3 || s.length() > 16) {
            return false;
        }
        if (Character.isDigit(s.charAt(0))) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
