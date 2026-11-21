package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class VacuumAudio {
    private static final int FADE_TICKS = 20 * 5;
    private static int fadeTicks;

    private VacuumAudio() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            fadeTicks = 0;
            minecraft.getSoundManager().updateSourceVolume(SoundSource.MASTER,
                    minecraft.options.getSoundSourceVolume(SoundSource.MASTER));
            return;
        }
        fadeTicks = BreathabilityClientState.breathable()
                ? Math.max(0, fadeTicks - 1)
                : Math.min(FADE_TICKS, fadeTicks + 1);
        float progress = fadeTicks / (float) FADE_TICKS;
        float volume = 1.0F - progress * progress;
        float configuredVolume = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
        minecraft.getSoundManager().updateSourceVolume(SoundSource.MASTER, configuredVolume * volume);
    }
}
