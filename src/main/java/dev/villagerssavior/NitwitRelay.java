package dev.villagerssavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Nitwits have no standing of their own: their gossip container keeps every entry exactly as vanilla stores
 * it, but the reputations read back out of a nitwit are always zero (see the {@code getPlayerReputation}
 * mixin), so trading, the HUD and iron-golem checks can never see a nitwit as a reputation holder.
 *
 * <p>What a nitwit does keep is its opinion. A successful villager chat ({@code Villager#gossip}, which
 * vanilla already rate-limits to one exchange per 1200 ticks) lets a nitwit pass one good word about a
 * player it likes on to the other, ordinary villager as MINOR_POSITIVE +1, capped per player and receiver
 * and in-game day so it can never be farmed.
 */
public final class NitwitRelay {
    /** Most a player can gain from nitwit relays per receiving villager and in-game day. */
    public static final int DAILY_LIMIT = 3;
    /** Shared daily-ledger event name. */
    public static final String EVENT = "nitwit_relay";
    private NitwitRelay() {}
    /**
     * Types that express a personal liking. TRADING is deliberately excluded: gossip spreads between
     * villagers, so counting it would let a nitwit advertise whoever merely shops a lot.
     */
    public static boolean liking(GossipType type) {
        return type == GossipType.MINOR_POSITIVE || type == GossipType.MAJOR_POSITIVE;
    }
    /**
     * Players this gossip container holds a positive personal impression of, in a stable order. This reads
     * the container directly on purpose: {@code getPlayerReputation} always reports zero for a nitwit.
     */
    public static List<UUID> positivePlayers(GossipContainer gossips) {
        List<UUID> players = new ArrayList<>();
        for (UUID player : gossips.getGossipEntries().keySet()) {
            if (gossips.getReputation(player, NitwitRelay::liking) > 0) players.add(player);
        }
        return players;
    }
    /** Uniformly picks one liked player, or nothing when the nitwit likes nobody. */
    public static Optional<UUID> pick(GossipContainer gossips, RandomSource random) {
        List<UUID> players = positivePlayers(gossips);
        return players.isEmpty() ? Optional.empty() : Optional.of(players.get(random.nextInt(players.size())));
    }
    /**
     * Runs at the tail of a successful vanilla villager chat. When exactly one of the two villagers is a
     * nitwit, one random player it likes gains MINOR_POSITIVE +1 with the other, ordinary villager.
     */
    public static void onGossip(ServerLevel level, Villager first, Villager second, long now) {
        Villager nitwit;
        Villager receiver;
        if (NitwitInventory.isNitwit(first) && !NitwitInventory.isNitwit(second)) {
            nitwit = first; receiver = second;
        } else if (NitwitInventory.isNitwit(second) && !NitwitInventory.isNitwit(first)) {
            nitwit = second; receiver = first;
        } else {
            return; // no nitwit, or two of them: nothing to relay
        }
        UUID player = pick(nitwit.getGossips(), level.getRandom()).orElse(null);
        if (player == null) return;
        int granted = SaviorState.get(level).dailyGrant(EVENT, player, receiver.getUUID(), now, 1, DAILY_LIMIT);
        if (granted <= 0) return;
        SaviorGossip.add(receiver, player, GossipType.MINOR_POSITIVE, granted);
    }
}
