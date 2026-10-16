// Hold a chore card, then drag it to a new place (hold-drag.js has the gesture). Admin only:
// the server adds .draggable to .board-sections for admins and nothing else, and stamps each
// real chore card with data-task-id (the 🙋 and ＋ tiles have none, so they can neither be
// lifted nor landed on). The drop dispatches 'board-place' on .board-sections with the dragged
// id, the grid's group and the next chore card's id (blank = the section's end); ChoresPanel
// takes it from there.
import { holdDrag } from './hold-drag.js';

holdDrag({
    item: '.board-sections.draggable .task-card[data-task-id]',
    member: '.task-card',
    ignore: '.snooze-chip, .book-btn, vaadin-button',
    container: '.board-sections.draggable .task-grid',
    idAttr: 'taskId',
    axis: 'x',
    root: '.board-sections',
    event: 'board-place',
    detail: (card, to) => ({
        taskId: card.dataset.taskId,
        groupId: to.container?.dataset.groupId || '',
        beforeTaskId: to.beforeId,
    }),
});
