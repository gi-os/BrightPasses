# LightPass

Movie ticket stubs on the Light Phone III. Photograph a stub, and LightPass reads the
title, theater, date, time, seat and price off the paper with Claude Haiku, then keeps
the ticket in a local collection sorted by showtime, moving it to an archive after the
film ends. LightOS shows the tool as **Movie Tickets**. Package `com.gios.lightpass`.
Current release: **v1.7.0**.

**This repo is also the shared skeleton.** Every other Light Phone III app in this
collection that needs a camera — LightNotebook, LightRSS's QR scanner, LightPods'
sideload packaging — copies its `app/` module structure, its Compose/Material3 theme,
its Akkurat-from-`SystemFonts` font loader, its keystore-and-CI signing setup, or its
QR-key-entry flow from here. Treat changes to `ui/theme/`, the signing workflow, or the
QR scanner contract as changes to the template, not just to this app: they get
hand-copied downstream, not pulled by any dependency mechanism.

## Quick start

```sh
git clone https://github.com/gi-os/LightPass.git
cd LightPass
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

JDK 17 and the Android SDK (`compileSdk` 35, `minSdk` 29). The repo commits a debug
keystore so every build carries the same signing certificate and Obtainium (or a plain
`adb install -r`) can update in place instead of failing with `Failure: Invalid`.

That build is a working app with no further setup: capture a stub with the camera or
import one from the album, and the ticket fields stay editable even with no API key —
you'd just be typing them in by hand instead of having Claude read them. Add the
Anthropic key (below) to skip the typing; add a TMDb key to get posters.

## Configuration

- **Anthropic key (parses the stub).** Settings → paste it, or scan a QR code built by
  [docs/index.html](docs/index.html) (runs entirely in the browser, sends nothing
  anywhere).
- **TMDb key (optional, posters only).** Same entry path. Without it, ticket detail
  pages show no poster and film search is unavailable; parsing and everything else
  still works.
- **Color permission**, so the ticket detail page can turn off system grayscale while a
  stub is open:

  ```sh
  adb shell pm grant com.gios.lightpass android.permission.WRITE_SECURE_SETTINGS
  ```

  Without it the stubs stay gray; nothing else changes. The phone returns to grayscale
  automatically when you leave the detail page.

## Usage

- Capture or import → Claude Haiku returns title, theater, date, time, seat, price, a
  confidence score, and a bounding box. LightPass crops to that box and keeps the full
  photo behind a zoomable viewer.
- A film search list lets you correct the poster/title match if the parser picked the
  wrong one.
- Every field stays editable; **SAVE** in the top bar commits a change.
- Tickets sort by showtime; dates render as "August 6th"; theater names are
  title-cased.
- The wheel scrolls the ticket list, the detail page, edit fields and film search
  results, and pans the stub photo once you've pinched in — the only way to read the
  small print at the bottom of a stub. It arrives as an ordinary key event
  (`WHEEL_CCW`/`WHEEL_CW`, claimed in `dispatchKeyEvent` ahead of the view hierarchy);
  [LightControl](https://github.com/gi-os/LightControl) is the separate, optional app
  that owns the wheel click and the camera button phone-wide.
- Room stores the collection on-device; there is no account and no server.

## Build

```sh
./gradlew :app:assembleDebug
```

CI (`.github/workflows/build.yml`) builds the debug APK on every push to `main` and
publishes/updates a GitHub Release tagged `v${versionName}` — **the tag does not change
between pushes that don't bump `versionName`**, so an unrelated commit (docs, a
provider addition) silently replaces the APK inside the existing release rather than
creating a new one. Obtainium sees nothing new in that case even though CI went green.
Bump `versionName` and `versionCode` in `app/build.gradle.kts` whenever the change
should actually reach users as an update.

## Contributing

Pull requests welcome, especially fixes shared with the downstream copies (LightNotebook,
LightRSS, LightPods): if you fix something in `ui/theme/`, the QR key-entry flow, or the
CI signing steps, consider whether the same fix applies to those repos too, since there
is no shared package to pull it from automatically. `LightFont.kt`'s
`akkuratFamilyOrDefault()` pattern (pull Akkurat out of `android.graphics.fonts.SystemFonts`
rather than bundling a font file) is the one piece most worth keeping identical across
repos, since it's what makes every sideloaded app match LightOS's own chrome.

## Version history

| Version | Commit | Change |
| --- | --- | --- |
| v1.7.0 | `80f0fa1` | Scroll the ticket list, detail page and search results with the hardware wheel |
| v1.6.2 | `95a588c` | Bump to 1.6.2 for the provider release (ships the `033c0cf` shelf-provider commit) |
| v1.6.1 | `d9b8e2c` | Bump to 1.6.1 so the launcher icon ships as a new release (app previously had no `android:icon`) |
| v1.6.0 | `78e67f5` | QR-scanned keys now persist and show in Settings (no clobber); remove white launch flash |
| v1.5.0 | `1650de1` | Auto-release on push (build + publish GitHub Release), committed signing key for stable Obtainium updates |
| v0.3   | `be0762c` | Sort by showtime, Archive tab, human dates, Title-Case theaters, rename to Movie Tickets, instant capture |
| beta2  | `c125a6b` | Bounding-box ticket crop + zoomable original, NDPass-style detail page, optional TMDb, auto-archive, all-black UI |
| Beta   | `8962712` | Switch to a standalone Android app: camera + album import, QR key scan, NDPass parser, full-color viewer |

Since v1.6.2, LightPass also exposes its ticket shelf to other tools: `PassProvider` at
authority `com.gios.lightpass.passes`, exported but answering only an allowlisted set of
calling packages (title/theater/seat/day/start/end only — never the photo or barcode).
[LightNotebook](https://github.com/gi-os/LightNotebook) is the current consumer, folding
a matching ticket into its calendar entries and opening `lightpass://pass/<id>` on tap.

## Origin and credits

- [gi-os/NDPass](https://github.com/gi-os/NDPass) is the iOS ticket collector this app
  grew out of; LightPass reuses its ticket schema and detail-page layout.
- The grayscale-off trick on the detail page is `util/Grayscale.kt`, a small
  implementation against `Settings.Secure` of the idea seen in
  [vandamd/zero](https://github.com/vandamd/zero) (MIT) and shipped natively in
  [garado/light-topographic](https://github.com/garado/light-topographic).
- CameraX, Jetpack Compose, Room and OkHttp do the rest. Uses the Anthropic API to parse
  stubs and the TMDb API for posters (this product uses the TMDb API but is not endorsed
  or certified by TMDb).

## License

MIT. See [LICENSE](LICENSE).
