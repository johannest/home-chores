// Hold a line on a shared list, then drag it to a new place (hold-drag.js has the gesture).
// For everyone in the home: a shared list is shared, and whoever is at the store arranges it.
// The server stamps data-item-id on the lines whose place is the family's to choose; a line with
// a reminder is pinned by its due time instead and carries no id, so it can be neither lifted nor
// landed on. The drop dispatches 'list-place' on .shared-list with the dragged id and the next
// arrangeable line's id (blank = the end); ListPanel takes it from there.
//
// Coexists with list-swipe.js on the same rows: a finger that moves within the hold is a swipe
// or a scroll and never lifts, and once a line is lifted the swipe script stands down.
import { holdDrag } from './hold-drag.js';

holdDrag({
    item: '.shared-list.draggable .list-item[data-item-id]',
    member: '.list-item',
    ignore: 'vaadin-checkbox, vaadin-button, .list-reminder',
    container: '.shared-list.draggable',
    idAttr: 'itemId',
    axis: 'y',
    root: '.shared-list',
    event: 'list-place',
    detail: (row, to) => ({ itemId: row.dataset.itemId, beforeItemId: to.beforeId }),
});
