package com.liveradio.network;

import com.liveradio.LiveRadioConstants;
import com.liveradio.blockentity.RadioBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: the player submitted a station URL (empty to clear it) for the radio at {@code pos}.
 */
public record SetStationPayload(BlockPos pos, String stationUrl) implements CustomPacketPayload {
    public static final Type<SetStationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LiveRadioConstants.MOD_ID, "set_station"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetStationPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetStationPayload::pos,
            ByteBufCodecs.stringUtf8(RadioBlockEntity.MAX_STATION_URL_LENGTH), SetStationPayload::stationUrl,
            SetStationPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
