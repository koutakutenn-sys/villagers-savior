package dev.villagerssavior.profession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/**
 * One villager profession's service. Implementations may only move resources that really exist and must
 * answer with the message key describing what happened, or nothing when they have nothing to offer now.
 */
public interface ProfessionInteraction {
    Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now);
    /**
     * Service that acts on the item the player is holding. Only the repair professions implement it, so a
     * held item never silently triggers an unrelated service.
     */
    default Optional<String> serveHeld(ServerLevel level, Villager villager, ServerPlayer player, ItemStack target,
                                       long now) {
        return Optional.empty();
    }
}
