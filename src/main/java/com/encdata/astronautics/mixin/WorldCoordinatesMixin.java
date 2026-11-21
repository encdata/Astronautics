package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceTeleportCoordinateText;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldCoordinates.class)
public abstract class WorldCoordinatesMixin {
    @Inject(method = "parseDouble", at = @At("HEAD"))
    private static void astronautics$rememberStart(StringReader reader, boolean centerCorrect,
                                                    CallbackInfoReturnable<WorldCoordinates> cir) {
        SpaceTeleportCoordinateText.begin(reader, reader.getCursor());
    }

    @Inject(method = "parseDouble", at = @At("RETURN"))
    private static void astronautics$rememberExactCoordinates(StringReader reader, boolean centerCorrect,
                                                               CallbackInfoReturnable<WorldCoordinates> cir) {
        SpaceTeleportCoordinateText.capture(cir.getReturnValue(), reader, reader.getCursor(), centerCorrect);
    }
}
