// Hold a chore card, then drag it to a new place — the way apps are arranged on a phone's home
// screen. Admin only: the server adds .draggable to .board-sections for admins and nothing else,
// and stamps each real chore card with data-task-id (the 🙋 and ＋ tiles have none, so they can
// neither be lifted nor landed on).
//
// One set of listeners on the document rather than one per card, for the same reason as
// list-swipe.js: ChoresPanel rebuilds every card on each change in the home, and anything attached
// to a card would be dropped mid-gesture. The DOM is only moved for the live preview; the drop
// dispatches a 'board-place' event on .board-sections, the server writes the order and the push
// redraw makes it real on every phone.
//
// Pointer events for touch, pen and mouse alike. A HOLD of 450ms is what separates the three
// things a finger on a card can mean: a tap completes, a move within the hold scrolls, a still
// hold lifts. Once lifted, touchmove is cancelled (non-passive) so the page stops panning under
// the drag; before that the browser scrolls as it always has.
(() => {
    if (window.__boardDrag) {
        return;
    }
    window.__boardDrag = true;

    const HOLD = 450;        // ms a finger must stay still to lift a card
    const SLOP = 8;          // px of travel within the hold that still counts as "still"
    const EDGE = 60;         // px from the viewport edge where the page auto-scrolls
    const SCROLL_STEP = 14;  // px per frame of auto-scroll

    let pending = null;      // { card, x0, y0, timer, pointerId }
    let drag = null;         // { card, ghost, x, y, dx, dy, raf, beforeId, groupId }

    const cardAt = (target) => {
        const card = target.closest?.('.board-sections.draggable .task-card[data-task-id]');
        if (!card || target.closest('.snooze-chip, .book-btn, vaadin-button')) {
            return null;
        }
        return card;
    };

    const clearPending = () => {
        if (pending) {
            clearTimeout(pending.timer);
            pending = null;
        }
    };

    // Where the lifted card would land right now: its next sibling that is a real chore card.
    const landing = (card) => {
        let next = card.nextElementSibling;
        while (next && !next.dataset.taskId) {
            next = next.nextElementSibling;
        }
        return {
            beforeId: next ? next.dataset.taskId : '',
            groupId: card.parentElement?.dataset.groupId || '',
        };
    };

    const lift = () => {
        const { card, x0, y0 } = pending;
        pending = null;
        navigator.vibrate?.(15);

        const rect = card.getBoundingClientRect();
        const ghost = card.cloneNode(true);
        ghost.classList.add('drag-ghost');
        ghost.classList.remove('lifting');
        ghost.removeAttribute('data-task-id'); // a picture of the card, never a card
        ghost.style.width = rect.width + 'px';
        ghost.style.height = rect.height + 'px';
        document.body.appendChild(ghost);

        card.classList.add('drag-placeholder');
        document.body.classList.add('board-dragging');
        const origin = landing(card);
        drag = {
            card, ghost,
            // The ghost keeps the finger where it landed on the card, not the card's centre.
            dx: x0 - rect.left, dy: y0 - rect.top,
            x: x0, y: y0, raf: 0,
            originBefore: origin.beforeId, originGroup: origin.groupId,
            // To put the preview back if the drag is cancelled or lands where it started.
            originParent: card.parentElement, originNext: card.nextElementSibling,
        };
        position();
    };

    const position = () => {
        if (!drag) {
            return;
        }
        drag.ghost.style.transform = `translate(${drag.x - drag.dx}px, ${drag.y - drag.dy}px)`;
    };

    // Move the placeholder to wherever the pointer is. The card under the pointer decides: its
    // left half means "before it", its right half "after it" — and when that card is the dragged
    // card's own slot, nothing moves. (Measuring against the *nearest other* card instead made
    // the slot wander: the moment the placeholder landed under the finger, the nearest other card
    // was in the row above, and the slot climbed a row per frame.) Over a gap, the nearest card
    // in that grid stands in. An extra tile (🙋/＋) means "the end of the chores".
    const settle = () => {
        if (!drag) {
            return;
        }
        const { card, x, y } = drag;
        const under = document.elementsFromPoint(x, y);
        const grid = under.find((el) => el.classList?.contains('task-grid')
                && el.closest('.board-sections.draggable'));
        if (!grid) {
            return;
        }
        let over = under.find((el) => el.classList?.contains('task-card') && el.parentElement === grid);
        if (over === card) {
            return; // hovering its own slot
        }
        if (!over) {
            let bestDist = Infinity;
            for (const other of grid.querySelectorAll('.task-card')) {
                if (other === card) {
                    continue;
                }
                const r = other.getBoundingClientRect();
                const cx = r.left + r.width / 2;
                const cy = r.top + r.height / 2;
                const d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
                if (d < bestDist) {
                    bestDist = d;
                    over = other;
                }
            }
        }
        let ref;
        if (!over) {
            ref = null; // a grid with nothing else in it
        } else if (!over.dataset.taskId) {
            ref = grid.querySelector('.task-card:not([data-task-id])'); // before the first extra
        } else {
            const r = over.getBoundingClientRect();
            const before = x < r.left + r.width / 2;
            ref = before ? over : over.nextElementSibling;
        }
        if (ref === card || (ref === card.nextElementSibling && card.parentElement === grid)) {
            return; // already exactly there
        }
        grid.insertBefore(card, ref);
    };

    // Scroll the page while the finger rests near the top or bottom edge, and re-settle as the
    // cards slide underneath it.
    const autoScroll = () => {
        if (!drag) {
            return;
        }
        const h = window.innerHeight;
        if (drag.y < EDGE) {
            window.scrollBy(0, -SCROLL_STEP);
            settle();
        } else if (drag.y > h - EDGE) {
            window.scrollBy(0, SCROLL_STEP);
            settle();
        }
        drag.raf = requestAnimationFrame(autoScroll);
    };

    const finish = (commit) => {
        if (!drag) {
            return;
        }
        const { card, ghost, originBefore, originGroup, originParent, originNext } = drag;
        cancelAnimationFrame(drag.raf);
        drag = null;
        ghost.remove();
        card.classList.remove('drag-placeholder', 'lifting');
        document.body.classList.remove('board-dragging');

        // The tap that ends a drag would land on the complete-area and log the chore.
        const swallow = (ev) => {
            ev.stopPropagation();
            ev.preventDefault();
        };
        card.addEventListener('click', swallow, { capture: true, once: true });
        setTimeout(() => card.removeEventListener('click', swallow, { capture: true }), 400);

        const to = landing(card);
        if (!commit || (to.beforeId === originBefore && to.groupId === originGroup)) {
            // Cancelled, or let go where it was picked up: nothing to tell the server, and no
            // redraw coming, so undo the preview here.
            if (originParent?.isConnected && (!originNext || originNext.parentElement === originParent)) {
                originParent.insertBefore(card, originNext);
            }
            return;
        }
        const board = card.closest('.board-sections');
        board?.dispatchEvent(new CustomEvent('board-place', {
            bubbles: true,
            detail: { taskId: card.dataset.taskId, groupId: to.groupId, beforeTaskId: to.beforeId },
        }));
    };

    document.addEventListener('pointerdown', (e) => {
        if (drag || !e.isPrimary) {
            return;
        }
        const card = cardAt(e.target);
        if (!card) {
            return;
        }
        clearPending();
        pending = {
            card, x0: e.clientX, y0: e.clientY, pointerId: e.pointerId,
            timer: setTimeout(() => {
                if (pending) {
                    lift();
                }
            }, HOLD),
        };
        // A hint that the hold is being noticed, a beat before it lifts: the card swells a
        // little, and settles back if the finger moves on or lets go.
        setTimeout(() => {
            if (pending?.card === card) {
                card.classList.add('lifting');
            }
        }, HOLD * 0.6);
    }, { passive: true });

    document.addEventListener('pointermove', (e) => {
        if (pending) {
            if (Math.abs(e.clientX - pending.x0) > SLOP || Math.abs(e.clientY - pending.y0) > SLOP) {
                pending.card.classList.remove('lifting');
                clearPending(); // a scroll, or a sloppy tap: not a hold
            }
            return;
        }
        if (!drag) {
            return;
        }
        drag.x = e.clientX;
        drag.y = e.clientY;
        position();
        // The slot follows the pointer here, on every move, not in the frame loop: a frame loop
        // pauses whenever the page is not being painted, and the preview must never lag the
        // finger. The loop below is only for scrolling the page when the finger rests at an edge.
        settle();
        if (!drag.raf) {
            drag.raf = requestAnimationFrame(autoScroll);
        }
    }, { passive: true });

    // Non-passive on purpose: once a card is lifted the page must stop panning under it. Before
    // the lift this does nothing, so scrolling the board stays native.
    document.addEventListener('touchmove', (e) => {
        if (drag) {
            e.preventDefault();
        }
    }, { passive: false });

    document.addEventListener('pointerup', () => {
        if (pending) {
            pending.card.classList.remove('lifting');
            clearPending(); // a tap: the card's own click listener takes it from here
            return;
        }
        finish(true);
    }, { passive: true });

    document.addEventListener('pointercancel', () => {
        if (pending) {
            pending.card.classList.remove('lifting');
            clearPending();
            return;
        }
        finish(false);
    }, { passive: true });

    // The long-press context menu (Android) and callout (iOS) would open over the hold.
    document.addEventListener('contextmenu', (e) => {
        if (cardAt(e.target)) {
            e.preventDefault();
        }
    });
})();
