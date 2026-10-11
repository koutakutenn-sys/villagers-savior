package dev.villagerssavior;

import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.UUID;

/** Writes the mod's own reputation values into the villager's real gossip container. */
public final class SaviorGossip {
    private SaviorGossip() {}

    /** Raw stored count for one type. Vanilla clamps on write, so a read can show values past its ceiling. */
    public static int count(Villager villager, UUID player, GossipType type) {
        var entries = villager.getGossips().getGossipEntries().get(player);
        return entries == null ? 0 : Math.max(0, entries.getInt(type));
    }

    /** Writes one type's raw count directly; MINOR_POSITIVE is deliberately allowed past its vanilla ceiling. */
    public static void setCount(Villager villager, UUID player, GossipType type, int value) {
        var gossip = villager.getGossips();
        var entries = gossip.getGossipEntries().get(player);
        if (entries == null) {
            if (value <= 0) return;
            gossip.add(player, type, Math.max(2, value));
            entries = gossip.getGossipEntries().get(player);
            if (entries == null) return;
        }
        entries.put(type, Math.max(0, value));
    }

    public static void add(Villager villager, UUID player, GossipType type, int delta) {
        if (delta <= 0) return;
        var gossip = villager.getGossips();
        var entries = gossip.getGossipEntries().get(player);
        int old = entries == null ? 0 : entries.getInt(type);
        // Vanilla discards new values below 2. Preserve the design's +1 events as real gossip.
        gossip.add(player, type, Math.max(2, delta));
        entries = gossip.getGossipEntries().get(player);
        if (entries == null) return;
        // MINOR_POSITIVE may grow past the vanilla ceiling during a day; the daily pass trims it back to 25.
        entries.put(type, type == GossipType.MINOR_POSITIVE ? old + delta : Math.min(type.max, old + delta));
    }

    /**
     * Rewrites this player's opinion of the villager to exactly {@code total}, so a total can move by a single
     * point even though vanilla stores it as weighted types (a major entry is worth five).
     */
    public static void setTotal(Villager villager, UUID player, int total) {
        var gossips = villager.getGossips();
        for (GossipType type : GossipType.values()) gossips.remove(player, type);
        int magnitude = Math.abs(total);
        boolean positive = total >= 0;
        if (magnitude / 5 > 0)
            add(villager, player, positive ? GossipType.MAJOR_POSITIVE : GossipType.MAJOR_NEGATIVE, magnitude / 5);
        if (magnitude % 5 > 0)
            add(villager, player, positive ? GossipType.MINOR_POSITIVE : GossipType.MINOR_NEGATIVE, magnitude % 5);
    }

    public static void subtract(Villager villager, UUID player, GossipType type, int delta) {
        var gossip = villager.getGossips();
        var entries = gossip.getGossipEntries().get(player);
        int old = entries == null ? 0 : entries.getInt(type);
        int remaining = Math.max(0, old - delta);
        gossip.remove(player, type);
        if (remaining > 0) add(villager, player, type, remaining);
    }
}
