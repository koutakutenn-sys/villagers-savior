package dev.villagerssavior.profession;

import dev.villagerssavior.FoodGifts;
import dev.villagerssavior.FoodRules;
import dev.villagerssavior.SaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Optional;
import java.util.Arrays;

/**
 * Farmer service: travel rations for trusted players who are not hungry. Everything comes from the farmer's
 * real inventory, and real wheat is baked into real bread first (3 -> 1, the vanilla recipe).
 */
public final class FarmerInteraction implements ProfessionInteraction {
    private static final int WHEAT_PER_BREAD = 3;
    private static final int MAX_BATCHES_PER_VISIT = 16;
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
        processWheat(inventory, reputation);
        int[] selection = selection(inventory, reputation);
        if (!FoodGifts.dropSelection(level, villager, player, inventory, selection)) return Optional.empty();
        ProfessionInteractions.book(state, "ration", player, village.get(), now);
        return Optional.of("ration");
    }
    /**
     * Bakes until the actual ration selection is nonempty (including reputation's share): the stock is only
     * transformed, never invented, and the farmer keeps the result when it cannot spare anything.
     */
    private static void processWheat(SimpleContainer inventory, int reputation) {
        for (int batch = 0; batch < MAX_BATCHES_PER_VISIT; batch++) {
            if (Arrays.stream(selection(inventory, reputation)).anyMatch(n -> n > 0)) return;
            if (ServiceItems.count(inventory, Items.WHEAT) < WHEAT_PER_BREAD) return;
            // Consuming the last wheat can free a slot even when the original inventory is full.
            var after = ServiceItems.copy(inventory);
            ServiceItems.consume(after, Items.WHEAT, WHEAT_PER_BREAD);
            if (ServiceItems.roomFor(after, new ItemStack(Items.BREAD)) != 1) return;
            if (!ServiceItems.consume(inventory, Items.WHEAT, WHEAT_PER_BREAD)) return;
            ServiceItems.give(inventory, new ItemStack(Items.BREAD));
        }
    }
    /** Simulates the exact wheat processing on copies; looking at a farmer never bakes real wheat. */
    public static SimpleContainer previewInventory(SimpleContainer inventory, int reputation) {
        var copy = ServiceItems.copy(inventory);
        processWheat(copy, reputation);
        return copy;
    }
    private static int[] selection(SimpleContainer inventory, int reputation) {
        int[] nutrition = new int[inventory.getContainerSize()], counts = new int[nutrition.length];
        long total = ServiceItems.nutrition(inventory, nutrition, counts);
        return FoodRules.select(nutrition, counts, Rations.budget(Math.max(0L, total - 20L), reputation));
    }
}
