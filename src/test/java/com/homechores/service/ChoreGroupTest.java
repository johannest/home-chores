package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreGroup;
import com.homechores.domain.ChoreTask;
import com.homechores.domain.ChoreTaskRepository;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chore groups and board order.
 *
 * <p>The headline case is {@link #reorderingChores_doesNotChangeTodaysRotationAssignment}. The
 * daily rotation assigns member <em>m</em> the chore at index {@code (m + epochDay) mod n}, so
 * before this feature the board order <em>was</em> the rotation's index space. Splitting the two
 * ({@code tasksOf} vs {@code tasksInRotationOrder}) is what keeps arranging the board a layout
 * preference instead of a reassignment of the family's day, and this is the test that says so.
 */
@SpringBootTest
@Transactional
class ChoreGroupTest {

    @Autowired ChoreService service;
    @Autowired ChoreTaskRepository taskRepo;

    private static List<String> names(List<ChoreTask> tasks) {
        return tasks.stream().map(ChoreTask::getName).toList();
    }

    // ---- The invariant the ordering split exists to protect ------------------

    @Test
    void reorderingChores_doesNotChangeTodaysRotationAssignment() {
        Member alex = service.createHome("Rotators", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        Member robin = service.joinHome(code, "Robin").orElseThrow();
        Home home = service.findHome(code).orElseThrow();
        home.setDivisionStyle(DivisionStyle.ROTATING);
        service.saveHome(home);

        LocalDate today = LocalDate.now();
        List<Long> before = new ArrayList<>();
        for (Member m : List.of(alex, sam, robin)) {
            before.add(service.rotationAssignedChoreId(home, m.getId(), today));
        }

        // Rearrange the board as thoroughly as an admin can: groups, membership, and position.
        ChoreGroup kitchen = service.addGroup(code, "Kitchen", "🍳");
        ChoreGroup laundry = service.addGroup(code, "Laundry", "🧺");
        List<ChoreTask> all = service.tasksOf(code);
        service.setChoreGroup(all.get(0).getId(), laundry.getId());
        service.setChoreGroup(all.get(1).getId(), kitchen.getId());
        service.setChoreGroup(all.get(2).getId(), kitchen.getId());
        service.moveChore(all.get(2).getId(), -1);
        service.moveGroup(laundry.getId(), -1);

        assertFalse(names(service.tasksOf(code)).equals(names(
                        service.tasksInRotationOrder(code))),
                "the board order should actually have changed, or this test proves nothing");

        List<Long> after = new ArrayList<>();
        for (Member m : List.of(alex, sam, robin)) {
            after.add(service.rotationAssignedChoreId(home, m.getId(), today));
        }
        assertEquals(before, after, "rearranging the board must not reassign anyone's day");
    }

    @Test
    void enforcedRotation_stillBlocksTheSameChore_afterReordering() {
        Member alex = service.createHome("Rotators", "Alex");
        String code = alex.getHomeCode();
        service.joinHome(code, "Sam").orElseThrow();
        Home home = service.findHome(code).orElseThrow();
        home.setDivisionStyle(DivisionStyle.ROTATING);
        home.setRotationEnforced(true);
        service.saveHome(home);

        Long assigned = service.rotationAssignedChoreId(home, alex.getId(), LocalDate.now());
        Long notAssigned = service.tasksInRotationOrder(code).stream()
                .map(ChoreTask::getId).filter(id -> !id.equals(assigned)).findFirst().orElseThrow();

        ChoreGroup g = service.addGroup(code, "Kitchen", "🍳");
        service.setChoreGroup(notAssigned, g.getId());

        assertEquals(ChoreService.LockReason.NOT_ASSIGNED,
                service.complete(notAssigned, alex.getId()).blockReason(),
                "the gate must still name the same chore the badge does");
    }

    // ---- Ordering ------------------------------------------------------------

    @Test
    void aHomeWithNoGroups_keepsExactlyTheOrderItHad() {
        String code = service.createHome("Plain", "Alex").getHomeCode();
        List<String> creationOrder = names(service.tasksInRotationOrder(code));
        assertEquals(creationOrder, names(service.tasksOf(code)),
                "every chore predates the column at sortOrder 0, so createdAt must still order them");
    }

    @Test
    void tasksOf_ordersByGroupThenPosition_withUngroupedLast() {
        String code = service.createHome("Grouped", "Alex").getHomeCode();
        ChoreGroup kitchen = service.addGroup(code, "Kitchen", "🍳");
        ChoreGroup laundry = service.addGroup(code, "Laundry", "🧺");
        List<ChoreTask> all = service.tasksOf(code);
        String first = all.get(3).getName();
        String second = all.get(1).getName();
        String third = all.get(5).getName();
        service.setChoreGroup(all.get(3).getId(), kitchen.getId());
        service.setChoreGroup(all.get(1).getId(), kitchen.getId());
        service.setChoreGroup(all.get(5).getId(), laundry.getId());

        List<String> board = names(service.tasksOf(code));
        assertEquals(List.of(first, second, third), board.subList(0, 3),
                "grouped chores come first, group by group, in the order they were added");
        assertEquals(all.size() - 3, board.size() - 3);
    }

    @Test
    void moveChore_swapsWithinItsGroup_andCannotLeaveIt() {
        String code = service.createHome("Grouped", "Alex").getHomeCode();
        ChoreGroup kitchen = service.addGroup(code, "Kitchen", "🍳");
        List<ChoreTask> all = service.tasksOf(code);
        service.setChoreGroup(all.get(0).getId(), kitchen.getId());
        service.setChoreGroup(all.get(1).getId(), kitchen.getId());

        Long second = all.get(1).getId();
        assertTrue(service.moveChore(second, -1));
        assertEquals(second, service.tasksOf(code).get(0).getId());
        assertEquals(kitchen.getId(), taskRepo.findById(second).orElseThrow().getGroupId(),
                "moving within a group must never move a chore out of it");
    }

    @Test
    void movingTheEdgeChore_isANoOp() {
        String code = service.createHome("Plain", "Alex").getHomeCode();
        List<ChoreTask> all = service.tasksOf(code);
        assertFalse(service.moveChore(all.get(0).getId(), -1), "the first chore cannot move up");
        assertFalse(service.moveChore(all.get(all.size() - 1).getId(), 1),
                "the last chore cannot move down");
        assertEquals(names(all), names(service.tasksOf(code)), "and neither attempt reorders anything");
    }

    @Test
    void everyMove_leavesTheBucketDense() {
        String code = service.createHome("Plain", "Alex").getHomeCode();
        List<ChoreTask> all = service.tasksOf(code);
        service.moveChore(all.get(4).getId(), -1);
        List<ChoreTask> after = service.tasksOf(code);
        for (int i = 0; i < after.size(); i++) {
            assertEquals(i, after.get(i).getSortOrder(), "positions must be a dense 0..n-1");
        }
    }

    @Test
    void movingAChoreToAnotherGroup_appendsItToTheEnd() {
        String code = service.createHome("Grouped", "Alex").getHomeCode();
        ChoreGroup kitchen = service.addGroup(code, "Kitchen", "🍳");
        List<ChoreTask> all = service.tasksOf(code);
        service.setChoreGroup(all.get(0).getId(), kitchen.getId());
        service.setChoreGroup(all.get(1).getId(), kitchen.getId());
        Long late = all.get(7).getId();
        service.setChoreGroup(late, kitchen.getId());

        List<ChoreTask> board = service.tasksOf(code);
        assertEquals(late, board.get(2).getId(), "a chore joining a group lands at its end");
    }

    // ---- Groups --------------------------------------------------------------

    @Test
    void groups_areAddedRenamedAndReordered() {
        String code = service.createHome("Grouped", "Alex").getHomeCode();
        ChoreGroup kitchen = service.addGroup(code, "Kitchen", "🍳");
        ChoreGroup laundry = service.addGroup(code, "Laundry", "🧺");
        assertEquals(List.of("Kitchen", "Laundry"),
                service.groupsOf(code).stream().map(ChoreGroup::getName).toList());

        service.updateGroup(kitchen.getId(), "Cooking", "🍲");
        assertEquals("Cooking", service.findGroup(kitchen.getId()).orElseThrow().getName());

        assertTrue(service.moveGroup(laundry.getId(), -1));
        assertEquals(List.of("Laundry", "Cooking"),
                service.groupsOf(code).stream().map(ChoreGroup::getName).toList());
        assertFalse(service.moveGroup(laundry.getId(), -1), "already first");
    }

    @Test
    void deletingAGroup_keepsItsChores_asUngroupedAtTheBottom() {
        Member alex = service.createHome("Grouped", "Alex");
        String code = alex.getHomeCode();
        ChoreGroup kitchen = service.addGroup(code, "Kitchen", "🍳");
        ChoreTask moved = service.tasksOf(code).get(0);
        service.setChoreGroup(moved.getId(), kitchen.getId());
        service.complete(moved.getId(), alex.getId());
        int choreCount = service.tasksOf(code).size();

        service.deleteGroup(kitchen.getId());

        assertTrue(service.groupsOf(code).isEmpty());
        assertEquals(choreCount, service.tasksOf(code).size(), "a label is not a container");
        assertNull(taskRepo.findById(moved.getId()).orElseThrow().getGroupId());
        assertEquals(1, service.recentCompletions(code, 10).size(),
                "and its history survives with it");
    }

    @Test
    void aChoreWhoseGroupVanished_rendersAsUngrouped() {
        String code = service.createHome("Grouped", "Alex").getHomeCode();
        ChoreTask t = service.tasksOf(code).get(0);
        t.setGroupId(999_999L); // a dangling id no group ever had
        taskRepo.save(t);
        service.addGroup(code, "Kitchen", "🍳");

        List<ChoreTask> board = service.tasksOf(code);
        assertTrue(board.stream().anyMatch(x -> x.getId().equals(t.getId())),
                "a dangling groupId must never make a chore disappear from the board");
        assertEquals(t.getId(), board.get(0).getId(),
                "it falls into the ungrouped bucket, which is the whole board here — the one real"
                + " group is empty, so nothing precedes it");
    }

    @Test
    void aGroupFromAnotherHome_isRefused() {
        String mine = service.createHome("Mine", "Alex").getHomeCode();
        String theirs = service.createHome("Theirs", "Sam").getHomeCode();
        ChoreGroup foreign = service.addGroup(theirs, "Kitchen", "🍳");

        ChoreTask t = service.tasksOf(mine).get(0);
        service.setChoreGroup(t.getId(), foreign.getId());

        assertNull(taskRepo.findById(t.getId()).orElseThrow().getGroupId(),
                "a chore must never join another family's group");
    }

    // ---- placeChore: where a drag drops a card ------------------------------

    @Test
    void placingAChoreBeforeAnother_movesItThere_andKeepsTheBucketDense() {
        String code = service.createHome("Plain", "Alex").getHomeCode();
        List<ChoreTask> all = service.tasksOf(code);
        Long fifth = all.get(4).getId();
        Long first = all.get(0).getId();

        assertTrue(service.placeChore(fifth, null, first));

        List<ChoreTask> after = service.tasksOf(code);
        assertEquals(fifth, after.get(0).getId(), "dropped before the first card");
        assertEquals(first, after.get(1).getId());
        List<String> expected = new ArrayList<>(names(all));
        expected.add(0, expected.remove(4));
        assertEquals(expected, names(after), "everything else keeps its relative order");
        for (int i = 0; i < after.size(); i++) {
            assertEquals(i, after.get(i).getSortOrder(), "positions must be a dense 0..n-1");
        }
    }

    @Test
    void placingAChoreWithNoNeighbour_appendsItToThatSection() {
        String code = service.createHome("Plain", "Alex").getHomeCode();
        List<ChoreTask> all = service.tasksOf(code);
        Long first = all.get(0).getId();

        assertTrue(service.placeChore(first, null, null));

        List<ChoreTask> after = service.tasksOf(code);
        assertEquals(first, after.get(after.size() - 1).getId(), "now last");
        assertEquals(names(all).subList(1, all.size()), names(after).subList(0, all.size() - 1));
    }

    @Test
    void placingAChoreNextToACardInAnotherGroup_movesItIntoThatGroup() {
        String code = service.createHome("Grouped", "Alex").getHomeCode();
        ChoreGroup kitchen = service.addGroup(code, "Kitchen", "🍳");
        List<ChoreTask> all = service.tasksOf(code);
        Long a = all.get(0).getId();
        Long b = all.get(1).getId();
        service.setChoreGroup(a, kitchen.getId());
        service.setChoreGroup(b, kitchen.getId());
        Long late = all.get(7).getId();

        // Dropped before b: the neighbour's section wins, whatever the caller said about the group.
        assertTrue(service.placeChore(late, null, b));

        assertEquals(kitchen.getId(), taskRepo.findById(late).orElseThrow().getGroupId());
        List<ChoreTask> after = service.tasksOf(code);
        assertEquals(List.of(a, late, b),
                after.subList(0, 3).stream().map(ChoreTask::getId).toList(),
                "the kitchen section leads the board: a, then the dropped card, then b");
        assertEquals(List.of(0, 1, 2),
                after.subList(0, 3).stream().map(ChoreTask::getSortOrder).toList());
    }

    @Test
    void placingAChoreAtTheEndOfAnEmptyGroup_putsItThere() {
        String code = service.createHome("Grouped", "Alex").getHomeCode();
        ChoreGroup laundry = service.addGroup(code, "Laundry", "🧺");
        List<ChoreTask> all = service.tasksOf(code);
        Long moved = all.get(3).getId();

        assertTrue(service.placeChore(moved, laundry.getId(), null));

        ChoreTask t = taskRepo.findById(moved).orElseThrow();
        assertEquals(laundry.getId(), t.getGroupId());
        assertEquals(0, t.getSortOrder(), "first and only in its new section");
        assertEquals(moved, service.tasksOf(code).get(0).getId(), "and the section leads the board");
    }

    @Test
    void droppingACardWhereItWasPickedUp_changesNothing() {
        String code = service.createHome("Plain", "Alex").getHomeCode();
        List<ChoreTask> all = service.tasksOf(code);
        Long second = all.get(1).getId();
        Long third = all.get(2).getId();

        assertFalse(service.placeChore(second, null, third), "before its own next card = same slot");
        assertFalse(service.placeChore(second, null, second), "before itself");
        assertFalse(service.placeChore(all.get(all.size() - 1).getId(), null, null),
                "the last card to the end");
        assertEquals(names(all), names(service.tasksOf(code)));
    }

    @Test
    void placingNextToAnotherHomesCard_isRefused() {
        String code = service.createHome("Ours", "Alex").getHomeCode();
        String theirs = service.createHome("Theirs", "Sam").getHomeCode();
        List<ChoreTask> ours = service.tasksOf(code);
        Long stranger = service.tasksOf(theirs).get(0).getId();

        assertFalse(service.placeChore(ours.get(3).getId(), null, stranger));
        assertEquals(names(ours), names(service.tasksOf(code)), "nothing moved");
        assertNull(taskRepo.findById(ours.get(3).getId()).orElseThrow().getGroupId());
    }

    /** The drag is one more way to arrange the board, so it gets the headline invariant too. */
    @Test
    void draggingChoresAround_doesNotChangeTodaysRotationAssignment() {
        Member alex = service.createHome("Rotators", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        Home home = service.findHome(code).orElseThrow();
        home.setDivisionStyle(DivisionStyle.ROTATING);
        service.saveHome(home);
        LocalDate today = LocalDate.now();
        List<Long> before = List.of(
                service.rotationAssignedChoreId(home, alex.getId(), today),
                service.rotationAssignedChoreId(home, sam.getId(), today));

        ChoreGroup kitchen = service.addGroup(code, "Kitchen", "🍳");
        List<ChoreTask> all = service.tasksOf(code);
        service.placeChore(all.get(5).getId(), kitchen.getId(), null);
        service.placeChore(all.get(0).getId(), null, all.get(5).getId());
        service.placeChore(all.get(9).getId(), null, all.get(1).getId());
        assertFalse(names(service.tasksOf(code)).equals(names(service.tasksInRotationOrder(code))),
                "the board order should actually have changed, or this test proves nothing");

        assertEquals(before, List.of(
                service.rotationAssignedChoreId(home, alex.getId(), today),
                service.rotationAssignedChoreId(home, sam.getId(), today)),
                "dragging cards about must not reassign anyone's day");
    }

    /** A new chore joins the end of its section even after the section has been renumbered. */
    @Test
    void aChoreAddedAfterAReorder_stillLandsLast() {
        String code = service.createHome("Plain", "Alex").getHomeCode();
        List<ChoreTask> all = service.tasksOf(code);
        service.placeChore(all.get(4).getId(), null, all.get(0).getId()); // renumbers 0..10

        ChoreTask oven = service.addTask(code, "Clean the oven", "🧽");

        List<ChoreTask> after = service.tasksOf(code);
        assertEquals(oven.getId(), after.get(after.size() - 1).getId(), "new chores go last");
        assertEquals(all.size(), oven.getSortOrder(), "appended after a dense 0..n-1");
    }
}
