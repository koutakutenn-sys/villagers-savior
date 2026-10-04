package dev.villagerssavior.profession;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Optional;

/**
 * Cartographer service: directions only. It points at a nearby village that really exists in the POI
 * storage; no map items and no structure discoveries are ever created.
 */
public final class CartographerInteraction implements ProfessionInteraction {
    private static final int SEARCH_RADIUS = 256;
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        BlockPos origin = villager.blockPosition();
        var nearest = level.getPoiManager().findClosest(type -> type.is(PoiTypeTags.VILLAGE), origin,
            SEARCH_RADIUS, PoiManager.Occupancy.ANY);
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
}
