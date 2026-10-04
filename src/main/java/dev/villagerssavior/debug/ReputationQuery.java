package dev.villagerssavior.debug;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client request for the read-only reputation view of one villager. Carries no data besides the target. */
public record ReputationQuery(int entityId) implements CustomPacketPayload {
    public static final Type<ReputationQuery> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("villagers_savior", "villager_details_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReputationQuery> CODEC = CustomPacketPayload.codec(
        (payload, buffer) -> buffer.writeVarInt(payload.entityId),
        buffer -> new ReputationQuery(buffer.readVarInt()));
    @Override public Type<ReputationQuery> type() { return TYPE; }
}
