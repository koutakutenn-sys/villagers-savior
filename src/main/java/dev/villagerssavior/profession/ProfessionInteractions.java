package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import dev.villagerssavior.Villages;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import java.util.*;
import java.util.Optional;

/**
 * Registry of profession services. Villagers without an entry (unemployed, nitwit, unknown) simply have no
 * profession service and keep the generic food gift, emergency relief and vanilla gossip.
 */
public final class ProfessionInteractions {
    /** Reputation every material service requires before it will help. */
    public static final int MIN_REPUTATION = 25;
    /** Default cooldown window for profession services that do not pick their own. */
    public static final long SERVICE_WINDOW = 6000L;
    private static final Map<ResourceKey<VillagerProfession>, ProfessionInteraction> SERVICES = new LinkedHashMap<>();
    static {
        register(VillagerProfession.FARMER, new FarmerInteraction());
        register(VillagerProfession.FISHERMAN, new FishermanInteraction());
        register(VillagerProfession.FLETCHER, new FletcherInteraction());
        register(VillagerProfession.SHEPHERD, new ShepherdInteraction());
        register(VillagerProfession.LEATHERWORKER, new LeatherworkerInteraction());
        register(VillagerProfession.LIBRARIAN, new LibrarianInteraction());
        register(VillagerProfession.CARTOGRAPHER, new CartographerInteraction());
        register(VillagerProfession.CLERIC, new ClericInteraction());
        register(VillagerProfession.MASON, new MasonInteraction());
        register(VillagerProfession.BUTCHER, new ButcherInteraction());
        register(VillagerProfession.TOOLSMITH, new ToolsmithInteraction());
        register(VillagerProfession.WEAPONSMITH, new WeaponsmithInteraction());
        register(VillagerProfession.ARMORER, new ArmorerInteraction());
    }
    private ProfessionInteractions() {}
    public static void register(ResourceKey<VillagerProfession> profession, ProfessionInteraction interaction) {
        SERVICES.put(profession, interaction);
    }
    /** Professions that currently have a service, for tests and documentation. */
    public static Set<ResourceKey<VillagerProfession>> implemented() {
        return Collections.unmodifiableSet(SERVICES.keySet());
    }
    /** Offers the villager's profession service; empty when this villager has nothing to offer. */
    public static Optional<String> serve(Villager villager, ServerPlayer player) {
        if (!(villager.level() instanceof ServerLevel level)) return Optional.empty();
        if (!villager.isAlive() || villager.isSleeping()) return Optional.empty();
        return villager.getVillagerData().profession().unwrapKey()
            .map(SERVICES::get)
            .flatMap(service -> service.serve(level, villager, player, level.getGameTime()));
    }
    /** Effective reputation: the real vanilla value, capped at 100 for service strength. */
    static int reputation(Villager villager, ServerPlayer player) {
        return Math.min(villager.getPlayerReputation(player), 100);
    }
    /**
     * Services that hand things out are keyed to the village so that changing villagers cannot reset them.
     * Villagers outside any recognised village get no village-scoped service, matching emergency relief.
     */
    static Optional<String> village(ServerLevel level, Villager villager) {
        return Villages.identify(level, villager.blockPosition());
    }
    static boolean ready(SaviorState state, String event, ServerPlayer player, String village, long now, long window) {
        return state.ready(event, player.getUUID(), village, now, window);
    }
    static void book(SaviorState state, String event, ServerPlayer player, String village, long now) {
        state.cooldown(event, player.getUUID(), village, now);
    }
}
