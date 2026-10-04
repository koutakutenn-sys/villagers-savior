package dev.villagerssavior.profession;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import java.util.function.Predicate;

/** Vanilla-anvil flavoured repair rules, limited by real materials and a daily quota. */
public final class Repairs {
    /** Trusted players get a small bonus on top of the vanilla quarter of max durability per material. */
    public static final int BASE_UNITS = 2;
    private Repairs() {}
    /** Repair units a villager is willing to spend on one player per in-game day. */
    public static int dailyUnits(int reputation) {
        int units = BASE_UNITS;
        if (reputation >= 50) units++;
        if (reputation >= 90) units++;
        return units;
    }
    /** Vanilla anvil semantics: one material unit repairs a quarter of max durability. */
    public static int amountPerUnit(int maxDamage, int reputation) {
        int percent = reputation >= 75 ? 35 : 25;
        return Math.max(1, maxDamage * percent / 100);
    }
    public record Job(int slot, ItemStack target, Item material, int available) {}
    /** The most damaged eligible stack the player can actually pay to repair, if any. */
    public static Optional<Job> find(Inventory inventory, Predicate<ItemStack> eligible) {
        Job best = null;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack target = inventory.getItem(slot);
            if (target.isEmpty() || !target.isDamageableItem() || target.getDamageValue() <= 0
                || !eligible.test(target)) continue;
            Item material = null;
            int available = 0;
            for (int other = 0; other < inventory.getContainerSize(); other++) {
                if (other == slot) continue;
                ItemStack candidate = inventory.getItem(other);
                if (candidate.isEmpty() || !target.isValidRepairItem(candidate)) continue;
                if (material == null) material = candidate.getItem();
                if (candidate.is(material)) available += candidate.getCount();
            }
            if (material == null || available <= 0) continue;
            if (best == null || ratio(target) < ratio(best.target())) best = new Job(slot, target, material, available);
        }
        return Optional.ofNullable(best);
    }
    private static float ratio(ItemStack stack) { return (float) stack.getDamageValue() / stack.getMaxDamage(); }
}
