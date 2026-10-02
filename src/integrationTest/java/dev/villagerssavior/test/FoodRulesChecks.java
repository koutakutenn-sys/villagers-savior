package dev.villagerssavior.test;
import dev.villagerssavior.FoodRules;
import java.util.Random;

public final class FoodRulesChecks {
    public static int run() {
        int checks = 0;
        int[] reps = {Integer.MIN_VALUE, -300, -100, -99, -50, -28, 0, 50, 99, 100, 150, Integer.MAX_VALUE};
        for (int h = 0; h <= 20; h++) for (int r : reps) for (int s = 0; s <= 100; s++) {
            int expected = 0;
            if (h < 20) {
                long share = Math.max(0L, Math.min(200L, 100L + Math.min(r,100)));
                int offered = (int) (Math.max(0, s - 20) * share / 400);
                int rp = Math.max(0, Math.min(r, 100));
                int needed = Math.max(2, Math.min(8, ((20 - h) * (250 + 3 * rp) + 999) / 1000));
                expected = Math.min(needed, offered);
            }
            require(FoodRules.budget(h,r,s) == expected, "budget h="+h+" r="+r+" s="+s); checks++;
        }
        Random random = new Random(2642);
        for (int trial = 0; trial < 2000; trial++) {
            int[] nutrition = new int[4], counts = new int[4];
            for (int i = 0; i < 4; i++) { nutrition[i] = random.nextInt(12) - 1; counts[i] = random.nextInt(4); }
            int budget = random.nextInt(9), best = 0;
            for (int a = 0; a <= counts[0]; a++) for (int b = 0; b <= counts[1]; b++)
            for (int c = 0; c <= counts[2]; c++) for (int d = 0; d <= counts[3]; d++) {
                if ((a>0 && nutrition[0]<=0)||(b>0 && nutrition[1]<=0)||(c>0 && nutrition[2]<=0)||(d>0 && nutrition[3]<=0)) continue;
                int sum=a*nutrition[0]+b*nutrition[1]+c*nutrition[2]+d*nutrition[3];
                if (sum <= budget) best = Math.max(best,sum);
            }
            int[] selected = FoodRules.select(nutrition,counts,budget);
            int actual = 0;
            for (int i = 0; i < 4; i++) { require(selected[i]>=0 && selected[i]<=counts[i],"stock bound"); actual += selected[i]*nutrition[i]; checks++; }
            require(actual == best,"optimal knapsack"); checks++;
        }
        require(FoodRules.emergencySlot(new int[]{5,4,3,1,-1},new int[]{1,1,1,1,64})==3,"lowest food"); checks++;
        require(FoodRules.emergencySlot(new int[]{-1,0,5},new int[]{64,1,1})==1,"zero nutrition edible"); checks++;
        require(FoodRules.emergencySlot(new int[]{-1,1},new int[]{64,0})==-1,"no food"); checks++;
        require(FoodRules.budget(0,100,Long.MAX_VALUE)==8,"huge stock safe"); checks++;
        return checks;
    }
    public static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) { System.out.println("PASS: " + run() + " food rule checks"); }
}
