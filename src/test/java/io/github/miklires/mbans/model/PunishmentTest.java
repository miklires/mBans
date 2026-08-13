package io.github.miklires.mbans.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentTest {

    @Test
    void permanentPunishmentDoesNotExpire() {
        Punishment punishment = new Punishment();
        assertTrue(punishment.isPermanent());
        assertFalse(punishment.isExpired());
    }

    @Test
    void detectsExpiredPunishment() {
        Punishment punishment = new Punishment();
        punishment.setExpiresAt(Instant.now().minusSeconds(1));
        assertTrue(punishment.isExpired());
    }
}
