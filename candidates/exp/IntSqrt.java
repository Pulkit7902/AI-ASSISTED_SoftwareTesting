package exp;

public final class IntSqrt {
    private IntSqrt() {}

    /**
     * Floor of the square root of n: the largest int r with r * r <= n.
     * Examples: intSqrt(0) = 0, intSqrt(15) = 3, intSqrt(16) = 4.
     * @throws IllegalArgumentException if n < 0
     */
    public static int intSqrt(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0");
        }
        long lo = 0;
        long hi = 46340; // 46340^2 fits in an int
        while (lo < hi) {
            long mid = (lo + hi + 1) / 2;
            if (mid * mid <= n) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return (int) lo;
    }
}
