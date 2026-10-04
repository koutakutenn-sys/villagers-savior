package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ColorCollection;
import java.util.Optional;

/** Shepherd service: the vanilla carpet recipe (2 wool -> 3 carpets) for the player's real, own wool. */
public final class ShepherdInteraction implements ProfessionInteraction {
    private static final int WOOL_PER_BATCH = 2;
    private static final int CARPETS_PER_BATCH = 3;
    @Override public Optional<String> serve(ServerLevel level, Villager villager, ServerPlayer player, long now) {
        int reputation = ProfessionInteractions.reputation(villager, player);
        if (reputation < ProfessionInteractions.MIN_REPUTATION) return Optional.empty();
        var village = ProfessionInteractions.village(level, villager);
        if (village.isEmpty()) return Optional.empty();
        var state = SaviorState.get(level);
        if (!ProfessionInteractions.ready(state, "service_shepherd", player, village.get(), now,
            ProfessionInteractions.SERVICE_WINDOW)) return Optional.empty();
        int batches = weave(villager, player, Conversions.capFor(reputation));
        if (batches <= 0) return Optional.empty();
        ProfessionInteractions.book(state, "service_shepherd", player, village.get(), now);
        return Optional.of("service_shepherd");
    }
    /** Weaves each colour the villager really owns, never more than the cap, always at the vanilla ratio. */
    private static int weave(Villager villager, ServerPlayer player, int cap) {
        var inventory = villager.getInventory();
        int[] batches = {0};
        ColorCollection.zipApply(Items.WOOL, Items.CARPET, (wool, carpet) -> {
            if (wool == null || carpet == null || batches[0] >= cap) return;
            int possible = Math.min(ServiceItems.count(inventory, wool) / WOOL_PER_BATCH, cap - batches[0]);
            if (possible <= 0) return;
            if (!ServiceItems.consume(inventory, wool, possible * WOOL_PER_BATCH)) return;
            ServiceItems.give(player, new ItemStack(carpet, possible * CARPETS_PER_BATCH));
            batches[0] += possible;
        });
        return batches[0];
    }
    /** Exposed for tests: which carpet a given wool becomes. */
    static Item carpetFor(Item wool) {
        Item[] result = {null};
        ColorCollection.zipApply(Items.WOOL, Items.CARPET, (candidateWool, carpet) -> {
            if (candidateWool == wool) result[0] = carpet;
        });
        return result[0];
    }
}
