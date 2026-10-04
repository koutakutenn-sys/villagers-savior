package dev.villagerssavior.test;

import java.util.*;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Observes actual server command packets and localized client chat through Fabric message events. */
public final class ScanLiveClient {
    private static final List<Component> messages = new ArrayList<>();
    private static AuditNet.Reply reply;
    private static int code = 20, wait;
    public static void initialize() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) messages.add(message.copy()); });
    }
    public static void accept(AuditNet.Reply packet) { reply = packet; }
    public static void start() { send(20); }
    private static void send(int next) {
        code = next; wait = 0; reply = null; messages.clear(); ClientPlayNetworking.send(new AuditNet.Control(next));
    }
    private static List<TranslatableContents> contents(String key) {
        List<TranslatableContents> result = new ArrayList<>();
        for (var message : messages) collect(message, key, result);
        return result;
    }
    private static void collect(Component message, String key, List<TranslatableContents> result) {
        if (message.getContents() instanceof TranslatableContents contents) {
            if (contents.getKey().equals("debug.villagers_savior." + key)) result.add(contents);
            for (var argument : contents.getArgs()) if (argument instanceof Component child) collect(child, key, result);
        }
        for (var sibling : message.getSiblings()) collect(sibling, key, result);
    }
    private static int integer(Object value) { return ((Number) value).intValue(); }
    private static void summary(BiConsumer<Boolean, String> check, int total, int live, int saved, int sum) {
        var args = contents("summary").getFirst().getArgs();
        check.accept(integer(args[0]) == total && integer(args[1]) == live && integer(args[2]) == saved && integer(args[3]) == sum,
            "native scan " + code + " delivers exact aggregate/live/saved counts and uncapped reputation sum");
    }
    private static void rows(BiConsumer<Boolean, String> check) {
        var rows = contents("entry"); boolean valid = !rows.isEmpty(); int previous = Integer.MIN_VALUE;
        for (var row : rows) {
            var args = row.getArgs(); UUID.fromString((String) args[1]); int reputation = integer(args[6]);
            valid &= args[5] instanceof Component profession && !profession.getString().contains("entity.minecraft.villager.");
            valid &= args[7] instanceof Component status && !status.getString().contains("debug.villagers_savior.");
            valid &= reputation >= previous; previous = reputation;
        }
        check.accept(valid, "native scan " + code + " rows include full UUID, localized profession, real reputation and snapshot source, sorted lowest first");
    }
    public static boolean tick(Minecraft mc, BiConsumer<Boolean, String> check) {
        if (++wait > 900) throw new IllegalStateException("native scan timeout at " + code + ": " + messages);
        if (reply == null || reply.code() != code || wait < 30) return false;
        if (code == 20) {
            check.accept(reply.flag(), "native command fixtures include villagers in genuinely unloaded entity chunks");
            send(21); return false;
        }
        if (code == 21 || code == 24 || code == 25 || code == 29) {
            if (contents("summary").isEmpty() || contents("page").isEmpty()) return false;
            check.accept(contents("failed").isEmpty(), "native scan " + code + " completes asynchronously without storage error");
            if (code == 21 || code == 29) summary(check, 13, 13, 0, 458);
            if (code == 24) summary(check, 14, 13, 1, 470);
            if (code == 25) summary(check, 15, 13, 2, 569);
            rows(check);
            if (code == 21) check.accept(contents("entry").stream().anyMatch(row -> ((Component) row.getArgs()[5]).getString().equals("无业")),
                "native scan explicitly labels unemployed villager profession");
            check.accept(contents("entry").size() == 10, "native scan " + code + " first page contains ten individual villagers");
            check.accept(messages.stream().noneMatch(line -> line.getString().contains("debug.villagers_savior.")),
                "native scan " + code + " Chinese command messages resolve all translation keys");
            check.accept(reply.flag(), "native scan " + code + " leaves saved entity chunks unloaded");
            if (code == 24) {
                check.accept(integer(contents("village_scope").getFirst().getArgs()[0]) == 3 && !contents("saved_note").isEmpty(),
                    "native whole-village scan follows connected POIs, excludes neighbor and warns about saved gossip snapshots");
                Screenshot.grab(mc.gameDirectory, "scan-village.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            }
            if (code == 29) { check.accept(!contents("running").isEmpty(), "native duplicate scan is throttled"); return true; }
            send(code == 21 ? 22 : code == 24 ? 25 : 26); return false;
        }
        if (code == 22) {
            if (contents("page").isEmpty()) return false;
            var page = contents("page").getFirst().getArgs();
            check.accept(integer(page[0]) == 2 && integer(page[1]) == 2 && contents("entry").size() == 3,
                "native list page 2 displays the remaining three villagers");
            rows(check);
            check.accept(contents("entry").stream().anyMatch(row -> integer(row.getArgs()[6]) == 120),
                "native individual details preserve reputation above 100");
            Screenshot.grab(mc.gameDirectory, "scan-individuals-page-2.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            send(23); return false;
        }
        if (code == 23) {
            check.accept(!contents("invalid_page").isEmpty() && contents("entry").isEmpty(), "native invalid page is rejected");
            send(24); return false;
        }
        if (code == 26 || code == 27) {
            if (code == 27 && wait < 650) return false;
            check.accept(contents("scanning").isEmpty() && contents("summary").isEmpty() && !messages.isEmpty(),
                code == 26 ? "native radius zero is rejected by command parser" : "native unprivileged command source cannot scan");
            send(code + 1); return false;
        }
        if (code == 28) {
            check.accept(!contents("no_result").isEmpty() && contents("entry").isEmpty(), "native cached detail list expires after 30 game seconds");
            send(29); return false;
        }
        return false;
    }
}
