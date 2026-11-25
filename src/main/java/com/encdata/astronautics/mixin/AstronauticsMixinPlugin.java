package com.encdata.astronautics.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class AstronauticsMixinPlugin implements IMixinConfigPlugin {
    private static final String DASH_PANELS_MIXIN =
            "com.encdata.astronautics.mixin.DashPanelsModuleEntryMixin";
    private static final String DASH_PANELS_MOD_ID = "dashpanels";
    private static final String SPACE_DISTANCE_MANAGER_MIXIN =
            "com.encdata.astronautics.mixin.SpaceDistanceManagerMixin";
    private static final String C2ME_EMPTY_CHUNK_MIXIN =
            "com.encdata.astronautics.mixin.C2meEmptySpaceChunkMixin";
    private static final Set<String> VANILLA_CHUNK_MIXINS = Set.of(
            "com.encdata.astronautics.mixin.SpaceChunkMapMixin",
            "com.encdata.astronautics.mixin.SpacePlayerChunkSenderMixin");
    private static final String MOONRISE_MOD_ID = "moonrise";
    private static final String C2ME_MOD_ID = "c2me";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(DASH_PANELS_MIXIN)) {
            return FMLLoader.getLoadingModList().getModFileById(DASH_PANELS_MOD_ID) != null;
        }
        if (VANILLA_CHUNK_MIXINS.contains(mixinClassName)) {
            return FMLLoader.getLoadingModList().getModFileById(MOONRISE_MOD_ID) == null
                    && FMLLoader.getLoadingModList().getModFileById(C2ME_MOD_ID) == null;
        }
        if (mixinClassName.equals(SPACE_DISTANCE_MANAGER_MIXIN)) {
            return FMLLoader.getLoadingModList().getModFileById(MOONRISE_MOD_ID) == null;
        }
        if (mixinClassName.equals(C2ME_EMPTY_CHUNK_MIXIN)) {
            return FMLLoader.getLoadingModList().getModFileById(C2ME_MOD_ID) != null;
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
