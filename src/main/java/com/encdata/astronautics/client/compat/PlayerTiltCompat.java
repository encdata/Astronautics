package com.encdata.astronautics.client.compat;

import com.encdata.astronautics.Astronautics;
import net.neoforged.fml.ModList;

public final class PlayerTiltCompat {
    private PlayerTiltCompat() {}

    public static void registerIfPresent() {
        if (hasApi("aero_player_tilt", "com.mlh.aero_player_tilt.api.PlayerTiltApi", "0.3.0")) {
            PlayerTiltFloat.register();
        }
        if (hasApi("aero_cam_sync", "com.playsi.aero_cam_sync.api.AeroCamSyncApi", "1.4.0")) {
            CameraSyncFloat.register();
        }
    }

    private static boolean hasApi(String modId, String apiClass, String sinceVersion) {
        if (!ModList.get().isLoaded(modId)) return false;
        try {
            Class.forName(apiClass, false, PlayerTiltCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            Astronautics.LOGGER.warn("Skipping {} compat: version {} or newer is needed", modId, sinceVersion);
            return false;
        }
    }
}
