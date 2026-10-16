// Hold something, then drag it to a new place — the way apps are arranged on a phone's home
// screen. The one gesture behind board-drag.js (chore cards in a grid) and list-drag.js (lines
// on a shared list); each of those is a short configuration of this.
//
// One set of listeners on the document rather than one per element, for the same reason as
// list-swipe.js: the panels rebuild every card and row on each change in the home, and anything
// attached to one would be dropped mid-gesture. The DOM is only moved for the live preview; the
// drop dispatches the configured event on the container, the server writes the order and the
// push redraw makes it real on every phone.
//
// Pointer events for touch, pen and mouse alike. A HOLD of 450ms is what separates the three
// things a finger on an element can mean: a tap taps, a move within the hold scrolls (or swipes),
// a still hold lifts. Once lifted, touchmove is cancelled (non-passive) so the page stops panning
// under the drag; that listener exists only for the lifetime of a drag, so before the lift — and
// on every phone where nothing ever lifts — the browser scrolls as it always has.
//
// A config names:
//   item      selector for what can be lifted — must only match inside an enabled container
//             and must carry the id attribute (`data-<idAttr>`); siblings without the attribute
//             can be neither lifted nor landed on and mark the ends of the landable run
//   member    selector for any child of the container that takes part, with or without an id
//   ignore    selector for controls inside an item that keep their own gesture
//   container selector for what items are dropped into; may hold several per page (grids)
//   idAttr    the dataset key of an item's id ('taskId', 'itemId')
//   axis      'x' when the item under the pointer is split into left/right halves (a grid that
//             fills rows), 'y' for top/bottom halves (a vertical list)
//   event     the CustomEvent dispatched on the container at the drop
//   detail    (item, landing) => the event's detail; landing is { beforeId, container }
//   root      selector of the element the event bubbles from (the container's closest)

const HOLD = 450;        // ms a finger must stay still to lift
const SLOP = 8;          // px of travel within the hold that still counts as "still"
const EDGE = 60;         // px from the viewport edge where the page auto-scrolls
const SCROLL_STEP = 14;  // px per frame of auto-scroll

const configs = [];
let pending = null;      // { cfg, item, x0, y0, timer }
let drag = null;         // { cfg, item, ghost, x, y, dx, dy, raf, originBefore, ... }
let installed = false;

const itemAt = (target) => {
    for (const cfg of configs) {
        const item = target.closest?.(cfg.item);
        if (item && !target.closest(cfg.ignore)) {
            return { cfg, item };
        }
    }
    return null;
};

const hasId = (cfg, el) => el?.dataset?.[cfg.idAttr] !== undefined;

const clearPending = () => {
    if (pending) {
        clearTimeout(pending.timer);
        pending = null;
    }
};

// Where the lifted item would land right now: its next sibling that carries an id.
const landing = (cfg, item) => {
    let next = item.nextElementSibling;
    while (next && !hasId(cfg, next)) {
        next = next.nextElementSibling;
    }
    return { beforeId: next ? next.dataset[cfg.idAttr] : '', container: item.parentElement };
};

// Non-passive on purpose: while something is lifted the page must stop panning under it.
// Attached at lift and removed at finish, never left on the document: a non-passive touchmove
// listener makes every scroll wait for it, and most phones never drag.
const blockPan = (e) => e.preventDefault();

const lift = () => {
    const { cfg, item, x0, y0 } = pending;
    pending = null;
    navigator.vibrate?.(15);
    document.addEventListener('touchmove', blockPan, { passive: false });

    const rect = item.getBoundingClientRect();
    const ghost = item.cloneNode(true);
    ghost.classList.add('drag-ghost');
    ghost.classList.remove('lifting');
    delete ghost.dataset[cfg.idAttr]; // a picture of the item, never an item
    ghost.style.width = rect.width + 'px';
    ghost.style.height = rect.height + 'px';
    document.body.appendChild(ghost);

    item.classList.add('drag-placeholder');
    document.body.classList.add('hold-dragging');
    const origin = landing(cfg, item);
    drag = {
        cfg, item, ghost,
        // The ghost keeps the finger where it landed on the item, not the item's centre.
        dx: x0 - rect.left, dy: y0 - rect.top,
        x: x0, y: y0, raf: 0,
        originBefore: origin.beforeId, originContainer: origin.container,
        // To put the preview back if the drag is cancelled or lands where it started.
        originParent: item.parentElement, originNext: item.nextElementSibling,
    };
    position();
};

const position = () => {
    if (!drag) {
        return;
    }
    drag.ghost.style.transform = `translate(${drag.x - drag.dx}px, ${drag.y - drag.dy}px)`;
};

// Move the placeholder to wherever the pointer is. The item under the pointer decides: its
// near half means "before it", its far half "after it" — and when that item is the dragged
// item's own slot, nothing moves. (Measuring against the *nearest other* item instead made the
// slot wander in a grid: the moment the placeholder landed under the finger, the nearest other
// card was in the row above, and the slot climbed a row per frame.) Over a gap, the nearest item
// in that container stands in. An element without an id is the edge of the landable run: before
// the first id it means "the start", after the last one "the end" — the board's 🙋/＋ tiles, a
// list's lines pinned by their reminder.
const settle = () => {
    if (!drag) {
        return;
    }
    const { cfg, item, x, y } = drag;
    const under = document.elementsFromPoint(x, y);
    const container = under.find((el) => el.matches?.(cfg.container));
    if (!container) {
        return;
    }
    const members = [...container.children].filter((el) => el.matches(cfg.member));
    let over = under.find((el) => el.parentElement === container && el.matches(cfg.member));
    if (over === item) {
        return; // hovering its own slot
    }
    if (!over) {
        let bestDist = Infinity;
        for (const other of members) {
            if (other === item) {
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
        ref = null; // a container with nothing else in it
    } else if (!hasId(cfg, over)) {
        const withId = members.filter((el) => hasId(cfg, el) && el !== item);
        const first = withId[0];
        if (!first) {
            ref = members.find((el) => !hasId(cfg, el)); // only edges here: before the first
        } else if (over.compareDocumentPosition(first) & Node.DOCUMENT_POSITION_FOLLOWING) {
            ref = first; // an edge before the run: the start
        } else {
            ref = withId[withId.length - 1].nextElementSibling; // after the run: the end
            if (ref === item) {
                ref = item.nextElementSibling;
            }
        }
    } else {
        const r = over.getBoundingClientRect();
        const before = cfg.axis === 'x' ? x < r.left + r.width / 2 : y < r.top + r.height / 2;
        ref = before ? over : over.nextElementSibling;
    }
    if (ref === item || (ref === item.nextElementSibling && item.parentElement === container)) {
        return; // already exactly there
    }
    container.insertBefore(item, ref);
};

// Scroll the page while the finger rests near the top or bottom edge, and re-settle as things
// slide underneath it.
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
    const { cfg, item, ghost, originBefore, originContainer, originParent, originNext } = drag;
    cancelAnimationFrame(drag.raf);
    drag = null;
    document.removeEventListener('touchmove', blockPan);
    ghost.remove();
    item.classList.remove('drag-placeholder', 'lifting');
    document.body.classList.remove('hold-dragging');

    // The tap that ends a drag would land on the item — and complete the chore, or open the
    // line's edit dialog.
    const swallow = (ev) => {
        ev.stopPropagation();
        ev.preventDefault();
    };
    item.addEventListener('click', swallow, { capture: true, once: true });
    setTimeout(() => item.removeEventListener('click', swallow, { capture: true }), 400);

    const to = landing(cfg, item);
    if (!commit || (to.beforeId === originBefore && to.container === originContainer)) {
        // Cancelled, or let go where it was picked up: nothing to tell the server, and no
        // redraw coming, so undo the preview here.
        if (originParent?.isConnected && (!originNext || originNext.parentElement === originParent)) {
            originParent.insertBefore(item, originNext);
        }
        return;
    }
    const root = item.closest(cfg.root);
    root?.dispatchEvent(new CustomEvent(cfg.event, { bubbles: true, detail: cfg.detail(item, to) }));
};

const install = () => {
    if (installed) {
        return;
    }
    installed = true;

    document.addEventListener('pointerdown', (e) => {
        if (drag || !e.isPrimary) {
            return;
        }
        const hit = itemAt(e.target);
        if (!hit) {
            return;
        }
        clearPending();
        const { cfg, item } = hit;
        pending = {
            cfg, item, x0: e.clientX, y0: e.clientY,
            timer: setTimeout(() => {
                if (pending) {
                    lift();
                }
            }, HOLD),
        };
        // A hint that the hold is being noticed, a beat before it lifts: the item swells a
        // little, and settles back if the finger moves on or lets go.
        setTimeout(() => {
            if (pending?.item === item) {
                item.classList.add('lifting');
            }
        }, HOLD * 0.6);
    }, { passive: true });

    document.addEventListener('pointermove', (e) => {
        if (pending) {
            if (Math.abs(e.clientX - pending.x0) > SLOP || Math.abs(e.clientY - pending.y0) > SLOP) {
                pending.item.classList.remove('lifting');
                clearPending(); // a scroll, a swipe, or a sloppy tap: not a hold
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

    document.addEventListener('pointerup', () => {
        if (pending) {
            pending.item.classList.remove('lifting');
            clearPending(); // a tap: the item's own click listener takes it from here
            return;
        }
        finish(true);
    }, { passive: true });

    document.addEventListener('pointercancel', () => {
        if (pending) {
            pending.item.classList.remove('lifting');
            clearPending();
            return;
        }
        finish(false);
    }, { passive: true });

    // The long-press context menu (Android) and callout (iOS) would open over the hold.
    document.addEventListener('contextmenu', (e) => {
        if (itemAt(e.target)) {
            e.preventDefault();
        }
    });
};

/** Registers one kind of hold-and-drag. Safe to call more than once per kind. */
export function holdDrag(config) {
    if (configs.some((c) => c.event === config.event)) {
        return;
    }
    configs.push(config);
    install();
}
