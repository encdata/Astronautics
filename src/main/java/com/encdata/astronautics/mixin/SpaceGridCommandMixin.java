package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.ExecuteCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ExecuteCommand.class)
public abstract class SpaceGridCommandMixin {
    @WrapMethod(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V")
    private void astronautics$runInSourceGrid(ExecutionCommandSource<?> source, ExecutionContext<?> context,
                                              Frame frame, Operation<Void> original) {
        Entity entity = source instanceof CommandSourceStack stack ? stack.getEntity() : null;
        if (entity == null || !SpaceGrids.isMainSpace(entity.level())) {
            original.call(source, context, frame);
            return;
        }
        SpaceGridAddress grid = SpaceGrids.frameOf(entity);
        SpaceGridAddress previous = SpaceGrids.enter(grid);
        SpaceGridAddress previousFrame = SpaceGrids.enterFrame(grid);
        try {
            original.call(source, context, frame);
        } finally {
            SpaceGrids.exitFrame(previousFrame);
            SpaceGrids.exit(previous);
        }
    }
}
