package com.encdata.astronautics.client;

import com.encdata.astronautics.space.SpaceGridAddress;

public final class SpaceGridClientState {
    private static SpaceGridAddress address = SpaceGridAddress.ORIGIN;
    private static long revision = -1L;
    private static boolean received;

    private SpaceGridClientState() {}

    public static SpaceGridAddress address() {
        return address;
    }

    public static boolean received() {
        return received;
    }

    public static void set(SpaceGridAddress value) {
        set(revision + 1L, value);
    }

    public static void set(long nextRevision, SpaceGridAddress value) {
        if (nextRevision < revision) return;
        revision = nextRevision;
        address = value;
        received = true;
    }

    public static boolean applyRebase(long nextRevision, SpaceGridAddress value) {
        if (nextRevision <= revision) return false;
        revision = nextRevision;
        address = value;
        received = true;
        return true;
    }

    public static void clear() {
        address = SpaceGridAddress.ORIGIN;
        revision = -1L;
        received = false;
    }
}
