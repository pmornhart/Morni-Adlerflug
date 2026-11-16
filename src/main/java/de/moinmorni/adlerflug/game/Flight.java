package de.moinmorni.adlerflug.game;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.time.RunClock;
import java.util.Arrays;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/** Ein Flug eines Spielers auf einer Strecke. */
final class Flight {

    enum State {
        /** Auf der Schanze: Die Zeit startet beim Absprung (Verlassen des Startbereichs). */
        READY,
        /** Party-Rennen: wartet auf den Startschuss, darf die Schanze nicht verlassen. */
        FROZEN,
        /** In der Luft, die Zeit läuft. */
        FLYING
    }

    /** Größte Aufzeichnung für den Geist: 15 Minuten. */
    private static final int MAX_PATH = 3 * 20 * 60 * 15;

    final Course course;
    final boolean practice;
    Race race;
    State state = State.READY;
    RunClock clock;
    /** Nächster Ring (0 = noch keiner geschafft). */
    int next;
    final long[] splits;
    int resets;
    int ringsThisFlight;
    boolean hasPrev;
    Vec prev;
    /** Ticks am Boden/im Wasser bzw. neben der Strecke. */
    int landed;
    /** Bis zu diesem Tick die Flügel immer wieder aufklappen (nach Absprung/Zurücksetzen). */
    long glideUntil;
    /** Geschwindigkeit, die nach dem Aufklappen gesetzt wird (null = keine). */
    Vec launch;
    /** Übungsmodus: ab welchem Ring gestartet wurde (0 = Schanze). */
    int fromRing;
    /** Test-Autopilot: aktueller Tick des Musterflugs (−1 = aus), Ring, bis zu dem geflogen wird, Ring zum Verfehlen. */
    int guide = -1;
    int guideUntil = Integer.MAX_VALUE;
    int guideMiss = -1;
    /** Bis zu diesem Tick zeigt die Aktionsleiste die letzte Zwischenzeit statt der Uhr. */
    long splitShownUntil;
    Component splitText;
    BossBar bar;
    /** Weg dieses Flugs (für den Geist, falls er Bestzeit wird). */
    private float[] path = new float[900];
    private int pathLen;
    /** Geist der Bestzeit (x/y/z pro Tick) oder null. */
    float[] ghost;

    Flight(Course course, boolean practice) {
        this.course = course;
        this.practice = practice;
        this.splits = new long[course.ringCount()];
    }

    void resetProgress() {
        next = 0;
        Arrays.fill(splits, 0);
        clock = null;
        pathLen = 0;
        hasPrev = false;
        splitShownUntil = 0;
        landed = 0;
        ringsThisFlight = 0;
    }

    void record(Vec feet) {
        if (pathLen + 3 > MAX_PATH) {
            return;
        }
        if (pathLen + 3 > path.length) {
            path = Arrays.copyOf(path, Math.min(MAX_PATH, path.length * 2));
        }
        path[pathLen++] = (float) feet.x();
        path[pathLen++] = (float) feet.y();
        path[pathLen++] = (float) feet.z();
    }

    float[] path() {
        return Arrays.copyOf(path, pathLen);
    }
}
