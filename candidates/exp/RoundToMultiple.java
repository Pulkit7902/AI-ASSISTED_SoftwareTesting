package exp;

public final class RoundToMultiple {
    private RoundToMultiple() {}

    /**
     * Rounds value to the nearest multiple of m; exact halves round away from zero.
     * Examples: roundToMultiple(14, 5) = 15, roundToMultiple(12, 5) = 10,
     * roundToMultiple(-12, 5) = -10, roundToMultiple(-13, 5) = -15, roundToMultiple(15, 10) = 20.
     * @param m the multiple, must be positive
     * @throws IllegalArgumentException if m <= 0
     */
    public static int roundToMultiple(int value, int m) {
        if (m <= 0) {
            throw new IllegalArgumentException("m must be positive");
        }
        int rem = Math.abs(value) % m;
        int base = Math.abs(value) - rem;
        int mag = (rem * 2 >= m) ? base + m : base;
        return value < 0 ? -mag : mag;
    }
}
