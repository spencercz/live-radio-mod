package com.liveradio.network;

import com.liveradio.blockentity.RadioBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public final class RadioNetworking {
    private RadioNetworking() {
    }

    /**
     * Registers payload types on both sides and the server's handler for station changes.
     */
    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(OpenRadioScreenPayload.ID, OpenRadioScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetStationPayload.ID, SetStationPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetStationPayload.ID, RadioNetworking::onSetStation);
    }

    private static void onSetStation(SetStationPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        BlockPos pos = payload.pos();
        String url = payload.stationUrl().trim();

        // The packet comes from the client, so check everything the GUI was supposed to enforce.
        if (!player.canModifyBlocks() || !player.canInteractWithBlockAt(pos, 1.0)) {
            return;
        }
        if (!RadioBlockEntity.isValidStationUrl(url)) {
            return;
        }
        if (player.getWorld().getBlockEntity(pos) instanceof RadioBlockEntity radioBlockEntity) {
            radioBlockEntity.setStationUrl(url);
        }
    }
}
