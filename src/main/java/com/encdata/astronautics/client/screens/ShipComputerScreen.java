package com.encdata.astronautics.client.screens;

import com.encdata.panelworks.client.gui.GuiRenderer;
import com.encdata.panelworks.client.gui.GuiTheme;
import com.encdata.astronautics.content.blocks.ship_computer.ShipComputerMenu;
import com.encdata.astronautics.content.blocks.ship_computer.ShipComputerMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ShipComputerScreen extends AbstractContainerScreen<ShipComputerMenu> {
    public ShipComputerScreen(ShipComputerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 164;
        imageHeight = 178;
    }

    @Override
    protected void init() {
        super.init();
        addModeButton(ShipComputerMode.OFF, 10, 27, 68);
        addModeButton(ShipComputerMode.POSITION, 82, 27, 68);
        addModeButton(ShipComputerMode.ROTATION, 10, 49, 68);
        addModeButton(ShipComputerMode.BOTH, 82, 49, 68);
        addModeButton(ShipComputerMode.ORBIT, 10, 71, 140);
        addAltitudeButton("gui.astronautics.ship_computer.altitude_down", 11, 24);
        addAltitudeButton("gui.astronautics.ship_computer.altitude_up", 12, 92);
    }

    private void addModeButton(ShipComputerMode mode, int x, int y, int width) {
        Button button = Button.builder(Component.translatable(
                        "gui.astronautics.ship_computer.mode." + mode.name().toLowerCase()),
                ignored -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, mode.ordinal()))
                .bounds(leftPos + x, topPos + y, width, 19).build();
        if (mode == ShipComputerMode.ORBIT) button.setFGColor(0xFFE6C46A);
        addRenderableWidget(button);
    }

    private void addAltitudeButton(String key, int menuButton, int x) {
        addRenderableWidget(Button.builder(Component.translatable(key),
                button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, menuButton))
                .bounds(leftPos + x, topPos + 132, 48, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiRenderer.renderWindow(graphics, leftPos, topPos, imageWidth, imageHeight, GuiTheme.MINECRAFT);
        GuiRenderer.renderMachinePanel(graphics, leftPos + 7, topPos + 19, 148, 154, GuiTheme.MINECRAFT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 9, 8, 0xFF404040, false);
        graphics.drawCenteredString(font,
                Component.translatable("gui.astronautics.ship_computer.target_altitude"), 82, 103, 0xFF404040);
        graphics.drawCenteredString(font,
                Component.translatable("gui.astronautics.ship_computer.altitude_value", menu.targetAltitude()),
                82, 115, 0xFF404040);
    }
}
