package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.Member;
import com.homechores.service.ChoreService.RejoinResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * An admin adding a member who has no phone — a child, usually. They are a member like any
 * other, except that no device can sign in as them until they get one.
 */
@SpringBootTest
@Transactional
class AddMemberTest {

    @Autowired
    ChoreService service;

    @Test
    void addsAMemberWithNoDevice_whoCountsLikeAnyOther() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();

        Member kid = service.addMemberWithoutDevice(code, "  Milla ", alex.getId()).orElseThrow();

        assertEquals("Milla", kid.getName(), "trimmed");
        assertFalse(kid.isAdmin());
        assertFalse(kid.hasDevice());
        assertNotNull(kid.getTermsAcceptedAt(), "the adult accepted on their behalf");
        assertEquals(2, service.membersOf(code).size());
        assertTrue(service.completeFor(service.tasksOf(code).get(0).getId(), kid.getId(),
                alex.getId()).allowed(), "and an admin can log a chore for them");
    }

    @Test
    void refusesABlankOrTakenNickname_becauseSignBackInFindsPeopleByIt() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();

        assertTrue(service.addMemberWithoutDevice(code, "   ", alex.getId()).isEmpty());
        assertTrue(service.addMemberWithoutDevice(code, "alex", alex.getId()).isEmpty(),
                "case-insensitive, like the nickname match itself");
        assertEquals(1, service.membersOf(code).size());
    }

    @Test
    void onlyAnAdminOfThatHome_canAdd() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        Member other = service.createHome("Elsewhere", "Kim");

        assertThrows(IllegalArgumentException.class,
                () -> service.addMemberWithoutDevice(code, "Milla", sam.getId()), "a member");
        assertThrows(IllegalArgumentException.class,
                () -> service.addMemberWithoutDevice(code, "Milla", other.getId()),
                "another home's admin");
        assertEquals(2, service.membersOf(code).size());
    }

    /** The day they get a phone, the ordinary sign-back-in by nickname is their door in. */
    @Test
    void laterGetsAPhone_throughSignBackInByNickname() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member kid = service.addMemberWithoutDevice(code, "Milla", alex.getId()).orElseThrow();
        String pin = service.findHome(code).orElseThrow().getAdminPin();

        Optional<Member> found = service.findMemberByName(code, "milla");
        assertEquals(kid.getId(), found.orElseThrow().getId());
        assertEquals(RejoinResult.SIGNED_IN,
                service.requestRejoin(code, kid.getId(), pin).result());
        String secret = service.issueDeviceSecret(kid.getId());
        assertTrue(service.verifyDeviceSecret(kid.getId(), secret));
        assertTrue(service.findMember(kid.getId()).orElseThrow().hasDevice());
    }
}
