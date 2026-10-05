package dev.villagerssavior.debug;

import dev.villagerssavior.FoodRules;
import dev.villagerssavior.NitwitInventory;
import dev.villagerssavior.SaviorState;
import dev.villagerssavior.Villages;
import dev.villagerssavior.profession.ProfessionOffers;
import dev.villagerssavior.profession.ServiceItems;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;

/** All inspection paths are read-only: no give, serve, dailyGrant, cooldown or village-ID writes. */
public final class VillagerInspection {
    private VillagerInspection() {}
    public static VillagerDetails inspect(Villager villager, ServerPlayer player) {
        List<Component> lines = new ArrayList<>();
        if (villager.isSleeping() || (villager.getTradingPlayer() != null && villager.getTradingPlayer() != player)) {
            lines.add(text(villager.isSleeping() ? "sleeping" : "busy"));
        } else if (NitwitInventory.isNitwit(villager)) {
            int[] contents = NitwitInventory.contents(villager);
            lines.add(Arrays.stream(contents).anyMatch(count -> count > 0)
                ? text("nitwit_items", items(villager.getInventory(), contents)) : text("nitwit_empty"));
        } else {
            var positions = Villages.previewPositions(player.level(), villager.blockPosition());
            boolean foodFirst = food(villager, player, positions, lines);
            lines.addAll(ProfessionOffers.inspect(villager, player, positions, foodFirst));
        }
        return new VillagerDetails(ReputationDebug.inspect(villager, player), lines);
    }
    public static Component text(String key, Object... args) {
        return Component.translatable("hud.villagers_savior." + key, args);
    }
    /** The exact FoodRules selection used by the real gift, including emergency eligibility. */
    private static boolean food(Villager villager, ServerPlayer player, Optional<Collection<String>> positions,
                                List<Component> lines) {
        int hunger = player.getFoodData().getFoodLevel();
        if (hunger >= 20) { lines.add(text("food_full")); return false; }
        var inventory = villager.getInventory();
        int[] nutrition = new int[inventory.getContainerSize()], counts = new int[nutrition.length];
        long total = ServiceItems.nutrition(inventory, nutrition, counts);
        int budget = FoodRules.budget(hunger, villager.getPlayerReputation(player), total);
        int[] selection = FoodRules.select(nutrition, counts, budget);
        if (Arrays.stream(selection).anyMatch(n -> n > 0)) {
            lines.add(text("food_offer", items(inventory, selection))); return true;
        }
        if (hunger < 6 && positions.isPresent()) {
            int slot = FoodRules.emergencySlot(nutrition, counts);
            if (slot >= 0) {
                long remaining = SaviorState.get(player.level()).remainingAt("emergency", player.getUUID(),
                    positions.get(), player.level().getGameTime(), SaviorState.EMERGENCY);
                if (remaining == 0) {
                    selection[slot] = 1;
                    lines.add(text("food_emergency", items(inventory, selection))); return true;
                }
                lines.add(text("food_cooldown", seconds(remaining))); return false;
            }
        }
        lines.add(text("food_none")); return false;
    }
    public static long seconds(long ticks) { return (ticks + 19) / 20; }
    public static Component items(Container inventory, int[] selection) {
        var result = Component.empty();
        for (int slot = 0; slot < selection.length; slot++) {
            if (selection[slot] <= 0) continue;
            if (!result.getSiblings().isEmpty()) result.append(Component.literal("、"));
            result.append(text("item_count", inventory.getItem(slot).getHoverName(), selection[slot]));
        }
        return result;
    }
}
