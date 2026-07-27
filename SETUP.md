# LightPass — pass/ticket holder for Light Phone III

Stores pass images locally, shows the **original** full-screen for scanning, and
uses your **own Anthropic API key** (Claude Vision) to auto-title them.

## Your setup (≈4 steps, no Android SDK on your machine)

1. Create an empty repo on your GitHub (e.g. `gi-os/LightPass`) and push these files:
   ```
   git init && git add . && git commit -m "LightPass v1"
   git branch -M main
   git remote add origin https://github.com/gi-os/LightPass.git
   git push -u origin main
   ```

2. In the repo: **Settings → Secrets and variables → Actions → New repository secret**, add two:
   - `GH_PACKAGES_USER` = `gi-os`
   - `GH_PACKAGES_TOKEN` = a GitHub PAT with **`read:packages`** scope
   (This is only to pull Light's `light-keyboard` package. Token stays in GitHub, never in code.)

3. The push triggers **Actions → Build LightPass APK**. When it's green, open the run and
   download the artifact **`lightpass-debug-apk`** → `tool-debug.apk`.

4. Light dashboard → **Developer Mode** → upload `tool-debug.apk`. It appears in your toolbox as **Passes**.
   (Self-built = "dangerous sideload" warning; accept it.)

## Using it
- **API key:** open Passes → Settings (gear) → paste your Anthropic key → check. Stored locally on the phone.
- **Add a pass:** drop `.jpg/.png` files into the phone's app folder over USB:
  the tool.'s import inbox (see note) then → open Passes → tap **+**.
  Each image is copied to permanent storage, parsed by Claude, and titled.
- **Show a pass:** tap it → original image full-screen. Turn brightness up (side wheel) for scanning.

## Notes
- No key set? Imports still save with a placeholder title; re-import later once the key is set.
- Screen renders monochrome outside the camera/album — fine for QR/barcodes.
- Cost: ~a fraction of a cent per pass on Haiku.
