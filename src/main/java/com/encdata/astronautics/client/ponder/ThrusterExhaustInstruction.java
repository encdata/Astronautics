package com.encdata.astronautics.client.ponder;

import com.encdata.astronautics.registry.client.AstronauticsParticles;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.minecraft.world.phys.Vec3;

final class ThrusterExhaustInstruction extends TickingInstruction {
    private static final int FLAME_INTERVAL_TICKS = 2;
    private static final int SMOKE_INTERVAL_TICKS = 4;
    private static final double JITTER_RANGE = 0.05;
    private static final double FLAME_SPEED_SCALE = 0.38;
    private static final double SMOKE_SPEED_SCALE = 0.14;
    private final Vec3 origin;
    private final Vec3 direction;
    private final ElementLink<WorldSectionElement> thrusterSection;

    ThrusterExhaustInstruction(Vec3 origin, double x, double y, double z,
                               ElementLink<WorldSectionElement> thrusterSection, int duration) {
        super(false, duration);
        this.origin = origin;
        this.direction = new Vec3(x, y, z);
        this.thrusterSection = thrusterSection;
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (remainingTicks % FLAME_INTERVAL_TICKS != 0) return;

        Vec3 jitter = new Vec3((scene.getWorld().random.nextDouble() - 0.5) * JITTER_RANGE,
                (scene.getWorld().random.nextDouble() - 0.5) * JITTER_RANGE,
                (scene.getWorld().random.nextDouble() - 0.5) * JITTER_RANGE);
        WorldSectionElement section = scene.resolve(thrusterSection);
        Vec3 sectionOffset = section == null ? Vec3.ZERO : section.getAnimatedOffset();
        Vec3 position = origin.add(sectionOffset).add(jitter);
        scene.getWorld().addParticle(AstronauticsParticles.THRUSTER_FLAME.get(), position.x, position.y, position.z,
                direction.x * FLAME_SPEED_SCALE, direction.y * FLAME_SPEED_SCALE,
                direction.z * FLAME_SPEED_SCALE);
        if (remainingTicks % SMOKE_INTERVAL_TICKS == 0) {
            scene.getWorld().addParticle(AstronauticsParticles.THRUSTER_SMOKE.get(), position.x, position.y, position.z,
                    direction.x * SMOKE_SPEED_SCALE, direction.y * SMOKE_SPEED_SCALE,
                    direction.z * SMOKE_SPEED_SCALE);
        }
    }
}
