package exp;

public final class CeilDiv {
    private CeilDiv() {}

    /**
     * Integer division rounded toward positive infinity (ceiling of a / b).
     * Examples: ceilDiv(7, 2) = 4, ceilDiv(-7, 2) = -3, ceilDiv(6, 3) = 2.
     * @throws ArithmeticException if b == 0
     */
    public static int ceilDiv(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("division by zero");
        }
        int q = a / b;
        if (a % b != 0 && ((a < 0) == (b < 0))) {
            q++;
        }
        return q;
    }
}
