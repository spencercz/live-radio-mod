package com.liveradio.audio;

import com.liveradio.LiveRadio;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.util.Util;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Streams an MP3 internet radio station into Minecraft's sound system.
 * <p>
 * A background thread connects to the station and decodes it with JLayer into a queue of mono PCM
 * chunks (mono so that OpenAL positions the sound at the radio). {@link #read} runs on Minecraft's
 * sound thread and must not block, so it hands out whatever has been decoded, or a short burst of
 * silence if the network falls behind.
 */
public class RadioAudioStream implements AudioStream {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_PLAYLIST_BYTES = 64 * 1024;
    /** Audio decoded before playback starts, because Minecraft queues four buffers straight away. */
    private static final float PREBUFFER_SECONDS = 2.0F;
    /** Caps each buffer handed to the sound system so the four initial buffers all get real audio. */
    private static final float MAX_CHUNK_SECONDS = 0.5F;
    private static final float UNDERRUN_SILENCE_SECONDS = 0.1F;
    /** Older audio is dropped beyond this (e.g. while the game is paused) to keep the radio live. */
    private static final float MAX_BUFFERED_SECONDS = 10.0F;

    private final String url;
    private final CompletableFuture<AudioStream> ready = new CompletableFuture<>();
    private final Deque<byte[]> chunks = new ArrayDeque<>();
    private int bufferedBytes;
    private volatile AudioFormat format;
    private volatile HttpURLConnection connection;
    private volatile boolean closed;
    private volatile boolean finished;

    private RadioAudioStream(String url) {
        this.url = url;
    }

    /**
     * Starts connecting to {@code url} in the background.
     */
    public static RadioAudioStream open(String url) {
        RadioAudioStream stream = new RadioAudioStream(url);
        Thread thread = new Thread(stream::run, "Live Radio stream");
        thread.setDaemon(true);
        thread.start();
        return stream;
    }

    /**
     * Completes once enough audio is buffered to start playback, or exceptionally if the stream fails first.
     */
    public CompletableFuture<AudioStream> whenReady() {
        return ready;
    }

    public boolean failedToStart() {
        return ready.isCompletedExceptionally();
    }

    private void run() {
        try {
            decode(connect(url, 0));
            if (!closed) {
                LiveRadio.LOGGER.warn("Radio stream ended: {}", url);
            }
        } catch (Exception e) {
            if (!closed) {
                LiveRadio.LOGGER.warn("Radio stream {} failed: {}", url, e.toString());
            }
        } finally {
            finished = true;
            ready.completeExceptionally(new IOException("Radio stream stopped before playback started"));
            HttpURLConnection conn = connection;
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private InputStream connect(String address, int depth) throws IOException {
        if (depth > MAX_REDIRECTS) {
            throw new IOException("Too many redirects or nested playlists");
        }
        HttpURLConnection conn = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection = conn;
        if (closed) {
            throw new IOException("Closed");
        }
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        // HttpURLConnection won't follow redirects between http and https, so they're handled below.
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", "LiveRadio-Minecraft-Mod/1.0");

        int status = conn.getResponseCode();
        if (status >= 300 && status < 400) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location == null) {
                throw new IOException("HTTP " + status + " redirect without a location");
            }
            return connect(URI.create(address).resolve(location).toString(), depth + 1);
        }
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + status);
        }

        String type = conn.getContentType() == null ? "" : conn.getContentType().toLowerCase(Locale.ROOT);
        String path = conn.getURL().getPath().toLowerCase(Locale.ROOT);
        if (type.contains("mpegurl") || type.contains("scpls")
                || path.endsWith(".m3u") || path.endsWith(".m3u8") || path.endsWith(".pls")) {
            String playlist = readPlaylist(conn.getInputStream());
            conn.disconnect();
            return connect(firstStreamUrl(playlist, address), depth + 1);
        }
        if (type.startsWith("text/") || type.contains("aac") || type.contains("ogg")
                || type.contains("opus") || type.contains("flac")) {
            throw new IOException("Unsupported stream format " + type + " (only MP3 is supported)");
        }
        return conn.getInputStream();
    }

    private static String readPlaylist(InputStream in) throws IOException {
        try (in) {
            return new String(in.readNBytes(MAX_PLAYLIST_BYTES), StandardCharsets.UTF_8);
        }
    }

    /**
     * Finds the first stream in an .m3u or .pls playlist.
     */
    private static String firstStreamUrl(String playlist, String playlistUrl) throws IOException {
        if (playlist.contains("#EXT-X-")) {
            throw new IOException("HLS streams are not supported");
        }
        for (String line : playlist.split("\\R")) {
            line = line.trim();
            if (line.regionMatches(true, 0, "File", 0, 4) && line.contains("=")) {
                line = line.substring(line.indexOf('=') + 1).trim();
            } else if (line.startsWith("#") || line.startsWith("[") || line.contains("=")) {
                continue;
            }
            if (!line.isEmpty()) {
                return URI.create(playlistUrl).resolve(line).toString();
            }
        }
        throw new IOException("Playlist has no streams");
    }

    private void decode(InputStream in) throws Exception {
        Bitstream bitstream = new Bitstream(new BufferedInputStream(in));
        Decoder decoder = new Decoder();
        try {
            while (!closed) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    return;
                }
                try {
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    if (format == null) {
                        format = new AudioFormat(output.getSampleFrequency(), 16, 1, true, false);
                    } else if (output.getSampleFrequency() != (int) format.getSampleRate()) {
                        // Rare; the manager reconnects and picks up the new rate.
                        throw new IOException("Stream changed sample rate");
                    }
                    enqueue(toMono(output));
                } catch (DecoderException e) {
                    // Skip a corrupt frame rather than dropping the whole stream.
                } finally {
                    bitstream.closeFrame();
                }
            }
        } finally {
            bitstream.close();
        }
    }

    /**
     * Averages the decoded channels into little-endian 16-bit mono samples.
     */
    private static byte[] toMono(SampleBuffer output) {
        short[] samples = output.getBuffer();
        int channels = output.getChannelCount();
        // Interleaved sample count across all channels.
        int length = output.getBufferLength();
        byte[] pcm = new byte[length / channels * 2];
        int out = 0;
        for (int frame = 0; frame + channels <= length; frame += channels) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                sum += samples[frame + channel];
            }
            short mono = (short) (sum / channels);
            pcm[out++] = (byte) mono;
            pcm[out++] = (byte) (mono >> 8);
        }
        return pcm;
    }

    private void enqueue(byte[] pcm) {
        boolean startPlayback;
        synchronized (this) {
            chunks.addLast(pcm);
            bufferedBytes += pcm.length;
            int maxBytes = bytesFor(MAX_BUFFERED_SECONDS);
            while (bufferedBytes > maxBytes) {
                bufferedBytes -= chunks.removeFirst().length;
            }
            startPlayback = bufferedBytes >= bytesFor(PREBUFFER_SECONDS);
        }
        if (startPlayback && !ready.isDone()) {
            ready.complete(this);
        }
    }

    private int bytesFor(float seconds) {
        return (int) (seconds * format.getSampleRate()) * 2;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) {
        byte[] pcm = take(Math.min(size, bytesFor(MAX_CHUNK_SECONDS)) & ~1);
        if (pcm == null) {
            // The stream is over; once the source drains, Minecraft stops the sound.
            return null;
        }
        ByteBuffer buffer = BufferUtils.createByteBuffer(pcm.length);
        buffer.put(pcm).flip();
        return buffer;
    }

    private synchronized byte[] take(int maxBytes) {
        if (closed) {
            return null;
        }
        if (bufferedBytes == 0) {
            if (finished) {
                return null;
            }
            // Keep the OpenAL source fed through a network hiccup, or it would stop playing.
            return new byte[bytesFor(UNDERRUN_SILENCE_SECONDS)];
        }
        byte[] out = new byte[Math.min(maxBytes, bufferedBytes)];
        int offset = 0;
        while (offset < out.length) {
            byte[] chunk = chunks.removeFirst();
            int count = Math.min(chunk.length, out.length - offset);
            System.arraycopy(chunk, 0, out, offset, count);
            offset += count;
            if (count < chunk.length) {
                byte[] rest = new byte[chunk.length - count];
                System.arraycopy(chunk, count, rest, 0, rest.length);
                chunks.addFirst(rest);
            }
        }
        bufferedBytes -= out.length;
        return out;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        synchronized (this) {
            chunks.clear();
            bufferedBytes = 0;
        }
        HttpURLConnection conn = connection;
        if (conn != null) {
            // Unblocks the decode thread. Done off-thread since closing a socket can block.
            Util.getIoWorkerExecutor().execute(conn::disconnect);
        }
    }
}
