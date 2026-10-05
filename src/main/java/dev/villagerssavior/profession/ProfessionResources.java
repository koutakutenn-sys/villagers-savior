package dev.villagerssavior.profession;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.*;

/**
 * Per-profession production tables. Every entry carries its own production model:
 *
 * <pre>
 * ProductionPerRestock  how much one successful workstation restock may add
 * DailyProductionCap    hard limit per villager and in-game day
 * InventoryTarget       once this many are held, production stops
 * InventoryHardCap      Villagers' Savior never produces past this, no matter what
 * </pre>
 *
 * The hard cap only limits what this mod produces; items the villager obtained through vanilla behaviour
 * are never removed.
 */
public final class ProfessionResources {
    public record Resource(Item item, int perRestock, int dailyCap, int target, int hardCap) {
        public Resource {
            if (target > hardCap) throw new IllegalArgumentException("target above hard cap: " + item);
        }
    }
    private static final Map<ResourceKey<VillagerProfession>, List<Resource>> TABLES = new LinkedHashMap<>();
    private static Resource produce(Item item, int perRestock, int dailyCap, int target, int hardCap) {
        return new Resource(item, perRestock, dailyCap, target, hardCap);
    }
    /** Recorded for its inventory target / hard cap only: the farmer never conjures bread. */
    private static Resource capOnly(Item item, int target, int hardCap) {
        return new Resource(item, 0, 0, target, hardCap);
    }
    static {
        TABLES.put(VillagerProfession.FARMER, List.of(
            produce(Items.WHEAT, 1, 4, PopulationSupply.RESERVE, 48),
            produce(Items.CARROT, 1, 4, PopulationSupply.RESERVE, 48),
            produce(Items.POTATO, 1, 4, PopulationSupply.RESERVE, 48),
            produce(Items.BEETROOT, 1, 4, PopulationSupply.RESERVE, 48),
            capOnly(Items.BREAD, 24, 48)));
        TABLES.put(VillagerProfession.FISHERMAN, List.of(
            produce(Items.COD, 1, 2, 8, 16),
            produce(Items.SALMON, 1, 2, 8, 16),
            produce(Items.COAL, 1, 1, 2, 4)));
        TABLES.put(VillagerProfession.BUTCHER, List.of(
            produce(Items.BEEF, 1, 1, 6, 12),
            produce(Items.PORKCHOP, 1, 1, 6, 12),
            produce(Items.CHICKEN, 1, 1, 6, 12),
            produce(Items.MUTTON, 1, 1, 6, 12),
            produce(Items.RABBIT, 1, 1, 6, 12),
            produce(Items.COAL, 1, 1, 2, 4)));
        TABLES.put(VillagerProfession.FLETCHER, List.of(
            produce(Items.ARROW, 2, 4, 8, 16),
            produce(Items.FLINT, 1, 1, 4, 8),
            produce(Items.FEATHER, 1, 1, 4, 8),
            produce(Items.STICK, 1, 1, 4, 8)));
        // Only white wool is produced; naturally acquired wool of other colours is left untouched.
        TABLES.put(VillagerProfession.SHEPHERD, List.of(produce(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), 1, 1, 4, 8)));
        TABLES.put(VillagerProfession.MASON, List.of(
            produce(Items.STONE, 1, 2, 8, 16),
            produce(Items.STONE_BRICKS, 1, 2, 8, 16)));
        TABLES.put(VillagerProfession.CLERIC, List.of(
            produce(Items.REDSTONE, 1, 1, 2, 4),
            produce(Items.GLOWSTONE_DUST, 1, 1, 2, 4),
            produce(Items.LAPIS_LAZULI, 1, 1, 2, 4)));
        TABLES.put(VillagerProfession.TOOLSMITH, List.of(produce(Items.IRON_INGOT, 1, 1, 4, 8)));
        TABLES.put(VillagerProfession.WEAPONSMITH, List.of(produce(Items.IRON_INGOT, 1, 1, 4, 8)));
        TABLES.put(VillagerProfession.ARMORER, List.of(produce(Items.IRON_INGOT, 1, 1, 4, 8)));
        TABLES.put(VillagerProfession.LEATHERWORKER, List.of(produce(Items.LEATHER, 1, 1, 4, 8)));
        TABLES.put(VillagerProfession.LIBRARIAN, List.of(
            produce(Items.PAPER, 1, 2, 8, 16),
            produce(Items.BOOK, 1, 1, 2, 4)));
        TABLES.put(VillagerProfession.CARTOGRAPHER, List.of(produce(Items.PAPER, 1, 2, 8, 16)));
    }
    private ProfessionResources() {}
    /** Production entries for a profession; empty for unemployed, nitwits and unknown professions. */
    public static List<Resource> of(ResourceKey<VillagerProfession> profession) {
        return TABLES.getOrDefault(profession, List.of());
    }
    public static Set<ResourceKey<VillagerProfession>> professions() {
        return Collections.unmodifiableSet(TABLES.keySet());
    }
    /** The resource entry for an item, or null when this profession does not track it. */
    public static Resource find(ResourceKey<VillagerProfession> profession, Item item) {
        for (Resource resource : of(profession)) if (resource.item() == item) return resource;
        return null;
    }
    /**
     * How many items a restock may add: never more than one production step, never past the inventory
     * target, and never past the hard cap even when the target was somehow exceeded by vanilla pickups.
     */
    public static int producible(Resource resource, int held) {
        if (resource.perRestock() <= 0 || held >= resource.hardCap() || held >= resource.target()) return 0;
        int room = Math.min(resource.target(), resource.hardCap()) - held;
        return Math.max(0, Math.min(resource.perRestock(), room));
    }
}
