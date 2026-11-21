package com.encdata.astronautics.content.items;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.client.SpaceSuitArmorModel;
import com.encdata.astronautics.content.misc.OxygenStorage;
import com.encdata.astronautics.registry.AstronauticsItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.function.Consumer;

public final class SpaceSuitItem extends ArmorItem {
    private static final ResourceLocation WORN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    Astronautics.MOD_ID, "textures/entity/space_suit.png");

    public SpaceSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties.stacksTo(1));
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack,
            Entity entity, EquipmentSlot slot,
            ArmorMaterial.Layer layer, boolean innerModel) {
        return WORN_TEXTURE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (getType() == Type.CHESTPLATE) {
            tooltip.add(Component.translatable("item.astronautics.space_suit.oxygen",
                    OxygenStorage.get(stack), OxygenStorage.SUIT_CAPACITY));
        }
    }

    public static boolean hasSealedSuit(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(AstronauticsItems.SPACE_SUIT_HELMET.get())
                && entity.getItemBySlot(EquipmentSlot.CHEST).is(AstronauticsItems.SPACE_SUIT_CHESTPLATE.get())
                && entity.getItemBySlot(EquipmentSlot.LEGS).is(AstronauticsItems.SPACE_SUIT_LEGGINGS.get())
                && entity.getItemBySlot(EquipmentSlot.FEET).is(AstronauticsItems.SPACE_SUIT_BOOTS.get());
    }

    public static boolean consumeBreathableAir(LivingEntity entity) {
        if (!hasSealedSuit(entity)) return false;
        if (entity instanceof Player player
                && player.getAbilities().instabuild) {
            return true;
        }
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        return OxygenStorage.extract(chest, 1) == 1;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,
                    ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                SpaceSuitArmorModel model = SpaceSuitArmorModel.instance();
                model.showSlot(slot);
                return model;
            }
        });
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getType() == Type.CHESTPLATE;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return getType() == Type.CHESTPLATE
                ? OxygenStorage.barWidth(stack, OxygenStorage.SUIT_CAPACITY)
                : 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return OxygenStorage.BAR_COLOR;
    }
}
