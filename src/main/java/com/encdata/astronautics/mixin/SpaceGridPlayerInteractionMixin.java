package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGrids;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpacePosition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

@Mixin(Player.class)
public abstract class SpaceGridPlayerInteractionMixin extends LivingEntity {
    @Shadow public abstract double entityInteractionRange();

    protected SpaceGridPlayerInteractionMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "canInteractWithEntity(Lnet/minecraft/world/entity/Entity;D)Z",
            at = @At("HEAD"), cancellable = true)
    private void astronautics$checkLogicalEntityReach(Entity target, double slop,
                                                       CallbackInfoReturnable<Boolean> callback) {
        if (!SpaceGrids.isMainSpace(this.level())) return;
        SpaceGridAddress from = SpaceGrids.frameOf(this);
        SpaceGridAddress to = SpaceGrids.frameOf(target);
        if (from.equals(to)) return;

        BigInteger gridX = to.x().subtract(from.x());
        BigInteger gridY = to.y().subtract(from.y());
        BigInteger gridZ = to.z().subtract(from.z());
        if (gridX.abs().compareTo(BigInteger.ONE) > 0
                || gridY.abs().compareTo(BigInteger.ONE) > 0
                || gridZ.abs().compareTo(BigInteger.ONE) > 0) {
            callback.setReturnValue(false);
            return;
        }

        double offsetX = gridX.doubleValue() * SpacePosition.GRID_SIZE;
        double offsetY = gridY.doubleValue() * SpacePosition.GRID_SIZE;
        double offsetZ = gridZ.doubleValue() * SpacePosition.GRID_SIZE;
        AABB box = target.getBoundingBox().move(offsetX, offsetY, offsetZ);
        Vec3 eye = this.getEyePosition();
        double dx = axisDistance(eye.x, box.minX, box.maxX);
        double dy = axisDistance(eye.y, box.minY, box.maxY);
        double dz = axisDistance(eye.z, box.minZ, box.maxZ);
        double range = this.entityInteractionRange() + slop;
        callback.setReturnValue(dx * dx + dy * dy + dz * dz < range * range);
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        return value < minimum ? minimum - value : value > maximum ? value - maximum : 0.0;
    }
}
