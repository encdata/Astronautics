package com.encdata.astronautics.client.compat;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.client.ZeroGGrab;
import com.encdata.astronautics.space.SpaceMovement;
import com.mlh.aero_player_tilt.api.PlayerTiltApi;
import org.joml.Quaternionf;

final class PlayerTiltFloat {
    private static final int PRIORITY = 1000;
    private static final PlayerTiltApi.Tilt UPRIGHT = new PlayerTiltApi.Tilt(new Quaternionf(), true, true, true, 0f);

    private PlayerTiltFloat() {}

    static void register() {
        PlayerTiltApi.Handle handle = PlayerTiltApi.forMod(Astronautics.MOD_ID);
        handle.addTiltSource(PRIORITY, context -> SpaceMovement.isFloating(context.player()) ? UPRIGHT : null);
        ZeroGGrab.standingTilt = (player, partialTick) -> handle.state(player, partialTick).model();
    }
}
