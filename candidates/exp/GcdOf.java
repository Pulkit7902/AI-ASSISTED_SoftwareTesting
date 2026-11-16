package exp;

public final class GcdOf {
    private GcdOf() {}

    /**
     * Greatest common divisor of a and b, always non-negative.
     * gcd(0, 0) is defined as 0; gcd(a, 0) = |a|; signs of the inputs are ignored.
     * Inputs are limited to values whose absolute value fits in an int
     * (Integer.MIN_VALUE is not supported).
     */
    public static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
