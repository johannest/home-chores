package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Home;
import com.homechores.domain.InputLimits;
import com.homechores.domain.Member;
import com.homechores.domain.RejoinRequestRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side input limits. The UI caps field lengths too, but those caps live in the
 * browser — these tests call the services directly, the way a tampered client would.
 */
@SpringBootTest
@Transactional
class InputLimitsTest {

    @Autowired ChoreService service;
    @Autowired CreditService creditService;
    @Autowired BackupService backupService;
    @Autowired RejoinRequestRepository rejoins;

    private static final String LOREM = "lorem ipsum ".repeat(100); // 1200 chars

    @Test
    void homeAndMemberNames_areClippedOnCreate() {
        Member m = service.createHome(LOREM, LOREM);
        Home home = service.findHome(m.getHomeCode()).orElseThrow();

        assertEquals(InputLimits.HOME_NAME, home.getName().length());
        assertTrue(m.getName().length() <= InputLimits.MEMBER_NAME);
    }

    @Test
    void oversizedJoinNickname_noLongerCrashesTheGatedJoinFlow() {
        Member admin = service.createHome("Gated", "Alex");
        // approveJoin defaults to true → the name lands in RejoinRequest.requestedName,
        // whose column is 60 chars. Un-clipped, this save used to throw.
        ChoreService.JoinOutcome outcome = service.requestJoin(admin.getHomeCode(), LOREM);

        assertEquals(ChoreService.RejoinResult.PENDING, outcome.result());
        String stored = rejoins.findByDeviceToken(outcome.token()).orElseThrow()
                .getRequestedName();
        assertTrue(stored.length() <= InputLimits.MEMBER_NAME);
    }

    @Test
    void renameAndTaskFields_areClippedAndClamped() {
        Member m = service.createHome("Limits", "Alex");
        service.renameMember(m.getId(), LOREM);
        assertTrue(service.findMember(m.getId()).orElseThrow().getName().length()
                <= InputLimits.MEMBER_NAME);

        ChoreTask t = service.addTask(m.getHomeCode(), LOREM, LOREM, 100000, 100000);
        assertTrue(t.getName().length() <= InputLimits.TASK_NAME);
        assertTrue(t.getEmoji().length() <= InputLimits.EMOJI);
        assertEquals(InputLimits.MAX_DAYS, t.getIntervalDays());
        assertEquals(InputLimits.MAX_CREDITS, t.getCreditValue());
    }

    @Test
    void timeWindows_areCappedAtSixWindows() {
        String many = "08:00-09:00,".repeat(19) + "08:00-09:00"; // 20 windows
        assertEquals(InputLimits.TIME_WINDOW_COUNT,
                ChoreService.clipWindows(many).split(",").length);
        assertEquals("08:00-10:00", ChoreService.clipWindows("08:00-10:00"));
        assertEquals(null, ChoreService.clipWindows(null));
    }

    @Test
    void saveHome_clipsTheNameAndRefusesAMalformedPin() {
        Member m = service.createHome("PinHome", "Alex");
        Home home = service.findHome(m.getHomeCode()).orElseThrow();

        home.setName(LOREM);
        service.saveHome(home);
        assertEquals(InputLimits.HOME_NAME,
                service.findHome(m.getHomeCode()).orElseThrow().getName().length());

        Home again = service.findHome(m.getHomeCode()).orElseThrow();
        again.setAdminPin("<script>");
        assertThrows(IllegalArgumentException.class, () -> service.saveHome(again));
    }

    @Test
    void redeemNote_isClipped() {
        Member m = service.createHome("Redeem", "Alex");
        ChoreTask t = service.addTask(m.getHomeCode(), "Dishes", "🍽️", 0, 10);
        service.complete(t.getId(), m.getId());

        assertTrue(creditService.redeem(m.getHomeCode(), m.getId(), 1, LOREM, m.getId()));
        String reason = creditService.ledger(m.getHomeCode()).get(0).getReason();
        assertTrue(reason.length() <= InputLimits.REASON);
    }

    @Test
    void backupRestore_sanitizesEveryUserString() {
        Member m = service.createHome("Restore", "Alex");
        String code = m.getHomeCode();
        String originalPin = service.findHome(code).orElseThrow().getAdminPin();

        String json = """
                {"version":1,
                 "home":{"code":"%s","name":"%s","adminPin":"not-a-pin",
                         "requireApproval":false,"dailyTargetPerMember":99,
                         "divisionStyle":"DEFAULT","rotationEnforced":true,
                         "bookingTimeoutHours":4},
                 "members":[{"id":1,"name":"%s","color":"javascript:alert(1)","admin":true}],
                 "tasks":[{"id":1,"name":"%s","emoji":"%s","intervalDays":99999,
                           "creditValue":99999,"availableWindows":"%s"}],
                 "completions":[],"spreeTiers":[{"days":-5,"credits":10}],"credits":[]}
                """.formatted(code, LOREM, LOREM, LOREM, LOREM,
                        "08:00-09:00,".repeat(19) + "08:00-09:00");

        backupService.restore(json.getBytes(StandardCharsets.UTF_8), code);

        Home home = service.findHome(code).orElseThrow();
        assertEquals(InputLimits.HOME_NAME, home.getName().length());
        assertEquals(originalPin, home.getAdminPin(), "garbage PIN rejected, old one kept");
        assertEquals(1, home.getDailyTargetPerMember() > 0 && home.getDailyTargetPerMember() <= 3
                ? 1 : 0, "daily target clamped to 1..3");

        Member restored = service.membersOf(code).get(0);
        assertTrue(restored.getName().length() <= InputLimits.MEMBER_NAME);
        assertNotEquals("javascript:alert(1)", restored.getColor(),
                "a non-hex color is replaced from the palette");

        ChoreTask task = service.tasksOf(code).get(0);
        assertTrue(task.getName().length() <= InputLimits.TASK_NAME);
        assertTrue(task.getEmoji().length() <= InputLimits.EMOJI);
        assertEquals(InputLimits.MAX_DAYS, task.getIntervalDays());
        assertEquals(InputLimits.MAX_CREDITS, task.getCreditValue());
        assertTrue(task.getAvailableWindows().length() <= InputLimits.TIME_WINDOWS);

        assertTrue(creditService.tiersOf(code).isEmpty(), "non-positive spree tier skipped");
    }

    /**
     * Emoji are two code units each, so a cap that lands mid-emoji must step back rather
     * than store half of one. A lone surrogate is not a character: it would ride through
     * the database and into backups, and render as a replacement glyph on the board.
     */
    @Test
    void clipNeverSplitsAnEmojiInHalf() {
        String family = "\uD83D\uDC68\uD83D\uDC69\uD83D\uDC67"; // 3 emoji, 6 code units

        for (int max = 1; max <= family.length(); max++) {
            String clipped = InputLimits.clip(family, max);
            assertTrue(clipped.length() <= max, "stays within the cap at max=" + max);
            assertEquals(clipped.codePointCount(0, clipped.length()),
                    clipped.length() / 2,
                    "only whole emoji survive at max=" + max);
            // An unpaired half shows up in codePoints() as a raw surrogate value.
            assertTrue(clipped.codePoints().noneMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF),
                    "no orphaned surrogate half at max=" + max);
        }
        assertEquals("\uD83D\uDC68", InputLimits.clip(family, 3),
                "a cap of 3 keeps one emoji, not one-and-a-half");
    }

    /** The same, through the field that makes it reachable: a chore's emoji. */
    @Test
    void anOverlongEmojiIsStoredAsWholeCharacters() {
        String chain = "\uD83D\uDC68".repeat(20); // 40 code units, EMOJI caps at 16
        String clipped = InputLimits.clip(chain, InputLimits.EMOJI);

        assertEquals(InputLimits.EMOJI, clipped.length());
        assertEquals(InputLimits.EMOJI / 2, clipped.codePointCount(0, clipped.length()));
    }
}
