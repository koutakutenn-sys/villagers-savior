package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Map;

/** Heat treatment services (fisherman, butcher): the villager cooks its own stock and gives the food away. */
public final class Cooking {
    /** Vanilla furnace fuel: one piece of coal smelts eight items, so one coal never cooks more than eight. */
    public static final int ITEMS_PER_COAL = 8;
    private Cooking() {}
    /**
     * Cooks up to {@code cap} of the villager's own raw items, burning the villager's own coal, and hands the
     * cooked food to the player. Returns how many items were cooked.
     */
    public static int cook(Villager villager, ServerPlayer player, Map<Item, Item> recipes, int cap) {
        if (cap <= 0) return 0;
        SimpleContainer stock = villager.getInventory();
        int raw = 0;
        for (Item item : recipes.keySet()) raw += ServiceItems.count(stock, item);
        if (raw <= 0) return 0;
        int coal = ServiceItems.count(stock, Items.COAL);
        int cookable = Math.min(Math.min(raw, cap), coal * ITEMS_PER_COAL);
        if (cookable <= 0) return 0;
        int coalNeeded = (cookable + ITEMS_PER_COAL - 1) / ITEMS_PER_COAL;
        if (!ServiceItems.consume(stock, Items.COAL, coalNeeded)) return 0;
        int left = cookable;
        for (int slot = 0; slot < stock.getContainerSize() && left > 0; slot++) {
            ItemStack stack = stock.getItem(slot);
            if (stack.isEmpty()) continue;
            Item cooked = recipes.get(stack.getItem());
            if (cooked == null) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) stock.setItem(slot, ItemStack.EMPTY);
            ServiceItems.give(player, new ItemStack(cooked, take));
            left -= take;
        }
        stock.setChanged();
        return cookable - left;
    }
}
