package com.encdata.astronautics.content.blocks.oxygen_compressor;

import com.encdata.astronautics.registry.AstronauticsBlocks;
import com.encdata.astronautics.registry.AstronauticsItems;
import com.encdata.astronautics.registry.AstronauticsMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class OxygenCompressorMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT = 0;
    private static final int PLAYER_INVENTORY_START = 1;
    private final OxygenCompressorBlockEntity compressor;
    private final DataSlot progressData;

    public OxygenCompressorMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, resolve(inventory, buffer.readBlockPos()));
    }
    public OxygenCompressorMenu(int id, Inventory inventory, OxygenCompressorBlockEntity compressor) {
        super(AstronauticsMenus.OXYGEN_COMPRESSOR.get(), id);
        this.compressor = compressor;
        addSlot(new Slot(compressor, MACHINE_SLOT, 26, 38) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(AstronauticsItems.EMPTY_OXYGEN_TANK.get());
            }
        });
        progressData = addDataSlot(DataSlot.standalone());
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 94 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 152));
        }
    }

    @Override
    public void broadcastChanges() {
        if (compressor.getLevel() != null && !compressor.getLevel().isClientSide) {
            progressData.set(compressor.progress());
        }
        super.broadcastChanges();
    }

    public int progress() {
        return progressData.get();
    }

    @Override
    public boolean stillValid(Player player) {
        return compressor.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        boolean moved = index == MACHINE_SLOT
                ? moveItemStackTo(source, PLAYER_INVENTORY_START, slots.size(), true)
                : source.is(AstronauticsItems.EMPTY_OXYGEN_TANK.get())
                        && moveItemStackTo(source, MACHINE_SLOT, PLAYER_INVENTORY_START, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    private static OxygenCompressorBlockEntity resolve(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof OxygenCompressorBlockEntity compressor) return compressor;
        return new OxygenCompressorBlockEntity(pos, AstronauticsBlocks.OXYGEN_COMPRESSOR.get().defaultBlockState());
    }
}
