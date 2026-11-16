package de.moinmorni.adlerflug.land;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.adlerflug.flight.Vec;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Landschaft: immer gleich, Schneisen halten die Strecke frei, Seen haben Wasser. */
class TerrainTest {

    @Test
    void sameSeedSameMountains() {
        Terrain a = new Terrain(42);
        Terrain b = new Terrain(42);
        Terrain c = new Terrain(43);
        double diff = 0;
        for (int i = 0; i < 50; i++) {
            assertEquals(a.height(i * 37, i * -53), b.height(i * 37, i * -53));
            diff += Math.abs(a.heightExact(i * 37, i * -53) - c.heightExact(i * 37, i * -53));
        }
        assertTrue(diff > 50, "anderer Startwert, andere Berge");
    }

    @Test
    void corridorKeepsTheWayFree() {
        Terrain t = new Terrain(7);
        t.shape(new Terrain.Mound(0, 0, 200, 260, 1.5, 10));
        List<Vec> path = new java.util.ArrayList<>();
        for (int x = -100; x <= 100; x += 2) {
            path.add(new Vec(x, 180 - (x + 100) * 0.1, 0));
        }
        Corridor c = new Corridor(path, 8, 6, 2);
        t.corridor(c);
        for (int x = -100; x <= 100; x += 5) {
            double y = 180 - (x + 100) * 0.1;
            assertTrue(t.heightExact(x, 0) <= y - 8 + 0.6, "frei bei x=" + x + ": " + t.heightExact(x, 0));
        }
        // zur Seite steigen die Hänge wieder an
        assertTrue(t.heightExact(0, 40) > t.heightExact(0, 0) + 20, "Talhänge");
        assertTrue(c.distance(0, 10) < 11 && c.distance(0, 10) > 9);
    }

    @Test
    void lakesHoldWater() {
        Terrain t = new Terrain(9);
        t.shape(new Terrain.Plateau(0, 0, 80, 20, 90));
        t.lake(new Terrain.Lake(0, 0, 20, 89));
        int ground = t.height(0, 0);
        assertTrue(ground < 89, "Seegrund tiefer als der Spiegel");
        assertEquals(89, t.water(0, 0, ground));
        assertEquals(Integer.MIN_VALUE, t.water(60, 0, t.height(60, 0)));
    }

    @Test
    void snowOnTopMeadowBelow() {
        Terrain t = new Terrain(1);
        assertEquals("snow_block", t.surface(0, 0, 230, 0.5));
        assertEquals("grass_block", t.surface(0, 0, 90, 0.5));
        String steep = t.surface(5, 5, 120, 6);
        assertTrue(List.of("stone", "andesite", "tuff", "cobblestone", "gravel").contains(steep), steep);
    }
}
