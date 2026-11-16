package pipeline;

import java.math.BigInteger;
import java.util.*;

/** Exact tie-aware Wilcoxon, bootstrap CI, Vargha-Delaney A12, Holm, Cohen's kappa. */
final class Stats {
    private Stats() {}

    record Wilcoxon(double p, int nNonzero) {}

    record Summary(String metric, double meanA, double meanB, double delta, double ciLo, double ciHi,
                   double sd, double median, double iqr, int nPos, int nZero, int nNeg, int nEffective,
                   double p, double a12, String unit) {}

    static double round9(double x) { return Math.rint(x * 1e9) / 1e9; }

    static double mean(double[] v) {
        double s = 0;
        for (double x : v) s += x;
        return v.length == 0 ? Double.NaN : s / v.length;
    }

    /** Average ranks (ties share the mean rank), like scipy.stats.rankdata. */
    static double[] rankAverage(double[] v) {
        int n = v.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> v[i]));
        double[] ranks = new double[n];
        for (int a = 0; a < n; ) {
            int b = a;
            while (b + 1 < n && v[idx[b + 1]] == v[idx[a]]) b++;
            double r = (a + b) / 2.0 + 1.0;
            for (int k = a; k <= b; k++) ranks[idx[k]] = r;
            a = b + 1;
        }
        return ranks;
    }

    /** Exact two-sided Wilcoxon signed-rank p-value, zeros dropped, ties by average ranks. */
    static Wilcoxon exactWilcoxon(double[] dIn) {
        List<Double> nz = new ArrayList<>();
        for (double x : dIn) {
            double r = round9(x);
            if (r != 0) nz.add(r);
        }
        int n = nz.size();
        if (n == 0) return new Wilcoxon(1.0, 0);
        double[] d = new double[n], abs = new double[n];
        for (int i = 0; i < n; i++) { d[i] = nz.get(i); abs[i] = Math.abs(d[i]); }
        double[] rk = rankAverage(abs);
        int[] r2 = new int[n]; // doubled average ranks are exact integers
        int total = 0;
        for (int i = 0; i < n; i++) { r2[i] = (int) Math.rint(2 * rk[i]); total += r2[i]; }
        BigInteger[] counts = new BigInteger[total + 1];
        Arrays.fill(counts, BigInteger.ZERO);
        counts[0] = BigInteger.ONE;
        for (int r : r2)
            for (int s = total; s >= r; s--) counts[s] = counts[s].add(counts[s - r]);
        int wObs = 0;
        for (int i = 0; i < n; i++) if (d[i] > 0) wObs += r2[i];
        int dev = Math.abs(2 * wObs - total);
        BigInteger hits = BigInteger.ZERO;
        for (int s = 0; s <= total; s++) if (Math.abs(2 * s - total) >= dev) hits = hits.add(counts[s]);
        return new Wilcoxon(Math.min(1.0, hits.doubleValue() / Math.pow(2, n)), n);
    }

    /** P(B > A) + 0.5 P(B = A) over all (B, A) pairs. */
    static double a12(double[] xa, double[] xb) {
        long gt = 0, eq = 0;
        for (double b : xb) for (double a : xa) { if (b > a) gt++; else if (b == a) eq++; }
        double pairs = (double) xa.length * xb.length;
        return gt / pairs + 0.5 * (eq / pairs);
    }

    /** Linear-interpolation percentile (numpy default). */
    static double percentile(double[] values, double q) {
        double[] v = values.clone();
        Arrays.sort(v);
        if (v.length == 0) return Double.NaN;
        double pos = q / 100.0 * (v.length - 1);
        int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
        return v[lo] + (v[hi] - v[lo]) * (pos - lo);
    }

    static double median(double[] v) { return percentile(v, 50); }

    /** 95% percentile bootstrap CI of the mean. */
    static double[] bootCi(double[] d, Random rng, int b) {
        int n = d.length;
        double[] means = new double[b];
        for (int k = 0; k < b; k++) {
            double s = 0;
            for (int j = 0; j < n; j++) s += d[rng.nextInt(n)];
            means[k] = s / n;
        }
        return new double[]{percentile(means, 2.5), percentile(means, 97.5)};
    }

    /** Holm step-down adjusted p-values (same order as input). */
    static double[] holm(double[] p) {
        int m = p.length;
        Integer[] order = new Integer[m];
        for (int i = 0; i < m; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> p[i]));
        double[] adj = new double[m];
        double run = 0.0;
        for (int rank = 0; rank < m; rank++) {
            int i = order[rank];
            run = Math.max(run, (m - rank) * p[i]);
            adj[i] = Math.min(1.0, run);
        }
        return adj;
    }

    static double cohenKappa(List<String> r1, List<String> r2) {
        Set<String> cats = new TreeSet<>(r1);
        cats.addAll(r2);
        int n = r1.size();
        double po = 0;
        for (int i = 0; i < n; i++) if (r1.get(i).equals(r2.get(i))) po++;
        po /= n;
        double pe = 0;
        for (String c : cats)
            pe += (Collections.frequency(r1, c) / (double) n) * (Collections.frequency(r2, c) / (double) n);
        return pe < 1 ? (po - pe) / (1 - pe) : 1.0;
    }

    static Summary summarize(String name, double[] xa, double[] xb, Random rng, String unit, int nBoot) {
        int n = xa.length;
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = xb[i] - xa[i];
        Wilcoxon w = exactWilcoxon(d);
        double[] ci = bootCi(d, rng, nBoot);
        double dm = mean(d), sd = 0;
        if (n > 1) {
            double ss = 0;
            for (double x : d) ss += (x - dm) * (x - dm);
            sd = Math.sqrt(ss / (n - 1));
        }
        int pos = 0, zero = 0, neg = 0;
        for (double x : d) {
            double r = round9(x);
            if (r > 0) pos++; else if (r == 0) zero++; else neg++;
        }
        return new Summary(name, mean(xa), mean(xb), dm, ci[0], ci[1], sd, median(d),
                percentile(d, 75) - percentile(d, 25), pos, zero, neg, w.nNonzero(), w.p(), a12(xa, xb), unit);
    }
}
