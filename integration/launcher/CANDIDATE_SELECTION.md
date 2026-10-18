# SA-MP client base comparison and provisional choice

Date checked: 2026-10-19. This is a read-only source-structure assessment, not a license opinion and not a completed client integration.

## Provisional choice

Use **`kuzia15/SAMP-Mobile` branch `GTA-2.10` as the first technical candidate to investigate for a generic SA-MP client, because it already has a dedicated Android launcher and server-browser code. Keep the selection provisional until reuse and distribution permissions are clarified.

Repository: https://github.com/kuzia15/SAMP-Mobile/tree/GTA-2.10
Inspected branch commit: `c67c8bdfbc95840ec2d016c456a1a978bdef8fbf`

### Evidence for the choice

- The README identifies separate SA-MP 2.10 and 2.11 branches; its 2.10 branch claims ARM32 and ARM64 support.
- Existing launcher code includes `com.samp.mobile.launcher.MainActivity`, server and favorite lists, server information/query helpers, a server-add flow, and tabs for Servers, Info, and Settings.
- Existing game entry point is `com.samp.mobile.game.SAMP`.
- The inspected manifest already declares the gameplay activity landscape, but declares launcher, splash, and update activities portrait. Those launcher activities would have to be changed and checked individually to meet the landscape-only requirement.
- The source tree contains committed build outputs and GTA-related data/native binaries. `app/build.gradle` also contains hard-coded signing values referencing a keystore path outside the source tree. Remove the signing values and use an isolated debug signing configuration in a working fork; never reuse or publish private signing credentials.

### Why the other option is not the first choice

Repository: https://github.com/noobcooder-sys/SA-MP-Mobile-Client-GTA-SA-2.10-

- The README describes a modified client and asks the builder to configure the server IP/port in `main.cpp`. That suggests a less ready-made generic server-browser path than the first candidate.
- The tree includes many `com.russia.game.gui` features and Russia-RP-specific panels. Retaining these would add unrelated complexity to a general-purpose SA-MP client.
- The README specifies JDK 21 and NDK 28; the manifest inspected on `main` still declares launcher and loader activities portrait, while gameplay is landscape.
- The branch currently has one initial commit, so the README's claims of stability/refactoring have not been confirmed by a visible history of incremental fixes in that branch.

## Permissions and source hygiene — release blocker

Neither inspected target branch surfaced a top-level project `LICENSE` file that clearly grants permission to modify and redistribute the full project. The first candidate's README says educational/research use; that statement is not a substitute for an explicit open-source license. The second README does not provide a clear project-wide reuse license.

Both trees contain GTA-related data/native binaries. Permission from an individual client author does not, by itself, grant redistribution rights for third-party game files. Before publishing a fork or downloadable APK, obtain appropriate permission/licensing for every reused component. Keep proprietary binaries/data out of OpenCEF and public CI artifacts. The permission previously mentioned for Alyn must not be assumed to apply to these unrelated repositories.

## Safe integration path

1. Keep OpenCEF and client source histories separate until the intended base is legally reusable.
2. Build the OpenCEF modules and adapter independently, without any game binaries/assets.
3. Create a working fork of the selected client only after appropriate permission is confirmed.
4. Integrate the `core`, `android`, and `host` modules; reconcile Gradle settings/version catalogs instead of overwriting the client's existing dependencies.
5. Add the local smoke-test page to the client's own assets and wire the overlay into its gameplay Activity.
6. Change all user-visible launcher flow activities to landscape and run a separate Android build/test on the device.
7. Do not claim success until a combined client APK compiles and has passed device checks.
