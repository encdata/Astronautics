package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
public abstract class SpaceGridLevelMixin {
    @WrapMethod(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;")
    private List<Entity> astronautics$queryFromEntityGrid(Entity except, AABB area, Predicate<? super Entity> predicate,
                                                          Operation<List<Entity>> original) {
        if (except == null || !SpaceGrids.isMainSpace((Level) (Object) this)) {
            return original.call(except, area, predicate);
        }
        SpaceGridAddress previous = SpaceGrids.enter(SpaceGrids.of(except));
        try {
            return original.call(except, area, predicate);
        } finally {
            SpaceGrids.exit(previous);
        }
    }

    @WrapOperation(
            method = "tickBlockEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"),
            require = 1
    )
    private void astronautics$tickInShipGrid(TickingBlockEntity ticker, Operation<Void> original) {
        Level level = (Level) (Object) this;
        SpaceGridAddress grid = SpaceGrids.isMainSpace(level)
                ? SpaceGrids.plotGrid((ServerLevel) level, ticker.getPos()) : null;
        if (grid == null) {
            original.call(ticker);
            return;
        }
        SpaceGridAddress previous = SpaceGrids.enter(grid);
        try {
            original.call(ticker);
        } finally {
            SpaceGrids.exit(previous);
        }
    }
}
