package com.liveradio.audio;

import com.liveradio.LiveRadioConstants;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/**
 * A radio playing at a block. Goes through Minecraft's sound system like a jukebox, so it fades
 * with distance and follows the "Jukebox/Note Blocks" volume slider.
 */
public class RadioSoundInstance extends AbstractSoundInstance {
    /** Placeholder sound from sounds.json; the audio itself comes from {@link #getAudioStream}. */
    private static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath(LiveRadioConstants.MOD_ID, "radio_stream");

    private final RadioAudioStream stream;

    /**
     * @param stream a stream that is already buffered and ready to play
     */
    public RadioSoundInstance(BlockPos pos, RadioAudioStream stream) {
        super(SOUND_ID, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.stream = stream;
        Vec3 center = Vec3.atCenterOf(pos);
        this.x = center.x;
        this.y = center.y;
        this.z = center.z;
        // Same as a jukebox: volume above 1 stretches the linear falloff to 4 * 16 = 64 blocks.
        this.volume = 4.0F;
        this.attenuation = Attenuation.LINEAR;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
        return CompletableFuture.completedFuture(stream);
    }

    /**
     * Closes the stream. Needed when Minecraft skipped the sound (e.g. no free channel), because
     * then it never takes ownership of the stream.
     */
    public void closeStream() {
        stream.close();
    }
}
