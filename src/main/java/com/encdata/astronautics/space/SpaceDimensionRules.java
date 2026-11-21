package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.content.misc.ShipAtmosphereSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = Astronautics.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class SpaceDimensionRules {
    private SpaceDimensionRules() {}

    @SubscribeEvent
    public static void onSpaceLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof Level level && SpaceConstants.isSpace(level)) {
            SpaceWorldBorderRegistry.mark(level.getWorldBorder());
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!SpaceConstants.isSpace(event.getEntity().level())) {
            return;
        }
        if (!(event.getEntity() instanceof Player)
                && SpaceShipIntegration.containing(event.getEntity()) == null) {
            event.getEntity().setNoGravity(true);
        }
        event.getEntity().resetFallDistance();
    }

    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        if (livingEntity instanceof Player player) {
            SpaceFlightPhysics.tickPlayer(player);
        } else {
            SpaceFlightPhysics.tickLivingEntity(livingEntity);
        }
    }

    @SubscribeEvent
    public static void onVoidDamage(LivingIncomingDamageEvent event) {
        if (SpaceConstants.isSpace(event.getEntity().level())
                && event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (onBlockChanged(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (onBlockChanged(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getEntity().level();
        if (SpaceConstants.isSpace(level) && !SpaceShipIntegration.isInsideShip(level, event.getPos())) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            event.setCanceled(true);
        }
    }

    private static boolean onBlockChanged(Object level, BlockPos pos) {
        if (!(level instanceof Level actual)) return false;
        ShipAtmosphereSystem.invalidate(actual, pos);
        return SpaceConstants.isSpace(actual) && !SpaceShipIntegration.isInsideShip(actual, pos);
    }

}
