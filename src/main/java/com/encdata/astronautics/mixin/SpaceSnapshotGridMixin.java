package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.SpaceGridClientRebase;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelSnapshotInterpolator.class, remap = false)
public abstract class SpaceSnapshotGridMixin {
    @Unique private static final long astronautics$PATIENCE_NANOS = 500_000_000L;

    @Shadow @Final public ObjectArrayList<SubLevelSnapshotInterpolator.Snapshot> buffer;
    @Unique private long astronautics$waitingSince;
    @Unique private boolean astronautics$waiting;

    @Inject(method = "receiveSnapshot", at = @At("HEAD"), cancellable = true, remap = false)
    private void astronautics$holdBackOtherGrid(int gameTick, Pose3dc pose, CallbackInfo callback) {
        Pose3dc previous;
        synchronized (this.buffer) {
            previous = this.buffer.isEmpty() ? null : this.buffer.getLast().pose();
        }
        if (previous == null || !SpaceGridClientRebase.inOtherGrid(previous, pose)) {
            this.astronautics$waiting = false;
            return;
        }
        long now = System.nanoTime();
        if (!this.astronautics$waiting) {
            this.astronautics$waiting = true;
            this.astronautics$waitingSince = now;
        }
        if (now - this.astronautics$waitingSince < astronautics$PATIENCE_NANOS) callback.cancel();
    }
}
