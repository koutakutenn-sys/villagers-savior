package dev.villagerssavior.client;

import dev.villagerssavior.FoodRequest;
import dev.villagerssavior.debug.VillagerDetails;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class SaviorClient implements ClientModInitializer {
    public static KeyMapping requestFood;
    @Override public void onInitializeClient() {
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("villagers_savior", "requests"));
        requestFood = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.villagers_savior.request_food",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
        ClientPlayNetworking.registerGlobalReceiver(VillagerDetails.TYPE,
            (report, context) -> context.client().execute(() -> VillagerHud.accept(context.client(), report)));
        ClientTickEvents.END_CLIENT_TICK.register(VillagerHud::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VillagerHud.clear());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("villagers_savior", "villager_details"), VillagerHud::render);
    }
    public static boolean requesting() { return requestFood != null && requestFood.isDown() && ClientPlayNetworking.canSend(FoodRequest.TYPE); }
}
