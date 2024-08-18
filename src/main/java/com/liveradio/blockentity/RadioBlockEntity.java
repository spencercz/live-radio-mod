package com.liveradio.blockentity;

import com.liveradio.AudioPlayer;
import javazoom.jl.decoder.JavaLayerException;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;

import static com.liveradio.blocks.RadioBlock.RADIO_BLOCK_ENTITY;

public class RadioBlockEntity extends BlockEntity {
    public AudioPlayer audioPlayer = new AudioPlayer();
    public String currStation = "";
    public Thread musicThread;

    public RadioBlockEntity(BlockPos pos, BlockState state) {
        super(RADIO_BLOCK_ENTITY, pos, state);
    }

    public void buildMusicThread() {
        currStation = "http://media-ice.musicradio.com/HeartLondonMP3";
        musicThread = new Thread(() -> {
            try {
                audioPlayer.streamAudio(currStation);
            } catch (IOException | JavaLayerException e) {
                throw new RuntimeException(e);
            }});
        musicThread.start();
    }

    public void closeMusicThread() throws InterruptedException {
        audioPlayer.stopAudio();
        musicThread.interrupt();
        musicThread.join();
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        nbt.putString("station", currStation);
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        currStation = nbt.getString("station");
    }
}
