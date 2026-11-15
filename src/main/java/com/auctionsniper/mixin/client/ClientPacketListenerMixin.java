package com.auctionsniper.mixin.client;

import com.auctionsniper.client.AuctionSniperClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void auctionSniper$onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        AuctionSniperClient.onServerInventoryUpdate(packet.containerId(), -1);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void auctionSniper$onContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        AuctionSniperClient.onServerInventoryUpdate(packet.getContainerId(), packet.getSlot());
    }
}
