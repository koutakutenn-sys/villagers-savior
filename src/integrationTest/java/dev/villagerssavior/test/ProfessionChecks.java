package dev.villagerssavior.test;

import dev.villagerssavior.debug.ReputationDebug;
import dev.villagerssavior.debug.ReputationReport;
import dev.villagerssavior.profession.Conversions;
import dev.villagerssavior.profession.LibrarianInteraction;
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
