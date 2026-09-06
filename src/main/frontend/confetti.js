// Lightweight self-contained confetti — no external dependencies.
// Exposed on window so it can be triggered from the server via Page.executeJs.
(function () {
  function fire(intensity) {
    // 'fire' (3+ chores in one day) trades a smaller opening burst for side cannons and
    // a delayed second volley — more spectacle, similar total particle budget to 'big'.
    const count =
      intensity === 'big' ? 220 : intensity === 'fire' ? 120 : intensity === 'medium' ? 130 : 80;
    const canvas = document.createElement('canvas');
    // The popover resets (margin/padding/border/background/overflow) undo the UA styles
    // that a promoted popover would otherwise get; harmless when the fallback path runs.
    canvas.style.cssText =
      'position:fixed;inset:0;width:100%;height:100%;pointer-events:none;z-index:99999;' +
      'margin:0;padding:0;border:0;background:transparent;overflow:hidden';
    document.body.appendChild(canvas);
    // Vaadin dialogs live in the browser's native top layer, which paints above any
    // z-index — promote the canvas there too, or the confetti bursts behind the dialog.
    const promote = () => {
      if (typeof canvas.showPopover !== 'function') return;
      try {
        if (canvas.matches(':popover-open')) canvas.hidePopover();
        canvas.showPopover();
      } catch (e) {
        /* fall back to plain z-index stacking */
      }
    };
    canvas.popover = 'manual';
    promote();
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    canvas.width = window.innerWidth * dpr;
    canvas.height = window.innerHeight * dpr;
    ctx.scale(dpr, dpr);
    const W = window.innerWidth;
    const H = window.innerHeight;
    const colors = ['#10b981', '#0ea5e9', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899'];
    const parts = [];
    // A burst of n squares around (cx, cy); a non-zero vxBase aims it sideways (cannons).
    function spawn(cx, cy, n, vxBase) {
      for (let i = 0; i < n; i++) {
        parts.push({
          x: cx + (Math.random() - 0.5) * 120,
          y: cy + (Math.random() - 0.5) * 60,
          vx: vxBase + (Math.random() - 0.5) * 14,
          vy: Math.random() * -15 - 4,
          size: Math.random() * 8 + 4,
          color: colors[(Math.random() * colors.length) | 0],
          rot: Math.random() * Math.PI,
          vr: (Math.random() - 0.5) * 0.4,
          life: 1,
        });
      }
    }
    spawn(W / 2, H / 3, count, 0);
    if (intensity === 'fire') {
      spawn(0, H * 0.6, 60, 9); // left cannon, aimed inward and up
      spawn(W, H * 0.6, 60, -9); // right cannon
    }
    let frames = 0;
    function step() {
      frames++;
      // The celebration dialog enters the top layer from a Lit microtask, i.e. possibly
      // after this canvas — and top-layer order is promotion order. Re-promoting for the
      // first ~half second keeps the confetti above it (Vaadin's own bringToFront pattern).
      if (frames <= 30) promote();
      if (intensity === 'fire' && frames === 25) spawn(W / 2, H / 3, 80, 0); // second volley
      ctx.clearRect(0, 0, W, H);
      let alive = false;
      for (const p of parts) {
        p.vy += 0.42; // gravity
        p.vx *= 0.99;
        p.x += p.vx;
        p.y += p.vy;
        p.rot += p.vr;
        if (frames > 60) p.life -= 0.02;
        if (p.life > 0 && p.y < H + 40) {
          alive = true;
          ctx.save();
          ctx.globalAlpha = Math.max(0, p.life);
          ctx.translate(p.x, p.y);
          ctx.rotate(p.rot);
          ctx.fillStyle = p.color;
          ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size * 0.6);
          ctx.restore();
        }
      }
      if (alive && frames < 260) {
        requestAnimationFrame(step);
      } else {
        canvas.remove();
      }
    }
    requestAnimationFrame(step);
  }

  window.fireConfetti = fire;
})();
