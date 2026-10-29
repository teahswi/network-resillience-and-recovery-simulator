package netrescue.ds;

import java.util.Comparator;

/**
 * Two classic comparison sorts.
 *
 * Merge sort (von Neumann, 1945): split in half, sort each half, merge.
 *   T(n) = 2T(n/2) + O(n)  =>  O(n log n) always, stable, needs O(n) extra space.
 *
 * Quicksort (Hoare, 1961): pick a pivot, partition into < and >, recurse.
 *   O(n log n) expected, O(n^2) worst case, in place, not stable.
 *   We use a random-ish median-of-three pivot to make the worst case unlikely.
 *
 * Both count comparisons so the benchmark tab can show them.
 */
public final class Sorting {
    private Sorting() {}

    /** Number of comparisons done by the most recent sort call (for benchmarks). */
    public static long lastComparisons;

    // ------------------------------------------------------------------ merge sort
    public static <T> void mergeSort(DynamicArray<T> a, Comparator<? super T> cmp) {
        lastComparisons = 0;
        int n = a.size();
        if (n < 2) return;
        Object[] tmp = new Object[n];
        mergeSort(a, tmp, 0, n - 1, cmp);
    }

    @SuppressWarnings("unchecked")
    private static <T> void mergeSort(DynamicArray<T> a, Object[] tmp, int lo, int hi, Comparator<? super T> cmp) {
        if (lo >= hi) return;
        int mid = (lo + hi) >>> 1;
        mergeSort(a, tmp, lo, mid, cmp);
        mergeSort(a, tmp, mid + 1, hi, cmp);
        // merge a[lo..mid] and a[mid+1..hi]
        for (int k = lo; k <= hi; k++) tmp[k] = a.get(k);
        int i = lo, j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if (i > mid) a.set(k, (T) tmp[j++]);
            else if (j > hi) a.set(k, (T) tmp[i++]);
            else {
                lastComparisons++;
                // "<=" keeps equal elements in original order => stable
                if (cmp.compare((T) tmp[i], (T) tmp[j]) <= 0) a.set(k, (T) tmp[i++]);
                else a.set(k, (T) tmp[j++]);
            }
        }
    }

    // ------------------------------------------------------------------ quicksort
    public static <T> void quickSort(DynamicArray<T> a, Comparator<? super T> cmp) {
        lastComparisons = 0;
        quickSort(a, 0, a.size() - 1, cmp);
    }

    private static <T> void quickSort(DynamicArray<T> a, int lo, int hi, Comparator<? super T> cmp) {
        while (lo < hi) {
            if (hi - lo < 12) { insertionSort(a, lo, hi, cmp); return; }
            int p = partition(a, lo, hi, cmp);
            // recurse into the smaller side, loop on the larger: keeps stack depth O(log n)
            if (p - lo < hi - p) { quickSort(a, lo, p - 1, cmp); lo = p + 1; }
            else { quickSort(a, p + 1, hi, cmp); hi = p - 1; }
        }
    }

    private static <T> int partition(DynamicArray<T> a, int lo, int hi, Comparator<? super T> cmp) {
        int mid = (lo + hi) >>> 1;
        // median of three -> move pivot to a[hi]
        if (less(a, mid, lo, cmp)) a.swap(mid, lo);
        if (less(a, hi, lo, cmp)) a.swap(hi, lo);
        if (less(a, mid, hi, cmp)) a.swap(mid, hi);
        T pivot = a.get(hi);
        int i = lo;
        for (int j = lo; j < hi; j++) {
            lastComparisons++;
            if (cmp.compare(a.get(j), pivot) < 0) a.swap(i++, j);
        }
        a.swap(i, hi);
        return i;
    }

    private static <T> void insertionSort(DynamicArray<T> a, int lo, int hi, Comparator<? super T> cmp) {
        for (int i = lo + 1; i <= hi; i++)
            for (int j = i; j > lo && less(a, j, j - 1, cmp); j--) a.swap(j, j - 1);
    }

    private static <T> boolean less(DynamicArray<T> a, int i, int j, Comparator<? super T> cmp) {
        lastComparisons++;
        return cmp.compare(a.get(i), a.get(j)) < 0;
    }
}
