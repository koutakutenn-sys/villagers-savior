package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Optional;

/** Shared entry point for the material-conversion services. */
final class ConversionService {
    private ConversionService() {}
    /**
     * Runs one real, vanilla-conserving conversion: the player pays the full vanilla cost for every batch
     * and receives the vanilla result. Nothing is ever produced without the matching cost.
     */
    static Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now,
                                  String event, java.util.Map<net.minecraft.world.item.Item, Integer> costPerBatch,
                                  net.minecraft.world.item.Item result, int resultPerBatch) {
        int reputation = ProfessionInteractions.reputation(villager, player);
        if (reputation < ProfessionInteractions.MIN_REPUTATION) return Optional.empty();
        var village = ProfessionInteractions.village(level, villager);
        if (village.isEmpty()) return Optional.empty();
        var state = SaviorState.get(level);
        if (!ProfessionInteractions.ready(state, event, player, village.get(), now, ProfessionInteractions.SERVICE_WINDOW))
            return Optional.empty();
        var inventory = player.getInventory();
        int batches = Conversions.affordable(inventory, costPerBatch, Conversions.capFor(reputation));
        if (batches <= 0) return Optional.empty();
        if (!Conversions.pay(inventory, costPerBatch, batches)) return Optional.empty();
        ServiceItems.give(player, new net.minecraft.world.item.ItemStack(result, resultPerBatch * batches));
        ProfessionInteractions.book(state, event, player, village.get(), now);
        return Optional.of(event);
    }
}
