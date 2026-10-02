package dev.villagerssavior;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class FoodGifts {
    private FoodGifts() {}
    public static void request(ServerPlayer player, FoodRequest request) {
        ServerLevel level = (ServerLevel) player.level();
        if (!player.isAlive() || player.isSpectator() || !player.getItemInHand(request.hand()).isEmpty()) return;
        if (!(level.getEntity(request.entityId()) instanceof Villager v) || !v.isAlive() || v.isSleeping()
            || !player.isWithinEntityInteractionRange(v, 0) || !player.hasLineOfSight(v)) return;
        if (v.getTradingPlayer() != null && v.getTradingPlayer() != player) return;
        var access = (RequestThrottle) player;
        long now = level.getGameTime();
        // One second per player: bound replay/spam without imposing an extra in-game day limit.
        if (!access.savior$acceptRequest(now)) return;
        player.sendSystemMessage(Component.translatable("message.villagers_savior." + give(v, player, request.hand())), true);
    }
    public static String give(Villager villager, ServerPlayer player, InteractionHand hand) {
        var level = (ServerLevel) villager.level();
        int hunger = player.getFoodData().getFoodLevel();
        if (hunger >= 20) return "full";
        var inventory = villager.getInventory();
        int size = inventory.getContainerSize();
        int[] nutrition = new int[size], counts = new int[size];
        long total = 0;
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            var food = stack.get(DataComponents.FOOD);
            nutrition[i] = food == null ? -1 : Math.max(0, food.nutrition());
            counts[i] = stack.getCount();
            total += (long) Math.max(0, nutrition[i]) * counts[i];
        }
        int budget = FoodRules.budget(hunger, villager.getPlayerReputation(player), total);
        int[] selection = FoodRules.select(nutrition, counts, budget);
        boolean hasNormal = Arrays.stream(selection).anyMatch(n -> n > 0);
        String emergencyVillage = null;
        var state = SaviorState.get(level);
        if (!hasNormal && hunger < 6) {
            var village = Villages.identify(level, villager.blockPosition());
            if (village.isPresent() && state.ready("emergency", player.getUUID(), village.get(), level.getGameTime())) {
                int slot = FoodRules.emergencySlot(nutrition, counts);
                if (slot >= 0) { selection[slot] = 1; emergencyVillage = village.get(); }
            }
        }
        boolean dropped = false;
        for (int slot = 0; slot < size; slot++) {
            if (selection[slot] == 0) continue;
            // Spawn a copy first: denied entity spawns never destroy inventory or start cooldowns.
            ItemStack gift = inventory.getItem(slot).copyWithCount(selection[slot]);
            ItemEntity item = new ItemEntity(level, villager.getX(), villager.getEyeY() - 0.3, villager.getZ(), gift);
            item.setTarget(player.getUUID()); item.setThrower(villager); item.setPickUpDelay(10);
            var motion = player.position().subtract(villager.position()).normalize().scale(0.2).add(0, 0.2, 0);
            item.setDeltaMovement(motion);
            if (level.addFreshEntity(item)) { inventory.removeItem(slot, selection[slot]); dropped = true; }
        }
        if (!dropped) return "none";
        if (emergencyVillage != null) {
            state.cooldown("emergency", player.getUUID(), emergencyVillage, level.getGameTime());
            return "emergency";
        }
        return "gift";
    }
    public interface RequestThrottle { boolean savior$acceptRequest(long now); }
}
