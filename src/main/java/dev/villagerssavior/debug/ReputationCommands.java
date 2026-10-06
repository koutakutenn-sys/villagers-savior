package dev.villagerssavior.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.villagerssavior.SaviorEvents;
import dev.villagerssavior.SaviorGossip;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import static net.minecraft.commands.Commands.*;

/** Operator-only debug reports, always about the calling player's real vanilla reputation. */
public final class ReputationCommands {
    private static final int PAGE_SIZE = 10;
    /** Sanity bound for the requested reputation; the gossip caps clamp anything larger anyway. */
    private static final int FAVOR_LIMIT = 100_000;
    private enum FavorMode { ADD, SET, CLEAR }
    private record Cached(ResourceKey<Level> dimension, long created, ReputationScan.Summary summary) {}
    private static final Map<ServerPlayer, Cached> RESULTS = new WeakHashMap<>();
    private static final Set<ServerPlayer> RUNNING = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerPlayer, Long> REQUESTED = new WeakHashMap<>();
    private ReputationCommands() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
            literal("villagerssavior").then(literal("debug")
                .requires(source -> source.isPlayer() && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source))
                .then(literal("nearby").executes(context -> begin(context.getSource(), ReputationScan.DEFAULT_RADIUS, false))
                    .then(argument("radius", IntegerArgumentType.integer(1, ReputationScan.MAX_RADIUS))
                        .executes(context -> begin(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), false))))
                .then(literal("village").executes(context -> begin(context.getSource(), 64, true)))
                // Bulk favour editing over any distance; radius defaults to a village-sized area and its
                // upper bound is the world border, so the edit is never capped by the smaller scan radius.
                .then(literal("favor")
                    .then(literal("add")
                        .then(argument("delta", IntegerArgumentType.integer(-FAVOR_LIMIT, FAVOR_LIMIT))
                            .executes(context -> favor(context.getSource(), FavorGossip.DEFAULT_RADIUS, FavorMode.ADD,
                                IntegerArgumentType.getInteger(context, "delta")))
                            .then(argument("radius", IntegerArgumentType.integer(1, FavorGossip.MAX_RADIUS))
                                .executes(context -> favor(context.getSource(), IntegerArgumentType.getInteger(context, "radius"),
                                    FavorMode.ADD, IntegerArgumentType.getInteger(context, "delta"))))))
                    .then(literal("set")
                        .then(argument("total", IntegerArgumentType.integer(-FAVOR_LIMIT, FAVOR_LIMIT))
                            .executes(context -> favor(context.getSource(), FavorGossip.DEFAULT_RADIUS, FavorMode.SET,
                                IntegerArgumentType.getInteger(context, "total")))
                            .then(argument("radius", IntegerArgumentType.integer(1, FavorGossip.MAX_RADIUS))
                                .executes(context -> favor(context.getSource(), IntegerArgumentType.getInteger(context, "radius"),
                                    FavorMode.SET, IntegerArgumentType.getInteger(context, "total"))))))
                    .then(literal("clear")
                        .executes(context -> favor(context.getSource(), FavorGossip.DEFAULT_RADIUS, FavorMode.CLEAR, 0))
                        .then(argument("radius", IntegerArgumentType.integer(1, FavorGossip.MAX_RADIUS))
                            .executes(context -> favor(context.getSource(), IntegerArgumentType.getInteger(context, "radius"),
                                FavorMode.CLEAR, 0)))))
                .then(literal("list").executes(context -> list(context.getSource(), 1))
                    .then(argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> list(context.getSource(), IntegerArgumentType.getInteger(context, "page"))))))));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { RESULTS.clear(); RUNNING.clear(); REQUESTED.clear(); });
    }
    private static Component text(String key, Object... args) { return Component.translatable("debug.villagers_savior." + key, args); }
    private static void send(CommandSourceStack source, Component line) { source.sendSuccess(() -> line, false); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static Component profession(String value) {
        if (value.equals("none")) return text("profession_none");
        var id = Identifier.parse(value);
        return Component.translatableWithFallback("entity." + id.getNamespace() + ".villager." + id.getPath(), value);
    }
    private static int begin(CommandSourceStack source, int radius, boolean village) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException(); long now = player.level().getGameTime();
        if (RUNNING.contains(player) || now - REQUESTED.getOrDefault(player, Long.MIN_VALUE / 2) < 20) {
            source.sendFailure(text("running")); return 0;
        }
        final ReputationScan.Region region;
        try {
            var selected = village ? ReputationScan.village(player.level(), player.blockPosition())
                : Optional.of(ReputationScan.nearby(player.position(), radius));
            if (selected.isEmpty()) { source.sendFailure(text("no_village")); return 0; }
            region = selected.get();
        } catch (IllegalArgumentException error) { source.sendFailure(text("too_large", ReputationScan.MAX_CHUNKS)); return 0; }
        RUNNING.add(player); REQUESTED.put(player, now);
        var dimension = player.level().dimension();
        send(source, text("scanning", region.chunks().size()));
        try {
            ReputationScan.scan(player, region).whenComplete((summary, error) -> source.getServer().execute(() -> {
                RUNNING.remove(player);
                if (source.getServer().isStopped() || player.hasDisconnected()) return;
                if (error != null) { source.sendFailure(text("failed")); return; }
                RESULTS.put(player, new Cached(dimension, player.level().getGameTime(), summary));
                send(source, text(region.village() ? "village_scope" : "nearby_scope",
                    region.village() ? region.villagePois().size() : region.radius(),
                    dimension.identifier().toString(), (int) Math.floor(region.center().x),
                    (int) Math.floor(region.center().y), (int) Math.floor(region.center().z)));
                send(source, text("summary", summary.entries().size(), summary.live(), summary.saved(), summary.sum(),
                    number(summary.average()), number(summary.median()), summary.min(), summary.max()));
                send(source, text("standing", summary.wary(), summary.neutral(), summary.trusted(), summary.honored()));
                if (summary.saved() > 0) send(source, text("saved_note"));
                if (!summary.entries().isEmpty()) page(source, summary, 1);
            }));
        } catch (RuntimeException error) { RUNNING.remove(player); source.sendFailure(text("failed")); return 0; }
        return 1;
    }
    /**
     * Bulk-edits the calling player's real vanilla gossip for every living villager within {@code radius}.
     * No parallel reputation is stored, and the success message reports the values that were actually
     * reached (vanilla caps can clamp a request).
     */
    private static int favor(CommandSourceStack source, int radius, FavorMode mode, int amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        List<Villager> targets = SaviorEvents.villagers(level, player.position(), radius);
        if (targets.isEmpty()) { source.sendFailure(text("favor_none", radius)); return 0; }
        int minAfter = Integer.MAX_VALUE, maxAfter = Integer.MIN_VALUE;
        int minChange = Integer.MAX_VALUE, maxChange = Integer.MIN_VALUE;
        for (Villager villager : targets) {
            int before = storedFavor(villager, player);
            switch (mode) {
                // ADD is "clamp(current + delta)" and then written absolutely, so a delta that the major gossip
                // entry can no longer absorb still uses the remaining minor headroom instead of silently doing
                // nothing (which made add look broken once favour passed +100).
                case ADD -> writeFavor(villager, player, FavorGossip.clamp(before + amount));
                case SET -> writeFavor(villager, player, amount);
                case CLEAR -> clearFavor(villager, player);
            }
            int value = storedFavor(villager, player);
            minChange = Math.min(minChange, value - before);
            maxChange = Math.max(maxChange, value - before);
            minAfter = Math.min(minAfter, value);
            maxAfter = Math.max(maxAfter, value);
        }
        send(source, switch (mode) {
            case ADD -> text("favor_add", amount, targets.size(), radius, minChange, maxChange, minAfter, maxAfter);
            case SET -> text("favor_set", FavorGossip.clamp(amount), targets.size(), radius, minAfter, maxAfter, amount);
            case CLEAR -> text("favor_clear", targets.size(), radius);
        });
        return targets.size();
    }
    /**
     * Writes the request as real vanilla gossip entries. Entry values are always magnitudes; the sign comes
     * from the gossip type's own weight, so negative favour really becomes negative reputation instead of
     * being flattened to zero (which is all {@link SaviorGossip#subtract} can do, since it only trims
     * existing positive entries).
     */
    /**
     * Writes exactly {@code target} as real vanilla gossip, replacing this player's existing entries. Writing
     * absolutely (rather than adding to whatever type the decomposition happened to pick) is what keeps the
     * whole representable range reachable in one step.
     */
    private static void writeFavor(Villager villager, ServerPlayer player, int target) {
        clearFavor(villager, player);
        for (FavorGossip.Entry entry : FavorGossip.entriesFor(target)) {
            SaviorGossip.add(villager, player.getUUID(), entry.type(), entry.value());
        }
    }
    /**
     * The villager's stored total for this player, read straight from the gossip container: a nitwit's
     * {@code getPlayerReputation} is fixed at zero, while this command edits what is really stored.
     */
    private static int storedFavor(Villager villager, ServerPlayer player) {
        return villager.getGossips().getReputation(player.getUUID(), type -> true);
    }
    /** Removes every gossip entry this player has with the villager, leaving its other opinions untouched. */
    private static void clearFavor(Villager villager, ServerPlayer player) {
        for (GossipType type : GossipType.values()) villager.getGossips().remove(player.getUUID(), type);
    }
    private static int list(CommandSourceStack source, int page) throws CommandSyntaxException {
        var player = source.getPlayerOrException(); var cached = RESULTS.get(player);
        if (cached == null || !cached.dimension().equals(player.level().dimension())
            || player.level().getGameTime() - cached.created() > 600) {
            source.sendFailure(text("no_result")); return 0;
        }
        return page(source, cached.summary(), page);
    }
    private static int page(CommandSourceStack source, ReputationScan.Summary summary, int page) {
        int pages = Math.max(1, (summary.entries().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) { source.sendFailure(text("invalid_page", pages)); return 0; }
        send(source, text("page", page, pages));
        for (int index = (page - 1) * PAGE_SIZE; index < Math.min(page * PAGE_SIZE, summary.entries().size()); index++) {
            var entry = summary.entries().get(index);
            send(source, text("entry", index + 1, entry.uuid().toString(),
                (int) Math.floor(entry.position().x), (int) Math.floor(entry.position().y), (int) Math.floor(entry.position().z),
                profession(entry.profession()),
                entry.reputation(), text(entry.saved() ? "saved" : "live")));
        }
        return Math.min(PAGE_SIZE, summary.entries().size() - (page - 1) * PAGE_SIZE);
    }
}
