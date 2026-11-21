package com.encdata.astronautics.space;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

final class SpaceGridHostPipelineHandler implements InvocationHandler {
    private final PhysicsPipeline pipeline;

    SpaceGridHostPipelineHandler(PhysicsPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == SpaceGridHostPipeline.class) return pipeline;
        return switch (method.getName()) {
            case "prePhysicsTicks", "physicsTick", "postPhysicsTicks", "tick" -> null;
            default -> SpaceGridPhysicsPipeline.invoke(pipeline, method, args);
        };
    }
}
