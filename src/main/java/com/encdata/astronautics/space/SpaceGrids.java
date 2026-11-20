package com.encdata.astronautics.space;

import com.encdata.astronautics.AstronauticsNetworkSync;
import com.encdata.astronautics.network.SpaceGridPoseRebasePayload;
import com.google.common.collect.Iterables;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.joml.Vector2i;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class SpaceGrids {
    private static final ThreadLocal<SpaceGridAddress> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<SpaceGridAddress> FRAME = new ThreadLocal<>();
    private static final double SPAWN_PLAYER_RANGE = 512.0;
    private static final int ENTITY_SHIP_CHECK_INTERVAL = 5;

    private SpaceGrids() {}

    public static boolean isMainSpace(Level level) {
        return level instanceof ServerLevel && SpaceConstants.SPACE_LEVEL.equals(level.dimension());
    }

    public static SpaceGridAddress of(Entity entity) {
        if (isMainSpace(entity.level())) {
            SubLevel attached = SpaceShipIntegration.containing(entity);
            if (attached instanceof ServerSubLevel ship) {
                return simulationGrid((ServerLevel) entity.level(), ship);
            }
        }
        Holder holder = (Holder) entity;
        SpaceGridAddress grid = holder.astronautics$grid();
        if (grid == null) {
            grid = SpaceEntityState.owner(entity).orElseGet(() -> {
                if (entity.level() instanceof ServerLevel level
                        && SpaceConstants.isGridLevel(level.dimension())) {
                    SpaceGridAddress address = SpaceGridLevelFactory.addressOf(level.getServer(), level.dimension());
                    if (address != null) return address;
                }
                return SpaceEntityState.storedPosition(entity).grid();
            });
            holder.astronautics$setGrid(grid);
        }
        if (holder.astronautics$positionGrid() == null) {
            holder.astronautics$setPositionGrid(SpaceEntityState.storedPosition(entity).grid());
        }
        return grid;
    }

    public static void set(Entity entity, SpaceGridAddress grid) {
        Holder holder = (Holder) entity;
        if (grid.equals(holder.astronautics$grid())
                && SpaceEntityState.owner(entity).filter(grid::equals).isPresent()) return;
        holder.astronautics$setGrid(grid);
        SpaceEntityState.storeOwner(entity, grid);
    }

    public static void rebasePlayer(ServerPlayer player, SpaceGridAddress positionGrid) {
        SpaceGridAddress previous = SpaceProjection.frameGrid(player);
        if (positionGrid.equals(previous)) return;
        sendProjectionShift(player, previous, positionGrid);
    }

    public static void moveToFrame(ServerPlayer player, double x, double y, double z) {
        Vec3 velocity = player.getDeltaMovement();
        player.setPos(x, y, z);
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot(), RelativeMovement.ALL);
        player.connection.resetPosition();
        player.setDeltaMovement(velocity);
    }

    public static boolean pulledOntoShip(ServerPlayer player, SpaceGridAddress from, SpaceGridAddress to) {
        if (!isMainSpace(player.level()) || !adjacent(from, to)) return false;
        return Sable.HELPER.getTrackingSubLevel(player) instanceof ServerSubLevel ship
                && to.equals(shipGrid((ServerLevel) player.level(), ship));
    }

    public static void joinShipFrame(ServerPlayer player, ServerSubLevel ship) {
        SpaceGridAddress own = frameOf(player);
        SpaceGridAddress target = shipGrid((ServerLevel) player.level(), ship);
        if (own.equals(target) || !adjacent(own, target)) return;
        double x = player.getX() + own.x().subtract(target.x()).intValue() * SpacePosition.GRID_SIZE;
        double y = player.getY() + own.y().subtract(target.y()).intValue() * SpacePosition.GRID_SIZE;
        double z = player.getZ() + own.z().subtract(target.z()).intValue() * SpacePosition.GRID_SIZE;
        rebasePlayer(player, target);
        moveToFrame(player, x, y, z);
    }

    public static SpaceGridAddress playerAnchorGrid(ServerPlayer player) {
        List<ServerPlayer> players = player.serverLevel().players();
        List<SpacePosition> positions = players.stream().map(SpaceProjection::observerPosition).toList();
        int start = players.indexOf(player);
        if (start < 0) return SpaceProjection.frameGrid(player);

        boolean[] included = new boolean[players.size()];
        List<Integer> queue = new ArrayList<>();
        List<SpacePosition> anchors = new ArrayList<>();
        included[start] = true;
        queue.add(start);
        for (int index = 0; index < queue.size(); index++) {
            SpacePosition anchor = positions.get(queue.get(index));
            anchors.add(anchor);
            for (int candidate = 0; candidate < players.size(); candidate++) {
                if (!included[candidate]
                        && anchor.deltaWithin(positions.get(candidate), SpaceConstants.SHIP_GRID_ATTACH_RANGE).isPresent()) {
                    included[candidate] = true;
                    queue.add(candidate);
                }
            }
        }
        ServerPlayer stableAnchor = queue.stream().map(players::get)
                .min((first, second) -> first.getUUID().compareTo(second.getUUID())).orElse(player);
        SpaceGridAddress currentFrame = SpaceProjection.frameGrid(stableAnchor);
        return SpacePosition.average(anchors, 30_000_000.0)
                .map(center -> stableFrame(currentFrame, center, SpaceConstants.SHIP_GRID_ATTACH_RANGE * 0.5))
                .orElse(currentFrame);
    }

    static SpaceGridAddress stableFrame(SpaceGridAddress current, SpacePosition center, double margin) {
        SpacePosition origin = new SpacePosition(current, 0.0, 0.0, 0.0);
        SpaceVec3 delta = origin.deltaWithin(center, SpacePosition.GRID_SIZE * 3.0).orElse(null);
        if (delta == null) return center.grid();
        return SpacePosition.normalize(current,
                crossGridEdge(delta.x(), margin) ? delta.x() : 0.0,
                crossGridEdge(delta.y(), margin) ? delta.y() : 0.0,
                crossGridEdge(delta.z(), margin) ? delta.z() : 0.0).grid();
    }

    private static boolean crossGridEdge(double local, double margin) {
        return local >= SpacePosition.GRID_HALF_SIZE + margin
                || local < -SpacePosition.GRID_HALF_SIZE - margin;
    }

    public static void alignPlayerFrame(ServerPlayer player, SpaceGridAddress target) {
        if (SpaceProjection.frameGrid(player).equals(target)) return;
        SpacePosition position = SpaceProjection.observerPosition(player);
        SpacePosition origin = new SpacePosition(target, 0.0, 0.0, 0.0);
        Vec3 local = origin.deltaWithin(position, SpacePosition.GRID_SIZE)
                .map(delta -> new Vec3(delta.x(), delta.y(), delta.z())).orElse(null);
        if (local == null) return;
        rebasePlayer(player, target);
        moveToFrame(player, local.x, local.y, local.z);
    }

    public static void shiftClient(ServerPlayer player, SpaceGridAddress from, SpaceGridAddress to) {
        sendProjectionShift(player, from, to);
    }

    public static boolean adjacent(SpaceGridAddress first, SpaceGridAddress second) {
        return first.x().subtract(second.x()).abs().compareTo(BigInteger.ONE) <= 0
                && first.y().subtract(second.y()).abs().compareTo(BigInteger.ONE) <= 0
                && first.z().subtract(second.z()).abs().compareTo(BigInteger.ONE) <= 0;
    }

    public static SpaceGridAddress context() {
        return CONTEXT.get();
    }

    public static SpaceGridAddress enter(SpaceGridAddress grid) {
        SpaceGridAddress previous = CONTEXT.get();
        CONTEXT.set(grid);
        return previous;
    }

    public static void exit(SpaceGridAddress previous) {
        if (previous == null) CONTEXT.remove();
        else CONTEXT.set(previous);
    }

    public static SpaceGridAddress frame() {
        return FRAME.get();
    }

    public static SpaceGridAddress enterFrame(SpaceGridAddress grid) {
        SpaceGridAddress previous = FRAME.get();
        FRAME.set(grid);
        return previous;
    }

    public static void exitFrame(SpaceGridAddress previous) {
        if (previous == null) FRAME.remove();
        else FRAME.set(previous);
    }

    public static boolean placedInSpace(ServerLevel level, double x, double z) {
        return FRAME.get() != null && isMainSpace(level)
                && SpaceChunkLoading.isVirtual(level, Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    public static Vec3 imageFor(ServerPlayer viewer, double x, double y, double z) {
        SpaceGridAddress anchor = FRAME.get();
        if (anchor == null) return Vec3.ZERO;
        long gridX = Math.round((viewer.getX() - x) / SpacePosition.GRID_SIZE);
        long gridY = Math.round((viewer.getY() - y) / SpacePosition.GRID_SIZE);
        long gridZ = Math.round((viewer.getZ() - z) / SpacePosition.GRID_SIZE);
        boolean same = gridX == 0L && gridY == 0L && gridZ == 0L;
        SpaceGridAddress source = frameOf(viewer);
        if (!same) {
            source = source.offset(BigInteger.valueOf(gridX), BigInteger.valueOf(gridY), BigInteger.valueOf(gridZ));
        }
        if (!adjacent(source, anchor)) return null;
        return same ? Vec3.ZERO : new Vec3(gridX * SpacePosition.GRID_SIZE, gridY * SpacePosition.GRID_SIZE,
                gridZ * SpacePosition.GRID_SIZE);
    }

    public static Vec3 shipOffset(Entity entity, ServerSubLevel ship) {
        if (!(entity.level() instanceof ServerLevel level) || !isMainSpace(level) || ship.getLevel() != level) {
            return Vec3.ZERO;
        }
        SpaceGridAddress from = frameOf(entity);
        SpaceGridAddress to = shipGrid(level, ship);
        if (from.equals(to)) return Vec3.ZERO;
        if (!adjacent(from, to)) return null;
        return new Vec3(from.x().subtract(to.x()).intValue() * SpacePosition.GRID_SIZE,
                from.y().subtract(to.y()).intValue() * SpacePosition.GRID_SIZE,
                from.z().subtract(to.z()).intValue() * SpacePosition.GRID_SIZE);
    }

    public static <T> T within(SpaceGridAddress grid, Supplier<T> action) {
        SpaceGridAddress previous = enter(grid);
        try {
            return action.get();
        } finally {
            exit(previous);
        }
    }

    public static boolean sees(SpaceGridAddress viewer, Entity entity) {
        if (viewer == null) return true;
        SpaceGridAddress grid = of(entity);
        return grid == viewer || grid.equals(viewer);
    }

    public static boolean seesContext(Entity target) {
        SpaceGridAddress viewer = context();
        return viewer == null || viewer.equals(of(target));
    }

    private static SpaceGridFrameData.Frame frameData(ServerSubLevel ship) {
        if (!(ship.getLevel() instanceof ServerLevel level) || !isMainSpace(level)) return null;
        SpaceGridDirectory directory = SpaceGridDirectory.get(level);
        SpaceGridAddress owner = directory.frameOwnerAddress(ship.getUniqueId());
        if (owner == null) return null;
        ServerLevel frameLevel = SpaceGridLevelFactory.getOrCreate(level.getServer(), owner);
        return SpaceGridFrameData.get(frameLevel).frameForShip(ship.getUniqueId())
                .orElse(null);
    }

    public static SpaceGridAddress frameOf(Entity entity) {
        if (entity instanceof ServerPlayer player) return SpaceProjection.frameGrid(player);
        SpaceGridAddress grid = ((Holder) entity).astronautics$positionGrid();
        return grid != null ? grid : SpaceEntityState.position(entity).grid();
    }

    public static Vec3 viewOffset(ServerPlayer viewer, Entity entity) {
        if (!isMainSpace(entity.level()) || inPlot(entity)) return Vec3.ZERO;
        SpaceGridAddress from = frameOf(entity);
        SpaceGridAddress to = frameOf(viewer);
        if (from == to || from.equals(to)) return Vec3.ZERO;
        BigInteger dx = from.x().subtract(to.x());
        BigInteger dy = from.y().subtract(to.y());
        BigInteger dz = from.z().subtract(to.z());
        if (dx.abs().compareTo(BigInteger.ONE) > 0 || dy.abs().compareTo(BigInteger.ONE) > 0
                || dz.abs().compareTo(BigInteger.ONE) > 0) {
            return null;
        }
        return new Vec3(dx.intValue() * SpacePosition.GRID_SIZE, dy.intValue() * SpacePosition.GRID_SIZE,
                dz.intValue() * SpacePosition.GRID_SIZE);
    }

    public static double distanceSqr(ServerPlayer player, Entity entity) {
        Vec3 offset = viewOffset(player, entity);
        if (offset == null) return Double.POSITIVE_INFINITY;
        return player.distanceToSqr(entity.getX() + offset.x, entity.getY() + offset.y, entity.getZ() + offset.z);
    }

    public static ServerPlayer nearestPlayer(ServerLevel level, Entity entity) {
        ServerPlayer nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            double distance = distanceSqr(player, entity);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    public static Packet<?> viewPacket(Packet<?> packet, ServerPlayer viewer) {
        if (!(viewer.level() instanceof ServerLevel level) || !isMainSpace(level)
                || !level.getServer().isSameThread()) {
            return packet;
        }
        if (packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
            boolean changed = false;
            for (Packet<? super ClientGamePacketListener> part : bundle.subPackets()) {
                @SuppressWarnings("unchecked")
                Packet<? super ClientGamePacketListener> viewed =
                        (Packet<? super ClientGamePacketListener>) viewPacket(part, viewer);
                changed |= viewed != part;
                packets.add(viewed);
            }
            return changed ? new ClientboundBundlePacket(packets) : packet;
        }
        if (FRAME.get() != null) {
            Packet<?> placed = placedPacket(packet, viewer, level);
            if (placed != null) return placed;
        }
        int id;
        if (packet instanceof ClientboundAddEntityPacket add) id = add.getId();
        else if (packet instanceof ClientboundTeleportEntityPacket teleport) id = teleport.getId();
        else if (packet instanceof ClientboundAddExperienceOrbPacket orb) id = orb.getId();
        else return packet;

        Entity entity = unfiltered(() -> level.getEntity(id));
        if (entity == null || entity == viewer) return packet;
        Vec3 offset = viewOffset(viewer, entity);
        if (offset == null || offset.equals(Vec3.ZERO)) return packet;

        if (packet instanceof ClientboundAddEntityPacket add) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess(),
                    ConnectionType.NEOFORGE);
            ClientboundAddEntityPacket.STREAM_CODEC.encode(buffer, add);
            buffer.readVarInt();
            buffer.readUUID();
            buffer.readVarInt();
            return shifted(level, packet, ClientboundAddEntityPacket.STREAM_CODEC, buffer, offset);
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            ClientboundTeleportEntityPacket.STREAM_CODEC.encode(buffer, teleport);
            buffer.readVarInt();
            return shifted(level, packet, ClientboundTeleportEntityPacket.STREAM_CODEC, buffer, offset);
        }
        ClientboundAddExperienceOrbPacket.STREAM_CODEC.encode(buffer, (ClientboundAddExperienceOrbPacket) packet);
        buffer.readVarInt();
        return shifted(level, packet, ClientboundAddExperienceOrbPacket.STREAM_CODEC, buffer, offset);
    }

    private static Packet<?> placedPacket(Packet<?> packet, ServerPlayer viewer, ServerLevel level) {
        if (packet instanceof ClientboundSoundPacket sound) {
            Vec3 image = image(viewer, level, sound.getX(), sound.getY(), sound.getZ());
            return image == null ? packet : new ClientboundSoundPacket(sound.getSound(), sound.getSource(),
                    sound.getX() + image.x, sound.getY() + image.y, sound.getZ() + image.z,
                    sound.getVolume(), sound.getPitch(), sound.getSeed());
        }
        if (packet instanceof ClientboundLevelParticlesPacket particles) {
            Vec3 image = image(viewer, level, particles.getX(), particles.getY(), particles.getZ());
            return image == null ? packet : new ClientboundLevelParticlesPacket(particles.getParticle(),
                    particles.isOverrideLimiter(), particles.getX() + image.x, particles.getY() + image.y,
                    particles.getZ() + image.z, particles.getXDist(), particles.getYDist(), particles.getZDist(),
                    particles.getMaxSpeed(), particles.getCount());
        }
        if (packet instanceof ClientboundExplodePacket explosion) {
            Vec3 image = image(viewer, level, explosion.getX(), explosion.getY(), explosion.getZ());
            if (image == null) return packet;
            BlockPos shift = BlockPos.containing(image);
            List<BlockPos> blocks = new ArrayList<>(explosion.getToBlow().size());
            for (BlockPos block : explosion.getToBlow()) {
                blocks.add(inPlot(level, block) ? block : block.offset(shift));
            }
            return new ClientboundExplodePacket(explosion.getX() + image.x, explosion.getY() + image.y,
                    explosion.getZ() + image.z, explosion.getPower(), blocks,
                    new Vec3(explosion.getKnockbackX(), explosion.getKnockbackY(), explosion.getKnockbackZ()),
                    explosion.getBlockInteraction(), explosion.getSmallExplosionParticles(),
                    explosion.getLargeExplosionParticles(), explosion.getExplosionSound());
        }
        if (packet instanceof ClientboundLevelEventPacket event) {
            if (event.isGlobalEvent()) return packet;
            BlockPos pos = event.getPos();
            Vec3 image = image(viewer, level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            return image == null ? packet : new ClientboundLevelEventPacket(event.getType(),
                    pos.offset(BlockPos.containing(image)), event.getData(), false);
        }
        return null;
    }

    private static Vec3 image(ServerPlayer viewer, ServerLevel level, double x, double y, double z) {
        if (!placedInSpace(level, x, z)) return null;
        Vec3 image = imageFor(viewer, x, y, z);
        return image == null || image.equals(Vec3.ZERO) ? null : image;
    }

    private static boolean inPlot(ServerLevel level, BlockPos pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(pos);
    }

    public static <T> T anyGrid(Supplier<T> action) {
        return unfiltered(action);
    }

    private static <T> T unfiltered(Supplier<T> action) {
        SpaceGridAddress previous = CONTEXT.get();
        if (previous == null) return action.get();
        CONTEXT.remove();
        try {
            return action.get();
        } finally {
            CONTEXT.set(previous);
        }
    }

    @SuppressWarnings("unchecked")
    private static <B extends FriendlyByteBuf, P> P shifted(ServerLevel level, Packet<?> original,
                                                            StreamCodec<? super B, ? extends P> codec, B buffer,
                                                            Vec3 offset) {
        try {
            int at = buffer.readerIndex();
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null && container.inBounds(
                    (int) Math.floor(buffer.getDouble(at) / 16.0), (int) Math.floor(buffer.getDouble(at + 16) / 16.0))) {
                return (P) original;
            }
            buffer.setDouble(at, buffer.getDouble(at) + offset.x);
            buffer.setDouble(at + 8, buffer.getDouble(at + 8) + offset.y);
            buffer.setDouble(at + 16, buffer.getDouble(at + 16) + offset.z);
            buffer.readerIndex(0);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    public static boolean inPlot(Entity entity) {
        SubLevelContainer container = SubLevelContainer.getContainer(entity.level());
        return container != null && container.inBounds(entity.chunkPosition());
    }

    public static SpaceGridAddress shipGrid(ServerLevel level, SubLevel ship) {
        if (ship instanceof ServerSubLevel serverShip) {
            SpaceGridFrameData.Frame frame = frameData(serverShip);
            if (frame != null) return frame.projectionGrid();
        }
        SpaceGridAddress grid = SpaceGridDirectory.get(level).shipAddress(ship.getUniqueId());
        return grid == null ? SpaceGridAddress.ORIGIN : grid;
    }

    public static SpaceGridAddress simulationGrid(ServerLevel level, ServerSubLevel ship) {
        SpaceGridDirectory directory = SpaceGridDirectory.get(level);
        SpaceGridAddress owner = directory.frameOwnerAddress(ship.getUniqueId());
        if (owner != null) return owner;
        return shipGrid(level, ship);
    }

    public static SpaceGridAddress plotGrid(ServerLevel level, BlockPos pos) {
        SubLevel ship = Sable.HELPER.getContaining(level, pos);
        if (ship == null) return null;
        return ship instanceof ServerSubLevel serverShip ? simulationGrid(level, serverShip) : shipGrid(level, ship);
    }

    public static Iterable<SubLevel> sameGridShips(ServerLevel level, Iterable<SubLevel> ships) {
        SpaceGridAddress viewer = context();
        if (viewer == null) return ships;
        return Iterables.filter(ships, ship -> ship instanceof ServerSubLevel serverShip
                ? viewer.equals(simulationGrid(level, serverShip))
                : viewer.equals(shipGrid(level, ship)));
    }

    public static void wrapAcrossEdge(Entity entity) {
        if (entity instanceof Player || entity.isPassenger()) return;
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        if (inPlot(entity)) return;
        SubLevel attached = SpaceShipIntegration.containing(entity);
        if (attached != null) {
            if (Math.floorMod(entity.tickCount, ENTITY_SHIP_CHECK_INTERVAL) == 0
                    && attached instanceof ServerSubLevel ship && sharesCoordinates(entity, ship)) {
                SpaceGridAddress frame = shipGrid((ServerLevel) entity.level(), ship);
                rebaseEntityFrame(entity, frame, SpaceEntityState.position(entity));
                set(entity, simulationGrid((ServerLevel) entity.level(), ship));
            }
            return;
        }

        if (Math.floorMod(entity.tickCount, ENTITY_SHIP_CHECK_INTERVAL) != 0) return;
        SpaceGridAddress positionGrid = frameOf(entity);
        SpacePosition logicalPosition = SpaceEntityState.position(entity);
        ServerSubLevel nearbyShip = SpaceShipIntegration.findLiveShip(entity,
                SpaceConstants.SHIP_GRID_ATTACH_RANGE);
        if (nearbyShip != null) {
            SpaceGridAddress target = shipGrid((ServerLevel) nearbyShip.getLevel(), nearbyShip);
            rebaseWithPassengers(entity, target, logicalPosition);
            set(entity, simulationGrid((ServerLevel) nearbyShip.getLevel(), nearbyShip));
            return;
        }
        ServerPlayer nearbyPlayer = nearestPlayer(entity, logicalPosition);
        if (nearbyPlayer != null) {
            SpaceGridAddress target = playerAnchorGrid(nearbyPlayer);
            rebaseWithPassengers(entity, target, logicalPosition);
            set(entity, target);
            return;
        }
        if (nearGridBoundary(logicalPosition)) {
            SpacePosition entityAnchor = nearbyEntityAnchor(entity, logicalPosition);
            if (!entityAnchor.equals(logicalPosition)) {
                SpaceGridAddress target = entityAnchor.grid();
                rebaseWithPassengers(entity, target, logicalPosition);
                set(entity, target);
                return;
            }
        }
        if (isLocal(x) && isLocal(y) && isLocal(z)) {
            if (!SpaceGrids.of(entity).equals(positionGrid)) set(entity, positionGrid);
            return;
        }

        SpacePosition position = SpacePosition.normalize(positionGrid, x, y, z);
        rebaseWithPassengers(entity, position.grid(), position);
        set(entity, position.grid());
    }

    private static void rebaseWithPassengers(Entity entity, SpaceGridAddress target, SpacePosition logicalPosition) {
        if (!target.equals(frameOf(entity))) {
            for (Entity passenger : entity.getIndirectPassengers()) {
                if (passenger instanceof ServerPlayer player) {
                    rebasePlayer(player, target);
                    continue;
                }
                rebaseEntityFrame(passenger, target, SpaceEntityState.position(passenger));
                set(passenger, target);
            }
        }
        rebaseEntityFrame(entity, target, logicalPosition);
    }

    private static ServerPlayer nearestPlayer(Entity entity, SpacePosition position) {
        ServerPlayer nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : ((ServerLevel) entity.level()).players()) {
            SpaceVec3 delta = position.deltaWithin(SpaceProjection.observerPosition(player),
                    SpaceConstants.SHIP_GRID_ATTACH_RANGE).orElse(null);
            if (delta == null) continue;
            double distance = delta.x() * delta.x() + delta.y() * delta.y() + delta.z() * delta.z();
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static SpacePosition nearbyEntityAnchor(Entity entity, SpacePosition position) {
        double range = SpaceConstants.SHIP_GRID_ATTACH_RANGE;
        List<Entity> nearby = unfiltered(() -> entity.level().getEntities(entity,
                entity.getBoundingBox().inflate(range), candidate -> !(candidate instanceof Player)
                        && !candidate.isPassenger() && !inPlot(candidate)));
        List<SpacePosition> anchors = new ArrayList<>();
        anchors.add(position);
        for (Entity candidate : nearby) {
            SpacePosition candidatePosition = SpaceEntityState.position(candidate);
            if (position.deltaWithin(candidatePosition, range).isPresent()) anchors.add(candidatePosition);
        }
        return SpacePosition.average(anchors, range).orElse(position);
    }

    private static boolean nearGridBoundary(SpacePosition position) {
        double range = SpaceConstants.SHIP_GRID_ATTACH_RANGE;
        return nearGridBoundary(position.x(), range) || nearGridBoundary(position.y(), range)
                || nearGridBoundary(position.z(), range);
    }

    private static boolean nearGridBoundary(double local, double range) {
        return local + SpacePosition.GRID_HALF_SIZE < range
                || SpacePosition.GRID_HALF_SIZE - local <= range;
    }

    private static void rebaseEntityFrame(Entity entity, SpaceGridAddress target, SpacePosition logicalPosition) {
        if (target.equals(frameOf(entity))) return;
        SpacePosition origin = new SpacePosition(target, 0.0, 0.0, 0.0);
        SpaceVec3 projected = origin.deltaWithin(logicalPosition, SpacePosition.GRID_SIZE).orElse(null);
        if (projected == null) return;
        double dx = projected.x() - entity.getX();
        double dy = projected.y() - entity.getY();
        double dz = projected.z() - entity.getZ();
        entity.setPos(projected.x(), projected.y(), projected.z());
        entity.xo += dx;
        entity.yo += dy;
        entity.zo += dz;
        entity.xOld += dx;
        entity.yOld += dy;
        entity.zOld += dz;
        SpaceEntityState.setFrame(entity, target);
    }

    private static boolean sharesCoordinates(Entity entity, ServerSubLevel ship) {
        var pose = ship.logicalPose().position();
        return Math.abs(entity.getX() - pose.x) < SpacePosition.GRID_HALF_SIZE
                && Math.abs(entity.getY() - pose.y) < SpacePosition.GRID_HALF_SIZE
                && Math.abs(entity.getZ() - pose.z) < SpacePosition.GRID_HALF_SIZE;
    }

    static void carryEntities(ServerLevel level, AABB area, SpaceGridAddress from, SpaceGridAddress to, Vec3 jump) {
        List<Entity> entities = unfiltered(() -> level.getEntities((Entity) null, area,
                entity -> !(entity instanceof Player) && !entity.isPassenger()));
        for (Entity entity : entities) {
            if (inPlot(entity) || !from.equals(frameOf(entity))) continue;
            carry(entity, to, jump);
            for (Entity passenger : entity.getIndirectPassengers()) {
                if (!(passenger instanceof Player)) carry(passenger, to, jump);
            }
        }
        for (ServerPlayer player : level.players()) {
            if (player.isPassenger() || !area.contains(player.position()) || inPlot(player)
                    || !from.equals(frameOf(player))) {
                continue;
            }
            rebasePlayer(player, to);
            moveToFrame(player, player.getX() + jump.x, player.getY() + jump.y, player.getZ() + jump.z);
        }
    }

    private static void carry(Entity entity, SpaceGridAddress frame, Vec3 jump) {
        entity.setPos(entity.getX() + jump.x, entity.getY() + jump.y, entity.getZ() + jump.z);
        entity.xo += jump.x;
        entity.yo += jump.y;
        entity.zo += jump.z;
        entity.xOld += jump.x;
        entity.yOld += jump.y;
        entity.zOld += jump.z;
        SpaceEntityState.setFrame(entity, frame);
    }

    public static void followVehicle(Entity vehicle, Entity passenger) {
        if (passenger instanceof ServerPlayer) return;
        SpaceEntityState.setFrame(passenger, frameOf(vehicle));
        SpaceGridAddress grid = of(vehicle);
        if (!grid.equals(of(passenger))) set(passenger, grid);
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!isMainSpace(event.getLevel())) return;
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer || event.loadedFromDisk()) {
            Holder holder = (Holder) entity;
            holder.astronautics$setGrid(SpaceEntityState.owner(entity).orElse(null));
            holder.astronautics$setPositionGrid(SpaceEntityState.frame(entity));
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        ((Holder) entity).astronautics$setPositionGrid(null);
        SpaceEntityState.setFrame(entity, spawnFrame(level, entity));
        set(entity, spawnGrid(level, entity));
        ServerSubLevel nearbyShip = SpaceShipIntegration.findLiveShip(entity,
                SpaceConstants.SHIP_GRID_ATTACH_RANGE);
        if (nearbyShip != null) set(entity,
                simulationGrid((ServerLevel) nearbyShip.getLevel(), nearbyShip));
    }

    private static SpaceGridAddress spawnFrame(ServerLevel level, Entity entity) {
        SubLevel plot = Sable.HELPER.getContaining(level, entity.blockPosition());
        if (plot != null) return shipGrid(level, plot);

        Entity source = entity instanceof TraceableEntity traceable ? traceable.getOwner() : null;
        if (source == null && entity instanceof OwnableEntity ownable) source = ownable.getOwner();
        if (source != null && source != entity && source.level() == level) return frameOf(source);

        Entity vehicle = entity.getVehicle();
        if (vehicle != null && vehicle.level() == level) return frameOf(vehicle);

        SpaceGridAddress frame = FRAME.get();
        if (frame != null) return frame;

        SpaceGridAddress context = context();
        return unfiltered(() -> {
            BoundingBox3d around = new BoundingBox3d(
                    entity.getBoundingBox().inflate(SpaceConstants.SHIP_GRID_ATTACH_RANGE));
            SubLevel nearby = null;
            for (SubLevel ship : Sable.HELPER.getAllIntersecting(level, around)) {
                if (context == null || ship instanceof ServerSubLevel serverShip
                        && context.equals(simulationGrid(level, serverShip))) {
                    return shipGrid(level, ship);
                }
                if (nearby == null) nearby = ship;
            }
            if (nearby != null) return shipGrid(level, nearby);

            Player player = level.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(),
                    SPAWN_PLAYER_RANGE, null);
            if (player != null) return frameOf(player);
            return context != null ? context : SpaceGridAddress.ORIGIN;
        });
    }

    private static SpaceGridAddress spawnGrid(ServerLevel level, Entity entity) {
        SpaceGridAddress plot = plotGrid(level, entity.blockPosition());
        if (plot != null) return plot;

        Entity source = entity instanceof TraceableEntity traceable ? traceable.getOwner() : null;
        if (source == null && entity instanceof OwnableEntity ownable) source = ownable.getOwner();
        if (source != null && source != entity && source.level() == level) return of(source);

        SpaceGridAddress context = context();
        if (context != null) return context;

        Entity vehicle = entity.getVehicle();
        if (vehicle != null) return of(vehicle);

        BoundingBox3d around = new BoundingBox3d(
                entity.getBoundingBox().inflate(SpaceConstants.SHIP_GRID_ATTACH_RANGE));
        var ships = Sable.HELPER.getAllIntersecting(level, around).iterator();
        if (ships.hasNext()) {
            SubLevel ship = ships.next();
            return ship instanceof ServerSubLevel serverShip ? simulationGrid(level, serverShip) : shipGrid(level, ship);
        }

        Player player = level.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), SPAWN_PLAYER_RANGE, null);
        return player == null ? SpaceGridAddress.ORIGIN : of(player);
    }

    private static void sendProjectionShift(ServerPlayer player, SpaceGridAddress previous,
                                             SpaceGridAddress current) {
        BigInteger x = previous.x().subtract(current.x());
        BigInteger y = previous.y().subtract(current.y());
        BigInteger z = previous.z().subtract(current.z());
        if (x.abs().compareTo(BigInteger.ONE) > 0 || y.abs().compareTo(BigInteger.ONE) > 0
                || z.abs().compareTo(BigInteger.ONE) > 0) return;
        long revision = AstronauticsNetworkSync.rebaseSpaceGrid(player, current);
        player.connection.send(new ClientboundCustomPayloadPacket(new SpaceGridPoseRebasePayload(
                revision, current,
                x.doubleValue() * SpacePosition.GRID_SIZE,
                y.doubleValue() * SpacePosition.GRID_SIZE,
                z.doubleValue() * SpacePosition.GRID_SIZE)));
    }

    private static boolean isLocal(double value) {
        return value >= -SpacePosition.GRID_HALF_SIZE && value < SpacePosition.GRID_HALF_SIZE;
    }

    public interface Holder {
        SpaceGridAddress astronautics$grid();

        void astronautics$setGrid(SpaceGridAddress grid);

        SpaceGridAddress astronautics$positionGrid();

        void astronautics$setPositionGrid(SpaceGridAddress grid);
    }

}
