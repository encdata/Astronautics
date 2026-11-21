package com.encdata.astronautics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ClientPacketListener.class, priority = 2000)
public abstract class ClientChunkCacheMixin {
    @WrapOperation(
            method = "handleForgetLevelChunk",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/multiplayer/ClientChunkCache;drop(Lnet/minecraft/world/level/ChunkPos;)V"))
    private void astronautics$ignorePlotDrop(ClientChunkCache cache, ChunkPos chunkPos, Operation<Void> original) {
        try {
            original.call(cache, chunkPos);
        } catch (UnsupportedOperationException exception) {
            if (!"Cannot drop chunks in plot".equals(exception.getMessage())) {
                throw exception;
            }
        }
    }
}
