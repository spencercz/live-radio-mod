package com.liveradio.audio;

import com.liveradio.LiveRadio;
import com.liveradio.blockentity.RadioBlockEntity;
import com.liveradio.blocks.RadioBlock;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Starts and stops a stream for each loaded radio that is powered, has a station, and is in earshot.
 */
public final class RadioAudioManager {
    /** Radios start within jukebox hearing range and stop a bit further out, so walking along the edge doesn't restart them. */
    private static final double START_DISTANCE = 64.0;
    private static final double STOP_DISTANCE = 80.0;
    /** Wait before reconnecting after a stream fails; doubles with each consecutive failure. */
    private static final int RETRY_DELAY_TICKS = 5 * 20;
    private static final int MAX_RETRY_DELAY_TICKS = 60 * 20;
    /** A stream that played this long counts as a success and resets the retry delay. */
    private static final int HEALTHY_PLAY_TICKS = 60 * 20;

    private static final Set<RadioBlockEntity> RADIOS = new HashSet<>();
    private static final Map<RadioBlockEntity, Playback> PLAYBACKS = new HashMap<>();
    private static long ticks;

    private RadioAudioManager() {
    }

    public static void initialize() {
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof RadioBlockEntity radio) {
                RADIOS.add(radio);
            }
        });
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof RadioBlockEntity radio) {
                RADIOS.remove(radio);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(RadioAudioManager::tick);
    }

    private static void tick(Minecraft client) {
        ticks++;
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            PLAYBACKS.values().forEach(playback -> playback.stop(client));
            PLAYBACKS.clear();
            RADIOS.clear();
            return;
        }

        RADIOS.removeIf(radio -> radio.isRemoved() || radio.getLevel() != level
                || level.getBlockEntity(radio.getBlockPos()) != radio);
        PLAYBACKS.entrySet().removeIf(entry -> {
            if (RADIOS.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().stop(client);
            return true;
        });

        boolean audible = isAudible(client.options);
        for (RadioBlockEntity radio : RADIOS) {
            update(client, level, radio, audible);
        }
    }

    /**
     * Minecraft won't play sounds at zero volume, so a muted radio is treated as switched off.
     * Otherwise each attempt to play would count as a failed stream and back off the retry delay.
     */
    private static boolean isAudible(Options options) {
        return options.getSoundSourceVolume(SoundSource.MASTER) > 0.0F
                && options.getSoundSourceVolume(SoundSource.RECORDS) > 0.0F;
    }

    private static void update(Minecraft client, ClientLevel level, RadioBlockEntity radio, boolean audible) {
        BlockPos pos = radio.getBlockPos();
        String url = radio.getStationUrl();
        BlockState state = level.getBlockState(pos);
        Playback playback = PLAYBACKS.get(radio);

        double range = playback == null ? START_DISTANCE : STOP_DISTANCE;
        boolean shouldPlay = audible && state.is(RadioBlock.RADIO_BLOCK) && state.getValue(RadioBlock.ACTIVATED)
                && !url.isEmpty()
                && client.player.distanceToSqr(Vec3.atCenterOf(pos)) <= range * range;

        if (!shouldPlay || (playback != null && !playback.url.equals(url))) {
            if (playback != null) {
                playback.stop(client);
                PLAYBACKS.remove(radio);
                playback = null;
            }
        }
        if (shouldPlay) {
            if (playback == null) {
                playback = new Playback(url);
                PLAYBACKS.put(radio, playback);
            }
            playback.tick(client, pos);
        }
    }

    /**
     * One radio's stream, including the retry state when the station is unreachable.
     */
    private static final class Playback {
        private final String url;
        /** Connecting and buffering; not handed to Minecraft yet. */
        @Nullable
        private RadioAudioStream pending;
        @Nullable
        private RadioSoundInstance sound;
        private int failures;
        private long startedAt;
        private long retryAt;

        private Playback(String url) {
            this.url = url;
        }

        private void tick(Minecraft client, BlockPos pos) {
            if (pending != null) {
                if (pending.failedToStart()) {
                    stop(client);
                    scheduleRetry(pos);
                } else if (pending.isReady()) {
                    // Minecraft has only a few streaming channels and never frees one that didn't start
                    // playing, so a channel is only claimed once there's audio to play.
                    sound = new RadioSoundInstance(pos, pending);
                    pending = null;
                    startedAt = ticks;
                    client.getSoundManager().play(sound);
                }
            } else if (sound != null) {
                if (client.getSoundManager().isActive(sound)) {
                    return;
                }
                // The stream ended, or Minecraft couldn't play it.
                stop(client);
                scheduleRetry(pos);
            } else if (ticks >= retryAt) {
                LiveRadio.LOGGER.info("Radio at {} tuning in to {}", pos.toShortString(), url);
                pending = RadioAudioStream.open(url);
            }
        }

        private void scheduleRetry(BlockPos pos) {
            if (startedAt > 0 && ticks - startedAt >= HEALTHY_PLAY_TICKS) {
                failures = 0;
            }
            startedAt = 0;
            long delay = Math.min((long) RETRY_DELAY_TICKS << failures, MAX_RETRY_DELAY_TICKS);
            retryAt = ticks + delay;
            failures = Math.min(failures + 1, 10);
            LiveRadio.LOGGER.info("Radio at {} stopped playing; retrying in {}s", pos.toShortString(), delay / 20);
        }

        private void stop(Minecraft client) {
            if (pending != null) {
                pending.close();
                pending = null;
            }
            if (sound != null) {
                client.getSoundManager().stop(sound);
                sound.closeStream();
                sound = null;
            }
        }
    }
}
