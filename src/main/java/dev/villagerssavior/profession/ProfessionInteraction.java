package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Optional;

/**
 * One villager profession's service. Implementations may only move resources that really exist and must
 * answer with the message key describing what happened, or nothing when they have nothing to offer now.
 */
public interface ProfessionInteraction {
    Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now);
}
