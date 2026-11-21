package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.content.blocks.atmospheric_control.AtmosphericControlUnitBlock;
import com.encdata.astronautics.content.blocks.center_of_mass_core.CenterOfMassCoreBlock;
import com.encdata.astronautics.content.blocks.oxygen_compressor.OxygenCompressorBlock;
import com.encdata.astronautics.content.blocks.rcs_thruster.RcsThrusterBlock;
import com.encdata.astronautics.content.blocks.stabilizing_thruster.StabilizingThrusterBlock;
import com.encdata.astronautics.content.blocks.ship_computer.ShipComputerBlock;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class AstronauticsBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Astronautics.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Astronautics.MOD_ID);

    public static final Supplier<Block> THRUSTER = BLOCKS.register("thruster", () ->
            new RcsThrusterBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final Supplier<Item> THRUSTER_ITEM = blockItem("thruster", THRUSTER);

    public static final Supplier<Block> STABILIZING_THRUSTER = BLOCKS.register("reaction_based_thruster", () ->
            new StabilizingThrusterBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final Supplier<Item> STABILIZING_THRUSTER_ITEM =
            blockItem("reaction_based_thruster", STABILIZING_THRUSTER);

    public static final Supplier<Block> SHIP_COMPUTER = BLOCKS.register("ship_computer", () ->
            new ShipComputerBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final Supplier<Item> SHIP_COMPUTER_ITEM = blockItem("ship_computer", SHIP_COMPUTER);

    public static final Supplier<Block> CENTER_OF_MASS_CORE = BLOCKS.register("center_of_mass_core", () ->
            new CenterOfMassCoreBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final Supplier<Item> CENTER_OF_MASS_CORE_ITEM = blockItem("center_of_mass_core", CENTER_OF_MASS_CORE);

    public static final Supplier<Block> OXYGEN_DISTRIBUTOR = BLOCKS.register("oxygen_distributor", () ->
            new AtmosphericControlUnitBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final Supplier<Item> OXYGEN_DISTRIBUTOR_ITEM = blockItem("oxygen_distributor", OXYGEN_DISTRIBUTOR);

    public static final Supplier<Block> OXYGEN_COMPRESSOR = BLOCKS.register("oxygen_compressor", () ->
            new OxygenCompressorBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final Supplier<Item> OXYGEN_COMPRESSOR_ITEM = blockItem("oxygen_compressor", OXYGEN_COMPRESSOR);

    public static final Supplier<Block> LUNAR_SOIL = BLOCKS.register("lunar_soil", () ->
            new ColoredFallingBlock(new ColorRGBA(0x876B62), BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)
                    .mapColor(MapColor.TERRACOTTA_LIGHT_GRAY)));
    public static final Supplier<Item> LUNAR_SOIL_ITEM = blockItem("lunar_soil", LUNAR_SOIL);

    public static final Supplier<Block> MOON_STONE = BLOCKS.register("moon_stone", () ->
            new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                    .mapColor(MapColor.TERRACOTTA_LIGHT_GRAY)));
    public static final Supplier<Item> MOON_STONE_ITEM = blockItem("moon_stone", MOON_STONE);
    public static final Supplier<Block> MOON_STONE_STAIRS = BLOCKS.register("moon_stone_stairs", () ->
            new StairBlock(MOON_STONE.get().defaultBlockState(), moonStoneProperties()));
    public static final Supplier<Item> MOON_STONE_STAIRS_ITEM = blockItem("moon_stone_stairs", MOON_STONE_STAIRS);
    public static final Supplier<Block> MOON_STONE_SLAB = BLOCKS.register("moon_stone_slab", () -> new SlabBlock(moonStoneProperties()));
    public static final Supplier<Item> MOON_STONE_SLAB_ITEM = blockItem("moon_stone_slab", MOON_STONE_SLAB);
    public static final Supplier<Block> MOON_STONE_WALL = BLOCKS.register("moon_stone_wall", () -> new WallBlock(moonStoneProperties()));
    public static final Supplier<Item> MOON_STONE_WALL_ITEM = blockItem("moon_stone_wall", MOON_STONE_WALL);

    public static final Supplier<Block> MOON_COBBLESTONE = BLOCKS.register("moon_cobblestone", () ->
            new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                    .mapColor(MapColor.TERRACOTTA_LIGHT_GRAY)));
    public static final Supplier<Item> MOON_COBBLESTONE_ITEM = blockItem("moon_cobblestone", MOON_COBBLESTONE);
    public static final Supplier<Block> MOON_COBBLESTONE_STAIRS = BLOCKS.register("moon_cobblestone_stairs", () ->
            new StairBlock(MOON_COBBLESTONE.get().defaultBlockState(), moonStoneProperties()));
    public static final Supplier<Item> MOON_COBBLESTONE_STAIRS_ITEM = blockItem("moon_cobblestone_stairs", MOON_COBBLESTONE_STAIRS);
    public static final Supplier<Block> MOON_COBBLESTONE_SLAB = BLOCKS.register("moon_cobblestone_slab", () ->
            new SlabBlock(moonStoneProperties()));
    public static final Supplier<Item> MOON_COBBLESTONE_SLAB_ITEM = blockItem("moon_cobblestone_slab", MOON_COBBLESTONE_SLAB);
    public static final Supplier<Block> MOON_COBBLESTONE_WALL = BLOCKS.register("moon_cobblestone_wall", () ->
            new WallBlock(moonStoneProperties()));
    public static final Supplier<Item> MOON_COBBLESTONE_WALL_ITEM = blockItem("moon_cobblestone_wall", MOON_COBBLESTONE_WALL);

    public static final Supplier<Block> MOON_IRON_ORE = BLOCKS.register("moon_iron_ore", () ->
            new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final Supplier<Item> MOON_IRON_ORE_ITEM = blockItem("moon_iron_ore", MOON_IRON_ORE);

    public static final Supplier<Block> MARS_SAND = BLOCKS.register("mars_sand", () ->
            new ColoredFallingBlock(new ColorRGBA(0x8E3C2E), BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)
                    .mapColor(MapColor.TERRACOTTA_RED)));
    public static final Supplier<Item> MARS_SAND_ITEM = blockItem("mars_sand", MARS_SAND);

    public static final Supplier<Block> MARS_STONE = BLOCKS.register("mars_stone", () -> stoneBlock());
    public static final Supplier<Item> MARS_STONE_ITEM = blockItem("mars_stone", MARS_STONE);
    public static final Supplier<Block> MARS_STONE_STAIRS = BLOCKS.register("mars_stone_stairs", () ->
            new StairBlock(MARS_STONE.get().defaultBlockState(), stoneProperties()));
    public static final Supplier<Item> MARS_STONE_STAIRS_ITEM = blockItem("mars_stone_stairs", MARS_STONE_STAIRS);
    public static final Supplier<Block> MARS_STONE_SLAB = BLOCKS.register("mars_stone_slab", () -> new SlabBlock(stoneProperties()));
    public static final Supplier<Item> MARS_STONE_SLAB_ITEM = blockItem("mars_stone_slab", MARS_STONE_SLAB);
    public static final Supplier<Block> MARS_STONE_WALL = BLOCKS.register("mars_stone_wall", () -> new WallBlock(stoneProperties()));
    public static final Supplier<Item> MARS_STONE_WALL_ITEM = blockItem("mars_stone_wall", MARS_STONE_WALL);

    public static final Supplier<Block> MARS_COBBLESTONE = BLOCKS.register("mars_cobblestone", () -> stoneBlock());
    public static final Supplier<Item> MARS_COBBLESTONE_ITEM = blockItem("mars_cobblestone", MARS_COBBLESTONE);
    public static final Supplier<Block> MARS_COBBLESTONE_STAIRS = BLOCKS.register("mars_cobblestone_stairs", () ->
            new StairBlock(MARS_COBBLESTONE.get().defaultBlockState(), stoneProperties()));
    public static final Supplier<Item> MARS_COBBLESTONE_STAIRS_ITEM = blockItem("mars_cobblestone_stairs", MARS_COBBLESTONE_STAIRS);
    public static final Supplier<Block> MARS_COBBLESTONE_SLAB = BLOCKS.register("mars_cobblestone_slab", () ->
            new SlabBlock(stoneProperties()));
    public static final Supplier<Item> MARS_COBBLESTONE_SLAB_ITEM = blockItem("mars_cobblestone_slab", MARS_COBBLESTONE_SLAB);
    public static final Supplier<Block> MARS_COBBLESTONE_WALL = BLOCKS.register("mars_cobblestone_wall", () ->
            new WallBlock(stoneProperties()));
    public static final Supplier<Item> MARS_COBBLESTONE_WALL_ITEM = blockItem("mars_cobblestone_wall", MARS_COBBLESTONE_WALL);

    public static final Supplier<Block> MARS_IRON_ORE = BLOCKS.register("mars_iron_ore", () ->
            new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final Supplier<Item> MARS_IRON_ORE_ITEM = blockItem("mars_iron_ore", MARS_IRON_ORE);
    public static final Supplier<Block> MARS_DIAMOND_ORE = BLOCKS.register("mars_diamond_ore", () ->
            new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final Supplier<Item> MARS_DIAMOND_ORE_ITEM = blockItem("mars_diamond_ore", MARS_DIAMOND_ORE);

    public static final Supplier<Block> MERCURY_SEDIMENT = BLOCKS.register("mercury_sediment", () ->
            new ColoredFallingBlock(new ColorRGBA(0x4A4540), BlockBehaviour.Properties.ofFullCopy(Blocks.GRAVEL)
                    .mapColor(MapColor.TERRACOTTA_GRAY)));
    public static final Supplier<Item> MERCURY_SEDIMENT_ITEM = blockItem("mercury_sediment", MERCURY_SEDIMENT);
    public static final Supplier<Block> ENSTATITE = BLOCKS.register("enstatite", () ->
            new Block(mercuryStoneProperties()));
    public static final Supplier<Item> ENSTATITE_ITEM = blockItem("enstatite", ENSTATITE);
    public static final Supplier<Block> ENSTATITE_STAIRS = BLOCKS.register("enstatite_stairs", () ->
            new StairBlock(ENSTATITE.get().defaultBlockState(), mercuryStoneProperties()));
    public static final Supplier<Item> ENSTATITE_STAIRS_ITEM = blockItem("enstatite_stairs", ENSTATITE_STAIRS);
    public static final Supplier<Block> ENSTATITE_SLAB = BLOCKS.register("enstatite_slab", () ->
            new SlabBlock(mercuryStoneProperties()));
    public static final Supplier<Item> ENSTATITE_SLAB_ITEM = blockItem("enstatite_slab", ENSTATITE_SLAB);
    public static final Supplier<Block> ENSTATITE_WALL = BLOCKS.register("enstatite_wall", () ->
            new WallBlock(mercuryStoneProperties()));
    public static final Supplier<Item> ENSTATITE_WALL_ITEM = blockItem("enstatite_wall", ENSTATITE_WALL);
    public static final Supplier<Block> MERCURY_IRON_ORE = BLOCKS.register("mercury_iron_ore", () ->
            new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)));
    public static final Supplier<Item> MERCURY_IRON_ORE_ITEM = blockItem("mercury_iron_ore", MERCURY_IRON_ORE);

    public static final Supplier<Block> SULFUR_SOIL = BLOCKS.register("sulfur_soil", () ->
            new ColoredFallingBlock(new ColorRGBA(0x9F5224), BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)
                    .mapColor(MapColor.TERRACOTTA_ORANGE)));
    public static final Supplier<Item> SULFUR_SOIL_ITEM = blockItem("sulfur_soil", SULFUR_SOIL);
    public static final Supplier<Block> VENUS_SEDIMENT = BLOCKS.register("venus_sediment", () ->
            new Block(venusStoneProperties()));
    public static final Supplier<Item> VENUS_SEDIMENT_ITEM = blockItem("venus_sediment", VENUS_SEDIMENT);
    public static final Supplier<Block> VENUS_SEDIMENT_STAIRS = BLOCKS.register("venus_sediment_stairs", () ->
            new StairBlock(VENUS_SEDIMENT.get().defaultBlockState(), venusStoneProperties()));
    public static final Supplier<Item> VENUS_SEDIMENT_STAIRS_ITEM = blockItem("venus_sediment_stairs", VENUS_SEDIMENT_STAIRS);
    public static final Supplier<Block> VENUS_SEDIMENT_SLAB = BLOCKS.register("venus_sediment_slab", () ->
            new SlabBlock(venusStoneProperties()));
    public static final Supplier<Item> VENUS_SEDIMENT_SLAB_ITEM = blockItem("venus_sediment_slab", VENUS_SEDIMENT_SLAB);
    public static final Supplier<Block> VENUS_SEDIMENT_WALL = BLOCKS.register("venus_sediment_wall", () ->
            new WallBlock(venusStoneProperties()));
    public static final Supplier<Item> VENUS_SEDIMENT_WALL_ITEM = blockItem("venus_sediment_wall", VENUS_SEDIMENT_WALL);
    public static final Supplier<Block> VENUS_COAL_ORE = BLOCKS.register("venus_coal_ore", () ->
            new DropExperienceBlock(UniformInt.of(0, 2), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final Supplier<Item> VENUS_COAL_ORE_ITEM = blockItem("venus_coal_ore", VENUS_COAL_ORE);
    public static final Supplier<Block> VENUS_GOLD_ORE = BLOCKS.register("venus_gold_ore", () ->
            new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final Supplier<Item> VENUS_GOLD_ORE_ITEM = blockItem("venus_gold_ore", VENUS_GOLD_ORE);

    private static Supplier<Item> blockItem(String name, Supplier<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static Block stoneBlock() {
        return new Block(stoneProperties());
    }

    private static BlockBehaviour.Properties moonStoneProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                .mapColor(MapColor.TERRACOTTA_LIGHT_GRAY);
    }

    private static BlockBehaviour.Properties stoneProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                .mapColor(MapColor.TERRACOTTA_RED);
    }

    private static BlockBehaviour.Properties venusStoneProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                .mapColor(MapColor.TERRACOTTA_ORANGE);
    }

    private static BlockBehaviour.Properties mercuryStoneProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE)
                .mapColor(MapColor.TERRACOTTA_GRAY);
    }

    private AstronauticsBlocks() {}
}
