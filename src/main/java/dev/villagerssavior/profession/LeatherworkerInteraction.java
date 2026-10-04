package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Optional;

/** Leatherworker service: repairs leather armor with the player's real leather, nothing else. */
public final class LeatherworkerInteraction implements ProfessionInteraction {
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        return RepairService.serve(level, villager, player, now, "service_leatherworker", RepairService.LEATHER_ARMOR);
    }
}
