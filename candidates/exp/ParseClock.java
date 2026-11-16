package exp;

public final class ParseClock {
    private ParseClock() {}

    /**
     * Converts a 24-hour clock string "HH:MM" to minutes since midnight.
     * HH must be 00-23 and MM must be 00-59; both are exactly two digits.
     * Examples: "00:00" -> 0, "01:30" -> 90, "23:59" -> 1439.
     * @throws IllegalArgumentException if s is null or does not match the format or ranges
     */
    public static int parseClock(String s) {
        if (s == null || s.length() != 5 || s.charAt(2) != ':') {
            throw new IllegalArgumentException("expected HH:MM");
        }
        for (int i : new int[] {0, 1, 3, 4}) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                throw new IllegalArgumentException("non-digit in time");
            }
        }
        int hh = (s.charAt(0) - '0') * 10 + (s.charAt(1) - '0');
        int mm = (s.charAt(3) - '0') * 10 + (s.charAt(4) - '0');
        if (hh > 23 || mm > 59) {
            throw new IllegalArgumentException("time out of range");
        }
        return hh * 60 + mm;
    }
}
