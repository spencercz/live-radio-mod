package com.liveradio.network;

import com.liveradio.blockentity.RadioBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class RadioNetworking {
    private RadioNetworking() {
    }

    /**
     * Registers payload types on both sides and the server's handler for station changes.
     */
    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(OpenRadioScreenPayload.TYPE, OpenRadioScreenPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetStationPayload.TYPE, SetStationPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetStationPayload.TYPE, RadioNetworking::onSetStation);
    }

    private static void onSetStation(SetStationPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        BlockPos pos = payload.pos();
        String url = payload.stationUrl().trim();

        // The packet comes from the client, so check everything the GUI was supposed to enforce.
        if (!player.mayBuild() || !player.isWithinBlockInteractionRange(pos, 1.0)) {
            return;
        }
        if (!RadioBlockEntity.isValidStationUrl(url)) {
            return;
        }
        if (player.level().getBlockEntity(pos) instanceof RadioBlockEntity radioBlockEntity) {
            radioBlockEntity.setStationUrl(url);
        }
    }
}
