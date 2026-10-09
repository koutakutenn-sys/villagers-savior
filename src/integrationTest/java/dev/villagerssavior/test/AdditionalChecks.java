package dev.villagerssavior.test;
import dev.villagerssavior.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.BiConsumer;
public final class AdditionalChecks {
    public static int run(MinecraftServer server,BiConsumer<Boolean,String> check){
        var level=server.overworld(); var at=new BlockPos(1024,4,0);
        for(int x=62;x<=68;x++)for(int z=-2;z<=2;z++)level.getChunk(x,z);
        var p=player(server,level);p.snapTo(1025,4,0);
        // 20 blocks away: still inside every reward scope (village bounds and the 24 block fallback) but
        // outside the 12 block doubling radius, so these checks assert attribution, not the new multiplier.
        var v=new Villager(EntityTypes.VILLAGER,level);v.snapTo(1044,4,0);level.addFreshEntity(v);
        var home=level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
        level.getPoiManager().add(at,home);level.getPoiManager().take(t->t.equals(home),(t,pos)->pos.equals(at),at,1);level.getPoiManager().tick(()->true);
        var z=new Zombie(level);z.snapTo(1024,4,0);z.setHealth(1);z.hurtServer(level,level.damageSources().playerAttack(p),2);
        check.accept(value(v,p,GossipType.MINOR_POSITIVE)==1,"lethal actual hurtServer reaches death hook");
        var arrow=new Arrow(EntityTypes.ARROW,level);arrow.setOwner(p);
        z=new Zombie(level);z.snapTo(1024,4,0);z.setHealth(1);z.hurtServer(level,level.damageSources().arrow(arrow,p),2);
        check.accept(value(v,p,GossipType.MINOR_POSITIVE)==2,"player-owned arrow death credited");
        var ravager=new Ravager(EntityTypes.RAVAGER,level);ravager.snapTo(1024,4,0);ravager.die(level.damageSources().playerAttack(p));
        check.accept(value(v,p,GossipType.MINOR_POSITIVE)==5,"ravager threat three clips combined daily cap");
        var animal=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);animal.snapTo(1024,4,0);animal.die(level.damageSources().playerAttack(p));
        check.accept(value(v,p,GossipType.MINOR_POSITIVE)==5,"passive animal kill gives no protector reward");
        v.getGossips().clear();SaviorGossip.add(v,p.getUUID(),GossipType.MINOR_POSITIVE,20);
        var sources=List.of(level.damageSources().lava(),level.damageSources().inWall(),level.damageSources().fall(),level.damageSources().cactus());
        for(int i=0;i<sources.size();i++){
            var g=new IronGolem(EntityTypes.IRON_GOLEM,level);g.snapTo(1024,4,0);g.setLastHurtByPlayer(p,100);g.die(sources.get(i));
            check.accept(value(v,p,GossipType.MINOR_POSITIVE)==20 && value(v,p,GossipType.MINOR_NEGATIVE)==0,"prior-player-hit environmental golem death no penalty " + i);
        }
        var g=new IronGolem(EntityTypes.IRON_GOLEM,level);g.snapTo(2000,4,0);g.die(level.damageSources().playerAttack(p));
        check.accept(value(v,p,GossipType.MINOR_POSITIVE)==20,"outside village golem cannot penalize distant villagers");
        v.getGossips().clear();
        var near=new Villager(EntityTypes.VILLAGER,level);near.snapTo(1056,4,0);level.addFreshEntity(near);
        var far=new Villager(EntityTypes.VILLAGER,level);far.snapTo(1056.1,4,0);level.addFreshEntity(far);
        g=new IronGolem(EntityTypes.IRON_GOLEM,level);g.snapTo(1024,4,0);g.setPlayerCreated(true);level.addFreshEntity(g);
        for(int i=0;i<4;i++){var other=new IronGolem(EntityTypes.IRON_GOLEM,level);other.snapTo(1024+i,4,3);level.addFreshEntity(other);}
        SaviorEvents.constructed(g,p);
        check.accept(value(v,p,GossipType.MINOR_POSITIVE)==5,"exactly five existing golems allows construction reward");
        check.accept(value(near,p,GossipType.MINOR_POSITIVE)==5 && value(far,p,GossipType.MINOR_POSITIVE)==0,"construction radius 32 exact sphere");
        var sixth=new IronGolem(EntityTypes.IRON_GOLEM,level);sixth.snapTo(1024,4,2);level.addFreshEntity(sixth);
        SaviorEvents.constructed(g,p);
        check.accept(value(v,p,GossipType.MINOR_POSITIVE)==5,"sixth golem blocks construction reward");
        // New isolated area for the real shearing path and no-player spawn.
        var u=new Villager(EntityTypes.VILLAGER,level);u.snapTo(1280,4,0);level.getChunk(80,0);level.addFreshEntity(u);p.snapTo(1281,4,0);
        var base=new BlockPos(1280,4,0);pattern(level,base);level.setBlock(base.above(2),Blocks.PUMPKIN.defaultBlockState(),3);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));
        int beforeCarve=level.getEntitiesOfClass(IronGolem.class,new AABB(1270,0,-10,1295,15,10)).size();
        p.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var carved=p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(base.above(2)),Direction.NORTH,base.above(2),false));
        int afterCarve=level.getEntitiesOfClass(IronGolem.class,new AABB(1270,0,-10,1295,15,10)).size();
        check.accept(carved.consumesAction()&&afterCarve==beforeCarve+1,"real game-mode carving actually spawns one iron golem");
        System.out.println("AUDIT_CARVING before="+beforeCarve+" after="+afterCarve+" actualPositive="+value(u,p,GossipType.MINOR_POSITIVE));
        check.accept(value(u,p,GossipType.MINOR_POSITIVE)==5,"actual shearing pumpkin path rewards creator");
        int beforeUnattributed=value(u,p,GossipType.MINOR_POSITIVE);
        var base2=base.east(4);pattern(level,base2);level.setBlock(base2.above(2),Blocks.CARVED_PUMPKIN.defaultBlockState(),3);
        check.accept(value(u,p,GossipType.MINOR_POSITIVE)==beforeUnattributed,"unattributed pumpkin spawn never credits nearby player");
        // Emergency threshold, budget miss and successful week expiry on real inventories.
        p.snapTo(1025,4,0);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);v.getGossips().clear();v.getInventory().clearContent();
        v.getInventory().setItem(0,new ItemStack(Items.BREAD));p.getFoodData().setFoodLevel(6);
        check.accept(FoodGifts.give(v,p,InteractionHand.MAIN_HAND).equals("none")&&v.getInventory().getItem(0).getCount()==1,"H equals six excludes emergency");
        p.getFoodData().setFoodLevel(5);
        check.accept(FoodGifts.give(v,p,InteractionHand.MAIN_HAND).equals("emergency")&&v.getInventory().getItem(0).isEmpty(),"H five gives last bread ignoring reserve");
        var village=Villages.identify(level,at).orElseThrow();var state=SaviorState.get(level);
        state.cooldown("emergency",p.getUUID(),village,level.getGameTime()-168000);
        v.getInventory().setItem(0,new ItemStack(Items.BREAD,5));
        check.accept(FoodRules.budget(5,0,25)==1,"normal budget below single food nutrition");
        check.accept(FoodGifts.give(v,p,InteractionHand.MAIN_HAND).equals("emergency")&&v.getInventory().getItem(0).getCount()==4,"week expiry permits fallback when normal knapsack empty");
        p.getFoodData().setFoodLevel(0);v.getInventory().clearContent();v.getInventory().setItem(0,new ItemStack(Items.POTATO,20));
        check.accept(FoodGifts.give(v,p,InteractionHand.MAIN_HAND).equals("none")&&v.getInventory().getItem(0).getCount()==20,"cooldown blocks last-reserve drain");
        var emptyPlayer=player(server,level);emptyPlayer.getFoodData().setFoodLevel(0);
        v.getInventory().clearContent();v.getInventory().setItem(0,new ItemStack(Items.STICK,64));
        check.accept(FoodGifts.give(v,emptyPlayer,InteractionHand.MAIN_HAND).equals("none"),"nonfood never gifted as emergency");
        check.accept(state.ready("emergency",emptyPlayer.getUUID(),village,level.getGameTime()),"nonfood failure does not consume cooldown");
        // Actual 32/64 block spheres and week expiry on the live world-owned state.
        var repairPlayer=player(server,level);repairPlayer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,2));
        var repairGolem=new RepairGolem(level);repairGolem.snapTo(1024,4,0);repairGolem.setHealth(99);repairGolem.repair(repairPlayer);
        check.accept(repairGolem.getHealth()==repairGolem.getMaxHealth()&&repairPlayer.getMainHandItem().getCount()==1,"partial actual repair heals and consumes one ingot");
        check.accept(value(near,repairPlayer,GossipType.MINOR_POSITIVE)==1&&value(far,repairPlayer,GossipType.MINOR_POSITIVE)==0,"repair radius 32 exact sphere");
        var raidEdge=new Villager(EntityTypes.VILLAGER,level);raidEdge.snapTo(1088.5,4.5,0.5);level.addFreshEntity(raidEdge);
        var raidOutside=new Villager(EntityTypes.VILLAGER,level);raidOutside.snapTo(1088.6,4.5,0.5);level.addFreshEntity(raidOutside);
        var defender=player(server,level);
        SaviorEvents.raidWon(level,at,Set.of(defender.getUUID()));
        check.accept(value(v,defender,GossipType.MAJOR_POSITIVE)==2,"live raid award writes major positive two");
        check.accept(value(raidEdge,defender,GossipType.MAJOR_POSITIVE)==2&&value(raidOutside,defender,GossipType.MAJOR_POSITIVE)==0,"raid radius 64 exact sphere");
        state.cooldown("raid",defender.getUUID(),village,level.getGameTime()-167999);
        SaviorEvents.raidWon(level,at,Set.of(defender.getUUID()));
        check.accept(value(v,defender,GossipType.MAJOR_POSITIVE)==2,"raid at week minus one tick still blocked");
        state.cooldown("raid",defender.getUUID(),village,level.getGameTime()-168000);
        SaviorEvents.raidWon(level,at,Set.of(defender.getUUID()));
        check.accept(value(v,defender,GossipType.MAJOR_POSITIVE)==4,"raid at exact week expiry rewards again");
        var north=new BlockPos(2048,4,0);var south=new BlockPos(2304,4,0);
        level.getPoiManager().add(north,home);level.getPoiManager().add(south,home);
        String villageA=Villages.identify(level,north).orElseThrow(),villageB=Villages.identify(level,south).orElseThrow();
        check.accept(!villageA.equals(villageB),"separate actual POI clusters identify separate villages");
        state.cooldown("emergency",defender.getUUID(),villageA,level.getGameTime());
        check.accept(state.ready("emergency",defender.getUUID(),villageB,level.getGameTime()),"separate actual village has independent relief cooldown");
        for(int x=2112;x<=2240;x+=64)level.getPoiManager().add(new BlockPos(x,4,0),home);
        String mergedActual=Villages.identify(level,south).orElseThrow();
        check.accept(!state.ready("emergency",defender.getUUID(),mergedActual,level.getGameTime()),"actual POI bridge merge retains existing cooldown");
        var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);
        check.accept(nether!=null&&SaviorState.get(nether)!=state&&SaviorState.get(nether).ready("emergency",defender.getUUID(),villageA,nether.getGameTime()),"dimension SavedData and cooldowns remain independent");
        return 0;
    }
    private static final class RepairGolem extends IronGolem {
        RepairGolem(ServerLevel l){super(EntityTypes.IRON_GOLEM,l);}
        void repair(ServerPlayer p){super.mobInteract(p,InteractionHand.MAIN_HAND);}
    }
    private static void pattern(ServerLevel l,BlockPos p){l.setBlock(p,Blocks.IRON_BLOCK.defaultBlockState(),3);l.setBlock(p.above(),Blocks.IRON_BLOCK.defaultBlockState(),3);l.setBlock(p.above().east(),Blocks.IRON_BLOCK.defaultBlockState(),3);l.setBlock(p.above().west(),Blocks.IRON_BLOCK.defaultBlockState(),3);}
    private static int value(Villager v,ServerPlayer p,GossipType t){var e=v.getGossips().getGossipEntries().get(p.getUUID());return e==null?0:e.getInt(t);}
    private static ServerPlayer player(MinecraftServer s,ServerLevel l){var profile=new GameProfile(UUID.randomUUID(),"AuditBoundary");var p=new ServerPlayer(s,l,profile,ClientInformation.createDefault());p.connection=new ServerGamePacketListenerImpl(s,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(profile,false));return p;}
}
