package com.auctionsniper.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class AuctionSniperConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("auction_sniper.json");

    public String targetItem = "diamond";
    public long maximumPrice = 100000L;
    public boolean dontShowQuickBuyPrompt;

    public static AuctionSniperConfig load() {
        if (!Files.exists(PATH)) {
            AuctionSniperConfig config = new AuctionSniperConfig();
            config.save();
            return config;
        }

        try (Reader reader = Files.newBufferedReader(PATH)) {
            AuctionSniperConfig config = GSON.fromJson(reader, AuctionSniperConfig.class);
            return config == null ? new AuctionSniperConfig() : config;
        } catch (IOException exception) {
            AuctionSniperClient.LOGGER.error("Could not load Auction Sniper config", exception);
            return new AuctionSniperConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            AuctionSniperClient.LOGGER.error("Could not save Auction Sniper config", exception);
        }
    }
}
