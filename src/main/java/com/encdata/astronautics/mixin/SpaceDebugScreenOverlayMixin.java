package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.SpaceGridClientState;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpacePosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class SpaceDebugScreenOverlayMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void astronautics$showGlobalSpacePosition(CallbackInfoReturnable<List<String>> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity player = minecraft.player;
        if (player == null || minecraft.level == null || !SpaceConstants.isSpace(minecraft.level)
                || !SpaceGridClientState.received()) return;

        SpacePosition position = SpacePosition.normalize(SpaceGridClientState.address(),
                player.getX(), player.getY(), player.getZ());
        cir.getReturnValue().add("Space XYZ: " + position.globalX() + ", "
                + position.globalY() + ", " + position.globalZ());
        cir.getReturnValue().add("Space grid: " + position.grid().x() + ", "
                + position.grid().y() + ", " + position.grid().z());
    }
}
