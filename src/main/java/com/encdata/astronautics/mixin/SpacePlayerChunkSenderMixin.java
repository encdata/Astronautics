package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceChunkLoading;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
public abstract class SpacePlayerChunkSenderMixin {
    @Shadow
    @Final
    private LongSet pendingChunks;

    @Unique
    private final LongOpenHashSet astronautics$sentSpaceChunks = new LongOpenHashSet();

    @WrapOperation(
            method = "sendNextChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/PlayerChunkSender;sendChunk("
                            + "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"
                            + "Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/world/level/chunk/LevelChunk;)V"))
    private void astronautics$rememberSentSpaceChunk(ServerGamePacketListenerImpl listener,
                                                     ServerLevel level,
                                                     LevelChunk chunk,
                                                     Operation<Void> original) {
        original.call(listener, level, chunk);
        if (SpaceChunkLoading.isActive(level)) {
            astronautics$sentSpaceChunks.add(chunk.getPos().toLong());
        } else if (!astronautics$sentSpaceChunks.isEmpty()) {
            astronautics$sentSpaceChunks.clear();
        }
    }

    @Inject(method = "dropChunk", at = @At("HEAD"), cancellable = true)
    private void astronautics$skipUnsentSpaceChunk(ServerPlayer player, ChunkPos chunkPos, CallbackInfo ci) {
        ServerLevel level = player.serverLevel();
        if (!SpaceChunkLoading.isActive(level)) return;

        long key = chunkPos.toLong();
        if (astronautics$sentSpaceChunks.remove(key) || pendingChunks.contains(key)
                || !SpaceChunkLoading.isVirtual(level, chunkPos.x, chunkPos.z)) {
            return;
        }
        ci.cancel();
    }
}
