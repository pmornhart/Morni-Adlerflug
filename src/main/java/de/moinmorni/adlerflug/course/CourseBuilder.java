package de.moinmorni.adlerflug.course;

import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Glide;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import java.util.ArrayList;
import java.util.List;

/**
 * Baukasten für eine Strecke: Man gibt nur die Wegpunkte (x, z) vor, ein nachgebauter Gleitflug ({@link Glide}) fliegt
 * sie ab, und jeder Ring kommt genau dorthin, wo der Flug am Wegpunkt vorbeikommt – mit der Flugrichtung als Ausrichtung.
 * So ist jede Strecke von Anfang an fliegbar, und die Höhen ergeben sich aus der Physik (Gleitwinkel, Aufwind, Schub).
 */
public final class CourseBuilder {

    private static final int MAX_LEG_TICKS = 1600;
    /** Weiter als so weit am Wegpunkt vorbei = Fehler im Entwurf (Kurve zu eng). */
    private static final double MAX_PASS = 4.0;
    /** So viele Ticks fliegt der Musterflug nach dem Ziel noch weiter (zum Ausgleiten). */
    private static final int AFTER_FINISH = 30;

    private final String id;
    private final String nameDe;
    private final String nameEn;
    private final Difficulty difficulty;
    private String textDe = "";
    private String textEn = "";
    private String icon = "feather";
    private String biome = "minecraft:meadow";
    private double radius = 5;
    private double maxOff = 30;
    private Course.Start start;
    private Course.Landing landing;
    private final List<Ring> rings = new ArrayList<>();
    private final List<Updraft> updrafts = new ArrayList<>();
    private final List<Vec> path = new ArrayList<>();
    private final List<Integer> pathRing = new ArrayList<>();
    private Vec pos;
    private Vec vel;
    private double pitch;
    private int tick;
    private long finishMs = -1;

    public CourseBuilder(String id, String nameDe, String nameEn, Difficulty difficulty) {
        this.id = id;
        this.nameDe = nameDe;
        this.nameEn = nameEn;
        this.difficulty = difficulty;
    }

    public CourseBuilder text(String de, String en) {
        this.textDe = de;
        this.textEn = en;
        return this;
    }

    public CourseBuilder icon(String material) {
        this.icon = material;
        return this;
    }

    public CourseBuilder biome(String key) {
        this.biome = key;
        return this;
    }

    /** Öffnung der folgenden Ringe (Blöcke bis zum inneren Rand). */
    public CourseBuilder radius(double r) {
        this.radius = r;
        return this;
    }

    public CourseBuilder maxOff(double d) {
        this.maxOff = d;
        return this;
    }

    /**
     * Absprung: Kante der Schanze bei (x, y, z) (Füße auf Höhe y), Flug in Richtung {@code yaw}. Der Stehplatz liegt
     * {@code runUp} Blöcke dahinter; der Startbereich reicht bis zur Kante.
     *
     * @param speed Schwung beim Absprung (Blöcke pro Tick)
     * @param up    Absprung leicht nach oben (Grad)
     */
    public CourseBuilder start(double x, double y, double z, float yaw, double runUp, double speed, double up) {
        Vec dir = Vec.look(yaw, 0);
        Vec side = new Vec(dir.z(), 0, -dir.x());
        Vec line = new Vec(x, y, z);
        Vec stand = line.sub(dir.mul(runUp));
        Vec a = line.sub(dir.mul(runUp + 2)).add(side.mul(3)).add(0, -0.5, 0);
        Vec b = line.add(side.mul(-3)).add(0, 3, 0);
        Box area = Box.around(a, b);
        Vec launch = Vec.look(yaw, -up).mul(speed);
        this.start = new Course.Start(stand, yaw, area, launch, line);
        this.pos = line;
        this.vel = launch;
        this.tick = 0;
        record();
        return this;
    }

    /** Neigung der folgenden Abschnitte (Grad, positiv = Nase nach unten, 0 = weit gleiten). */
    public CourseBuilder pitch(double deg) {
        this.pitch = deg;
        return this;
    }

    /** Ring an der Stelle, wo der Flug am Wegpunkt (x, z) vorbeikommt. */
    public CourseBuilder ring(double x, double z) {
        return place(x, z, Ring.Kind.NORMAL, radius, 0, 0);
    }

    public CourseBuilder ring(double x, double z, double r) {
        return place(x, z, Ring.Kind.NORMAL, r, 0, 0);
    }

    /** Schub-Ring: danach mindestens {@code power} Blöcke pro Tick, Richtung um {@code up} Grad angehoben. */
    public CourseBuilder boost(double x, double z, double power, double up) {
        return place(x, z, Ring.Kind.BOOST, radius, power, up);
    }

    /** Wegpunkt ohne Ring (Kurve um ein Hindernis). */
    public CourseBuilder via(double x, double z) {
        steer(x, z);
        return this;
    }

    /**
     * Aufwind-Säule bei (x, z) mit Radius und Steiggeschwindigkeit; der Flug geht mitten hindurch. Die Säule reicht von
     * {@code bottom} bis {@code top} (absolute Höhen).
     */
    public CourseBuilder updraft(double x, double z, double r, double rise, double bottom, double top) {
        updrafts.add(new Updraft(x, z, r, bottom, top, rise));
        steer(x, z);
        return this;
    }

    /** Ziel-Ring. */
    public CourseBuilder finish(double x, double z, double r) {
        place(x, z, Ring.Kind.FINISH, r, 0, 0);
        for (int i = 0; i < AFTER_FINISH; i++) {
            step(vel.yaw());
        }
        return this;
    }

    public CourseBuilder landing(double x, double y, double z, float yaw) {
        this.landing = new Course.Landing(new Vec(x, y, z), yaw);
        return this;
    }

    /** Wo der Musterflug gerade ist (Füße) – für Bauten, die sich nach dem Flug richten. */
    public Vec here() {
        return pos;
    }

    public List<Ring> rings() {
        return rings;
    }

    // ---------------------------------------------------------------------------------------------

    private CourseBuilder place(double x, double z, Ring.Kind kind, double r, double power, double up) {
        if (start == null) {
            throw new IllegalStateException(id + ": erst start(…)");
        }
        Vec[] seg = steer(x, z);
        Vec c0 = Course.center(seg[0]);
        Vec c1 = Course.center(seg[1]);
        Vec dir = c1.sub(c0).normalize();
        Vec normal = dir;
        if (up != 0) {
            normal = Vec.look(dir.yaw(), dir.pitch() - up);
        }
        Ring ring = new Ring(rings.size(), c0.lerp(c1, 0.5), normal, r, kind, 0, power);
        rings.add(ring);
        if (kind == Ring.Kind.FINISH) {
            finishMs = Math.round((tick - 1 + 0.5) * 50);
        }
        vel = ring.boost(vel);
        // Der Punkt dieses Ticks liegt schon hinter dem Ring: er gehört zum nächsten Abschnitt.
        pathRing.set(pathRing.size() - 1, rings.size());
        return this;
    }

    /** Fliegt bis zur größten Annäherung an (x, z); gibt die letzten beiden Füße-Punkte zurück (davor, danach). */
    private Vec[] steer(double x, double z) {
        double before = Math.hypot(pos.x() - x, pos.z() - z);
        Vec last = pos;
        for (int i = 0; i < MAX_LEG_TICKS; i++) {
            Vec from = pos;
            double yaw = new Vec(x - pos.x(), 0, z - pos.z()).yaw();
            step(yaw);
            double d = Math.hypot(pos.x() - x, pos.z() - z);
            if (d > before && i > 0) {
                if (before > MAX_PASS) {
                    throw new IllegalStateException(String.format(java.util.Locale.ROOT,
                            "%s: Wegpunkt %.0f/%.0f um %.1f Blöcke verfehlt (Kurve zu eng) – Ring %d", id, x, z, before,
                            rings.size() + 1));
                }
                return new Vec[] {from, pos};
            }
            before = d;
            last = pos;
        }
        throw new IllegalStateException(id + ": Wegpunkt " + x + "/" + z + " nicht erreicht (" + last + ")");
    }

    private void step(double yaw) {
        vel = Glide.step(vel, yaw, pitch);
        pos = pos.add(vel);
        tick++;
        Vec c = Course.center(pos);
        for (Updraft u : updrafts) {
            if (u.contains(c)) {
                vel = u.lift(vel);
            }
        }
        record();
        if (pos.y() < -50) {
            throw new IllegalStateException(id + ": Musterflug stürzt ab bei " + pos);
        }
    }

    private void record() {
        path.add(pos);
        pathRing.add(rings.size());
    }

    /**
     * Fertige Strecke: Ringe in {@code sections} gleich große Abschnitte (Farben), Medaillen aus dem Musterflug.
     */
    public Course build(int sections) {
        if (finishMs < 0 || landing == null) {
            throw new IllegalStateException(id + ": Ziel oder Landeplatz fehlt");
        }
        List<Ring> out = new ArrayList<>();
        int n = rings.size();
        for (Ring r : rings) {
            int section = r.finish() ? sections - 1 : Math.min(sections - 1, r.index() * sections / Math.max(1, n - 1));
            out.add(new Ring(r.index(), r.center(), r.normal(), r.radius(), r.kind(), section, r.power()));
        }
        float[] p = new float[path.size() * 3];
        int[] pr = new int[path.size()];
        for (int i = 0; i < path.size(); i++) {
            p[i * 3] = (float) path.get(i).x();
            p[i * 3 + 1] = (float) path.get(i).y();
            p[i * 3 + 2] = (float) path.get(i).z();
            pr[i] = pathRing.get(i);
        }
        Course draft = new Course(id, nameDe, nameEn, textDe, textEn, difficulty, icon, biome, start, out, updrafts,
                landing, medals(finishMs), p, pr, finishMs, maxOff);
        // Die Medaillen richten sich nach dem schnelleren von zwei Piloten: dem Musterflug und dem, der direkt auf die
        // Ringe zielt (wie ein geübter Spieler).
        FlightSim.Result aim = FlightSim.fly(start.line(), start.launch(), out, updrafts, Pilots.aim(draft), null, 8000);
        long best = aim.finished() ? Math.min(finishMs, aim.finishMs()) : finishMs;
        return new Course(id, nameDe, nameEn, textDe, textEn, difficulty, icon, biome, start, out, updrafts, landing,
                medals(best), p, pr, best, maxOff);
    }

    /** Medaillen aus der besten Zeit der Piloten: Adler so schnell wie sie, darunter großzügiger. */
    static Course.Medals medals(long best) {
        return new Course.Medals(round(best * 1.5), round(best * 1.25), round(best * 1.1), round(best * 1.0));
    }

    /** Auf eine Viertelsekunde aufrunden. */
    private static long round(double ms) {
        return (long) (Math.ceil(ms / 250.0) * 250);
    }

    /** Für Tests und Bauten: die Flugbahn bis jetzt (Füße). */
    public List<Vec> pathSoFar() {
        return List.copyOf(path);
    }
}
