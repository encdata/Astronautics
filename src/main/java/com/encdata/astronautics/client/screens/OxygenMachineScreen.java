package com.encdata.astronautics.client.screens;

import com.encdata.panelworks.client.gui.GuiRenderer;
import com.encdata.panelworks.client.gui.GuiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class OxygenMachineScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected OxygenMachineScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 176;
    }

    protected abstract int progress();

    protected abstract int progressCapacity();

    protected abstract Component progressText();

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiRenderer.renderWindow(graphics, x, y, imageWidth, imageHeight, GuiTheme.MINECRAFT);
        GuiRenderer.renderMachinePanel(graphics, x + 7, y + 19, 162, 61, GuiTheme.MINECRAFT);
        GuiRenderer.renderSlot(graphics, x + 25, y + 37, GuiTheme.MINECRAFT);
        GuiRenderer.renderPlayerInventorySlots(graphics, x, y, GuiTheme.MINECRAFT);
        GuiRenderer.renderProgressBar(graphics, x + 76, y + 48, progress(), progressCapacity(), GuiTheme.MINECRAFT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 9, 8, 0xFF404040, false);
        graphics.drawString(font, progressText(), 76, 39, 0xFF404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"), 9, 82, 0xFF404040, false);
    }
}
