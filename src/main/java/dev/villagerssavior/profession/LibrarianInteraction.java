package dev.villagerssavior.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Optional;

/**
 * Librarian service: knowledge only. It reads the real vanilla reputation and tells the player how this
 * villager regards them; it never hands out items and never changes gossip.
 */
public final class LibrarianInteraction implements ProfessionInteraction {
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        int reputation = villager.getPlayerReputation(player);
        player.sendSystemMessage(Component.translatable("message.villagers_savior.librarian_report",
            Component.translatable(standing(reputation)), reputation), false);
        return Optional.of("service_librarian");
    }
    /** Qualitative standing bands derived from the real reputation value. */
    public static String standing(int reputation) {
        if (reputation < 0) return "message.villagers_savior.standing.wary";
        if (reputation < ProfessionInteractions.MIN_REPUTATION) return "message.villagers_savior.standing.neutral";
        if (reputation < 75) return "message.villagers_savior.standing.trusted";
        return "message.villagers_savior.standing.honored";
    }
}
