package exp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DedupKeepOrder {
    private DedupKeepOrder() {}

    /**
     * Returns a new list with duplicates removed, keeping the first occurrence of each
     * value in its original position. The input list is not modified.
     * A null input yields an empty list.
     */
    public static List<Integer> dedup(List<Integer> in) {
        List<Integer> out = new ArrayList<>();
        if (in == null) {
            return out;
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer v : in) {
            if (seen.add(v)) {
                out.add(v);
            }
        }
        return out;
    }
}
