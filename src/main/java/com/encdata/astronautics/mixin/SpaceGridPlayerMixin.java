package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayer.class)
public abstract class SpaceGridPlayerMixin {
    @WrapMethod(method = "doTick")
    private void astronautics$tickInPlayerGrid(Operation<Void> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!SpaceGrids.isMainSpace(player.level())) {
            original.call();
            return;
        }
        SpaceGridAddress previous = SpaceGrids.enter(SpaceGrids.of(player));
        SpaceGridAddress previousFrame = SpaceGrids.enterFrame(SpaceGrids.frameOf(player));
        try {
            original.call();
        } finally {
            SpaceGrids.exitFrame(previousFrame);
            SpaceGrids.exit(previous);
        }
    }
}
