package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceProjection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.UUID;

@Mixin(value = SubLevelTrackingSystem.class, remap = false)
public abstract class SpaceSubLevelTrackingMixin {
    @Inject(method = "shouldLoad", at = @At("HEAD"), cancellable = true)
    private void astronautics$spaceTrackingRange(Player player, Vector3dc entityPosition,
                                               CallbackInfoReturnable<Boolean> callback) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !SpaceConstants.isSpace(serverPlayer.level())) {
            return;
        }

        callback.setReturnValue(entityPosition.distanceSquared(
                serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ())
                <= SpaceConstants.SHIP_TRACKING_RANGE * SpaceConstants.SHIP_TRACKING_RANGE);
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelTrackingSystem;shouldLoad(Lnet/minecraft/world/entity/player/Player;Lorg/joml/Vector3dc;)Z"),
            remap = false
    )
    private boolean astronautics$trackByUniverseDistance(SubLevelTrackingSystem system, Player player,
                                                         Vector3dc position, Operation<Boolean> original,
                                                         @Local ServerSubLevel ship) {
        if (player instanceof ServerPlayer serverPlayer && astronautics$isMainSpace(ship)) {
            return SpaceProjection.isWithinRange(serverPlayer, ship, SpaceConstants.SHIP_TRACKING_RANGE);
        }
        return original.call(system, player, position);
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelTrackingSystem;collectPlayers(Lorg/joml/Vector3d;Ljava/util/Collection;)V"),
            remap = false
    )
    private void astronautics$collectByUniverseDistance(SubLevelTrackingSystem system, Vector3d position,
                                                        Collection<UUID> tracking, Operation<Void> original,
                                                        @Local ServerSubLevel ship) {
        if (!astronautics$isMainSpace(ship)) {
            original.call(system, position, tracking);
            return;
        }
        for (ServerPlayer player : ship.getLevel().getServer().getPlayerList().getPlayers()) {
            if (!SpaceConstants.isSpace(player.level())) continue;
            if (SpaceProjection.isWithinRange(player, ship, SpaceConstants.SHIP_TRACKING_RANGE)) {
                tracking.add(player.getGameProfile().getId());
            }
        }
    }

    @Unique
    private static boolean astronautics$isMainSpace(ServerSubLevel ship) {
        return SpaceConstants.SPACE_LEVEL.equals(ship.getLevel().dimension());
    }
}
