# BrightPasses

Movie ticket stubs on the Light Phone III. Photograph a stub, and BrightPasses reads the
title, theater, date, time, seat and price off the paper with Claude Haiku, then keeps
the ticket in a local collection sorted by showtime, moving it to an archive after the
film ends. LightOS shows the tool as **Movie Tickets**. Package `com.gios.lightpass`.
Current release: **v1.9.0**.

## Install via BrightMarket

<p align="center">
  <img src="https://gi-os.github.io/brightmarket-index/assets/qr/BrightPasses.png" alt="Scan to open BrightPasses in BrightMarket" width="180" />
</p>

Scan the code above with **BrightMarket** installed to open BrightPasses there and
install or update it directly. Don't have BrightMarket yet? Get it, and browse
every Bright app, at
**[gi-os.github.io/brightmarket-index/browse.html](https://gi-os.github.io/brightmarket-index/browse.html)**.

**This repo is also the shared skeleton.** Every other Light Phone III app in this
collection that needs a camera — BrightNotebook, BrightNews's QR scanner, LightPods'
sideload packaging — copies its `app/` module structure, its Compose/Material3 theme,
its Akkurat-from-`SystemFonts` font loader, its keystore-and-CI signing setup, or its
QR-key-entry flow from here. Treat changes to `ui/theme/`, the signing workflow, or the
QR scanner contract as changes to the template, not just to this app: they get
hand-copied downstream, not pulled by any dependency mechanism.

## Quick start

```sh
git clone https://github.com/gi-os/BrightPasses.git
cd BrightPasses
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
  booking code, a confidence score, and a bounding box. BrightPasses crops to that box and
  keeps the full photo behind a zoomable viewer.
- **A date with no year on it is read as upcoming.** Stubs print "DEC 18" and leave the
  year off, so the model is told to return `MM-DD` in that case rather than guess, and
  `util/TicketDate.kt` picks the next occurrence of that date — this year if it hasn't
  passed, next year if it has, with a two-day grace window so a late showing photographed
  after midnight doesn't jump eleven months forward. A year actually printed on the ticket
  is kept; a year the model appears to have invented (2024, on a ticket photographed in
  2026) is not. `app/src/test/kotlin` covers the rule.
- **A scannable code sits at the foot of the ticket**, generated from the booking
  reference with ZXing — Code 128 for a short alphanumeric reference, QR once it grows or
  picks up punctuation, PDF417 for an airline-length string. Tapping it fills the screen
  on white and pins the brightness up while it's there. It is labelled as generated,
  because it only scans if the cinema encoded that same string; the real barcode is in the
  photo, which stays one tap away. The booking code is editable, since one wrong character
  makes the generated code useless.
- A film search list lets you correct the poster/title match if the parser picked the
  wrong one.
- Every field stays editable; **SAVE** in the top bar commits a change.
- Tickets sort by showtime; dates render as "August 6th"; theater names are
  title-cased.
- The wheel scrolls the ticket list, the detail page, edit fields and film search
  results, and pans the stub photo or the enlarged booking code once you've pinched in —
  the only way to read the small print at the bottom of a stub. It arrives as an ordinary key event
  (`WHEEL_CCW`/`WHEEL_CW`, claimed in `dispatchKeyEvent` ahead of the view hierarchy);
  [BrightControl](https://github.com/gi-os/BrightControl) is the separate, optional app
  that owns the wheel click and the camera button phone-wide.
- Room stores the collection on-device; there is no account and no server.

## Build

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The unit tests are plain JUnit over `util/TicketDate.kt` and `util/BookingCode.kt`, which
import nothing from `android.*` for exactly that reason — no Robolectric, no device, and
the date rule gets checked on every push.

CI (`.github/workflows/build.yml`) tests and builds every branch, and publishes only from
`main`, where it also creates or updates a GitHub Release tagged `v${versionName}` —
**the tag does not change between pushes that don't bump `versionName`**, so an unrelated
commit (docs, a provider addition) silently replaces the APK inside the existing release
rather than creating a new one. Obtainium sees nothing new in that case even though CI went
green. Bump `versionName` and `versionCode` in `app/build.gradle.kts` whenever the change
should actually reach users as an update.

## Contributing

Pull requests welcome, especially fixes shared with the downstream copies (BrightNotebook,
BrightNews, LightPods): if you fix something in `ui/theme/`, the QR key-entry flow, or the
CI signing steps, consider whether the same fix applies to those repos too, since there
is no shared package to pull it from automatically. `LightFont.kt`'s
`akkuratFamilyOrDefault()` pattern (pull Akkurat out of `android.graphics.fonts.SystemFonts`
rather than bundling a font file) is the one piece most worth keeping identical across
repos, since it's what makes every sideloaded app match LightOS's own chrome.

## The code on the ticket

The barcode at the foot of a pass is **decoded from the photograph**, not generated from the text a
model read. That distinction is the whole feature. A code re-encoded from a booking reference scans
only if the cinema's system looks that reference up, and most encode an internal id instead — so the
first version of this looked right and did nothing. The real code was in the photo the whole time.

Decoding a photograph of a code is much harder than a viewfinder aimed at one, so it is attempted
across four axes and stops at the first two-dimensional hit:

- **Scale** — 1600, 1000, 2400 and 640 px on the long edge. A 12MP still of a code an inch across
  gives the binarizer a module a few pixels wide surrounded by grain; downscaling averages the grain
  out, and past a point takes the code with it.
- **Quarter turn** — ZXing's 1D readers scan horizontal rows and `RGBLuminanceSource` doesn't
  implement rotation, so a barcode printed down the side of a ticket is invisible until the bitmap
  itself is turned.
- **Binarizer** — `HybridBinarizer` for uneven lighting (most phone photos), `GlobalHistogramBinarizer`
  for a flat screenshot its local blocks over-think. They fail on different pictures.
- **Inversion** — a ticket held up on someone else's phone in dark mode is a white code on black,
  which ZXing will not find at all.

The crop is tried before the whole frame (less paper, fewer false edges), QR / PDF417 / Aztec /
Data Matrix / Code 128 / Code 39 / ITF are all in the hint set, and a scan is bounded to six seconds
so a ticket with no code doesn't cost the same as one with. Where two codes are found — tickets often
carry a QR for the app and a linear code for the usher's handheld — the 2D one wins, then the longer
payload. A short 1D read is discarded outright: perforations and table rules will occasionally give
up a six-character Code 39, while a 2D symbology's checksums make a false positive effectively
impossible.

The **symbology is stored alongside the payload** and the code is redrawn as itself. A PDF417 payload
re-encoded as a QR carries the same characters and is a different code as far as a scanner is
concerned. Verified by round-tripping all seven formats through ZXing — encode, decode, compare — and
that test also caught ZXing not honouring the size you ask for: a PDF417 requested at 910x340 comes
back 778x188, and drawing it at the requested size would resample the bars and defeat the point of
generating at device pixels.

Built on `com.google.zxing:core` the way [LightQR](https://github.com/gi-os/LightQR) does it, for the
reason that app's own comment gives: pure Java, and LightOS has no Play Services, so ML Kit's barcode
scanner would compile, install, and silently never work.

## Version history

| Version | Commit | Change |
| --- | --- | --- |
| v1.9.0 | (this release) | **The barcode is now read off the photograph** rather than re-encoded from the parsed reference, so what's on screen is the cinema's own payload in the cinema's own symbology. Seven symbologies attempted over four scales, two rotations, two binarizers and an inverted pass; tickets already on the shelf are backfilled in the background. Real Room migration, so a schema change no longer empties the shelf |
| v1.8.0 | `c7fdbb8` | Year-less dates resolve to the next occurrence in Kotlin instead of being guessed by the model; scannable booking code at the foot of the ticket, with a brightness boost and an editable code field |
| v1.7.0 | `80f0fa1` | Scroll the ticket list, detail page and search results with the hardware wheel |
| v1.6.2 | `95a588c` | Bump to 1.6.2 for the provider release (ships the `033c0cf` shelf-provider commit) |
| v1.6.1 | `d9b8e2c` | Bump to 1.6.1 so the launcher icon ships as a new release (app previously had no `android:icon`) |
| v1.6.0 | `78e67f5` | QR-scanned keys now persist and show in Settings (no clobber); remove white launch flash |
| v1.5.0 | `1650de1` | Auto-release on push (build + publish GitHub Release), committed signing key for stable Obtainium updates |
| v0.3   | `be0762c` | Sort by showtime, Archive tab, human dates, Title-Case theaters, rename to Movie Tickets, instant capture |
| beta2  | `c125a6b` | Bounding-box ticket crop + zoomable original, NDPass-style detail page, optional TMDb, auto-archive, all-black UI |
| Beta   | `8962712` | Switch to a standalone Android app: camera + album import, QR key scan, NDPass parser, full-color viewer |

Since v1.6.2, BrightPasses also exposes its ticket shelf to other tools: `PassProvider` at
authority `com.gios.lightpass.passes`, exported but answering only an allowlisted set of
calling packages (title/theater/seat/day/start/end only — never the photo or barcode).
[BrightNotebook](https://github.com/gi-os/BrightNotebook) is the current consumer, folding
a matching ticket into its calendar entries and opening `lightpass://pass/<id>` on tap.

## Origin and credits

- [gi-os/NDPass](https://github.com/gi-os/NDPass) is the iOS ticket collector this app
  grew out of; BrightPasses reuses its ticket schema and detail-page layout.
- The grayscale-off trick on the detail page is `util/Grayscale.kt`, a small
  implementation against `Settings.Secure` of the idea seen in
  [vandamd/zero](https://github.com/vandamd/zero) (MIT) and shipped natively in
  [garado/light-topographic](https://github.com/garado/light-topographic).
- CameraX, Jetpack Compose, Room and OkHttp do the rest. Uses the Anthropic API to parse
  stubs and the TMDb API for posters (this product uses the TMDb API but is not endorsed
  or certified by TMDb).

## License

MIT. See [LICENSE](LICENSE).
