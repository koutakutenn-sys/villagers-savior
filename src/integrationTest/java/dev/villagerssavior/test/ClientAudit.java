package dev.villagerssavior.test;
import dev.villagerssavior.*;
import dev.villagerssavior.client.SaviorClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.EntityHitResult;
import java.nio.file.*;
import java.util.*;
public final class ClientAudit implements ClientModInitializer {
    private static int ticks,stage=-2,wait,entity;
    private static AuditNet.Reply reply;
    private static final List<String> checks=new ArrayList<>();
    public void onInitializeClient(){
        if(Boolean.getBoolean("savior.audit.hud")){HudLiveClient.initialize();return;}
        if(!Boolean.getBoolean("savior.audit.client"))return;
        ClientPlayNetworking.registerGlobalReceiver(AuditNet.Reply.TYPE,(p,c)->reply=p);
        ClientTickEvents.END_CLIENT_TICK.register(ClientAudit::tick);
    }
    private static void check(boolean value,String name){checks.add((value?"PASS ":"FAIL ")+name);System.out.println(checks.getLast());}
    private static void control(int code){reply=null;ClientPlayNetworking.send(new AuditNet.Control(code));wait=0;}
    private static void interact(Minecraft mc,boolean pressed){
        var v=(Villager)mc.level.getEntity(entity);
        SaviorClient.requestFood.setDown(pressed);
        mc.gui.setScreen(null);
        var result=mc.gameMode.interact(mc.player,v,new EntityHitResult(v,v.position()),InteractionHand.MAIN_HAND);
        if(pressed)check(result.consumesAction(),"request client consumes interaction");
        SaviorClient.requestFood.setDown(false);
    }
    private static void finish(Minecraft mc,String failure){
        if(failure!=null)checks.add("FAIL "+failure);
        try{Files.writeString(Path.of("../audit/client-result.txt"),(checks.stream().anyMatch(s->s.startsWith("FAIL"))?"FAIL":"PASS")+": "+checks.size()+" checks\n"+String.join("\n",checks)+"\n");}catch(Exception e){e.printStackTrace();}
        try{if(mc.getConnection()!=null)ClientPlayNetworking.send(new AuditNet.Control(99));}catch(Exception ignored){}
        mc.stop();stage=99;
    }
    private static void tick(Minecraft mc){
        if(stage==99)return;
        if(++ticks>3600){finish(mc,"timeout stage="+stage);return;}
        try{
            if(stage==-2){
                if(mc.gui.overlay()!=null || ticks<40)return;
                check(KeyMappingHelper.getBoundKeyOf(SaviorClient.requestFood).getValue()==86,"default key V registered");
                check(Arrays.asList(mc.options.keyMappings).contains(SaviorClient.requestFood),"key exposed in controls");
                ConnectScreen.startConnecting(new TitleScreen(),mc,new ServerAddress("127.0.0.1",Integer.getInteger("savior.audit.port",29955)),new ServerData("Savior audit","127.0.0.1:29955",ServerData.Type.OTHER),false,null);stage=-1;return;
            }
            if(mc.player==null || mc.level==null){if(stage==-1 && mc.gui.screen() instanceof DisconnectedScreen){finish(mc,"connection failed: "+mc.gui.screen().getClass().getSimpleName());}return;}
            if(stage==-1){check(ClientPlayNetworking.canSend(FoodRequest.TYPE),"server advertises request channel");control(0);stage=0;return;}
            wait++;
            if(stage%2==0){
                if(reply==null||reply.code()!=stage)return;
                entity=reply.entity();
                if(!(mc.level.getEntity(entity) instanceof Villager)||wait<30)return;
                if(stage==12){
                    mc.gui.setScreen(null);
                    mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(new net.minecraft.core.BlockPos(3,6,1)),net.minecraft.core.Direction.WEST,new net.minecraft.core.BlockPos(3,6,1),false));
                }
                else if(stage==8){ClientPlayNetworking.send(new FoodRequest(entity,InteractionHand.MAIN_HAND));}
                else {
                    if(stage==0){SaviorClient.requestFood.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(71));net.minecraft.client.KeyMapping.resetMapping();check(KeyMappingHelper.getBoundKeyOf(SaviorClient.requestFood).getValue()==71,"request key can rebind to G");}
                    interact(mc,stage!=6);
                }
                stage++;wait=0;reply=null;return;
            }
            if(wait==30)control(stage);
            if(reply==null||reply.code()!=stage)return;
            switch(stage){
                case 1 -> {check(reply.potatoes()==37,"V interaction deducts normal budget three on server");check(reply.delivered()==3,"real network request delivers exactly three potatoes");}
                case 3 -> {check(reply.potatoes()==0&&reply.bread()==1,"emergency gives lowest-nutrition one and retains bread");check(reply.flag(),"emergency records shared village cooldown");}
                case 5 -> check(reply.potatoes()==1,"same-player same-village emergency cannot repeat");
                case 7 -> {check(reply.flag(),"without V vanilla trading opens");check(mc.gui.screen()!=null&&mc.gui.screen().getClass().getSimpleName().equals("MerchantScreen"),"real merchant screen received");}
                case 9 -> check(reply.potatoes()==40,"forged network request with held item rejected by server");
                case 11 -> check(reply.potatoes()==40,"full-hunger client request receives no food");
                case 13 -> {check(reply.potatoes()==1,"live client carving actually spawns iron golem");check(reply.bread()==5,"live client carving creator receives positive gossip five (actual="+reply.bread()+")");finish(mc,null);return;}
            }
            stage++;control(stage);
        }catch(Throwable e){e.printStackTrace();finish(mc,e.toString());}
    }
}
