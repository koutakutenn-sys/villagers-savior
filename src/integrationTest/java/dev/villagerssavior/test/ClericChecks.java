package dev.villagerssavior.test;

import com.mojang.authlib.GameProfile;
import dev.villagerssavior.*;
import dev.villagerssavior.debug.VillagerInspection;
import dev.villagerssavior.profession.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Real profession dispatch, effect application, resource production and shared-cooldown boundaries. */
public final class ClericChecks {
    public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) {
        var level = server.overworld(); level.getChunk(768, 0); level.getChunk(784, 0);
        var home = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
        level.getPoiManager().add(new BlockPos(12288, 4, 0), home);
        level.getPoiManager().add(new BlockPos(12544, 4, 0), home);
        for (int reputation : new int[]{-101, -100, -99, -1, 0, 24, 25, 100})
            for (float health : new float[]{2, 2.01f, 6, 6.01f, 20}) {
                var player = player(server, level); player.setHealth(health);
                var cleric = cleric(level, 12288); reputation(cleric, player, reputation);
                cleric.getInventory().setItem(0, new ItemStack(Items.REDSTONE));
                boolean allowed = reputation >= 0 || health <= (reputation <= -100 ? 2 : 6);
                var preview = VillagerInspection.inspect(cleric, player);
                String label = " R=" + reputation + " health=" + health;
                check.accept(HudInspectionChecks.has(preview, allowed ? "available" : "cleric_health_required")
                    && !HudInspectionChecks.has(preview, "trust_required"), "cleric: HUD matches eligibility" + label);
                var result = ProfessionInteractions.serve(cleric, player);
                check.accept(result.isPresent() == allowed, "cleric: actual service matches reputation and health boundary" + label);
                var effect = player.getEffect(MobEffects.REGENERATION);
                check.accept(allowed ? effect != null && effect.getAmplifier() == 0 && effect.getDuration() == (reputation >= 0 ? 200 : 100)
                    : effect == null, "cleric: correct regeneration intensity and duration" + label);
                check.accept(ServiceItems.count(cleric.getInventory(), Items.REDSTONE) == (allowed ? 0 : 1),
                    "cleric: only successful service spends one charge" + label);
            }

        var player = player(server, level); var first = cleric(level, 12288); var second = cleric(level, 12290);
        reputation(first, player, -1); player.setHealth(6);
        first.getInventory().setItem(0, new ItemStack(Items.REDSTONE, 3));
        second.getInventory().setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 3));
        var service = new ClericInteraction(); long now = level.getGameTime();
        check.accept(service.serve(level, first, player, now).orElse("").equals("service_cleric_emergency"),
            "cleric: emergency cast starts its longer cooldown");
        check.accept(service.serve(level, second, player, now + 23999).isEmpty()
            && ServiceItems.count(second.getInventory(), Items.GLOWSTONE_DUST) == 3,
            "cleric: switching to neutral cleric cannot bypass 24000-tick emergency cooldown");
        var positions = Villages.positions(level, first.blockPosition()).orElseThrow();
        var state = SaviorState.get(level);
        check.accept(ClericInteraction.remainingAt(state, player.getUUID(), positions, now + 23999) == 1,
            "cleric: HUD keeps emergency cooldown after reputation improves");
        player.removeEffect(MobEffects.REGENERATION);
        check.accept(service.serve(level, second, player, now + 24000).orElse("").equals("service_cleric")
            && player.getEffect(MobEffects.REGENERATION).getDuration() == 200,
            "cleric: neutral blessing unlocks at exact emergency cooldown boundary");
        check.accept(service.serve(level, first, player, now + 35999).isEmpty(),
            "cleric: a normal cast shares its 12000-tick cooldown with emergency treatment");
        player.removeEffect(MobEffects.REGENERATION);
        check.accept(service.serve(level, first, player, now + 36000).isPresent()
            && player.getEffect(MobEffects.REGENERATION).getDuration() == 100,
            "cleric: emergency treatment unlocks at exact normal cooldown boundary");
        var otherVillage = cleric(level, 12544); otherVillage.getInventory().setItem(0, new ItemStack(Items.LAPIS_LAZULI));
        check.accept(service.serve(level, otherVillage, player, now + 36000).isPresent(),
            "cleric: another village has an independent cooldown");

        var worker = cleric(level, 12288); var requester = player(server, level);
        requester.snapTo(12289, 4, 0); requester.getInventory().clearContent();
        FoodGifts.request(requester, new FoodRequest(worker.getId(), InteractionHand.MAIN_HAND));
        var village = Villages.identify(level, worker.blockPosition()).orElseThrow();
        check.accept(!requester.hasEffect(MobEffects.REGENERATION)
            && state.ready("service_cleric", requester.getUUID(), village, now, 12000),
            "cleric: no charge means no blessing and no cooldown");
        worker.restock();
        check.accept(ServiceItems.count(worker.getInventory(), Items.REDSTONE) == 1
            && ServiceItems.count(worker.getInventory(), Items.GLOWSTONE_DUST) == 1
            && ServiceItems.count(worker.getInventory(), Items.LAPIS_LAZULI) == 1,
            "cleric: real workstation restock restores three bounded reagent charges");
        // Exercise the service directly; the previous failed packet did not start a blessing cooldown.
        var direct = service.serve(level, worker, requester, now);
        check.accept(direct.isPresent() && requester.getEffect(MobEffects.REGENERATION).getDuration() == 200
            && ServiceItems.count(worker.getInventory(), Items.REDSTONE) == 0 && requester.getInventory().isEmpty(),
            "cleric: neutral player gets free blessing by spending restored villager charge");
        worker.restock();
        check.accept(ServiceItems.count(worker.getInventory(), Items.REDSTONE) == 0,
            "cleric: repeat same-day restock cannot regenerate a spent charge beyond daily cap");
        var packetPlayer = player(server, level); packetPlayer.snapTo(12289, 4, 0);
        FoodGifts.request(packetPlayer, new FoodRequest(worker.getId(), InteractionHand.MAIN_HAND));
        check.accept(packetPlayer.hasEffect(MobEffects.REGENERATION)
            && ServiceItems.count(worker.getInventory(), Items.GLOWSTONE_DUST) == 0,
            "cleric: actual neutral-player request path reaches normal blessing");
        check.accept(ClericInteraction.blessing(0, 0).isEmpty(), "cleric: a dead player is not eligible");
        var outside = cleric(level, 13000); outside.getInventory().setItem(0, new ItemStack(Items.REDSTONE));
        var lonely = player(server, level);
        check.accept(service.serve(level, outside, lonely, now).isEmpty()
            && ServiceItems.count(outside.getInventory(), Items.REDSTONE) == 1, "cleric: blessing still requires recognized village");
        var otherProfession = new Villager(EntityTypes.VILLAGER, level); otherProfession.snapTo(12288, 4, 0);
        otherProfession.setVillagerData(otherProfession.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FLETCHER));
        otherProfession.getInventory().setItem(0, new ItemStack(Items.FLINT));
        otherProfession.getInventory().setItem(1, new ItemStack(Items.STICK));
        otherProfession.getInventory().setItem(2, new ItemStack(Items.FEATHER));
        check.accept(ProfessionInteractions.serve(otherProfession, lonely).isEmpty(), "cleric: other material services keep positive-reputation gate");
    }
    private static Villager cleric(ServerLevel level, int x) {
        level.getChunk(x >> 4, 0);
        var villager = new Villager(EntityTypes.VILLAGER, level); villager.snapTo(x, 4, 0); villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.CLERIC));
        level.addFreshEntity(villager); return villager;
    }
    private static ServerPlayer player(MinecraftServer server, ServerLevel level) {
        var profile = new GameProfile(UUID.randomUUID(), "ClericAudit");
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
            CommonListenerCookie.createInitial(profile, false));
        player.getFoodData().setFoodLevel(20); return player;
    }
    private static void reputation(Villager villager, ServerPlayer player, int reputation) {
        if (reputation < 0) SaviorGossip.add(villager, player.getUUID(), GossipType.MINOR_NEGATIVE, -reputation);
        else {
            SaviorGossip.add(villager, player.getUUID(), GossipType.MAJOR_POSITIVE, reputation / 5);
            SaviorGossip.add(villager, player.getUUID(), GossipType.MINOR_POSITIVE, reputation % 5);
        }
    }
}
