package de.moinmorni.adlerflug.courses;

import de.moinmorni.adlerflug.course.Box;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.CourseBuilder;
import de.moinmorni.adlerflug.course.Difficulty;
import de.moinmorni.adlerflug.land.Terrain;
import java.util.List;

/**
 * Die acht Strecken von Adlerflug, von leicht bis extrem – alle eigene Entwürfe in eigener Alpen-Landschaft. Die
 * Wegpunkte bestimmen die Richtung; Höhen und Ausrichtung der Ringe ergeben sich aus dem nachgebauten Gleitflug.
 *
 * <p>Lage (Basislager in der Mitte): Almrundflug im Osten, Klammflug im Süden, Burgruine im Westen, Gletscherspalte im
 * Norden, Stollenflug im Südosten, Felsenlabyrinth im Nordwesten, Gipfelkreuz im Nordosten, Höllenschlund im
 * Südwesten. Die Strecke des Tages ({@link Daily}) liegt im Süden.
 */
public final class Designs {

    private Designs() {
    }

    public static List<Design> all() {
        return List.of(alm(), klamm(), burg(), gletscher(), stollen(), labyrinth(), gipfel(), schlund());
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
                .icon("grass_block").biome("minecraft:meadow").radius(5).maxOff(32)
                .start(400.5, 132, 0.5, -90, 5, 1.3, 4)
                .pitch(3)
                .ring(450, 2)
                .ring(500, 14)
                .glide(546, 38, 2.5, 120, false)
                .bonus(568, 52, 1500)
                .boost(590, 52, 2.1, 10)
                .ring(642, 50)
                .updraft(690, 30, 9, 1.25, 60, 150)
                .ring(728, 4)
                .ring(756, -32)
                .ring(764, -76)
                .ring(744, -118)
                .ring(702, -146)
                .ring(650, -154)
                .finish(600, -142, 6)
                .landing(556.5, 84, -176.5, 45)
                .build(3);
        return new Design(c, t -> {
            // Almtal: Wiesenboden mit Bergsee, ringsum steigen die Hänge zu Graten und Schneegipfeln
            t.shape(new Terrain.Plateau(612, -48, 92, 80, 88, 4));
            t.shape(new Terrain.Plateau(500, 40, 50, 50, 92, 3));
            t.shape(new Terrain.Mound(396, 0, 36, 118, 1.6, 4));
            // Felskopf am See, von dem der Wasserfall stürzt
            t.shape(new Terrain.Mound(684, -80, 40, 122, 1.5, 11));
            t.lake(new Terrain.Lake(620, -60, 26, 85));
            // der Felskopf bricht zum See als Wand ab (dort stürzt der Wasserfall hinunter)
            t.shape(new Terrain.Basin(624, -62, 40, 3, 86));
            // Schneegipfel hinter dem Tal
            t.shape(new Terrain.Mound(900, -60, 120, 286, 1.5, 18));
            t.shape(new Terrain.Mound(820, 230, 110, 262, 1.5, 16));
            t.shape(new Terrain.Mound(560, -330, 100, 250, 1.5, 16));
            t.region(new Terrain.Region(600, -40, 320, Terrain.Style.ALM));
            // Wanderwege: Kapelle – Hütten – Steg am See – Heustadel
            t.path(1.2, 470, -36, 522, -30, 560, -46, 596, -58);
            t.path(1.0, 522, -30, 540, 10, 528, 60, 512, 95);
            t.path(1.0, 560, -46, 600, 0, 614, 50, 616, 82);
            t.path(1.0, 614, 50, 650, 96);
        }, Design.cut(8, 16, 0.45), Builders::alm, "lime_concrete", null);
    }

    // =========================================================================================
    // Klammflug – mittel: eine enge Schlucht mit Bach und Wasserfall
    // =========================================================================================

    static Design klamm() {
        Course c = new CourseBuilder("klammflug", "Klammflug", "Gorge Glide", Difficulty.MITTEL)
                .text("Hinein in die Klamm: steile Felswände, ein Bach tief unten, ein Wasserfall und Seitenwind, der "
                        + "dich gegen die Wand drückt. Kurvig – bleib in der Mitte!",
                        "Into the gorge: steep rock walls, a stream far below, a waterfall and a crosswind pushing you "
                        + "towards the wall. Lots of bends – stay in the middle!")
                .icon("moss_block").biome("minecraft:old_growth_spruce_taiga").radius(4.5).maxOff(24)
                .start(0.5, 168, 400.5, 0, 5, 1.3, 2)
                .pitch(4)
                .ring(4, 446)
                .ring(-10, 492)
                .ring(6, 536)
                .wind(30, 556, -10, 650, 14, 0, 300, 0.012, 0)
                .ring(26, 576)
                .glide(20, 622, 2.0, 90, false)
                .ring(-4, 660)
                .bonus(-24, 678, 1500)
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
                .text("Die alte Burg auf dem Hügel: durchs Burgtor, solange das Fallgatter oben ist, im Aufwind über den "
                        + "Hof, zwischen zwei Türmen hindurch und unter den Bögen der alten Steinbrücke.",
                        "The old castle on the hill: through the gate while the portcullis is up, up the updraft over the "
                        + "courtyard, between two towers and under the arches of the old stone bridge.")
                .icon("mossy_stone_bricks").biome("minecraft:plains").radius(4.5).maxOff(26)
                .start(-399.5, 146, 0.5, 90, 5, 1.3, 3)
                .pitch(3)
                .ring(-452, -4)
                .ring(-506, -8)
                .gate(-566, -2, 80, 52)
                .updraft(-596, 4, 9, 1.3, 100, 156)
                .ring(-634, 10)
                .ring(-676, 18)
                .ring(-712, -6)
                .bonus(-734, -24, 1500)
                .boost(-726, -48, 2.1, 8)
                .ring(-702, -92)
                .ring(-656, -110)
                .ring(-608, -106)
                .finish(-566, -86, 6)
                .landing(-528.5, 98, -108.5, -90)
                .build(3);
        return new Design(c, t -> {
            t.shape(new Terrain.Plateau(-565, -35, 115, 90, 92, 6));
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
                .text("Hoch über dem Gletscher geht es los, dann hinab in die eisblaue Spalte – unter Eisbögen hindurch, "
                        + "durch ein Eistor im Takt. Ein Aufwind holt dich heraus, oben pfeift der Gletscherwind.",
                        "It starts high above the glacier, then down into the ice-blue crevasse – under ice arches, "
                        + "through a timed ice gate. An updraft lifts you out, up top the glacier wind howls.")
                .icon("blue_ice").biome("minecraft:frozen_peaks").radius(4).maxOff(22)
                .start(0.5, 218, -399.5, 180, 5, 1.3, 2)
                .pitch(6)
                .ring(-4, -446)
                .ring(6, -492)
                .glide(-6, -538, 2.0, 70, true)
                .ring(10, -584)
                .ring(2, -628)
                .gate(-14, -668, 70, 46)
                .ring(-8, -712)
                .ring(10, -752)
                .pitch(2)
                .updraft(22, -792, 8, 1.35, 120, 205)
                .wind(26, -800, 14, -870, 16, 0, 400, -0.014, 0)
                .ring(30, -834)
                .bonus(33, -854, 1500)
                .boost(20, -878, 2.2, 10)
                .ring(-6, -916)
                .finish(-34, -950, 5)
                .landing(-74.5, 172, -980.5, 90)
                .build(4);
        return new Design(c, t -> {
            t.shape(new Terrain.Mound(0, -700, 360, 214, 2.6, 10));
            t.shape(new Terrain.Plateau(0, -650, 140, 90, 200, 2));
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
                .text("Mitten durch den Berg: ein alter Bergwerksstollen mit Stützbalken und Gleisen, in der Mitte stockdunkel "
                        + "– nur die Ringe leuchten, eine Wettertür geht auf und zu. Glück auf!",
                        "Right through the mountain: an old mine tunnel with beams and rails, pitch-dark in the middle – "
                        + "only the rings glow, an air door opens and shuts. Good luck!")
                .icon("rail").biome("minecraft:windswept_hills").radius(3.2).maxOff(20)
                .start(420.5, 168, 450.5, -90, 5, 1.3, 2)
                .pitch(5)
                .ring(466, 452)
                .glide(510, 448, 2.0, 60, false)
                .pitch(2)
                .ring(552, 452)
                .ring(590, 462)
                .ring(626, 458)
                .gate(662, 446, 80, 56)
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
                .text("Die Königsstrecke: rund ums Gipfelkreuz mit kreisendem Ring, durchs Tor in der Felsscharte, Gratwind, "
                        + "zwei Aufwinde und kleine Ringe bis zur Hütte. Nur für Mutige.",
                        "The king of courses: around the summit cross with a circling ring, through the gate in the notch, "
                        + "ridge wind, two updrafts and small rings down to the hut. Only for the brave.")
                .icon("lightning_rod").biome("minecraft:jagged_peaks").radius(3.2).maxOff(20)
                .start(500.5, 258, -500.5, 180, 5, 1.3, 2)
                .pitch(3)
                .ring(505, -548)
                .ring(520, -594)
                .ring(550, -634)
                .ring(590, -664)
                .ring(634, -682)
                .circle(680, -676, 2.2, 100)
                .ring(714, -646)
                .ring(724, -602)
                .pitch(10)
                .ring(718, -556)
                .gate(740, -514, 64, 42)
                .pitch(3)
                .updraft(782, -498, 8, 1.4, 120, 236)
                .ring(826, -510)
                .bonus(854, -522, 2000)
                .boost(858, -546, 2.3, 12)
                .wind(862, -556, 872, -632, 16, 0, 400, -0.012, 0)
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

    // =========================================================================================
    // Felsenlabyrinth – schwer: Slalom um Felsnadeln, durch Felsbögen, in eine Spalte, dicht über den See
    // =========================================================================================

    static Design labyrinth() {
        Course c = new CourseBuilder("felsenlabyrinth", "Felsenlabyrinth", "Rock Labyrinth", Difficulty.SCHWER)
                .text("Slalom um haushohe Felsnadeln, durch zwei Felsbögen, hinab in eine enge Spalte und zum Schluss "
                        + "knapp über dem Bergsee. Wer zu weit ausholt, verliert.",
                        "Slalom round towering rock needles, through two rock arches, down into a narrow cleft and "
                        + "finally skimming the mountain lake. Swing too wide and you lose.")
                .icon("tuff").biome("minecraft:stony_peaks").radius(3.8).maxOff(22)
                .start(-420.5, 196, -600.5, 90, 5, 1.3, 2)
                .pitch(4)
                .ring(-466, -602)
                .ring(-512, -598)
                .pitch(5)
                .ring(-548, -616)
                .ring(-584, -584)
                .ring(-620, -616)
                .ring(-656, -584)
                .pitch(4)
                .ring(-694, -606)
                .updraft(-728, -632, 8, 1.35, 40, 200)
                .ring(-754, -664)
                .pitch(9)
                .ring(-770, -706)
                .glide(-768, -748, 1.6, 60, true)
                .bonus(-772, -776, 1500)
                .boost(-748, -790, 2.2, 4)
                .pitch(7)
                .ring(-706, -812)
                .ring(-662, -826)
                .pitch(-12)
                .finish(-616, -818, 5)
                .landingBelow(-584.5, -848.5, -90, 4)
                .build(4);
        return new Design(c, t -> {
            // Felsmassiv, durch das die Strecke schneidet: Nadeln stehen tief unten im Tal und ragen bis über den Flug
            t.shape(new Terrain.Mound(-620, -640, 230, 238, 1.6, 20));
            t.shape(new Terrain.Mound(-776, -728, 64, 216, 1.3, 10));
            t.shape(new Terrain.Mound(-420, -600, 30, 190, 1.4, 4));
            // Bergsee: Ring 13 streift fast das Wasser, das Ufer liegt einen Block über dem Spiegel
            int lake = (int) Math.floor(c.rings.get(12).center().y() - 6);
            t.shape(new Terrain.Plateau(-676, -820, 64, 28, lake + 1, 1));
            t.lake(new Terrain.Lake(-680, -820, 44, lake));
            t.region(new Terrain.Region(-620, -680, 260, Terrain.Style.ROCK));
        }, List.of(new Design.Cut(0, 30, 7, 1.4), new Design.Cut(2, 16, 9, 1.2), new Design.Cut(6, 14, 4, 1.8),
                new Design.Cut(8, 10, 2.5, 6.5), new Design.Cut(11, 3, 18, 0.6)), Builders::labyrinth, "orange_concrete",
                null);
    }

    // =========================================================================================
    // Höllenschlund – extrem: tiefer Pass, Wind, dunkle Höhle, Takt-Tor, Haarnadeln, knapp übers Wasser
    // =========================================================================================

    static Design schlund() {
        Course c = new CourseBuilder("hoellenschlund", "Höllenschlund", "Devil's Throat", Difficulty.EXTREM)
                .text("Steil hinab in den tiefen Pass, gegen den Fallwind, durch ein Tor im Takt und in die stockdunkle "
                        + "Höhle – nur die Ringe leuchten. Danach Haarnadeln und ganz knapp übers Wasser.",
                        "Dive steeply into the deep pass, fight the downwind, slip through a timed gate into the "
                        + "pitch-dark cave – only the rings glow. Then hairpins and a skim over the water.")
                .icon("crying_obsidian").biome("minecraft:jagged_peaks").radius(3.2).maxOff(18)
                .start(-330.5, 252, 400.5, 90, 5, 1.3, 2)
                .pitch(4)
                .ring(-376, 404)
                .pitch(14)
                .ring(-420, 412)
                .ring(-462, 400)
                .pitch(4)
                .wind(-480, 392, -560, 404, 14, 0, 400, 0, -0.016)
                .ring(-504, 388)
                .glide(-546, 398, 2.2, 70, true)
                .gate(-588, 416, 84, 56)
                .pitch(3)
                .ring(-626, 440)
                .ring(-660, 468)
                .ring(-688, 504)
                .updraft(-702, 542, 8, 1.4, 40, 230)
                .circle(-698, 588, 2.0, 90)
                .pitch(5)
                .ring(-668, 622)
                .ring(-640, 652)
                .ring(-664, 684)
                .bonus(-692, 700, 2000)
                .boost(-704, 728, 2.3, 4)
                .pitch(7)
                .ring(-694, 770)
                .ring(-664, 800)
                .pitch(-12)
                .finish(-622, 812, 4)
                .landingBelow(-588.5, 832.5, -90, 4)
                .build(4);
        int caveFrom = c.tickBefore(6, 22);
        int caveTo = c.tickBefore(9, 0) + 14;
        Box cave = new Box(-700, -64, 418, -606, 400, 528);
        return new Design(c, t -> {
            // zwei hohe Gipfel links und rechts des Passes, der Höhlenberg, Felskessel mit See am Ende
            t.shape(new Terrain.Mound(-440, 330, 90, 300, 1.4, 18));
            t.shape(new Terrain.Mound(-430, 404, 70, 292, 1.3, 14));
            t.shape(new Terrain.Mound(-450, 470, 90, 296, 1.4, 18));
            t.shape(new Terrain.Mound(-650, 470, 120, 252, 1.6, 14));
            t.shape(new Terrain.Mound(-680, 660, 130, 236, 1.5, 18));
            t.shape(new Terrain.Mound(-330, 400, 28, 246, 1.4, 4));
            int lake = (int) Math.floor(c.rings.get(15).center().y() - 6);
            t.shape(new Terrain.Plateau(-680, 786, 60, 26, lake + 1, 1));
            t.lake(new Terrain.Lake(-684, 784, 40, lake));
            t.region(new Terrain.Region(-560, 560, 300, Terrain.Style.ROCK));
        }, List.of(new Design.Cut(0, 10, 6, 1.6), new Design.Cut(1, 4, 3, 4.5), new Design.Cut(3, 12, 6, 1.6),
                new Design.Cut(16, 3, 16, 0.6)), (w, cc) -> Builders.schlund(w, cc, caveFrom, caveTo), "black_concrete",
                cave);
    }
}
