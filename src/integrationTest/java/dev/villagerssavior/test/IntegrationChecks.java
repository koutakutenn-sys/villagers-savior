package dev.villagerssavior.test;

import dev.villagerssavior.*;
import dev.villagerssavior.test.mixin.RaidAccess;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.gossip.*;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

public final class IntegrationChecks {
    private static int checks;
    private static final List<String> results = new ArrayList<>();
    private static void check(boolean value, String name) {
        checks++;
        results.add((value ? "PASS " : "FAIL ") + name);
    }
    public static void run(MinecraftServer server) {
        if (!System.getProperty("audit.restart","none").equals("none")) { RestartAudit.run(server); return; }
        boolean pass = false;
        try {
            checks += FoodRulesChecks.run();
            results.add("PASS 35456 numerical food-rule checks");
            int professionRules = ProfessionChecks.run();
            checks += professionRules;
            results.add("PASS " + professionRules + " profession rule checks");
            var level = server.overworld();
            level.getChunk(0,0); level.getChunk(1,0); level.getChunk(2,0);
            var player = player(server, level);
            player.snapTo(1, 4, 0);
            var v = villager(level,0,4,0);
            var edge = villager(level,24,4,0);
            var outside = villager(level,24.1,4,0);
            var damage = level.damageSources().playerAttack(player);
            Zombie z = new Zombie(level); z.snapTo(0,4,0); z.die(damage);
            check(value(v,player,GossipType.MINOR_POSITIVE)==1,"death hook preserves new +1 gossip");
            check(value(edge,player,GossipType.MINOR_POSITIVE)==1,"24 block boundary included");
            check(value(outside,player,GossipType.MINOR_POSITIVE)==0,"outside sphere excluded");
            z.die(damage);
            check(value(v,player,GossipType.MINOR_POSITIVE)==1,"duplicate die call earns once");
            for (int i=0;i<4;i++) { var c = new Creeper(EntityTypes.CREEPER,level); c.snapTo(0,4,0); c.die(damage); }
            check(value(v,player,GossipType.MINOR_POSITIVE)==5,"kill awards clip to five per pair/day");
            var env = new Zombie(level); env.snapTo(0,4,0); env.die(level.damageSources().lava());
            check(value(v,player,GossipType.MINOR_POSITIVE)==5,"environment kill gives no player credit");
            check(SaviorEvents.threat(new IronGolem(EntityTypes.IRON_GOLEM,level))==1,"default threat weight");
            check(SaviorEvents.threat(new Creeper(EntityTypes.CREEPER,level))==2,"creeper threat weight");
            var ledger = new SaviorState(); UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
            check(ledger.dailyGrant("kill",a,b,0,3,5)==3,"first daily grant");
            check(ledger.dailyGrant("kill",a,b,1,3,5)==2,"daily partial grant");
            check(ledger.dailyGrant("kill",a,b,23999,3,5)==0,"daily cap maintained");
            check(ledger.dailyGrant("kill",a,b,24000,3,5)==3,"new day resets daily cap");
            check(ledger.dailyGrant("kill",a,c,24000,3,5)==3,"different villager independent");
            check(ledger.dailyGrant("repair",a,b,24000,3,3)==3,"different event independent");
            for(int n=1;n<=6;n++) check(ledger.golemKill(a,b,0)==n,"rolling golem kill count " + n);
            check(ledger.golemKill(a,b,167999)==7,"week minus one retains history");
            check(ledger.golemKill(a,b,168000)==2,"week boundary expires old kills");
            String village1=ledger.village(List.of("a","b")),village2=ledger.village(List.of("c"));
            check(!village1.equals(village2),"separate villages distinct");
            ledger.cooldown("emergency",a,village1,100);
            check(!ledger.ready("emergency",a,village1,48099),"two day cooldown lower boundary");
            check(ledger.ready("emergency",a,village1,48100),"two day cooldown upper boundary");
            ledger.cooldown("raid",a,village1,100);
            check(!ledger.ready("raid",a,village1,168099),"raid cooldown keeps the seven day window");
            check(ledger.ready("raid",a,village1,168100),"raid cooldown seven day upper boundary");
            check(ledger.ready("emergency",c,village1,100),"different player independent cooldown");
            check(ledger.ready("emergency",a,village2,100),"different village independent cooldown");
            String merged=ledger.village(List.of("a","c"));
            check(!ledger.ready("emergency",a,merged,100),"merged villages retain existing cooldown");
            var encoded=SaviorState.CODEC.encodeStart(JsonOps.INSTANCE,ledger).getOrThrow();
            var restored=SaviorState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
            check(!restored.ready("emergency",a,merged,100),"serialized cooldown survives reload");
            check(restored.dailyGrant("repair",a,b,24000,1,3)==0,"serialized daily cap survives reload");
            check(restored.golemKill(a,b,168001)==3,"serialized rolling history survives reload");
            checks += PersistenceCheck.run(level.registryAccess());
            results.add("PASS 4 persistence disk round-trip checks");
            GossipContainer gossip=v.getGossips();
            SaviorGossip.add(v,c,GossipType.MAJOR_NEGATIVE,1);
            var gossipJson=GossipContainer.CODEC.encodeStart(JsonOps.INSTANCE,gossip).getOrThrow();
            var loadedGossip=GossipContainer.CODEC.parse(JsonOps.INSTANCE,gossipJson).getOrThrow();
            check(loadedGossip.getGossipEntries().get(c).getInt(GossipType.MAJOR_NEGATIVE)==1,"single-point real gossip survives serialization");
            v.getGossips().clear(); edge.getGossips().clear(); outside.getGossips().clear();
            var golem=new TestGolem(level); golem.snapTo(0,4,0); golem.setHealth(10);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,8));
            for(int i=0;i<4;i++){ golem.setHealth(10); golem.repair(player); }
            check(value(v,player,GossipType.MINOR_POSITIVE)==3,"actual repair hook daily cap three");
            golem.setHealth(golem.getMaxHealth()); golem.repair(player);
            check(value(v,player,GossipType.MINOR_POSITIVE)==3,"full-health interaction earns no repair credit");
            var freshPlayer=player(server,level);
            golem.repair(freshPlayer);
            check(value(v,freshPlayer,GossipType.MINOR_POSITIVE)==0,"no iron ingot means no repair");
            var poiPos=new BlockPos(0,4,0);
            var home=level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
            level.getPoiManager().add(poiPos,home);
            level.getPoiManager().take(t->t.equals(home),(t,pos)->pos.equals(poiPos),poiPos,1);
            level.getPoiManager().tick(()->true);
            check(level.isVillage(poiPos),"occupied home creates vanilla village");
            var identified=Villages.identify(level,poiPos);
            check(identified.isPresent(),"POI village identity exists");
            check(identified.equals(Villages.identify(level,new BlockPos(20,4,0))),"nearby villagers share identity");
            v.getGossips().clear();
            SaviorGossip.add(v,player.getUUID(),GossipType.MINOR_POSITIVE,11);
            SaviorGossip.subtract(v,player.getUUID(),GossipType.MINOR_POSITIVE,10);
            check(value(v,player,GossipType.MINOR_POSITIVE)==1,"subtract preserves exact positive remainder one");
            v.getGossips().clear();
            SaviorGossip.add(v,player.getUUID(),GossipType.MINOR_POSITIVE,20);
            var environmental = new IronGolem(EntityTypes.IRON_GOLEM,level); environmental.snapTo(0,4,0); environmental.die(level.damageSources().lava());
            check(value(v,player,GossipType.MINOR_POSITIVE)==20,"environment golem death no penalty");
            var built = new IronGolem(EntityTypes.IRON_GOLEM,level); built.snapTo(0,4,0); built.setPlayerCreated(true); built.die(damage);
            check(value(v,player,GossipType.MINOR_POSITIVE)==15,"player-created golem death subtracts five");
            check(value(v,player,GossipType.MINOR_NEGATIVE)==0,"player-created golem death adds no negative gossip");
            int expectedNegative=0;
            for(int n=1;n<=6;n++) {
                var killed=new IronGolem(EntityTypes.IRON_GOLEM,level); killed.snapTo(0,4,0); killed.die(damage);
                expectedNegative=Math.min(GossipType.MINOR_NEGATIVE.max,expectedNegative+10+5*(n-1));
                check(value(v,player,GossipType.MINOR_NEGATIVE)==expectedNegative,"escalating golem penalty " + n);
            }
            check(value(v,player,GossipType.MINOR_POSITIVE)==0,"golem kills remove positive gossip to zero");
            check(value(v,player,GossipType.MAJOR_NEGATIVE)==1,"sixth kill adds major negative one");
            // Food always comes out of the real inventory; emergency respects the shared village state.
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); player.snapTo(1,4,0);
            v.getGossips().clear(); v.getInventory().clearContent();
            v.getInventory().setItem(0,new ItemStack(Items.POTATO,40)); player.getFoodData().setFoodLevel(10);
            check(FoodGifts.give(v,player,InteractionHand.MAIN_HAND).equals("gift"),"normal real-stock gift");
            check(v.getInventory().getItem(0).getCount()==37,"normal gift deducts exact budget three");
            player.getFoodData().setFoodLevel(20);
            check(FoodGifts.give(v,player,InteractionHand.MAIN_HAND).equals("full"),"full player receives no gift");
            check(v.getInventory().getItem(0).getCount()==37,"full request leaves stock intact");
            v.getInventory().clearContent(); v.getInventory().setItem(0,new ItemStack(Items.BREAD,1));
            v.getInventory().setItem(1,new ItemStack(Items.POTATO,1));
            SaviorGossip.add(v,player.getUUID(),GossipType.MAJOR_NEGATIVE,100); player.getFoodData().setFoodLevel(5);
            check(FoodGifts.give(v,player,InteractionHand.MAIN_HAND).equals("emergency"),"emergency ignores negative reputation and reserve");
            check(v.getInventory().getItem(1).isEmpty() && v.getInventory().getItem(0).getCount()==1,"emergency gives lowest nutrition one");
            edge.getInventory().clearContent(); edge.getInventory().setItem(0,new ItemStack(Items.POTATO));
            check(FoodGifts.give(edge,player,InteractionHand.MAIN_HAND).equals("none"),"changing villager cannot bypass emergency cooldown");
            freshPlayer.getFoodData().setFoodLevel(5);
            check(FoodGifts.give(edge,freshPlayer,InteractionHand.MAIN_HAND).equals("emergency"),"other player can receive emergency independently");
            var state=SaviorState.get(level);
            var noFoodPlayer=player(server,level); noFoodPlayer.getFoodData().setFoodLevel(0); edge.getInventory().clearContent();
            check(FoodGifts.give(edge,noFoodPlayer,InteractionHand.MAIN_HAND).equals("none"),"empty inventory emergency fails");
            check(state.ready("emergency",noFoodPlayer.getUUID(),identified.orElseThrow(),level.getGameTime()),"failed emergency never starts cooldown");
            player.getFoodData().setFoodLevel(10); v.getInventory().clearContent(); v.getInventory().setItem(0,new ItemStack(Items.POTATO,40));
            v.getGossips().clear(); int stock=v.getInventory().getItem(0).getCount();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            FoodGifts.request(player,new FoodRequest(v.getId(),InteractionHand.MAIN_HAND));
            check(v.getInventory().getItem(0).getCount()==stock,"server rejects nonempty hand");
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); player.snapTo(1000,4,0);
            FoodGifts.request(player,new FoodRequest(v.getId(),InteractionHand.MAIN_HAND));
            check(v.getInventory().getItem(0).getCount()==stock,"server rejects distant target");
            check(((FoodGifts.RequestThrottle)player).savior$acceptRequest(1000),"first throttle request accepted");
            check(!((FoodGifts.RequestThrottle)player).savior$acceptRequest(1019),"request replay throttled");
            check(((FoodGifts.RequestThrottle)player).savior$acceptRequest(1020),"one second throttle boundary");
            // The actual pumpkin placement path supplies the creator, not the nearest observer.
            player.snapTo(5,4,5); player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.CARVED_PUMPKIN));
            v.getGossips().clear(); SaviorGossip.add(v,player.getUUID(),GossipType.MINOR_NEGATIVE,20);
            BlockPos base=new BlockPos(5,4,5); pattern(level,base);
            var hit=new BlockHitResult(Vec3.atCenterOf(base.above()),Direction.UP,base.above(),false);
            ((BlockItem)Items.CARVED_PUMPKIN).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,player.getMainHandItem(),hit));
            check(value(v,player,GossipType.MINOR_POSITIVE)==5,"actual construction path credits creator");
            check(value(v,player,GossipType.MINOR_NEGATIVE)==20,"construction never reduces negative gossip");
            check(value(v,freshPlayer,GossipType.MINOR_POSITIVE)==0,"nearby observer receives no construction credit");
            check(CreatorContext.current()==null,"construction context cleaned up");
            // Regression: carving a pumpkin in place with shears can complete a golem pattern as well.
            v.getGossips().clear();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));
            BlockPos carveBase=new BlockPos(5,4,12); pattern(level,carveBase);
            BlockPos carvePumpkin=carveBase.above().above();
            level.setBlock(carvePumpkin,Blocks.PUMPKIN.defaultBlockState(),3);
            var carveHit=new BlockHitResult(Vec3.atCenterOf(carvePumpkin),Direction.UP,carvePumpkin,false);
            level.getBlockState(carvePumpkin).useItemOn(player.getMainHandItem(),level,player,InteractionHand.MAIN_HAND,carveHit);
            check(value(v,player,GossipType.MINOR_POSITIVE)==5,"carving a pumpkin in place credits the carver");
            check(value(v,freshPlayer,GossipType.MINOR_POSITIVE)==0,"carving path does not credit observers");
            check(CreatorContext.current()==null,"carving context cleaned up");
            v.getGossips().clear(); SaviorGossip.add(v,player.getUUID(),GossipType.MINOR_POSITIVE,5);
            for(int i=0;i<6;i++){ var g=new IronGolem(EntityTypes.IRON_GOLEM,level); g.snapTo(10+i,4,5); check(level.addFreshEntity(g),"populate golem cap " + i); }
            var constructed=new IronGolem(EntityTypes.IRON_GOLEM,level); constructed.snapTo(5,4,5); constructed.setPlayerCreated(true);
            SaviorEvents.constructed(constructed,player);
            check(value(v,player,GossipType.MINOR_POSITIVE)==5,"more than five golems suppress construction reward");
            v.getGossips().clear();
            var raid=new Raid(poiPos,Difficulty.HARD);
            var access=(RaidAccess)raid;
            access.savior$groupsSpawned(access.savior$groups()); access.savior$started(true);
            access.savior$postTicks(40); access.savior$cooldown(0); raid.addHeroOfTheVillage(player);
            raid.tick(level);
            check(raid.isVictory(),"actual raid tick reaches victory");
            check(value(v,player,GossipType.MAJOR_POSITIVE)==2,"victory transition awards participant");
            check(value(v,freshPlayer,GossipType.MAJOR_POSITIVE)==0,"nonparticipant gets no raid award");
            raid.tick(level);
            check(value(v,player,GossipType.MAJOR_POSITIVE)==2,"celebration tick cannot repeat award");
            SaviorEvents.raidWon(level,poiPos,Set.of(player.getUUID()));
            check(value(v,player,GossipType.MAJOR_POSITIVE)==2,"repeat raid within week cannot award");
            AdditionalChecks.run(server, IntegrationChecks::check);
            VillagerDeathChecks.run(server, IntegrationChecks::check);
            NitwitInventoryChecks.run(server, IntegrationChecks::check);
            ClericChecks.run(server, IntegrationChecks::check);
            var profession = ProfessionIntegrationChecks.run(server, level);
            results.addAll(profession.lines());
            checks += profession.checks();
            results.add("PASS " + profession.checks() + " profession integration checks");
            HudInspectionChecks.run(server, IntegrationChecks::check);
            ReputationScanChecks.run(server, IntegrationChecks::check);
            pass=results.stream().noneMatch(r -> r.startsWith("FAIL"));
        } catch (Throwable failure) { results.add("FAIL " + failure); failure.printStackTrace(); }
        try {
            String report=(pass?"PASS":"FAIL")+": "+checks+" checks\n"+String.join("\n",results)+"\n";
            Files.writeString(Path.of("test-result.txt"),report); System.out.println(report);
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }
    private static void pattern(ServerLevel level,BlockPos base) {
        level.setBlock(base,Blocks.IRON_BLOCK.defaultBlockState(),3);
        level.setBlock(base.above(),Blocks.IRON_BLOCK.defaultBlockState(),3);
        level.setBlock(base.above().east(),Blocks.IRON_BLOCK.defaultBlockState(),3);
        level.setBlock(base.above().west(),Blocks.IRON_BLOCK.defaultBlockState(),3);
    }
    private static ServerPlayer player(MinecraftServer server,ServerLevel level) {
        var profile=new GameProfile(UUID.randomUUID(),"SaviorTest");
        var player=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player,
            CommonListenerCookie.createInitial(profile,false));
        return player;
    }
    private static Villager villager(ServerLevel level,double x,double y,double z) {
        var v=new Villager(EntityTypes.VILLAGER,level); v.snapTo(x,y,z);
        check(level.addFreshEntity(v),"spawn test villager");return v;
    }
    private static int value(Villager v,ServerPlayer p,GossipType type) {
        var entry=v.getGossips().getGossipEntries().get(p.getUUID()); return entry==null?0:entry.getInt(type);
    }
    private static final class TestGolem extends IronGolem {
        TestGolem(ServerLevel level) { super(EntityTypes.IRON_GOLEM,level); }
        void repair(ServerPlayer player) { super.mobInteract(player,InteractionHand.MAIN_HAND); }
    }
}
