package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.content.blocks.atmospheric_control.AtmosphericControlUnitBlockEntity;
import com.encdata.astronautics.content.blocks.center_of_mass_core.CenterOfMassCoreBlockEntity;
import com.encdata.astronautics.content.blocks.oxygen_compressor.OxygenCompressorBlockEntity;
import com.encdata.astronautics.content.blocks.rcs_thruster.RcsThrusterBlockEntity;
import com.encdata.astronautics.content.blocks.stabilizing_thruster.StabilizingThrusterBlockEntity;
import com.encdata.astronautics.content.blocks.ship_computer.ShipComputerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class AstronauticsBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Astronautics.MOD_ID);

    public static final Supplier<BlockEntityType<RcsThrusterBlockEntity>> THRUSTER = BLOCK_ENTITIES.register(
            "thruster", () -> BlockEntityType.Builder.of(
                    RcsThrusterBlockEntity::new, AstronauticsBlocks.THRUSTER.get()).build(null));

    public static final Supplier<BlockEntityType<StabilizingThrusterBlockEntity>> STABILIZING_THRUSTER = BLOCK_ENTITIES.register(
            "reaction_based_thruster", () -> BlockEntityType.Builder.of(
                    StabilizingThrusterBlockEntity::new, AstronauticsBlocks.STABILIZING_THRUSTER.get()).build(null));

    public static final Supplier<BlockEntityType<ShipComputerBlockEntity>> SHIP_COMPUTER = BLOCK_ENTITIES.register(
            "ship_computer", () -> BlockEntityType.Builder.of(
                    ShipComputerBlockEntity::new, AstronauticsBlocks.SHIP_COMPUTER.get()).build(null));

    public static final Supplier<BlockEntityType<CenterOfMassCoreBlockEntity>> CENTER_OF_MASS_CORE = BLOCK_ENTITIES.register(
            "center_of_mass_core", () -> BlockEntityType.Builder.of(
                    CenterOfMassCoreBlockEntity::new, AstronauticsBlocks.CENTER_OF_MASS_CORE.get()).build(null));

    public static final Supplier<BlockEntityType<AtmosphericControlUnitBlockEntity>> OXYGEN_DISTRIBUTOR = BLOCK_ENTITIES.register(
            "oxygen_distributor", () -> BlockEntityType.Builder.of(
                    AtmosphericControlUnitBlockEntity::new, AstronauticsBlocks.OXYGEN_DISTRIBUTOR.get()).build(null));

    public static final Supplier<BlockEntityType<OxygenCompressorBlockEntity>> OXYGEN_COMPRESSOR = BLOCK_ENTITIES.register(
            "oxygen_compressor", () -> BlockEntityType.Builder.of(
                    OxygenCompressorBlockEntity::new, AstronauticsBlocks.OXYGEN_COMPRESSOR.get()).build(null));

    private AstronauticsBlockEntities() {}
}
