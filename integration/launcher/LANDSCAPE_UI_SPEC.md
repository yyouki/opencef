# Launcher UI specification — PC SA-MP style, Android landscape

## Product direction

Build a **graphical native Android launcher** inspired by the functional layout of the classic PC SA-MP server browser. This is not a pixel-for-pixel copy of another launcher and must not reuse its copyrighted icons, logos, or artwork.

Do not make a terminal TUI the primary UI. A TUI may exist later as an optional developer diagnostic tool, but the normal client should be touch-operable.

**Landscape is a product requirement** for the entire launcher-to-game flow, not only the game Activity. Apply `android:screenOrientation="landscape"` to every applicable visible Activity: splash, launcher/server browser, update/download/loader screens, settings and gameplay. Preserve or explicitly handle each Activity's `configChanges` and lifecycle behavior; do not blindly copy the game Activity's settings to all screens.

## Main server-browser layout

Use a landscape layout with three working zones:

1. **Navigation rail (left):** Internet, Favorites, History, Direct Connect, Settings.
2. **Server list (center):** searchable/filterable rows with Hostname, Players, Ping, Mode, Map, Language, and password indicator. Display real values only; use a neutral unavailable/loading value until a successful query returns them.
3. **Selected-server details (right or lower-right):** address and port, server name, player count/max, ping, mode, map, language, and whether a password is required. Never invent online status, counts, ping or metadata.

A compact top bar contains the application title, connection status, and refresh action. A bottom connect panel contains the nickname field, selected address summary, and a prominent **Connect / Play** control.

## Required screens and interactions

### Internet
Show a public server list only when an approved, working data source is configured and tested. Include a search field and filters for hostname, game mode and password status. Provide loading, empty, offline and request-failure states. Do not hard-code fabricated rows or statistics.

### Favorites
Allow adding/removing a server, manually adding `host:port` addresses, refreshing its query data, and saving favorite entries locally. Favorites must remain available after app restart even when there is no network.

### History
Show recently selected/connected server entries only when that history is actually recorded. Permit clearing history. Do not prepopulate fake historical entries.

### Direct Connect
Provide hostname/IP, port, optional server password, and nickname inputs with validation. This must work without requiring an Internet-list API. Validate port range and reject empty/invalid host input with clear messages.

### Settings
Include nickname, language where supported, landscape/fullscreen preferences only if the client really implements them, sound/control preferences only when connected to real settings, and a diagnostics/version page. Do not expose settings that are decorative but non-functional.

## Landscape and touch behavior

- Designed for a minimum logical content width of about 720dp; use responsive columns on narrower landscape phones rather than fixed pixel widths.
- Interactive targets should be comfortable for touch (generally at least 48dp high).
- The on-screen keyboard must not obscure the active nickname/address/port field or Connect button. Preserve the landscape layout when text fields gain focus.
- Avoid hover-only actions, right-click-only actions, and dense desktop-sized menus. Provide visible touch buttons and context actions.
- Save form state on rotation/recreation as appropriate, and verify Android back navigation.
- Keep the browser overlay used in-game separate from launcher navigation and connection controls. Closing or destroying the WebView must return interaction to gameplay without intercepting touches.

## Data and reliability constraints

- Do not fabricate player counts, server lists, latency, online state, update progress, download size or version claims.
- Network calls must have timeouts, cancellation, loading/error states and UI-thread-safe result handling.
- Never bundle game data, `libGTASA.so`, or other third-party proprietary assets in OpenCEF or public build artifacts unless the relevant redistribution rights are explicitly established.
- Do not reuse external project signing credentials. Use a private, owner-controlled signing process for releases.

## Acceptance checklist

- [ ] Every visible launcher/game Activity opens in landscape.
- [ ] No screen switches back to portrait during splash, server selection, download, settings, connect, or return from gameplay.
- [ ] Server list shows only real query/API values and has loading/empty/error states.
- [ ] Favorites and direct connect work without depending on a fabricated server-list service.
- [ ] Nickname, address, port, and optional password inputs work with the landscape soft keyboard.
- [ ] Back navigation and Activity lifecycle do not leak or retain the WebView.
- [ ] When the overlay is closed, game controls receive touch input normally.
- [ ] A combined APK build succeeds in CI and the resulting APK passes the device checklist.
