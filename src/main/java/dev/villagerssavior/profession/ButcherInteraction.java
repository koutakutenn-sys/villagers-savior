package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Map;
import java.util.Optional;

/** Butcher service: cooks the villager's real raw meat with the villager's real coal. */
public final class ButcherInteraction implements ProfessionInteraction {
    private static final Map<Item, Item> RECIPES = Map.of(
        Items.BEEF, Items.COOKED_BEEF,
        Items.PORKCHOP, Items.COOKED_PORKCHOP,
        Items.CHICKEN, Items.COOKED_CHICKEN,
        Items.MUTTON, Items.COOKED_MUTTON,
        Items.RABBIT, Items.COOKED_RABBIT);
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        int reputation = ProfessionInteractions.reputation(villager, player);
        if (reputation < ProfessionInteractions.MIN_REPUTATION) return Optional.empty();
        var village = ProfessionInteractions.village(level, villager);
        if (village.isEmpty()) return Optional.empty();
        var state = SaviorState.get(level);
        if (!ProfessionInteractions.ready(state, "service_butcher", player, village.get(), now,
            ProfessionInteractions.SERVICE_WINDOW)) return Optional.empty();
        int cooked = Cooking.cook(villager, player, RECIPES, Conversions.capFor(reputation));
        if (cooked <= 0) return Optional.empty();
        ProfessionInteractions.book(state, "service_butcher", player, village.get(), now);
        return Optional.of("service_butcher");
    }
}
