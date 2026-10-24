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
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * A radio playing at a block. Goes through Minecraft's sound system like a jukebox, so it fades
 * with distance and follows the "Jukebox/Note Blocks" volume slider.
 */
public class RadioSoundInstance extends AbstractSoundInstance {
    /** Placeholder sound from sounds.json; the audio itself comes from {@link #getAudioStream}. */
    private static final Identifier SOUND_ID = Identifier.of(LiveRadioConstants.MOD_ID, "radio_stream");

    private final String stationUrl;
    @Nullable
    private RadioAudioStream stream;

    public RadioSoundInstance(BlockPos pos, String stationUrl) {
        super(SOUND_ID, SoundCategory.RECORDS, SoundInstance.createRandom());
        this.stationUrl = stationUrl;
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
        stream = RadioAudioStream.open(stationUrl);
        return stream.whenReady();
    }

    /**
     * True if the station couldn't be played at all. The sound system can't tell this case apart
     * from a stream that is still connecting, so the sound has to be stopped by hand.
     */
    public boolean failedToStart() {
        return stream != null && stream.failedToStart();
    }

    /**
     * Closes the stream. The sound system only does this itself once playback has started.
     */
    public void closeStream() {
        if (stream != null) {
            stream.close();
        }
    }
}
