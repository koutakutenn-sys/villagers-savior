package dev.villagerssavior;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/** Nitwits collect real ground items and return their complete inventory on request. */
public final class NitwitInventory {
    private NitwitInventory() {}
    public static boolean isNitwit(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.NITWIT);
    }
    public static int[] contents(Villager villager) {
        var inventory = villager.getInventory();
        int[] counts = new int[inventory.getContainerSize()];
        for (int slot = 0; slot < counts.length; slot++) counts[slot] = inventory.getItem(slot).getCount();
        return counts;
    }
    public static String giveAll(Villager villager, ServerPlayer player) {
        return FoodGifts.dropSelection(player.level(), villager, player, villager.getInventory(), contents(villager))
            ? "nitwit_items" : "nitwit_empty";
    }
}
