package dev.villagerssavior.test;
import dev.villagerssavior.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import java.nio.file.*;
public final class ClientServerAudit {
    private static Villager villager;
    private static long ticks;
    private static boolean done;
    public static void tick(MinecraftServer server){
        if(done || ++ticks>4000){server.halt(false);return;}
        if(ticks%200==0)System.out.println("CLIENT_AUDIT_WAIT tick="+ticks);
    }
    public static void control(ServerPlayer p,int code){
        var level=p.level();
        if(code==99){done=true;return;}
        if(villager==null){
            level.getChunk(0,0);
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)level.setBlock(new BlockPos(x,3,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            villager=new Villager(EntityTypes.VILLAGER,level);villager.snapTo(0.5,4,0.5);villager.setNoAi(true);villager.setNoGravity(true);
            villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(),VillagerProfession.FARMER));
            level.addFreshEntity(villager);
            var at=new BlockPos(0,4,0);
            var home=level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
            level.getPoiManager().add(at,home);
            level.getPoiManager().take(t->t.equals(home),(t,pos)->pos.equals(at),at,1);
            p.resetFallDistance();p.teleportTo(1.7,4,0.5);
        }
        if(code%2==0){
            p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            p.getFoodData().setFoodLevel((code==2||code==4)?5:code==10?20:10);
            villager.setTradingPlayer(null);villager.getGossips().clear();villager.getInventory().clearContent();
            villager.getInventory().setItem(0,new ItemStack(Items.POTATO,(code==2||code==4)?1:40));
            if(code==2||code==4){villager.getInventory().setItem(1,new ItemStack(Items.BREAD));SaviorGossip.add(villager,p.getUUID(),GossipType.MAJOR_NEGATIVE,100);}
            if(code==12){
                var base=new BlockPos(3,4,1);
                level.setBlock(base,net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(),3);
                level.setBlock(base.above(),net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(),3);
                level.setBlock(base.above().east(),net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(),3);
                level.setBlock(base.above().west(),net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(),3);
                level.setBlock(base.above(2),net.minecraft.world.level.block.Blocks.PUMPKIN.defaultBlockState(),3);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));
            }
            if(code==8)p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        }
        int delivered=p.getInventory().countItem(Items.POTATO);
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(-5,0,-5,5,10,5))) if(item.getItem().is(Items.POTATO))delivered+=item.getItem().getCount();
        boolean flag=code==7?villager.getTradingPlayer()==p:Villages.identify(level,villager.blockPosition()).map(id->!SaviorState.get(level).ready("emergency",p.getUUID(),id,level.getGameTime())).orElse(false);
        int potatoes=villager.getInventory().countItem(Items.POTATO),bread=villager.getInventory().countItem(Items.BREAD);
        if(code==13){
            potatoes=level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class,new AABB(-5,0,-5,8,15,8)).size();
            var gossip=villager.getGossips().getGossipEntries().get(p.getUUID());bread=gossip==null?0:gossip.getInt(GossipType.MINOR_POSITIVE);
        }
        var reply=new AuditNet.Reply(code,villager.getId(),potatoes,bread,delivered,flag);
        System.out.println("CLIENT_AUDIT_SERVER "+reply);ServerPlayNetworking.send(p,reply);
    }
}
