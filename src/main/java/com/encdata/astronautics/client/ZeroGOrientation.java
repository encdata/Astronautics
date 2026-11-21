package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.network.OrientationPayload;
import com.encdata.astronautics.space.SpaceMovement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ZeroGOrientation {
    public static final float BODY_CENTER = 0.9f;
    private static final float MOUSE_DEGREES = 0.15f;
    private static final float ROLL_RECOVERY_PER_TICK = 0.6f;
    private static final float RESEND_DOT = 0.99999f;
    private static final float REMOTE_SMOOTHING = 0.5f;
    private static final int KEEPALIVE_TICKS = 20;

    private static final class Synced {
        private final Quaternionf target = new Quaternionf();
        private final Quaternionf current = new Quaternionf();
        private final Quaternionf previous = new Quaternionf();
    }

    private static final Map<Integer, Synced> OTHERS = new HashMap<>();
    private static final Quaternionf lastSent = new Quaternionf();
    private static boolean sent;
    private static int ticksSinceSent;
    private static final Quaternionf orientation = new Quaternionf();
    private static boolean active;
    private static float lastYaw;
    private static float lastPitch;
    private static float roll;
    private static float rollO;

    private ZeroGOrientation() {}

    public static boolean turn(LocalPlayer player, double yawInput, double pitchInput) {
        if (!SpaceMovement.isFloating(player)) return false;

        if (!active || player.getYRot() != lastYaw || player.getXRot() != lastPitch) {
            lookRotation(player.getYRot(), player.getXRot(), roll, orientation);
            active = true;
        }

        orientation.rotateY(-(float) yawInput * MOUSE_DEGREES * Mth.DEG_TO_RAD)
                .rotateX((float) pitchInput * MOUSE_DEGREES * Mth.DEG_TO_RAD)
                .normalize();

        Vector3f forward = orientation.transform(new Vector3f(0f, 0f, 1f));
        float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-forward.y, -1f, 1f)));
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        Quaternionf twist = lookRotation(yaw, pitch, 0f, new Quaternionf()).conjugate().mul(orientation);
        roll = Mth.wrapDegrees((float) Math.toDegrees(2.0 * Math.atan2(twist.z, twist.w)));
        rollO = roll;

        float continuousYaw = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot());
        player.setYRot(continuousYaw);
        player.setXRot(pitch);
        player.yRotO = continuousYaw;
        player.xRotO = pitch;
        lastYaw = continuousYaw;
        lastPitch = pitch;
        return true;
    }

    public static Quaternionf viewFrame(LocalPlayer player) {
        return active
                ? new Quaternionf(orientation)
                : lookRotation(player.getYRot(), player.getXRot(), roll, new Quaternionf());
    }

    public static float roll(float partialTick) {
        return active ? roll : Mth.lerp(partialTick, rollO, roll);
    }

    public static Quaternionf bodyRotation(AbstractClientPlayer player, float partialTick) {
        Quaternionf rotation;
        if (player == Minecraft.getInstance().player) {
            rotation = active
                    ? new Quaternionf(orientation)
                    : lookRotation(player.getViewYRot(partialTick), player.getViewXRot(partialTick),
                            roll(partialTick), new Quaternionf());
        } else {
            Synced synced = OTHERS.get(player.getId());
            rotation = synced != null
                    ? synced.previous.slerp(synced.current, partialTick, new Quaternionf())
                    : lookRotation(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot),
                            player.getViewXRot(partialTick), 0f, new Quaternionf());
        }
        return rotation.rotateX(Mth.HALF_PI).rotateY(Mth.PI);
    }

    private static Quaternionf lookRotation(float yaw, float pitch, float roll, Quaternionf dest) {
        return dest.rotationYXZ(-yaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD, roll * Mth.DEG_TO_RAD);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            active = false;
            sent = false;
            roll = rollO = 0f;
            OTHERS.clear();
            return;
        }
        OTHERS.entrySet().removeIf(entry -> {
            if (player.level().getEntity(entry.getKey()) == null) return true;
            Synced synced = entry.getValue();
            synced.previous.set(synced.current);
            synced.current.slerp(synced.target, REMOTE_SMOOTHING);
            return false;
        });
        if (SpaceMovement.isFloating(player)) {
            sendOrientation(player);
            if (active) return;
        } else {
            sent = false;
        }

        active = false;
        rollO = roll;
        roll = Math.abs(roll) < 0.05f ? 0f : roll * ROLL_RECOVERY_PER_TICK;
    }

    private static void sendOrientation(LocalPlayer player) {
        Quaternionf frame = viewFrame(player);
        ticksSinceSent++;
        if (sent && ticksSinceSent < KEEPALIVE_TICKS && Math.abs(frame.dot(lastSent)) > RESEND_DOT) return;
        lastSent.set(frame);
        sent = true;
        ticksSinceSent = 0;
        PacketDistributor.sendToServer(new OrientationPayload(player.getId(), frame));
    }

    public static void showOrientation(OrientationPayload payload) {
        Synced synced = OTHERS.get(payload.playerId());
        if (synced == null) {
            synced = new Synced();
            synced.current.set(payload.orientation());
            synced.previous.set(payload.orientation());
            OTHERS.put(payload.playerId(), synced);
        }
        synced.target.set(payload.orientation());
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getCamera().getEntity() != minecraft.player) return;
        float cameraRoll = roll((float) event.getPartialTick());
        if (cameraRoll == 0f) return;
        event.setRoll(event.getRoll() + cameraRoll);
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)
                || !player.isVisuallySwimming() || !SpaceMovement.isFloating(player)) return;
        player.yBodyRot = player.yHeadRot;
        player.yBodyRotO = player.yHeadRotO;
    }
}
