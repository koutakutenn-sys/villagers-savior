package dev.villagerssavior.test;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.villagerssavior.SaviorState;
import dev.villagerssavior.TickCache;
import dev.villagerssavior.Villages;
import dev.villagerssavior.profession.PopulationSupply;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.util.datafix.DataFixers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** Cache reuse/expiry and saved-history regression checks, without timing-dependent TPS assertions. */
public final class PerformanceChecks {
    private PerformanceChecks() {}
    private static JsonObject json(SaviorState state) {
        return SaviorState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
    }
    public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) {
        cacheChecks(check);
        stateChecks(check);
        poiChecks(server.overworld(), check);
        cropChecks(server.overworld(), check);
        diskChecks(server.overworld(), check);
        maintenanceChecks(server.overworld(), check);
    }
    private static void cacheChecks(BiConsumer<Boolean, String> check) {
        var cache = new TickCache<String, Integer>(100, 3);
        var calls = new AtomicInteger();
        for (int i = 0; i < 1000; i++) cache.get("village", i % 100, calls::incrementAndGet);
        check.accept(calls.get() == 1, "cache performs one computation for 1000 same-window lookups");
        check.accept(cache.get("village", 100, calls::incrementAndGet) == 2, "cache expires at its exact tick boundary");
        check.accept(cache.get("village", 99, calls::incrementAndGet) == 3, "clock rollback invalidates cache entry");
        cache.put("second", 99, 2); cache.put("third", 99, 3);
        cache.get("village", 99, calls::incrementAndGet); cache.put("fourth", 99, 4);
        check.accept(cache.size() == 3 && cache.get("second", 99, () -> 20) == 20, "cache bounds memory and evicts least recently used key");
        var negatives = new TickCache<String, Integer>(200, 2);
        calls.set(0);
        for (int i = 0; i < 1000; i++) negatives.get("no-crops", 0, () -> { calls.incrementAndGet(); return -1; });
        check.accept(calls.get() == 1, "negative crop evidence is cached too");
    }
    private static void stateChecks(BiConsumer<Boolean, String> check) {
        UUID player = UUID.randomUUID(), villager = UUID.randomUUID();
        String prefix = "ration:" + player + ":";
        var legacy = new JsonObject(); var aliases = new JsonObject(); var cooldowns = new JsonObject(); var pois = new JsonObject();
        aliases.addProperty("legacy", "middle"); aliases.addProperty("middle", "current");
        cooldowns.addProperty(prefix + "legacy", 30); cooldowns.addProperty(prefix + "middle", 50);
        cooldowns.addProperty(prefix + "current", 40); pois.addProperty("old-poi", "legacy");
        legacy.add("aliases", aliases); legacy.add("cooldowns", cooldowns); legacy.add("pois", pois);
        var state = SaviorState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        check.accept(json(state).getAsJsonObject("cooldowns").size() == 1,
            "legacy alias cooldown keys normalize on load");
        check.accept(!state.ready("ration", player, "legacy", 149, 100) && state.ready("ration", player, "current", 150, 100),
            "legacy normalization keeps the latest timestamp and exact cooldown boundary");
        check.accept(state.remainingAt("ration", player, List.of("old-poi"), 51, 100) == 99,
            "readonly cooldown lookup follows legacy alias chains");
        String a = state.village(List.of("a")), b = state.village(List.of("b"));
        state.cooldown("ration", player, a, 100); state.cooldown("ration", player, b, 200);
        check.accept(state.remainingAt("ration", player, List.of("a", "b"), 201, 100) == 99,
            "pending village merge preview uses the later cooldown");
        String merged = state.village(List.of("a", "b"));
        check.accept(!state.ready("ration", player, merged, 299, 100) && state.ready("ration", player, a, 300, 100)
            && state.ready("ration", player, b, 300, 100), "actual village merge preserves latest cooldown for both old IDs");
        check.accept(json(state).getAsJsonObject("cooldowns").size() == 2, "merge collapses duplicate cooldown keys");
        for (int i = 0; i < 1000; i++) state.cooldown("unrelated", UUID.randomUUID(), "v" + i, 200);
        var before = json(state);
        check.accept(state.remainingAt("ration", player, List.of("a", "b"), 201, 100) == 99
            && !state.ready("ration", player, a, 201, 100), "indexed cooldown lookup works among 1000 unrelated records");
        check.accept(before.equals(json(state)), "cooldown inspection never cleans or modifies saved history");

        var history = new SaviorState();
        String old = history.village(List.of("permanent-a")), fresh = history.village(List.of("permanent-b"));
        history.village(List.of("permanent-a", "permanent-b"));
        history.cooldown("raid", player, old, 0);
        history.cooldown("ration", player, fresh, 1);
        history.cooldown("future", player, old, SaviorState.WEEK + 1);
        history.golemKill(player, villager, 0);
        UUID survivor = UUID.randomUUID();
        history.golemKill(player, survivor, 0); history.golemKill(player, survivor, 1);
        history.dailyVillagerGrant("old", villager, 0, 1, 1);
        history.dailyGrant("kill", player, survivor, SaviorState.WEEK, 1, 1);
        var permanent = json(history);
        history.prune(SaviorState.WEEK - 1);
        check.accept(json(history).getAsJsonObject("cooldowns").size() == 3, "history prune retains records before week boundary");
        history.prune(SaviorState.WEEK);
        var pruned = json(history);
        check.accept(pruned.getAsJsonObject("cooldowns").size() == 2, "history prune removes cooldown at exact week boundary and retains future timestamps");
        check.accept(pruned.getAsJsonObject("kills").size() == 1
            && pruned.getAsJsonObject("kills").getAsJsonArray(player + ":" + survivor).size() == 1,
            "history prune removes inactive kill keys and expired entries of active keys");
        check.accept(pruned.getAsJsonObject("daily").size() == 1, "daily maintenance keeps current day and removes old quota keys");
        check.accept(permanent.get("pois").equals(pruned.get("pois")) && permanent.get("aliases").equals(pruned.get("aliases")),
            "history cleanup preserves permanent POI identities and aliases");
        var restored = SaviorState.CODEC.parse(JsonOps.INSTANCE, pruned).getOrThrow();
        check.accept(!restored.ready("ration", player, fresh, SaviorState.WEEK, SaviorState.WEEK)
            && restored.golemKill(player, survivor, SaviorState.WEEK) == 2, "pruned history roundtrip preserves live cooldown and kill escalation");
        var quota = new SaviorState();
        check.accept(quota.dailyVillagerRemaining("produce", villager, 0, 2) == 2 && !json(quota).has("daily"),
            "production allowance peek is readonly");
        quota.dailyVillagerGrant("produce", villager, 0, 1, 2);
        check.accept(quota.dailyVillagerRemaining("produce", villager, 0, 2) == 1
            && quota.dailyVillagerRemaining("produce", villager, 24000, 2) == 2, "production allowance resets by game day");
    }
    private static void poiChecks(ServerLevel level, BiConsumer<Boolean, String> check) {
        var origin = new BlockPos(8192, 4, 8192);
        level.getChunk(origin);
        var home = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
        level.getPoiManager().add(origin, home); level.getPoiManager().add(origin.east(10), home);
        level.getPoiManager().tick(() -> true);
        long now = level.getGameTime();
        var data = (ServerLevelData) level.getLevelData();
        var history = json(SaviorState.get(level));
        try {
            var first = Villages.previewPositions(level, origin).orElseThrow();
            boolean reused = true;
            for (int i = 0; i < 1000; i++) reused &= Villages.previewPositions(level, origin.east(i % 2 * 10)).orElseThrow() == first;
            check.accept(first.size() == 2 && reused, "1000 HUD topology lookups share one snapshot across nearest POIs");
            level.getChunk(origin.east(20)); level.getPoiManager().add(origin.east(20), home);
            check.accept(Villages.positions(level, origin).orElseThrow().size() == 3, "authoritative topology sees newly added POI immediately");
            data.setGameTime(now + Villages.PREVIEW_TTL - 1);
            check.accept(Villages.previewPositions(level, origin).orElseThrow() == first, "HUD topology snapshot stays cached before expiry");
            data.setGameTime(now + Villages.PREVIEW_TTL);
            var refreshed = Villages.previewPositions(level, origin).orElseThrow();
            check.accept(refreshed != first && refreshed.size() == 3, "HUD topology refreshes at 100 ticks");
            level.getPoiManager().remove(origin.east(20));
            check.accept(Villages.positions(level, origin).orElseThrow().size() == 2, "authoritative topology also sees POI removal immediately");
            check.accept(history.equals(json(SaviorState.get(level))), "topology cache and authoritative positions do not assign village IDs");
        } finally { data.setGameTime(now); }
    }
    private static void cropChecks(ServerLevel level, BiConsumer<Boolean, String> check) {
        var origin = new BlockPos(8192, 4, 8704);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.getChunk((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z);
        var first = new Villager(EntityTypes.VILLAGER, level); first.snapTo(origin.getX(), 4, origin.getZ());
        var second = new Villager(EntityTypes.VILLAGER, level); second.snapTo(origin.getX() + 10, 4, origin.getZ());
        var points = List.of(Long.toString(origin.asLong()));
        long now = level.getGameTime();
        check.accept(PopulationSupply.chooseCrop(level, first, points, now) == -1, "empty village caches absence of planted crop evidence");
        level.setBlock(origin.below(), Blocks.FARMLAND.defaultBlockState(), 2);
        level.setBlock(origin, Blocks.CARROTS.defaultBlockState(), 2);
        check.accept(PopulationSupply.chooseCrop(level, second, points, now + 199) == -1, "same village shares negative crop snapshot until expiry");
        check.accept(PopulationSupply.chooseCrop(level, second, points, now + 200) == 1, "crop snapshot discovers planted carrots at 200 ticks");
        level.setBlock(origin, Blocks.POTATOES.defaultBlockState(), 2);
        check.accept(PopulationSupply.chooseCrop(level, first, points, now + 399) == 1, "farmers share positive crop evidence across the village");
        first.getInventory().setItem(0, new ItemStack(Items.WHEAT));
        check.accept(PopulationSupply.chooseCrop(level, first, points, now + 399) == 0, "live inventory overrides cached planted crop evidence immediately");
        first.getInventory().clearContent();
        check.accept(PopulationSupply.chooseCrop(level, first, points, now + 400) == 2, "positive crop snapshot refreshes at exact expiry");
        check.accept(PopulationSupply.chooseCrop(level, first, List.of("another-village"), now + 201) == 2,
            "separate village key has independent crop cache");
    }
    private static void diskChecks(ServerLevel level, BiConsumer<Boolean, String> check) {
        UUID player = UUID.randomUUID(), villager = UUID.randomUUID();
        try {
            var directory = Files.createTempDirectory("villagers-savior-review-history");
            String oldId;
            try (var storage = new SavedDataStorage(directory, DataFixers.getDataFixer(), level.registryAccess())) {
                var state = storage.computeIfAbsent(SaviorState.TYPE);
                oldId = state.village(List.of("disk-a"));
                String other = state.village(List.of("disk-b"));
                state.cooldown("raid", player, oldId, 1); state.cooldown("raid", player, other, 2);
                state.cooldown("expired", player, oldId, 0);
                state.village(List.of("disk-a", "disk-b"));
                state.golemKill(player, villager, 0); state.golemKill(player, villager, 1);
                state.dailyVillagerGrant("produce", villager, SaviorState.WEEK, 1, 2);
                state.prune(SaviorState.WEEK);
            }
            try (var storage = new SavedDataStorage(directory, DataFixers.getDataFixer(), level.registryAccess())) {
                var state = storage.computeIfAbsent(SaviorState.TYPE);
                check.accept(!state.ready("raid", player, oldId, SaviorState.WEEK, SaviorState.WEEK),
                    "indexed merged cooldown survives real SavedData disk reload");
                check.accept(json(state).getAsJsonObject("cooldowns").size() == 1,
                    "expired cooldown stays removed after real disk reload");
                check.accept(state.golemKill(player, villager, SaviorState.WEEK) == 2,
                    "pruned kill history keeps live escalation after disk reload");
                check.accept(state.dailyVillagerRemaining("produce", villager, SaviorState.WEEK, 2) == 1,
                    "successful production quota survives real disk reload");
                check.accept(state.remainingAt("raid", player, List.of("disk-a", "disk-b"), SaviorState.WEEK, SaviorState.WEEK) == 2,
                    "permanent POI aliases resolve readonly cooldown after disk reload");
            }
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }
    private static void maintenanceChecks(ServerLevel level, BiConsumer<Boolean, String> check) {
        var state = SaviorState.get(level); UUID player = UUID.randomUUID();
        String key = "maintenance-probe:" + player + ":probe";
        state.cooldown("maintenance-probe", player, "probe", 2400 - SaviorState.WEEK);
        long now = level.getGameTime(); var data = (ServerLevelData) level.getLevelData();
        try {
            data.setGameTime(2401);
            ServerTickEvents.END_LEVEL_TICK.invoker().onEndTick(level);
            check.accept(json(state).getAsJsonObject("cooldowns").has(key), "periodic maintenance skips non-1200-tick boundary");
            data.setGameTime(2400);
            ServerTickEvents.END_LEVEL_TICK.invoker().onEndTick(level);
            check.accept(!json(state).getAsJsonObject("cooldowns").has(key), "registered server tick hook prunes expired history at 1200-tick boundary");
        } finally { data.setGameTime(now); }
    }
}
