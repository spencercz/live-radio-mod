package com.liveradio.audio;

import com.liveradio.LiveRadioConstants;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.CompletableFuture;

/**
 * A radio playing at a block. Goes through Minecraft's sound system like a jukebox, so it fades
 * with distance and follows the "Jukebox/Note Blocks" volume slider.
 */
public class RadioSoundInstance extends AbstractSoundInstance {
    /** Placeholder sound from sounds.json; the audio itself comes from {@link #getAudioStream}. */
    private static final Identifier SOUND_ID = Identifier.of(LiveRadioConstants.MOD_ID, "radio_stream");

    private final RadioAudioStream stream;

    /**
     * @param stream a stream that is already buffered and ready to play
     */
    public RadioSoundInstance(BlockPos pos, RadioAudioStream stream) {
        super(SOUND_ID, SoundCategory.RECORDS, SoundInstance.createRandom());
        this.stream = stream;
        Vec3d center = Vec3d.ofCenter(pos);
        this.x = center.x;
        this.y = center.y;
        this.z = center.z;
        // Same as a jukebox: volume above 1 stretches the linear falloff to 4 * 16 = 64 blocks.
        this.volume = 4.0F;
        this.attenuationType = AttenuationType.LINEAR;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
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
