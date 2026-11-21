package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceChunkLoading;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceGrids;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkMap.class)
public abstract class SpaceChunkMapMixin {
    @Shadow @Final ServerLevel level;
    @Shadow @Final private BlockableEventLoop<Runnable> mainThreadExecutor;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    @Shadow public abstract DistanceManager getDistanceManager();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void astronautics$markSpace(CallbackInfo ci) {
        if (SpaceConstants.isSpace(level)
                && getDistanceManager() instanceof SpaceChunkLoading.SpaceTickets tickets) {
            tickets.astronautics$markSpace();
        }
    }

    @Unique
    private boolean astronautics$isSpace() {
        return getDistanceManager() instanceof SpaceChunkLoading.SpaceTickets tickets && tickets.astronautics$isSpace();
    }

    @Unique
    private boolean astronautics$isVirtual(ChunkPos pos) {
        return astronautics$isSpace() && SpaceChunkLoading.isVirtual(level, pos.x, pos.z);
    }

    @Inject(method = "scheduleUnload", at = @At("HEAD"), cancellable = true)
    private void astronautics$dropCancelledUnload(long chunkPos, ChunkHolder holder, CallbackInfo ci) {
        if (pendingUnloads.get(chunkPos) != holder && astronautics$isSpace()) ci.cancel();
    }

    @Inject(method = "scheduleChunkLoad", at = @At("HEAD"), cancellable = true)
    private void astronautics$emptySpaceChunk(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (astronautics$isVirtual(pos)) {
            cir.setReturnValue(CompletableFuture.supplyAsync(() -> SpaceChunkLoading.emptyChunk(level, pos), mainThreadExecutor));
        }
    }

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void astronautics$skipSpaceChunkSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (astronautics$isVirtual(chunk.getPos())) {
            chunk.setUnsaved(false);
            cir.setReturnValue(false);
        }
    }
}
