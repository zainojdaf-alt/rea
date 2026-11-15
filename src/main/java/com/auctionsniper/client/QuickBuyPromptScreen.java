package com.auctionsniper.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class QuickBuyPromptScreen extends Screen {
    private boolean dontShowAgain;
    private Button dontShowButton;

    public QuickBuyPromptScreen() {
        super(Component.literal("Auction Sniper"));
        this.dontShowAgain = AuctionSniperClient.getConfig().dontShowQuickBuyPrompt;
    }

    @Override
    protected void init() {
        int center = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Yes"), button -> {
            savePreference();
            AuctionSniperClient.startSniper(this.minecraft);
        }).bounds(center - 105, this.height / 2 + 5, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("No (Turn On)"), button -> {
            savePreference();
            AuctionSniperClient.beginQuickBuySetup();
        }).bounds(center + 5, this.height / 2 + 5, 100, 20).build());

        this.dontShowButton = this.addRenderableWidget(Button.builder(dontShowText(), button -> {
            this.dontShowAgain = !this.dontShowAgain;
            button.setMessage(dontShowText());
        }).bounds(center - 105, this.height / 2 + 35, 210, 20).build());
    }

    private Component dontShowText() {
        return Component.literal((this.dontShowAgain ? "[X] " : "[ ] ") + "Don't Show Again");
    }

    private void savePreference() {
        AuctionSniperClient.getConfig().dontShowQuickBuyPrompt = this.dontShowAgain;
        AuctionSniperClient.getConfig().save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, Component.literal("Do you have Quick Buy turned on?"),
            this.width / 2, this.height / 2 - 35, 0xFFFFFFFF);
    }
}
