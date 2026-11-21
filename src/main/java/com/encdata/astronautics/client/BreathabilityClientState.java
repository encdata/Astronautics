package com.encdata.astronautics.client;

public final class BreathabilityClientState {
    private static boolean breathable;

    private BreathabilityClientState() {}
    public static boolean breathable() { return breathable; }
    public static void setBreathable(boolean value) { breathable = value; }
}
