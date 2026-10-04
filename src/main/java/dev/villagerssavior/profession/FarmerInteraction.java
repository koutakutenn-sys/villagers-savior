package dev.villagerssavior.profession;

import dev.villagerssavior.FoodGifts;
import dev.villagerssavior.FoodRules;
import dev.villagerssavior.SaviorState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Optional;

/**
 * Farmer service: travel rations for trusted players who are not hungry. Everything comes from the farmer's
 * real inventory, and real wheat is baked into real bread first (3 -> 1, the vanilla recipe).
 */
public final class FarmerInteraction implements ProfessionInteraction {
    private static final int WHEAT_PER_BREAD = 3;
    private static final int MAX_BATCHES_PER_VISIT = 8;
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        // Rations are the "you are not hungry, but I trust you" case; hunger is handled by the generic gift.
        if (player.getFoodData().getFoodLevel() < 20) return Optional.empty();
        int reputation = ProfessionInteractions.reputation(villager, player);
        if (reputation < Rations.MIN_REPUTATION) return Optional.empty();
        var village = ProfessionInteractions.village(level, villager);
        if (village.isEmpty()) return Optional.empty();
        var state = SaviorState.get(level);
        if (!ProfessionInteractions.ready(state, "ration", player, village.get(), now, Rations.COOLDOWN))
            return Optional.empty();
        SimpleContainer inventory = villager.getInventory();
        processWheat(inventory);
        int size = inventory.getContainerSize();
        int[] nutrition = new int[size];
        int[] counts = new int[size];
        long total = ServiceItems.nutrition(inventory, nutrition, counts);
        int budget = Rations.budget(Math.max(0L, total - 20L), reputation);
        if (budget <= 0) return Optional.empty();
        int[] selection = FoodRules.select(nutrition, counts, budget);
        if (!FoodGifts.dropSelection(level, villager, player, inventory, selection)) return Optional.empty();
        ProfessionInteractions.book(state, "ration", player, village.get(), now);
        return Optional.of("ration");
    }
    /**
     * Bakes real wheat into real bread while the surplus is below the ration cap: the stock is only
     * transformed, never invented, and the farmer keeps the result when it cannot spare anything.
     */
    private static void processWheat(SimpleContainer inventory) {
        for (int batch = 0; batch < MAX_BATCHES_PER_VISIT; batch++) {
            if (surplus(inventory) >= Rations.MAX_BUDGET) return;
            if (ServiceItems.count(inventory, Items.WHEAT) < WHEAT_PER_BREAD) return;
            if (!inventory.canAddItem(new ItemStack(Items.BREAD))) return;
            if (!ServiceItems.consume(inventory, Items.WHEAT, WHEAT_PER_BREAD)) return;
            inventory.addItem(new ItemStack(Items.BREAD));
            inventory.setChanged();
        }
    }
    /** Simulates the exact wheat processing on copies; looking at a farmer never bakes real wheat. */
    public static SimpleContainer previewInventory(SimpleContainer inventory) {
        var copy = new SimpleContainer(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            copy.setItem(slot, inventory.getItem(slot).copy());
        processWheat(copy);
        return copy;
    }
    private static long surplus(SimpleContainer inventory) {
        long total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            var food = stack.get(DataComponents.FOOD);
            if (food == null) continue;
            total += (long) Math.max(0, food.nutrition()) * stack.getCount();
        }
        return Math.max(0L, total - 20L);
    }
}
