package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGridDirectory;
import com.encdata.astronautics.space.SpaceGridLevelFactory;
import com.encdata.astronautics.space.SpaceGridShipData;
import com.encdata.astronautics.space.SpaceGridFrameData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class SableGridShipOwnerMixin {
    @Inject(
            method = "removeSubLevel(Ldev/ryanhcode/sable/sublevel/SubLevel;Ldev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void astronautics$removeGridOwner(SubLevel subLevel, SubLevelRemovalReason reason, CallbackInfo ci) {
        if (reason != SubLevelRemovalReason.REMOVED || !(subLevel instanceof ServerSubLevel ship)) return;
        if (!(ship.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(SpaceConstants.SPACE_LEVEL)) return;
        SpaceGridDirectory directory = SpaceGridDirectory.get(level);
        SpaceGridAddress address = directory.shipAddress(ship.getUniqueId());
        if (address != null) {
            ServerLevel gridLevel = SpaceGridLevelFactory.getOrCreate(level.getServer(), address);
            SpaceGridShipData.get(gridLevel).remove(ship.getUniqueId());
        }
        SpaceGridAddress frameOwner = directory.frameOwnerAddress(ship.getUniqueId());
        if (frameOwner != null) {
            ServerLevel frameLevel = SpaceGridLevelFactory.getOrCreate(level.getServer(), frameOwner);
            SpaceGridFrameData.get(frameLevel).removeShip(ship.getUniqueId());
        }
        directory.removeShip(ship.getUniqueId());
    }
}
