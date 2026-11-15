package com.auctionsniper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Single place for the screen API, which is the most version-sensitive part of the client.
 * In 26.2 screens are owned by {@code Minecraft#gui}; if a later version moves them again,
 * only these two methods need to change.
 */
final class ClientCompat {
    private ClientCompat() {
    }

    /** The currently open screen, or {@code null} when none is open. */
    static Screen screen(Minecraft client) {
        return client.gui.screen();
    }

    /** Opens {@code screen}; pass {@code null} to close the current screen. */
    static void open(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }
}
