package dev.villagerssavior.profession;

import net.minecraft.world.Container;
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
    /**
     * The eligible stack with the highest damage ratio that {@code materials} can pay to repair.
     * Equal ratios keep the first eligible inventory slot.
     * The equipment belongs to the player; the repair material comes from the villager's own stock.
     */
    public static Optional<Job> find(Inventory targets, Container materials, Predicate<ItemStack> eligible) {
        Job best = null;
        for (int slot = 0; slot < targets.getContainerSize(); slot++) {
            ItemStack target = targets.getItem(slot);
            if (target.isEmpty() || !target.isDamageableItem() || target.getDamageValue() <= 0
                || !eligible.test(target)) continue;
            Item material = null;
            int available = 0;
            for (int other = 0; other < materials.getContainerSize(); other++) {
                ItemStack candidate = materials.getItem(other);
                if (candidate.isEmpty() || !target.isValidRepairItem(candidate)) continue;
                if (material == null) material = candidate.getItem();
                if (candidate.is(material)) available += candidate.getCount();
            }
            if (material == null || available <= 0) continue;
            if (best == null || ratio(target) > ratio(best.target())) best = new Job(slot, target, material, available);
        }
        return Optional.ofNullable(best);
    }
    private static float ratio(ItemStack stack) { return (float) stack.getDamageValue() / stack.getMaxDamage(); }
}
