package com.encdata.astronautics.content.misc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class OxygenStorage {
    public static final int TANK_CAPACITY = 1_000;
    public static final int SUIT_CAPACITY = 4_000;
    public static final int BAR_COLOR = 0x36C7FF;
    private static final int BAR_WIDTH = 13;
    private static final String OXYGEN_COMPONENT_KEY = "AstronauticsOxygen";

    private OxygenStorage() {}

    public static int get(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getInt(OXYGEN_COMPONENT_KEY);
    }

    public static int barWidth(ItemStack stack, int capacity) {
        return Math.round(get(stack) * (float) BAR_WIDTH / capacity);
    }

    public static void set(ItemStack stack, int oxygen) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(OXYGEN_COMPONENT_KEY, Math.max(0, oxygen));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int extract(ItemStack stack, int amount) {
        int oxygen = get(stack);
        if (oxygen <= 0 || amount <= 0) return 0;
        int extracted = Math.min(oxygen, amount);
        set(stack, oxygen - extracted);
        return extracted;
    }

    public static int insert(ItemStack stack, int amount, int capacity) {
        if (amount <= 0) return 0;
        int oxygen = get(stack);
        int accepted = Math.min(amount, capacity - oxygen);
        if (accepted <= 0) return 0;
        set(stack, oxygen + accepted);
        return accepted;
    }
}
