package com.liveradio;

import com.liveradio.audio.RadioAudioManager;
import com.liveradio.network.OpenRadioScreenPayload;
import com.liveradio.screen.RadioScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class LiveRadioClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(OpenRadioScreenPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new RadioScreen(payload.pos(), payload.stationUrl())));
		RadioAudioManager.initialize();
	}
}
