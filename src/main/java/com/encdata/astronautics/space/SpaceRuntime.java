package com.encdata.astronautics.space;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpaceRuntime {
    private final Map<UUID, SpacePlayerState> players = new ConcurrentHashMap<>();

    public SpacePlayerState addPlayer(UUID id) {
        return players.computeIfAbsent(id, ignored -> new SpacePlayerState());
    }

    public void removePlayer(UUID id) {
        players.remove(id);
    }

    public void clear() {
        players.clear();
    }
}
