package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Optional;
import java.util.function.Predicate;

/** Shared repair service: the villager spends its own repair stock, bounded by a daily quota. */
final class RepairService {
    static final Predicate<ItemStack> TOOLS =
        stack -> stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
    static final Predicate<ItemStack> WEAPONS =
        stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
    static final Predicate<ItemStack> ARMOR =
        stack -> stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR)
            || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR);
    static final Predicate<ItemStack> LEATHER_ARMOR =
        ARMOR.and(stack -> stack.isValidRepairItem(new ItemStack(Items.LEATHER)));
    private RepairService() {}
    static Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now,
                                  String event, Predicate<ItemStack> eligible) {
        int reputation = ProfessionInteractions.reputation(villager, player);
        if (reputation < ProfessionInteractions.MIN_REPUTATION) return Optional.empty();
        var village = ProfessionInteractions.village(level, villager);
        if (village.isEmpty()) return Optional.empty();
        var state = SaviorState.get(level);
        if (!ProfessionInteractions.ready(state, event, player, village.get(), now, ProfessionInteractions.SERVICE_WINDOW))
            return Optional.empty();
        var found = Repairs.find(player.getInventory(), villager.getInventory(), eligible);
        if (found.isEmpty()) return Optional.empty();
        var job = found.get();
        ItemStack target = job.target();
        int perUnit = Repairs.amountPerUnit(target.getMaxDamage(), reputation);
        int needed = (target.getDamageValue() + perUnit - 1) / perUnit;
        int wanted = Math.min(job.available(), needed);
        int allowed = state.dailyGrant(event + "_quota", player.getUUID(), villager.getUUID(), now, wanted,
            Repairs.dailyUnits(reputation));
        if (allowed <= 0) return Optional.empty();
        if (!ServiceItems.consume(villager.getInventory(), job.material(), allowed)) return Optional.empty();
        target.setDamageValue(Math.max(0, target.getDamageValue() - allowed * perUnit));
        ProfessionInteractions.book(state, event, player, village.get(), now);
        return Optional.of("service_repair");
    }
}
