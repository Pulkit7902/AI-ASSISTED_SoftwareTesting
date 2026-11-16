package exp;

public final class MaxRunLength {
    private MaxRunLength() {}

    /**
     * Length of the longest run of equal consecutive values in a.
     * Example: {1, 1, 2, 2, 2, 1} -> 3. Returns 0 for null or empty arrays.
     */
    public static int maxRunLength(int[] a) {
        if (a == null || a.length == 0) {
            return 0;
        }
        int best = 1;
        int cur = 1;
        for (int i = 1; i < a.length; i++) {
            if (a[i] == a[i - 1]) {
                cur++;
            } else {
                cur = 1;
            }
            if (cur > best) {
                best = cur;
            }
        }
        return best;
    }
}
