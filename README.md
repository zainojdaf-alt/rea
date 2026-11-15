# Auction Sniper (Fabric, Minecraft 26.2)

Client-side auction sniper for DonutSMP: opens `/ah <item>`, finds the cheapest listing of
that exact item at or below your maximum price, and buys it. If nothing qualifies it
refreshes and tries again.

- **Minecraft:** 26.2 · **Java:** 25 · **Fabric Loader:** >= 0.19.3 · **Fabric API** required
- **Default key:** `9` (Options → Controls → Key Binds → *Auction Sniper*)
- **Settings screen:** `/auctionsniper`
- **Config file:** `config/auction_sniper.json`

## Building

### GitHub Actions
Push to `main` (or run the **Build** workflow manually). The jar is attached to the run as the
`auction_sniper-jar` artifact. Pushing a tag such as `v1.0.0` also creates a GitHub Release with the jar.

### Locally
Requires JDK 25 and Gradle 9+.

```
gradle build
```

The mod jar is `build/libs/auction_sniper-1.0.0+26.2.jar` (ignore the `-sources` jar).
To add a Gradle wrapper: `gradle wrapper --gradle-version 9.7.0`.

## Updating versions
Everything version-related lives in `gradle.properties`
(`minecraft_version`, `loader_version`, `fabric_api_version`, `loom_version`).
Matching Fabric API versions are listed at <https://fabricmc.net/develop/>.

## What changed from the 1.21.4 build

Minecraft 26.1+ is unobfuscated, so the code now uses official Mojang names.

| 1.21.4 | 26.2 |
| --- | --- |
| Loom `fabric-loom`, Yarn/intermediary mappings | `net.fabricmc.fabric-loom`, no mappings |
| `modImplementation` | `implementation` |
| Java 21, mixin `JAVA_21`, refmap | Java 25, mixin `JAVA_25`, no refmap |
| `KeyBindingHelper`, string category | `KeyMappingHelper`, `KeyMapping.Category` |
| `client.screen` / `setScreen` | `client.gui.screen()` / `client.gui.setScreen()` |
| `render(DrawContext…)`, `drawText` | `extractRenderState(GuiGraphicsExtractor…)`, `text` / `centeredText` |
| `keyPressed(int, int, int)` | `keyPressed(KeyEvent)` |
| `ClickType` / `handleInventoryMouseClick` | `ContainerInput` / `handleContainerInput` |
| `displayClientMessage(msg, true)` | `sendOverlayMessage(msg)` |
| `ClientPlayNetworkHandlerMixin` | `ClientPacketListenerMixin` |

The keybind category translation key changed to `key.category.auction_sniper.main`.

## If the build fails to compile

This port was written without access to the 26.2 jars, so the first CI run is the real test.
The most likely spots, each a one-line fix:

- `ClientPacketListenerMixin`: packet accessors (`containerId()` on the content packet,
  `getContainerId()` / `getSlot()` on the slot packet).
- `ClientCompat`: screen access (`gui.screen()` / `gui.setScreen()`).
- `AuctionSniperClient#sendStatus`: `sendOverlayMessage` (alternative: `sendSystemMessage`).

## License
MIT. See `LICENSE`.
