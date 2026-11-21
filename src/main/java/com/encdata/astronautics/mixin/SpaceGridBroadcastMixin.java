package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlayerList.class, priority = 1100)
public abstract class SpaceGridBroadcastMixin {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private List<ServerPlayer> players;

    @Inject(
            method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void astronautics$acrossGridEdges(Player except, double x, double y, double z, double radius,
                                              ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo ci) {
        if (!SpaceConstants.SPACE_LEVEL.equals(dimension)) return;
        ServerLevel level = this.server.getLevel(dimension);
        if (level == null || !SpaceGrids.placedInSpace(level, x, z)) return;
        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayer player = this.players.get(i);
            if (player == except || player.level() != level) continue;
            Vec3 image = SpaceGrids.imageFor(player, x, y, z);
            if (image == null) continue;
            if (Sable.HELPER.distanceSquaredWithSubLevels(level, player.position(),
                    x + image.x, y + image.y, z + image.z) < radius * radius) {
                player.connection.send(packet);
            }
        }
        ci.cancel();
    }

    @WrapOperation(
            method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1
    )
    private void astronautics$sameGridListeners(ServerGamePacketListenerImpl connection, Packet<?> packet,
                                                Operation<Void> original,
                                                @Local(argsOnly = true) ResourceKey<Level> dimension) {
        if (SpaceConstants.SPACE_LEVEL.equals(dimension)) {
            SpaceGridAddress source = SpaceGrids.context();
            if (source != null && !source.equals(SpaceGrids.of(connection.player))) return;
        }
        original.call(connection, packet);
    }
}
