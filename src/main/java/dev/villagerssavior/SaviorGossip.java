package dev.villagerssavior;

import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.UUID;

public final class SaviorGossip {
    private SaviorGossip() {}
    public static void add(Villager villager, UUID player, GossipType type, int delta) {
        if (delta <= 0) return;
        var gossip = villager.getGossips();
        var entries = gossip.getGossipEntries().get(player);
        int old = entries == null ? 0 : entries.getInt(type);
        // Vanilla discards new values below 2. Preserve the design's +1 events as real gossip.
        gossip.add(player, type, Math.max(2, delta));
        entries = gossip.getGossipEntries().get(player);
        if (entries != null) entries.put(type, Math.min(type.max, old + delta));
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
