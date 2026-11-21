package com.encdata.astronautics.client.screens;

import com.encdata.astronautics.content.misc.OxygenDistributorMenu;
import com.encdata.astronautics.content.misc.OxygenStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class OxygenDistributorScreen extends OxygenMachineScreen<OxygenDistributorMenu> {
    public OxygenDistributorScreen(OxygenDistributorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected int progress() {
        return menu.oxygen();
    }

    @Override
    protected int progressCapacity() {
        return OxygenStorage.TANK_CAPACITY;
    }

    @Override
    protected Component progressText() {
        return Component.literal(menu.oxygen() + " / " + OxygenStorage.TANK_CAPACITY + " mB");
    }
}
