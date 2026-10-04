package dev.villagerssavior.test;

import dev.villagerssavior.debug.ReputationDebug;
import dev.villagerssavior.debug.ReputationReport;
import dev.villagerssavior.SaviorState;
import dev.villagerssavior.profession.Conversions;
import dev.villagerssavior.profession.LibrarianInteraction;
import dev.villagerssavior.profession.PopulationSupply;
import dev.villagerssavior.profession.Rations;
import dev.villagerssavior.profession.Repairs;
import java.util.List;

/** Pure rule checks for the profession services and the read-only reputation view. */
public final class ProfessionChecks {
    private static int checks;
    private ProfessionChecks() {}
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static int run() {
        checks = 0;
        // Farmer travel rations: P = min(E, floor(E * clamp(0.10 + 0.002R, 0, 0.30)), 8)
        require(Rations.budget(20, 24) == 0, "ration requires reputation 25");
        require(Rations.budget(20, 25) == 3, "ration share at 25 is 15%");
        require(Rations.budget(20, 50) == 4, "ration share at 50 is 20%");
        require(Rations.budget(20, 75) == 5, "ration share at 75 is 25%");
        require(Rations.budget(20, 100) == 6, "ration share at 100 is 30%");
        require(Rations.budget(20, 1000) == 6, "ration share is capped at 30%");
        require(Rations.budget(1000, 100) == 8, "ration budget is capped at 8");
        require(Rations.budget(0, 100) == 0, "no surplus means no ration");
        require(Rations.budget(-5, 100) == 0, "negative surplus means no ration");
        for (int surplus = 0; surplus <= 200; surplus++)
            for (int reputation : new int[]{Integer.MIN_VALUE, -1, 0, 24, 25, 50, 75, 100, 150}) {
                int budget = Rations.budget(surplus, reputation);
                require(budget >= 0 && budget <= Math.min(8, Math.max(0, surplus)),
                    "ration budget stays within bounds");
                require(reputation < 25 || budget <= surplus * 300 / 1000,
                    "ration budget never exceeds the reputation share");
            }
        // Repair quotas and vanilla anvil amounts
        require(Repairs.dailyUnits(0) == 2, "repair quota base is two");
        require(Repairs.dailyUnits(50) == 3, "repair quota grows at 50");
        require(Repairs.dailyUnits(90) == 4, "repair quota grows at 90");
        require(Repairs.amountPerUnit(100, 0) == 25, "vanilla quarter durability per material");
        require(Repairs.amountPerUnit(100, 75) == 35, "trusted repairs restore more per material");
        require(Repairs.amountPerUnit(1, 0) == 1, "tiny durability still restores at least one");
        // Conversion caps scale with reputation but never conjure materials
        require(Conversions.capFor(0) == 4, "processing cap base is four");
        require(Conversions.capFor(75) == 8, "processing cap grows at 75");
        // Librarian standing bands read the real reputation
        require(LibrarianInteraction.standing(-1).endsWith("wary"), "negative reputation is wary");
        require(LibrarianInteraction.standing(0).endsWith("neutral"), "zero reputation is neutral");
        require(LibrarianInteraction.standing(24).endsWith("neutral"), "below threshold stays neutral");
        require(LibrarianInteraction.standing(25).endsWith("trusted"), "threshold is trusted");
        require(LibrarianInteraction.standing(75).endsWith("honored"), "75 is honored");
        // Population supply: only while the village has spare beds, and never more than one reserve
        require(PopulationSupply.needsSupply(3, 5), "spare beds mean the village can grow");
        require(!PopulationSupply.needsSupply(5, 5), "a village at capacity needs no supply");
        require(!PopulationSupply.needsSupply(6, 5), "a village over capacity needs no supply");
        require(PopulationSupply.topUp(0) == 12, "an empty stock is topped up to the reserve");
        require(PopulationSupply.topUp(5) == 7, "a partial stock is only topped up");
        require(PopulationSupply.topUp(12) == 0, "a full reserve is left alone");
        require(PopulationSupply.topUp(20) == 0, "the reserve is never exceeded");
        require(PopulationSupply.pickIndex(new int[]{0, 0, 0, 0}) == -1, "no crop evidence means no supply");
        require(PopulationSupply.pickIndex(new int[]{3, 9, 2, 1}) == 1, "the most plentiful crop wins");
        require(PopulationSupply.pickIndex(new int[]{5, 0, 0, 0}) == 0, "a single crop is picked");
        require(PopulationSupply.pickIndex(new int[]{4, 4, 0, 0}) == 0, "ties keep the earliest crop");
        // Daily production counters are per villager, per item and per in-game day.
        var ledger = new SaviorState();
        var villager = java.util.UUID.randomUUID();
        require(ledger.dailyVillagerGrant("produce:test", villager, 0, 4, 4) == 4, "first production day grants the cap");
        require(ledger.dailyVillagerGrant("produce:test", villager, 100, 4, 4) == 0, "same day grants nothing more");
        require(ledger.dailyVillagerGrant("produce:test", villager, 23999, 4, 4) == 0, "the day boundary is exclusive");
        require(ledger.dailyVillagerGrant("produce:test", villager, 24000, 4, 4) == 4, "a new day resets production");
        require(ledger.dailyVillagerGrant("produce:test", java.util.UUID.randomUUID(), 100, 4, 4) == 4,
            "production counters are per villager");
        require(ledger.dailyVillagerGrant("produce:other", villager, 100, 4, 4) == 4,
            "production counters are per produced item");
        // Debug view shows exactly the required fields, all from the server report
        ReputationReport report = new ReputationReport(7, "uuid-1", "farmer", 3, 42, 25, 5, 12, 3, 1);
        List<String> lines = ReputationDebug.lines(report);
        String text = String.join("\n", lines);
        require(text.contains("uuid-1"), "debug shows the villager uuid");
        require(text.contains("farmer"), "debug shows the profession");
        require(text.contains("total reputation: 42"), "debug shows the total reputation");
        require(text.contains("MINOR_POSITIVE: 25"), "debug shows MINOR_POSITIVE");
        require(text.contains("MAJOR_POSITIVE: 5"), "debug shows MAJOR_POSITIVE");
        require(text.contains("TRADING: 12"), "debug shows TRADING");
        require(text.contains("MINOR_NEGATIVE: 3"), "debug shows MINOR_NEGATIVE");
        require(text.contains("MAJOR_NEGATIVE: 1"), "debug shows MAJOR_NEGATIVE");
        return checks;
    }
    public static void main(String[] args) {
        System.out.println("PASS: " + run() + " profession rule checks");
    }
}
