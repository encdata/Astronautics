package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceProjection;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public abstract class SpaceSubLevelHoldingChunkMapMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "updateChunkStatus", at = @At("HEAD"), cancellable = true)
    private void astronautics$keepNearbySpaceShipLoaded(ChunkPos chunkPos, boolean loaded, CallbackInfo callback) {
        if (loaded || !SpaceConstants.SPACE_LEVEL.equals(this.level.dimension())) {
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) {
            return;
        }

        double minX = chunkPos.getMinBlockX();
        double minZ = chunkPos.getMinBlockZ();
        double maxX = minX + 16.0;
        double maxZ = minZ + 16.0;

        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (!subLevel.boundingBox().intersects(minX, -Double.MAX_VALUE, minZ,
                    maxX, Double.MAX_VALUE, maxZ)) {
                continue;
            }

            for (ServerPlayer player : this.level.players()) {
                if (subLevel instanceof ServerSubLevel ship
                        && SpaceProjection.isWithinRange(player, ship, SpaceConstants.SHIP_TRACKING_RANGE)) {
                    callback.cancel();
                    return;
                }
            }
        }
    }
}
