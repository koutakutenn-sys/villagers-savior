package dev.villagerssavior.test;

import com.mojang.authlib.GameProfile;
import dev.villagerssavior.debug.FavorGossip;
import dev.villagerssavior.debug.VillagerInspection;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The operator {@code /villagerssavior debug favor} command: pure gossip arithmetic plus a real command run
 * against villagers near and far, so a distance cap or a parallel reputation store would be caught.
 */
public final class FavorChecks {
    private FavorChecks() {}
    public static int run() {
        int checks = 0;
        checks += require(FavorGossip.entriesFor(0).isEmpty(), "zero favor needs no gossip entry");
        checks += require(FavorGossip.reputationOf(FavorGossip.entriesFor(30)) == 30, "thirty is representable exactly");
        checks += require(FavorGossip.reputationOf(FavorGossip.entriesFor(33)) == 33, "non-multiples of five are exact");
        checks += require(FavorGossip.reputationOf(FavorGossip.entriesFor(-7)) == -7, "negative values are exact");
        checks += require(FavorGossip.reputationOf(FavorGossip.entriesFor(FavorGossip.maxPositive())) == FavorGossip.maxPositive(),
            "the positive gossip cap is reachable");
        checks += require(FavorGossip.reputationOf(FavorGossip.entriesFor(FavorGossip.maxNegative())) == FavorGossip.maxNegative(),
            "the negative gossip cap is reachable");
        checks += require(FavorGossip.reputationOf(FavorGossip.entriesFor(100_000)) == FavorGossip.maxPositive(),
            "requests above the cap clamp to the cap");
        checks += require(FavorGossip.reputationOf(FavorGossip.entriesFor(-100_000)) == FavorGossip.maxNegative(),
            "requests below the cap clamp to the cap");
        checks += require(FavorGossip.entriesFor(60).stream().allMatch(entry -> entry.type().weight > 0),
            "positive favor only uses positive gossip");
        checks += require(FavorGossip.entriesFor(-60).stream().allMatch(entry -> entry.type().weight < 0),
            "negative favor only uses negative gossip");
        checks += require(FavorGossip.entriesFor(60).stream().noneMatch(entry -> entry.value() > entry.type().max),
            "no entry exceeds its own vanilla cap");
        checks += require(FavorGossip.DEFAULT_RADIUS == 64, "the default radius stays village sized");
        // The localized command feedback must keep value, count and radius in their own slots: the Chinese
        // strings once swapped the requested value with the radius, which only a rendered check can catch.
        String zhSet = render("zh_cn", "debug.villagers_savior.favor_set", 60, 4, 8, 60, 60, 2000);
        checks += require(zhSet.contains("设为 60") && zhSet.contains("4 名村民") && zhSet.contains("8 格内")
            && zhSet.contains("请求值 2000"), "the Chinese favor set reply keeps every value in its own slot");
        String enSet = render("en_us", "debug.villagers_savior.favor_set", 60, 4, 8, 60, 60, 2000);
        checks += require(enSet.contains("set to 60") && enSet.contains("4 villagers") && enSet.contains("8 blocks")
            && enSet.contains("requested 2000"), "the English favor set reply keeps every value in its own slot");
        String zhAdd = render("zh_cn", "debug.villagers_savior.favor_add", 25, 4, 8, 25, 25, 25, 25);
        checks += require(zhAdd.contains("好感 25") && zhAdd.contains("4 名村民") && zhAdd.contains("8 格内")
            && zhAdd.contains("实际变化 25..25"), "the Chinese favor add reply keeps every value in its own slot");
        String zhClear = render("zh_cn", "debug.villagers_savior.favor_clear", 4, 8);
        checks += require(zhClear.contains("4 名村民") && zhClear.contains("8 格内"),
            "the Chinese favor clear reply keeps every value in its own slot");
        checks += require(FavorGossip.entriesFor(-40).stream().allMatch(entry -> entry.value() > 0),
            "negative favor stores magnitudes, so the vanilla weight supplies the sign");
        return checks;
    }
    /** Renders one localized string exactly as the command would, for slot-order checks. */
    private static String render(String locale, String key, Object... args) {
        try (var stream = FavorChecks.class.getResourceAsStream("/assets/villagers_savior/lang/" + locale + ".json")) {
            if (stream == null) throw new IllegalStateException("missing locale resource " + locale);
            var json = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            return String.format(json.getAsJsonObject().get(key).getAsString(), args);
        } catch (Exception error) {
            throw new AssertionError("cannot render " + locale + " " + key, error);
        }
    }
    private static int require(boolean ok, String name) {
        if (!ok) throw new AssertionError("favor rule: " + name);
        return 1;
    }
    public static void run(MinecraftServer server, ServerLevel level, BiConsumer<Boolean, String> check) {
        // Only entity-ticking chunks expose villagers to entity queries, so the distance test uses two
        // villagers the server really keeps live and checks the radius boundary between them.
        double x = 0.5, z = 0.5;
        var profile = new GameProfile(UUID.randomUUID(), "FavorTest");
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
            CommonListenerCookie.createInitial(profile, false));
        player.snapTo(x, 4, z);
        // Spawn-area chunks are entity-ticking, so both villagers are really visible to entity queries.
        Villager near = villager(level, x + 1.5, z);
        Villager distant = villager(level, x + 5.5, z);
        UUID otherId = UUID.randomUUID();
        near.getGossips().add(otherId, GossipType.MAJOR_POSITIVE, 10);
        var dispatcher = server.getCommands().getDispatcher().getRoot().getChild("villagerssavior").getChild("debug").getChild("favor");
        check.accept(dispatcher != null && dispatcher.getChild("add") != null && dispatcher.getChild("set") != null
            && dispatcher.getChild("clear") != null, "favor command exposes add, set and clear");
        check.accept(FavorGossip.MAX_RADIUS >= 30_000_000,
            "the command radius reaches the world border instead of the much smaller scan cap");
        var source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        String prefix = "villagerssavior debug favor ";
        // A radius that only covers the closer villager must leave the other one alone.
        server.getCommands().performPrefixedCommand(source, prefix + "set 60 3");
        check.accept(rep(near, player) == 60 && rep(distant, player) == 0,
            "favor set reaches villagers inside the radius and spares the rest");
        // Radii far above the old 256/512 cap are accepted and still applied.
        server.getCommands().performPrefixedCommand(source, prefix + "set 60 30000000");
        check.accept(rep(near, player) == 60 && rep(distant, player) == 60,
            "a world-border radius edits every live villager");
        server.getCommands().performPrefixedCommand(source, prefix + "add 25 2000");
        check.accept(rep(near, player) == 85 && rep(distant, player) == 85, "favor add is a relative edit");
        // ADD must keep working once the major gossip entry is capped; it may only use the minor headroom left.
        server.getCommands().performPrefixedCommand(source, prefix + "set 100 8");
        server.getCommands().performPrefixedCommand(source, prefix + "add 25 8");
        check.accept(rep(near, player) == 125, "add still applies once the major gossip entry is capped");
        server.getCommands().performPrefixedCommand(source, prefix + "add 25 8");
        check.accept(rep(near, player) == 125, "add cannot pass the vanilla gossip cap");
        server.getCommands().performPrefixedCommand(source, prefix + "set -20 8");
        server.getCommands().performPrefixedCommand(source, prefix + "add 25 8");
        check.accept(rep(near, player) == 5, "add crosses from negative to positive favour");
        server.getCommands().performPrefixedCommand(source, prefix + "add 25");
        check.accept(rep(near, player) == 30, "add without a radius uses the default radius");
        server.getCommands().performPrefixedCommand(source, prefix + "clear 2000");
        server.getCommands().performPrefixedCommand(source, prefix + "set -40 2000");
        check.accept(rep(near, player) == -40 && rep(distant, player) == -40, "negative favor is stored as negative gossip");
        server.getCommands().performPrefixedCommand(source, prefix + "set 1000 2000");
        check.accept(rep(near, player) == FavorGossip.maxPositive() && rep(distant, player) == FavorGossip.maxPositive(),
            "requests above the vanilla cap land exactly on it");
        // Only the calling player's gossip may change.
        check.accept(rep(near, otherId) == 50, "another player's gossip is untouched");
        // The edit is the value the read-only HUD report shows, so nothing parallel was created.
        server.getCommands().performPrefixedCommand(source, prefix + "set 30 3");
        check.accept(VillagerInspection.inspect(near, player).reputation().total() == 30,
            "the read-only HUD report shows the edited favor");
        // Default radius is village-sized: 8 blocks away is inside it.
        server.getCommands().performPrefixedCommand(source, prefix + "clear 2000");
        server.getCommands().performPrefixedCommand(source, prefix + "set 30");
        check.accept(rep(near, player) == 30 && rep(distant, player) == 30,
            "the default radius covers the whole immediate area");
        server.getCommands().performPrefixedCommand(source, prefix + "clear 2000");
        check.accept(rep(near, player) == 0 && rep(distant, player) == 0, "favor clear removes this player's gossip in range");
        // Negative control: a source without permission cannot change anything.
        server.getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS), prefix + "set 99 2000");
        check.accept(rep(near, player) == 0, "a source without permission cannot edit favor");
        // Negative control: with no villager in range the command changes nothing.
        player.snapTo(20000.5, 4, 20000.5);
        level.getChunk(1250, 1250);
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS),
            prefix + "set 50 32");
        check.accept(rep(near, player) == 0, "an empty area reports nothing and changes nothing");
        near.discard();
        distant.discard();
    }
    private static int rep(Villager villager, ServerPlayer player) { return villager.getPlayerReputation(player); }
    private static int rep(Villager villager, UUID player) { return villager.getGossips().getReputation(player, type -> true); }
    private static Villager villager(ServerLevel level, double x, double z) {
        level.getChunk(((int) x) >> 4, ((int) z) >> 4);
        Villager villager = new Villager(EntityTypes.VILLAGER, level);
        villager.snapTo(x, 4, z);
        level.addFreshEntity(villager);
        return villager;
    }
}
