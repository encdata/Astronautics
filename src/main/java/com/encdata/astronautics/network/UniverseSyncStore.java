package com.encdata.astronautics.network;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.GalaxyData;
import com.encdata.astronautics.space.SpaceCatalog;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class UniverseSyncStore {
    private static long revision = -1L;
    private static long serverGameTime;
    private static long receivedClientGameTime;

    private UniverseSyncStore() {}

    public static void replace(UniverseSyncPayload payload, long clientGameTime) {
        if (payload.catalogRevision() < revision) return;
        var galaxies = new HashMap<ResourceLocation, GalaxyData>();
        boolean decodeFailed = false;
        for (String json : payload.galaxies()) {
            try {
                GalaxyData galaxy = GalaxyData.decode(JsonParser.parseString(json));
                galaxies.put(galaxy.id(), galaxy);
            } catch (RuntimeException exception) {
                decodeFailed = true;
                Astronautics.LOGGER.error("Could not apply synchronized galaxy", exception);
            }
        }
        if (decodeFailed) return;
        revision = payload.catalogRevision();
        serverGameTime = payload.serverGameTime();
        receivedClientGameTime = clientGameTime;
        SpaceCatalog.replaceFromNetwork(galaxies);
    }

    public static long gameTime(long clientGameTime) {
        return serverGameTime + (clientGameTime - receivedClientGameTime);
    }

    public static void clear() {
        revision = -1L;
        serverGameTime = 0L;
        receivedClientGameTime = 0L;
        SpaceCatalog.replaceFromNetwork(Map.of());
    }
}
