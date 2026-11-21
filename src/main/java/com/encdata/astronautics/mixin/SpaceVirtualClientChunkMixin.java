package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.chunk.status.ChunkStatus;

@Mixin(value = ClientChunkCache.class, priority = 2100)
public abstract class SpaceVirtualClientChunkMixin {
    @Shadow @Final private ClientLevel level;
    @Shadow @Final private LevelChunk emptyChunk;

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void astronautics$getChunk(int x, int z, ChunkStatus status, boolean create,
                                    CallbackInfoReturnable<LevelChunk> cir) {
        if (!SpaceConstants.isSpace(level)) return;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(x, z)) cir.setReturnValue(emptyChunk);
    }
}
