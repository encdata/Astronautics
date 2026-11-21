package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.registry.AstronauticsItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import java.util.IdentityHashMap;
import java.util.Map;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class SpaceSuitCosmeticHider {
    private record Visibility(boolean hat, boolean jacket, boolean leftSleeve, boolean rightSleeve,
                              boolean leftPants, boolean rightPants) {}
    private static final Map<PlayerModel<?>, Visibility> RESTORE = new IdentityHashMap<>();

    @SubscribeEvent
    public static void beforePlayer(RenderPlayerEvent.Pre event) {
        PlayerModel<?> model = event.getRenderer().getModel();
        RESTORE.put(model, new Visibility(model.hat.visible, model.jacket.visible, model.leftSleeve.visible,
                model.rightSleeve.visible, model.leftPants.visible, model.rightPants.visible));
        var player = event.getEntity();
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(AstronauticsItems.SPACE_SUIT_HELMET.get())) {
            model.hat.visible = false;
        }
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(AstronauticsItems.SPACE_SUIT_CHESTPLATE.get())) {
            model.jacket.visible = false;
            model.leftSleeve.visible = false;
            model.rightSleeve.visible = false;
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(AstronauticsItems.SPACE_SUIT_LEGGINGS.get())
                || player.getItemBySlot(EquipmentSlot.FEET).is(AstronauticsItems.SPACE_SUIT_BOOTS.get())) {
            model.leftPants.visible = false;
            model.rightPants.visible = false;
        }
    }

    @SubscribeEvent
    public static void afterPlayer(RenderPlayerEvent.Post event) {
        PlayerModel<?> model = event.getRenderer().getModel();
        Visibility old = RESTORE.remove(model);
        if (old == null) return;
        model.hat.visible = old.hat;
        model.jacket.visible = old.jacket;
        model.leftSleeve.visible = old.leftSleeve;
        model.rightSleeve.visible = old.rightSleeve;
        model.leftPants.visible = old.leftPants;
        model.rightPants.visible = old.rightPants;
    }
}
