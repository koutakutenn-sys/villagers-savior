package dev.villagerssavior.client;

import dev.villagerssavior.FoodRequest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
    }
    public static boolean requesting() { return requestFood != null && requestFood.isDown() && ClientPlayNetworking.canSend(FoodRequest.TYPE); }
}
