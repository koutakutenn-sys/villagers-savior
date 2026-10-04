package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Map;
import java.util.Optional;

/** Cleric service: a short, low-intensity blessing paid for out of the cleric's own alchemy stock. */
public final class ClericInteraction implements ProfessionInteraction {
    private static final Map<Item, Integer> COST = Map.of(Items.GOLD_INGOT, 1);
    private static final int DURATION = 200;
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        int reputation = ProfessionInteractions.reputation(villager, player);
        if (reputation < ProfessionInteractions.MIN_REPUTATION) return Optional.empty();
        var village = ProfessionInteractions.village(level, villager);
        if (village.isEmpty()) return Optional.empty();
        var state = SaviorState.get(level);
        if (!ProfessionInteractions.ready(state, "service_cleric", player, village.get(), now,
            ProfessionInteractions.SERVICE_WINDOW * 2)) return Optional.empty();
        Item spent = null;
        for (Item candidate : new Item[] {Items.REDSTONE, Items.GLOWSTONE_DUST, Items.LAPIS_LAZULI}) {
            if (ServiceItems.count(villager.getInventory(), candidate) > 0) { spent = candidate; break; }
        }
        if (spent == null) return Optional.empty();
        if (!ServiceItems.consume(villager.getInventory(), spent, 1)) return Optional.empty();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 0));
        ProfessionInteractions.book(state, "service_cleric", player, village.get(), now);
        return Optional.of("service_cleric");
    }
}
