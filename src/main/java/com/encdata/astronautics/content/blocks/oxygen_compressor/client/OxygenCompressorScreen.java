package com.encdata.astronautics.content.blocks.oxygen_compressor.client;

import com.encdata.astronautics.client.screens.OxygenMachineScreen;
import com.encdata.astronautics.content.blocks.oxygen_compressor.OxygenCompressorBlockEntity;
import com.encdata.astronautics.content.blocks.oxygen_compressor.OxygenCompressorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class OxygenCompressorScreen extends OxygenMachineScreen<OxygenCompressorMenu> {
    public OxygenCompressorScreen(OxygenCompressorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected int progress() {
        return menu.progress();
    }

    @Override
    protected int progressCapacity() {
        return OxygenCompressorBlockEntity.FILL_TICKS;
    }

    @Override
    protected Component progressText() {
        return Component.literal(menu.progress() + " / " + OxygenCompressorBlockEntity.FILL_TICKS + " ticks");
    }
}
