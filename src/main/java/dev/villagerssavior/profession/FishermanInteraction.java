package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Map;
import java.util.Optional;

/** Fisherman service: cooks the player's real raw fish with the player's real coal. */
public final class FishermanInteraction implements ProfessionInteraction {
    private static final Map<Item, Item> RECIPES = Map.of(
        Items.COD, Items.COOKED_COD,
        Items.SALMON, Items.COOKED_SALMON);
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        int reputation = ProfessionInteractions.reputation(villager, player);
        if (reputation < ProfessionInteractions.MIN_REPUTATION) return Optional.empty();
        var village = ProfessionInteractions.village(level, villager);
        if (village.isEmpty()) return Optional.empty();
        var state = SaviorState.get(level);
        if (!ProfessionInteractions.ready(state, "service_fisherman", player, village.get(), now,
            ProfessionInteractions.SERVICE_WINDOW)) return Optional.empty();
        int cooked = Cooking.cook(villager, player, RECIPES, Conversions.capFor(reputation));
        if (cooked <= 0) return Optional.empty();
        ProfessionInteractions.book(state, "service_fisherman", player, village.get(), now);
        return Optional.of("service_fisherman");
    }
}
