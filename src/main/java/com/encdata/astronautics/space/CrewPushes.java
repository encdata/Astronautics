package com.encdata.astronautics.space;

import com.encdata.astronautics.network.GrabPayload;
import com.encdata.astronautics.network.OrientationPayload;
import com.encdata.astronautics.registry.AstronauticsForceGroups;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrewPushes {
    private static final double PLAYER_MASS = 1.0;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double MAX_VELOCITY_CHANGE_PER_TICK = 0.6;
    private static final double MAX_HOLD_DISTANCE = 6.0;

    private record Push(Vector3d point, Vector3d impulse) {}

    private record Budget(long tick, double used) {}

    private static final Map<UUID, List<Push>> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Budget> BUDGETS = new HashMap<>();

    private CrewPushes() {}

    public static void receive(ServerPlayer player, Vec3 hold, Vec3 velocityChange) {
        if (!SpaceMovement.isFloating(player) || !Double.isFinite(velocityChange.lengthSqr())) return;
        Level level = player.level();
        SubLevel ship = Sable.HELPER.getContaining(level, hold);
        if (!(ship instanceof ServerSubLevel) || ship.isRemoved() || !withinReach(player, hold)) return;

        long tick = player.server.getTickCount();
        Budget budget = BUDGETS.get(player.getUUID());
        double used = budget != null && budget.tick() == tick ? budget.used() : 0.0;
        double allowed = MAX_VELOCITY_CHANGE_PER_TICK - used;
        if (allowed <= 0.0) return;
        Vec3 change = velocityChange.length() > allowed ? velocityChange.normalize().scale(allowed) : velocityChange;
        BUDGETS.put(player.getUUID(), new Budget(tick, used + change.length()));

        Vector3d impulse = new Vector3d(change.x, change.y, change.z).mul(-PLAYER_MASS * TICKS_PER_SECOND);
        ship.logicalPose().orientation().transformInverse(impulse);
        PENDING.computeIfAbsent(ship.getUniqueId(), id -> new ArrayList<>())
                .add(new Push(new Vector3d(hold.x, hold.y, hold.z), impulse));
    }

    public static void relayGrab(ServerPlayer player, GrabPayload grab) {
        if (grab.hold() != null && (!SpaceMovement.isFloating(player) || !withinReach(player, grab.hold()))) return;
        PacketDistributor.sendToPlayersTrackingEntity(player,
                new GrabPayload(player.getId(), grab.hold(), grab.face(), grab.handle()));
    }

    public static void relayOrientation(ServerPlayer player, Quaternionf orientation) {
        if (!SpaceMovement.isFloating(player) || !orientation.isFinite() || orientation.lengthSquared() < 1.0e-6f) return;
        PacketDistributor.sendToPlayersTrackingEntity(player,
                new OrientationPayload(player.getId(), new Quaternionf(orientation).normalize()));
    }

    private static boolean withinReach(ServerPlayer player, Vec3 hold) {
        Vec3 holdInWorld = Sable.HELPER.projectOutOfSubLevel(player.level(), hold);
        return player.getEyePosition().distanceToSqr(holdInWorld) <= MAX_HOLD_DISTANCE * MAX_HOLD_DISTANCE;
    }

    public static void apply(ServerSubLevel ship) {
        List<Push> pushes = PENDING.remove(ship.getUniqueId());
        if (pushes == null) return;
        QueuedForceGroup group = ship.getOrCreateQueuedForceGroup(AstronauticsForceGroups.CREW.get());
        for (Push push : pushes) {
            group.applyAndRecordPointForce(push.point(), push.impulse());
        }
    }

    public static void removePlayer(UUID playerId) {
        BUDGETS.remove(playerId);
    }

    public static void clear() {
        PENDING.clear();
        BUDGETS.clear();
    }
}
