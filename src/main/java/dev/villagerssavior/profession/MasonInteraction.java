package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Map;
import java.util.Optional;

/** Mason service: the vanilla stone brick recipe (4 stone -> 4 stone bricks) for real materials. */
public final class MasonInteraction implements ProfessionInteraction {
    private static final Map<Item, Integer> COST = Map.of(Items.STONE, 4);
    private static final int BRICKS_PER_BATCH = 4;
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        return ConversionService.serve(level, villager, player, now, "service_mason", COST,
            Items.STONE_BRICKS, BRICKS_PER_BATCH);
    }
}
