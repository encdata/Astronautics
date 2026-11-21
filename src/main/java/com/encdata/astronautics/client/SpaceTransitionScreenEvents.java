package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.SpaceConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;

import java.util.function.BooleanSupplier;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SpaceTransitionScreenEvents {
    private SpaceTransitionScreenEvents() {}

    @SubscribeEvent
    public static void register(RegisterDimensionTransitionScreenEvent event) {
        event.registerConditionalEffect(Level.OVERWORLD, SpaceConstants.SPACE_LEVEL, InvisibleReceivingScreen::new);
        event.registerConditionalEffect(SpaceConstants.SPACE_LEVEL, Level.OVERWORLD, InvisibleReceivingScreen::new);
    }

    private static final class InvisibleReceivingScreen extends ReceivingLevelScreen {
        private InvisibleReceivingScreen(BooleanSupplier levelReady, Reason reason) {
            super(levelReady, reason);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
