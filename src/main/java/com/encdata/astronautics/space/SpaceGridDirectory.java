package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

public final class SpaceGridDirectory extends SavedData {
    private static final String DATA_NAME = "astronautics_space_grids";
    private static final String GRIDS_KEY = "grids";
    private static final String ADDRESS_KEY = "address";
    private static final String LEVEL_KEY = "level";
    private static final String SHIPS_KEY = "ships";
    private static final String FRAME_OWNERS_KEY = "frame_owners";
    private static final String GRID_LEVEL_PATH = "grid/";
    private static final Factory<SpaceGridDirectory> FACTORY = new Factory<>(
            SpaceGridDirectory::new, SpaceGridDirectory::load);

    private final Map<SpaceGridAddress, ResourceKey<Level>> levelsByAddress = new HashMap<>();
    private final Map<ResourceKey<Level>, SpaceGridAddress> addressesByLevel = new HashMap<>();
    private final Map<UUID, SpaceGridAddress> shipAddresses = new HashMap<>();
    private final Map<UUID, SpaceGridAddress> frameOwnerAddresses = new HashMap<>();

    private SpaceGridDirectory() {}

    public static SpaceGridDirectory get(ServerLevel mainSpaceLevel) {
        return mainSpaceLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public ResourceKey<Level> findLevelKey(SpaceGridAddress address) {
        return levelsByAddress.get(address);
    }

    public SpaceGridAddress findAddress(ResourceKey<Level> levelKey) {
        return addressesByLevel.get(levelKey);
    }

    public SpaceGridAddress shipAddress(UUID shipId) {
        return shipAddresses.get(shipId);
    }

    public SpaceGridAddress frameOwnerAddress(UUID shipId) {
        return frameOwnerAddresses.get(shipId);
    }

    public void setShipAddress(UUID shipId, SpaceGridAddress address) {
        if (address.equals(shipAddresses.put(shipId, address))) return;
        setDirty();
    }

    public void setFrameOwnerAddress(UUID shipId, SpaceGridAddress address) {
        if (address.equals(frameOwnerAddresses.put(shipId, address))) return;
        setDirty();
    }

    public SpaceGridAddress removeShip(UUID shipId) {
        SpaceGridAddress removed = shipAddresses.remove(shipId);
        SpaceGridAddress frameOwner = frameOwnerAddresses.remove(shipId);
        if (removed != null || frameOwner != null) setDirty();
        return removed;
    }

    public SpaceGridAddress removeFrameOwner(UUID shipId) {
        SpaceGridAddress removed = frameOwnerAddresses.remove(shipId);
        if (removed != null) setDirty();
        return removed;
    }

    public ResourceKey<Level> allocateLevelKey(SpaceGridAddress address) {
        ResourceKey<Level> existing = levelsByAddress.get(address);
        if (existing != null) return existing;

        ResourceKey<Level> levelKey = addressKey(address);
        while (addressesByLevel.containsKey(levelKey)) {
            String id = GRID_LEVEL_PATH + UUID.randomUUID().toString().replace("-", "");
            levelKey = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, id));
        }

        levelsByAddress.put(address, levelKey);
        addressesByLevel.put(levelKey, address);
        setDirty();
        return levelKey;
    }

    private static ResourceKey<Level> addressKey(SpaceGridAddress address) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    (address.x() + ":" + address.y() + ":" + address.z()).getBytes(StandardCharsets.UTF_8));
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(
                    Astronautics.MOD_ID, GRID_LEVEL_PATH + HexFormat.of().formatHex(hash, 0, 16)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public void removeLevelKey(SpaceGridAddress address) {
        ResourceKey<Level> removed = levelsByAddress.remove(address);
        if (removed != null) {
            addressesByLevel.remove(removed);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag grids = new ListTag();
        for (Map.Entry<SpaceGridAddress, ResourceKey<Level>> entry : levelsByAddress.entrySet()) {
            CompoundTag grid = new CompoundTag();
            grid.put(ADDRESS_KEY, SpaceGridAddress.CODEC
                    .encodeStart(NbtOps.INSTANCE, entry.getKey()).getOrThrow());
            grid.putString(LEVEL_KEY, entry.getValue().location().toString());
            grids.add(grid);
        }
        tag.put(GRIDS_KEY, grids);

        ListTag ships = new ListTag();
        for (Map.Entry<UUID, SpaceGridAddress> entry : shipAddresses.entrySet()) {
            CompoundTag ship = new CompoundTag();
            ship.put("id", NbtUtils.createUUID(entry.getKey()));
            ship.put(ADDRESS_KEY, SpaceGridAddress.CODEC
                    .encodeStart(NbtOps.INSTANCE, entry.getValue()).getOrThrow());
            ships.add(ship);
        }
        tag.put(SHIPS_KEY, ships);

        ListTag frameOwners = new ListTag();
        for (Map.Entry<UUID, SpaceGridAddress> entry : frameOwnerAddresses.entrySet()) {
            CompoundTag frameOwner = new CompoundTag();
            frameOwner.put("id", NbtUtils.createUUID(entry.getKey()));
            frameOwner.put(ADDRESS_KEY, SpaceGridAddress.CODEC
                    .encodeStart(NbtOps.INSTANCE, entry.getValue()).getOrThrow());
            frameOwners.add(frameOwner);
        }
        tag.put(FRAME_OWNERS_KEY, frameOwners);
        return tag;
    }

    private static SpaceGridDirectory load(CompoundTag tag, HolderLookup.Provider registries) {
        SpaceGridDirectory directory = new SpaceGridDirectory();
        ListTag grids = tag.getList(GRIDS_KEY, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < grids.size(); i++) {
            try {
                directory.loadGrid(grids.getCompound(i));
            } catch (RuntimeException exception) {
                Astronautics.LOGGER.warn("Skipping broken space grid level entry {}", i, exception);
            }
        }
        loadShipAddresses(tag.getList(SHIPS_KEY, CompoundTag.TAG_COMPOUND), directory.shipAddresses);
        loadShipAddresses(tag.getList(FRAME_OWNERS_KEY, CompoundTag.TAG_COMPOUND), directory.frameOwnerAddresses);
        return directory;
    }

    private void loadGrid(CompoundTag grid) {
        SpaceGridAddress address = SpaceGridAddress.CODEC
                .parse(NbtOps.INSTANCE, grid.get(ADDRESS_KEY)).getOrThrow();
        ResourceLocation location = ResourceLocation.parse(grid.getString(LEVEL_KEY));
        if (!location.getNamespace().equals(Astronautics.MOD_ID)
                || !location.getPath().startsWith(GRID_LEVEL_PATH)) {
            throw new IllegalStateException("Invalid internal space grid level key: " + location);
        }
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, location);
        if (levelsByAddress.containsKey(address) || addressesByLevel.containsKey(levelKey)) {
            throw new IllegalStateException("Duplicate space grid directory entry: " + address);
        }
        levelsByAddress.put(address, levelKey);
        addressesByLevel.put(levelKey, address);
    }

    private static void loadShipAddresses(ListTag list, Map<UUID, SpaceGridAddress> target) {
        for (int i = 0; i < list.size(); i++) {
            try {
                CompoundTag ship = list.getCompound(i);
                UUID id = NbtUtils.loadUUID(ship.get("id"));
                SpaceGridAddress address = SpaceGridAddress.CODEC
                        .parse(NbtOps.INSTANCE, ship.get(ADDRESS_KEY)).getOrThrow();
                target.putIfAbsent(id, address);
            } catch (RuntimeException exception) {
                Astronautics.LOGGER.warn("Skipping broken space grid ship entry {}", i, exception);
            }
        }
    }
}
