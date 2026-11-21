package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.content.misc.OxygenStorage;
import net.mcexpanded.fancytabsections.FancyTabSections;
import net.mcexpanded.fancytabsections.Section.SectionColored;
import net.mcexpanded.fancytabsections.Section.SectionTextured;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;

import java.util.function.Supplier;

public final class AstronauticsCreativeTabs {
    private static final int ORE_SECTION_PADDING_SLOTS = 5;
    public static final ResourceLocation MAIN_TAB = id("main");
    public static final ResourceLocation PLANETS_TAB = id("planets");

    private AstronauticsCreativeTabs() {}

    public static void register(IEventBus modEventBus) {

        FancyTabSections.registerCreativeModeTab(modEventBus, MAIN_TAB,
                stack(AstronauticsBlocks.THRUSTER_ITEM));
        FancyTabSections.registerCreativeModeTab(modEventBus, PLANETS_TAB,
                stack(AstronauticsBlocks.LUNAR_SOIL_ITEM));

        FancyTabSections.addSection(MAIN_TAB,
                new SectionTextured(id("machines"))
                        .setTitle(Component.translatable("section.astronautics.machines"))
                        .setRenderTitle(false)
                        .setTextureInsideRow()
                        .setCollapsible(false)
                        .add(stack(AstronauticsBlocks.THRUSTER_ITEM))
                        .add(stack(AstronauticsBlocks.STABILIZING_THRUSTER_ITEM))
                        .add(stack(AstronauticsBlocks.SHIP_COMPUTER_ITEM))
                        .add(stack(AstronauticsBlocks.CENTER_OF_MASS_CORE_ITEM))
                        .add(stack(AstronauticsBlocks.OXYGEN_DISTRIBUTOR_ITEM))
                        .add(stack(AstronauticsBlocks.OXYGEN_COMPRESSOR_ITEM))
                        .add(stack(AstronauticsItems.EMPTY_OXYGEN_TANK))
                        .add(AstronauticsCreativeTabs::fullOxygenTank)
                        .add(stack(AstronauticsItems.SPACE_SUIT_HELMET))
                        .add(stack(AstronauticsItems.SPACE_SUIT_CHESTPLATE))
                        .add(stack(AstronauticsItems.SPACE_SUIT_LEGGINGS))
                        .add(stack(AstronauticsItems.SPACE_SUIT_BOOTS)));

        FancyTabSections.addSection(MAIN_TAB,
                new SectionColored(id("fuels"))
                        .setTitle(Component.translatable("section.astronautics.fuels"))
                        .setBannerColor(0xFF1B3C55)
                        .setTextColor(0xFFE2F5FF)
                        .setTextShadow(true)
                        .setCollapsible(false)
                        .add(stack(AstronauticsFluids.BIOETHANOL.bucket))
                        .add(stack(AstronauticsFluids.METHANOL.bucket))
                        .add(stack(AstronauticsFluids.GASOLINE.bucket))
                        .add(stack(AstronauticsFluids.KEROSENE.bucket)));

        SectionColored ores = new SectionColored(id("ores"))
                .setTitle(Component.translatable("section.astronautics.ores"))
                .setBannerColor(0xFF273330)
                .setTextColor(0xFFE5F5D5)
                .setTextShadow(true)
                .setCollapsible(false);
        addOreRow(ores, AstronauticsItems.URANIUM_INGOT, AstronauticsItems.URANIUM_NUGGET,
                AstronauticsItems.URANIUM_DUST, AstronauticsItems.URANIUM_PLATE, true);
        addOreRow(ores, AstronauticsItems.TITANIUM_INGOT, AstronauticsItems.TITANIUM_NUGGET,
                AstronauticsItems.TITANIUM_DUST, AstronauticsItems.TITANIUM_PLATE, true);
        addOreRow(ores, AstronauticsItems.NICKEL_INGOT, AstronauticsItems.NICKEL_NUGGET,
                AstronauticsItems.NICKEL_DUST, AstronauticsItems.NICKEL_PLATE, false);
        FancyTabSections.addSection(MAIN_TAB, ores);

        addMoonPlanetSection(
                AstronauticsBlocks.LUNAR_SOIL_ITEM, AstronauticsBlocks.MOON_STONE_ITEM,
                AstronauticsBlocks.MOON_STONE_STAIRS_ITEM, AstronauticsBlocks.MOON_STONE_SLAB_ITEM,
                AstronauticsBlocks.MOON_STONE_WALL_ITEM, AstronauticsBlocks.MOON_COBBLESTONE_ITEM,
                AstronauticsBlocks.MOON_COBBLESTONE_STAIRS_ITEM, AstronauticsBlocks.MOON_COBBLESTONE_SLAB_ITEM,
                AstronauticsBlocks.MOON_COBBLESTONE_WALL_ITEM, AstronauticsBlocks.MOON_IRON_ORE_ITEM);
        addPlanetSection("mars", 0xFF63342A, 0xFFFFE9DE,
                AstronauticsBlocks.MARS_SAND_ITEM, AstronauticsBlocks.MARS_STONE_ITEM,
                AstronauticsBlocks.MARS_STONE_STAIRS_ITEM, AstronauticsBlocks.MARS_STONE_SLAB_ITEM,
                AstronauticsBlocks.MARS_STONE_WALL_ITEM, AstronauticsBlocks.MARS_COBBLESTONE_ITEM,
                AstronauticsBlocks.MARS_COBBLESTONE_STAIRS_ITEM, AstronauticsBlocks.MARS_COBBLESTONE_SLAB_ITEM,
                AstronauticsBlocks.MARS_COBBLESTONE_WALL_ITEM, AstronauticsBlocks.MARS_IRON_ORE_ITEM,
                AstronauticsBlocks.MARS_DIAMOND_ORE_ITEM);
        addPlanetSection("mercury", 0xFF4A4540, 0xFFE5DDD2,
                AstronauticsBlocks.MERCURY_SEDIMENT_ITEM, AstronauticsBlocks.ENSTATITE_ITEM,
                AstronauticsBlocks.ENSTATITE_STAIRS_ITEM, AstronauticsBlocks.ENSTATITE_SLAB_ITEM,
                AstronauticsBlocks.ENSTATITE_WALL_ITEM, AstronauticsBlocks.MERCURY_IRON_ORE_ITEM);
        addPlanetSection("venus", 0xFF8A4827, 0xFFFFE7D5,
                AstronauticsBlocks.SULFUR_SOIL_ITEM, AstronauticsBlocks.VENUS_SEDIMENT_ITEM,
                AstronauticsBlocks.VENUS_SEDIMENT_STAIRS_ITEM, AstronauticsBlocks.VENUS_SEDIMENT_SLAB_ITEM,
                AstronauticsBlocks.VENUS_SEDIMENT_WALL_ITEM, AstronauticsBlocks.VENUS_COAL_ORE_ITEM,
                AstronauticsBlocks.VENUS_GOLD_ORE_ITEM);
    }

    @SafeVarargs
    private static void addMoonPlanetSection(Supplier<Item>... items) {
        SectionTextured section = new SectionTextured(id("planets/moon"))
                .setTitle(Component.translatable("section.astronautics.planets.moon"))
                .setTexture(id("machines"))
                .setTextureInsideRow()
                .setCollapsible(false);
        for (Supplier<Item> item : items) {
            section.add(stack(item));
        }
        FancyTabSections.addSection(PLANETS_TAB, section);
    }

    @SafeVarargs
    private static void addPlanetSection(String planet, int bannerColor, int textColor,
                                         Supplier<Item>... items) {
        SectionColored section = new SectionColored(id("planets/" + planet))
                .setTitle(Component.translatable("section.astronautics.planets." + planet))
                .setBannerColor(bannerColor)
                .setTextColor(textColor)
                .setTextShadow(true)
                .setCollapsible(false);
        for (Supplier<Item> item : items) {
            section.add(stack(item));
        }
        FancyTabSections.addSection(PLANETS_TAB,
                section);
    }

    private static void addOreRow(SectionColored section,
                                  Supplier<Item> ingot,
                                  Supplier<Item> nugget,
                                  Supplier<Item> dust,
                                  Supplier<Item> plate,
                                  boolean addPadding) {
        section.add(stack(ingot)).add(stack(nugget)).add(stack(dust)).add(stack(plate));
        if (addPadding) {
            for (int slot = 0; slot < ORE_SECTION_PADDING_SLOTS; slot++) {
                section.add(ItemStack.EMPTY);
            }
        }
    }

    private static Supplier<ItemStack> stack(Supplier<? extends Item> item) {
        return () -> new ItemStack(item.get());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, path);
    }

    private static ItemStack fullOxygenTank() {
        ItemStack stack = new ItemStack(AstronauticsItems.OXYGEN_TANK.get());
        OxygenStorage.set(stack, OxygenStorage.TANK_CAPACITY);
        return stack;
    }
}
