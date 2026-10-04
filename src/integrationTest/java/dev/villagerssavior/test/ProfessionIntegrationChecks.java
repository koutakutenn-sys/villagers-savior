package dev.villagerssavior.test;

import com.mojang.authlib.GameProfile;
import dev.villagerssavior.SaviorGossip;
import dev.villagerssavior.SaviorState;
import dev.villagerssavior.Villages;
import dev.villagerssavior.debug.ReputationDebug;
import dev.villagerssavior.profession.ProfessionInteractions;
import dev.villagerssavior.profession.Rations;
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
        p.getInventory().setItem(0, new ItemStack(Items.FLINT, 2));
        p.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
        p.getInventory().setItem(2, new ItemStack(Items.FEATHER, 2));
        var fletcher = villager(VillagerProfession.FLETCHER, villagePos.getX() + 6, villagePos.getZ());
        trusted(fletcher, p, 25);
        check(ProfessionInteractions.serve(fletcher, p).orElse("none").equals("service_fletcher"),
            "fletcher crafts arrows");
        check(count(p, Items.ARROW) == 8, "two vanilla batches give exactly eight arrows");
        check(count(p, Items.FLINT) == 0 && count(p, Items.STICK) == 0 && count(p, Items.FEATHER) == 0,
            "fletcher consumed the real materials");
        var mason = villager(VillagerProfession.MASON, villagePos.getX() + 7, villagePos.getZ());
        trusted(mason, p, 25);
        check(ProfessionInteractions.serve(mason, p).isEmpty(), "no stone means no masonry");
        var stonePlayer = player();
        stonePlayer.getInventory().clearContent();
        stonePlayer.getInventory().setItem(0, new ItemStack(Items.STONE, 8));
        trusted(mason, stonePlayer, 25);
        check(ProfessionInteractions.serve(mason, stonePlayer).orElse("none").equals("service_mason"),
            "mason cuts stone");
        check(count(stonePlayer, Items.STONE_BRICKS) == 8 && count(stonePlayer, Items.STONE) == 0,
            "eight stone became exactly eight stone bricks");
        var cook = player();
        cook.getInventory().clearContent();
        cook.getInventory().setItem(0, new ItemStack(Items.BEEF, 3));
        cook.getInventory().setItem(1, new ItemStack(Items.COAL, 1));
        var butcher = villager(VillagerProfession.BUTCHER, villagePos.getX() + 8, villagePos.getZ());
        trusted(butcher, cook, 25);
        check(ProfessionInteractions.serve(butcher, cook).orElse("none").equals("service_butcher"),
            "butcher cooks meat");
        check(count(cook, Items.COOKED_BEEF) == 3 && count(cook, Items.BEEF) == 0 && count(cook, Items.COAL) == 0,
            "three beef became three cooked beef and the coal was used");
        var noCoal = player();
        noCoal.getInventory().clearContent();
        noCoal.getInventory().setItem(0, new ItemStack(Items.BEEF, 3));
        trusted(butcher, noCoal, 25);
        check(ProfessionInteractions.serve(butcher, noCoal).isEmpty(), "cooking needs real coal");
        var shepherd = villager(VillagerProfession.SHEPHERD, villagePos.getX() + 9, villagePos.getZ());
        var woolPlayer = player();
        woolPlayer.getInventory().clearContent();
        woolPlayer.getInventory().setItem(0, new ItemStack(Items.WOOL.pick(DyeColor.WHITE), 4));
        trusted(shepherd, woolPlayer, 25);
        check(ProfessionInteractions.serve(shepherd, woolPlayer).orElse("none").equals("service_shepherd"),
            "shepherd weaves carpet");
        check(count(woolPlayer, Items.CARPET.pick(DyeColor.WHITE)) == 6 && count(woolPlayer, Items.WOOL.pick(DyeColor.WHITE)) == 0,
            "four wool became exactly six carpet");
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
        p.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 4));
        trusted(smith, p, 25);
        int perUnit = max * 25 / 100;
        int quota = dev.villagerssavior.profession.Repairs.dailyUnits(25);
        check(ProfessionInteractions.serve(smith, p).orElse("none").equals("service_repair"),
            "toolsmith repairs a damaged tool");
        check(pick.getDamageValue() == 200 - quota * perUnit, "repair amount follows the vanilla anvil quarter");
        check(count(p, Items.IRON_INGOT) == 4 - quota, "repair consumed real materials");
        check(ProfessionInteractions.serve(smith, p).isEmpty(), "daily repair quota stops further repairs");
        var noMaterial = player();
        noMaterial.getInventory().clearContent();
        ItemStack second = new ItemStack(Items.IRON_PICKAXE);
        second.setDamageValue(200);
        noMaterial.getInventory().setItem(0, second);
        trusted(smith, noMaterial, 25);
        check(ProfessionInteractions.serve(smith, noMaterial).isEmpty(), "repairs need real materials");
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
