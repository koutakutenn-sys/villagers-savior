package dev.villagerssavior.test;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public final class AuditNet {
    public record Control(int code) implements CustomPacketPayload {
        public static final Type<Control> TYPE=new Type<>(Identifier.fromNamespaceAndPath("villagers_savior_test","control"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Control> CODEC=CustomPacketPayload.codec((p,b)->b.writeInt(p.code),b->new Control(b.readInt()));
        public Type<Control> type(){return TYPE;}
    }
    public record Reply(int code,int entity,int potatoes,int bread,int delivered,boolean flag) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(Identifier.fromNamespaceAndPath("villagers_savior_test","reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=CustomPacketPayload.codec(
            (p,b)->{b.writeInt(p.code);b.writeInt(p.entity);b.writeInt(p.potatoes);b.writeInt(p.bread);b.writeInt(p.delivered);b.writeBoolean(p.flag);},
            b->new Reply(b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readBoolean()));
        public Type<Reply> type(){return TYPE;}
    }
}
