// Swipe a shared-list line left to uncover Edit and Delete, the way a chat app does.
//
// One set of listeners on the document rather than one per row: ListPanel rebuilds every row on
// each change in the home, so anything attached to a row would be dropped mid-list. The server
// renders each line as .list-item > (.swipe-actions, .swipe-face); this only moves the face. The
// actions themselves are ordinary Vaadin buttons in the tray, so nothing here talks to the server.
//
// Touch and pen only. A mouse drag on desktop selects text as it always has, and the face keeps
// its own bell and trash buttons for anyone who never swipes.
(() => {
    if (window.__listSwipe) {
        return;
    }
    window.__listSwipe = true;

    const LOCK = 10;      // px of travel before deciding between a swipe and a scroll
    let drag = null;      // { row, face, x0, y0, base, width, dx, axis }
    let openRow = null;

    const trayWidth = (row) => row.querySelector('.swipe-actions')?.offsetWidth || 0;

    const setOffset = (face, dx) => {
        face.style.transform = dx ? `translateX(${dx}px)` : '';
    };

    const close = (row) => {
        const face = row?.querySelector('.swipe-face');
        if (face) {
            setOffset(face, 0);
        }
        row?.classList.remove('swipe-open');
        if (openRow === row) {
            openRow = null;
        }
    };

    const open = (row) => {
        if (openRow && openRow !== row) {
            close(openRow);
        }
        setOffset(row.querySelector('.swipe-face'), -trayWidth(row));
        row.classList.add('swipe-open');
        openRow = row;
    };

    document.addEventListener('pointerdown', (e) => {
        if (e.pointerType === 'mouse') {
            return;
        }
        const face = e.target.closest?.('.list-item .swipe-face');
        // A tap anywhere but the open row's tray closes it.
        if (openRow && !e.target.closest?.('.swipe-actions')
                && (!face || face.parentElement !== openRow)) {
            close(openRow);
        }
        // Controls on the face keep their own gestures: ticking, the bell, the trash.
        if (!face || e.target.closest('vaadin-checkbox, vaadin-button')) {
            return;
        }
        const row = face.parentElement;
        drag = {
            row, face, x0: e.clientX, y0: e.clientY,
            base: row.classList.contains('swipe-open') ? -trayWidth(row) : 0,
            width: trayWidth(row), dx: 0, axis: null,
        };
    }, { passive: true });

    document.addEventListener('pointermove', (e) => {
        if (!drag) {
            return;
        }
        const mx = e.clientX - drag.x0;
        const my = e.clientY - drag.y0;
        if (!drag.axis) {
            if (Math.abs(mx) < LOCK && Math.abs(my) < LOCK) {
                return;
            }
            drag.axis = Math.abs(mx) > Math.abs(my) ? 'x' : 'y';
            if (drag.axis === 'x') {
                drag.row.classList.add('swiping');
            }
        }
        if (drag.axis !== 'x') {
            return; // a scroll: let the page have it
        }
        // Left only, and no further than the tray; a little give past it reads as elastic.
        const raw = drag.base + mx;
        drag.dx = Math.min(0, Math.max(-drag.width - 16, raw));
        setOffset(drag.face, drag.dx);
    }, { passive: true });

    const end = () => {
        if (!drag) {
            return;
        }
        const { row, axis, dx, width } = drag;
        drag = null;
        row.classList.remove('swiping');
        if (axis !== 'x') {
            return;
        }
        // The tap that follows a swipe would land on the face's text and open the edit dialog.
        const swallow = (ev) => {
            ev.stopPropagation();
            ev.preventDefault();
        };
        row.addEventListener('click', swallow, { capture: true, once: true });
        setTimeout(() => row.removeEventListener('click', swallow, { capture: true }), 400);
        if (-dx > width / 2) {
            open(row);
        } else {
            close(row);
        }
    };
    document.addEventListener('pointerup', end, { passive: true });
    document.addEventListener('pointercancel', end, { passive: true });

    // An action in the tray was chosen: fold the row back so the next render starts closed.
    document.addEventListener('click', (e) => {
        const tray = e.target.closest?.('.swipe-actions');
        if (tray) {
            close(tray.parentElement);
        }
    });
})();
