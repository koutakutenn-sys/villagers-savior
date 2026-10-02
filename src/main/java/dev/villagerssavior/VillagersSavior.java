package dev.villagerssavior;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class VillagersSavior implements ModInitializer {
    @Override public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(FoodRequest.TYPE, FoodRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FoodRequest.TYPE, (payload, context) -> FoodGifts.request(context.player(), payload));
    }
}
