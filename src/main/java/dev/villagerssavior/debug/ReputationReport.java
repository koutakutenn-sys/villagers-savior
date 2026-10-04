package dev.villagerssavior.debug;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-authoritative snapshot of a villager's real vanilla gossip towards one player. Read-only: the
 * server fills it from {@code Villager.getPlayerReputation} and the raw gossip entries and never writes back.
 */
public record ReputationReport(int entityId, String villagerUuid, String profession, int level, int total,
                               int minorPositive, int majorPositive, int trading,
                               int minorNegative, int majorNegative) implements CustomPacketPayload {
    public static final Type<ReputationReport> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("villagers_savior", "reputation_report"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReputationReport> CODEC = CustomPacketPayload.codec(
        (report, buffer) -> {
            buffer.writeVarInt(report.entityId);
            buffer.writeUtf(report.villagerUuid);
            buffer.writeUtf(report.profession);
            buffer.writeVarInt(report.level);
            buffer.writeVarInt(report.total);
            buffer.writeVarInt(report.minorPositive);
            buffer.writeVarInt(report.majorPositive);
            buffer.writeVarInt(report.trading);
            buffer.writeVarInt(report.minorNegative);
            buffer.writeVarInt(report.majorNegative);
        },
        buffer -> new ReputationReport(buffer.readVarInt(), buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt()));
    @Override public Type<ReputationReport> type() { return TYPE; }
}
