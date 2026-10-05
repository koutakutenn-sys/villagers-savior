package dev.villagerssavior.profession;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Real-inventory helpers: profession services may only move items that actually exist. */
public final class ServiceItems {
    private ServiceItems() {}
    public static int count(Container container, Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
    /** Removes exactly {@code amount}, or nothing at all when the container holds fewer. */
    public static boolean consume(Container container, Item item, int amount) {
        if (amount <= 0) return true;
        if (count(container, item) < amount) return false;
        int left = amount;
        for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
            if (stack.isEmpty()) container.setItem(slot, ItemStack.EMPTY);
        }
        container.setChanged();
        return left == 0;
    }
    /** Hands a result to a player; whatever does not fit is dropped at their feet, never lost. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
    /** Adds to a villager inventory; false when it would not fit, so callers never consume first. */
    public static boolean give(SimpleContainer container, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (roomFor(container, stack) < stack.getCount()) return false;
        container.addItem(stack.copy());
        container.setChanged();
        return true;
    }
    /** The amount of this stack that fits, using vanilla insertion on an independent copy. */
    public static int roomFor(SimpleContainer container, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return stack.getCount() - copy(container).addItem(stack.copy()).getCount();
    }
    public static SimpleContainer copy(SimpleContainer container) {
        var copy = new SimpleContainer(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++)
            copy.setItem(slot, container.getItem(slot).copy());
        return copy;
    }
    /** Nutrition of every edible stack, filling {@code nutrition} (-1 for non-food) and {@code counts}. */
    public static long nutrition(Container container, int[] nutrition, int[] counts) {
        long total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            var food = stack.get(DataComponents.FOOD);
            nutrition[slot] = food == null ? -1 : Math.max(0, food.nutrition());
            counts[slot] = stack.getCount();
            total += (long) Math.max(0, nutrition[slot]) * counts[slot];
        }
        return total;
    }
}
