/*
 * The "Getting your chores…" card for the moments the server is slow to answer.
 *
 * Vaadin's own loading indicator is a 4px bar along the very top of the viewport. On a phone
 * that is under the camera island, so a member whose tap is taking seconds — a session that
 * expired in their pocket, a radio still waking up — sees nothing change, taps again, and
 * decides the app is broken. From the indicator's second stage on (1.5 s, the one number in
 * LoadingOverlayInitListener) this card covers the page instead. It is the landing page's
 * restore card built from the same classes (.restore-overlay, .brand-title, .brand-sub), so a
 * wait that ends in a reload flows into the card the landing page shows without a seam.
 *
 * The card is a manual popover, which puts it in the browser's top layer: a Vaadin dialog left
 * open (the "How was it?" question after a chore, say) lives there too and would otherwise sit
 * on top of it. The observer below follows the bar inside vaadin-connection-indicator — the
 * element Vaadin itself uses the same way — and steps aside while the connection itself is
 * down, when Vaadin's own "Connection lost" message is the honest one. The text is filled in
 * by the server in the UI's language (LoadingOverlayInitListener), because UI strings live in
 * the message bundles, not here.
 */
(function () {
  if (document.getElementById('loading-overlay')) {
    return;
  }
  const overlay = document.createElement('div');
  overlay.id = 'loading-overlay';
  overlay.className = 'restore-overlay loading-overlay';
  overlay.setAttribute('popover', 'manual');

  // Decorative: the same brand line the landing page draws, hidden from assistive tech so the
  // status text below is the one thing announced.
  const brand = document.createElement('div');
  brand.className = 'brand-title';
  brand.setAttribute('aria-hidden', 'true');
  const icon = document.createElement('img');
  icon.src = '/icons/icon.svg';
  icon.alt = '';
  const name = document.createElement('span');
  name.textContent = 'FlashChores';
  brand.append(icon, name);

  // An attribute rather than a property, so it holds whether or not the element is upgraded yet.
  const bar = document.createElement('vaadin-progress-bar');
  bar.setAttribute('indeterminate', '');

  const text = document.createElement('p');
  text.id = 'loading-overlay-text';
  text.className = 'brand-sub';
  text.setAttribute('role', 'status');

  overlay.append(brand, bar, text);
  document.body.appendChild(overlay);

  function follow(indicator) {
    const sync = () => {
      const stage = indicator.querySelector('.v-loading-indicator');
      const waiting = !!stage && (stage.classList.contains('second') || stage.classList.contains('third'));
      const down = indicator.hasAttribute('reconnecting') || indicator.hasAttribute('offline');
      const show = waiting && !down && overlay.isConnected;
      if (show !== overlay.matches(':popover-open')) {
        show ? overlay.showPopover() : overlay.hidePopover();
      }
    };
    new MutationObserver(sync).observe(indicator, {
      attributes: true, subtree: true, attributeFilter: ['class', 'reconnecting', 'offline']
    });
    sync();
  }

  // The Flow client adds its indicator to <body> after this module has run.
  const indicator = document.querySelector('body > vaadin-connection-indicator');
  if (indicator) {
    follow(indicator);
  } else {
    const arrival = new MutationObserver(() => {
      const found = document.querySelector('body > vaadin-connection-indicator');
      if (found) {
        arrival.disconnect();
        follow(found);
      }
    });
    arrival.observe(document.body, { childList: true });
  }
})();
