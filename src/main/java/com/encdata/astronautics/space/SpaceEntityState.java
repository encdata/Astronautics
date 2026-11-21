package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class SpaceEntityState {
    private static final String KEY = "astronautics_space_position";
    private static final String FRAME_KEY = "astronautics_space_frame";
    private static final String OWNER_KEY = "astronautics_space_owner";

    private SpaceEntityState() {}

    public static Optional<SpacePosition> get(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            var runtime = Astronautics.SPACE_RUNTIME.addPlayer(player.getUUID());
            if (!runtime.nbtLoaded()) {
                runtime.loadFromTag(player.getPersistentData().getCompound(SpacePlayerState.PERSISTENT_DATA_KEY));
            }
            if (SpaceConstants.isSpace(player.level())) {
                if (player.getPersistentData().getCompound(SpacePlayerState.PERSISTENT_DATA_KEY)
                        .contains("space_position")) {
                    return Optional.of(runtime.spacePosition());
                }
                SpaceGridAddress address = player.server == null ? SpaceGridAddress.ORIGIN
                        : SpaceGridLevelFactory.addressOf(player.server, player.level().dimension());
                if (address == null) address = SpaceGridAddress.ORIGIN;
                return Optional.of(SpacePosition.normalize(address,
                        player.getX(), player.getY(), player.getZ()));
            }
        }
        var tag = entity.getPersistentData().get(KEY);
        if (tag == null) return Optional.empty();
        return SpacePosition.CODEC.parse(NbtOps.INSTANCE, tag).result();
    }

    public static SpacePosition position(Entity entity) {
        if (!(entity instanceof ServerPlayer) && SpaceConstants.isSpace(entity.level())) {
            SpaceGridAddress grid = ((SpaceGrids.Holder) entity).astronautics$positionGrid();
            if (grid == null) grid = get(entity).map(SpacePosition::grid).orElseGet(() -> storedPosition(entity).grid());
            return SpacePosition.normalize(grid, entity.getX(), entity.getY(), entity.getZ());
        }
        return storedPosition(entity);
    }

    static Optional<SpaceGridAddress> owner(Entity entity) {
        var tag = entity.getPersistentData().get(OWNER_KEY);
        if (tag == null) return Optional.empty();
        return SpaceGridAddress.CODEC.parse(NbtOps.INSTANCE, tag).result();
    }

    static SpaceGridAddress frame(Entity entity) {
        var tag = entity.getPersistentData().get(FRAME_KEY);
        return tag == null ? get(entity).map(SpacePosition::grid).orElse(null)
                : SpaceGridAddress.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }

    static void storeOwner(Entity entity, SpaceGridAddress grid) {
        entity.getPersistentData().put(OWNER_KEY,
                SpaceGridAddress.CODEC.encodeStart(NbtOps.INSTANCE, grid).getOrThrow());
    }

    static SpacePosition storedPosition(Entity entity) {
        return get(entity).orElseGet(() -> {
            SpaceGridAddress address = entity.level().dimension().location().getNamespace()
                    .equals(Astronautics.MOD_ID)
                    && entity.level().dimension().location().getPath().startsWith("grid/")
                    && entity.level() instanceof net.minecraft.server.level.ServerLevel level
                    ? SpaceGridLevelFactory.addressOf(level.getServer(), level.dimension())
                    : SpaceGridAddress.ORIGIN;
            if (address == null) address = SpaceGridAddress.ORIGIN;
            return SpacePosition.normalize(address, entity.getX(), entity.getY(), entity.getZ());
        });
    }

    static void setFrame(Entity entity, SpaceGridAddress frame) {
        if (entity instanceof ServerPlayer) return;
        SpaceGrids.Holder holder = (SpaceGrids.Holder) entity;
        if (frame.equals(holder.astronautics$positionGrid())) return;
        SpacePosition position = SpacePosition.normalize(frame, entity.getX(), entity.getY(), entity.getZ());
        entity.getPersistentData().put(KEY,
                SpacePosition.CODEC.encodeStart(NbtOps.INSTANCE, position).getOrThrow());
        entity.getPersistentData().put(FRAME_KEY,
                SpaceGridAddress.CODEC.encodeStart(NbtOps.INSTANCE, frame).getOrThrow());
        holder.astronautics$setPositionGrid(frame);
    }

    public static void set(Entity entity, SpacePosition position) {
        if (entity instanceof ServerPlayer player) {
            var runtime = Astronautics.SPACE_RUNTIME.addPlayer(player.getUUID());
            if (!runtime.nbtLoaded()) {
                runtime.loadFromTag(player.getPersistentData().getCompound(SpacePlayerState.PERSISTENT_DATA_KEY));
            }
            runtime.setSpacePosition(position);
            runtime.saveToPlayer(player);
        } else {
            entity.getPersistentData().put(KEY,
                    SpacePosition.CODEC.encodeStart(NbtOps.INSTANCE, position).getOrThrow());
            entity.getPersistentData().put(FRAME_KEY,
                    SpaceGridAddress.CODEC.encodeStart(NbtOps.INSTANCE, position.grid()).getOrThrow());
            ((SpaceGrids.Holder) entity).astronautics$setPositionGrid(position.grid());
        }
        SpaceGrids.set(entity, position.grid());
    }
}
