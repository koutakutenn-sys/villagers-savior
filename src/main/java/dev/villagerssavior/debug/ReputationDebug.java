package dev.villagerssavior.debug;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.List;

/**
 * Reads the real vanilla reputation of a villager and formats it for the debug view. Everything here is
 * read-only: no gossip entry is created, changed or removed.
 */
public final class ReputationDebug {
    private ReputationDebug() {}
    /** Server-side snapshot; the numbers are exactly what vanilla would report. */
    public static ReputationReport inspect(Villager villager, ServerPlayer player) {
        Object2IntMap<GossipType> entries = villager.getGossips().getGossipEntries().get(player.getUUID());
        return new ReputationReport(
            villager.getId(),
            villager.getUUID().toString(),
            profession(villager),
            villager.getVillagerData().level(),
            villager.getPlayerReputation(player),
            value(entries, GossipType.MINOR_POSITIVE),
            value(entries, GossipType.MAJOR_POSITIVE),
            value(entries, GossipType.TRADING),
            value(entries, GossipType.MINOR_NEGATIVE),
            value(entries, GossipType.MAJOR_NEGATIVE));
    }
    static int value(Object2IntMap<GossipType> entries, GossipType type) {
        return entries == null ? 0 : entries.getInt(type);
    }
    /** Profession path, e.g. {@code farmer}; unemployed and nitwit villagers report {@code none}. */
    public static String profession(Villager villager) {
        return villager.getVillagerData().profession().unwrapKey()
            .map(key -> key.identifier().getPath())
            .orElse("none");
    }
    /** Client-side presentation of a server report; purely textual so it can be asserted in tests. */
    public static List<String> lines(ReputationReport report) {
        return List.of(
            "[Villagers' Savior] villager " + report.villagerUuid() + " (" + report.profession()
                + ", level " + report.level() + ")",
            "  total reputation: " + report.total(),
            "  MINOR_POSITIVE: " + report.minorPositive(),
            "  MAJOR_POSITIVE: " + report.majorPositive(),
            "  TRADING: " + report.trading(),
            "  MINOR_NEGATIVE: " + report.minorNegative(),
            "  MAJOR_NEGATIVE: " + report.majorNegative());
    }
}
