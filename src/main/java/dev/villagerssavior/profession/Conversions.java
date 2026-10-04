package dev.villagerssavior.profession;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import java.util.Map;

/** Vanilla-conserving material conversions: the payer always pays the full vanilla cost per batch. */
public final class Conversions {
    private Conversions() {}
    /** How many whole batches {@code container} can pay for, bounded by {@code cap}. */
    public static int affordable(Container container, Map<Item, Integer> costPerBatch, int cap) {
        int batches = Math.max(0, cap);
        for (var entry : costPerBatch.entrySet())
            batches = Math.min(batches, ServiceItems.count(container, entry.getKey()) / entry.getValue());
        return Math.max(0, batches);
    }
    /** Pays for {@code batches}; call only after {@link #affordable} confirmed the cost is affordable. */
    public static boolean pay(Container container, Map<Item, Integer> costPerBatch, int batches) {
        for (var entry : costPerBatch.entrySet())
            if (!ServiceItems.consume(container, entry.getKey(), entry.getValue() * batches)) return false;
        return true;
    }
    /** Batch cap scaled by reputation: trusted players may process more in a single visit. */
    public static int capFor(int reputation) { return reputation >= 75 ? 8 : 4; }
}
