package com.auctionsniper.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AuctionSniperScreen extends Screen {
    private EditBox itemField;
    private EditBox priceField;
    private Button keybindButton;
    private boolean waitingForKey;

    public AuctionSniperScreen() {
        super(Component.literal("Auction Sniper"));
    }

    @Override
    protected void init() {
        AuctionSniperConfig config = AuctionSniperClient.getConfig();
        int center = this.width / 2;

        this.itemField = new EditBox(this.font, center - 100, this.height / 2 - 50, 200, 20,
            Component.literal("Target item"));
        this.itemField.setValue(config.targetItem);
        this.itemField.setMaxLength(100);
        this.addRenderableWidget(this.itemField);

        this.priceField = new EditBox(this.font, center - 100, this.height / 2, 200, 20,
            Component.literal("Maximum price"));
        this.priceField.setValue(Long.toString(config.maximumPrice));
        this.priceField.setMaxLength(18);
        this.addRenderableWidget(this.priceField);

        this.keybindButton = this.addRenderableWidget(Button.builder(keybindText(), button -> {
            this.waitingForKey = true;
            button.setMessage(Component.literal("Press a key..."));
        }).bounds(center - 100, this.height / 2 + 35, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveAndClose())
            .bounds(center - 100, this.height / 2 + 70, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
            .bounds(center + 5, this.height / 2 + 70, 95, 20).build());
    }

    private Component keybindText() {
        return Component.literal("Start/Stop Key: "
            + AuctionSniperClient.getToggleKey().getTranslatedKeyMessage().getString());
    }

    private void saveAndClose() {
        AuctionSniperConfig config = AuctionSniperClient.getConfig();

        String item = this.itemField.getValue().trim().toLowerCase().replace(' ', '_');
        if (!item.isBlank()) {
            config.targetItem = item;
        }

        try {
            config.maximumPrice = Math.max(0L,
                Long.parseLong(this.priceField.getValue().replaceAll("[^0-9]", "")));
        } catch (NumberFormatException ignored) {
            // keep the previous maximum price
        }

        config.save();
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.waitingForKey) {
            // ESC unbinds the key, anything else rebinds it.
            InputConstants.Key key;
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                key = InputConstants.UNKNOWN;
            } else if (event.key() != GLFW.GLFW_KEY_UNKNOWN) {
                key = InputConstants.Type.KEYSYM.getOrCreate(event.key());
            } else {
                key = InputConstants.Type.SCANCODE.getOrCreate(event.scancode());
            }
            AuctionSniperClient.setToggleKey(key);
            this.waitingForKey = false;
            this.keybindButton.setMessage(keybindText());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int center = this.width / 2;
        graphics.centeredText(this.font, this.title, center, this.height / 2 - 100, 0xFFFFFFFF);
        graphics.text(this.font, "Target Item", center - 100, this.height / 2 - 64, 0xFFA0A0A0, true);
        graphics.text(this.font, "Maximum Price", center - 100, this.height / 2 - 14, 0xFFA0A0A0, true);
        graphics.centeredText(this.font,
            Component.literal("Press ESC while binding to remove the keybind."),
            center, this.height / 2 + 105, 0xFF707070);
    }

    @Override
    public void onClose() {
        ClientCompat.open(this.minecraft, null);
    }
}
