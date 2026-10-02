package dev.villagerssavior;

/** Pure bounded-knapsack rules. Counts are per real inventory slot, preserving stack components. */
public final class FoodRules {
    private FoodRules() {}
    public static int budget(int hunger, int reputation, long nutrition) {
        int missing = 20 - Math.clamp(hunger, 0, 20);
        if (missing == 0) return 0;
        int r = Math.min(reputation, 100);
        long excess = Math.max(0L, nutrition - 20);
        long factor = Math.clamp(100L + r, 0, 200);
        // Exact integer forms of floor(E * W_R) and ceil(N * (0.25 + 0.003 * R_plus)).
        long offered = (excess / 400) * factor + (excess % 400) * factor / 400;
        int need = Math.clamp((missing * (250 + 3 * Math.clamp(r, 0, 100)) + 999) / 1000, 2, 8);
        return (int) Math.max(0, Math.min(need, Math.min(excess, offered)));
    }
    public static int[] select(int[] nutrition, int[] counts, int budget) {
        if (nutrition.length != counts.length || budget < 0 || budget > 8) throw new IllegalArgumentException();
        int[][] solutions = new int[budget + 1][];
        solutions[0] = new int[counts.length];
        for (int slot = 0; slot < counts.length; slot++) {
            int h = nutrition[slot];
            if (h <= 0) continue;
            int[][] next = solutions.clone();
            for (int sum = 0; sum <= budget; sum++) {
                if (solutions[sum] == null) continue;
                for (int n = 1; n <= Math.min(counts[slot], (budget - sum) / h); n++) {
                    int target = sum + n * h;
                    if (next[target] == null) {
                        next[target] = solutions[sum].clone();
                        next[target][slot] = n;
                    }
                }
            }
            solutions = next;
        }
        for (int sum = budget; sum >= 0; sum--) if (solutions[sum] != null) return solutions[sum];
        throw new AssertionError();
    }
    public static int emergencySlot(int[] nutrition, int[] counts) {
        int best = -1;
        for (int i = 0; i < counts.length; i++)
            if (counts[i] > 0 && nutrition[i] >= 0 && (best < 0 || nutrition[i] < nutrition[best])) best = i;
        return best;
    }
}
