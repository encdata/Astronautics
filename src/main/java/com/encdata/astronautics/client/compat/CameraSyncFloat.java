package com.encdata.astronautics.client.compat;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.SpaceMovement;
import com.playsi.aero_cam_sync.api.AcsHandle;
import com.playsi.aero_cam_sync.api.AeroCamSyncApi;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

final class CameraSyncFloat {
    private static final long LEASE_MILLIS = 250L;

    private static AcsHandle handle;

    private CameraSyncFloat() {}

    static void register() {
        handle = AeroCamSyncApi.forMod(Astronautics.MOD_ID);
        NeoForge.EVENT_BUS.addListener(CameraSyncFloat::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player != null && SpaceMovement.isFloating(player)) handle.suppress(LEASE_MILLIS);
    }
}
