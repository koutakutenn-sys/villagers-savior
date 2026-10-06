package dev.villagerssavior.test;

import com.mojang.authlib.GameProfile;
import dev.villagerssavior.NitwitRelay;
import dev.villagerssavior.SaviorGossip;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Nitwit standing and the nitwit gossip relay: a nitwit never reports a reputation of its own although its
 * gossip stays stored, and a successful chat passes one good word about a liked player to the other villager,
 * bounded per player, receiver and in-game day.
 */
public final class NitwitRelayChecks {
    private NitwitRelayChecks() {}
    public static int run() {
        int checks = 0;
        var gossips = new GossipContainer();
        UUID liked = UUID.randomUUID(), disliked = UUID.randomUUID(), stranger = UUID.randomUUID();
        gossips.add(liked, GossipType.MINOR_POSITIVE, 4);
        gossips.add(disliked, GossipType.MINOR_NEGATIVE, 4);
        checks += require(NitwitRelay.positivePlayers(gossips).equals(List.of(liked)),
            "only a positive impression becomes a relay candidate");
        checks += require(!NitwitRelay.positivePlayers(gossips).contains(stranger), "a stranger is not a candidate");
        checks += require(NitwitRelay.pick(gossips, RandomSource.create(11L)).orElseThrow().equals(liked),
            "the relay picks the liked player");
        checks += require(NitwitRelay.pick(new GossipContainer(), RandomSource.create(11L)).isEmpty(),
            "a nitwit that likes nobody relays nothing");
        checks += require(NitwitRelay.positivePlayers(new GossipContainer()).isEmpty(), "zero is not a positive impression");
        var tradingOnly = new GossipContainer();
        tradingOnly.add(liked, GossipType.TRADING, 10);
        checks += require(NitwitRelay.positivePlayers(tradingOnly).isEmpty(), "trading alone never makes a player liked");
        var minorOnly = new GossipContainer();
        minorOnly.add(liked, GossipType.MINOR_POSITIVE, 3);
        checks += require(NitwitRelay.positivePlayers(minorOnly).equals(List.of(liked)),
            "a minor positive impression is enough to be relayed");
        var negativeOnly = new GossipContainer();
        negativeOnly.add(liked, GossipType.MAJOR_NEGATIVE, 4);
        checks += require(NitwitRelay.positivePlayers(negativeOnly).isEmpty(), "a negative impression is never relayed");
        checks += require(NitwitRelay.DAILY_LIMIT == 3, "the relay daily limit is three");
        return checks;
    }
    private static int require(boolean ok, String name) {
        if (!ok) throw new AssertionError("nitwit relay: " + name);
        return 1;
    }
    public static void run(MinecraftServer server, ServerLevel level, BiConsumer<Boolean, String> check) {
        Villager nitwit = villager(level, VillagerProfession.NITWIT, 8.5, 12.5);
        Villager receiver = villager(level, VillagerProfession.FARMER, 10.5, 12.5);
        Villager bystander = villager(level, VillagerProfession.BUTCHER, 12.5, 12.5);
        Villager otherNitwit = villager(level, VillagerProfession.NITWIT, 14.5, 12.5);
        UUID liked = UUID.randomUUID(), disliked = UUID.randomUUID();
        SaviorGossip.add(nitwit, liked, GossipType.MAJOR_POSITIVE, 4);
        SaviorGossip.add(nitwit, disliked, GossipType.MINOR_NEGATIVE, 4);
        ServerPlayer friend = player(server, level, liked);
        check.accept(nitwit.getPlayerReputation(friend) == 0, "a nitwit never reports a reputation of its own");
        check.accept(nitwit.getGossips().getReputation(liked, type -> true) == 20,
            "the nitwit's real gossip is still stored underneath");
        check.accept(receiver.getPlayerReputation(friend) == 0, "an ordinary villager still reports its own reputation");
        // A fixed synthetic day keeps the daily ledger away from any real day boundary.
        long day = 24_000L * 1_000L;
        nitwit.gossip(level, receiver, day);
        check.accept(minor(receiver, liked) == 1, "a successful chat passes one good word on");
        nitwit.gossip(level, receiver, day + 1_300);
        nitwit.gossip(level, receiver, day + 2_600);
        check.accept(minor(receiver, liked) == NitwitRelay.DAILY_LIMIT, "the relay reaches its daily limit");
        nitwit.gossip(level, receiver, day + 3_900);
        check.accept(minor(receiver, liked) == NitwitRelay.DAILY_LIMIT, "the same day never exceeds the daily limit");
        check.accept(minor(receiver, disliked) == 0, "a disliked player is never relayed");
        // The relay works in either direction: here the ordinary villager starts the chat.
        receiver.gossip(level, nitwit, day + 24_000);
        check.accept(minor(receiver, liked) == NitwitRelay.DAILY_LIMIT + 1,
            "a new day relays again, from either chat direction");
        // Negative controls: no relay unless exactly one of the two is a nitwit.
        int before = minor(bystander, liked);
        receiver.gossip(level, bystander, day + 24_000);
        check.accept(minor(bystander, liked) == before, "two ordinary villagers relay nothing");
        otherNitwit.gossip(level, nitwit, day + 24_000);
        check.accept(minor(nitwit, liked) == 0, "two nitwits relay nothing");
        nitwit.discard();
        receiver.discard();
        bystander.discard();
        otherNitwit.discard();
    }
    private static int minor(Villager villager, UUID player) {
        var entries = villager.getGossips().getGossipEntries().get(player);
        return entries == null ? 0 : entries.getInt(GossipType.MINOR_POSITIVE);
    }
    private static ServerPlayer player(MinecraftServer server, ServerLevel level, UUID uuid) {
        var profile = new GameProfile(uuid, "NitwitFriend");
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
            CommonListenerCookie.createInitial(profile, false));
        player.snapTo(0.5, 4, 0.5);
        return player;
    }
    private static Villager villager(ServerLevel level, ResourceKey<VillagerProfession> profession, double x, double z) {
        level.getChunk(((int) x) >> 4, ((int) z) >> 4);
        Villager villager = new Villager(EntityTypes.VILLAGER, level);
        villager.snapTo(x, 4, z);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), profession));
        level.addFreshEntity(villager);
        return villager;
    }
}
