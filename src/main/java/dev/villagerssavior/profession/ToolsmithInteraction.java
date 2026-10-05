package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Optional;

/** Toolsmith service: repairs the player's most damaged tool with the villager's real repair material. */
public final class ToolsmithInteraction implements ProfessionInteraction {
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        return RepairService.serve(level, villager, player, now, "service_toolsmith", RepairService.TOOLS);
    }
}
