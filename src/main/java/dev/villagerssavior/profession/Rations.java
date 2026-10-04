package dev.villagerssavior.profession;

/** Pure math for the farmer's travel rations. */
public final class Rations {
    /** Below this reputation a farmer will not prepare rations at all. */
    public static final int MIN_REPUTATION = 25;
    /** At most this much nutrition per handout. */
    public static final int MAX_BUDGET = 8;
    /** One in-game day: the same player cannot collect rations twice from one village per day. */
    public static final long COOLDOWN = 24000L;
    private Rations() {}
    /**
     * P = min(E, floor(E * clamp(0.10 + 0.002 * R, 0, 0.30)), 8).
     *
     * <p>Exact integer form: share = clamp(100 + 2R, 0, 300) and P = min(E, floor(E * share / 1000), 8),
     * which gives 15% at R = 25, 20% at R = 50, 25% at R = 75 and 30% at R >= 100.
     */
    public static int budget(long surplus, int reputation) {
        if (reputation < MIN_REPUTATION || surplus <= 0) return 0;
        long share = Math.clamp(100L + 2L * Math.min(reputation, 100), 0L, 300L);
        long offered = surplus * share / 1000;
        return (int) Math.max(0L, Math.min(Math.min(surplus, offered), MAX_BUDGET));
    }
}
