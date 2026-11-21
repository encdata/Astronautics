package com.encdata.astronautics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(targets = "moth.boxxed.panels.compat.create.panel_link.ModuleLinkEntries$ModuleEntry")
public abstract class DashPanelsModuleEntryMixin {
    @Overwrite
    public boolean isAlive() {
        try {
            Field parentalBE = this.getClass().getField("parentalBE");
            Object blockEntity = parentalBE.get(this);
            if (blockEntity == null) return false;

            Method getOrCreate = blockEntity.getClass().getMethod("getOrCreate");
            Object network = getOrCreate.invoke(blockEntity);
            if (network == null) return false;

            Method getCompiledModules = network.getClass().getMethod("getCompiledModules");
            return getCompiledModules.invoke(network) != null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {

            return false;
        }
    }
}
