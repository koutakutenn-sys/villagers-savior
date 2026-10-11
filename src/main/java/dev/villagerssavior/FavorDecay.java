package dev.villagerssavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.entity.EntityTypeTest;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Vanilla's gossip decay, with one change: every type keeps its own per-day decay amount
 * ({@link GossipType#decayPerDay}), except MINOR_POSITIVE, which only decays every third in-game day.
 * The daily pass also trims a MINOR_POSITIVE entry that grew past {@link #CEILING} back to the ceiling.
 */
public final class FavorDecay {
    public static final long DAY = 24000L;
    /** MINOR_POSITIVE decays once every this many in-game days instead of every day. */
    public static final int MINOR_PERIOD_DAYS = 3;
    /** The ceiling MINOR_POSITIVE is trimmed back to each day. */
    public static final int CEILING = 25;

    private FavorDecay() {}

    /** True once per in-game day. */
    public static boolean due(long gameTime) {
        return gameTime > 0 && gameTime % DAY == 0;
    }

    /** The in-game day number a tick belongs to. */
    public static long day(long gameTime) {
        return Math.floorDiv(gameTime, DAY);
    }

    /** The daily pass over every loaded villager. Returns how many entries changed. */
    public static int apply(ServerLevel level) {
        int changed = 0;
        for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class), villager -> true))
            changed += daily(level, villager);
        return changed;
    }

    public static int daily(ServerLevel level, Villager villager) {
        return daily(level, villager, level.getGameTime());
    }

    /** @return how many gossip entries were trimmed or decayed */
    public static int daily(ServerLevel level, Villager villager, long now) {
        var gossips = villager.getGossips();
        boolean minorDay = day(now) % MINOR_PERIOD_DAYS == 0;
        int changed = 0;
        for (UUID player : new ArrayList<>(gossips.getGossipEntries().keySet())) {
            int minor = SaviorGossip.count(villager, player, GossipType.MINOR_POSITIVE);
            if (minor > CEILING) {
                SaviorGossip.setCount(villager, player, GossipType.MINOR_POSITIVE, CEILING);
                changed++;
                minor = CEILING;
            }
            for (GossipType type : GossipType.values()) {
                if (type == GossipType.MINOR_POSITIVE && !minorDay) continue;
                int decay = type.decayPerDay;
                if (decay <= 0) continue;
                int value = type == GossipType.MINOR_POSITIVE ? minor : SaviorGossip.count(villager, player, type);
                if (value <= 0) continue;
                int remaining = value - decay;
                if (remaining <= 0) gossips.remove(player, type);
                else SaviorGossip.setCount(villager, player, type, remaining);
                changed++;
            }
        }
        return changed;
    }
}
