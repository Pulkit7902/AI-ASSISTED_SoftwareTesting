package exp;

public final class SafeAdd {
    private SafeAdd() {}

    /**
     * Adds two ints and detects overflow.
     * Returns a + b when the mathematical sum fits in an int.
     * @throws ArithmeticException if the sum is outside the int range
     */
    public static int safeAdd(int a, int b) {
        long sum = (long) a + (long) b;
        if (sum > Integer.MAX_VALUE || sum < Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        }
        return (int) sum;
    }
}
