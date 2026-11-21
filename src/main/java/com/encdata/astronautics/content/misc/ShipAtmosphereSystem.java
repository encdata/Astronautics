package com.encdata.astronautics.content.misc;

import com.encdata.astronautics.content.blocks.atmospheric_control.AtmosphericControlUnitBlockEntity;
import com.encdata.astronautics.content.items.SpaceSuitItem;
import com.encdata.astronautics.registry.AstronauticsDamageSources;
import com.encdata.astronautics.space.SpaceGrids;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceShipIntegration;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ShipAtmosphereSystem {
    private static final int DISTRIBUTOR_TRANSFER_PER_TICK = 1;
    private static final int DISTRIBUTOR_TRANSFER_INTERVAL = 20;
    private static final double PLAYER_CONSUMPTION = 0.5;
    private static final Map<UUID, ShipAtmosphereScanner.ShipAtmosphere> ATMOSPHERES = new HashMap<>();
    private static final Map<UUID, Map<Long, ShipAtmosphereScanner.UnitRef>> UNITS = new HashMap<>();
    private static final Set<UUID> DIRTY_SHIPS = new HashSet<>();
    private static final Map<UUID, Docked> DOCKED = new HashMap<>();

    private record Link(ShipAtmosphereScanner.Compartment room, UUID otherShip, long otherPort) {}

    private record Docked(ServerSubLevel ship, Set<Long> ports, ShipAtmosphereScanner.ShipAtmosphere atmosphere) {}

    private ShipAtmosphereSystem() {}

    public static void clear() {
        ATMOSPHERES.clear();
        UNITS.clear();
        DIRTY_SHIPS.clear();
        DOCKED.clear();
    }

    public static void register(AtmosphericControlUnitBlockEntity unit, ServerSubLevel subLevel) {
        Level level = unit.getLevel();
        if (level == null || level.isClientSide || subLevel.isRemoved()) return;
        UUID id = subLevel.getUniqueId();
        Map<Long, ShipAtmosphereScanner.UnitRef> units = UNITS.computeIfAbsent(id, ignored -> new HashMap<>());
        if (units.put(unit.getBlockPos().asLong(), new ShipAtmosphereScanner.UnitRef(unit, subLevel)) == null) {
            DIRTY_SHIPS.add(id);
        }
    }

    public static void requestRescan(AtmosphericControlUnitBlockEntity unit) {
        Level level = unit.getLevel();
        if (level == null || level.isClientSide) return;
        var subLevel = Sable.HELPER.getContaining(level, unit.getBlockPos());
        if (subLevel instanceof ServerSubLevel serverSubLevel) DIRTY_SHIPS.add(serverSubLevel.getUniqueId());
    }

    public static void invalidate(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        var subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel instanceof ServerSubLevel serverSubLevel) DIRTY_SHIPS.add(serverSubLevel.getUniqueId());
    }

    public static void tickAll(long gameTime) {
        removeInactiveUnits();
        updateAtmospheres(gameTime);
        ATMOSPHERES.keySet().removeIf(id -> !UNITS.containsKey(id));
        shareThroughDocks();
        DIRTY_SHIPS.removeIf(id -> !UNITS.containsKey(id) && !DOCKED.containsKey(id));
    }

    private static void shareThroughDocks() {
        List<Link> links = new ArrayList<>();
        Map<UUID, ServerSubLevel> dockedShips = new HashMap<>();
        Map<UUID, Set<Long>> dockedPorts = new HashMap<>();
        for (var entry : ATMOSPHERES.entrySet()) {
            ServerSubLevel ship = UNITS.get(entry.getKey()).values().iterator().next().subLevel();
            for (ShipAtmosphereScanner.Compartment room : entry.getValue().rooms()) {
                for (long port : room.ports) {
                    if (!(ship.getLevel().getBlockEntity(BlockPos.of(port)) instanceof DockingConnectorBlockEntity connector)
                            || !connector.isLocked()) {
                        continue;
                    }
                    DockingConnectorBlockEntity other = connector.getOtherConnector();
                    if (other == null || !(Sable.HELPER.getContaining(ship.getLevel(), other.getBlockPos())
                            instanceof ServerSubLevel otherShip) || otherShip == ship || otherShip.isRemoved()) {
                        continue;
                    }
                    long otherPort = other.getBlockPos().asLong();
                    links.add(new Link(room, otherShip.getUniqueId(), otherPort));
                    if (!UNITS.containsKey(otherShip.getUniqueId())) {
                        dockedShips.put(otherShip.getUniqueId(), otherShip);
                        dockedPorts.computeIfAbsent(otherShip.getUniqueId(), id -> new HashSet<>()).add(otherPort);
                    }
                }
            }
        }

        DOCKED.keySet().retainAll(dockedShips.keySet());
        for (var entry : dockedShips.entrySet()) {
            UUID id = entry.getKey();
            Set<Long> ports = dockedPorts.get(id);
            Docked previous = DOCKED.get(id);
            if (DIRTY_SHIPS.remove(id) || previous == null || !previous.ports().equals(ports)) {
                ShipAtmosphereScanner.ShipAtmosphere atmosphere = ShipAtmosphereScanner.scanFromPorts(
                        entry.getValue(), ports, previous == null ? null : previous.atmosphere());
                DOCKED.put(id, new Docked(entry.getValue(), ports, atmosphere));
            }
        }

        Map<ShipAtmosphereScanner.Compartment, ShipAtmosphereScanner.Compartment> groups = new HashMap<>();
        for (Link link : links) {
            ShipAtmosphereScanner.ShipAtmosphere other = atmosphereOf(link.otherShip());
            ShipAtmosphereScanner.Compartment otherRoom = other == null ? null : other.roomWithPort(link.otherPort());
            if (otherRoom != null && otherRoom != link.room()) union(groups, link.room(), otherRoom);
        }
        Map<ShipAtmosphereScanner.Compartment, List<ShipAtmosphereScanner.Compartment>> members = new HashMap<>();
        for (ShipAtmosphereScanner.Compartment room : new ArrayList<>(groups.keySet())) {
            members.computeIfAbsent(root(groups, room), key -> new ArrayList<>()).add(room);
        }
        for (List<ShipAtmosphereScanner.Compartment> group : members.values()) equalize(group);
    }

    private static void union(Map<ShipAtmosphereScanner.Compartment, ShipAtmosphereScanner.Compartment> groups,
                              ShipAtmosphereScanner.Compartment a, ShipAtmosphereScanner.Compartment b) {
        groups.put(root(groups, a), root(groups, b));
    }

    private static ShipAtmosphereScanner.Compartment root(
            Map<ShipAtmosphereScanner.Compartment, ShipAtmosphereScanner.Compartment> groups,
            ShipAtmosphereScanner.Compartment room) {
        ShipAtmosphereScanner.Compartment parent = groups.putIfAbsent(room, room);
        while (parent != null && parent != room) {
            room = parent;
            parent = groups.get(room);
        }
        return room;
    }

    private static void equalize(List<ShipAtmosphereScanner.Compartment> group) {
        boolean sealed = true;
        double oxygen = 0.0;
        double capacity = 0.0;
        for (ShipAtmosphereScanner.Compartment room : group) {
            sealed &= room.sealed;
            oxygen += room.oxygen;
            capacity += room.capacity();
        }
        for (ShipAtmosphereScanner.Compartment room : group) {
            room.oxygen = sealed && capacity > 0.0 ? oxygen * room.capacity() / capacity : 0.0;
        }
    }

    private static ShipAtmosphereScanner.ShipAtmosphere atmosphereOf(UUID ship) {
        ShipAtmosphereScanner.ShipAtmosphere atmosphere = ATMOSPHERES.get(ship);
        if (atmosphere != null) return atmosphere;
        Docked docked = DOCKED.get(ship);
        return docked == null ? null : docked.atmosphere();
    }

    private static void removeInactiveUnits() {
        var ships = UNITS.entrySet().iterator();
        while (ships.hasNext()) {
            Map<Long, ShipAtmosphereScanner.UnitRef> units = ships.next().getValue();
            var refs = units.values().iterator();
            while (refs.hasNext()) {
                ShipAtmosphereScanner.UnitRef ref = refs.next();
                if (ref.unit().getLevel() == null || ref.unit().isRemoved() || ref.subLevel().isRemoved()) {
                    refs.remove();
                }
            }
            if (units.isEmpty()) ships.remove();
        }
    }

    private static void updateAtmospheres(long gameTime) {
        boolean transferTick = gameTime % DISTRIBUTOR_TRANSFER_INTERVAL == 0;
        for (var entry : UNITS.entrySet()) {
            UUID id = entry.getKey();
            Map<Long, ShipAtmosphereScanner.UnitRef> refs = entry.getValue();
            ShipAtmosphereScanner.ShipAtmosphere previous = ATMOSPHERES.get(id);
            ShipAtmosphereScanner.ShipAtmosphere atmosphere = DIRTY_SHIPS.remove(id) || previous == null
                    ? ShipAtmosphereScanner.scanRooms(refs, previous)
                    : previous;
            ATMOSPHERES.put(id, atmosphere);
            for (ShipAtmosphereScanner.Compartment room : atmosphere.rooms()) {
                updateCompartmentOxygen(room, refs, transferTick);
            }
        }
    }

    private static void updateCompartmentOxygen(ShipAtmosphereScanner.Compartment room,
                                                Map<Long, ShipAtmosphereScanner.UnitRef> refs,
                                                boolean transferTick) {
        if (!room.sealed) {
            room.oxygen = 0.0;
            return;
        }
        if (!transferTick) return;

        int required = Math.max(0, (int) Math.ceil(room.capacity() - room.oxygen));
        for (long distributorPos : room.distributors) {
            if (required <= 0) break;
            ShipAtmosphereScanner.UnitRef distributor = refs.get(distributorPos);
            if (distributor == null || !distributor.unit().isPowered()) continue;

            int transferred = distributor.unit().drainOxygen(
                    Math.min(DISTRIBUTOR_TRANSFER_PER_TICK, required));
            room.oxygen += transferred;
            required -= transferred;
        }
    }

    public static void tickPlayer(Player player) {
        if (player.level().isClientSide || hasAmbientOxygen(player)) return;
        ShipAtmosphereScanner.Compartment room = roomFor(player);
        if (room == null || room.oxygen <= 0.0) {
            if (SpaceSuitItem.consumeBreathableAir(player)) return;
            if ((player.tickCount & 1) == 0) player.hurt(AstronauticsDamageSources.vacuum(player.level()), 2.0F);
            return;
        }
        room.oxygen = Math.max(0.0, room.oxygen - PLAYER_CONSUMPTION / 20.0);
    }

    public static boolean canBreathe(Player player) {
        if (hasAmbientOxygen(player)) return true;
        ShipAtmosphereScanner.Compartment room = roomFor(player);
        if (room != null && room.oxygen > 0.0) return true;
        if (!SpaceSuitItem.hasSealedSuit(player)) return false;
        return player.getAbilities().instabuild || OxygenStorage.get(
                player.getItemBySlot(EquipmentSlot.CHEST)) > 0;
    }

    public static boolean isInPressurizedRoom(Player player) {
        ShipAtmosphereScanner.Compartment room = roomFor(player);
        return room != null && room.oxygen > 0.0;
    }

    private static boolean hasAmbientOxygen(Player player) {
        Level level = player.level();
        return !SpaceConstants.isSpace(level) && SpaceCatalog.environmentForDimension(level.dimension()).oxygen();
    }

    private static ShipAtmosphereScanner.Compartment roomFor(Player player) {
        ServerSubLevel direct = SpaceShipIntegration.findLiveShip(player, 5.0);
        if (direct != null) {
            ShipAtmosphereScanner.Compartment room = roomAt(player, direct);
            if (room != null) return room;
        }
        for (var entry : UNITS.entrySet()) {
            if (direct != null && entry.getKey().equals(direct.getUniqueId())) continue;
            ShipAtmosphereScanner.Compartment room = roomAt(player, entry.getValue().values().iterator().next().subLevel());
            if (room != null) return room;
        }
        for (var entry : DOCKED.entrySet()) {
            if (direct != null && entry.getKey().equals(direct.getUniqueId())) continue;
            ShipAtmosphereScanner.Compartment room = roomAt(player, entry.getValue().ship());
            if (room != null) return room;
        }
        return null;
    }

    private static ShipAtmosphereScanner.Compartment roomAt(Player player, ServerSubLevel subLevel) {
        if (subLevel.getLevel() != player.level() || subLevel.isRemoved()) return null;
        ShipAtmosphereScanner.ShipAtmosphere atmosphere = atmosphereOf(subLevel.getUniqueId());
        if (atmosphere == null) return null;
        Vec3 offset = SpaceGrids.shipOffset(player, subLevel);
        if (offset == null) return null;
        double[] heights = {player.getBbHeight() * 0.5, 0.0, player.getEyeHeight()};
        for (double height : heights) {
            Vector3d localPosition = new Vector3d(player.getX() + offset.x, player.getY() + offset.y + height,
                    player.getZ() + offset.z);
            subLevel.logicalPose().transformPositionInverse(localPosition);
            ShipAtmosphereScanner.Compartment room = atmosphere.roomAt(
                    BlockPos.containing(localPosition.x, localPosition.y, localPosition.z));
            if (room != null) return room;
        }
        return null;
    }
}
