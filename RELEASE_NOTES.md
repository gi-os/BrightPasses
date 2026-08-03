## MovieTickets v1.10 — Shake to report, and the release pipeline stops leaking

**One feature and two build fixes, one of which was releasing from every branch.**

### Every push was cutting a release

The build workflow was triggered by a bare `push:` with no branch filter, so any push to any
branch built and published a release — a scratch branch, an experiment, a half-finished idea went
straight to the phone through Obtainium. It is `branches: [main]` now, with docs and CI-only
changes ignored, and a separate check workflow that compiles and tests every other branch without
publishing anything.

### The signing certificate is pinned

Android identifies an app by (package name, signing certificate). If the cert ever drifts,
Obtainium updates fail with an opaque `Failure: Invalid` and the only way back is uninstalling and
losing your stubs. This shipped broken in LightFastread for eleven builds before anyone noticed.
`signing-fingerprint.txt` now holds the expected SHA-256 and the build fails if what came out does
not match it.

### Shake the phone to report a bug

Shake twice and a sheet comes up. Pick what happened from five chips and add a note in your own
words — optional, but it is the part that carries anything, and what you type becomes the title of
the issue. The report brings the screen you were on, app and firmware versions, free space, heap,
and the stack trace if the app died the last time you had it open. Reports queue on disk before
anything is sent, so a report survives the crash that prompted it.
