package exp;

public final class ValidHexColor {
    private ValidHexColor() {}

    /**
     * Returns true for CSS-style hex colors: '#' followed by exactly 3 or exactly 6
     * hexadecimal digits (0-9, a-f, A-F). Null is invalid.
     */
    public static boolean isValidHexColor(String s) {
        if (s == null || s.isEmpty() || s.charAt(0) != '#') {
            return false;
        }
        int digits = s.length() - 1;
        if (digits != 3 && digits != 6) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
