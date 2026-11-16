package exp;

public final class IsLeapYear {
    private IsLeapYear() {}

    /**
     * Gregorian leap-year rule: a year is a leap year if it is divisible by 4,
     * except years divisible by 100, which are leap years only if also divisible by 400.
     * Examples: 2024 true, 1900 false, 2000 true, 2023 false.
     * @throws IllegalArgumentException if year < 1
     */
    public static boolean isLeapYear(int year) {
        if (year < 1) {
            throw new IllegalArgumentException("year must be >= 1");
        }
        if (year % 400 == 0) {
            return true;
        }
        if (year % 100 == 0) {
            return false;
        }
        return year % 4 == 0;
    }
}
