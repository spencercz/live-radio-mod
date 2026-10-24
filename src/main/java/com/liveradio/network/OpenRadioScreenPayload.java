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
 * Server to client: open the station screen for the radio at {@code pos}.
 */
public record OpenRadioScreenPayload(BlockPos pos, String stationUrl) implements CustomPacketPayload {
    public static final Type<OpenRadioScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LiveRadioConstants.MOD_ID, "open_radio_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRadioScreenPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenRadioScreenPayload::pos,
            ByteBufCodecs.stringUtf8(RadioBlockEntity.MAX_STATION_URL_LENGTH), OpenRadioScreenPayload::stationUrl,
            OpenRadioScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
