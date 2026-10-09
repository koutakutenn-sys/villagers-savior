package dev.villagerssavior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** POIs within 64 blocks connect. Persistent aliases keep cooldowns when villages merge/change. */
public final class Villages {
    public static final long PREVIEW_TTL = 100L;
    private static final Map<ServerLevel, TickCache<BlockPos, Optional<Collection<String>>>> PREVIEWS = new WeakHashMap<>();
    private Villages() {}
    public static Optional<String> identify(ServerLevel level, BlockPos origin) {
        return positions(level, origin).map(positions -> SaviorState.get(level).village(positions));
    }
    /** Same connected POIs as identify, without assigning IDs or merging saved history. */
    public static Optional<Collection<String>> positions(ServerLevel level, BlockPos origin) {
        var manager = level.getPoiManager();
        var first = manager.findClosest(t -> t.is(PoiTypeTags.VILLAGE), origin, 64, PoiManager.Occupancy.ANY);
        if (first.isEmpty()) return Optional.empty();
        return connected(level, first.get());
    }
    /** HUD-only topology snapshot. Real requests, deaths and debug scans always call fresh positions(). */
    public static Optional<Collection<String>> previewPositions(ServerLevel level, BlockPos origin) {
        var first = level.getPoiManager().findClosest(t -> t.is(PoiTypeTags.VILLAGE), origin, 64, PoiManager.Occupancy.ANY);
        if (first.isEmpty()) return Optional.empty();
        var cache = PREVIEWS.computeIfAbsent(level, ignored -> new TickCache<>(PREVIEW_TTL, 8192));
        long now = level.getGameTime();
        return cache.get(first.get(), now, () -> {
            var region = connected(level, first.get());
            // All targets in this region share one traversal, including targets with a different nearest POI.
            region.ifPresent(points -> points.forEach(encoded -> cache.put(BlockPos.of(Long.parseLong(encoded)), now, region)));
            return region;
        });
    }
    /** Bounding-box centre of a connected POI set; the village-centre definition used by kill rewards. */
    public static Optional<Vec3> center(Collection<String> positions) {
        return bounds(positions).map(Bounds::center);
    }
    /** Living villagers inside the connected POI bounding box inflated by {@code margin}. */
    public static List<Villager> residents(ServerLevel level, Collection<String> positions, int margin) {
        return bounds(positions)
            .map(b -> level.getEntitiesOfClass(Villager.class, b.box(level, margin), Entity::isAlive))
            .orElse(List.of());
    }
    /**
     * True when the nearest village POI within 64 blocks of {@code origin} belongs to this village: the same
     * membership rule the read-only scan uses, so "the whole village" means the same villagers everywhere.
     */
    public static boolean member(ServerLevel level, Collection<String> positions, BlockPos origin) {
        return level.getPoiManager().findClosest(t -> t.is(PoiTypeTags.VILLAGE), origin, 64, PoiManager.Occupancy.ANY)
            .map(pos -> positions.contains(Long.toString(pos.asLong()))).orElse(false);
    }
    private static Optional<Bounds> bounds(Collection<String> positions) {
        if (positions.isEmpty()) return Optional.empty();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (String encoded : positions) {
            BlockPos pos = BlockPos.of(Long.parseLong(encoded));
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
        }
        return Optional.of(new Bounds(minX, minY, minZ, maxX, maxY, maxZ));
    }
    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Vec3 center() {
            return new Vec3((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
        }
        AABB box(ServerLevel level, int margin) {
            return new AABB(minX - margin, Math.max(level.getMinY(), minY - margin), minZ - margin,
                maxX + 1 + margin, Math.min(level.getMaxY() + 1, maxY + 1 + margin), maxZ + 1 + margin);
        }
    }
    private static Optional<Collection<String>> connected(ServerLevel level, BlockPos first) {
        var manager = level.getPoiManager();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(first); queue.add(first);
        while (!queue.isEmpty()) {
            BlockPos at = queue.remove();
            manager.getInRange(t -> t.is(PoiTypeTags.VILLAGE), at, 64, PoiManager.Occupancy.ANY)
                .forEach(p -> { if (visited.add(p.getPos())) queue.add(p.getPos()); });
            // Fail closed for unusually huge networks rather than let partial IDs bypass cooldowns.
            if (visited.size() > 4096) return Optional.empty();
        }
        return Optional.of(visited.stream().map(p -> Long.toString(p.asLong())).toList());
    }
}
