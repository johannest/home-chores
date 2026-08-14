// Lightweight self-contained confetti — no external dependencies.
// Exposed on window so it can be triggered from the server via Page.executeJs.
(function () {
  function fire(intensity) {
    const count = intensity === 'big' ? 220 : intensity === 'medium' ? 130 : 80;
    const canvas = document.createElement('canvas');
    canvas.style.cssText =
      'position:fixed;inset:0;width:100%;height:100%;pointer-events:none;z-index:99999';
    document.body.appendChild(canvas);
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    canvas.width = window.innerWidth * dpr;
    canvas.height = window.innerHeight * dpr;
    ctx.scale(dpr, dpr);
    const W = window.innerWidth;
    const H = window.innerHeight;
    const colors = ['#10b981', '#0ea5e9', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899'];
    const parts = [];
    for (let i = 0; i < count; i++) {
      parts.push({
        x: W / 2 + (Math.random() - 0.5) * 120,
        y: H / 3 + (Math.random() - 0.5) * 60,
        vx: (Math.random() - 0.5) * 14,
        vy: Math.random() * -15 - 4,
        size: Math.random() * 8 + 4,
        color: colors[(Math.random() * colors.length) | 0],
        rot: Math.random() * Math.PI,
        vr: (Math.random() - 0.5) * 0.4,
        life: 1,
      });
    }
    let frames = 0;
    function step() {
      frames++;
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
