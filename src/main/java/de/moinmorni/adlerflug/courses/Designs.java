package de.moinmorni.adlerflug.courses;

import de.moinmorni.adlerflug.course.Box;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.CourseBuilder;
import de.moinmorni.adlerflug.course.Difficulty;
import de.moinmorni.adlerflug.land.Terrain;
import java.util.List;

/**
 * Die sechs Strecken von Adlerflug, von leicht bis extrem – alle eigene Entwürfe in eigener Alpen-Landschaft. Die
 * Wegpunkte bestimmen die Richtung; Höhen und Ausrichtung der Ringe ergeben sich aus dem nachgebauten Gleitflug.
 *
 * <p>Lage (Basislager in der Mitte): Almrundflug im Osten, Klammflug im Süden, Burgruine im Westen, Gletscherspalte im
 * Norden, Stollenflug im Südosten, Gipfelkreuz im Nordosten.
 */
public final class Designs {

    private Designs() {
    }

    public static List<Design> all() {
        return List.of(alm(), klamm(), burg(), gletscher(), stollen(), gipfel());
    }

    // =========================================================================================
    // Almrundflug – leicht: über Wiesen, Hütten und eine Kapelle
    // =========================================================================================

    static Design alm() {
        Course c = new CourseBuilder("almrundflug", "Almrundflug", "Alpine Meadow Loop", Difficulty.LEICHT)
                .text("Gemütlich über Almwiesen, Heustadel und die kleine Kapelle – große Ringe, ein Schub-Ring und "
                        + "eine Aufwind-Säule zum Ausprobieren.",
                        "A gentle flight over alpine meadows, hay barns and the little chapel – big rings, one boost "
                        + "ring and an updraft to try out.")
                .icon("grass_block").biome("minecraft:meadow").radius(6).maxOff(32)
                .start(400.5, 132, 0.5, -90, 5, 1.3, 4)
                .pitch(3)
                .ring(450, 2)
                .ring(500, 14)
                .ring(546, 38)
                .boost(590, 52, 2.1, 10)
                .ring(642, 50)
                .updraft(690, 30, 9, 1.25, 60, 150)
                .ring(728, 4)
                .ring(756, -32)
                .ring(764, -76)
                .ring(744, -118)
                .ring(702, -146)
                .ring(650, -154)
                .finish(600, -142, 7)
                .landing(556.5, 84, -176.5, 45)
                .build(3);
        return new Design(c, t -> {
            t.shape(new Terrain.Plateau(580, -50, 230, 120, 90, 7));
            t.shape(new Terrain.Mound(396, 0, 36, 118, 1.6, 4));
            t.lake(new Terrain.Lake(620, -60, 26, 85));
            t.region(new Terrain.Region(580, -50, 340, Terrain.Style.ALM));
        }, Design.cut(8, 16, 0.45), Builders::alm, "lime_concrete", null);
    }

    // =========================================================================================
    // Klammflug – mittel: eine enge Schlucht mit Bach und Wasserfall
    // =========================================================================================

    static Design klamm() {
        Course c = new CourseBuilder("klammflug", "Klammflug", "Gorge Glide", Difficulty.MITTEL)
                .text("Hinein in die Klamm: steile Felswände, ein Bach tief unten und ein Wasserfall, an dem du "
                        + "vorbeirauschst. Kurvig – bleib in der Mitte!",
                        "Into the gorge: steep rock walls, a stream far below and a waterfall you rush past. Lots of "
                        + "bends – stay in the middle!")
                .icon("moss_block").biome("minecraft:old_growth_spruce_taiga").radius(5).maxOff(24)
                .start(0.5, 168, 400.5, 0, 5, 1.3, 2)
                .pitch(4)
                .ring(4, 446)
                .ring(-10, 492)
                .ring(6, 536)
                .ring(26, 576)
                .ring(20, 622)
                .ring(-4, 660)
                .boost(-22, 700, 2.0, 6)
                .ring(-12, 746)
                .ring(14, 784)
                .ring(30, 826)
                .finish(18, 872, 6)
                .landing(-26.5, 84, 916.5, 0)
                .build(3);
        return new Design(c, t -> {
            t.shape(new Terrain.Mound(10, 640, 300, 226, 2.0, 22));
            t.region(new Terrain.Region(10, 640, 280, Terrain.Style.GORGE));
            java.util.List<double[]> river = new java.util.ArrayList<>();
            for (int k = 0; k < c.pathTicks(); k += 8) {
                var p = c.pathAt(k);
                river.add(new double[] {p.x(), p.z(), p.y() - 25});
            }
            t.river(new Terrain.River(river, 3.5));
        }, Design.cut(26, 6, 3.4), Builders::klamm, "cyan_concrete", null);
    }

    // =========================================================================================
    // Burgruine – mittel: durch Tore, zwischen Türmen, über den Burghof
    // =========================================================================================

    static Design burg() {
        Course c = new CourseBuilder("burgruine", "Burgruine", "Castle Ruins", Difficulty.MITTEL)
                .text("Die alte Burg auf dem Hügel: durch das Burgtor, im Aufwind über den Hof, zwischen zwei Türmen "
                        + "hindurch und unter den Bögen der alten Steinbrücke.",
                        "The old castle on the hill: through the gate, up the updraft over the courtyard, between two "
                        + "towers and under the arches of the old stone bridge.")
                .icon("mossy_stone_bricks").biome("minecraft:plains").radius(5).maxOff(26)
                .start(-399.5, 146, 0.5, 90, 5, 1.3, 3)
                .pitch(3)
                .ring(-452, -4)
                .ring(-506, -8)
                .ring(-566, -2)
                .updraft(-596, 4, 9, 1.3, 100, 156)
                .ring(-634, 10)
                .ring(-676, 18)
                .ring(-712, -6)
                .boost(-726, -48, 2.1, 8)
                .ring(-702, -92)
                .ring(-656, -110)
                .ring(-608, -106)
                .finish(-566, -86, 6)
                .landing(-528.5, 98, -108.5, -90)
                .build(3);
        return new Design(c, t -> {
            t.shape(new Terrain.Plateau(-560, -40, 220, 120, 92, 6));
            t.shape(new Terrain.Mound(-600, 0, 80, 118, 1.8, 6));
            t.shape(new Terrain.Plateau(-600, 0, 36, 12, 118));
            t.shape(new Terrain.Mound(-396, 0, 40, 124, 1.5, 4));
            t.region(new Terrain.Region(-560, -40, 300, Terrain.Style.ALM));
        }, java.util.List.of(new Design.Cut(0, 7, 12, 0.6), new Design.Cut(2, 3, 5, 0.6), new Design.Cut(3, 9, 12, 0.6)),
                Builders::burg, "red_concrete", null);
    }

    // =========================================================================================
    // Gletscherspalte – schwer: über das Eis und hinein in die Spalte
    // =========================================================================================

    static Design gletscher() {
        Course c = new CourseBuilder("gletscherspalte", "Gletscherspalte", "Glacier Crevasse", Difficulty.SCHWER)
                .text("Hoch über dem Gletscher geht es los, dann hinab in die eisblaue Spalte – unter Eisbögen "
                        + "hindurch, eng und schnell. Ein Aufwind holt dich wieder heraus.",
                        "It starts high above the glacier, then down into the ice-blue crevasse – under ice arches, "
                        + "narrow and fast. An updraft lifts you back out.")
                .icon("blue_ice").biome("minecraft:frozen_peaks").radius(4.5).maxOff(22)
                .start(0.5, 218, -399.5, 180, 5, 1.3, 2)
                .pitch(6)
                .ring(-4, -446)
                .ring(6, -492)
                .ring(-6, -538)
                .ring(10, -584)
                .ring(2, -628)
                .ring(-14, -668)
                .ring(-8, -712)
                .ring(10, -752)
                .pitch(2)
                .updraft(22, -792, 8, 1.35, 120, 205)
                .ring(30, -834)
                .boost(20, -878, 2.2, 10)
                .ring(-6, -916)
                .finish(-34, -950, 5)
                .landing(-74.5, 172, -980.5, 90)
                .build(4);
        return new Design(c, t -> {
            t.shape(new Terrain.Mound(0, -700, 360, 214, 2.6, 16));
            t.shape(new Terrain.Plateau(0, -650, 140, 90, 200, 5));
            t.shape(new Terrain.Basin(0, -930, 70, 70, 168));
            t.shape(new Terrain.Mound(0, -395, 30, 206, 1.4, 3));
            t.region(new Terrain.Region(0, -700, 330, Terrain.Style.GLACIER));
        }, java.util.List.of(new Design.Cut(0, 28, 3, 5.5), new Design.Cut(9, 8, 8, 1.2)), Builders::gletscher,
                "light_blue_concrete", null);
    }

    // =========================================================================================
    // Stollenflug – schwer: durch einen beleuchteten Bergwerksstollen
    // =========================================================================================

    static Design stollen() {
        Course c = new CourseBuilder("stollenflug", "Stollenflug", "Mine Shaft Flight", Difficulty.SCHWER)
                .text("Mitten durch den Berg: ein alter Bergwerksstollen mit Laternen, Stützbalken und Gleisen. "
                        + "Kleine Ringe, enge Kurven – Glück auf!",
                        "Right through the mountain: an old mine tunnel with lanterns, support beams and rails. Small "
                        + "rings, tight bends – good luck!")
                .icon("rail").biome("minecraft:windswept_hills").radius(3.5).maxOff(20)
                .start(420.5, 168, 450.5, -90, 5, 1.3, 2)
                .pitch(5)
                .ring(466, 452)
                .ring(510, 448)
                .pitch(2)
                .ring(552, 452)
                .ring(590, 462)
                .ring(626, 458)
                .ring(662, 446)
                .ring(698, 444)
                .ring(734, 452)
                .pitch(3)
                .boost(774, 458, 2.0, 12)
                .ring(814, 452)
                .finish(850, 440, 5)
                .landing(880.5, 98, 404.5, -90)
                .build(3);
        return new Design(c, t -> {
            t.shape(new Terrain.Mound(640, 452, 150, 232, 1.9, 18));
            t.shape(new Terrain.Mound(418, 450, 36, 152, 1.5, 4));
            t.region(new Terrain.Region(640, 452, 200, Terrain.Style.ROCK));
        }, Design.cut(8, 10, 1.2), Builders::stollen, "brown_concrete", new Box(545, 0, 400, 722, 400, 510));
    }

    // =========================================================================================
    // Gipfelkreuz – extrem: hoch, lang, eng
    // =========================================================================================

    static Design gipfel() {
        Course c = new CourseBuilder("gipfelkreuz", "Gipfelkreuz", "Summit Cross", Difficulty.EXTREM)
                .text("Die Königsstrecke: einmal rund ums Gipfelkreuz, durch die Felsscharte, zwei Aufwinde und kleine "
                        + "Ringe bis hinunter zur Hütte. Lang, hoch und nur für Mutige.",
                        "The king of courses: once around the summit cross, through the rocky notch, two updrafts and "
                        + "small rings down to the hut. Long, high and only for the brave.")
                .icon("lightning_rod").biome("minecraft:jagged_peaks").radius(3.5).maxOff(20)
                .start(500.5, 258, -500.5, 180, 5, 1.3, 2)
                .pitch(3)
                .ring(505, -548)
                .ring(520, -594)
                .ring(550, -634)
                .ring(590, -664)
                .ring(634, -682)
                .ring(680, -676)
                .ring(714, -646)
                .ring(724, -602)
                .pitch(10)
                .ring(718, -556)
                .ring(740, -514)
                .pitch(3)
                .updraft(782, -498, 8, 1.4, 120, 236)
                .ring(826, -510)
                .boost(858, -546, 2.3, 12)
                .ring(872, -592)
                .pitch(8)
                .ring(868, -640)
                .ring(850, -684)
                .pitch(3)
                .updraft(820, -720, 8, 1.4, 100, 226)
                .ring(784, -748)
                .ring(740, -762)
                .ring(694, -764)
                .finish(650, -756, 5)
                .landing(604.5, 152, -770.5, 90)
                .build(4);
        return new Design(c, t -> {
            t.shape(new Terrain.Mound(640, -600, 150, 306, 1.45, 22));
            t.shape(new Terrain.Mound(500, -494, 28, 246, 1.4, 4));
            t.region(new Terrain.Region(640, -600, 150, Terrain.Style.ROCK));
        }, Design.cut(8, 8, 1.8), Builders::gipfel, "white_concrete", null);
    }
}
