## BrightPasses v1.11 — Three tickets, one event, and not everything is a movie

**Tickets to the same showing now live together, and a ticket can be a game or a concert.**

### Same event, one shelf entry

Photograph three QR codes for the same screening and the second and third recognise the first:
same title (ignoring case and punctuation), same date, no disagreement on time — they join the
same event instead of piling up as three lookalike rows. On the shelf the entry reads
"3 tickets"; on the event page a pager steps through **TICKET 1 OF 3**, and the seat, the code,
and the photo all follow it — everyone at the door shows their own barcode.

There is also a way to say it outright: **ADD TICKET TO THIS EVENT** on any pass opens the
camera and attaches the photo directly, no matching required. A ticket added that way inherits
whatever its own photo failed to say — title, venue, showtime, and the movie match — so the
picker never asks about the same film twice. Grouping is retroactive in the practical sense
too: add a ticket to a months-old pass and the pass becomes a group on the spot.

LightNotebook sees one calendar entry per event, with the seats joined ("B12, B14"), instead
of three copies of the same film.

### Sports and concerts are tickets too

A Knicks ticket used to come back "not a ticket". The parser now reads movie, sports, and
concert tickets alike and classifies as it parses; a game gets its matchup as the title and
its arena as the venue. On the event page the type sits next to the title — tap **MOVIE** and
pick **SPORTS** or **CONCERT** instead, on any pass, however old, and the whole group switches
with it. A non-movie pass drops the TMDb plumbing: no picker, no poster, no synopsis, and the
field that said THEATER says VENUE.

Existing rows migrate untouched: everything already on the shelf is a movie ticket in no
group, which is exactly what it was.

---

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
