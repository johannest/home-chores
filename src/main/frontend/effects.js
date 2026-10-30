// Sound and vibration for the moments the board already marks: a chore done (scaled like the
// confetti), a tap the rules refused, a list line or routine ticked, a held card lifting.
// Exposed on window so the server can trigger it via Page.executeJs, like confetti.js.
//
// Both are per-device choices in localStorage, written by EffectsItems: sound is off until a
// member switches it on (a kitchen tablet that chimes all day wears thin), vibration is on.
// Nothing here talks to the server, and no audio file is fetched: the tones are synthesised.
(function () {
  const SOUND_KEY = 'flashchores.sound';
  const VIBRATION_KEY = 'flashchores.vibration';

  // Where localStorage throws (some private modes) a choice still lasts as long as the page.
  const unstored = {};
  const enabled = (key, fallback) => {
    try {
      const v = localStorage.getItem(key);
      if (v !== null) return v === 'on';
    } catch (e) {
      /* private mode */
    }
    return key in unstored ? unstored[key] : fallback;
  };
  const soundOn = () => enabled(SOUND_KEY, false);
  const vibrationOn = () => enabled(VIBRATION_KEY, true);

  // Milliseconds of buzz (and pause). Short on purpose: a tick, not a ring.
  const PATTERNS = {
    tick: 10,
    lift: 15,
    small: 20,
    medium: [20, 60, 20],
    fire: [20, 60, 20, 60, 40],
    big: [30, 60, 30, 60, 60],
    blocked: [40, 80, 40],
  };

  // [frequency Hz, start s] blips: rising notes for praise, a falling pair for "not now".
  const C6 = 1047, E6 = 1319, G6 = 1568, C7 = 2093;
  const TUNES = {
    tick: [[G6, 0]],
    small: [[C6, 0], [E6, 0.08]],
    medium: [[C6, 0], [E6, 0.08], [G6, 0.16]],
    fire: [[C6, 0], [E6, 0.07], [G6, 0.14], [C7, 0.21], [G6, 0.3], [C7, 0.37]],
    big: [[C6, 0], [E6, 0.08], [G6, 0.16], [C7, 0.24], [C7, 0.4]],
    blocked: [[392, 0], [311, 0.13]],
  };
  const VOLUME = 0.12;
  const NOTE_SECONDS = 0.18;

  let ctx = null;

  const context = () => {
    if (ctx) return ctx;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    // Safari: 'ambient' mixes with the member's music instead of pausing it, and stays quiet
    // when the phone's ring/silent switch is on — what a little chime should do.
    try {
      if (navigator.audioSession) navigator.audioSession.type = 'ambient';
    } catch (e) {
      /* older Safari */
    }
    ctx = new AC();
    return ctx;
  };

  // Browsers start audio suspended until a user gesture, and iOS only lets a gesture handler
  // resume it — but our sounds arrive after a server round trip, outside the tap. So every tap
  // (while sound is on) makes sure the context is running, and the chime that follows can play.
  // Passive and cheap: it never blocks scrolling, and after the first tap it is a state check.
  const unlock = () => {
    if (!soundOn()) return;
    const c = context();
    if (c && c.state !== 'running') c.resume().catch(() => {});
  };
  for (const type of ['touchend', 'click', 'keydown']) {
    document.addEventListener(type, unlock, { capture: true, passive: true });
  }

  const play = (notes) => {
    const c = context();
    if (!c) return;
    if (c.state !== 'running') {
      // Still waking from the tap that asked for it. A context that only wakes on some later
      // tap must not play a stale chime then, hence the deadline.
      const asked = Date.now();
      c.resume()
        .then(() => Date.now() - asked < 500 && schedule(c, notes))
        .catch(() => {});
      return;
    }
    schedule(c, notes);
  };

  const schedule = (c, notes) => {
    const t0 = c.currentTime + 0.01;
    for (const [freq, at] of notes) {
      const osc = c.createOscillator();
      const gain = c.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;
      const start = t0 + at;
      // A 10 ms attack and an exponential tail: no click at either end of the blip.
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(VOLUME, start + 0.01);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + NOTE_SECONDS);
      osc.connect(gain).connect(c.destination);
      osc.start(start);
      osc.stop(start + NOTE_SECONDS + 0.02);
    }
  };

  window.playEffect = function (kind) {
    if (vibrationOn() && navigator.vibrate) navigator.vibrate(PATTERNS[kind] || 0);
    if (soundOn() && TUNES[kind]) play(TUNES[kind]);
  };

  // A switch in the header menu: store the choice and, when one was switched on, let the member
  // hear or feel what they chose.
  window.__setEffect = function (which, on) {
    const key = which === 'sound' ? SOUND_KEY : VIBRATION_KEY;
    unstored[key] = on;
    try {
      localStorage.setItem(key, on ? 'on' : 'off');
    } catch (e) {
      /* private mode */
    }
    if (!on) return;
    if (which === 'sound') {
      play(TUNES.small);
    } else if (navigator.vibrate) {
      navigator.vibrate(PATTERNS.small);
    }
  };
})();
