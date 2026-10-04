package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Collection;
import java.util.UUID;
import java.util.Optional;

/** Cleric service: a short, low-intensity blessing paid for out of the cleric's own alchemy stock. */
public final class ClericInteraction implements ProfessionInteraction {
    public static final long NORMAL_COOLDOWN = 12000, EMERGENCY_COOLDOWN = 24000;
    public record Blessing(int duration, long cooldown, String event) {}
    public static Optional<Blessing> blessing(int reputation, float health) {
        if (health <= 0) return Optional.empty();
        if (reputation >= 0) return Optional.of(new Blessing(200, NORMAL_COOLDOWN, "service_cleric"));
        float threshold = reputation <= -100 ? 2 : 6;
        return health <= threshold ? Optional.of(new Blessing(100, EMERGENCY_COOLDOWN, "service_cleric_emergency"))
            : Optional.empty();
    }
    public static Optional<Item> reagent(Villager villager) {
        for (Item candidate : new Item[]{Items.REDSTONE, Items.GLOWSTONE_DUST, Items.LAPIS_LAZULI})
            if (ServiceItems.count(villager.getInventory(), candidate) > 0) return Optional.of(candidate);
        return Optional.empty();
    }
    /** A cast keeps its own cooldown duration even if reputation changes afterwards. */
    public static long remainingAt(SaviorState state, UUID player, Collection<String> positions, long now) {
        return Math.max(state.remainingAt("service_cleric", player, positions, now, NORMAL_COOLDOWN),
            state.remainingAt("service_cleric_emergency", player, positions, now, EMERGENCY_COOLDOWN));
    }
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        int reputation = ProfessionInteractions.reputation(villager, player);
        var plan = blessing(reputation, player.getHealth());
        if (plan.isEmpty()) return Optional.empty();
        var village = ProfessionInteractions.village(level, villager);
        if (village.isEmpty()) return Optional.empty();
        var state = SaviorState.get(level);
        if (!ProfessionInteractions.ready(state, "service_cleric", player, village.get(), now,
            NORMAL_COOLDOWN) || !ProfessionInteractions.ready(state, "service_cleric_emergency", player, village.get(), now,
            EMERGENCY_COOLDOWN)) return Optional.empty();
        var spent = reagent(villager);
        if (spent.isEmpty() || !ServiceItems.consume(villager.getInventory(), spent.get(), 1)) return Optional.empty();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, plan.get().duration(), 0));
        ProfessionInteractions.book(state, plan.get().event(), player, village.get(), now);
        return Optional.of(plan.get().event());
    }
}
