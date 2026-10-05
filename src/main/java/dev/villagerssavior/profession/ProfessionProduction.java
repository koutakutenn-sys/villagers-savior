package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/**
 * Profession production: a successful vanilla workstation restock ("补货") adds a little of the profession's
 * own stock to the villager's real hidden inventory.
 *
 * <p>Every resource is bounded per villager: at most {@code perRestock} per restock, at most
 * {@code dailyCap} per in-game day, production stops
 * at {@code target} and can never pass {@code hardCap}. Hard caps only limit what this mod adds; items a
 * villager obtained through vanilla behaviour are never removed.
 */
public final class ProfessionProduction {
    private ProfessionProduction() {}
    /** Called right after a villager restocked its workstation. */
    public static void onRestock(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        ResourceKey<VillagerProfession> key = villager.getVillagerData().profession().unwrapKey().orElse(null);
        if (key == null) return;
        long now = level.getGameTime();
        SaviorState state = SaviorState.get(level);
        if (VillagerProfession.FARMER.equals(key)) {
            // The farmer is special: it only tops up the breeding reserve, for crops the village really grows.
            PopulationSupply.produce(level, villager, state, now);
            return;
        }
        for (ProfessionResources.Resource resource : ProfessionResources.of(key)) produce(villager, state, now, resource);
    }
    /** Adds one production step, bounded by the target, the hard cap and the daily cap. Returns what was added. */
    public static int produce(Villager villager, SaviorState state, long now, ProfessionResources.Resource resource) {
        int amount = ProfessionResources.producible(resource,
            ServiceItems.count(villager.getInventory(), resource.item()));
        if (amount <= 0) return 0;
        amount = Math.min(amount, state.dailyVillagerRemaining(dailyKey(resource), villager.getUUID(), now, resource.dailyCap()));
        amount = ServiceItems.roomFor(villager.getInventory(), new ItemStack(resource.item(), amount));
        if (amount <= 0) return 0;
        if (!ServiceItems.give(villager.getInventory(), new ItemStack(resource.item(), amount))) return 0;
        // Restock and insertion run synchronously on the server thread: book only successful insertion.
        state.dailyVillagerGrant(dailyKey(resource), villager.getUUID(), now, amount, resource.dailyCap());
        return amount;
    }
    /** Stable daily-counter key for one produced item. */
    static String dailyKey(ProfessionResources.Resource resource) {
        var id = BuiltInRegistries.ITEM.getKey(resource.item());
        return "produce:" + (id == null ? resource.item().toString() : id.toString());
    }
}
