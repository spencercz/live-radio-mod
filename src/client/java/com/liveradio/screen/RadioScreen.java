package com.liveradio.screen;

import com.liveradio.blockentity.RadioBlockEntity;
import com.liveradio.network.SetStationPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Lets the player pick a preset station or enter a station URL for a radio.
 */
public class RadioScreen extends Screen {
    private static final Text URL_LABEL = Text.translatable("gui.live-radio.radio.url");
    private static final Text HINT = Text.translatable("gui.live-radio.radio.hint").formatted(Formatting.GRAY);
    private static final Text INVALID_URL = Text.translatable("gui.live-radio.radio.invalid_url").formatted(Formatting.RED);
    private static final int PRESET_COLUMNS = 2;
    private static final int PRESET_ROW_HEIGHT = 24;
    private static final int PRESETS_TOP = 16;

    private final BlockPos pos;
    private final Map<ButtonWidget, RadioPresets.Preset> presetButtons = new HashMap<>();
    private String url;
    private int top;
    private int urlTop;
    private TextFieldWidget urlField;
    private ButtonWidget doneButton;

    public RadioScreen(BlockPos pos, String stationUrl) {
        super(Text.translatable("gui.live-radio.radio.title"));
        this.pos = pos;
        this.url = stationUrl;
    }

    @Override
    protected void init() {
        int presetRows = (RadioPresets.PRESETS.size() + PRESET_COLUMNS - 1) / PRESET_COLUMNS;
        int presetsHeight = presetRows * PRESET_ROW_HEIGHT;
        top = (height - (PRESETS_TOP + presetsHeight + 84)) / 2;
        urlTop = top + PRESETS_TOP + presetsHeight + 6;

        presetButtons.clear();
        for (int i = 0; i < RadioPresets.PRESETS.size(); i++) {
            RadioPresets.Preset preset = RadioPresets.PRESETS.get(i);
            int x = width / 2 - 152 + (i % PRESET_COLUMNS) * 154;
            int y = top + PRESETS_TOP + (i / PRESET_COLUMNS) * PRESET_ROW_HEIGHT;
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(preset.name()),
                            b -> urlField.setText(preset.url()))
                    .dimensions(x, y, 150, 20)
                    .tooltip(Tooltip.of(Text.literal(preset.description()).append("\n")
                            .append(Text.literal(preset.url()).formatted(Formatting.GRAY))))
                    .build());
            presetButtons.put(button, preset);
        }

        urlField = new TextFieldWidget(textRenderer, width / 2 - 152, urlTop + 12, 304, 20, URL_LABEL);
        urlField.setMaxLength(RadioBlockEntity.MAX_STATION_URL_LENGTH);
        urlField.setText(url);
        urlField.setPlaceholder(Text.literal("https://").formatted(Formatting.DARK_GRAY));
        urlField.setChangedListener(text -> {
            url = text;
            onUrlChanged();
        });
        addDrawableChild(urlField);

        doneButton = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> save())
                .dimensions(width / 2 - 152, urlTop + 56, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
                .dimensions(width / 2 + 2, urlTop + 56, 150, 20).build());
        onUrlChanged();

        setInitialFocus(urlField);
    }

    /**
     * Enables Done only for a valid URL, and marks the preset that matches the URL, if any.
     */
    private void onUrlChanged() {
        doneButton.active = isValid();
        String trimmed = url.trim();
        presetButtons.forEach((button, preset) -> {
            Text name = Text.literal(preset.name());
            button.setMessage(preset.url().equals(trimmed) ? name.copy().formatted(Formatting.YELLOW) : name);
        });
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
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, URL_LABEL, width / 2 - 152, urlTop, 0xA0A0A0);
        context.drawCenteredTextWithShadow(textRenderer, isValid() ? HINT : INVALID_URL, width / 2, urlTop + 40, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        // Keep radios playing while the station is being changed in singleplayer.
        return false;
    }
}
