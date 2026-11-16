package de.moinmorni.adlerflug.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Der nachgebaute Gleitflug verhält sich wie im Spiel (Werte aus dem Vanilla-Code nachgerechnet). */
class GlideTest {

    /** Gleitet {@code ticks} lang mit fester Neigung und liefert {Weg waagerecht, Höhenverlust, Tempo am Ende}. */
    private static double[] glide(double pitch, int ticks) {
        Vec v = new Vec(0, 0, 0.6);
        for (int t = 0; t < 600; t++) {
            v = Glide.step(v, 0, pitch);
        }
        Vec pos = Vec.ZERO;
        for (int t = 0; t < ticks; t++) {
            v = Glide.step(v, 0, pitch);
            pos = pos.add(v);
        }
        return new double[] {pos.horizontal(), -pos.y(), v.length()};
    }

    @Test
    void lookVectorMatchesMinecraft() {
        Vec south = Vec.look(0, 0);
        assertEquals(1, south.z(), 1e-9);
        Vec west = Vec.look(90, 0);
        assertEquals(-1, west.x(), 1e-9);
        Vec down = Vec.look(0, 90);
        assertEquals(-1, down.y(), 1e-9);
        assertEquals(-90f, new Vec(1, 0, 0).yaw(), 1e-4);
        assertEquals(30f, Vec.look(10, 30).pitch(), 1e-4);
    }

    @Test
    void levelGlideIsAboutTenToOneAtHundredKmh() {
        double[] g = glide(0, 200);
        double ratio = g[0] / g[1];
        assertTrue(ratio > 9.5 && ratio < 10.5, "Gleitzahl " + ratio);
        assertTrue(g[2] > 1.45 && g[2] < 1.6, "Tempo " + g[2]);
        assertEquals(109, Glide.kmh(new Vec(0, 0, g[2])), 3);
    }

    @Test
    void divingIsFasterButSteeper() {
        double[] level = glide(0, 200);
        double[] dive = glide(30, 200);
        assertTrue(dive[2] > level[2] * 1.5, "Sturzflug schneller");
        assertTrue(dive[0] / dive[1] < level[0] / level[1], "aber steiler");
    }

    @Test
    void pullingUpTradesSpeedForHeight() {
        Vec v = new Vec(0, 0, 2.0);
        Vec pos = Vec.ZERO;
        for (int t = 0; t < 40; t++) {
            v = Glide.step(v, 0, -30);
            pos = pos.add(v);
        }
        assertTrue(pos.y() > 25, "steigt " + pos.y());
        assertTrue(v.length() < 1.4, "wird langsamer " + v.length());
    }

    @Test
    void turningBendsTheFlightGradually() {
        Vec v = new Vec(0, 0, 1.5);
        v = Glide.step(v, 90, 0);
        // nach einem Tick erst ein Zehntel zur Seite
        assertTrue(v.x() < 0 && Math.abs(v.x()) < 0.3, "x " + v.x());
        for (int t = 0; t < 40; t++) {
            v = Glide.step(v, 90, 0);
        }
        assertTrue(v.x() < -0.8 && Math.abs(v.z()) < 0.1, "nach 2 s gedreht: " + v);
    }

    @Test
    void updraftLiftsButNeverBrakes() {
        Updraft u = new Updraft(0, 0, 8, 60, 120, 1.2);
        Vec v = new Vec(1.4, -0.15, 0);
        for (int i = 0; i < 12; i++) {
            v = u.lift(v);
        }
        assertTrue(v.y() > 1.15 && v.y() <= 1.2, "steigt " + v.y());
        assertEquals(1.4, v.x(), 1e-9, "waagerecht unverändert");
        Vec fast = new Vec(0, 2.0, 0);
        assertEquals(2.0, u.lift(fast).y(), 1e-9, "schneller Steigflug wird nicht gebremst");
        assertTrue(u.contains(new Vec(3, 80, 3)));
        assertTrue(!u.contains(new Vec(3, 130, 3)) && !u.contains(new Vec(9, 80, 0)));
    }
}
