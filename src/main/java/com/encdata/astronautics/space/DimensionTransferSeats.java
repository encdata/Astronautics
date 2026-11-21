package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class DimensionTransferSeats {
    private static final ResourceLocation CREATE_SEAT_ENTITY =
            ResourceLocation.fromNamespaceAndPath("create", "seat");
    private static final Class<?> CREATE_SEAT_BLOCK = loadCreateSeatBlock();

    private DimensionTransferSeats() {}

    static boolean isCreateSeat(Entity vehicle) {
        return vehicle != null && BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType())
                .equals(CREATE_SEAT_ENTITY);
    }

    static void sitOnCreateSeatBlock(ServerLevel level, ServerSubLevel subLevel,
                                     BlockPos seatPosition, ServerPlayer player,
                                     Set<BlockPos> reservedSeats) {
        if (reservedSeats.contains(seatPosition)
                || Sable.HELPER.getContaining(level, seatPosition) != subLevel
                || !isCreateSeatBlock(level, seatPosition)) {
            Astronautics.LOGGER.warn(
                    "Destination Create seat block missing at {} for {}",
                    seatPosition, player.getGameProfile().getName());
            return;
        }
        try {
            Class<?> seatBlock = Class.forName(
                    "com.simibubi.create.content.contraptions.actors.seat.SeatBlock");
            seatBlock.getMethod("sitDown", Level.class,
                    BlockPos.class, Entity.class).invoke(null, level, seatPosition, player);
            reservedSeats.add(seatPosition);
        } catch (ReflectiveOperationException exception) {
            Astronautics.LOGGER.warn(
                    "Could not attach passenger to destination Create seat block {}", seatPosition, exception);
            return;
        }
    }

    static BlockPos createSeatPlotOffset(ServerLevel source, ServerPlayer player, Entity vehicle,
                                         ServerSubLevel subLevel) {
        if (!isCreateSeat(vehicle)) return null;
        for (BlockPos center : List.of(vehicle.blockPosition(), player.getOnPos(),
                vehicle.getOnPos(), vehicle.getOnPosLegacy())) {
            BlockPos seat = findCreateSeatBlock(source, subLevel, center, 1, Set.of());
            if (seat != null) return DimensionTransferGeometry.toPlotOffset(subLevel, seat);
        }
        Astronautics.LOGGER.warn(
                "Could not find source Create seat block for {} in sublevel {}",
                player.getGameProfile().getName(), subLevel.getUniqueId());
        return null;
    }

    static BlockPos findCreateSeatBlock(ServerLevel level, ServerSubLevel subLevel,
                                        BlockPos center, int radius, Set<BlockPos> reservedSeats) {
        if (Sable.HELPER.getContaining(level, center) == subLevel
                && !reservedSeats.contains(center) && isCreateSeatBlock(level, center)) {
            return center;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos candidate = center.offset(dx, dy, dz);
                    if (!reservedSeats.contains(candidate)
                            && Sable.HELPER.getContaining(level, candidate) == subLevel
                            && isCreateSeatBlock(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    static boolean hasDestinationSeats(ServerLevel destination,
                                       Map<UUID, ServerSubLevel> transferred,
                                       Collection<DimensionTransferPassengers.Snapshot> passengers) {
        for (DimensionTransferPassengers.Snapshot passenger : passengers) {
            if (!isCreateSeat(passenger.vehicle()) || passenger.createSeatPlotOffset() == null) continue;
            ServerSubLevel subLevel = transferred.get(passenger.subLevelId());
            if (subLevel == null) return false;
            BlockPos seat = DimensionTransferGeometry.destinationSeatPosition(
                    subLevel, passenger.createSeatPlotOffset());
            if (Sable.HELPER.getContaining(destination, seat) != subLevel
                    || !isCreateSeatBlock(destination, seat)) {
                Astronautics.LOGGER.error(
                        "Refusing transfer: destination seat block {} for {} was not restored in sublevel {}",
                        seat, passenger.player().getGameProfile().getName(), subLevel.getUniqueId());
                return false;
            }
        }
        return true;
    }

    static boolean isCreateSeatBlock(ServerLevel level, BlockPos position) {
        var block = level.getBlockState(position).getBlock();
        if (CREATE_SEAT_BLOCK != null) return CREATE_SEAT_BLOCK.isInstance(block);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        return blockId.getNamespace().equals("create") && blockId.getPath().endsWith("_seat");
    }

    private static Class<?> loadCreateSeatBlock() {
        try {
            return Class.forName("com.simibubi.create.content.contraptions.actors.seat.SeatBlock");
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
