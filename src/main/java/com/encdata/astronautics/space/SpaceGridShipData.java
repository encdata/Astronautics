package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpaceGridShipData extends SavedData {
    private static final String DATA_NAME = "astronautics_grid_ship_states";
    private static final String SHIPS_KEY = "ships";
    private static final Factory<SpaceGridShipData> FACTORY = new Factory<>(
            SpaceGridShipData::new, SpaceGridShipData::load);

    private final Map<UUID, SpaceGridShipState> ships = new HashMap<>();

    private SpaceGridShipData() {}

    public static SpaceGridShipData get(ServerLevel gridLevel) {
        if (!SpaceConstants.isGridLevel(gridLevel.dimension())) {
            throw new IllegalArgumentException("ship state must be stored in a space grid level");
        }
        return gridLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public SpaceGridShipState get(UUID shipId) {
        return ships.get(shipId);
    }

    public void put(UUID shipId, SpaceGridShipState state) {
        if (state.equals(ships.put(shipId, state))) return;
        setDirty();
    }

    public void remove(UUID shipId) {
        if (ships.remove(shipId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, SpaceGridShipState> entry : ships.entrySet()) {
            SpaceGridShipState state = entry.getValue();
            CompoundTag ship = new CompoundTag();
            ship.put("id", NbtUtils.createUUID(entry.getKey()));
            ship.putDouble("x", state.x());
            ship.putDouble("y", state.y());
            ship.putDouble("z", state.z());
            ship.putDouble("qx", state.rotationX());
            ship.putDouble("qy", state.rotationY());
            ship.putDouble("qz", state.rotationZ());
            ship.putDouble("qw", state.rotationW());
            ship.putDouble("rx", state.rotationPointX());
            ship.putDouble("ry", state.rotationPointY());
            ship.putDouble("rz", state.rotationPointZ());
            ship.putDouble("vx", state.velocityX());
            ship.putDouble("vy", state.velocityY());
            ship.putDouble("vz", state.velocityZ());
            ship.putDouble("wx", state.angularVelocityX());
            ship.putDouble("wy", state.angularVelocityY());
            ship.putDouble("wz", state.angularVelocityZ());
            list.add(ship);
        }
        tag.put(SHIPS_KEY, list);
        return tag;
    }

    private static SpaceGridShipData load(CompoundTag tag, HolderLookup.Provider registries) {
        SpaceGridShipData data = new SpaceGridShipData();
        ListTag list = tag.getList(SHIPS_KEY, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                CompoundTag ship = list.getCompound(i);
                UUID id = NbtUtils.loadUUID(ship.get("id"));
                SpaceGridShipState state = new SpaceGridShipState(
                        ship.getDouble("x"), ship.getDouble("y"), ship.getDouble("z"),
                        ship.getDouble("qx"), ship.getDouble("qy"), ship.getDouble("qz"), ship.getDouble("qw"),
                        ship.getDouble("rx"), ship.getDouble("ry"), ship.getDouble("rz"),
                        ship.getDouble("vx"), ship.getDouble("vy"), ship.getDouble("vz"),
                        ship.getDouble("wx"), ship.getDouble("wy"), ship.getDouble("wz"));
                data.ships.putIfAbsent(id, state);
            } catch (RuntimeException exception) {
                Astronautics.LOGGER.warn("Skipping broken space grid ship state entry {}", i, exception);
            }
        }
        return data;
    }
}
