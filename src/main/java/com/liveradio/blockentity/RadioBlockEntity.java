package com.liveradio.blockentity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.net.URI;
import java.net.URISyntaxException;

import static com.liveradio.blocks.RadioBlock.RADIO_BLOCK_ENTITY;

/**
 * Stores the radio's station URL. The URL is synced to clients, which do the actual streaming
 * (see {@code RadioAudioManager} in the client source set).
 */
public class RadioBlockEntity extends BlockEntity {
    public static final int MAX_STATION_URL_LENGTH = 512;

    private String stationUrl = "";

    public RadioBlockEntity(BlockPos pos, BlockState state) {
        super(RADIO_BLOCK_ENTITY, pos, state);
    }

    public String getStationUrl() {
        return stationUrl;
    }

    public void setStationUrl(String url) {
        stationUrl = url;
        markDirty();
        if (world != null) {
            // Sends the new URL to clients via toUpdatePacket().
            BlockState state = getCachedState();
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    /**
     * Returns true for an empty string (no station) or an absolute http(s) URL.
     */
    public static boolean isValidStationUrl(String url) {
        if (url.isEmpty()) {
            return true;
        }
        if (url.length() > MAX_STATION_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup wrapper) {
        return createNbt(wrapper);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        nbt.putString("station", stationUrl);
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        String url = nbt.getString("station");
        stationUrl = isValidStationUrl(url) ? url : "";
    }
}
