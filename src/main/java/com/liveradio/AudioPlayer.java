package com.liveradio;

import java.io.IOException;
import java.io.InputStream;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AudioPlayer {
    public Player player;
    private boolean isPlaying = false;
    public AudioPlayer() {
    }

    public void streamAudio(String url) throws IOException, JavaLayerException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        InputStream stream = response.body().byteStream();

        player = new Player(stream);
        isPlaying = true;
        player.play();
    }
    public void stopAudio() {
        isPlaying = false;
        if (player != null) {
            player.close();
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
