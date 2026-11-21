package com.encdata.astronautics.registry;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;

public final class AstronauticsFluidType extends FluidType implements IClientFluidTypeExtensions {

    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;


    public AstronauticsFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture) {
        super(properties);

        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
    }

    public @NotNull ResourceLocation getStillTexture() {
        return stillTexture;
    }

    @Override
    public @NotNull ResourceLocation getFlowingTexture() {
        return flowingTexture;
    }

}
