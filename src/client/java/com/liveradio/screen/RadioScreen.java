package com.liveradio.screen;

import com.liveradio.blockentity.RadioBlockEntity;
import com.liveradio.network.SetStationPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Lets the player enter the station URL for a radio.
 */
public class RadioScreen extends Screen {
    private static final Text URL_LABEL = Text.translatable("gui.live-radio.radio.url");
    private static final Text HINT = Text.translatable("gui.live-radio.radio.hint").formatted(Formatting.GRAY);
    private static final Text INVALID_URL = Text.translatable("gui.live-radio.radio.invalid_url").formatted(Formatting.RED);

    private final BlockPos pos;
    private String url;
    private TextFieldWidget urlField;
    private ButtonWidget doneButton;

    public RadioScreen(BlockPos pos, String stationUrl) {
        super(Text.translatable("gui.live-radio.radio.title"));
        this.pos = pos;
        this.url = stationUrl;
    }

    @Override
    protected void init() {
        int top = height / 2 - 40;

        urlField = new TextFieldWidget(textRenderer, width / 2 - 150, top + 20, 300, 20, URL_LABEL);
        urlField.setMaxLength(RadioBlockEntity.MAX_STATION_URL_LENGTH);
        urlField.setText(url);
        urlField.setPlaceholder(Text.literal("https://").formatted(Formatting.DARK_GRAY));
        urlField.setChangedListener(text -> {
            url = text;
            doneButton.active = isValid();
        });
        addDrawableChild(urlField);

        doneButton = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> save())
                .dimensions(width / 2 - 152, top + 64, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
                .dimensions(width / 2 + 2, top + 64, 150, 20).build());
        doneButton.active = isValid();

        setInitialFocus(urlField);
    }

    private boolean isValid() {
        return RadioBlockEntity.isValidStationUrl(url.trim());
    }

    private void save() {
        if (isValid()) {
            ClientPlayNetworking.send(new SetStationPayload(pos, url.trim()));
            close();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int top = height / 2 - 40;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top - 20, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, URL_LABEL, width / 2 - 150, top + 8, 0xA0A0A0);
        context.drawCenteredTextWithShadow(textRenderer, isValid() ? HINT : INVALID_URL, width / 2, top + 48, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        // Keep radios playing while the station is being changed in singleplayer.
        return false;
    }
}
