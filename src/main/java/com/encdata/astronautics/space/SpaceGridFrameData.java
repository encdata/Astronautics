package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SpaceGridFrameData extends SavedData {
    private static final String DATA_NAME = "astronautics_physics_frames";
    private static final String FRAMES_KEY = "frames";
    private static final Factory<SpaceGridFrameData> FACTORY = new Factory<>(
            SpaceGridFrameData::new, SpaceGridFrameData::load);

    private final Map<UUID, Frame> framesById = new HashMap<>();
    private final Map<SpacePhysicsFrameGrid.Cell, UUID> framesByCell = new HashMap<>();
    private final Map<UUID, UUID> frameByShip = new HashMap<>();

    private SpaceGridFrameData() {}

    public static SpaceGridFrameData get(ServerLevel level) {
        if (!SpaceConstants.isGridLevel(level.dimension())) {
            throw new IllegalArgumentException("physics frames must be stored in a space grid level");
        }
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Frame allocate(SpacePosition origin, UUID firstShip) {
        return allocate(origin, origin.grid(), firstShip);
    }

    public Frame allocate(SpacePosition origin, SpaceGridAddress projectionGrid, UUID firstShip) {
        if (frameByShip.containsKey(firstShip)) {
            throw new IllegalStateException("ship already belongs to a physics frame: " + firstShip);
        }
        return addShip(allocateEmpty(origin, projectionGrid).id(), firstShip);
    }

    Frame allocateEmpty(SpacePosition origin, SpaceGridAddress projectionGrid) {
        if (!SpacePhysicsFrameGrid.isPrecise()) {
            throw new IllegalStateException("configured physics frame grid exceeds the precise Rapier extent");
        }

        SpacePhysicsFrameGrid.Cell cell = SpacePhysicsFrameGrid.nearestFree(
                candidate -> !framesByCell.containsKey(candidate))
                .orElseThrow(() -> new IllegalStateException("no free physics frame cells"));
        Frame frame = new Frame(UUID.randomUUID(), cell, origin, projectionGrid, Set.of(), false);
        framesById.put(frame.id(), frame);
        framesByCell.put(cell, frame.id());
        return frame;
    }

    void removeEmptyFrame(UUID frameId) {
        Frame frame = framesById.get(frameId);
        if (frame == null || !frame.ships().isEmpty()) return;
        framesById.remove(frameId);
        framesByCell.remove(frame.cell());
    }

    Frame addShip(UUID frameId, UUID shipId) {
        if (frameByShip.containsKey(shipId)) {
            throw new IllegalStateException("ship already belongs to a physics frame: " + shipId);
        }
        Frame frame = requireFrame(frameId);
        Set<UUID> ships = new HashSet<>(frame.ships());
        ships.add(shipId);
        Frame updated = new Frame(frame.id(), frame.cell(), frame.origin(), frame.projectionGrid(),
                ships, frame.constrained());
        framesById.put(frameId, updated);
        frameByShip.put(shipId, frameId);
        setDirty();
        return updated;
    }

    Frame moveShip(UUID shipId, UUID targetFrameId) {
        UUID sourceFrameId = frameByShip.get(shipId);
        if (sourceFrameId == null) throw new IllegalStateException("ship has no physics frame: " + shipId);
        if (sourceFrameId.equals(targetFrameId)) return requireFrame(targetFrameId);

        Frame source = requireFrame(sourceFrameId);
        Frame target = requireFrame(targetFrameId);
        Set<UUID> targetShips = new HashSet<>(target.ships());
        targetShips.add(shipId);
        Frame updatedTarget = new Frame(target.id(), target.cell(), target.origin(), target.projectionGrid(),
                targetShips, target.constrained());

        Set<UUID> sourceShips = new HashSet<>(source.ships());
        sourceShips.remove(shipId);
        if (sourceShips.isEmpty()) {
            framesById.remove(sourceFrameId);
            framesByCell.remove(source.cell());
        } else {
            framesById.put(sourceFrameId, new Frame(source.id(), source.cell(), source.origin(),
                    source.projectionGrid(), sourceShips, source.constrained()));
        }
        framesById.put(targetFrameId, updatedTarget);
        frameByShip.put(shipId, targetFrameId);
        setDirty();
        return updatedTarget;
    }

    void restoreMembership(Frame previous, UUID shipId) {
        UUID currentFrameId = frameByShip.get(shipId);
        if (currentFrameId != null) {
            if (currentFrameId.equals(previous.id())) return;
            throw new IllegalStateException("ship already belongs to another physics frame: " + shipId);
        }

        Frame current = framesById.get(previous.id());
        if (current == null) {
            UUID cellOwner = framesByCell.get(previous.cell());
            if (cellOwner != null && !cellOwner.equals(previous.id())) {
                throw new IllegalStateException("physics frame cell is already occupied: " + previous.cell());
            }
            Set<UUID> ships = new HashSet<>(previous.ships());
            ships.add(shipId);
            Frame restored = new Frame(previous.id(), previous.cell(), previous.origin(),
                    previous.projectionGrid(), ships, previous.constrained());
            framesById.put(restored.id(), restored);
            framesByCell.put(restored.cell(), restored.id());
            frameByShip.put(shipId, restored.id());
            setDirty();
            return;
        }

        Set<UUID> ships = new HashSet<>(current.ships());
        ships.add(shipId);
        Frame restored = new Frame(current.id(), current.cell(), current.origin(),
                current.projectionGrid(), ships, current.constrained());
        framesById.put(restored.id(), restored);
        frameByShip.put(shipId, restored.id());
        setDirty();
    }

    public Frame setOrigin(UUID frameId, SpacePosition origin) {
        Frame frame = requireFrame(frameId);
        Frame updated = new Frame(frame.id(), frame.cell(), origin, frame.projectionGrid(),
                frame.ships(), frame.constrained());
        framesById.put(frameId, updated);
        setDirty();
        return updated;
    }

    public Frame setProjectionGrid(UUID frameId, SpaceGridAddress projectionGrid) {
        Frame frame = requireFrame(frameId);
        if (frame.projectionGrid().equals(projectionGrid)) return frame;
        Frame updated = new Frame(frame.id(), frame.cell(), frame.origin(), projectionGrid,
                frame.ships(), frame.constrained());
        framesById.put(frameId, updated);
        setDirty();
        return updated;
    }

    public Frame markConstrained(UUID frameId) {
        return setConstrained(frameId, true);
    }

    public Frame setConstrained(UUID frameId, boolean constrained) {
        Frame frame = requireFrame(frameId);
        if (frame.constrained() == constrained) return frame;
        Frame updated = new Frame(frame.id(), frame.cell(), frame.origin(), frame.projectionGrid(),
                frame.ships(), constrained);
        framesById.put(frameId, updated);
        setDirty();
        return updated;
    }

    public boolean hasFreeCell() {
        return framesByCell.size() < SpacePhysicsFrameGrid.cells().size();
    }

    public Optional<Frame> frame(UUID frameId) {
        return Optional.ofNullable(framesById.get(frameId));
    }

    public Optional<Frame> frameForShip(UUID shipId) {
        UUID frameId = frameByShip.get(shipId);
        return frameId == null ? Optional.empty() : frame(frameId);
    }

    public Optional<Frame> removeShip(UUID shipId) {
        UUID frameId = frameByShip.remove(shipId);
        if (frameId == null) return Optional.empty();

        Frame frame = framesById.get(frameId);
        Set<UUID> ships = new HashSet<>(frame.ships());
        ships.remove(shipId);
        if (ships.isEmpty()) {
            framesById.remove(frameId);
            framesByCell.remove(frame.cell());
            setDirty();
            return Optional.empty();
        }

        Frame updated = new Frame(frame.id(), frame.cell(), frame.origin(), frame.projectionGrid(),
                ships, frame.constrained());
        framesById.put(frameId, updated);
        setDirty();
        return Optional.of(updated);
    }

    public Set<Frame> frames() {
        return Set.copyOf(framesById.values());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Frame frame : framesById.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put("id", NbtUtils.createUUID(frame.id()));
            entry.putInt("cell_x", frame.cell().x());
            entry.putInt("cell_y", frame.cell().y());
            entry.putInt("cell_z", frame.cell().z());
            entry.put("origin", SpacePosition.CODEC.encodeStart(NbtOps.INSTANCE, frame.origin()).getOrThrow());
            entry.put("projection_grid", SpaceGridAddress.CODEC.encodeStart(
                    NbtOps.INSTANCE, frame.projectionGrid()).getOrThrow());
            entry.putBoolean("constrained", frame.constrained());
            ListTag ships = new ListTag();
            for (UUID shipId : frame.ships()) ships.add(NbtUtils.createUUID(shipId));
            entry.put("ships", ships);
            list.add(entry);
        }
        tag.put(FRAMES_KEY, list);
        return tag;
    }

    private static SpaceGridFrameData load(CompoundTag tag, HolderLookup.Provider registries) {
        SpaceGridFrameData data = new SpaceGridFrameData();
        ListTag list = tag.getList(FRAMES_KEY, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.loadFrame(list.getCompound(i));
            } catch (RuntimeException exception) {
                Astronautics.LOGGER.warn("Skipping broken space physics frame entry {}", i, exception);
            }
        }
        return data;
    }

    private void loadFrame(CompoundTag entry) {
        SpacePhysicsFrameGrid.Cell cell = new SpacePhysicsFrameGrid.Cell(
                entry.getInt("cell_x"), entry.getInt("cell_y"), entry.getInt("cell_z"));
        if (!SpacePhysicsFrameGrid.cells().contains(cell)) {
            throw new IllegalStateException("invalid physics frame cell: " + cell);
        }
        UUID id = NbtUtils.loadUUID(entry.get("id"));
        if (framesById.containsKey(id) || framesByCell.containsKey(cell)) {
            throw new IllegalStateException("duplicate physics frame or cell: " + id);
        }
        SpacePosition origin = SpacePosition.CODEC.parse(NbtOps.INSTANCE, entry.get("origin")).getOrThrow();
        ListTag shipTags = entry.getList("ships", CompoundTag.TAG_INT_ARRAY);
        Set<UUID> ships = new HashSet<>();
        for (int j = 0; j < shipTags.size(); j++) {
            UUID shipId = NbtUtils.loadUUID(shipTags.get(j));
            if (!frameByShip.containsKey(shipId)) ships.add(shipId);
        }
        if (ships.isEmpty()) throw new IllegalStateException("empty physics frame: " + id);

        for (UUID shipId : ships) frameByShip.put(shipId, id);
        SpaceGridAddress projectionGrid = entry.contains("projection_grid")
                ? SpaceGridAddress.CODEC.parse(NbtOps.INSTANCE, entry.get("projection_grid")).getOrThrow()
                : origin.grid();
        framesById.put(id, new Frame(id, cell, origin, projectionGrid, ships, entry.getBoolean("constrained")));
        framesByCell.put(cell, id);
    }

    private Frame requireFrame(UUID frameId) {
        Frame frame = framesById.get(frameId);
        if (frame == null) throw new IllegalArgumentException("unknown physics frame: " + frameId);
        return frame;
    }

    public record Frame(UUID id, SpacePhysicsFrameGrid.Cell cell, SpacePosition origin,
                        SpaceGridAddress projectionGrid, Set<UUID> ships, boolean constrained) {
        public Frame {
            ships = Set.copyOf(ships);
        }
    }
}
