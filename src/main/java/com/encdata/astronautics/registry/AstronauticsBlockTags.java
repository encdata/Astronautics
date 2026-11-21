package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class AstronauticsBlockTags {
    public static final TagKey<Block> HEAT_SHIELDING = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "heat_shielding"));

    private AstronauticsBlockTags() {}
}
