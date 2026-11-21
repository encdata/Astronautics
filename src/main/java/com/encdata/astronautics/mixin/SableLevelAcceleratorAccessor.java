package com.encdata.astronautics.mixin;

import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LevelAccelerator.class, remap = false)
public interface SableLevelAcceleratorAccessor {
    @Mutable
    @Accessor("level")
    void astronautics$setLevel(Level level);
}
