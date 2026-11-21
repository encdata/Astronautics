package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.content.blocks.oxygen_compressor.OxygenCompressorMenu;
import com.encdata.astronautics.content.misc.OxygenDistributorMenu;
import com.encdata.astronautics.content.blocks.ship_computer.ShipComputerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class AstronauticsMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Astronautics.MOD_ID);

    public static final Supplier<MenuType<OxygenDistributorMenu>> OXYGEN_DISTRIBUTOR = MENUS.register(
            "oxygen_distributor", () -> IMenuTypeExtension.create(OxygenDistributorMenu::new));
    public static final Supplier<MenuType<OxygenCompressorMenu>> OXYGEN_COMPRESSOR = MENUS.register(
            "oxygen_compressor", () -> IMenuTypeExtension.create(OxygenCompressorMenu::new));
    public static final Supplier<MenuType<ShipComputerMenu>> SHIP_COMPUTER = MENUS.register(
            "ship_computer", () -> IMenuTypeExtension.create(ShipComputerMenu::new));

    private AstronauticsMenus() {}
}
