package com.encdata.astronautics.client.ponder;

import com.encdata.astronautics.registry.AstronauticsBlocks;
import com.encdata.astronautics.content.blocks.rcs_thruster.RcsThrusterBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class AstronauticsThrusterScenes {
    private AstronauticsThrusterScenes() {}

    public static void centerOfMassCore(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("center_of_mass_core", "Center of Mass Core");
        scene.configureBasePlate(0, 0, 3);

        Selection core = util.select().position(0, 0, 1);
        Selection power = util.select().position(0, 1, 1);
        scene.idle(10);
        scene.world().showSection(core, Direction.DOWN);
        scene.overlay().showText(80)
                .text("The Core is the ship's physics center.")
                .pointAt(util.vector().centerOf(util.grid().at(0, 0, 1)))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(power, Direction.DOWN);
        scene.effects().indicateRedstone(util.grid().at(0, 0, 1));
        scene.overlay().showText(80)
                .colored(PonderPalette.GREEN)
                .text("Power the Core to activate the ship.")
                .pointAt(util.vector().centerOf(util.grid().at(0, 0, 1)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void thruster(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("thruster", "Powering a Thruster");
        scene.configureBasePlate(0, 0, 5);

        Selection shipCore = util.select().position(0, 0, 1);
        Selection hullAndSeat = util.select().fromTo(1, 0, 1, 1, 1, 1);
        Selection fuelSystem = util.select().fromTo(2, 0, 1, 3, 0, 1);
        Selection driveSystem = util.select().fromTo(2, 1, 1, 3, 1, 1);
        Selection thruster = util.select().position(4, 0, 1);
        Selection redstoneLink = util.select().position(4, 1, 1);
        Selection redstonePower = util.select().position(0, 1, 1);
        Selection wholeShip = util.select().fromTo(0, 0, 0, 4, 1, 2);

        scene.world().setBlock(util.grid().at(4, 0, 1), AstronauticsBlocks.THRUSTER.get().defaultBlockState()
                .setValue(RcsThrusterBlock.FACING, Direction.EAST), false);

        scene.idle(10);
        scene.world().showSection(shipCore, Direction.DOWN);
        scene.world().showSection(hullAndSeat, Direction.DOWN);
        scene.overlay().showText(70)
                .text("A powered Core makes this construction an active ship.")
                .pointAt(util.vector().centerOf(util.grid().at(0, 0, 1)))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(fuelSystem, Direction.WEST);
        scene.overlay().showText(70)
                .text("The tank supplies fuel. The pump feeds the thruster.")
                .pointAt(util.vector().centerOf(util.grid().at(2, 0, 1)))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(driveSystem, Direction.DOWN);
        scene.world().showSection(redstoneLink, Direction.DOWN);
        scene.world().showSection(thruster, Direction.EAST);
        scene.overlay().showText(70)
                .text("Point the nozzle where exhaust should leave the ship.")
                .pointAt(util.vector().centerOf(util.grid().at(4, 0, 1)))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(redstonePower, Direction.DOWN);
        scene.effects().indicateRedstone(util.grid().at(4, 1, 1));
        scene.effects().indicateSuccess(util.grid().at(4, 0, 1));
        scene.overlay().showText(90)
                .colored(PonderPalette.GREEN)
                .text("Power on: exhaust fires and the ship accelerates forward.")
                .pointAt(util.vector().centerOf(util.grid().at(4, 0, 1)))
                .placeNearTarget()
                .attachKeyFrame();

        scene.idle(90);

        scene.world().hideSection(wholeShip, Direction.WEST);
        scene.idle(20);
        ElementLink<WorldSectionElement> movingShip = scene.world().showIndependentSectionImmediately(wholeShip);

        Vec3 thrustTranslation = new Vec3(-4.0, 0.0, 0.0);
        scene.world().moveSection(movingShip, thrustTranslation, 90);
        scene.addInstruction(new ThrusterExhaustInstruction(util.vector().centerOf(util.grid().at(4, 0, 1))
                .add(0.62, 0.0, 0.0), 1.0, 0.0, 0.0, movingShip, 90));
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void stabilizingThruster(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("reaction_based_thruster", "Reaction Based Thrusters");
        scene.configureBasePlate(0, 0, 5);

        Selection coreAndHull = util.select().fromTo(0, 0, 1, 1, 1, 1);
        Selection fuel = util.select().fromTo(2, 0, 1, 3, 1, 1);
        Selection stabilizer = util.select().position(4, 0, 1);
        Selection redstone = util.select().fromTo(0, 1, 1, 0, 1, 1)
                .add(util.select().position(4, 1, 1));
        scene.world().setBlock(util.grid().at(4, 0, 1), AstronauticsBlocks.STABILIZING_THRUSTER.get().defaultBlockState()
                .setValue(RcsThrusterBlock.FACING, Direction.WEST), false);

        scene.idle(10);
        scene.world().showSection(coreAndHull, Direction.DOWN);
        scene.overlay().showText(80)
                .text("Place Reaction Based Thrusters around the ship, not in only one location.")
                .pointAt(util.vector().centerOf(util.grid().at(0, 0, 1)))
                .placeNearTarget();
        scene.idle(90);
        scene.world().showSection(fuel, Direction.DOWN);
        scene.world().showSection(stabilizer, Direction.EAST);
        scene.overlay().showText(80)
                .text("They use the same fuel system and signal strength as normal thrusters.")
                .pointAt(util.vector().centerOf(util.grid().at(4, 0, 1)))
                .placeNearTarget();
        scene.idle(90);
        scene.world().showSection(redstone, Direction.DOWN);
        scene.effects().indicateRedstone(util.grid().at(4, 1, 1));
        scene.overlay().showText(100)
                .colored(PonderPalette.GREEN)
                .text("When powered, each one fires only if its normal force slows current drift or rotation.")
                .pointAt(util.vector().centerOf(util.grid().at(4, 0, 1)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);
        scene.markAsFinished();
    }
}
