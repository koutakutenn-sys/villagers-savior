package dev.villagerssavior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
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
