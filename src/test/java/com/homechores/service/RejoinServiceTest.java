package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.RejoinRequest;
import com.homechores.domain.RejoinStatus;
import com.homechores.service.ChoreService.Rejoin;
import com.homechores.service.ChoreService.RejoinResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Signing back in as an existing member after a device cleared its browser storage. */
@SpringBootTest
@Transactional
class RejoinServiceTest {

    @Autowired
    ChoreService service;

    private String wrongPin(Home home) {
        return "0000".equals(home.getAdminPin()) ? "1111" : "0000";
    }

    @Test
    void newHome_gatesRejoinsByDefault() {
        Member admin = service.createHome("Nest", "Alex");
        assertTrue(service.findHome(admin.getHomeCode()).orElseThrow().isApproveRejoin());
    }

    @Test
    void gateOff_signsInImmediately() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();
        home.setApproveRejoin(false);
        service.saveHome(home);

        Rejoin r = service.requestRejoin(home.getCode(), sam.getId(), null);
        assertEquals(RejoinResult.SIGNED_IN, r.result());
        assertNull(r.token());
        assertTrue(service.pendingRejoins(home.getCode()).isEmpty(), "no request needed");
    }

    @Test
    void gateOn_raisesPendingRequest() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();

        Rejoin r = service.requestRejoin(admin.getHomeCode(), sam.getId(), null);
        assertEquals(RejoinResult.PENDING, r.result());
        assertNotNull(r.token());
        assertEquals(1, service.pendingRejoins(admin.getHomeCode()).size());
        assertEquals(1, service.pendingRejoinCount(admin.getHomeCode()));
    }

    @Test
    void correctPin_skipsTheGate_withoutGrantingAdmin() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();

        Rejoin r = service.requestRejoin(home.getCode(), sam.getId(), home.getAdminPin());
        assertEquals(RejoinResult.SIGNED_IN, r.result());
        assertTrue(service.pendingRejoins(home.getCode()).isEmpty());
        assertFalse(service.findMember(sam.getId()).orElseThrow().isAdmin(),
                "the PIN opens the gate but is not itself a promotion");
    }

    @Test
    void admin_getsBackInWithTheirPin_keepingTheirExistingRole() {
        Member admin = service.createHome("Nest", "Alex");
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();

        Rejoin r = service.requestRejoin(home.getCode(), admin.getId(), home.getAdminPin());
        assertEquals(RejoinResult.SIGNED_IN, r.result());
        assertTrue(service.findMember(admin.getId()).orElseThrow().isAdmin());
    }

    @Test
    void wrongPin_isRejected_andRaisesNothing() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();

        Rejoin r = service.requestRejoin(home.getCode(), sam.getId(), wrongPin(home));
        assertEquals(RejoinResult.WRONG_PIN, r.result());
        assertTrue(service.pendingRejoins(home.getCode()).isEmpty());
    }

    @Test
    void memberFromAnotherHome_isUnknown() {
        Member alex = service.createHome("Nest", "Alex");
        Member other = service.createHome("Other", "Robin");

        assertEquals(RejoinResult.UNKNOWN,
                service.requestRejoin(alex.getHomeCode(), other.getId(), null).result());
        assertEquals(RejoinResult.UNKNOWN,
                service.requestRejoin("ZZZZZZZ", alex.getId(), null).result());
    }

    @Test
    void approval_flipsTheRequest_andTheTokenSurvivesForTheDevice() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Rejoin r = service.requestRejoin(admin.getHomeCode(), sam.getId(), null);

        RejoinRequest pending = service.pendingRejoins(admin.getHomeCode()).get(0);
        assertTrue(service.decideRejoin(pending.getId(), admin.getId(), true));

        RejoinRequest found = service.findRejoinByToken(r.token()).orElseThrow();
        assertEquals(RejoinStatus.APPROVED, found.getStatus());
        assertEquals(sam.getId(), found.getMemberId());
        assertEquals(admin.getId(), found.getDecidedByMemberId());
        assertTrue(service.pendingRejoins(admin.getHomeCode()).isEmpty());

        // The device consumes it on sign-in, so the token can't be replayed elsewhere.
        service.consumeRejoin(found.getId());
        assertTrue(service.findRejoinByToken(r.token()).isEmpty());
    }

    @Test
    void rejection_isRecorded_andDecidingTwiceFails() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Rejoin r = service.requestRejoin(admin.getHomeCode(), sam.getId(), null);
        RejoinRequest pending = service.pendingRejoins(admin.getHomeCode()).get(0);

        assertTrue(service.decideRejoin(pending.getId(), admin.getId(), false));
        assertEquals(RejoinStatus.REJECTED,
                service.findRejoinByToken(r.token()).orElseThrow().getStatus());
        assertFalse(service.decideRejoin(pending.getId(), admin.getId(), true),
                "a decided request can't be decided again");
    }

    @Test
    void reRequesting_replacesTheOlderPendingRequest() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();

        Rejoin first = service.requestRejoin(admin.getHomeCode(), sam.getId(), null);
        Rejoin second = service.requestRejoin(admin.getHomeCode(), sam.getId(), null);

        assertEquals(1, service.pendingRejoins(admin.getHomeCode()).size());
        assertTrue(service.findRejoinByToken(first.token()).isEmpty(), "old token is void");
        assertTrue(service.findRejoinByToken(second.token()).isPresent());
    }

    @Test
    void cancelling_dropsTheRequest() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Rejoin r = service.requestRejoin(admin.getHomeCode(), sam.getId(), null);

        service.cancelRejoin(r.token());
        assertTrue(service.pendingRejoins(admin.getHomeCode()).isEmpty());
        assertTrue(service.findRejoinByToken(r.token()).isEmpty());
    }

    @Test
    void removingAMember_clearsTheirPendingRequest() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Rejoin r = service.requestRejoin(admin.getHomeCode(), sam.getId(), null);

        assertTrue(service.removeMember(sam.getId()));
        assertTrue(service.findRejoinByToken(r.token()).isEmpty());
        assertTrue(service.pendingRejoins(admin.getHomeCode()).isEmpty());
    }

    @Test
    void unknownToken_findsNothing() {
        assertTrue(service.findRejoinByToken("not-a-token").isEmpty());
        assertTrue(service.findRejoinByToken(null).isEmpty());
        assertTrue(service.findRejoinByToken("  ").isEmpty());
    }

    // ---- First-time joins (gated by default) ---------------------------------

    @Test
    void newHome_gatesFirstJoinsByDefault() {
        Member admin = service.createHome("Nest", "Alex");
        assertTrue(service.findHome(admin.getHomeCode()).orElseThrow().isApproveJoin());
    }

    @Test
    void gatedJoin_createsNoMemberUntilAnAdminApproves() {
        Member admin = service.createHome("Nest", "Alex");

        var outcome = service.requestJoin(admin.getHomeCode(), "Sam");
        assertEquals(RejoinResult.PENDING, outcome.result());
        assertNotNull(outcome.token());
        assertNull(outcome.member());
        assertEquals(1, service.membersOf(admin.getHomeCode()).size(), "still only Alex");

        RejoinRequest request = service.pendingRejoins(admin.getHomeCode()).get(0);
        assertTrue(request.isJoin());
        assertEquals("Sam", request.getRequestedName());
        assertTrue(service.decideRejoin(request.getId(), admin.getId(), true));

        RejoinRequest approved = service.findRejoinByToken(outcome.token()).orElseThrow();
        assertEquals(RejoinStatus.APPROVED, approved.getStatus());
        assertNotNull(approved.getMemberId(), "approval created the member");
        Member sam = service.findMember(approved.getMemberId()).orElseThrow();
        assertEquals("Sam", sam.getName());
        assertFalse(sam.isAdmin());
    }

    @Test
    void gatedJoin_rejected_createsNobody() {
        Member admin = service.createHome("Nest", "Alex");
        var outcome = service.requestJoin(admin.getHomeCode(), "Sam");
        RejoinRequest request = service.pendingRejoins(admin.getHomeCode()).get(0);

        assertTrue(service.decideRejoin(request.getId(), admin.getId(), false));
        assertEquals(1, service.membersOf(admin.getHomeCode()).size());
        assertEquals(RejoinStatus.REJECTED,
                service.findRejoinByToken(outcome.token()).orElseThrow().getStatus());
    }

    @Test
    void joinGateOff_signsInImmediately() {
        Member admin = service.createHome("Nest", "Alex");
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();
        home.setApproveJoin(false);
        service.saveHome(home);

        var outcome = service.requestJoin(home.getCode(), "Sam");
        assertEquals(RejoinResult.SIGNED_IN, outcome.result());
        assertEquals("Sam", outcome.member().getName());
        assertTrue(service.pendingRejoins(home.getCode()).isEmpty());
    }

    @Test
    void join_unknownCodeOrBlankName_isUnknown() {
        Member admin = service.createHome("Nest", "Alex");
        assertEquals(RejoinResult.UNKNOWN, service.requestJoin("ZZZZZZZ", "Sam").result());
        assertEquals(RejoinResult.UNKNOWN, service.requestJoin(admin.getHomeCode(), "  ").result());
    }

    @Test
    void pendingJoinRequest_doesNotBreakAMembersReRequest() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        service.requestJoin(admin.getHomeCode(), "Newcomer"); // memberless request in the queue

        Rejoin r = service.requestRejoin(admin.getHomeCode(), sam.getId(), null);
        assertEquals(RejoinResult.PENDING, r.result());
        assertEquals(2, service.pendingRejoins(admin.getHomeCode()).size(),
                "the join request survives Sam's rejoin");
    }

    // ---- Finding oneself by nickname -----------------------------------------

    @Test
    void findMemberByName_matchesCaseInsensitively_andOnlyWithinTheHome() {
        Member admin = service.createHome("Nest", "Alex");
        Member other = service.createHome("Elsewhere", "Sam");
        service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();

        Member found = service.findMemberByName(admin.getHomeCode(), "  sam ").orElseThrow();
        assertEquals(admin.getHomeCode(), found.getHomeCode());
        assertFalse(found.getId().equals(other.getId()), "not the Sam from the other home");
        assertTrue(service.findMemberByName(admin.getHomeCode(), "Mallory").isEmpty());
        assertTrue(service.findMemberByName(admin.getHomeCode(), null).isEmpty());
    }

    // ---- Device secrets --------------------------------------------------------

    @Test
    void deviceSecret_verifies_andRotatesOnReissue() {
        Member admin = service.createHome("Nest", "Alex");

        assertFalse(service.verifyDeviceSecret(admin.getId(), "guess"),
                "no secret issued yet — nothing verifies");
        String first = service.issueDeviceSecret(admin.getId());
        assertTrue(service.verifyDeviceSecret(admin.getId(), first));
        assertFalse(service.verifyDeviceSecret(admin.getId(), "guess"));
        assertFalse(service.verifyDeviceSecret(admin.getId(), null));

        String second = service.issueDeviceSecret(admin.getId());
        assertFalse(service.verifyDeviceSecret(admin.getId(), first), "old device cut off");
        assertTrue(service.verifyDeviceSecret(admin.getId(), second));
    }

    // ---- Legacy identity migration (trust-on-first-use) -------------------------

    @Test
    void legacyIdentity_migratesExactlyOnce() {
        Member admin = service.createHome("Nest", "Alex");

        String secret = service.migrateLegacyIdentity(admin.getId(), admin.getHomeCode())
                .orElseThrow();
        assertTrue(service.verifyDeviceSecret(admin.getId(), secret),
                "the upgraded device holds a working secret");
        assertTrue(service.migrateLegacyIdentity(admin.getId(), admin.getHomeCode()).isEmpty(),
                "a second secret-less claim on the same member is refused");
    }

    @Test
    void legacyIdentity_wrongHomeOrMember_isRefused() {
        Member admin = service.createHome("Nest", "Alex");
        assertTrue(service.migrateLegacyIdentity(admin.getId(), "ZZZZZZZ").isEmpty());
        assertTrue(service.migrateLegacyIdentity(999999L, admin.getHomeCode()).isEmpty());
        assertTrue(service.migrateLegacyIdentity(null, admin.getHomeCode()).isEmpty());
    }

    @Test
    void legacyIdentity_afterARealSignIn_isRefused() {
        Member admin = service.createHome("Nest", "Alex");
        service.issueDeviceSecret(admin.getId()); // the member's device signed in normally
        assertTrue(service.migrateLegacyIdentity(admin.getId(), admin.getHomeCode()).isEmpty(),
                "a member with a secret can never be claimed by a secret-less identity");
    }

    // ---- PIN brute-force gate ---------------------------------------------------

    @Test
    void pinGate_locksAfterRepeatedFailures_evenForTheRightPin() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();

        for (int i = 0; i < ChoreService.MAX_PIN_FAILURES; i++) {
            assertFalse(service.claimAdmin(sam.getId(), wrongPin(home)));
        }
        assertFalse(service.claimAdmin(sam.getId(), home.getAdminPin()),
                "locked: even the correct PIN is refused for a while");
        assertEquals(RejoinResult.WRONG_PIN,
                service.requestRejoin(home.getCode(), sam.getId(), home.getAdminPin()).result(),
                "the rejoin PIN shortcut shares the same gate");
        assertFalse(service.findMember(sam.getId()).orElseThrow().isAdmin());
    }

    @Test
    void pinGate_typosBelowTheLimit_dontBlockTheRightPin() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();

        for (int i = 0; i < ChoreService.MAX_PIN_FAILURES - 1; i++) {
            assertFalse(service.claimAdmin(sam.getId(), wrongPin(home)));
        }
        assertTrue(service.claimAdmin(sam.getId(), home.getAdminPin()));
    }
}
