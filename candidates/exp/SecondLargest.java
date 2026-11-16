package exp;

public final class SecondLargest {
    private SecondLargest() {}

    /**
     * Returns the second largest DISTINCT value in a, or null if a is null or has fewer
     * than two distinct values. Example: {5, 5, 3} -> 3; {7, 7} -> null; {1, 9, 4} -> 4.
     */
    public static Integer secondLargest(int[] a) {
        if (a == null) {
            return null;
        }
        Integer first = null;
        Integer second = null;
        for (int v : a) {
            if (first == null || v > first) {
                if (first != null) {
                    second = first;
                }
                first = v;
            } else if (v < first && (second == null || v > second)) {
                second = v;
            }
        }
        return second;
    }
}
