package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.content.misc.OxygenStorage;
import com.encdata.astronautics.registry.AstronauticsItems;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT)
public final class SuitOxygenHud {
    private static final int PANEL = 0xB8101010;
    private static final int EDGE = 0xFF555555;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int COPPER = 0xFFB87333;
    private static final int SAFE = 0xFF55AA55;
    private static final int WARN = 0xFFFFAA00;
    private static final int CRITICAL = 0xFFFF5555;

    private SuitOxygenHud() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.screen != null || player == null
                || !player.getItemBySlot(EquipmentSlot.HEAD)
                .is(AstronauticsItems.SPACE_SUIT_HELMET.get())) {
            return;
        }

        int oxygenAmount = OxygenStorage.get(player.getItemBySlot(EquipmentSlot.CHEST));
        int oxygenCapacity = OxygenStorage.SUIT_CAPACITY;
        int oxygenPercent = Math.clamp(Math.round(oxygenAmount * 100.0F / oxygenCapacity), 0, 100);
        var dimension = player.level().dimension();
        boolean ambientOxygen = !dimension.equals(SpaceConstants.SPACE_LEVEL)
                && SpaceCatalog.environmentForDimension(dimension).oxygen();
        boolean breathable = BreathabilityClientState.breathable() || ambientOxygen;
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        Component title = Component.translatable("hud.astronautics.life_support");
        Component oxygenText = Component.translatable("hud.astronautics.oxygen", oxygenPercent);
        Component status = status(oxygenAmount, oxygenCapacity, breathable);

        int unit = font.lineHeight;
        int padding = Math.max(1, unit / 2);
        int gap = Math.max(1, unit / 3);
        int edge = Math.max(1, unit / 9);
        int barHeight = Math.max(1, unit / 3);
        int barWidth = unit * 8;
        int statusColor = statusColor(oxygenAmount, oxygenCapacity, breathable);
        int contentWidth = Math.max(font.width(title),
                font.width(oxygenText) + gap + font.width(status));
        contentWidth = Math.max(contentWidth, barWidth);

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int margin = Math.max(unit, Math.min(screenWidth, screenHeight) / 64);
        int panelWidth = Math.min(contentWidth + padding * 2, screenWidth - margin * 2);
        int panelHeight = padding * 2 + unit * 2 + gap * 2 + barHeight;
        int x = margin;
        int y = margin;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
        graphics.fill(x, y, x + panelWidth, y + edge, EDGE);
        graphics.fill(x, y + panelHeight - edge, x + panelWidth, y + panelHeight, EDGE);
        graphics.fill(x, y, x + edge, y + panelHeight, EDGE);
        graphics.fill(x + panelWidth - edge, y, x + panelWidth, y + panelHeight, EDGE);

        int textX = x + padding;
        int titleY = y + padding;
        int oxygenY = titleY + unit + gap;
        int barY = oxygenY + unit + gap;
        graphics.drawString(font, title, textX, titleY, MUTED, false);
        graphics.drawString(font, oxygenText, textX, oxygenY, TEXT, false);
        graphics.drawString(font, status, x + panelWidth - padding - font.width(status),
                oxygenY, statusColor, false);

        int innerBarWidth = panelWidth - padding * 2;
        graphics.fill(textX, barY, textX + innerBarWidth, barY + barHeight, EDGE);
        int fillWidth = Math.round(innerBarWidth * Math.clamp(
                oxygenAmount / (float) oxygenCapacity, 0.0F, 1.0F));
        graphics.fill(textX, barY, textX + fillWidth, barY + barHeight, COPPER);
    }

    private static Component status(int oxygen, int capacity, boolean breathable) {
        if (breathable) return Component.translatable("hud.astronautics.air_ok");
        if (oxygen <= 0) return Component.translatable("hud.astronautics.vacuum");
        if (oxygen * 3 < capacity) {
            return Component.translatable("hud.astronautics.oxygen_low");
        }
        return Component.translatable("hud.astronautics.suit_ok");
    }

    private static int statusColor(int oxygen, int capacity, boolean breathable) {
        if (breathable || oxygen > 0 && oxygen * 3 >= capacity) {
            return SAFE;
        }
        return oxygen <= 0 ? CRITICAL : WARN;
    }
}
