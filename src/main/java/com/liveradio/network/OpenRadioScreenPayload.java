package com.liveradio.network;

import com.liveradio.LiveRadioConstants;
import com.liveradio.blockentity.RadioBlockEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Server to client: open the station screen for the radio at {@code pos}.
 */
public record OpenRadioScreenPayload(BlockPos pos, String stationUrl) implements CustomPayload {
    public static final Id<OpenRadioScreenPayload> ID =
            new Id<>(Identifier.of(LiveRadioConstants.MOD_ID, "open_radio_screen"));

    public static final PacketCodec<RegistryByteBuf, OpenRadioScreenPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, OpenRadioScreenPayload::pos,
            PacketCodecs.string(RadioBlockEntity.MAX_STATION_URL_LENGTH), OpenRadioScreenPayload::stationUrl,
            OpenRadioScreenPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
