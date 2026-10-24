package com.liveradio.screen;

import com.liveradio.blockentity.RadioBlockEntity;
import com.liveradio.network.SetStationPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Lets the player pick a preset station or enter a station URL for a radio.
 */
public class RadioScreen extends Screen {
    private static final Component URL_LABEL = Component.translatable("gui.live-radio.radio.url");
    private static final Component HINT = Component.translatable("gui.live-radio.radio.hint").withStyle(ChatFormatting.GRAY);
    private static final Component INVALID_URL = Component.translatable("gui.live-radio.radio.invalid_url").withStyle(ChatFormatting.RED);
    private static final int PRESET_COLUMNS = 2;
    private static final int PRESET_ROW_HEIGHT = 24;
    private static final int PRESETS_TOP = 16;

    private final BlockPos pos;
    private final Map<Button, RadioPresets.Preset> presetButtons = new HashMap<>();
    private String url;
    private int top;
    private int urlTop;
    private EditBox urlField;
    private Button doneButton;

    public RadioScreen(BlockPos pos, String stationUrl) {
        super(Component.translatable("gui.live-radio.radio.title"));
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
            Button button = addRenderableWidget(Button.builder(Component.literal(preset.name()),
                            b -> urlField.setValue(preset.url()))
                    .bounds(x, y, 150, 20)
                    .tooltip(Tooltip.create(Component.literal(preset.description()).append("\n")
                            .append(Component.literal(preset.url()).withStyle(ChatFormatting.GRAY))))
                    .build());
            presetButtons.put(button, preset);
        }

        urlField = new EditBox(font, width / 2 - 152, urlTop + 12, 304, 20, URL_LABEL);
        urlField.setMaxLength(RadioBlockEntity.MAX_STATION_URL_LENGTH);
        urlField.setValue(url);
        urlField.setHint(Component.literal("https://").withStyle(ChatFormatting.DARK_GRAY));
        urlField.setResponder(text -> {
            url = text;
            onUrlChanged();
        });
        addRenderableWidget(urlField);

        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(width / 2 - 152, urlTop + 56, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 2, urlTop + 56, 150, 20).build());
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
            Component name = Component.literal(preset.name());
            button.setMessage(preset.url().equals(trimmed) ? name.copy().withStyle(ChatFormatting.YELLOW) : name);
        });
    }

    private boolean isValid() {
        return RadioBlockEntity.isValidStationUrl(url.trim());
    }

    private void save() {
        if (isValid()) {
            ClientPlayNetworking.send(new SetStationPayload(pos, url.trim()));
            onClose();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top, 0xFFFFFFFF);
        graphics.text(font, URL_LABEL, width / 2 - 152, urlTop, 0xFFA0A0A0);
        graphics.centeredText(font, isValid() ? HINT : INVALID_URL, width / 2, urlTop + 40, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        // Keep radios playing while the station is being changed in singleplayer.
        return false;
    }
}
