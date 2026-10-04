package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Optional;

/** Armorer service: repairs the player's most damaged armor piece with their own real material. */
public final class ArmorerInteraction implements ProfessionInteraction {
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        return RepairService.serve(level, villager, player, now, "service_armorer", RepairService.ARMOR);
    }
}
