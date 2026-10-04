package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Map;

/** Heat treatment services (fisherman, butcher): real raw food plus real coal, one furnace load. */
public final class Cooking {
    /** Vanilla furnace fuel: one piece of coal smelts eight items, so one coal never cooks more than eight. */
    public static final int ITEMS_PER_COAL = 8;
    private Cooking() {}
    /** Cooks up to {@code cap} real raw items, paid for with real coal; returns how many were cooked. */
    public static int cook(ServerPlayer player, Map<Item, Item> recipes, int cap) {
        if (cap <= 0) return 0;
        Inventory inventory = player.getInventory();
        int raw = 0;
        for (Item item : recipes.keySet()) raw += ServiceItems.count(inventory, item);
        if (raw <= 0) return 0;
        int coal = ServiceItems.count(inventory, Items.COAL);
        int cookable = Math.min(Math.min(raw, cap), coal * ITEMS_PER_COAL);
        if (cookable <= 0) return 0;
        int coalNeeded = (cookable + ITEMS_PER_COAL - 1) / ITEMS_PER_COAL;
        if (!ServiceItems.consume(inventory, Items.COAL, coalNeeded)) return 0;
        int left = cookable;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            Item cooked = recipes.get(stack.getItem());
            if (cooked == null) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
            ServiceItems.give(player, new ItemStack(cooked, take));
            left -= take;
        }
        return cookable - left;
    }
}
