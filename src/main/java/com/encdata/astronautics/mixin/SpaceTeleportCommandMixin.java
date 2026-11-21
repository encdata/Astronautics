package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceEntityState;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGridLevelFactory;
import com.encdata.astronautics.space.SpaceGrids;
import com.encdata.astronautics.space.SpacePosition;
import com.encdata.astronautics.space.SpaceTeleportCoordinateText;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;
import java.math.BigDecimal;

@Mixin(TeleportCommand.class)
public abstract class SpaceTeleportCommandMixin {
    @Unique private static final ThreadLocal<SpacePosition> astronautics$targetPosition = new ThreadLocal<>();
    @Unique private static final ThreadLocal<SpaceGridAddress> astronautics$coordinateBase = new ThreadLocal<>();

    @Inject(method = "teleportToEntity", at = @At("HEAD"))
    private static void astronautics$rememberEntityGrid(CallbackInfoReturnable<Integer> cir,
                                                          @Local(argsOnly = true) Entity target) {
        astronautics$targetPosition.remove();
        astronautics$coordinateBase.remove();
        if (SpaceConstants.isSpace(target.level())) {
            astronautics$targetPosition.set(SpaceEntityState.position(target));
        }
    }

    @Inject(method = "teleportToEntity", at = @At("RETURN"))
    private static void astronautics$clearEntityGrid(CallbackInfoReturnable<Integer> cir) {
        astronautics$targetPosition.remove();
        astronautics$coordinateBase.remove();
    }

    @Inject(method = "teleportToPos", at = @At("HEAD"))
    private static void astronautics$rememberCoordinateGrid(CallbackInfoReturnable<Integer> cir,
                                                              @Local(argsOnly = true) CommandSourceStack source,
                                                              @Local(argsOnly = true) ServerLevel destination) {
        astronautics$targetPosition.remove();
        astronautics$coordinateBase.remove();
        if (SpaceConstants.isSpace(destination)) astronautics$coordinateBase.set(astronautics$sourceGrid(source));
    }

    @Inject(method = "teleportToPos", at = @At("RETURN"))
    private static void astronautics$clearCoordinateGrid(CallbackInfoReturnable<Integer> cir) {
        astronautics$targetPosition.remove();
        astronautics$coordinateBase.remove();
        SpaceTeleportCoordinateText.clear();
    }

    @ModifyExpressionValue(
            method = "teleportToPos",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/arguments/coordinates/Coordinates;getPosition(Lnet/minecraft/commands/CommandSourceStack;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 astronautics$boundSpaceCoordinates(Vec3 position,
                                                           @Local(argsOnly = true, ordinal = 0) Coordinates coordinates,
                                                           @Local(argsOnly = true) CommandSourceStack source) {
        SpaceGridAddress base = astronautics$coordinateBase.get();
        if (base == null) return position;

        SpaceGridAddress origin = SpaceGridAddress.ORIGIN;
        SpaceGridAddress address = new SpaceGridAddress(
                coordinates.isXRelative() ? base.x() : origin.x(),
                coordinates.isYRelative() ? base.y() : origin.y(),
                coordinates.isZRelative() ? base.z() : origin.z());
        SpaceTeleportCoordinateText.Parsed exact = SpaceTeleportCoordinateText.take(coordinates);
        SpacePosition destination;
        if (exact != null) {
            Vec3 sourcePosition = source.getPosition();
            destination = SpacePosition.normalize(address,
                    exact.value(0, sourcePosition.x),
                    exact.value(1, sourcePosition.y),
                    exact.value(2, sourcePosition.z));
        } else {
            destination = SpacePosition.normalize(address, position.x, position.y, position.z);
        }
        astronautics$targetPosition.set(destination);
        return new Vec3(destination.x(), destination.y(), destination.z());
    }

    @Inject(method = "performTeleport", at = @At("HEAD"))
    private static void astronautics$shiftClientBeforeTeleport(CallbackInfo ci,
                                                               @Local(argsOnly = true) Entity entity,
                                                               @Local(argsOnly = true) ServerLevel destination) {
        SpacePosition target = astronautics$targetPosition.get();
        if (target == null || !(entity instanceof net.minecraft.server.level.ServerPlayer player)
                || player.level() != destination || !SpaceGrids.isMainSpace(destination)) {
            return;
        }
        SpaceGrids.rebasePlayer(player, target.grid());
    }

    @ModifyExpressionValue(
            method = "performTeleport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z")
    )
    private static boolean astronautics$keepSpaceTeleportInGrid(boolean teleported,
                                                                  @Local(argsOnly = true) Entity entity,
                                                                  @Local(argsOnly = true) ServerLevel destination) {
        if (!teleported || !SpaceConstants.isSpace(destination)) return teleported;

        SpacePosition logical = astronautics$targetPosition.get();
        Vec3 position = entity.position();
        if (logical == null) {
            SpacePosition base = SpaceEntityState.position(entity);
            logical = SpacePosition.normalize(base.grid(), position.x, position.y, position.z);
        }
        SpacePosition target = logical;
        SpaceEntityState.set(entity, target);
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.setNoGravity(SpaceConstants.isSpace(destination));
        }
        if (target.x() != position.x || target.y() != position.y || target.z() != position.z) {
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                player.teleportTo(destination, target.x(), target.y(), target.z(),
                        player.getYRot(), player.getXRot());
            } else {
                entity.setPos(target.x(), target.y(), target.z());
            }
        }
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            com.encdata.astronautics.AstronauticsNetworkSync.resetSpaceGrid(player);
        }
        return true;
    }

    @Unique
    private static SpaceGridAddress astronautics$sourceGrid(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity != null && SpaceConstants.isSpace(entity.level())) {
            return SpaceGrids.frameOf(entity);
        }

        SpaceGridAddress address = SpaceGridLevelFactory.addressOf(source.getServer(), source.getLevel().dimension());
        return address == null ? SpaceGridAddress.ORIGIN : address;
    }
}
