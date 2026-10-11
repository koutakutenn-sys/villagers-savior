package dev.villagerssavior.profession;

import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * A villager accepts the very items its own profession works with (the same resources it produces and its
 * services consume), so a cleric collects redstone, glowstone dust and lapis lazuli. Vanilla decides how
 * the item reaches the villager; this only answers "does this villager want this stack".
 */
public final class ProfessionPickup {
    private ProfessionPickup() {}

    /** True when the stack belongs to this profession's resource table and there is room up to its hard cap. */
    public static boolean accepts(Villager villager, ItemStack stack) {
        if (stack.isEmpty()) return false;
        var key = villager.getVillagerData().profession().unwrapKey().orElse(null);
        if (key == null) return false;
        for (ProfessionResources.Resource resource : ProfessionResources.of(key)) {
            if (!stack.is(resource.item())) continue;
            return ServiceItems.count(villager.getInventory(), resource.item()) + stack.getCount()
                <= resource.hardCap();
        }
        return false;
    }
}
