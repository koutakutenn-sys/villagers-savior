package dev.villagerssavior.profession;

import dev.villagerssavior.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Collection;
import java.util.Set;
import java.util.Optional;

/**
 * Cartographer service: directions only, and only towards a <em>different</em> village. The nearest village
 * POI used to be the one the cartographer is standing in, which made the service useless; its own connected
 * POI cluster is therefore excluded. No map items and no structure discoveries are ever created.
 */
public final class CartographerInteraction implements ProfessionInteraction {
    public static final int SEARCH_RADIUS = 1024;
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        BlockPos origin = villager.blockPosition();
        var nearest = findOtherVillage(level, origin);
        if (nearest.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.villagers_savior.cartographer_none",
                SEARCH_RADIUS), false);
            return Optional.of("service_cartographer");
        }
        BlockPos target = nearest.get();
        int distance = (int) Math.round(Math.sqrt(target.distSqr(origin)));
        Direction direction = Direction.getApproximateNearest(target.getX() - origin.getX(), 0.0, target.getZ() - origin.getZ());
        player.sendSystemMessage(Component.translatable("message.villagers_savior.cartographer_report",
            target.getX(), target.getY(), target.getZ(), distance,
            Component.translatable("message.villagers_savior.direction." + direction.getName())), false);
        return Optional.of("service_cartographer");
    }
    /**
     * Nearest VILLAGE POI that does not belong to the origin's own connected village cluster, so the
     * cartographer never "points" at the village it is standing in.
     */
    public static Optional<BlockPos> findOtherVillage(ServerLevel level, BlockPos origin) {
        Set<String> own = Villages.positions(level, origin).map(Set::copyOf).orElse(Set.of());
        return level.getPoiManager().findClosest(
            type -> type.is(PoiTypeTags.VILLAGE),
            pos -> !own.contains(Long.toString(pos.asLong())),
            origin, SEARCH_RADIUS, PoiManager.Occupancy.ANY);
    }
}
