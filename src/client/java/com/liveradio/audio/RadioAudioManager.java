package com.liveradio.audio;

import com.liveradio.LiveRadio;
import com.liveradio.blockentity.RadioBlockEntity;
import com.liveradio.blocks.RadioBlock;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

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
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof RadioBlockEntity radio) {
                RADIOS.add(radio);
            }
        });
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof RadioBlockEntity radio) {
                RADIOS.remove(radio);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(RadioAudioManager::tick);
    }

    private static void tick(MinecraftClient client) {
        ticks++;
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            PLAYBACKS.values().forEach(playback -> playback.stop(client));
            PLAYBACKS.clear();
            RADIOS.clear();
            return;
        }

        RADIOS.removeIf(radio -> radio.isRemoved() || radio.getWorld() != world
                || world.getBlockEntity(radio.getPos()) != radio);
        PLAYBACKS.entrySet().removeIf(entry -> {
            if (RADIOS.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().stop(client);
            return true;
        });

        boolean audible = isAudible(client.options);
        for (RadioBlockEntity radio : RADIOS) {
            update(client, world, radio, audible);
        }
    }

    /**
     * Minecraft won't play sounds at zero volume, so a muted radio is treated as switched off.
     * Otherwise each attempt to play would count as a failed stream and back off the retry delay.
     */
    private static boolean isAudible(GameOptions options) {
        return options.getSoundVolume(SoundCategory.MASTER) > 0.0F
                && options.getSoundVolume(SoundCategory.RECORDS) > 0.0F;
    }

    private static void update(MinecraftClient client, ClientWorld world, RadioBlockEntity radio, boolean audible) {
        BlockPos pos = radio.getPos();
        String url = radio.getStationUrl();
        BlockState state = world.getBlockState(pos);
        Playback playback = PLAYBACKS.get(radio);

        double range = playback == null ? START_DISTANCE : STOP_DISTANCE;
        boolean shouldPlay = audible && state.isOf(RadioBlock.RADIO_BLOCK) && state.get(RadioBlock.ACTIVATED)
                && !url.isEmpty()
                && client.player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= range * range;

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

        private void tick(MinecraftClient client, BlockPos pos) {
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
                if (client.getSoundManager().isPlaying(sound)) {
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

        private void stop(MinecraftClient client) {
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
