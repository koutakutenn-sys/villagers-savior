package dev.villagerssavior.debug;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.ai.gossip.GossipType;

/**
 * Pure gossip arithmetic behind the operator {@code /villagerssavior debug favor} command.
 *
 * <p>It only ever produces real vanilla gossip entries, so the mod keeps a single source of truth for a
 * villager's opinion of a player and no parallel reputation is introduced. What is representable is decided
 * by vanilla weights and caps; the paired major + minor entries below reproduce every value inside
 * {@link #maxNegative()}..{@link #maxPositive()} exactly, and anything larger is clamped to the cap so the
 * command can report the value that was really reached.
 */
public final class FavorGossip {
    /** Default radius of the bulk edit: a village-sized area around the caller. */
    public static final int DEFAULT_RADIUS = 64;
    /**
     * Distance limit. This is the world-border radius, i.e. effectively unbounded: the command is not capped
     * by the smaller scan radius. Only villagers in loaded chunks exist as entities and can be edited.
     */
    public static final int MAX_RADIUS = 30_000_000;
    /** One gossip entry: a vanilla type plus the value to store for it (clamped by that type's own cap). */
    public record Entry(GossipType type, int value) {}
    private FavorGossip() {}
    /** The largest positive reputation vanilla gossip can hold. */
    public static int maxPositive() {
        return GossipType.MAJOR_POSITIVE.max * Math.abs(GossipType.MAJOR_POSITIVE.weight)
            + GossipType.MINOR_POSITIVE.max * Math.abs(GossipType.MINOR_POSITIVE.weight);
    }
    /** The largest negative reputation vanilla gossip can hold (a negative number). */
    public static int maxNegative() {
        return -(GossipType.MAJOR_NEGATIVE.max * Math.abs(GossipType.MAJOR_NEGATIVE.weight)
            + GossipType.MINOR_NEGATIVE.max * Math.abs(GossipType.MINOR_NEGATIVE.weight));
    }
    /** Clamps a requested reputation into the range vanilla gossip can actually express. */
    public static int clamp(int total) {
        return Math.max(maxNegative(), Math.min(maxPositive(), total));
    }
    /** Entries worth exactly {@link #clamp(int)}: one major entry plus one minor entry of the same sign. */
    public static List<Entry> entriesFor(int total) {
        int clamped = clamp(total);
        if (clamped == 0) return List.of();
        boolean positive = clamped > 0;
        GossipType major = positive ? GossipType.MAJOR_POSITIVE : GossipType.MAJOR_NEGATIVE;
        GossipType minor = positive ? GossipType.MINOR_POSITIVE : GossipType.MINOR_NEGATIVE;
        int magnitude = Math.abs(clamped);
        int majorUnit = Math.abs(major.weight);
        int majorValue = Math.min(magnitude / majorUnit, major.max);
        int remainder = magnitude - majorValue * majorUnit;
        int minorValue = Math.min(remainder / Math.abs(minor.weight), minor.max);
        List<Entry> entries = new ArrayList<>(2);
        if (majorValue > 0) entries.add(new Entry(major, majorValue));
        if (minorValue > 0) entries.add(new Entry(minor, minorValue));
        return List.copyOf(entries);
    }
    /** What a set of entries is worth under vanilla weights. */
    public static int reputationOf(List<Entry> entries) {
        int total = 0;
        for (Entry entry : entries) total += entry.value() * entry.type().weight;
        return total;
    }
}
