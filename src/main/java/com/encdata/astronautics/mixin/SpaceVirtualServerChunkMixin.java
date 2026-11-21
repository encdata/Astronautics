package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import com.mojang.datafixers.DataFixer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mixin(value = ServerChunkCache.class, priority = 2100)
public abstract class SpaceVirtualServerChunkMixin {
    @Shadow @Final private ServerLevel level;
    @Unique private EmptyLevelChunk astronautics$emptyChunk;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void astronautics$init(ServerLevel level, LevelStorageSource.LevelStorageAccess storage,
                                 DataFixer dataFixer, StructureTemplateManager structures, Executor executor,
                                 ChunkGenerator generator, int viewDistance,
                                 int simulationDistance, boolean debug, ChunkProgressListener progress,
                                 ChunkStatusUpdateListener status, Supplier<?> supplier, CallbackInfo ci) {
        astronautics$emptyChunk = new EmptyLevelChunk(level, new ChunkPos(0, 0),
                level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
    }

    @Unique
    private boolean astronautics$isVirtualSpace(int x, int z) {
        if (SpaceConstants.isGridLevel(level.dimension())) return true;
        if (!SpaceConstants.isSpace(level)) return false;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null || !container.inBounds(x, z);
    }

    @Inject(method = "updatePlayerStatus", at = @At("HEAD"), cancellable = true)
    private void astronautics$skipSpacePlayerTickets(ServerPlayer player, boolean ignored, CallbackInfo ci) {
        if (astronautics$isVirtualSpace(player.chunkPosition().x, player.chunkPosition().z)) {
            ci.cancel();
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void astronautics$getChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        if (astronautics$isVirtualSpace(x, z)) cir.setReturnValue(astronautics$emptyChunk);
    }

    @Inject(method = "getChunkFutureMainThread", at = @At("HEAD"), cancellable = true)
    private void astronautics$getChunkFuture(int x, int z, ChunkStatus status, boolean create,
                                          CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir) {
        if (astronautics$isVirtualSpace(x, z)) {
            cir.setReturnValue(CompletableFuture.completedFuture(ChunkResult.of(astronautics$emptyChunk)));
        }
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true)
    private void astronautics$hasChunk(int x, int z, CallbackInfoReturnable<Boolean> cir) {
        if (astronautics$isVirtualSpace(x, z)) cir.setReturnValue(false);
    }

    @Inject(method = "getChunkForLighting", at = @At("HEAD"), cancellable = true)
    private void astronautics$getChunkForLighting(int x, int z, CallbackInfoReturnable<LightChunk> cir) {
        if (astronautics$isVirtualSpace(x, z)) cir.setReturnValue(astronautics$emptyChunk);
    }

    @Inject(method = "isPositionTicking", at = @At("HEAD"), cancellable = true)
    private void astronautics$isPositionTicking(long pos, CallbackInfoReturnable<Boolean> cir) {
        if (astronautics$isVirtualSpace(ChunkPos.getX(pos), ChunkPos.getZ(pos))) cir.setReturnValue(false);
    }

    @Inject(method = "getFullChunk", at = @At("HEAD"), cancellable = true)
    private void astronautics$getFullChunk(long pos, Consumer<LevelChunk> consumer, CallbackInfo ci) {
        if (astronautics$isVirtualSpace(ChunkPos.getX(pos), ChunkPos.getZ(pos))) ci.cancel();
    }

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void astronautics$blockChanged(BlockPos pos, CallbackInfo ci) {
        if (astronautics$isVirtualSpace(pos.getX() >> 4, pos.getZ() >> 4)) ci.cancel();
    }

    @Inject(method = "getVisibleChunkIfPresent", at = @At("HEAD"), cancellable = true)
    private void astronautics$getVisibleChunk(long pos, CallbackInfoReturnable<ChunkHolder> cir) {
        if (astronautics$isVirtualSpace(ChunkPos.getX(pos), ChunkPos.getZ(pos))) cir.setReturnValue(null);
    }

    @Inject(method = "addRegionTicket", at = @At("HEAD"), cancellable = true)
    private <T> void astronautics$addRegionTicket(TicketType<T> type, ChunkPos pos,
                                                int distance, T value, CallbackInfo ci) {
        if (astronautics$isVirtualSpace(pos.x, pos.z)) ci.cancel();
    }
}
