## BrightPasses v1.18 — the shutter takes one answer

**Three fast taps no longer freeze the camera into a black screen.** The capture button looked
single-shot, but it kept firing until the screen actually left — and every extra tap compressed a
full-resolution photograph on the interface thread and started another reading of it. Three of
those at once is more memory than this phone has, and running out inside the camera is the black
preview that never came back without a force-stop. The second tap now does nothing, instead of
starting a second full-size encode on the UI thread.

**A failed capture says so and lets you try again.** A shot that cannot be taken or saved now
costs that shot: the app offers to report what went wrong, the shutter wakes back up, and the
preview stays live — instead of silently killing the camera.

**Photo processing happens one at a time, off the interface thread.** Saving a photograph and
reading the ticket in it each run on their own single lane, so nothing heavy ever sits between
your finger and the next preview frame — and leaving the camera now releases it properly, rather
than leaving it bound to a screen that no longer exists.
