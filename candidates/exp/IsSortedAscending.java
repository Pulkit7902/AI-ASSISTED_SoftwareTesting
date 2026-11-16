package exp;

public final class IsSortedAscending {
    private IsSortedAscending() {}

    /**
     * Returns true if each element is greater than or equal to its predecessor
     * (non-decreasing order). Null, empty, and single-element arrays are sorted.
     */
    public static boolean isSortedAscending(int[] a) {
        if (a == null) {
            return true;
        }
        for (int i = 1; i < a.length; i++) {
            if (a[i] < a[i - 1]) {
                return false;
            }
        }
        return true;
    }
}
