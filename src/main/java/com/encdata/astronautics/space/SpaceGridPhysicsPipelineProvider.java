package com.encdata.astronautics.space;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@PhysicsPipelineProvider.LoadPriority(Integer.MAX_VALUE)
public final class SpaceGridPhysicsPipelineProvider implements PhysicsPipelineProvider {
    private static final ThreadLocal<PhysicsPipeline> CONSTRAINT_PIPELINE = new ThreadLocal<>();
    private static final ThreadLocal<Set<UUID>> UNLOADING_SHIPS = ThreadLocal.withInitial(HashSet::new);

    public static void beginShipUnload(UUID shipId) {
        UNLOADING_SHIPS.get().add(shipId);
    }

    public static void endShipUnload(UUID shipId) {
        Set<UUID> unloading = UNLOADING_SHIPS.get();
        unloading.remove(shipId);
        if (unloading.isEmpty()) UNLOADING_SHIPS.remove();
    }

    static boolean consumeShipUnload(UUID shipId) {
        Set<UUID> unloading = UNLOADING_SHIPS.get();
        boolean result = unloading.remove(shipId);
        if (unloading.isEmpty()) UNLOADING_SHIPS.remove();
        return result;
    }

    public static PhysicsPipeline constraintPipeline() {
        return CONSTRAINT_PIPELINE.get();
    }

    static Object addConstraint(PhysicsPipeline pipeline, Method method, Object[] args) throws Throwable {
        PhysicsPipeline previous = CONSTRAINT_PIPELINE.get();
        CONSTRAINT_PIPELINE.set(pipeline);
        try {
            return method.invoke(pipeline, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        } finally {
            if (previous == null) CONSTRAINT_PIPELINE.remove();
            else CONSTRAINT_PIPELINE.set(previous);
        }
    }

    private static PhysicsPipelineProvider findDelegate() {
        return ServiceLoader.load(PhysicsPipelineProvider.class).stream()
                .filter(provider -> provider.type() != SpaceGridPhysicsPipelineProvider.class)
                .max(Comparator.comparingInt(provider -> {
                    PhysicsPipelineProvider.LoadPriority priority =
                            provider.type().getAnnotation(PhysicsPipelineProvider.LoadPriority.class);
                    return priority == null ? 1000 : priority.value();
                }))
                .map(ServiceLoader.Provider::get)
                .orElseThrow(() -> new IllegalStateException("no Sable physics pipeline provider is available"));
    }

    @Override
    public PhysicsPipeline createPipeline(ServerLevel level) {
        ServerLevel pipelineLevel = level;
        if (SpaceConstants.isGridLevel(level.dimension())) {
            pipelineLevel = SpaceGridLevelFactory.currentPlotLevel();
            if (pipelineLevel == null && level.getServer() != null) {
                pipelineLevel = SpaceGridLevelFactory.spaceLevel(level.getServer());
            }
            if (pipelineLevel == null) {
                throw new IllegalStateException("space level is unavailable while creating a grid physics pipeline");
            }
        }

        PhysicsPipeline pipeline = findDelegate().createPipeline(pipelineLevel);
        if (SpaceConstants.SPACE_LEVEL.equals(level.dimension())) {
            SpaceGridLevelFactory.spaceLevelStarting(level);
            return (PhysicsPipeline) Proxy.newProxyInstance(
                    SpaceGridPhysicsPipelineProvider.class.getClassLoader(),
                    new Class<?>[]{PhysicsPipeline.class},
                    new SpaceGridPhysicsPipeline(level, pipeline));
        }
        if (SpaceConstants.isGridLevel(level.dimension())) {
            return (PhysicsPipeline) Proxy.newProxyInstance(
                    SpaceGridPhysicsPipelineProvider.class.getClassLoader(),
                    new Class<?>[]{PhysicsPipeline.class, SpaceGridHostPipeline.class},
                    new SpaceGridHostPipelineHandler(pipeline));
        }
        return pipeline;
    }
}
