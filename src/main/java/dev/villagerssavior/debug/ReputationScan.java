package dev.villagerssavior.debug;

import dev.villagerssavior.Villages;
import dev.villagerssavior.mixin.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.phys.Vec3;

/** Reads live villagers and unloaded entity NBT, without loading/generating chunks or spawning entities. */
public final class ReputationScan {
    public static final int DEFAULT_RADIUS = 64, MAX_RADIUS = 256, MAX_CHUNKS = 4096;
    public record Entry(UUID uuid, Vec3 position, String profession, int reputation, boolean saved) {}
    private static String profession(VillagerData data) {
        return data.profession().unwrapKey().map(key -> key.identifier())
            .map(id -> id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()).orElse("none");
    }
    public record Summary(List<Entry> entries, long sum, double average, double median, int min, int max,
                          int wary, int neutral, int trusted, int honored, int live, int saved) {
        public Summary { entries = List.copyOf(entries); }
    }
    public record Region(Vec3 center, int radius, List<BlockPos> villagePois, List<BlockPos> nearestPois,
                         Set<ChunkPos> chunks) {
        public Region {
            villagePois = List.copyOf(villagePois); nearestPois = List.copyOf(nearestPois); chunks = Set.copyOf(chunks);
        }
        public boolean village() { return !villagePois.isEmpty(); }
        public boolean contains(Vec3 position) {
            if (!village()) return center.distanceToSqr(position) <= (double) radius * radius;
            BlockPos at = BlockPos.containing(position);
            // Nearest POI determines village ownership; coordinates break exact-distance ties consistently.
            BlockPos closest = nearestPois.stream().filter(p -> p.distSqr(at) <= 4096)
                .min(Comparator.<BlockPos>comparingDouble(p -> p.distSqr(at)).thenComparingLong(BlockPos::asLong)).orElse(null);
            return closest != null && villagePois.contains(closest);
        }
    }
    private ReputationScan() {}
    public static Region nearby(Vec3 center, int radius) {
        if (radius < 1 || radius > MAX_RADIUS) throw new IllegalArgumentException("radius");
        Set<ChunkPos> chunks = new HashSet<>(); addChunks(chunks, center.x, center.z, radius);
        return new Region(center, radius, List.of(), List.of(), chunks);
    }
    public static Optional<Region> village(ServerLevel level, BlockPos origin) {
        return Villages.positions(level, origin).map(positions -> {
            var pois = positions.stream().map(Long::parseLong).map(BlockPos::of).toList();
            Set<ChunkPos> chunks = new HashSet<>(); Set<BlockPos> nearest = new HashSet<>();
            for (BlockPos poi : pois) {
                addChunks(chunks, poi.getX(), poi.getZ(), 64);
                level.getPoiManager().getInRange(t -> t.is(PoiTypeTags.VILLAGE), poi, 128, PoiManager.Occupancy.ANY)
                    .forEach(record -> nearest.add(record.getPos()));
            }
            return new Region(Vec3.atCenterOf(origin), 64, pois, new ArrayList<>(nearest), chunks);
        });
    }
    private static void addChunks(Set<ChunkPos> chunks, double x, double z, int radius) {
        int minX = Math.floorDiv((int) Math.floor(x - radius), 16), maxX = Math.floorDiv((int) Math.floor(x + radius), 16);
        int minZ = Math.floorDiv((int) Math.floor(z - radius), 16), maxZ = Math.floorDiv((int) Math.floor(z + radius), 16);
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
            chunks.add(new ChunkPos(cx, cz));
            if (chunks.size() > MAX_CHUNKS) throw new IllegalArgumentException("scan too large");
        }
    }
    public static SimpleRegionStorage storage(ServerLevel level) {
        var manager = ((InspectionLevelAccess) level).savior$entityManager();
        var storage = ((InspectionEntityManagerAccess) manager).savior$storage();
        if (!(storage instanceof EntityStorage entities)) throw new IllegalStateException("unsupported entity storage");
        return ((InspectionStorageAccess) entities).savior$region();
    }
    /** Call on the server thread; asynchronous continuation only reads captured values and NBT. */
    public static CompletableFuture<Summary> scan(ServerPlayer player, Region region) {
        ServerLevel level = player.level(); UUID playerId = player.getUUID();
        Set<UUID> liveIds = new HashSet<>(); Map<UUID, Entry> selected = new HashMap<>();
        // Include entities already held in hidden/non-ticking sections as well as tracked entities.
        // getAllEntities() only exposes visible entities and can miss a loaded section during transitions.
        var sections = ((InspectionEntityManagerAccess) ((InspectionLevelAccess) level).savior$entityManager()).savior$sections();
        var residents = sections.getAllChunksWithExistingSections().longStream()
            .boxed().flatMap(sections::getExistingSectionsInChunk).flatMap(section -> section.getEntities()).toList();
        for (var entity : residents) if (entity instanceof Villager villager) {
            liveIds.add(villager.getUUID());
            if (villager.isAlive() && region.contains(villager.position())) selected.put(villager.getUUID(),
                new Entry(villager.getUUID(), villager.position(), profession(villager.getVillagerData()),
                    villager.getPlayerReputation(player), false));
        }
        var unread = region.chunks().stream().filter(chunk -> !level.areEntitiesLoaded(chunk.pack())).toList();
        var storage = storage(level);
        var ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        // At most sixteen reads in flight. No chunk tickets, region writes, entity construction or gossip updates.
        for (int first = 0; first < unread.size(); first += 16) {
            var batch = unread.subList(first, Math.min(first + 16, unread.size()));
            chain = chain.thenCompose(ignored -> {
                var reads = batch.stream().map(storage::read).toList();
                return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).thenAccept(done -> {
                    for (var read : reads) read.join().ifPresent(tag -> {
                        CompoundTag upgraded = storage.upgradeChunkTag(tag, -1);
                        for (var value : upgraded.getListOrEmpty("Entities")) if (value instanceof CompoundTag entity)
                            collectSaved(entity, playerId, region, ops, liveIds, selected);
                    });
                });
            });
        }
        return chain.thenApply(ignored -> summarize(selected.values()));
    }
    public static void collectSaved(CompoundTag entity, UUID player, Region region,
                                     RegistryOps<net.minecraft.nbt.Tag> ops, Set<UUID> liveIds, Map<UUID, Entry> selected) {
        if (entity.getStringOr("id", "").equals("minecraft:villager") && entity.getFloatOr("Health", 20) > 0) {
            UUID uuid = entity.read("UUID", UUIDUtil.CODEC).orElseThrow(() -> new IllegalArgumentException("villager UUID missing"));
            Vec3 position = entity.read("Pos", Vec3.CODEC).orElseThrow(() -> new IllegalArgumentException("villager position missing"));
            if (!liveIds.contains(uuid) && region.contains(position)) {
                var gossip = entity.contains("Gossips") ? entity.read("Gossips", GossipContainer.CODEC)
                    .orElseThrow(() -> new IllegalArgumentException("invalid villager gossip")) : new GossipContainer();
                String profession = entity.read("VillagerData", VillagerData.CODEC, ops)
                    .map(ReputationScan::profession).orElseThrow(() -> new IllegalArgumentException("invalid villager profession"));
                selected.putIfAbsent(uuid, new Entry(uuid, position, profession, gossip.getReputation(player, type -> true), true));
            }
        }
        for (var value : entity.getListOrEmpty("Passengers")) if (value instanceof CompoundTag passenger)
            collectSaved(passenger, player, region, ops, liveIds, selected);
    }
    public static Summary summarize(Collection<Entry> values) {
        var sorted = values.stream().sorted(Comparator.comparingInt(Entry::reputation).thenComparing(Entry::uuid)).toList();
        long sum = 0; int wary = 0, neutral = 0, trusted = 0, honored = 0, live = 0, saved = 0;
        for (Entry entry : sorted) {
            sum += entry.reputation();
            if (entry.reputation() < 0) wary++; else if (entry.reputation() < 25) neutral++;
            else if (entry.reputation() < 75) trusted++; else honored++;
            if (entry.saved()) saved++; else live++;
        }
        int count = sorted.size();
        double median = count == 0 ? 0 : count % 2 == 1 ? sorted.get(count / 2).reputation()
            : ((long) sorted.get(count / 2 - 1).reputation() + sorted.get(count / 2).reputation()) / 2.0;
        return new Summary(sorted, sum, count == 0 ? 0 : (double) sum / count, median,
            count == 0 ? 0 : sorted.getFirst().reputation(), count == 0 ? 0 : sorted.getLast().reputation(),
            wary, neutral, trusted, honored, live, saved);
    }
}
