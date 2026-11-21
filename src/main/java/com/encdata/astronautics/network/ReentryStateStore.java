package com.encdata.astronautics.network;

import com.encdata.astronautics.space.ReentryState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;

public final class ReentryStateStore {
    private static final Map<UUID, TimedState> STATES = new ConcurrentHashMap<>();

    private ReentryStateStore() {}

    public static void put(ReentryStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        long tick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        STATES.put(payload.shipId(), new TimedState(payload.state(), tick));
    }

    public static Map<UUID, ReentryState> states() {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        Map<UUID, ReentryState> active = new HashMap<>();
        for (Map.Entry<UUID, TimedState> entry : STATES.entrySet()) {
            TimedState value = entry.getValue();
            if (now - value.tick() > 30L && STATES.remove(entry.getKey(), value)) continue;
            active.put(entry.getKey(), value.state());
        }
        return Map.copyOf(active);
    }

    public static void clear() {
        STATES.clear();
    }

    private record TimedState(ReentryState state, long tick) {}
}
