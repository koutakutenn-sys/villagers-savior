package dev.villagerssavior.test;

import com.mojang.authlib.GameProfile;
import dev.villagerssavior.SaviorGossip;
import dev.villagerssavior.SaviorState;
import dev.villagerssavior.Villages;
import dev.villagerssavior.debug.ReputationDebug;
import dev.villagerssavior.profession.CartographerInteraction;
import dev.villagerssavior.profession.PopulationSupply;
import dev.villagerssavior.profession.ProfessionInteractions;
import dev.villagerssavior.profession.ProfessionProduction;
import dev.villagerssavior.profession.ProfessionResources;
import dev.villagerssavior.profession.Rations;
import dev.villagerssavior.profession.ServiceItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/**
 * Server-side integration checks for the profession services and the reputation debug view. Everything runs
 * on a real ServerLevel with real villagers, real inventories and the real gossip container.
 */
public final class ProfessionIntegrationChecks {
    public record Report(List<String> lines, int checks) {}
    private final List<String> lines = new ArrayList<>();
    private final ServerLevel level;
    private final MinecraftServer server;
    private final BlockPos villagePos = new BlockPos(0, 4, 0);
    private String village;
    private long now;
    private int checks;
    private ProfessionIntegrationChecks(MinecraftServer server, ServerLevel level) {
        this.server = server;
        this.level = level;
    }
    public static Report run(MinecraftServer server, ServerLevel level) {
        var suite = new ProfessionIntegrationChecks(server, level);
        suite.execute();
        return new Report(suite.lines, suite.checks);
    }
    private void check(boolean value, String name) {
        checks++;
        lines.add((value ? "PASS " : "FAIL ") + name);
    }
    private void execute() {
        check(ProfessionInteractions.implemented().contains(VillagerProfession.FARMER), "farmer service registered");
        check(ProfessionInteractions.implemented().contains(VillagerProfession.LIBRARIAN), "librarian service registered");
        if (!ensureVillage()) {
            check(false, "profession tests need an identified village");
            return;
        }
        rationTests();
        wheatProcessingTests();
        conversionTests();
        repairTests();
        informationTests();
        populationSupplyTests();
        productionTests();
        tableConformanceTests();
        deathDropTests();
        cartographerTests();
        debugTests();
    }
    // ---------------------------------------------------------------- setup
    private boolean ensureVillage() {
        var home = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
        level.getPoiManager().add(villagePos, home);
        level.getPoiManager().tick(() -> true);
        village = Villages.identify(level, villagePos).orElse(null);
        now = level.getGameTime();
        return village != null;
    }
    private ServerPlayer player() {
        var profile = new GameProfile(UUID.randomUUID(), "ProfessionTest");
        var p = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        p.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), p,
            CommonListenerCookie.createInitial(profile, false));
        p.snapTo(villagePos.getX() + 2, 4, villagePos.getZ() + 2);
        return p;
    }
    private Villager villager(ResourceKey<VillagerProfession> profession, double x, double z) {
        var v = new Villager(EntityTypes.VILLAGER, level);
        v.snapTo(x, 4, z);
        v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), profession));
        v.getInventory().clearContent();
        level.addFreshEntity(v);
        return v;
    }
    private void trusted(Villager villager, ServerPlayer player, int reputation) {
        if (reputation <= 0) return;
        // MAJOR_POSITIVE weighs five, so twenty points are exactly one hundred reputation.
        SaviorGossip.add(villager, player.getUUID(), GossipType.MAJOR_POSITIVE, Math.min(20, reputation / 5));
        int remainder = reputation - Math.min(20, reputation / 5) * 5;
        if (remainder > 0) SaviorGossip.add(villager, player.getUUID(), GossipType.MINOR_POSITIVE, remainder);
    }
    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
    // ---------------------------------------------------------------- rations
    private void rationTests() {
        var p = player();
        p.getFoodData().setFoodLevel(20);
        var farmer = villager(VillagerProfession.FARMER, villagePos.getX() + 3, villagePos.getZ());
        farmer.getInventory().setItem(0, new ItemStack(Items.POTATO, 40));
        trusted(farmer, p, 25);
        check(ProfessionInteractions.serve(farmer, p).orElse("none").equals("ration"), "farmer gives travel rations");
        check(farmer.getInventory().getItem(0).getCount() == 37, "rations come out of the real inventory");
        check(!level.getEntitiesOfClass(ItemEntity.class, farmer.getBoundingBox().inflate(4)).isEmpty(),
            "ration drop entity was spawned");
        check(ProfessionInteractions.serve(farmer, p).isEmpty(), "ration cooldown blocks the same player");
        var other = player();
        other.getFoodData().setFoodLevel(20);
        trusted(farmer, other, 25);
        check(ProfessionInteractions.serve(farmer, other).orElse("none").equals("ration"),
            "ration cooldown is per player");
        var stranger = player();
        stranger.getFoodData().setFoodLevel(20);
        var poorFarmer = villager(VillagerProfession.FARMER, villagePos.getX() + 4, villagePos.getZ());
        poorFarmer.getInventory().setItem(0, new ItemStack(Items.POTATO, 40));
        check(ProfessionInteractions.serve(poorFarmer, stranger).isEmpty(), "rations need reputation 25");
        var hungry = player();
        hungry.getFoodData().setFoodLevel(10);
        trusted(poorFarmer, hungry, 100);
        check(ProfessionInteractions.serve(poorFarmer, hungry).isEmpty(), "rations are only for players who are full");
        int budget = Rations.budget(20, 25);
        check(budget == 3, "ration budget for twenty surplus at 25 reputation is three");
    }
    private void wheatProcessingTests() {
        var p = player();
        p.getFoodData().setFoodLevel(20);
        var farmer = villager(VillagerProfession.FARMER, villagePos.getX() + 5, villagePos.getZ());
        farmer.getInventory().setItem(0, new ItemStack(Items.WHEAT, 9));
        trusted(farmer, p, 100);
        check(ProfessionInteractions.serve(farmer, p).isEmpty(), "no surplus means no handout");
        check(farmer.getInventory().getItem(0).isEmpty(), "nine real wheat were consumed");
        check(farmer.getInventory().getItem(1).is(Items.BREAD) && farmer.getInventory().getItem(1).getCount() == 3,
            "nine wheat became exactly three bread");
        var state = SaviorState.get(level);
        check(state.ready("ration", p.getUUID(), village, now, Rations.COOLDOWN),
            "a failed handout does not start the ration cooldown");
    }
    // ---------------------------------------------------------------- conversions
    private void conversionTests() {
        var p = player();
        p.getInventory().clearContent();
        var fletcher = villager(VillagerProfession.FLETCHER, villagePos.getX() + 6, villagePos.getZ());
        fletcher.getInventory().setItem(0, new ItemStack(Items.FLINT, 2));
        fletcher.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
        fletcher.getInventory().setItem(2, new ItemStack(Items.FEATHER, 2));
        trusted(fletcher, p, 25);
        check(ProfessionInteractions.serve(fletcher, p).orElse("none").equals("service_fletcher"),
            "fletcher crafts arrows from its own stock");
        check(count(p, Items.ARROW) == 8, "two vanilla batches give exactly eight arrows");
        check(count(fletcher.getInventory(), Items.FLINT) == 0 && count(fletcher.getInventory(), Items.STICK) == 0
            && count(fletcher.getInventory(), Items.FEATHER) == 0, "the fletcher paid with its own materials");
        check(p.getInventory().countItem(Items.FLINT) == 0, "the player paid nothing");
        var mason = villager(VillagerProfession.MASON, villagePos.getX() + 7, villagePos.getZ());
        trusted(mason, p, 25);
        check(ProfessionInteractions.serve(mason, p).isEmpty(), "no stone means no masonry");
        var stonePlayer = player();
        stonePlayer.getInventory().clearContent();
        mason.getInventory().setItem(0, new ItemStack(Items.STONE, 8));
        trusted(mason, stonePlayer, 25);
        check(ProfessionInteractions.serve(mason, stonePlayer).orElse("none").equals("service_mason"),
            "mason cuts its own stone");
        check(count(stonePlayer, Items.STONE_BRICKS) == 8 && count(mason.getInventory(), Items.STONE) == 0,
            "eight of the mason's stone became exactly eight stone bricks");
        var cook = player();
        cook.getInventory().clearContent();
        var butcher = villager(VillagerProfession.BUTCHER, villagePos.getX() + 8, villagePos.getZ());
        butcher.getInventory().setItem(0, new ItemStack(Items.BEEF, 3));
        butcher.getInventory().setItem(1, new ItemStack(Items.COAL, 1));
        trusted(butcher, cook, 25);
        check(ProfessionInteractions.serve(butcher, cook).orElse("none").equals("service_butcher"),
            "butcher cooks its own meat");
        check(count(cook, Items.COOKED_BEEF) == 3 && count(butcher.getInventory(), Items.BEEF) == 0
            && count(butcher.getInventory(), Items.COAL) == 0,
            "three of the butcher's beef became three cooked beef and its coal was used");
        var noCoalPlayer = player();
        var noCoalButcher = villager(VillagerProfession.BUTCHER, villagePos.getX() + 18, villagePos.getZ());
        noCoalButcher.getInventory().setItem(0, new ItemStack(Items.BEEF, 3));
        trusted(noCoalButcher, noCoalPlayer, 25);
        check(ProfessionInteractions.serve(noCoalButcher, noCoalPlayer).isEmpty(), "cooking needs the villager's own coal");
        var shepherd = villager(VillagerProfession.SHEPHERD, villagePos.getX() + 9, villagePos.getZ());
        var woolPlayer = player();
        woolPlayer.getInventory().clearContent();
        shepherd.getInventory().setItem(0, new ItemStack(Items.WOOL.pick(DyeColor.WHITE), 4));
        trusted(shepherd, woolPlayer, 25);
        check(ProfessionInteractions.serve(shepherd, woolPlayer).orElse("none").equals("service_shepherd"),
            "shepherd weaves its own wool");
        check(count(woolPlayer, Items.CARPET.pick(DyeColor.WHITE)) == 6
            && count(shepherd.getInventory(), Items.WOOL.pick(DyeColor.WHITE)) == 0,
            "four of the shepherd's wool became exactly six carpet");
        var cleric = villager(VillagerProfession.CLERIC, villagePos.getX() + 19, villagePos.getZ());
        var blessed = player();
        cleric.getInventory().setItem(0, new ItemStack(Items.REDSTONE));
        trusted(cleric, blessed, 25);
        check(ProfessionInteractions.serve(cleric, blessed).orElse("none").equals("service_cleric"),
            "cleric blesses out of its own stock");
        check(count(cleric.getInventory(), Items.REDSTONE) == 0 && blessed.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),
            "the blessing consumed the cleric's reagent and reached the player");
    }
    // ---------------------------------------------------------------- repairs
    private void repairTests() {
        var smith = villager(VillagerProfession.TOOLSMITH, villagePos.getX() + 10, villagePos.getZ());
        var p = player();
        p.getInventory().clearContent();
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        int max = pick.getMaxDamage();
        pick.setDamageValue(200);
        p.getInventory().setItem(0, pick);
        smith.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        trusted(smith, p, 25);
        int perUnit = max * 25 / 100;
        int quota = dev.villagerssavior.profession.Repairs.dailyUnits(25);
        check(ProfessionInteractions.serve(smith, p).orElse("none").equals("service_repair"),
            "toolsmith repairs a damaged tool");
        check(pick.getDamageValue() == 200 - quota * perUnit, "repair amount follows the vanilla anvil quarter");
        check(count(smith.getInventory(), Items.IRON_INGOT) == 4 - quota, "repair consumed the villager's materials");
        check(ProfessionInteractions.serve(smith, p).isEmpty(), "daily repair quota stops further repairs");
        var noMaterial = player();
        noMaterial.getInventory().clearContent();
        ItemStack second = new ItemStack(Items.IRON_PICKAXE);
        second.setDamageValue(200);
        noMaterial.getInventory().setItem(0, second);
        var brokeSmith = villager(VillagerProfession.TOOLSMITH, villagePos.getX() + 20, villagePos.getZ());
        trusted(brokeSmith, noMaterial, 25);
        check(ProfessionInteractions.serve(brokeSmith, noMaterial).isEmpty(), "repairs need the villager's own materials");
    }
    // ---------------------------------------------------------------- information
    private void informationTests() {
        var p = player();
        p.getInventory().clearContent();
        var librarian = villager(VillagerProfession.LIBRARIAN, villagePos.getX() + 11, villagePos.getZ());
        trusted(librarian, p, 25);
        check(ProfessionInteractions.serve(librarian, p).orElse("none").equals("service_librarian"),
            "librarian reports the relationship");
        check(p.getInventory().isEmpty(), "librarian information costs nothing and conjures nothing");
        var cartographer = villager(VillagerProfession.CARTOGRAPHER, villagePos.getX() + 12, villagePos.getZ());
        trusted(cartographer, p, 25);
        check(ProfessionInteractions.serve(cartographer, p).orElse("none").equals("service_cartographer"),
            "cartographer shares directions");
        check(p.getInventory().isEmpty(), "cartographer gives no map items");
        var nitwit = villager(VillagerProfession.NITWIT, villagePos.getX() + 13, villagePos.getZ());
        var none = villager(VillagerProfession.NONE, villagePos.getX() + 14, villagePos.getZ());
        check(ProfessionInteractions.serve(nitwit, p).isEmpty(), "nitwit has no profession service");
        check(ProfessionInteractions.serve(none, p).isEmpty(), "unemployed has no profession service");
    }
    // ---------------------------------------------------------------- population supply
    private void populationSupplyTests() {
        var home = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
        // Room to grow: five beds, one farmer -> the farmer keeps one breeding reserve.
        BlockPos roomy = new BlockPos(1024, 4, 4096);
        loadArea(roomy);
        for (int i = 0; i < 5; i++) level.getPoiManager().add(roomy.offset(i * 2, 0, 0), home);
        var farmer = villager(VillagerProfession.FARMER, roomy.getX(), roomy.getZ());
        farmer.getInventory().setItem(0, new ItemStack(Items.CARROT, 3));
        farmer.getInventory().setItem(1, new ItemStack(Items.WHEAT, 1));
        farmer.restock();
        check(count(farmer.getInventory(), Items.CARROT) == 4,
            "restock adds one step of the village's main crop");
        check(count(farmer.getInventory(), Items.WHEAT) == 1, "only the village's main crop is supplied");
        for (int i = 0; i < 10; i++) farmer.restock();
        check(count(farmer.getInventory(), Items.CARROT) == 7,
            "the farmer's production stops at its daily cap of four");
        var atReserve = villager(VillagerProfession.FARMER, roomy.getX() + 4, roomy.getZ());
        atReserve.getInventory().setItem(0, new ItemStack(Items.CARROT, PopulationSupply.RESERVE));
        trusted(atReserve, player(), 25);
        atReserve.restock();
        check(count(atReserve.getInventory(), Items.CARROT) == PopulationSupply.RESERVE,
            "the breeding reserve is never exceeded");
        // No room to grow: three beds, four villagers -> nothing happens.
        BlockPos crowded = new BlockPos(1024, 4, 4608);
        loadArea(crowded);
        for (int i = 0; i < 3; i++) level.getPoiManager().add(crowded.offset(i * 2, 0, 0), home);
        var crowdedFarmer = villager(VillagerProfession.FARMER, crowded.getX(), crowded.getZ());
        for (int i = 0; i < 3; i++) villager(VillagerProfession.NONE, crowded.getX() + 1 + i, crowded.getZ() + 1);
        crowdedFarmer.getInventory().setItem(0, new ItemStack(Items.CARROT, 1));
        crowdedFarmer.restock();
        check(count(crowdedFarmer.getInventory(), Items.CARROT) == 1,
            "a village without spare beds receives no population supply");
        // Nothing tells the farmer what this village grows -> nothing happens.
        BlockPos unknown = new BlockPos(1024, 4, 5120);
        loadArea(unknown);
        for (int i = 0; i < 3; i++) level.getPoiManager().add(unknown.offset(i * 2, 0, 0), home);
        var emptyFarmer = villager(VillagerProfession.FARMER, unknown.getX(), unknown.getZ());
        emptyFarmer.restock();
        check(emptyFarmer.getInventory().isEmpty(), "no crop evidence means no population supply");
        // Only farmers run the population supply.
        BlockPos other = new BlockPos(1024, 4, 5632);
        loadArea(other);
        for (int i = 0; i < 3; i++) level.getPoiManager().add(other.offset(i * 2, 0, 0), home);
        var librarian = villager(VillagerProfession.LIBRARIAN, other.getX(), other.getZ());
        librarian.getInventory().setItem(0, new ItemStack(Items.CARROT, 1));
        librarian.restock();
        check(count(librarian.getInventory(), Items.CARROT) == 1, "only farmers run the population supply");
    }
    // ---------------------------------------------------------------- profession production
    private void productionTests() {
        // One restock produces a little; several restocks in the same day stop at the daily cap.
        var fletcher = villager(VillagerProfession.FLETCHER, villagePos.getX() + 24, villagePos.getZ());
        fletcher.restock();
        check(ServiceItems.count(fletcher.getInventory(), Items.ARROW) == 2, "one restock produces two arrows");
        check(ServiceItems.count(fletcher.getInventory(), Items.FLINT) == 1, "one restock produces one flint");
        for (int i = 0; i < 20; i++) fletcher.restock();
        check(ServiceItems.count(fletcher.getInventory(), Items.ARROW) == 4, "arrows stop at the daily production cap");
        check(ServiceItems.count(fletcher.getInventory(), Items.FLINT) == 1, "flint stops at its daily production cap");
        check(ServiceItems.count(fletcher.getInventory(), Items.FEATHER) == 1, "feather stops at its daily production cap");
        // At or above the inventory target nothing more is produced, and the hard cap is never passed.
        var stocked = villager(VillagerProfession.FLETCHER, villagePos.getX() + 25, villagePos.getZ());
        stocked.getInventory().setItem(0, new ItemStack(Items.ARROW, 8));
        stocked.restock();
        check(ServiceItems.count(stocked.getInventory(), Items.ARROW) == 8, "production stops at the inventory target");
        var capped = villager(VillagerProfession.FLETCHER, villagePos.getX() + 26, villagePos.getZ());
        capped.getInventory().setItem(0, new ItemStack(Items.ARROW, 16));
        capped.restock();
        check(ServiceItems.count(capped.getInventory(), Items.ARROW) == 16, "the hard cap is never exceeded");
        var aboveTarget = villager(VillagerProfession.FLETCHER, villagePos.getX() + 27, villagePos.getZ());
        aboveTarget.getInventory().setItem(0, new ItemStack(Items.ARROW, 12));
        aboveTarget.restock();
        check(ServiceItems.count(aboveTarget.getInventory(), Items.ARROW) == 12,
            "stock obtained through vanilla behaviour is left alone above the target");
        // A later in-game day grants production again, through the real production path.
        var ledger = SaviorState.get(level);
        var arrowResource = ProfessionResources.find(VillagerProfession.FLETCHER, Items.ARROW);
        int beforeNextDay = ServiceItems.count(fletcher.getInventory(), Items.ARROW);
        int gained = ProfessionProduction.produce(fletcher, ledger, now + 24000L, arrowResource);
        check(gained == 2 && ServiceItems.count(fletcher.getInventory(), Items.ARROW) == beforeNextDay + 2,
            "production resumes on the next in-game day");
        // Other professions produce their own stock; unemployed villagers produce nothing.
        var cleric = villager(VillagerProfession.CLERIC, villagePos.getX() + 28, villagePos.getZ());
        cleric.restock();
        int reagents = ServiceItems.count(cleric.getInventory(), Items.REDSTONE)
            + ServiceItems.count(cleric.getInventory(), Items.GLOWSTONE_DUST)
            + ServiceItems.count(cleric.getInventory(), Items.LAPIS_LAZULI);
        check(reagents == 3, "the cleric produces one of each alchemy reagent per day");
        for (int i = 0; i < 10; i++) cleric.restock();
        int after = ServiceItems.count(cleric.getInventory(), Items.REDSTONE)
            + ServiceItems.count(cleric.getInventory(), Items.GLOWSTONE_DUST)
            + ServiceItems.count(cleric.getInventory(), Items.LAPIS_LAZULI);
        check(after == 3, "the cleric stops at its daily cap of one per reagent");
        var stockedCleric = villager(VillagerProfession.CLERIC, villagePos.getX() + 30, villagePos.getZ());
        stockedCleric.getInventory().setItem(0, new ItemStack(Items.REDSTONE, 2));
        stockedCleric.restock();
        check(ServiceItems.count(stockedCleric.getInventory(), Items.REDSTONE) == 2,
            "the cleric stops at the reagent target");
        var nitwit = villager(VillagerProfession.NITWIT, villagePos.getX() + 29, villagePos.getZ());
        nitwit.restock();
        check(nitwit.getInventory().isEmpty(), "unemployed villagers produce nothing");
        check(ProfessionResources.professions().contains(VillagerProfession.FARMER)
            && ProfessionResources.professions().contains(VillagerProfession.CARTOGRAPHER),
            "production tables exist for the working professions");
        // The production model itself: per restock, target, hard cap, and no conjured bread.
        var arrow = ProfessionResources.find(VillagerProfession.FLETCHER, Items.ARROW);
        check(arrow != null && arrow.perRestock() == 2 && arrow.target() == 8 && arrow.hardCap() == 16,
            "fletcher arrow table matches the specification");
        check(ProfessionResources.producible(arrow, 0) == 2, "an empty stock gets one production step");
        check(ProfessionResources.producible(arrow, 7) == 1, "production never passes the target");
        check(ProfessionResources.producible(arrow, 8) == 0, "production stops at the target");
        check(ProfessionResources.producible(arrow, 12) == 0, "vanilla stock above the target is left alone");
        check(ProfessionResources.producible(arrow, 16) == 0, "the hard cap is absolute");
        var bread = ProfessionResources.find(VillagerProfession.FARMER, Items.BREAD);
        check(bread != null && bread.perRestock() == 0 && bread.dailyCap() == 0,
            "the farmer never produces bread on its own");
        check(ProfessionResources.of(VillagerProfession.NITWIT).isEmpty(), "nitwits have no production table");
        // Economy sweep: 200 restocks of every profession must stay inside the model and invent nothing.
        for (var profession : ProfessionResources.professions()) {
            var swept = villager(profession, villagePos.getX() + 32, villagePos.getZ());
            swept.getInventory().clearContent();
            for (int i = 0; i < 200; i++) swept.restock();
            boolean insideModel = true;
            for (int slot = 0; slot < swept.getInventory().getContainerSize(); slot++) {
                ItemStack stack = swept.getInventory().getItem(slot);
                if (stack.isEmpty()) continue;
                var resource = ProfessionResources.find(profession, stack.getItem());
                if (resource == null || stack.getCount() > resource.hardCap()) { insideModel = false; break; }
            }
            check(insideModel, "200 restocks stay inside the production model for " + profession.identifier().getPath());
        }
    }
    // ---------------------------------------------------------------- table conformance
    private void tableConformanceTests() {
        record Expected(net.minecraft.resources.ResourceKey<VillagerProfession> profession, net.minecraft.world.item.Item item, int target, int hardCap) {}
        var wool = Items.WOOL.pick(DyeColor.WHITE);
        var expected = java.util.List.of(
            new Expected(VillagerProfession.FARMER, Items.WHEAT, PopulationSupply.RESERVE, 48),
            new Expected(VillagerProfession.FARMER, Items.CARROT, PopulationSupply.RESERVE, 48),
            new Expected(VillagerProfession.FARMER, Items.POTATO, PopulationSupply.RESERVE, 48),
            new Expected(VillagerProfession.FARMER, Items.BEETROOT, PopulationSupply.RESERVE, 48),
            new Expected(VillagerProfession.FARMER, Items.BREAD, 24, 48),
            new Expected(VillagerProfession.FISHERMAN, Items.COD, 8, 16),
            new Expected(VillagerProfession.FISHERMAN, Items.SALMON, 8, 16),
            new Expected(VillagerProfession.FISHERMAN, Items.COAL, 2, 4),
            new Expected(VillagerProfession.BUTCHER, Items.BEEF, 6, 12),
            new Expected(VillagerProfession.BUTCHER, Items.PORKCHOP, 6, 12),
            new Expected(VillagerProfession.BUTCHER, Items.CHICKEN, 6, 12),
            new Expected(VillagerProfession.BUTCHER, Items.MUTTON, 6, 12),
            new Expected(VillagerProfession.BUTCHER, Items.RABBIT, 6, 12),
            new Expected(VillagerProfession.BUTCHER, Items.COAL, 2, 4),
            new Expected(VillagerProfession.FLETCHER, Items.ARROW, 8, 16),
            new Expected(VillagerProfession.FLETCHER, Items.FLINT, 4, 8),
            new Expected(VillagerProfession.FLETCHER, Items.FEATHER, 4, 8),
            new Expected(VillagerProfession.FLETCHER, Items.STICK, 4, 8),
            new Expected(VillagerProfession.SHEPHERD, wool, 4, 8),
            new Expected(VillagerProfession.MASON, Items.STONE, 8, 16),
            new Expected(VillagerProfession.MASON, Items.STONE_BRICKS, 8, 16),
            new Expected(VillagerProfession.CLERIC, Items.REDSTONE, 2, 4),
            new Expected(VillagerProfession.CLERIC, Items.GLOWSTONE_DUST, 2, 4),
            new Expected(VillagerProfession.CLERIC, Items.LAPIS_LAZULI, 2, 4),
            new Expected(VillagerProfession.TOOLSMITH, Items.IRON_INGOT, 4, 8),
            new Expected(VillagerProfession.WEAPONSMITH, Items.IRON_INGOT, 4, 8),
            new Expected(VillagerProfession.ARMORER, Items.IRON_INGOT, 4, 8),
            new Expected(VillagerProfession.LEATHERWORKER, Items.LEATHER, 4, 8),
            new Expected(VillagerProfession.LIBRARIAN, Items.PAPER, 8, 16),
            new Expected(VillagerProfession.LIBRARIAN, Items.BOOK, 2, 4),
            new Expected(VillagerProfession.CARTOGRAPHER, Items.PAPER, 8, 16));
        for (var entry : expected) {
            var resource = ProfessionResources.find(entry.profession(), entry.item());
            check(resource != null && resource.target() == entry.target() && resource.hardCap() == entry.hardCap(),
                "table conformance: " + entry.profession().identifier().getPath() + " " + entry.item());
        }
        int entries = 0;
        for (var profession : ProfessionResources.professions()) {
            for (var resource : ProfessionResources.of(profession)) {
                entries++;
                check(resource.target() <= resource.hardCap() && resource.dailyCap() >= 0 && resource.perRestock() >= 0,
                    "sane production bounds: " + profession.identifier().getPath() + " " + resource.item());
            }
        }
        check(entries == expected.size(), "the production table holds exactly the specified entries (" + entries + ")");
    }
    // ---------------------------------------------------------------- death drops
    private void deathDropTests() {
        BlockPos spot = new BlockPos(2048, 4, 4096);
        loadArea(spot);
        var doomed = villager(VillagerProfession.FLETCHER, spot.getX(), spot.getZ());
        doomed.getInventory().setItem(0, new ItemStack(Items.ARROW, 5));
        doomed.getInventory().setItem(1, new ItemStack(Items.EMERALD, 2));
        doomed.getInventory().setItem(2, new ItemStack(Items.BREAD, 3));
        int before = level.getEntitiesOfClass(ItemEntity.class, doomed.getBoundingBox().inflate(3)).size();
        doomed.hurtServer(level, level.damageSources().generic(), 1000.0f);
        check(doomed.getInventory().isEmpty(), "death clears the villager's whole hidden inventory");
        int drops = level.getEntitiesOfClass(ItemEntity.class, doomed.getBoundingBox().inflate(3)).size() - before;
        check(drops == 3, "death drops every stored stack, whether produced or picked up");
    }
    // ---------------------------------------------------------------- cartographer
    private void cartographerTests() {
        var home = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
        BlockPos own = new BlockPos(1536, 4, 4096);
        BlockPos far = new BlockPos(1536, 4, 4352);
        loadArea(own);
        loadArea(far);
        level.getPoiManager().add(own, home);
        level.getPoiManager().add(own.offset(2, 0, 0), home);
        level.getPoiManager().add(far, home);
        var cartographer = villager(VillagerProfession.CARTOGRAPHER, own.getX(), own.getZ());
        var target = CartographerInteraction.findOtherVillage(level, cartographer.blockPosition());
        check(target.isPresent(), "cartographer finds another village");
        check(target.map(pos -> pos.distSqr(own) >= 64 * 64).orElse(false),
            "cartographer never points at its own village");
        BlockPos isolated = new BlockPos(4096, 4, -4096);
        loadArea(isolated);
        level.getPoiManager().add(isolated, home);
        var lonely = villager(VillagerProfession.CARTOGRAPHER, isolated.getX(), isolated.getZ());
        check(CartographerInteraction.findOtherVillage(level, lonely.blockPosition()).isEmpty(),
            "cartographer reports no other village when none is in range");
    }
    private void loadArea(BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) level.getChunk((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz);
    }
    private static int count(net.minecraft.world.Container container, net.minecraft.world.item.Item item) {
        return ServiceItems.count(container, item);
    }

    // ---------------------------------------------------------------- debug view
    private void debugTests() {
        var p = player();
        var villager = villager(VillagerProfession.CLERIC, villagePos.getX() + 15, villagePos.getZ());
        SaviorGossip.add(villager, p.getUUID(), GossipType.MINOR_POSITIVE, 10);
        SaviorGossip.add(villager, p.getUUID(), GossipType.TRADING, 4);
        SaviorGossip.add(villager, p.getUUID(), GossipType.MINOR_NEGATIVE, 3);
        var report = ReputationDebug.inspect(villager, p);
        check(report.total() == villager.getPlayerReputation(p), "debug total is the real vanilla reputation");
        check(report.minorPositive() == 10 && report.trading() == 4 && report.minorNegative() == 3,
            "debug reads every gossip type from the server");
        check(report.majorPositive() == 0 && report.majorNegative() == 0, "missing gossip types read as zero");
        check(report.villagerUuid().equals(villager.getUUID().toString()), "debug reports the villager uuid");
        check(report.profession().equals("cleric"), "debug reports the profession");
        check(report.level() == villager.getVillagerData().level(), "debug reports the profession level");
        check(ReputationDebug.lines(report).size() == 7, "debug prints one line per required field plus a header");
        var entries = villager.getGossips().getGossipEntries().get(p.getUUID());
        check(entries.getInt(GossipType.MINOR_POSITIVE) == 10 && entries.getInt(GossipType.TRADING) == 4
            && entries.getInt(GossipType.MINOR_NEGATIVE) == 3, "reading the debug view never changes gossip");
    }
}
