package dev.villagerssavior.test;

import dev.villagerssavior.client.*;
import dev.villagerssavior.debug.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.npc.villager.Villager;

/** Native client automation; aims the real camera so vanilla supplies crosshairPickEntity. */
public final class HudLiveClient {
    private static final List<String> checks = new ArrayList<>();
    private static int ticks, stage = -2, wait, entity;
    private static AuditNet.Reply reply;
    private static VillagerDetails old;
    private static boolean aim = true;
    public static void initialize() {
        if (Boolean.getBoolean("savior.audit.scan")) ScanLiveClient.initialize();
        ClientPlayNetworking.registerGlobalReceiver(AuditNet.Reply.TYPE, (report, context) -> {
            reply = report; if (Boolean.getBoolean("savior.audit.scan")) ScanLiveClient.accept(report);
        });
        ClientTickEvents.END_CLIENT_TICK.register(HudLiveClient::tick);
    }
    private static void check(boolean value, String name) {
        checks.add((value ? "PASS " : "FAIL ") + name); System.out.println(checks.getLast());
    }
    private static void control(int code) { reply = null; ClientPlayNetworking.send(new AuditNet.Control(code)); wait = 0; }
    private static void finish(Minecraft mc, String failure) {
        if (failure != null) check(false, failure);
        try { ClientPlayNetworking.send(new AuditNet.Control(99)); } catch (Exception ignored) {}
        mc.disconnect(new TitleScreen(), false);
        stage = 8; wait = 0;
    }
    private static void writeResultsAndStop(Minecraft mc) {
        try { Files.writeString(Path.of(System.getProperty("savior.audit.result", "../audit/hud/client-result.txt")),
            (checks.stream().anyMatch(line -> line.startsWith("FAIL")) ? "FAIL" : "PASS") + ": " + checks.size() + " checks\n" + String.join("\n", checks) + "\n");
        } catch (Exception e) { e.printStackTrace(); }
        mc.stop(); stage = 99;
    }
    private static void point(Minecraft mc) {
        if (!aim || mc.player == null || mc.level == null || !(mc.level.getEntity(entity) instanceof Villager v)) return;
        var vector = v.getEyePosition().subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-vector.x, vector.z)));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(vector.y, Math.hypot(vector.x, vector.z))));
    }
    private static void tick(Minecraft mc) {
        if (stage == 99) return;
        if (stage == 8) {
            if (++wait < 120) return;
            check(VillagerHud.current() == null && VillagerHud.lines(mc).isEmpty(), "disconnect clears cached villager data");
            writeResultsAndStop(mc); return;
        }
        if (++ticks > 2800) { finish(mc, "timeout at stage " + stage); return; }
        try {
            if (stage == -2) {
                if (mc.gui.overlay() != null || ticks < 40) return;
                check(Arrays.stream(mc.options.keyMappings).noneMatch(key -> key.getName().equals("key.villagers_savior.debug_reputation")), "G debug binding removed");
                int port = Integer.getInteger("savior.audit.port", 29957);
                ConnectScreen.startConnecting(new TitleScreen(), mc, new ServerAddress("127.0.0.1", port),
                    new ServerData("HUD audit", "127.0.0.1:" + port, ServerData.Type.OTHER), false, null);
                stage = -1; return;
            }
            if (mc.player == null || mc.level == null) return;
            if (stage == -1) { check(ClientPlayNetworking.canSend(ReputationQuery.TYPE), "automatic inspection channel advertised"); control(0); stage = 0; return; }
            wait++; point(mc);
            if (stage == 0) {
                if (reply == null) return;
                entity = reply.entity();
                var current = VillagerHud.current();
                if (wait < 60 || current == null) return;
                check(mc.crosshairPickEntity instanceof Villager, "real camera ray selects villager without injected crosshair");
                check(current.reputation().total() == 25, "automatic network HUD shows real reputation 25");
                check(HudInspectionChecks.has(current, "food_offer") && HudInspectionChecks.has(current, "after_food"), "HUD shows food and service priority");
                String text = VillagerHud.lines(mc).stream().map(line -> line.getString()).reduce("", (a, b) -> a + "\n" + b);
                check(!text.contains("hud.villagers_savior"), "HUD translation keys render as localized text");
                Screenshot.grab(mc.gameDirectory, "hud-food-services.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
                old = current; control(4); stage = 4; return;
            }
            if (stage == 4) {
                if (reply == null || wait < 30) return;
                check(reply.potatoes() == 40 && reply.bread() == 2 && reply.delivered() == 0, "automatic viewing leaves food and crafting materials untouched");
                control(1); stage = 1; return;
            }
            if (stage == 1) {
                var current = VillagerHud.current();
                if (reply == null || wait < 30 || current == null || current.reputation().total() != 75) return;
                check(HudInspectionChecks.has(current, "food_full") && HudInspectionChecks.has(current, "available"), "HUD refreshes reputation, full hunger and available service");
                Screenshot.grab(mc.gameDirectory, "hud-full-service.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
                control(2); stage = 2; return;
            }
            if (stage == 2) {
                var current = VillagerHud.current();
                if (reply == null || wait < 30 || current == null || !HudInspectionChecks.has(current, "cooldown")) return;
                check(reply.delivered() == 8 && reply.bread() == 0, "actual service crafts eight arrows from real materials");
                check(HudInspectionChecks.has(current, "cooldown"), "HUD refreshes actual service cooldown");
                aim = false; mc.player.setYRot(0); stage = 5; wait = 0; return;
            }
            if (stage == 5) {
                if (wait < 10) return;
                check(VillagerHud.current() == null && VillagerHud.lines(mc).isEmpty(), "looking away clears HUD");
                aim = true; stage = 6; wait = 0; return;
            }
            if (stage == 6) {
                if (wait < 20 || VillagerHud.current() == null) return;
                mc.gui.setScreen(new PauseScreen(false));
                check(VillagerHud.lines(mc).isEmpty(), "opening a screen hides HUD immediately");
                mc.gui.setScreen(null); mc.gui.hud.toggle();
                check(VillagerHud.lines(mc).isEmpty(), "F1 HUD toggle hides villager panel");
                mc.gui.hud.toggle(); control(3); stage = 3; return;
            }
            if (stage == 3) {
                if (reply == null) return;
                entity = reply.entity();
                var current = VillagerHud.current();
                if (wait < 30 || current == null || current.reputation().entityId() != entity) return;
                check(current.reputation().total() == 0 && HudInspectionChecks.has(current, "service_none"), "switching target replaces all reputation and service data");
                VillagerHud.accept(mc, old);
                check(VillagerHud.current() == current, "late packet for old villager cannot replace current HUD");
                Screenshot.grab(mc.gameDirectory, "hud-second-villager.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
                stage = 7; wait = 0; return;
            }
            if (stage == 7 && wait >= 20) {
                if (Boolean.getBoolean("savior.audit.scan")) { ScanLiveClient.start(); stage = 9; wait = 0; }
                else finish(mc, null);
            }
            if (stage == 9 && ScanLiveClient.tick(mc, HudLiveClient::check)) finish(mc, null);
        } catch (Throwable failure) { failure.printStackTrace(); finish(mc, failure.toString()); }
    }
}
