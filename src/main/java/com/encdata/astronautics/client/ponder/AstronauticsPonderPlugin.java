package com.encdata.astronautics.client.ponder;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.registry.AstronauticsBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class AstronauticsPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return Astronautics.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<Block> blocks = helper.withKeyFunction(BuiltInRegistries.BLOCK::getKey);
        blocks.forComponents(AstronauticsBlocks.CENTER_OF_MASS_CORE.get())
                .addStoryBoard("center_of_mass_core", AstronauticsThrusterScenes::centerOfMassCore);
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("core.intro", "The Center of Mass Core identifies the active physics center of a ship.");
        helper.registerSharedText("core.power", "Power the Core to activate it. Place it near the ship's center for stable thrust.");
    }
}
