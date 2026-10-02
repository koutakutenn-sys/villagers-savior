package dev.villagerssavior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import java.util.*;

/** POIs within 64 blocks connect. Persistent aliases keep cooldowns when villages merge/change. */
public final class Villages {
    private Villages() {}
    public static Optional<String> identify(ServerLevel level, BlockPos origin) {
        var manager = level.getPoiManager();
        var first = manager.findClosest(t -> t.is(PoiTypeTags.VILLAGE), origin, 64, PoiManager.Occupancy.ANY);
        if (first.isEmpty()) return Optional.empty();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(first.get()); queue.add(first.get());
        while (!queue.isEmpty()) {
            BlockPos at = queue.remove();
            manager.getInRange(t -> t.is(PoiTypeTags.VILLAGE), at, 64, PoiManager.Occupancy.ANY)
                .forEach(p -> { if (visited.add(p.getPos())) queue.add(p.getPos()); });
            // Fail closed for unusually huge networks rather than let partial IDs bypass cooldowns.
            if (visited.size() > 4096) return Optional.empty();
        }
        return Optional.of(SaviorState.get(level).village(visited.stream().map(p -> Long.toString(p.asLong())).toList()));
    }
}
