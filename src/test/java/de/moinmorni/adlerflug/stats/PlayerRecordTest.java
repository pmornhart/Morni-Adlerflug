package de.moinmorni.adlerflug.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spielerdatei: Strecke des Tages gilt nur einen Tag, Fingerabdruck der Strecke bleibt erhalten. */
class PlayerRecordTest {

    @Test
    void dailyBestLastsOneDay() {
        PlayerRecord r = new PlayerRecord(UUID.randomUUID(), "Anna");
        r.enterDay("2026-11-16", "tagesflug");
        PlayerRecord.CourseStats d = r.course("tagesflug");
        d.best = 40_000;
        d.flights = 3;
        d.splits.add(1000L);
        r.enterDay("2026-11-16", "tagesflug");
        assertEquals(40_000, r.best("tagesflug"));
        r.enterDay("2026-11-17", "tagesflug");
        assertEquals(0, r.best("tagesflug"), "neuer Tag, neue Bestzeit");
        assertEquals(0, r.course("tagesflug").splits.size());
        assertEquals(3, r.course("tagesflug").flights, "Zähler bleiben");
    }

    @Test
    void roundTripKeepsDayAndSignature() {
        PlayerRecord r = new PlayerRecord(UUID.randomUUID(), "Bert");
        r.course("klammflug").best = 21_000;
        r.course("klammflug").sig = "abc123";
        r.dayId = "2026-11-16";
        r.days = 4;
        PlayerRecord back = PlayerRecord.fromMap(r.uuid, r.toMap());
        assertEquals("abc123", back.course("klammflug").sig);
        assertEquals(21_000, back.best("klammflug"));
        assertEquals("2026-11-16", back.dayId);
        assertEquals(4, back.days);
        // alte Dateien ohne Fingerabdruck
        PlayerRecord old = PlayerRecord.fromMap(r.uuid, java.util.Map.of("name", "Carl"));
        assertEquals("", old.dayId);
        assertEquals(0, old.days);
    }
}
