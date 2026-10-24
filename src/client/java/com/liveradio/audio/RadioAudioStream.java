package com.liveradio.audio;

import com.liveradio.LiveRadio;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

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
    private static final int MAX_PLAYLIST_ENTRIES = 5;
    private static final int PLAYLIST_SNIFF_BYTES = 64;
    /** A .pls stream entry, e.g. {@code File1=http://example.com/stream}. */
    private static final Pattern PLS_FILE_ENTRY = Pattern.compile("(?i)file\\d+=.*");
    /** Any other .pls line, e.g. {@code Title1=...} or {@code NumberOfEntries=1}. */
    private static final Pattern PLS_SETTING = Pattern.compile("[A-Za-z]+\\d*=.*");
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
     * True once enough audio is buffered to start playback.
     */
    public boolean isReady() {
        return ready.isDone() && !ready.isCompletedExceptionally();
    }

    /**
     * True if the stream ended or failed before it was ready to play.
     */
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
        BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
        if (isPlaylist(type, conn.getURL().getPath().toLowerCase(Locale.ROOT), in)) {
            List<String> entries = playlistEntries(readPlaylist(in), address);
            conn.disconnect();
            // Playlists often list mirrors, so fall back to the next entry if one is down.
            IOException failure = new IOException("Playlist has no http(s) streams");
            for (String entry : entries) {
                try {
                    return connect(entry, depth + 1);
                } catch (IOException e) {
                    if (closed) {
                        throw e;
                    }
                    failure = e;
                }
            }
            throw failure;
        }
        if (type.startsWith("text/") || type.contains("aac") || type.contains("ogg")
                || type.contains("opus") || type.contains("flac")) {
            throw new IOException("Unsupported stream format " + type + " (only MP3 is supported)");
        }
        return in;
    }

    /**
     * Recognizes .m3u and .pls playlists by content type, extension, or, for servers that send them as
     * plain text from an address without an extension, by their first line.
     */
    private static boolean isPlaylist(String type, String path, BufferedInputStream in) throws IOException {
        if (type.contains("mpegurl") || type.contains("scpls") || type.contains("pls+xml")
                || path.endsWith(".m3u") || path.endsWith(".m3u8") || path.endsWith(".pls")) {
            return true;
        }
        in.mark(PLAYLIST_SNIFF_BYTES);
        byte[] head = in.readNBytes(PLAYLIST_SNIFF_BYTES);
        in.reset();
        String start = new String(head, StandardCharsets.UTF_8).replace("﻿", "").stripLeading().toLowerCase(Locale.ROOT);
        return start.startsWith("#extm3u") || start.startsWith("[playlist]")
                || start.startsWith("http://") || start.startsWith("https://");
    }

    private static String readPlaylist(InputStream in) throws IOException {
        try (in) {
            return new String(in.readNBytes(MAX_PLAYLIST_BYTES), StandardCharsets.UTF_8);
        }
    }

    /**
     * Lists the http(s) streams in an .m3u or .pls playlist, resolving relative entries.
     */
    private static List<String> playlistEntries(String playlist, String playlistUrl) throws IOException {
        if (playlist.contains("#EXT-X-")) {
            throw new IOException("HLS streams are not supported");
        }
        List<String> entries = new ArrayList<>();
        for (String line : playlist.replace("﻿", "").split("\\R")) {
            line = line.trim();
            if (PLS_FILE_ENTRY.matcher(line).matches()) {
                line = line.substring(line.indexOf('=') + 1).trim();
            } else if (line.isEmpty() || line.startsWith("#") || line.startsWith("[") || PLS_SETTING.matcher(line).matches()) {
                continue;
            }
            try {
                URI uri = URI.create(playlistUrl).resolve(line);
                String scheme = uri.getScheme();
                if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                        && entries.size() < MAX_PLAYLIST_ENTRIES) {
                    entries.add(uri.toString());
                }
            } catch (IllegalArgumentException e) {
                // Not a usable address; skip the line.
            }
        }
        return entries;
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
        if (startPlayback && !ready.isDone() && ready.complete(this)) {
            LiveRadio.LOGGER.info("Radio stream {} started ({} Hz)", url, (int) format.getSampleRate());
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
            Util.ioPool().execute(conn::disconnect);
        }
    }
}
