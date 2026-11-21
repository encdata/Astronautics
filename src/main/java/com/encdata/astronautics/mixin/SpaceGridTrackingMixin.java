package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceChunkLoading;
import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class SpaceGridTrackingMixin {
    @Unique private static final double astronautics$VERTICAL_RANGE = 512.0;

    @Shadow @Final Entity entity;

    @ModifyExpressionValue(
            method = "updatePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"),
            require = 1
    )
    private Vec3 astronautics$positionInViewerGrid(Vec3 position, @Local(argsOnly = true) ServerPlayer player) {
        Vec3 offset = SpaceGrids.viewOffset(player, this.entity);
        return offset == null ? position : position.add(offset);
    }

    @ModifyExpressionValue(
            method = "updatePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;broadcastToPlayer(Lnet/minecraft/server/level/ServerPlayer;)Z"),
            require = 1
    )
    private boolean astronautics$nearbyGridsOnly(boolean visible, @Local(argsOnly = true) ServerPlayer player) {
        if (!visible) return false;
        if (SpaceGrids.isMainSpace(this.entity.level())
                && Sable.HELPER.getTrackingSubLevel(this.entity) instanceof ServerSubLevel ship
                && !ship.isRemoved()
                && !ship.getTrackingPlayers().contains(player.getGameProfile().getId())) {
            return false;
        }
        Vec3 offset = SpaceGrids.viewOffset(player, this.entity);
        return offset != null && (offset.y == 0.0
                || Math.abs(player.getY() - this.entity.getY() - offset.y) <= astronautics$VERTICAL_RANGE);
    }

    @WrapOperation(
            method = "updatePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"),
            require = 1
    )
    private boolean astronautics$chunkInViewerGrid(ChunkMap chunkMap, ServerPlayer player, int x, int z,
                                                   Operation<Boolean> original) {
        Vec3 offset = SpaceGrids.viewOffset(player, this.entity);
        if (offset == null) return original.call(chunkMap, player, x, z);
        if (SpaceGrids.isMainSpace(this.entity.level())
                && SpaceChunkLoading.isVirtual((ServerLevel) this.entity.level(), x, z)) {
            return true;
        }
        return original.call(chunkMap, player, x + (int) Math.round(offset.x / 16.0),
                z + (int) Math.round(offset.z / 16.0));
    }
}
