package com.auctionsniper.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuctionSniperClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("auction_sniper");

    private static final Pattern PRICE_PATTERN =
        Pattern.compile("\\$\\s*([0-9]+(?:[.,][0-9]+)?)\\s*([kKmMbBtT]?)");

    /** The auction GUI is a 6-row chest: 45 listing slots, then a control row; slot 49 is the refresh button. */
    private static final int LISTING_SLOTS = 45;
    private static final int REFRESH_SLOT = 49;

    private static AuctionSniperConfig config;
    private static KeyMapping toggleKey;
    private static boolean running;
    private static boolean waitingForAuctionScreen;
    private static int previousSyncId = -1;
    private static int auctionSyncId = -1;
    private static boolean auctionUpdatePending;
    private static QuickBuyState quickBuyState = QuickBuyState.IDLE;
    private static int quickBuySlot = -1;

    enum QuickBuyState {
        IDLE,
        WAITING_FOR_SETTINGS,
        WAITING_FOR_ON,
        STARTING_AFTER_CLOSE
    }

    @Override
    public void onInitializeClient() {
        config = AuctionSniperConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("auction_sniper", "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.auction_sniper.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_9,
            category));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("auctionsniper").executes(context -> {
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> ClientCompat.open(client, new AuctionSniperScreen()));
                return 1;
            })));

        ClientTickEvents.END_CLIENT_TICK.register(AuctionSniperClient::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            Screen current = ClientCompat.screen(client);
            if (current instanceof AuctionSniperScreen || current instanceof QuickBuyPromptScreen) {
                continue;
            }
            if (running || quickBuyState != QuickBuyState.IDLE) {
                stop(client);
                continue;
            }
            if (config.dontShowQuickBuyPrompt) {
                startSniper(client);
                continue;
            }
            ClientCompat.open(client, new QuickBuyPromptScreen());
        }

        if (client.player == null || client.gameMode == null) {
            return;
        }

        if (quickBuyState != QuickBuyState.IDLE) {
            if (quickBuyState == QuickBuyState.STARTING_AFTER_CLOSE) {
                if (!(ClientCompat.screen(client) instanceof AbstractContainerScreen<?>)) {
                    quickBuyState = QuickBuyState.IDLE;
                    startSniper(client);
                }
                return;
            }
            handleQuickBuySetup(client);
            return;
        }

        if (!running) {
            return;
        }

        Screen current = ClientCompat.screen(client);

        if (waitingForAuctionScreen) {
            if (!(current instanceof AbstractContainerScreen<?>)
                || client.player.containerMenu.containerId == previousSyncId) {
                return;
            }
            waitingForAuctionScreen = false;
            auctionSyncId = client.player.containerMenu.containerId;
            processAuctionUpdate(client, (AbstractContainerScreen<?>) current);
        }

        if (current instanceof AbstractContainerScreen<?> screen
            && screen.getMenu().containerId == auctionSyncId
            && auctionUpdatePending) {
            auctionUpdatePending = false;
            processAuctionUpdate(client, screen);
        }
    }

    private static void processAuctionUpdate(Minecraft client, AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        if (menu.slots.size() <= REFRESH_SLOT) {
            return;
        }

        int listingSlot = findCheapestMatchingListing(screen);
        if (listingSlot >= 0) {
            click(client, menu, listingSlot, ContainerInput.PICKUP);
        } else {
            click(client, menu, REFRESH_SLOT, ContainerInput.QUICK_MOVE);
        }
    }

    /** Called from the packet mixin whenever the server updates a container's contents or one of its slots. */
    public static void onServerInventoryUpdate(int syncId, int slot) {
        if (!running || syncId != auctionSyncId) {
            return;
        }
        if (slot < 0 || slot < LISTING_SLOTS) {
            auctionUpdatePending = true;
        }
    }

    private static void handleQuickBuySetup(Minecraft client) {
        if (!(ClientCompat.screen(client) instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();

        if (quickBuyState == QuickBuyState.WAITING_FOR_SETTINGS) {
            if (menu.containerId == previousSyncId) {
                return;
            }
            quickBuySlot = findQuickBuySlot(screen);
            if (quickBuySlot < 0) {
                return;
            }
            String text = searchableText(menu.getSlot(quickBuySlot).getItem());
            if (isQuickBuyOn(text)) {
                finishQuickBuySetup(client);
                return;
            }
            click(client, menu, quickBuySlot, ContainerInput.PICKUP);
            quickBuyState = QuickBuyState.WAITING_FOR_ON;
            return;
        }

        if (quickBuySlot >= 0 && quickBuySlot < menu.slots.size()) {
            String text = searchableText(menu.getSlot(quickBuySlot).getItem());
            if (isQuickBuyOn(text)) {
                finishQuickBuySetup(client);
            }
        }
    }

    private static int findQuickBuySlot(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        for (int i = 0; i < menu.slots.size(); i++) {
            String text = searchableText(menu.getSlot(i).getItem());
            if (text.contains("auction quick buy")) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isQuickBuyOn(String text) {
        return text.matches("(?s).*auction quick buy\\s*:\\s*on\\b.*");
    }

    private static void finishQuickBuySetup(Minecraft client) {
        quickBuyState = QuickBuyState.STARTING_AFTER_CLOSE;
        quickBuySlot = -1;
        client.player.closeContainer();
    }

    static void beginQuickBuySetup() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) {
            return;
        }
        previousSyncId = client.player.containerMenu.containerId;
        quickBuyState = QuickBuyState.WAITING_FOR_SETTINGS;
        ClientCompat.open(client, null);
        client.getConnection().sendCommand("settings");
        sendStatus(client, "opening settings to enable Auction Quick Buy...", ChatFormatting.YELLOW);
    }

    static void startSniper(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
            return;
        }
        ClientCompat.open(client, null);
        running = true;
        waitingForAuctionScreen = true;
        previousSyncId = client.player.containerMenu.containerId;
        auctionSyncId = -1;
        auctionUpdatePending = false;
        client.getConnection().sendCommand("ah " + config.targetItem);
        sendStatus(client, "started: /ah " + config.targetItem, ChatFormatting.GREEN);
    }

    private static void stop(Minecraft client) {
        running = false;
        waitingForAuctionScreen = false;
        auctionSyncId = -1;
        auctionUpdatePending = false;
        quickBuyState = QuickBuyState.IDLE;
        quickBuySlot = -1;
        sendStatus(client, "stopped", ChatFormatting.RED);
    }

    private static int findCheapestMatchingListing(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        int bestSlot = -1;
        double bestPrice = Double.MAX_VALUE;
        int limit = Math.min(LISTING_SLOTS, menu.slots.size());

        for (int i = 0; i < limit; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty() || !matchesTargetItem(stack)) {
                continue;
            }
            double price = readPrice(stack);
            if (price < 0 || price > (double) config.maximumPrice || price >= bestPrice) {
                continue;
            }
            bestSlot = i;
            bestPrice = price;
        }
        return bestSlot;
    }

    /** Matches the item id and an unrenamed default name, so renamed or custom listings are ignored. */
    private static boolean matchesTargetItem(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String target = config.targetItem.toLowerCase(Locale.ROOT);
        String fullId = target.contains(":") ? target : "minecraft:" + target;
        String shortName = target.substring(target.indexOf(':') + 1).replace('_', ' ');
        String displayName = stack.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
        return itemId.equals(fullId) && displayName.equals(shortName);
    }

    private static double readPrice(ItemStack stack) {
        Matcher matcher = PRICE_PATTERN.matcher(searchableText(stack));
        if (!matcher.find()) {
            return -1.0;
        }
        try {
            double value = Double.parseDouble(matcher.group(1).replace(',', '.'));
            double multiplier = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "k" -> 1_000.0;
                case "m" -> 1_000_000.0;
                case "b" -> 1_000_000_000.0;
                case "t" -> 1_000_000_000_000.0;
                default -> 1.0;
            };
            return value * multiplier;
        } catch (NumberFormatException exception) {
            return -1.0;
        }
    }

    /** Item name plus lore lines, lowercased. */
    private static String searchableText(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder(stack.getHoverName().getString());
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                text.append('\n').append(line.getString());
            }
        }
        return text.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static void click(Minecraft client, AbstractContainerMenu menu, int slot, ContainerInput input) {
        client.gameMode.handleContainerInput(menu.containerId, slot, 0, input, client.player);
    }

    private static void sendStatus(Minecraft client, String message, ChatFormatting color) {
        if (client.player != null) {
            client.player.sendOverlayMessage(
                Component.literal("[Auction Sniper] " + message).withStyle(color));
        }
    }

    public static AuctionSniperConfig getConfig() {
        return config;
    }

    public static KeyMapping getToggleKey() {
        return toggleKey;
    }

    public static void setToggleKey(InputConstants.Key key) {
        toggleKey.setKey(key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }
}
