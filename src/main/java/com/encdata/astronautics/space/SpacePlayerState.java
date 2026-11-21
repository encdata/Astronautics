package com.encdata.astronautics.space;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class SpacePlayerState {
    public static final String PERSISTENT_DATA_KEY = "astronautics_space";
    private static final String SHIP_ID_KEY = "ship_id";
    private static final String LOCAL_POSITION_KEY = "local_position";
    private static final String SPACE_POSITION_KEY = "space_position";
    private static final String VECTOR_X_KEY = "x";
    private static final String VECTOR_Y_KEY = "y";
    private static final String VECTOR_Z_KEY = "z";

    private UUID shipId;
    private SpaceVec3 localPosition = SpaceVec3.ZERO;
    private SpacePosition spacePosition = SpacePosition.ORIGIN;
    private boolean nbtLoaded;
    private int ticksOffShipFrame;

    public UUID shipId() {
        return shipId;
    }

    public SpaceVec3 localPosition() {
        return localPosition;
    }

    public SpacePosition spacePosition() {
        return spacePosition;
    }

    public void setShipId(UUID shipId) {
        this.shipId = shipId;
    }

    public void setLocalPosition(SpaceVec3 localPosition) {
        this.localPosition = localPosition;
    }

    public void setSpacePosition(SpacePosition spacePosition) {
        this.spacePosition = spacePosition;
    }

    public int countOffShipFrame(boolean off) {
        ticksOffShipFrame = off ? ticksOffShipFrame + 1 : 0;
        return ticksOffShipFrame;
    }

    public boolean nbtLoaded() {
        return nbtLoaded;
    }

    public void loadFromTag(CompoundTag tag) {
        shipId = tag.hasUUID(SHIP_ID_KEY) ? tag.getUUID(SHIP_ID_KEY) : null;
        localPosition = readVec(tag, LOCAL_POSITION_KEY);
        if (tag.contains(SPACE_POSITION_KEY)) {
            spacePosition = SpacePosition.CODEC.parse(NbtOps.INSTANCE, tag.get(SPACE_POSITION_KEY))
                    .result().orElse(SpacePosition.ORIGIN);
        }
        nbtLoaded = true;
    }

    public void saveToTag(CompoundTag tag) {
        if (shipId != null) tag.putUUID(SHIP_ID_KEY, shipId);
        else tag.remove(SHIP_ID_KEY);

        writeVec(tag, LOCAL_POSITION_KEY, localPosition);
        tag.put(SPACE_POSITION_KEY,
                SpacePosition.CODEC.encodeStart(NbtOps.INSTANCE, spacePosition).getOrThrow());
    }

    public void saveToPlayer(Player player) {
        CompoundTag tag = new CompoundTag();
        saveToTag(tag);
        player.getPersistentData().put(PERSISTENT_DATA_KEY, tag);
    }

    private static SpaceVec3 readVec(CompoundTag tag, String key) {
        CompoundTag value = tag.getCompound(key);
        return new SpaceVec3(value.getDouble(VECTOR_X_KEY), value.getDouble(VECTOR_Y_KEY),
                value.getDouble(VECTOR_Z_KEY));
    }

    private static void writeVec(CompoundTag tag, String key, SpaceVec3 value) {
        CompoundTag result = new CompoundTag();
        result.putDouble(VECTOR_X_KEY, value.x());
        result.putDouble(VECTOR_Y_KEY, value.y());
        result.putDouble(VECTOR_Z_KEY, value.z());
        tag.put(key, result);
    }

    public void detachFromShip() {
        shipId = null;
        localPosition = SpaceVec3.ZERO;
    }

}
