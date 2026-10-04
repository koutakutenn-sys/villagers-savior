package dev.villagerssavior.client;

import dev.villagerssavior.debug.ReputationQuery;
import dev.villagerssavior.debug.VillagerDetails;
import dev.villagerssavior.profession.LibrarianInteraction;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.util.FormattedCharSequence;
import static dev.villagerssavior.debug.VillagerInspection.text;

/** Target-scoped HUD cache: delayed packets can never display another villager's reputation. */
public final class VillagerHud {
    private static VillagerDetails details;
    private static int ticks, nextQuery, receivedAt;
    private static UUID targetUuid;
    private static int targetId = -1;
    private VillagerHud() {}
    public static void clear() { details = null; targetUuid = null; targetId = -1; nextQuery = ticks; }
    private static Villager target(Minecraft client) {
        if (client.player == null || client.level == null || !client.player.isAlive() || client.player.isSpectator()
            || client.gui.screen() != null || client.gui.hud.isHidden()) return null;
        return client.crosshairPickEntity instanceof Villager villager && villager.isAlive()
            && client.player.isWithinEntityInteractionRange(villager, 0) ? villager : null;
    }
    public static void tick(Minecraft client) {
        ticks++;
        Villager villager = target(client);
        if (villager == null || !ClientPlayNetworking.canSend(ReputationQuery.TYPE)) {
            // Preserve the poll deadline when looking away, so rapidly changing targets cannot spam.
            details = null; targetUuid = null; targetId = -1; return;
        }
        if (!villager.getUUID().equals(targetUuid) || villager.getId() != targetId) {
            details = null; targetUuid = villager.getUUID(); targetId = villager.getId();
        }
        if (details != null && ticks - receivedAt > 40) details = null;
        if (ticks >= nextQuery) {
            ClientPlayNetworking.send(new ReputationQuery(targetId)); nextQuery = ticks + 10;
        }
    }
    public static void accept(Minecraft client, VillagerDetails report) {
        Villager villager = target(client);
        if (villager == null || report.reputation().entityId() != villager.getId()
            || !report.reputation().villagerUuid().equals(villager.getUUID().toString())
            || !villager.getUUID().equals(targetUuid)) return;
        details = report; receivedAt = ticks;
    }
    public static VillagerDetails current() { return details; }
    public static List<Component> lines(Minecraft client) {
        Villager villager = target(client);
        if (villager == null || details == null || ticks - receivedAt > 40
            || details.reputation().entityId() != villager.getId()
            || !details.reputation().villagerUuid().equals(villager.getUUID().toString())) return List.of();
        var report = details.reputation();
        List<Component> lines = new ArrayList<>();
        lines.add(text("title", villager.getDisplayName(),
            Component.translatable("entity.minecraft.villager." + report.profession()), report.level()));
        lines.add(text("reputation", report.total(), Component.translatable(LibrarianInteraction.standing(report.total()))));
        lines.addAll(details.offers());
        lines.add(text("request_hint", SaviorClient.requestFood.getTranslatedKeyMessage()));
        return lines;
    }
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        var client = Minecraft.getInstance();
        var source = lines(client);
        if (source.isEmpty()) return;
        int width = Math.min(280, graphics.guiWidth() - 24);
        if (width < 60 || graphics.guiHeight() < 50) return;
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : source) wrapped.addAll(client.font.split(line, width - 12));
        int spacing = client.font.lineHeight + 3;
        int maxLines = Math.max(1, (graphics.guiHeight() - 24) / spacing);
        if (wrapped.size() > maxLines) {
            wrapped = new ArrayList<>(wrapped.subList(0, maxLines));
            wrapped.set(maxLines - 1, Component.literal("…").getVisualOrderText());
        }
        int height = wrapped.size() * spacing + 9;
        int x = 8, y = Math.min(graphics.guiHeight() / 3, graphics.guiHeight() - height - 8);
        graphics.fill(x, y, x + width, y + height, 0xD0182028);
        graphics.fill(x, y, x + 2, y + height, 0xFF6BCB91);
        for (int i = 0; i < wrapped.size(); i++)
            graphics.text(client.font, wrapped.get(i), x + 6, y + 5 + i * spacing,
                i == 0 ? 0xFFF0D990 : 0xFFF4F4F4, true);
    }
}
