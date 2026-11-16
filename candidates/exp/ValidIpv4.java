package exp;

public final class ValidIpv4 {
    private ValidIpv4() {}

    /**
     * Returns true if s is a dotted-quad IPv4 address: exactly four decimal fields
     * separated by single dots, each 0-255, with no leading zeros (except "0" itself),
     * no signs, and no whitespace. Null is invalid.
     */
    public static boolean isValidIpv4(String s) {
        if (s == null) {
            return false;
        }
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3) {
                return false;
            }
            if (p.length() > 1 && p.charAt(0) == '0') {
                return false;
            }
            int v = 0;
            for (int i = 0; i < p.length(); i++) {
                char c = p.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
                v = v * 10 + (c - '0');
            }
            if (v > 255) {
                return false;
            }
        }
        return true;
    }
}
