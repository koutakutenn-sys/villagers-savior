package dev.villagerssavior;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

public record FoodRequest(int entityId, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<FoodRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("villagers_savior", "request_food"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FoodRequest> CODEC = CustomPacketPayload.codec(
        (p, b) -> { b.writeVarInt(p.entityId); b.writeBoolean(p.hand == InteractionHand.OFF_HAND); },
        b -> new FoodRequest(b.readVarInt(), b.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));
    @Override public Type<FoodRequest> type() { return TYPE; }
}
