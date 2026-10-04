package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Map;
import java.util.Optional;

/** Fletcher service: the vanilla arrow recipe (flint + stick + feather -> 4 arrows) for real materials. */
public final class FletcherInteraction implements ProfessionInteraction {
    private static final Map<Item, Integer> COST = Map.of(
        Items.FLINT, 1,
        Items.STICK, 1,
        Items.FEATHER, 1);
    private static final int ARROWS_PER_BATCH = 4;
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        return ConversionService.serve(level, villager, player, now, "service_fletcher", COST,
            Items.ARROW, ARROWS_PER_BATCH);
    }
}
