package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceChunkLoading;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(DistanceManager.class)
public abstract class SpaceDistanceManagerMixin implements SpaceChunkLoading.SpaceTickets {
    @Unique private boolean astronautics$space;

    @WrapWithCondition(
            method = "addPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;update(JIZ)V"))
    private boolean astronautics$skipSpaceViewDistance(@Coerce Object tracker, long chunkPos, int level, boolean decreasing) {
        return !astronautics$space;
    }

    @Override
    public boolean astronautics$isSpace() {
        return astronautics$space;
    }

    @Override
    public void astronautics$markSpace() {
        astronautics$space = true;
    }
}
