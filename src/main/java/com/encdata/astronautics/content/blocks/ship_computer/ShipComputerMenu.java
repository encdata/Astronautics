package com.encdata.astronautics.content.blocks.ship_computer;

import com.encdata.astronautics.registry.AstronauticsBlocks;
import com.encdata.astronautics.registry.AstronauticsMenus;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.CelestialGravity;
import com.encdata.astronautics.content.blocks.ship_computer.ShipComputerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;

public final class ShipComputerMenu extends AbstractContainerMenu {
    private final ShipComputerBlockEntity terminal;
    private final ShipComputerBlockEntity computer;
    private final DataSlot modeData = DataSlot.standalone();
    private final DataSlot targetBodyData = DataSlot.standalone();
    private final DataSlot targetAltitudeData = DataSlot.standalone();
    private final DataSlot primaryData = DataSlot.standalone();
    private final DataSlot computerCountData = DataSlot.standalone();
    private long lastComputerScan = Long.MIN_VALUE;

    public ShipComputerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, resolve(inventory, buffer.readBlockPos()));
    }

    public ShipComputerMenu(int id, Inventory inventory, ShipComputerBlockEntity computer) {
        super(AstronauticsMenus.SHIP_COMPUTER.get(), id);
        this.terminal = computer;
        this.computer = controllerFor(computer);
        addDataSlot(modeData);
        addDataSlot(targetBodyData);
        addDataSlot(targetAltitudeData);
        addDataSlot(primaryData);
        addDataSlot(computerCountData);
    }

    @Override
    public void broadcastChanges() {
        if (computer.getLevel() != null && !computer.getLevel().isClientSide) {
            modeData.set(computer.mode().ordinal());
            long gameTime = computer.getLevel().getGameTime();
            if (lastComputerScan == Long.MIN_VALUE || gameTime - lastComputerScan >= 10) {
                lastComputerScan = gameTime;
                var subLevel = Sable.HELPER.getContaining(terminal.getLevel(), terminal.getBlockPos());
                if (subLevel instanceof ServerSubLevel ship) {
                    ShipComputerBlockEntity primary = ShipComputerBlockEntity.primaryComputer(ship);
                    primaryData.set(primary != null
                            && primary.getBlockPos().equals(terminal.getBlockPos()) ? 1 : 0);
                    computerCountData.set(ShipComputerBlockEntity.computerCount(ship));
                } else {
                    primaryData.set(1);
                    computerCountData.set(1);
                }
            }
            var bodyIds = SpaceCatalog.bodyIds();
            if (computer.targetBody() == null && !bodyIds.isEmpty()) {
                var level = computer.getLevel();
                ResourceLocation nearestBody = SpaceCatalog.bodyForDimension(level.dimension())
                        .map(body -> body.id()).orElse(null);
                var subLevel = Sable.HELPER.getContaining(terminal.getLevel(), terminal.getBlockPos());
                if (subLevel instanceof ServerSubLevel ship) {
                    var centerOfMass = ship.getMassTracker().getCenterOfMass();
                    if (centerOfMass != null) {
                        Vector3d position = ship.logicalPose().transformPosition(
                                new Vector3d(centerOfMass), new Vector3d());
                        double nearestDistance = Double.POSITIVE_INFINITY;
                        for (var source : CelestialGravity.sourcesAtTime(level.getDayTime() / 24000.0)) {
                            if (!bodyIds.contains(source.id())) continue;
                            Vector3d bodyPosition = new Vector3d(source.absolutePosition().x(),
                                    source.absolutePosition().y(), source.absolutePosition().z());
                            double distance = position.distanceSquared(bodyPosition);
                            if (distance < nearestDistance) {
                                nearestDistance = distance;
                                nearestBody = source.id();
                            }
                        }
                    }
                }
                if (nearestBody == null) nearestBody = bodyIds.getFirst();
                computer.setOrbitTarget(nearestBody, computer.targetAltitude());
            }
            int selectedBody = computer.targetBody() == null ? -1 : bodyIds.indexOf(computer.targetBody());
            targetBodyData.set(selectedBody);
            targetAltitudeData.set(computer.targetAltitude());
        }
        super.broadcastChanges();
    }

    public ShipComputerMode mode() {
        return ShipComputerMode.byId(modeData.get());
    }

    public int targetBodyIndex() {
        return targetBodyData.get();
    }

    public ResourceLocation targetBody() {
        int index = targetBodyIndex();
        var bodyIds = SpaceCatalog.bodyIds();
        return index >= 0 && index < bodyIds.size() ? bodyIds.get(index) : null;
    }

    public int targetAltitude() {
        return targetAltitudeData.get();
    }

    public boolean isPrimaryConsole() {
        return primaryData.get() != 0;
    }

    public int computerCount() {
        return computerCountData.get();
    }

    public BlockPos computerPos() {
        return terminal.computerPos();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player)) return false;
        if (id >= 0 && id < ShipComputerMode.values().length) {
            computer.setMode(ShipComputerMode.byId(id));
            modeData.set(id);
            return true;
        }
        if (id == 11) {
            computer.setOrbitTarget(computer.targetBody(), computer.targetAltitude() - 100);
        } else if (id == 12) {
            computer.setOrbitTarget(computer.targetBody(), computer.targetAltitude() + 100);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return terminal.stillValid(player) && computer.getLevel() != null
                && computer.getLevel().getBlockEntity(computer.getBlockPos()) == computer;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static ShipComputerBlockEntity resolve(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof ShipComputerBlockEntity computer) return computer;
        return new ShipComputerBlockEntity(pos, AstronauticsBlocks.SHIP_COMPUTER.get().defaultBlockState());
    }

    private static ShipComputerBlockEntity controllerFor(ShipComputerBlockEntity terminal) {
        if (terminal.getLevel() == null || terminal.getLevel().isClientSide) return terminal;
        var subLevel = Sable.HELPER.getContaining(terminal.getLevel(), terminal.getBlockPos());
        if (subLevel instanceof ServerSubLevel ship) {
            ShipComputerBlockEntity primary = ShipComputerBlockEntity.primaryComputer(ship);
            if (primary != null) return primary;
        }
        return terminal;
    }
}
