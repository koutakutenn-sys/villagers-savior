package dev.villagerssavior.debug;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A server-authored, localized offer preview. The client never infers private villager inventory. */
public record VillagerDetails(ReputationReport reputation, List<Component> offers) implements CustomPacketPayload {
    public VillagerDetails { offers = List.copyOf(offers); }
    public static final Type<VillagerDetails> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath("villagers_savior", "villager_details"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerDetails> CODEC = CustomPacketPayload.codec(
        (report, buffer) -> {
            ReputationReport.CODEC.encode(buffer, report.reputation);
            buffer.writeVarInt(report.offers.size());
            for (Component line : report.offers) ComponentSerialization.STREAM_CODEC.encode(buffer, line);
        }, buffer -> {
            var reputation = ReputationReport.CODEC.decode(buffer);
            int count = buffer.readVarInt();
            if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid villager offer count");
            List<Component> offers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) offers.add(ComponentSerialization.STREAM_CODEC.decode(buffer));
            return new VillagerDetails(reputation, offers);
        });
    @Override public Type<VillagerDetails> type() { return TYPE; }
}
