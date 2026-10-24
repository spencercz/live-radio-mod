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
 * Client to server: the player submitted a station URL (empty to clear it) for the radio at {@code pos}.
 */
public record SetStationPayload(BlockPos pos, String stationUrl) implements CustomPayload {
    public static final Id<SetStationPayload> ID =
            new Id<>(Identifier.of(LiveRadioConstants.MOD_ID, "set_station"));

    public static final PacketCodec<RegistryByteBuf, SetStationPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, SetStationPayload::pos,
            PacketCodecs.string(RadioBlockEntity.MAX_STATION_URL_LENGTH), SetStationPayload::stationUrl,
            SetStationPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
