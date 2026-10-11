package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** Leatherworker service: repairs leather armor with the villager's real leather, nothing else. */
public final class LeatherworkerInteraction implements ProfessionInteraction {
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        return RepairService.serve(level, villager, player, now, "service_leatherworker", RepairService.LEATHER_ARMOR);
    }
    @Override public Optional<String> serveHeld(ServerLevel level, Villager villager, ServerPlayer player,
                                                ItemStack target, long now) {
        return RepairService.serveHeld(level, villager, player, now, "service_leatherworker", RepairService.LEATHER_ARMOR, target);
    }
}
