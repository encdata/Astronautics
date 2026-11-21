package com.encdata.astronautics.mixin;

import dev.simulated_team.simulated.content.blocks.handle.PlayerHoldingHandleRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

@Mixin(value = PlayerHoldingHandleRenderer.class, remap = false)
public interface HandleHoldersAccessor {
    @Accessor("holdingPlayers")
    static Set<UUID> astronautics$holdingPlayers() {
        throw new AssertionError();
    }
}
