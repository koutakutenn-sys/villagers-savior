package dev.villagerssavior;

import dev.villagerssavior.debug.ReputationQuery;
import dev.villagerssavior.debug.VillagerDetails;
import dev.villagerssavior.debug.VillagerInspection;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

public final class VillagersSavior implements ModInitializer {
    private static final java.util.Map<ServerPlayer, Long> INSPECTION_TIMES = new java.util.WeakHashMap<>();
    @Override public void onInitialize() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.getGameTime() % 1200L == 0) SaviorState.get(level).prune(level.getGameTime());
        });
        dev.villagerssavior.debug.ReputationCommands.register();
        PayloadTypeRegistry.serverboundPlay().register(FoodRequest.TYPE, FoodRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FoodRequest.TYPE, (payload, context) -> FoodGifts.request(context.player(), payload));

        PayloadTypeRegistry.serverboundPlay().register(ReputationQuery.TYPE, ReputationQuery.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VillagerDetails.TYPE, VillagerDetails.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReputationQuery.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerLevel level = player.level();
            long now = level.getGameTime();
            Long last = INSPECTION_TIMES.get(player);
            if (last != null && now - last < 10) return;
            INSPECTION_TIMES.put(player, now);
            if (!player.isAlive() || player.isSpectator()) return;
            if (!(level.getEntity(payload.entityId()) instanceof Villager villager) || !villager.isAlive()) return;
            if (!player.isWithinEntityInteractionRange(villager, 0) || !player.hasLineOfSight(villager)) return;
            ServerPlayNetworking.send(player, VillagerInspection.inspect(villager, player));
        });
    }
}
