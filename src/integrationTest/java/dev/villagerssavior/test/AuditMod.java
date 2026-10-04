package dev.villagerssavior.test;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
public final class AuditMod implements ModInitializer {
    public void onInitialize(){
        PayloadTypeRegistry.serverboundPlay().register(AuditNet.Control.TYPE,AuditNet.Control.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AuditNet.Reply.TYPE,AuditNet.Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AuditNet.Control.TYPE,(p,c)->{
            if (Boolean.getBoolean("savior.audit.hud")) HudLiveServer.control(c.player(),p.code());
            else ClientServerAudit.control(c.player(),p.code());
        });
    }
}
