package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.ThrusterFuelDataLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class AstronauticsFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Astronautics.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Astronautics.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Astronautics.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Astronautics.MOD_ID);
    public static final Fuel METHANOL = new Fuel("methanol", 792, 600, true);
    public static final Fuel GASOLINE = new Fuel("gasoline", 740, 520, true);
    public static final Fuel KEROSENE = new Fuel("kerosene", 810, 700, true);
    public static final Fuel BIOETHANOL = new Fuel("bioethanol", 800, 900, true);
    public static final Gas OXYGEN = new Gas("oxygen", 1, 100);
    public static final Gas HYDROGEN = new Gas("hydrogen", 1, 80);

    public static boolean isFuel(Fluid fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        return id != null && ThrusterFuelDataLoader.isFuel(id);
    }

    private AstronauticsFluids() {}

    public static final class Fuel {
        public final String id;
        public final Supplier<FluidType> type;
        public final Supplier<Item> bucket;
        public final Supplier<LiquidBlock> block;
        public final Supplier<BaseFlowingFluid.Source> source;
        public final Supplier<BaseFlowingFluid.Flowing> flowing;

        private Fuel(String id, int density, int viscosity, boolean CustomTexture) {
            this.id = id;
            ResourceLocation still = CustomTexture
                    ? ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "block/" + id + "_still")
                    : ResourceLocation.withDefaultNamespace("block/water_still");
            ResourceLocation flow = CustomTexture
                    ? ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "block/" + id + "_flow")
                    : ResourceLocation.withDefaultNamespace("block/water_flow");
            this.type = FLUID_TYPES.register(id, () -> new AstronauticsFluidType(AstronauticsFluidType.Properties.create()
                    .density(density).viscosity(viscosity).canSwim(false).supportsBoating(false),
                    still, flow));
            this.bucket = ITEMS.register(id + "_bucket", () -> new BucketItem(source(), new Item.Properties()
                    .craftRemainder(Items.BUCKET).stacksTo(1)));
            this.block = BLOCKS.register(id, () -> new LiquidBlock(source(), BlockBehaviour.Properties.of()
                    .liquid().noCollission().strength(100.0F).replaceable()));
            this.source = FLUIDS.register(id, () -> new BaseFlowingFluid.Source(properties()));
            this.flowing = FLUIDS.register(id + "_flowing", () -> new BaseFlowingFluid.Flowing(properties()));
        }

        private BaseFlowingFluid.Properties properties() {
            return new BaseFlowingFluid.Properties(type, this::source, () -> flowing.get())
                    .bucket(bucket).block(block);
        }

        private BaseFlowingFluid.Source source() {
            return source.get();
        }
    }

    public static final class Gas {
        public final String id;
        public final Supplier<FluidType> type;
        public final Supplier<BaseFlowingFluid.Source> source;
        public final Supplier<BaseFlowingFluid.Flowing> flowing;

        private Gas(String id, int density, int viscosity) {
            this.id = id;
            ResourceLocation still = ResourceLocation.withDefaultNamespace("block/water_still");
            ResourceLocation flow = ResourceLocation.withDefaultNamespace("block/water_flow");
            this.type = FLUID_TYPES.register(id, () -> new AstronauticsFluidType(FluidType.Properties.create()
                    .density(density).viscosity(viscosity).canSwim(false).supportsBoating(false), still, flow));
            this.source = FLUIDS.register(id, () -> new BaseFlowingFluid.Source(properties()));
            this.flowing = FLUIDS.register(id + "_flowing", () -> new BaseFlowingFluid.Flowing(properties()));
        }

        private BaseFlowingFluid.Properties properties() {
            return new BaseFlowingFluid.Properties(type, this::source, () -> flowing.get());
        }

        private BaseFlowingFluid.Source source() {
            return source.get();
        }
    }
}
