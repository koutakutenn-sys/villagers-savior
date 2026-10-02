package dev.villagerssavior.test;

import dev.villagerssavior.SaviorState;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.SavedDataStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Regression check for cross-restart persistence: the world SavedData must survive a real
 * encode-to-disk / decode-from-disk round trip through SavedDataStorage.
 *
 * <p>A null DataFixTypes in SaviorState.TYPE used to make every reload throw inside the storage's
 * guarded read, so the file was silently discarded and all cooldowns/history restarted from empty.
 */
public final class PersistenceCheck {
    private PersistenceCheck() {}
    public static int run(HolderLookup.Provider registries) {
        UUID player = UUID.randomUUID();
        String village = "persistence-regression-village";
        try {
            Path directory = Files.createTempDirectory("villagers-savior-persistence");
            try (SavedDataStorage first = new SavedDataStorage(directory, DataFixers.getDataFixer(), registries)) {
                SaviorState fresh = first.computeIfAbsent(SaviorState.TYPE);
                FoodRulesChecks.require(fresh.ready("emergency", player, village, 100), "fresh state has no cooldown");
                fresh.cooldown("emergency", player, village, 100);
            }
            try (SavedDataStorage second = new SavedDataStorage(directory, DataFixers.getDataFixer(), registries)) {
                SaviorState reloaded = second.computeIfAbsent(SaviorState.TYPE);
                FoodRulesChecks.require(!reloaded.ready("emergency", player, village, 100),
                    "cooldown survives a disk reload");
                FoodRulesChecks.require(reloaded.ready("emergency", player, village, 48100),
                    "reloaded emergency cooldown keeps its 2 day window");
                FoodRulesChecks.require(reloaded.ready("emergency", UUID.randomUUID(), village, 100),
                    "reloaded cooldown stays per player");
            }
            return 4;
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        System.out.println("PASS: " + run(RegistryAccess.EMPTY) + " persistence checks");
        System.exit(0);
    }
}
