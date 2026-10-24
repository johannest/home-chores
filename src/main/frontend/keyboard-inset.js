/*
 * How much of the bottom of the page the on-screen keyboard covers, as --keyboard-inset on <html>.
 *
 * Toasts open at the bottom (Toasts.java). iOS Safari, and Chrome on Android since 108, open the
 * keyboard over the page without resizing it, so a toast that answers Enter in a field — "No home
 * with code …" on the landing page — would open under the keys. The visual viewport is the part
 * still visible; what the layout viewport has below it is covered, and styles.css pads the
 * notification container by that much. A browser that resizes the page for the keyboard reports
 * nothing covered, and so does a desktop.
 *
 * The listeners are passive, and the property is written only when the number changes: the
 * visual viewport also fires while the page scrolls and the URL bar collapses, and a custom
 * property on <html> restyles the whole tree.
 */
(function () {
  const vv = window.visualViewport;
  if (!vv) {
    return;
  }
  let last = -1;
  const update = () => {
    const covered = Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop));
    if (covered !== last) {
      last = covered;
      document.documentElement.style.setProperty('--keyboard-inset', covered + 'px');
    }
  };
  vv.addEventListener('resize', update, { passive: true });
  vv.addEventListener('scroll', update, { passive: true });
  update();
})();
