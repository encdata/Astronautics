package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGridLevelFactory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;
import java.util.stream.Collectors;

@Mixin(PlayerList.class)
public abstract class PlayerListGridLevelsMixin {
    @Redirect(
            method = "placeNewPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;")
    )
    private ServerLevel astronautics$loadSavedGridLevel(MinecraftServer server, ResourceKey<Level> key) {
        if (!SpaceConstants.isGridLevel(key)) return server.getLevel(key);
        SpaceGridAddress address = SpaceGridLevelFactory.addressOf(server, key);
        if (address == null) return null;
        ServerLevel level = SpaceGridLevelFactory.getOrCreate(server, address);
        return level.dimension().equals(key) ? level : null;
    }

    @ModifyArg(
            method = "placeNewPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;createCommonSpawnInfo(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;"),
            index = 0
    )
    private ServerLevel astronautics$keepGridLoginInPublicSpace(ServerLevel level) {
        if (!SpaceConstants.isGridLevel(level.dimension())) return level;
        ServerLevel space = level.getServer().getLevel(SpaceConstants.SPACE_LEVEL);
        return space == null ? level : space;
    }

    @ModifyArg(
            method = "placeNewPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;<init>(IZLjava/util/Set;IIIZZZLnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;Z)V"),
            index = 2
    )
    private Set<ResourceKey<Level>> astronautics$hideGridLevels(Set<ResourceKey<Level>> levels) {
        return levels.stream()
                .filter(level -> !SpaceConstants.isGridLevel(level))
                .collect(Collectors.toUnmodifiableSet());
    }
}
