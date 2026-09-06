package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.Avatars;
import com.homechores.domain.Member;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Selectable avatars: the fixed CC0 catalog, the whitelist, and backup round-trips. */
@SpringBootTest
@Transactional
class AvatarTest {

    @Autowired ChoreService service;
    @Autowired BackupService backupService;

    @Test
    void catalogIdsAreValid_andGarbageIsNot() {
        assertTrue(Avatars.isValid("panda"));
        assertEquals("avatars/panda.png", Avatars.urlFor("panda"));
        assertFalse(Avatars.isValid("../../etc/passwd"));
        assertNull(Avatars.urlFor("<script>"));
        assertEquals(30, Avatars.IDS.size());
    }

    @Test
    void setAvatar_acceptsCatalogIds_andClearsGarbage() {
        Member alex = service.createHome("Avatars", "Alex");

        service.setAvatar(alex.getId(), "owl");
        assertEquals("owl", service.findMember(alex.getId()).orElseThrow().getAvatar());

        service.setAvatar(alex.getId(), "not-an-animal");
        assertNull(service.findMember(alex.getId()).orElseThrow().getAvatar(),
                "an unknown id clears rather than stores");

        service.setAvatar(alex.getId(), null);
        assertNull(service.findMember(alex.getId()).orElseThrow().getAvatar());
    }

    @Test
    void backup_roundTripsAvatars_andRejectsUnknownIdsFromEditedFiles() {
        Member alex = service.createHome("Backup", "Alex");
        String code = alex.getHomeCode();
        service.setAvatar(alex.getId(), "narwhal");

        String json = backupService.export(code);
        assertTrue(json.contains("narwhal"));

        // A restore keeps a valid avatar…
        backupService.restore(json.getBytes(StandardCharsets.UTF_8), code);
        assertEquals("narwhal", service.membersOf(code).get(0).getAvatar());

        // …and a hand-edited file with a bogus one falls back to initials.
        String tampered = json.replace("narwhal", "javascript:alert(1)");
        backupService.restore(tampered.getBytes(StandardCharsets.UTF_8), code);
        assertNull(service.membersOf(code).get(0).getAvatar());
    }
}
