package exp;

public final class Clamp {
    private Clamp() {}

    /**
     * Restricts v to the inclusive range [lo, hi]: returns lo if v < lo, hi if v > hi,
     * otherwise v.
     * @throws IllegalArgumentException if lo > hi
     */
    public static int clamp(int v, int lo, int hi) {
        if (lo > hi) {
            throw new IllegalArgumentException("lo must not exceed hi");
        }
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }
}
