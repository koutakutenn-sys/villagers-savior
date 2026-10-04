package dev.villagerssavior.test;

import com.mojang.authlib.GameProfile;
import dev.villagerssavior.SaviorGossip;
import dev.villagerssavior.debug.ReputationScan;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.*;
import java.util.function.BiConsumer;

/** Exercises the actual vanilla death notification and the release mixin, not a direct helper call. */
public final class VillagerDeathChecks {
    public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) {
        var level = server.overworld();
        level.getChunk(256, 0);
        var profile = new GameProfile(UUID.randomUUID(), "NitwitAudit");
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
            player, CommonListenerCookie.createInitial(profile, false));
        player.snapTo(4096, 8, 1);
        var witness = villager(level, VillagerProfession.NONE, 4098, 8, 0);
        var second = villager(level, VillagerProfession.NITWIT, 4098, 8, 1);
        var unseen = villager(level, VillagerProfession.NONE, 4099, 8, 0);
        var damage = level.damageSources().playerAttack(player);

        // Every ordinary vanilla profession keeps only the vanilla murder gossip.
        for (var profession : level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).listElements().toList()) {
            if (profession.is(VillagerProfession.NITWIT)) continue;
            witness.getGossips().clear();
            var victim = villager(level, profession.key(), 4096, 8, 0);
            witnesses(level, victim, witness);
            victim.setHealth(1);
            check.accept(victim.hurtServer(level, damage, 2) && !victim.isAlive(),
                "villager death: lethal player damage reaches vanilla death for " + profession.key().identifier());
            check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 25
                && value(witness, player, GossipType.MINOR_NEGATIVE) == 0,
                "villager death: ordinary profession keeps vanilla-only punishment " + profession.key().identifier());
        }

        var home = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
        for (int x : new int[]{4096, 4160, 4260}) {
            level.getChunk(x >> 4, 0);
            level.getPoiManager().add(new BlockPos(x, 8, 0), home);
        }
        var distant = villager(level, VillagerProfession.FARMER, 4170, 8, 0);
        var otherVillage = villager(level, VillagerProfession.NONE, 4261, 8, 0);
        var deadResident = villager(level, VillagerProfession.NONE, 4100, 8, 0); deadResident.setHealth(0);
        var saved = new Villager(EntityTypes.VILLAGER, level); saved.snapTo(4130, 8, 0);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        check.accept(saved.save(output), "nitwit death: save unloaded resident fixture");
        var savedEntities = new ListTag(); savedEntities.add(output.buildResult());
        var savedChunk = NbtUtils.addCurrentDataVersion(new CompoundTag()); savedChunk.put("Entities", savedEntities);
        savedChunk.store("Position", ChunkPos.CODEC, saved.chunkPosition());
        ReputationScan.storage(level).write(saved.chunkPosition(), savedChunk).join();
        check.accept(!level.areEntitiesLoaded(saved.chunkPosition().pack()) && !level.isLoaded(saved.blockPosition()),
            "nitwit death: saved resident starts in an unloaded chunk");
        witness.getGossips().clear(); second.getGossips().clear(); unseen.getGossips().clear();
        var victim = villager(level, VillagerProfession.NITWIT, 4096, 8, 0);
        witnesses(level, victim, witness, second);
        victim.setHealth(1);
        check.accept(victim.hurtServer(level, damage, 2) && !victim.isAlive(), "nitwit death: actual lethal melee damage");
        int minor = value(witness, player, GossipType.MINOR_NEGATIVE);
        check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 26, "nitwit death: vanilla 25 plus exactly one major negative");
        check.accept(minor >= 10 && minor <= 20, "nitwit death: minor negative is inside inclusive 10..20");
        check.accept(value(second, player, GossipType.MAJOR_NEGATIVE) == 26
            && value(second, player, GossipType.MINOR_NEGATIVE) == minor, "nitwit death: all visible witnesses share one random roll");
        check.accept(value(unseen, player, GossipType.MAJOR_NEGATIVE) == 1
            && value(unseen, player, GossipType.MINOR_NEGATIVE) == minor, "nitwit death: nonwitness resident immediately gets the village penalty");
        check.accept(value(distant, player, GossipType.MAJOR_NEGATIVE) == 1
            && value(distant, player, GossipType.MINOR_NEGATIVE) == minor, "nitwit death: far resident in the connected village gets the same penalty");
        check.accept(value(otherVillage, player, GossipType.MAJOR_NEGATIVE) == 0
            && value(otherVillage, player, GossipType.MINOR_NEGATIVE) == 0, "nitwit death: another village gets no penalty");
        check.accept(value(deadResident, player, GossipType.MAJOR_NEGATIVE) == 0, "nitwit death: dead residents are excluded");
        check.accept(value(unseen, player, GossipType.MINOR_POSITIVE) == 0, "nitwit death: killing a villager never earns protection credit");
        check.accept(witness.getPlayerReputation(player) == -130 - minor, "nitwit death: raw gossip uses vanilla reputation weights");
        check.accept(!level.areEntitiesLoaded(saved.chunkPosition().pack()) && !level.isLoaded(saved.blockPosition())
            && ReputationScan.storage(level).read(saved.chunkPosition()).join().orElseThrow().equals(savedChunk),
            "nitwit death: whole-village penalty neither loads nor edits saved resident");
        var restoredGossip = GossipContainer.CODEC.parse(JsonOps.INSTANCE,
            GossipContainer.CODEC.encodeStart(JsonOps.INSTANCE, unseen.getGossips()).getOrThrow()).getOrThrow();
        check.accept(restoredGossip.getGossipEntries().get(player.getUUID()).getInt(GossipType.MAJOR_NEGATIVE) == 1
            && restoredGossip.getReputation(player.getUUID(), type -> true) == -5 - minor,
            "nitwit death: nonwitness +1 and minor penalty survive gossip serialization");
        victim.die(damage);
        check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 51
            && value(witness, player, GossipType.MINOR_NEGATIVE) == minor,
            "nitwit death: duplicate die adds no second mod penalty while leaving vanilla behaviour intact");

        witness.getGossips().clear();
        var arrow = new Arrow(EntityTypes.ARROW, level); arrow.setOwner(player);
        victim = villager(level, VillagerProfession.NITWIT, 4096, 8, 0);
        witnesses(level, victim, witness);
        victim.setHealth(1); victim.hurtServer(level, level.damageSources().arrow(arrow, player), 2);
        check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 26
            && value(witness, player, GossipType.MINOR_NEGATIVE) >= 10, "nitwit death: lethal player-owned arrow is attributed to its owner");

        for (var source : List.of(level.damageSources().lava(), level.damageSources().fall(),
            level.damageSources().inWall(), level.damageSources().cactus())) {
            witness.getGossips().clear();
            victim = villager(level, VillagerProfession.NITWIT, 4096, 8, 0);
            witnesses(level, victim, witness); victim.setLastHurtByPlayer(player, 100);
            die(victim, source);
            check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 0
                && value(witness, player, GossipType.MINOR_NEGATIVE) == 0,
                "nitwit death: prior player hit does not credit environmental death " + source.type().msgId());
        }
        witness.getGossips().clear();
        var mob = new Zombie(level);
        victim = villager(level, VillagerProfession.NITWIT, 4096, 8, 0);
        witnesses(level, victim, witness); die(victim, level.damageSources().mobAttack(mob));
        check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 0
            && value(witness, player, GossipType.MINOR_NEGATIVE) == 0, "nitwit death: nonplayer killer never penalizes a player");
        var mobEntries = witness.getGossips().getGossipEntries().get(mob.getUUID());
        check.accept(mobEntries != null && mobEntries.getInt(GossipType.MAJOR_NEGATIVE) == 25
            && mobEntries.getInt(GossipType.MINOR_NEGATIVE) == 0, "nitwit death: nonplayer killer retains vanilla-only gossip");
        witness.getGossips().clear();
        var pet = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND); pet.setOwner(player);
        victim = villager(level, VillagerProfession.NITWIT, 4096, 8, 0);
        witnesses(level, victim, witness); victim.setLastHurtByPlayer(player, 100);
        die(victim, level.damageSources().mobAttack(pet));
        check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 0
            && value(witness, player, GossipType.MINOR_NEGATIVE) == 0, "nitwit death: player-owned pet kill is not a direct player kill");

        witness.getGossips().clear();
        victim = villager(level, VillagerProfession.NITWIT, 4096, 8, 0);
        die(victim, damage);
        check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 1
            && value(witness, player, GossipType.MINOR_NEGATIVE) >= 10, "nitwit death: absent witness memory still applies the whole-village penalty");
        witness.getGossips().clear();
        victim = villager(level, VillagerProfession.NITWIT, 4096, 8, 0);
        victim.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, NearestVisibleLivingEntities.empty());
        die(victim, damage);
        check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 1, "nitwit death: empty visible memory still applies the whole-village penalty");
        witness.getGossips().clear();
        victim = villager(level, VillagerProfession.NITWIT, 4400, 8, 0);
        var outsideWitness = villager(level, VillagerProfession.NONE, 4402, 8, 0);
        witnesses(level, victim, outsideWitness); die(victim, damage);
        check.accept(value(outsideWitness, player, GossipType.MAJOR_NEGATIVE) == 25
            && value(outsideWitness, player, GossipType.MINOR_NEGATIVE) == 0, "nitwit death: no recognized village leaves only vanilla punishment");

        // The vanilla memory applies its real line-of-sight predicate to the supplied candidate list.
        for (int y = 8; y <= 10; y++) for (int z = -1; z <= 1; z++)
            level.setBlock(new BlockPos(4097, y, z), Blocks.STONE.defaultBlockState(), 3);
        victim = villager(level, VillagerProfession.NITWIT, 4096, 8, 0);
        witnesses(level, victim, witness); die(victim, damage);
        check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 1
            && value(witness, player, GossipType.MINOR_NEGATIVE) >= 10, "nitwit death: an occluded resident gets village gossip without vanilla witness gossip");

        // Separate clear area, deterministic seeds; test every random outcome, including both endpoints.
        witness.snapTo(4106, 8, 0);
        var outcomes = new HashSet<Integer>();
        for (int seed = 0; seed < 128; seed++) {
            witness.getGossips().clear();
            victim = villager(level, VillagerProfession.NITWIT, 4104, 8, 0);
            witnesses(level, victim, witness); victim.getRandom().setSeed(seed); die(victim, damage);
            int roll = value(witness, player, GossipType.MINOR_NEGATIVE);
            outcomes.add(roll);
            check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 26 && roll >= 10 && roll <= 20,
                "nitwit death: seeded death stays in range " + seed);
        }
        check.accept(outcomes.equals(Set.of(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)), "nitwit death: all eleven outcomes including 10 and 20 occur");
        witness.getGossips().clear();
        SaviorGossip.add(witness, player.getUUID(), GossipType.MAJOR_NEGATIVE, 99);
        SaviorGossip.add(witness, player.getUUID(), GossipType.MINOR_NEGATIVE, 199);
        victim = villager(level, VillagerProfession.NITWIT, 4104, 8, 0);
        witnesses(level, victim, witness); die(victim, damage);
        check.accept(value(witness, player, GossipType.MAJOR_NEGATIVE) == 100
            && value(witness, player, GossipType.MINOR_NEGATIVE) == 200, "nitwit death: repeated punishment respects vanilla gossip caps");
    }

    private static void die(Villager victim, DamageSource source) { victim.setHealth(0); victim.die(source); }

    private static Villager villager(ServerLevel level, net.minecraft.resources.ResourceKey<VillagerProfession> profession,
                                      double x, double y, double z) {
        var villager = new Villager(EntityTypes.VILLAGER, level);
        level.getChunk((int) Math.floor(x / 16), (int) Math.floor(z / 16));
        villager.setVillagerData(villager.getVillagerData().withProfession(
            level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(profession)));
        villager.snapTo(x, y, z); villager.setNoAi(true); level.addFreshEntity(villager);
        return villager;
    }
    private static void witnesses(ServerLevel level, Villager victim, LivingEntity... witnesses) {
        victim.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            new NearestVisibleLivingEntities(level, victim, List.of(witnesses)));
    }
    private static int value(Villager villager, ServerPlayer player, GossipType type) {
        var entry = villager.getGossips().getGossipEntries().get(player.getUUID());
        return entry == null ? 0 : entry.getInt(type);
    }
}
