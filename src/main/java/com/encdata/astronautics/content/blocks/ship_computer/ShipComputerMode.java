package com.encdata.astronautics.content.blocks.ship_computer;

public enum ShipComputerMode {
    OFF(false, false),
    POSITION(true, false),
    ROTATION(false, true),
    BOTH(true, true),
    ORBIT(true, false);

    private final boolean position;
    private final boolean rotation;

    ShipComputerMode(boolean position, boolean rotation) {
        this.position = position;
        this.rotation = rotation;
    }

    public boolean dampensPosition() {
        return position;
    }

    public boolean dampensRotation() {
        return rotation;
    }

    public static ShipComputerMode byId(int id) {
        ShipComputerMode[] values = values();
        return id >= 0 && id < values.length ? values[id] : BOTH;
    }
}
