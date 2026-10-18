# Alyn Android host integration spike

This directory describes the first integration step for **Alyn SA-MP Mobile v17.x**. The source was inspected read-only at upstream commit `824260b19bd3fb473b44ee5104d237e300733944` from `east9-777/Nativo_ApkSamp`.

This branch adds an optional, reusable Activity overlay adapter to OpenCEF and a self-contained HTML smoke-test page. It **does not modify the Alyn repository**, does not bundle any Alyn or GTA: San Andreas files, and is not yet an Alyn APK build.

## Findings from the Alyn source

- The gameplay Activity is `com.nativo.rpg.game.SAMP`, extending `com.rockstargames.gtasa.GTASA`.
- Its `onCreate()` calls `ui.initializeUI()` before calling native `initializeSAMP(...)`.
- Existing game widgets are Android overlays created with `Activity.addContentView()`, including the chat widget.
- The project uses Android Gradle Plugin 8.8.0, Gradle 8.10.2, JDK 17, NDK 25.1.8937393, and supports ARM32 and ARM64 in its app configuration.
- The source repository contains proprietary game-related binaries and data files, including `libGTASA.so`, `.gxt`, and `.scm` files. Permission to modify Alyn's source does not by itself grant rights to redistribute third-party GTA assets. Do not copy those assets into OpenCEF or create public build artifacts containing them.

## What the adapter does

`host/src/main/kotlin/com/opencef/host/ActivityBrowserOverlay.kt` provides a small Web button, an explicit close button, lifecycle cleanup, and a WebView container placed above an existing Activity. While closed, the full-screen layer is `GONE` so the native game can receive input. While open, it intentionally covers the game view.

For safety, the helper accepts only local files below `file:///android_asset/`. OpenCEF's JavaScript bridge is reachable from the active page; do not use this helper with untrusted remote content. Remote pages need a separate security design with a strict origin allowlist and a reduced bridge API.

The test page `alyn_test.html` can be copied to `app/src/main/assets/opencef/alyn_test.html` in a private working copy for local smoke testing.

## Wiring it into Alyn

Do this in a separate working branch. Keep the upstream source as the reference baseline and do not modify `main` until the integration builds and is tested. The source repository was read-only to the connected GitHub session, so this branch does not pretend to have changed Alyn files.

1. Bring the OpenCEF `core`, `android`, and `host` modules into the Alyn working tree. Keep this separate from the Alyn source history. In `settings.gradle`, include `:core`, `:android`, and `:host` alongside `:app`. Reconcile the OpenCEF version-catalog aliases in `gradle/libs.versions.toml` with Alyn's existing catalog rather than overwriting the existing Alyn dependencies. The OpenCEF Android and host modules currently declare minSdk 26, while the inspected Alyn app declares minSdk 24; preserve that compatibility decision deliberately (either raise the app's minSdk to 26, or make and test a separate API-24 compatibility change).
2. Add `implementation project(':host')` to Alyn's `app/build.gradle`.
3. Copy `alyn_test.html` into `app/src/main/assets/opencef/alyn_test.html`.
4. In `app/src/main/java/com/nativo/rpg/game/SAMP.java`, add the import and field:

   ```java
   import com.opencef.host.ActivityBrowserOverlay;

   private ActivityBrowserOverlay openCefOverlay;
   ```

   Immediately after `ui.initializeUI();`, add:

   ```java
   openCefOverlay = new ActivityBrowserOverlay(
       this,
       "file:///android_asset/opencef/alyn_test.html"
   );
   openCefOverlay.on("overlay_test_event", payload -> {
       Log.i(TAG, "OpenCEF test event: " + payload);
       ActivityBrowserOverlay overlay = openCefOverlay;
       if (overlay != null) {
           overlay.emit(
               "overlay_test_result",
               "{\"ok\":true,\"message\":\"Kotlin received the event\"}"
           );
       }
   });
   ```

   This creates an initially closed browser and a small `WEB` toggle. Tapping it opens the local page; tapping `×` closes the page without destroying it. The event response confirms a JavaScript-to-Kotlin-to-JavaScript round-trip.
5. In the existing `onDestroy()`, dispose the overlay before `super.onDestroy()`:

   ```java
   if (openCefOverlay != null) {
       openCefOverlay.dispose();
       openCefOverlay = null;
   }
   super.onDestroy();
   ```

6. Build and test the Alyn fork separately. The OpenCEF CI build only compiles the adapter inside OpenCEF's host module; it does **not** prove that a combined Alyn APK builds or runs.

## Device acceptance checklist

- Alyn starts normally; native game rendering and current touch widgets remain unchanged when the overlay is closed.
- The `WEB` control opens the local test page.
- The page reports `Native bridge ready` and enables the test button.
- Tapping the test button displays a JSON response from Kotlin.
- Closing the page returns touch input to the game.
- Reopening the page works without duplicate callbacks.
- Leaving gameplay destroys the WebView without a crash.
- Test both landscape orientations/configuration changes supported by the existing client.

Do not call this integration complete until the combined Alyn build succeeds and these device checks pass.
