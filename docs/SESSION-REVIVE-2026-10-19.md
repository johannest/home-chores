# Reviving an expired session faster — options (2026-10-19)

The complaint: a phone put down for a while comes back to a board that seems dead. The
first tap does nothing visible for a moment, then the whole app reloads, and the only
sign of life was Vaadin's 4px loading bar, hidden under the camera island. This note is
the second half of that pain point — the first half, a full-screen "Getting your chores…"
card from 1.5 s into any request, shipped alongside it (SPEC §4.1.2) — and lists what could
make the reload itself shorter or disappear. Nothing below is implemented; each item says
what it costs.

## What happens today

Sessions are deliberately short (SPEC §4.1.2: 5 min member, 20 min admin, measured from
the last real interaction because `vaadin.closeIdleSessions=true`). A pocketed phone
therefore comes back to a dead session as the normal case. The client has three ways of
noticing, and all three end in the same place — `SessionExpiryInitListener` turns Vaadin's
"Session expired" dialog into a plain redirect to `/`:

1. **The member taps something.** The `v-r=uidl` request is answered with Vaadin's
   session-expired JSON; the Flow client redirects to `/`.
2. **The overdue heartbeat fires.** Browsers freeze a hidden page's timers; when the phone
   wakes they fire at once. The heartbeat gets 403, `heartbeatInvalidStatusCode` calls
   `handleSessionExpiredError`, same redirect. So the reload usually starts on its own a
   moment after the phone wakes, before any tap.
3. **`HomeView.onVisible`.** The board already sends a server call on every
   `visibilitychange` to visible (for fired reminders). On an expired session that call is
   the tap in (1), so it has the same effect as (2), just sooner.

Then the reload itself, measured below:

4. `GET /` — the HTML, with the crawler text in `#outlet` as its first paint (a service
   worker precaches the bundle, but the HTML itself is always fetched).
5. The bundle (browser- or service-worker-cached after the first visit) and Lumo CSS.
6. `v-r=init` — the server builds `LandingView` behind its restore card and queues a
   JavaScript read of `localStorage`.
7. `v-r=uidl` with the answer — `restoreFrom` verifies the secret, signs the session in and
   `ui.navigate(HomeView)`; the board comes back in this response.
8. The push connection (`/VAADIN/push`), then the `onVisible` call and the time-zone
   `retrieveExtendedClientDetails` round trip, which redraws the header once more.

**Measured on the dev server** (localhost, Chrome in the browser pane, the dev bundle;
a member session left alone until it expired). The client noticed the expiry on its own:
the heartbeat that ran a minute past the 5-minute mark closed the session on its way out,
and the next long-poll reconnect got the session-expired reply, 46 s later at most — so a
*visible* idle board reloads itself within about a minute of expiring, with nobody tapping.
The reload it then performed, in milliseconds from the navigation start:

| ms | what |
|---:|---|
| 9 | `GET /` answered (22 KB of HTML) |
| 16–54 | bundle, Lumo, `styles.css`, push client — all from cache (`transferSize` 0) |
| 139–157 | `v-r=init`: 60 KB — the landing view behind its restore card, plus the `localStorage` read |
| 1001–1013 | `v-r=uidl` with the stored identity: 38 KB — `restoreFrom`, sign-in, the board |
| 1051–1094 | `v-r=uidl`: 82 KB — the board **again**, rebuilt by `buildChrome()` once the time zone arrived |
| 1120–1130 | two small `v-r=uidl` (the `onVisible` call, device re-stamp) |

Two things in that table are worth more than the totals. The 850 ms gap before the
first `uidl` is the browser throttling a *hidden* page's timers (the pane was in the
background; `v-pv=HIDDEN` in the init URL) — a phone in the hand would not pay it, but a
PWA resumed from the background may well. And the board is rendered **twice** on every
load: once in the restore response and once, twice the size, when
`retrieveExtendedClientDetails` delivers the time zone and `buildChrome()` runs again. On
localhost each server step is 10–45 ms; on a phone each of the four sequential round trips
(HTML, init, uidl, uidl) is a network RTT, and the bundle — 1.4 MB in production — is
parsed again.

So between the member's first tap and a usable board there are two sequential full server
round trips *after* the page load (6 and 7), plus the ones in 8, and the time from the
redirect to the new document's first paint is spent looking at the old, frozen board with
no indicator at all: Vaadin ends its loading state when the session-expired reply arrives,
and the new page has not painted yet.

## Options

Ordered by how much of the wait they remove per unit of work. The first three together
make the common case "the board refreshes itself as I pick the phone up".

### A. Restore the identity on the server, from a cookie — no landing page round trips

Store `memberId|homeCode|secret` in a cookie as well as in `localStorage` (`HttpOnly`,
`Secure`, `SameSite=Lax`, long `Max-Age`). A `SessionInitListener` — the shape
`LocaleInitListener` already has — reads it, verifies the secret exactly as
`LandingView.restoreFrom` does today, and signs the new session in before any view is
built. `LandingView.beforeEnter` then forwards straight to `HomeView`, so the board is in
the `v-r=init` response: step 7 disappears and step 6 renders the board instead of the
landing page. `localStorage` stays as the fallback (a cookie cleared or expired) and as
the thing that re-stamps the cookie.

- Removes: one full round trip plus the landing view's render, on every restore and every
  cold open of the app.
- Costs: the identity travels on every request (about 60 bytes); the privacy page's
  "single strictly-necessary session cookie" becomes two and must say so (SPEC §4.15);
  `DeviceIdentity.forget()` must clear the cookie too (a `Set-Cookie` from the server, not
  JavaScript — it is `HttpOnly`); the cookie must be refused when the secret does not
  verify, the same as the stored value is now. `DeviceIdentity`'s class comment and SPEC
  §4.1 / §5 "Identity storage" change with it.
- Risk: low. The secret's hash is already what the server trusts; the cookie only moves
  when it is presented.

### B. Paint the restore card first, from `index.html`

The gap between "redirect issued" and "new page painted" shows the frozen old board, and
the new page's own first paint is the crawler text in `#outlet` until Vaadin mounts. Put the
restore card's markup statically in `index.html` next to `#seo-content`, hidden by the same
`#outlet:has(> :not(#seo-content))` rule once the app mounts and by a `<noscript>` style for
visitors without JavaScript, with the text in the three languages picked by the `lang` cookie
`SeoIndexHtmlListener`-style on the server. The sequence then reads: frozen board → card
(static HTML, first paint) → card (`LandingView`) → board.

- Removes: the "nothing is happening" window between redirect and first paint, which is
  the whole of it on a slow connection with a cold bundle.
- Costs: the card exists in a third place (static HTML, `loading-overlay.js`, `LandingView`);
  with A in place the `LandingView` copy is no longer on the restore path and could go.
  Localising static HTML needs the server to patch it (there is precedent in
  `SeoIndexHtmlListener`).

### C. Keep a *visible* board's session alive

`closeIdleSessions=true` means a board left open and visible — a tablet on the fridge, a
member reading the stats for five minutes — expires and reloads itself every six minutes
or so (heartbeat 60 s past the 5-min lifetime). A small client interval that, only while
`document.visibilityState === 'visible'`, makes one real server call every few minutes
(a `@ClientCallable` no-op; `ServerRpcHandler.handleRpc` is what stamps
`lastRequestTimestamp`) keeps the session for as long as somebody is actually looking,
and still lets a pocketed or backgrounded phone expire — hidden pages get no interval.

- Removes: every reload of a board somebody is looking at. Does nothing for the pocketed
  phone, which is the common case on a phone.
- Costs: one tiny request per visible board per few minutes; SPEC §4.1.2 and US-48 must
  say "while nobody is looking at it" rather than "while nobody is using it".

### D. A longer member lifetime

Raising `SessionContext.MEMBER_TIMEOUT_SECONDS` from 5 to, say, 15 or 30 minutes makes
most pocket-and-return cycles land on a live session, with no reload at all. The cost is
exactly what US-48 was written to avoid: server memory for idle phones. The number to
decide this with is in production already — `vaadin_sessions_active` and
`vaadin_ui_state_nodes` on the Actuator endpoint (README, Observability) give the live
session count and the per-UI state size; multiply the typical per-session footprint by the
peak simultaneous phones and compare with the `-Xmx512m` headroom. A middle value (10 min)
covers "I put the phone down to do the chore" without changing the memory picture much.

- Removes: most expiries during active hours; nothing structural.
- Costs: memory per idle phone; a decision recorded in SPEC §4.1.2 and `application.properties`.

### E. Render the board once per load, not twice

Not about expiry, but it sits on the same path and halves the server work of every
restore: `HomeView.onAttach` renders the board from the signal effect, then
`retrieveExtendedClientDetails` arrives with the time zone and `buildChrome()` renders it
again (the second response is the larger one above, 82 KB). The time zone is what
availability windows ("dog out 8–10") need, and it is already persisted per member for
the reminder sweep (`reminderService.updateZone`) — so a session could start from the
member's *stored* zone and skip the second render whenever the browser then reports the
same one, which is every day but the day the family flies somewhere. With A in place the
zone can also come in on the first request: `v-r=init` carries `v-tzid` (see the init URL
above), readable server-side before anything is rendered.

- Removes: one of two full board renders per load, and the flicker between them.
- Costs: `SessionContext.timeZone()` needs a "from the member record" source; SPEC §4.1
  and §5 (time zones) say where the zone comes from.

### F. Start the reload before the tap

Already mostly true through the heartbeat and `onVisible` (2 and 3 above). One gap: a
page restored from the back-forward cache gets `pageshow` with `persisted = true`, not
`visibilitychange`, so the first sign of expiry there is the member's tap. Listening for
`pageshow` too in the same hook as `__fcVisible` closes it. Small.

### G. Not available: reviving without a page load

Flow can resynchronise a UI into a fresh HTTP session without reloading only in embedded
(web-component) mode — `SystemErrorHandler.#resynchronizeSession` in the client — and
there is no server-side state to reattach to anyway: an expired session's UI is gone.
A full page load is the floor; the options above make it short and visible rather than
avoid it.

## Recommendation

A, then B, then E, then C, as separate changes. A is the structural win and is what makes
the restored board arrive in the first server response; B makes the wait look like loading
from the first frame; E halves the server work on that path and removes a flicker; C is a
cheap fix for the one case the others do not touch. D is a knob to turn with the
production metrics in hand, not before.
