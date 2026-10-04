package dev.villagerssavior.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import dev.villagerssavior.*;
import dev.villagerssavior.debug.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Covers aggregation and actual unloaded entity-region reads in a dedicated test world. */
public final class ReputationScanChecks {
    private static CompoundTag tag(Villager villager) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, villager.level().registryAccess());
        if (!villager.save(output)) throw new IllegalStateException("fixture villager save failed");
        return output.buildResult();
    }
    private static Villager detached(ServerLevel level, ServerPlayer player, double x, String profession, int positive) {
        var villager = new Villager(EntityTypes.VILLAGER, level); villager.snapTo(x, 4, 0.5);
        var key = switch (profession) { case "farmer" -> VillagerProfession.FARMER; case "cleric" -> VillagerProfession.CLERIC;
            case "librarian" -> VillagerProfession.LIBRARIAN; default -> VillagerProfession.FLETCHER; };
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), key));
        if (positive > 0) {
            SaviorGossip.add(villager, player.getUUID(), GossipType.MAJOR_POSITIVE, positive / 5);
            SaviorGossip.add(villager, player.getUUID(), GossipType.MINOR_POSITIVE, positive % 5);
        }
        return villager;
    }
    public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) {
        var level = server.overworld();
        var profile = new GameProfile(UUID.randomUUID(), "ScanTest");
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
            CommonListenerCookie.createInitial(profile, false));
        player.snapTo(0.5, 4, 0.5);
        List<ReputationScan.Entry> values = new ArrayList<>();
        int[] reputations = {-20, 0, 24, 25, 74, 75, 180};
        for (int i = 0; i < reputations.length; i++) values.add(new ReputationScan.Entry(UUID.randomUUID(), Vec3.ZERO,
            "farmer", reputations[i], i % 2 == 0));
        var sum = ReputationScan.summarize(values);
        check.accept(sum.sum() == 358 && Math.abs(sum.average() - 358.0 / 7) < 1e-12, "scan mean uses uncapped real reputation");
        check.accept(sum.median() == 25 && sum.min() == -20 && sum.max() == 180, "scan reports odd median and extrema");
        check.accept(sum.wary() == 1 && sum.neutral() == 2 && sum.trusted() == 2 && sum.honored() == 2,
            "scan standing counts cover all exact boundaries");
        check.accept(sum.live() == 3 && sum.saved() == 4, "scan distinguishes live and saved snapshots");
        check.accept(sum.entries().getFirst().reputation() == -20 && sum.entries().getLast().reputation() == 180,
            "individual profession/reputation details sorted lowest first");
        var empty = ReputationScan.summarize(List.of());
        check.accept(empty.entries().isEmpty() && empty.average() == 0 && empty.median() == 0, "empty scan has finite zero statistics");
        check.accept(ReputationScan.summarize(values.subList(0, 2)).median() == -10, "scan even median averages middle two");
        var extremes = List.of(new ReputationScan.Entry(UUID.randomUUID(), Vec3.ZERO, "none", Integer.MAX_VALUE, false),
            new ReputationScan.Entry(UUID.randomUUID(), Vec3.ZERO, "none", Integer.MAX_VALUE, false));
        check.accept(ReputationScan.summarize(extremes).sum() == 4294967294L, "scan sum does not overflow signed integer");
        var sphere = ReputationScan.nearby(new Vec3(0.5, 4, 0.5), 64);
        check.accept(sphere.contains(new Vec3(64.5, 4, 0.5)) && !sphere.contains(new Vec3(64.5001, 4, 0.5)),
            "nearby scan includes spherical radius boundary only");
        check.accept(!sphere.contains(new Vec3(64.5, 68, 0.5)), "nearby scan rejects cube corners");
        check.accept(sphere.chunks().contains(new ChunkPos(-4, -4)), "scan chunks correctly include negative coordinates");
        for (int invalid : new int[]{0, 257}) {
            boolean rejected = false;
            try { ReputationScan.nearby(Vec3.ZERO, invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            check.accept(rejected, "scan rejects radius outside 1..256: " + invalid);
        }
        var village = new ReputationScan.Region(Vec3.ZERO, 64, List.of(new BlockPos(0, 4, 0)),
            List.of(new BlockPos(0, 4, 0), new BlockPos(90, 4, 0)), Set.of());
        check.accept(village.contains(new Vec3(20, 4, 0)) && !village.contains(new Vec3(60, 4, 0)),
            "village scan assigns villagers to nearest POI region");
        check.accept(village.contains(new Vec3(45, 4, 0)), "village exact-distance ties use stable coordinates");
        var storage = ReputationScan.storage(level);
        var live = detached(level, player, 2.5, "farmer", 31); live.setNoAi(true);
        check.accept(level.addFreshEntity(live), "scan live fixture added to entity manager");
        var savedOne = detached(level, player, 3201.5, "cleric", 12);
        var savedTwo = detached(level, player, 3240.5, "farmer", 35);
        var passenger = detached(level, player, 3265.5, "librarian", 80);
        var outside = detached(level, player, 3310.5, "fletcher", 99);
        var dead = detached(level, player, 3202.5, "cleric", 50); dead.setHealth(0);
        var duplicate = detached(level, player, 3203.5, "farmer", 100); duplicate.setUUID(live.getUUID());
        Map<ChunkPos, List<CompoundTag>> fixtures = new HashMap<>();
        for (var villager : List.of(savedOne, savedTwo, outside, dead, duplicate))
            fixtures.computeIfAbsent(villager.chunkPosition(), ignored -> new ArrayList<>()).add(tag(villager));
        var boat = new CompoundTag(); boat.putString("id", "minecraft:oak_boat");
        var passengers = new ListTag(); passengers.add(tag(passenger)); boat.put("Passengers", passengers);
        fixtures.computeIfAbsent(passenger.chunkPosition(), ignored -> new ArrayList<>()).add(boat);
        for (var entry : fixtures.entrySet()) {
            var data = NbtUtils.addCurrentDataVersion(new CompoundTag()); var entities = new ListTag();
            entry.getValue().forEach(entities::add); data.put("Entities", entities); data.store("Position", ChunkPos.CODEC, entry.getKey());
            storage.write(entry.getKey(), data).join();
        }
        int chunksBefore = level.getChunkSource().getLoadedChunksCount();
        String historyBefore = SaviorState.CODEC.encodeStart(JsonOps.INSTANCE, SaviorState.get(level)).getOrThrow().toString();
        var diskBefore = storage.read(new ChunkPos(200, 0)).join().orElseThrow().copy();
        var savedScan = ReputationScan.scan(player, ReputationScan.nearby(new Vec3(3200.5, 4, 0.5), 80)).join();
        check.accept(savedScan.saved() == 3 && savedScan.live() == 0 && savedScan.sum() == 127,
            "scan reads all three saved villagers including boat passenger; dead and stale live UUID excluded");
        check.accept(savedScan.entries().stream().anyMatch(e -> e.uuid().equals(savedOne.getUUID()) && e.profession().equals("cleric") && e.reputation() == 12),
            "saved villager retains individual profession and actual gossip reputation");
        check.accept(savedScan.entries().stream().anyMatch(e -> e.uuid().equals(passenger.getUUID()) && e.profession().equals("librarian")),
            "saved passenger profession appears in individual details");
        check.accept(level.getChunkSource().getLoadedChunksCount() == chunksBefore && !level.areEntitiesLoaded(new ChunkPos(200, 0).pack()),
            "saved scan does not load or generate world chunks");
        check.accept(level.getEntityInAnyDimension(savedOne.getUUID()) == null, "saved scan does not instantiate live villagers");
        check.accept(storage.read(new ChunkPos(200, 0)).join().orElseThrow().equals(diskBefore), "saved scan leaves entity NBT unchanged");
        check.accept(historyBefore.equals(SaviorState.CODEC.encodeStart(JsonOps.INSTANCE, SaviorState.get(level)).getOrThrow().toString()),
            "scan does not write reputation/cooldown/village history");
        var liveScan = ReputationScan.scan(player, ReputationScan.nearby(new Vec3(2.5, 4, 0.5), 1)).join();
        check.accept(liveScan.entries().stream().anyMatch(e -> e.uuid().equals(live.getUUID()) && !e.saved() && e.reputation() == 31),
            "loaded villager uses live profession and real reputation");
        var home = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
        for (int x : new int[]{3200, 3264, 3354}) level.getPoiManager().add(new BlockPos(x, 4, 0), home);
        var villageRegion = ReputationScan.village(level, new BlockPos(3200, 4, 0)).orElseThrow();
        check.accept(villageRegion.villagePois().size() == 2, "village scan uses actual connected POI boundary");
        var villageScan = ReputationScan.scan(player, villageRegion).join();
        check.accept(villageScan.saved() == 3 && villageScan.sum() == 127, "whole village scan includes unloaded residents and excludes neighboring village");
        check.accept(level.getChunkSource().getLoadedChunksCount() == chunksBefore,
            "whole village POI traversal and entity scan do not load or generate world chunks");
        var otherPlayer = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "OtherScan"), ClientInformation.createDefault());
        check.accept(ReputationScan.scan(otherPlayer, villageRegion).join().sum() == 0, "scan always reads calling player's reputation only");
        var debugNode = server.getCommands().getDispatcher().getRoot().getChild("villagerssavior").getChild("debug");
        check.accept(debugNode.canUse(player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS)), "operator can use debug commands");
        check.accept(!debugNode.canUse(player.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS)), "ordinary players cannot run disk-backed debug scans");
        check.accept(debugNode.getChild("nearby") != null && debugNode.getChild("village") != null && debugNode.getChild("list") != null,
            "nearby, village and paginated individual list commands registered");
        int index = 0; Map<UUID, String> professions = new HashMap<>();
        var registry = level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION);
        for (var key : registry.registryKeySet()) {
            var villager = detached(level, player, 1000 + index++, "farmer", 7);
            villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), key));
            if (!level.addFreshEntity(villager)) throw new IllegalStateException("profession fixture spawn failed");
            var id = key.identifier(); professions.put(villager.getUUID(), id.getNamespace().equals("minecraft") ? id.getPath() : id.toString());
        }
        var jobsScan = ReputationScan.scan(player, ReputationScan.nearby(new Vec3(1000, 4, 0.5), 64)).join();
        for (var expected : professions.entrySet()) check.accept(jobsScan.entries().stream().anyMatch(entry ->
            entry.uuid().equals(expected.getKey()) && entry.profession().equals(expected.getValue()) && entry.reputation() == 7 && !entry.saved()),
            "individual live profession and reputation retained: " + expected.getValue());
        check.accept(level.getChunkSource().getLoadedChunksCount() == chunksBefore,
            "scan reads villagers held in non-active sections without terrain loading");
        var malformed = tag(savedOne); malformed.remove("VillagerData"); boolean failed = false;
        try {
            ReputationScan.collectSaved(malformed, player.getUUID(), ReputationScan.nearby(savedOne.position(), 1),
                net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, level.registryAccess()), Set.of(), new HashMap<>());
        } catch (IllegalArgumentException expected) { failed = true; }
        check.accept(failed, "malformed saved profession fails closed instead of silently reporting wrong occupation");
    }
}
