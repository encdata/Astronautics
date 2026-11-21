package com.encdata.astronautics.content.items;

import com.encdata.astronautics.content.misc.OxygenStorage;
import com.encdata.astronautics.registry.AstronauticsItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class OxygenTankItem extends Item {
    public OxygenTankItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return OxygenStorage.barWidth(stack, OxygenStorage.TANK_CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return OxygenStorage.BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.astronautics.oxygen_tank.oxygen",
                OxygenStorage.get(stack), OxygenStorage.TANK_CAPACITY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tank = player.getItemInHand(hand);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(AstronauticsItems.SPACE_SUIT_CHESTPLATE.get())) {
            return InteractionResultHolder.pass(tank);
        }
        if (!level.isClientSide) {
            int availableSuitCapacity = OxygenStorage.SUIT_CAPACITY - OxygenStorage.get(chest);
            int transferred = OxygenStorage.extract(
                    tank, Math.min(OxygenStorage.TANK_CAPACITY, availableSuitCapacity));
            if (transferred > 0) {
                OxygenStorage.insert(chest, transferred, OxygenStorage.SUIT_CAPACITY);
                if (OxygenStorage.get(tank) == 0) {
                    player.setItemInHand(hand, new ItemStack(AstronauticsItems.EMPTY_OXYGEN_TANK.get()));
                    tank = player.getItemInHand(hand);
                }
                player.displayClientMessage(Component.translatable("message.astronautics.tank.filled_suit"), true);
                return InteractionResultHolder.consume(tank);
            }
        }
        return InteractionResultHolder.sidedSuccess(tank, level.isClientSide);
    }
}
