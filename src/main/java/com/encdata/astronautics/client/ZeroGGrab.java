package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.mixin.HandleHoldersAccessor;
import com.encdata.astronautics.network.GrabPayload;
import com.encdata.astronautics.network.ShipPushPayload;
import com.encdata.astronautics.space.SpaceMovement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.handle.ClientHandleHandler;
import dev.simulated_team.simulated.content.blocks.handle.HandleBlock;
import dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ZeroGGrab {
    private record Grip(double reach, double radialDamping, double tangentialDamping, double maxPull, double steer,
                        double kick, double slipSpeed) {}

    private static final Grip BARE = new Grip(2.2, 0.12, 0.05, 0.03, 0.01, 0.26, 0.3);
    private static final Grip HANDLE = new Grip(3.5, 0.35, 0.15, 0.06, 0.018, 0.4, Double.POSITIVE_INFINITY);
    private static final float LOGICAL_POSE = -1f;
    private static final double ARM_REACH = 0.59;
    private static final double EDGE_CATCH = 0.4;
    private static final double BREAK_MARGIN = 0.7;
    private static final double SPRING = 0.16;
    private static final int REGRAB_COOLDOWN_TICKS = 8;
    private static final double MIN_SHIP_PUSH = 1.0e-3;
    private static final float REACH_IN_PER_TICK = 0.3f;
    private static final float REACH_OUT_PER_TICK = 0.2f;
    private static final Vector3f FIRST_PERSON_SHOULDER = new Vector3f(0.504f, -0.819f, -0.663f);
    private static final Vector3f FIRST_PERSON_FIST = new Vector3f(0.755f, -0.438f, -1.095f);
    private static final float FIRST_PERSON_MAX_SWING = 1.3f;
    private static final float FIRST_PERSON_MAX_STRETCH = 0.25f;
    private static final float FIRST_PERSON_MAX_TUCK = 0.3f;

    public static BiFunction<AbstractClientPlayer, Float, Quaternionf> standingTilt = (player, partialTick) ->
            new Quaternionf();

    private static final class Hand {
        private Vec3 hold;
        private Direction face;
        private boolean onHandle;
        private boolean holding;
        private float reach;
        private float reachO;

        private void grab(Vec3 hold, Direction face, boolean onHandle) {
            this.hold = hold;
            this.face = face;
            this.onHandle = onHandle;
            holding = true;
        }

        private void tick() {
            reachO = reach;
            reach = holding ? Math.min(1f, reach + REACH_IN_PER_TICK) : Math.max(0f, reach - REACH_OUT_PER_TICK);
        }

        private float amount(float partialTick) {
            return Mth.lerp(partialTick, reachO, reach);
        }
    }

    private static final Hand HAND = new Hand();
    private static final Map<Integer, Hand> OTHER_HANDS = new HashMap<>();
    private static AbstractClientPlayer posedInWorld;
    private static Vec3 hold;
    private static BlockPos holdBlock;
    private static Grip grip = BARE;
    private static int cooldown;

    private ZeroGGrab() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (cooldown > 0) cooldown--;

        if (player != null && SpaceMovement.isWeightless(player)
                && HoldInteractionManager.isActive(SimClickInteractions.HANDLE_HANDLER)) {
            HoldInteractionManager.stop();
        }

        if (player == null || minecraft.screen != null || !minecraft.options.keyUse.isDown()
                || !SpaceMovement.isWeightless(player)) {
            letGo();
        } else if (hold != null && player.level().getBlockState(holdBlock).isAir()) {
            letGo();
        }

        HAND.tick();
        if (player == null) {
            HAND.hold = null;
            OTHER_HANDS.clear();
            return;
        }
        OTHER_HANDS.entrySet().removeIf(entry -> {
            entry.getValue().tick();
            return player.level().getEntity(entry.getKey()) == null
                    || !entry.getValue().holding && entry.getValue().reach <= 0f;
        });
    }

    public static void showGrab(GrabPayload grab) {
        if (grab.hold() != null) {
            OTHER_HANDS.computeIfAbsent(grab.playerId(), id -> new Hand()).grab(grab.hold(), grab.face(), grab.handle());
            return;
        }
        Hand hand = OTHER_HANDS.get(grab.playerId());
        if (hand != null) hand.holding = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        if (hold == null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || !SpaceMovement.isWeightless(minecraft.player)
                    || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                    || !(minecraft.player.level().getBlockState(hit.getBlockPos()).getBlock() instanceof HandleBlock)) {
                return;
            }
            if (event.getHand() == InteractionHand.MAIN_HAND) tryGrab(minecraft.player, hit);
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    public static boolean tryGrab(LocalPlayer player, BlockHitResult hit) {
        if (hold != null || cooldown > 0 || !SpaceMovement.isWeightless(player)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        Level level = player.level();
        BlockState state = level.getBlockState(hit.getBlockPos());
        boolean handle = state.getBlock() instanceof HandleBlock;
        Grip kind = handle ? HANDLE : BARE;
        Vec3 point = handle ? barCenter(level, hit.getBlockPos(), state) : catchEdge(level, hit, state);
        if (player.getEyePosition().distanceToSqr(toWorld(level, point, LOGICAL_POSE)) > kind.reach() * kind.reach()
                || player.getDeltaMovement().subtract(velocityAt(level, point)).length() > kind.slipSpeed()) {
            return false;
        }

        hold = point;
        holdBlock = hit.getBlockPos();
        grip = kind;
        HAND.grab(point, hit.getDirection(), handle);
        PacketDistributor.sendToServer(new GrabPayload(player.getId(), point, hit.getDirection(), handle));
        player.swing(InteractionHand.MAIN_HAND);
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.4f, 0.5f));
        return true;
    }

    private static Vec3 catchEdge(Level level, BlockHitResult hit, BlockState state) {
        BlockPos pos = hit.getBlockPos();
        VoxelShape shape = state.getShape(level, pos);
        AABB bounds = (shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds()).move(pos);
        double[] point = {hit.getLocation().x, hit.getLocation().y, hit.getLocation().z};

        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == hit.getDirection().getAxis()) continue;
            int i = axis.ordinal();
            double low = bounds.min(axis);
            double high = bounds.max(axis);
            double toLow = point[i] - low;
            double toHigh = high - point[i];
            boolean lowFree = isOpen(level, pos, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE));
            boolean highFree = isOpen(level, pos, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
            if (lowFree && toLow <= EDGE_CATCH && (!highFree || toLow <= toHigh)) {
                point[i] = low;
            } else if (highFree && toHigh <= EDGE_CATCH) {
                point[i] = high;
            }
        }
        return new Vec3(point[0], point[1], point[2]);
    }

    private static Vec3 barCenter(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof HandleBlockEntity handle) {
            Vector3d center = handle.getGrabCenter();
            if (center != null) return new Vec3(center.x, center.y, center.z);
        }
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? Vec3.atCenterOf(pos) : shape.bounds().move(pos).getCenter();
    }

    private static boolean isOpen(Level level, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        return !level.getBlockState(next).isCollisionShapeFullBlock(level, next);
    }

    private static void letGo() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (hold != null && player != null) PacketDistributor.sendToServer(GrabPayload.release(player.getId()));
        hold = null;
        holdBlock = null;
        HAND.holding = false;
    }

    public static boolean hold(LocalPlayer player) {
        if (hold == null) return false;

        Level level = player.level();
        Vec3 point = toWorld(level, hold, LOGICAL_POSE);
        Vec3 pointVelocity = velocityAt(level, hold);
        Vec3 offset = shoulder(player, 1f, ZeroGOrientation.bodyRotation(player, 1f)).subtract(point);
        double distance = offset.length();
        Vec3 relative = player.getDeltaMovement().subtract(pointVelocity);
        if (distance > grip.reach() + BREAK_MARGIN || relative.length() > grip.slipSpeed()) {
            letGo();
            return false;
        }
        Vec3 away = distance > 1.0e-4 ? offset.scale(1.0 / distance) : player.getLookAngle().reverse();

        if (player.input.jumping) {
            moveAgainstHold(player, pointVelocity.add(player.getLookAngle().scale(grip.kick())));
            letGo();
            cooldown = REGRAB_COOLDOWN_TICKS;
            return true;
        }

        double radial = relative.dot(away);
        Vec3 tangential = relative.subtract(away.scale(radial));
        double pull = Math.min(Math.max(0.0, distance - ARM_REACH) * SPRING, grip.maxPull());
        Vec3 settled = away.scale(radial * (1.0 - grip.radialDamping()) - pull)
                .add(tangential.scale(1.0 - grip.tangentialDamping()))
                .add(steer(player));
        moveAgainstHold(player, pointVelocity.add(settled));
        return true;
    }

    private static void moveAgainstHold(LocalPlayer player, Vec3 velocity) {
        Vec3 change = velocity.subtract(player.getDeltaMovement());
        player.setDeltaMovement(velocity);
        if (change.lengthSqr() > MIN_SHIP_PUSH * MIN_SHIP_PUSH
                && Sable.HELPER.getContaining(player.level(), hold) != null) {
            PacketDistributor.sendToServer(new ShipPushPayload(hold, change));
        }
    }

    private static Vec3 steer(LocalPlayer player) {
        Vector3f push = new Vector3f(player.input.leftImpulse, 0f, player.input.forwardImpulse);
        if (push.lengthSquared() < 1.0e-4f) return Vec3.ZERO;
        ZeroGOrientation.viewFrame(player).transform(push.normalize()).mul((float) grip.steer());
        return new Vec3(push.x, push.y, push.z);
    }

    public static void poseArm(AbstractClientPlayer player, ModelPart arm, float partialTick) {
        Hand hand = player == Minecraft.getInstance().player ? HAND : OTHER_HANDS.get(player.getId());
        if (hand == null || hand.hold == null) return;
        float amount = hand.amount(partialTick);
        if (amount <= 0f) return;

        Level level = player.level();
        Quaternionf body = ZeroGOrientation.bodyRotation(player, partialTick);
        Vec3 shoulder = shoulder(player, partialTick, body);
        Vec3 point = toWorld(level, hand.hold, partialTick);
        Vec3 reachDirection = point.subtract(shoulder);
        if (!hand.onHandle && reachDirection.length() < ARM_REACH) {
            Vec3 normal = faceNormal(level, hand.hold, hand.face, partialTick);
            double height = shoulder.subtract(point).dot(normal);
            if (height > 0.0 && height < ARM_REACH) {
                Vec3 along = reachDirection.add(normal.scale(height));
                Vec3 slide = along.lengthSqr() > 1.0e-8 ? along.normalize() : perpendicular(normal);
                double drop = height / ARM_REACH;
                reachDirection = slide.scale(Math.sqrt(1.0 - drop * drop)).subtract(normal.scale(drop));
            }
        }

        aimArm(arm, body, reachDirection, ease(amount));
    }

    public static void markWorldPose(AbstractClientPlayer player) {
        posedInWorld = player;
    }

    public static void poseHandleArms(LivingEntity entity, HumanoidModel<?> model, float partialTick) {
        boolean posed = entity == posedInWorld;
        posedInWorld = null;
        if (!posed || entity != Minecraft.getInstance().player || SpaceMovement.isFloating(entity)) return;
        AbstractClientPlayer player = (AbstractClientPlayer) entity;
        Vec3 bar = heldHandleBar(player.level(), partialTick);
        if (bar == null) return;

        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Quaternionf body = standingTilt.apply(player, partialTick)
                .mul(Axis.YP.rotationDegrees(180f - bodyYaw));
        Vec3 feet = player.getPosition(partialTick);
        aimArm(model.rightArm, body, bar.subtract(feet.add(joint(HumanoidArm.RIGHT, body, 0f))), 1f);
        aimArm(model.leftArm, body, bar.subtract(feet.add(joint(HumanoidArm.LEFT, body, 0f))), 1f);
    }

    public static boolean holdingSimulatedHandle() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && HandleHoldersAccessor.astronautics$holdingPlayers().contains(player.getUUID())) return true;
        ClientHandleHandler handler = SimClickInteractions.HANDLE_HANDLER;
        return handler != null && handler.isActive();
    }

    private static Vec3 heldHandleBar(Level level, float partialTick) {
        if (!holdingSimulatedHandle()) return null;
        ClientHandleHandler handler = SimClickInteractions.HANDLE_HANDLER;
        BlockPos pos = handler == null ? null : handler.getInteractionPos();
        if (pos == null && Minecraft.getInstance().hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getBlockPos();
        }
        if (pos == null) return null;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof HandleBlock ? toWorld(level, barCenter(level, pos, state), partialTick) : null;
    }

    private static void aimArm(ModelPart arm, Quaternionf body, Vec3 direction, float blend) {
        Vector3f local = body.conjugate(new Quaternionf()).transform(
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
        local.set(-local.x, -local.y, local.z);
        if (local.lengthSquared() < 1.0e-6f) return;
        local.normalize();

        float xRot = (float) Math.asin(Mth.clamp(local.z, -1f, 1f));
        float zRot = (float) Mth.atan2(-local.x, local.y);
        arm.xRot = Mth.lerp(blend, arm.xRot, xRot);
        arm.yRot = Mth.lerp(blend, arm.yRot, 0f);
        arm.zRot = Mth.lerp(blend, arm.zRot, zRot);
    }

    public static void poseFirstPersonArm(PoseStack poses, HumanoidArm side, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        Vec3 point = heldHandleBar(player.level(), partialTick);
        float amount = 1f;
        if (point == null) {
            if (HAND.hold == null) return;
            amount = HAND.amount(partialTick);
            if (amount <= 0f) return;
            point = toWorld(player.level(), HAND.hold, partialTick);
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 toHold = point.subtract(camera.getPosition());
        Vector3f target = camera.rotation().conjugate(new Quaternionf())
                .transform(new Vector3f((float) toHold.x, (float) toHold.y, (float) toHold.z));

        float mirror = side == HumanoidArm.RIGHT ? 1f : -1f;
        Vector3f shoulder = new Vector3f(FIRST_PERSON_SHOULDER).mul(mirror, 1f, 1f);
        Vector3f rest = new Vector3f(FIRST_PERSON_FIST).mul(mirror, 1f, 1f).sub(shoulder);
        float armLength = rest.length();
        Vector3f reachLine = target.sub(shoulder);
        float holdDistance = reachLine.length();
        if (holdDistance < 1.0e-4f) return;
        reachLine.div(holdDistance);
        rest.div(armLength);

        Quaternionf swing = new Quaternionf().rotationTo(rest, reachLine);
        float angle = swing.angle();
        if (angle > FIRST_PERSON_MAX_SWING) swing = new Quaternionf().slerp(swing, FIRST_PERSON_MAX_SWING / angle);

        float blend = ease(amount);
        float slide = Mth.clamp(holdDistance - armLength, -FIRST_PERSON_MAX_TUCK, FIRST_PERSON_MAX_STRETCH) * blend;
        poses.translate(shoulder.x + reachLine.x * slide, shoulder.y + reachLine.y * slide,
                shoulder.z + reachLine.z * slide);
        poses.mulPose(new Quaternionf().slerp(swing, blend));
        poses.translate(-shoulder.x, -shoulder.y, -shoulder.z);
    }

    private static float ease(float amount) {
        return amount * amount * (3f - 2f * amount);
    }

    private static Vec3 shoulder(AbstractClientPlayer player, float partialTick, Quaternionf body) {
        Vec3 joint = joint(player.getMainArm(), body, ZeroGOrientation.BODY_CENTER);
        return player.getPosition(partialTick).add(joint.x, joint.y + player.getBbHeight() * 0.5f, joint.z);
    }

    private static Vec3 joint(HumanoidArm side, Quaternionf body, float lift) {
        float x = side == HumanoidArm.RIGHT ? -5f : 5f;
        Vector3f joint = new Vector3f(x / 16f, 2f / 16f - 1.501f, 0f).mul(0.9375f);
        joint.set(-joint.x, -joint.y - lift, joint.z);
        body.transform(joint);
        return new Vec3(joint.x, joint.y, joint.z);
    }

    private static Vec3 toWorld(Level level, Vec3 hold, float partialTick) {
        Pose3dc pose = pose(level, hold, partialTick);
        if (pose == null) return hold;
        Vector3d world = pose.transformPosition(new Vector3d(hold.x, hold.y, hold.z), new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    private static Vec3 faceNormal(Level level, Vec3 hold, Direction face, float partialTick) {
        Vector3d normal = new Vector3d(face.getStepX(), face.getStepY(), face.getStepZ());
        Pose3dc pose = pose(level, hold, partialTick);
        if (pose != null) pose.orientation().transform(normal);
        return new Vec3(normal.x, normal.y, normal.z);
    }

    private static Pose3dc pose(Level level, Vec3 hold, float partialTick) {
        SubLevel ship = Sable.HELPER.getContaining(level, hold);
        if (ship == null) return null;
        return partialTick >= 0f && ship instanceof ClientSubLevel client
                ? client.renderPose(partialTick)
                : ship.logicalPose();
    }

    private static Vec3 velocityAt(Level level, Vec3 hold) {
        SubLevel ship = Sable.HELPER.getContaining(level, hold);
        if (ship == null) return Vec3.ZERO;
        Vector3d before = ship.lastPose().transformPosition(new Vector3d(hold.x, hold.y, hold.z), new Vector3d());
        return toWorld(level, hold, LOGICAL_POSE).subtract(before.x, before.y, before.z);
    }

    private static Vec3 perpendicular(Vec3 normal) {
        Vec3 axis = Math.abs(normal.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        return axis.subtract(normal.scale(axis.dot(normal))).normalize();
    }
}
