package com.liveradio.screen;

import java.util.List;

/**
 * Stations offered as one-click buttons in the radio screen. All are MP3 streams, since that's the
 * only format the radio can decode.
 */
public final class RadioPresets {
    public record Preset(String name, String description, String url) {
    }

    public static final List<Preset> PRESETS = List.of(
            new Preset("NPR News", "NPR's national program stream", "https://npr-ice.streamguys1.com/live.mp3"),
            new Preset("WBUR Boston", "NPR news and talk, Boston", "https://icecast-stream.wbur.org/wbur"),
            new Preset("KQED San Francisco", "NPR news and talk, San Francisco", "https://streams.kqed.org/kqedradio"),
            new Preset("WBEZ Chicago", "NPR news and talk, Chicago", "https://stream.wbez.org/wbez128.mp3"),
            new Preset("KEXP Seattle", "Independent and alternative music", "https://kexp-mp3-128.streamguys1.com/kexp128.mp3"),
            new Preset("WXPN Philadelphia", "Adult alternative music", "https://wxpn.xpn.org/xpnmp3hi"),
            new Preset("WQXR Classical", "Classical music, New York", "https://stream.wqxr.org/wqxr"),
            new Preset("BBC World Service", "International news", "http://stream.live.vc.bbcmedia.co.uk/bbc_world_service")
    );

    private RadioPresets() {
    }
}
