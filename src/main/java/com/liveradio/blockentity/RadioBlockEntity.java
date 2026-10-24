package com.liveradio.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
        setChanged();
        if (level != null) {
            // Sends the new URL to clients via getUpdatePacket().
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_CLIENTS);
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
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("station", stationUrl);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String url = input.getStringOr("station", "");
        stationUrl = isValidStationUrl(url) ? url : "";
    }
}
