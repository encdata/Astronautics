package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.network.SpaceGridPoseRebasePayload;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpacePosition;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.joml.Vector3dc;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT)
public final class SpaceGridClientRebase {
    private static final int LERP_STEPS = 3;

    private SpaceGridClientRebase() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ClientLevel level) || !SpaceConstants.isSpace(level)) return;
        SubLevelContainer plots = SubLevelContainer.getContainer(level);
        ChunkPos chunk = event.getEntity().chunkPosition();
        if (plots == null || !plots.inBounds(chunk)) level.onChunkLoaded(chunk);
    }

    public static void apply(SpaceGridPoseRebasePayload payload) {
        if (!SpaceGridClientState.applyRebase(payload.revision(), payload.address())) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        SubLevelContainer plots = SubLevelContainer.getContainer(level);
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (Entity entity : level.entitiesForRendering()) {
            if (plots != null && plots.inBounds(entity.chunkPosition())) continue;
            shift(entity, plots, payload.x(), payload.y(), payload.z());
            chunks.add(entity.chunkPosition().toLong());
        }
        chunks.forEach(chunk -> level.onChunkLoaded(new ChunkPos(chunk)));

        if (!(plots instanceof ClientSubLevelContainer container)) return;
        for (var subLevel : container.getAllSubLevels()) {
            if (!(subLevel instanceof ClientSubLevel clientSubLevel)) continue;
            shift(clientSubLevel.logicalPose(), payload);
            shift(clientSubLevel.lastPose(), payload);
            shift(clientSubLevel.renderPose(), payload);
            var interpolator = clientSubLevel.getInterpolator();
            shift(interpolator.getInterpolatedPose(), payload);
            synchronized (interpolator.buffer) {
                for (var snapshot : interpolator.buffer) shift(snapshot.pose(), payload);
            }
        }
    }

    public static boolean inOtherGrid(Pose3dc previous, Pose3dc pose) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !SpaceConstants.isSpace(minecraft.level)) return false;
        Vector3dc from = previous.position();
        Vector3dc to = pose.position();
        return Math.abs(to.x() - from.x()) > SpacePosition.GRID_HALF_SIZE
                || Math.abs(to.y() - from.y()) > SpacePosition.GRID_HALF_SIZE
                || Math.abs(to.z() - from.z()) > SpacePosition.GRID_HALF_SIZE;
    }

    private static void shift(Entity entity, SubLevelContainer plots, double x, double y, double z) {
        double targetX = entity.lerpTargetX() + x;
        double targetY = entity.lerpTargetY() + y;
        double targetZ = entity.lerpTargetZ() + z;
        float targetYRot = entity.lerpTargetYRot();
        float targetXRot = entity.lerpTargetXRot();

        entity.setPos(entity.getX() + x, entity.getY() + y, entity.getZ() + z);
        entity.xo += x;
        entity.yo += y;
        entity.zo += z;
        entity.xOld += x;
        entity.yOld += y;
        entity.zOld += z;

        VecDeltaCodec codec = entity.getPositionCodec();
        Vec3 base = codec.getBase();
        if (plots == null || !plots.inBounds(Mth.floor(base.x) >> 4, Mth.floor(base.z) >> 4)) {
            codec.setBase(base.add(x, y, z));
        }
        if (!entity.isControlledByLocalInstance()) {
            entity.lerpTo(targetX, targetY, targetZ, targetYRot, targetXRot, LERP_STEPS);
        }
    }

    private static void shift(Pose3dc pose, SpaceGridPoseRebasePayload offset) {
        ((Pose3d) pose).position().add(offset.x(), offset.y(), offset.z());
    }
}
