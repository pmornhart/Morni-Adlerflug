package de.moinmorni.adlerflug.course;

import de.moinmorni.adlerflug.flight.Air;
import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Gate;
import de.moinmorni.adlerflug.flight.Glide;
import de.moinmorni.adlerflug.flight.Motion;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.RingCheck;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.flight.Wind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Baukasten für eine Strecke: Man gibt nur die Wegpunkte (x, z) vor, ein nachgebauter Gleitflug ({@link Glide}) fliegt
 * sie ab, und jeder Ring kommt genau dorthin, wo der Flug am Wegpunkt vorbeikommt – mit der Flugrichtung als Ausrichtung.
 * So ist jede Strecke von Anfang an fliegbar, und die Höhen ergeben sich aus der Physik (Gleitwinkel, Aufwind, Wind,
 * Schub).
 *
 * <p>Bewegte Ringe und Takt-Tore richten sich nach der Flugzeit: Der Musterflug kommt genau dann an, wenn der Ring an
 * seiner Mitte steht bzw. das Tor ganz offen ist. Bonus-Ringe liegen abseits: Ein zweiter Flug zweigt ab, fliegt durch
 * den Bonus-Ring und muss den nächsten Pflicht-Ring noch schaffen.
 */
public final class CourseBuilder {

    private static final int MAX_LEG_TICKS = 1600;
    /** Weiter als so weit am Wegpunkt vorbei = Fehler im Entwurf (Kurve zu eng). */
    private static final double MAX_PASS = 4.0;
    /** So viele Ticks fliegt der Musterflug nach dem Ziel noch weiter (zum Ausgleiten). */
    private static final int AFTER_FINISH = 30;
    /** Öffnung der Bonus-Ringe. */
    private static final double BONUS_RADIUS = 3.0;
    private static final int MAX_FLIGHT = 8000;

    /** Zustand eines Gleitflugs im Entwurf (Musterflug oder Abzweig zu einem Bonus-Ring). */
    private static final class State {
        Vec pos;
        Vec vel;
        int tick;
        final List<Vec> path = new ArrayList<>();

        State(Vec pos, Vec vel, int tick) {
            this.pos = pos;
            this.vel = vel;
            this.tick = tick;
        }

        State copy() {
            return new State(pos, vel, tick);
        }
    }

    /** Ein Abzweig zu einem Bonus-Ring, der beim nächsten Pflicht-Ring geprüft wird. */
    private record Fork(Ring ring, State after) {
    }

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
    private String day;
    private Vec wind = Vec.ZERO;
    private Course.Start start;
    private Course.Landing landing;
    private final List<Ring> rings = new ArrayList<>();
    private final List<Ring> bonus = new ArrayList<>();
    private final List<Fork> forks = new ArrayList<>();
    private final List<Updraft> updrafts = new ArrayList<>();
    private final List<Wind> winds = new ArrayList<>();
    private final List<Vec> path = new ArrayList<>();
    private final List<Integer> pathRing = new ArrayList<>();
    private final List<List<Vec>> forkPaths = new ArrayList<>();
    private State main;
    private double pitch;
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

    /** Strecke des Tages: Datum, für das sie gilt. */
    public CourseBuilder day(String date) {
        this.day = date;
        return this;
    }

    /** Wind, der auf der ganzen Strecke drückt (Blöcke pro Tick) – vor {@link #start} setzen. */
    public CourseBuilder wind(Vec push) {
        this.wind = push == null ? Vec.ZERO : push;
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
        this.main = new State(line, launch, 0);
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
        place(x, z, Ring.Kind.NORMAL, radius, 0, 0);
        return this;
    }

    public CourseBuilder ring(double x, double z, double r) {
        place(x, z, Ring.Kind.NORMAL, r, 0, 0);
        return this;
    }

    /** Schub-Ring: danach mindestens {@code power} Blöcke pro Tick, Richtung um {@code up} Grad angehoben. */
    public CourseBuilder boost(double x, double z, double power, double up) {
        place(x, z, Ring.Kind.BOOST, radius, power, up);
        return this;
    }

    /**
     * Ring, der quer zur Flugrichtung hin und her gleitet ({@code vertical}: auf und ab) – um {@code amplitude} Blöcke,
     * eine ganze Bewegung dauert {@code period} Ticks.
     */
    public CourseBuilder glide(double x, double z, double amplitude, double period, boolean vertical) {
        Ring r = place(x, z, Ring.Kind.NORMAL, radius, 0, 0);
        Vec axis = vertical ? r.up() : r.across();
        replaceLast(r.moving(Motion.glide(axis, amplitude, period, anchor())));
        return this;
    }

    /** Ring, der in seiner Ebene im Kreis läuft (Radius {@code amplitude}, eine Runde in {@code period} Ticks). */
    public CourseBuilder circle(double x, double z, double amplitude, double period) {
        Ring r = place(x, z, Ring.Kind.NORMAL, radius, 0, 0);
        replaceLast(r.moving(Motion.circle(r.across(), r.up(), amplitude, period, anchor())));
        return this;
    }

    /** Takt-Tor: offen {@code open} von {@code period} Ticks; der Musterflug kommt mitten in der offenen Zeit. */
    public CourseBuilder gate(double x, double z, double period, double open) {
        Ring r = place(x, z, Ring.Kind.NORMAL, radius, 0, 0);
        replaceLast(r.gated(new Gate(period, open, anchor())));
        return this;
    }

    /**
     * Bonus-Ring abseits der Linie bei (x, z): Ein zweiter Flug zweigt hier ab, fliegt hindurch und muss den nächsten
     * Pflicht-Ring noch treffen (sonst ist der Bonus zu weit weg). Schenkt {@code creditMs}.
     */
    public CourseBuilder bonus(double x, double z, long creditMs) {
        State fork = main.copy();
        Vec[] seg = steer(fork, x, z, null);
        Vec c0 = Course.center(seg[0]);
        Vec c1 = Course.center(seg[1]);
        Ring ring = new Ring(rings.size(), c0.lerp(c1, 0.5), c1.sub(c0), BONUS_RADIUS, Ring.Kind.BONUS, 0, 0, null,
                null, creditMs);
        forks.add(new Fork(ring, fork));
        return this;
    }

    /** Wegpunkt ohne Ring (Kurve um ein Hindernis). */
    public CourseBuilder via(double x, double z) {
        steer(main, x, z, path);
        return this;
    }

    /**
     * Aufwind-Säule bei (x, z) mit Radius und Steiggeschwindigkeit; der Flug geht mitten hindurch. Die Säule reicht von
     * {@code bottom} bis {@code top} (absolute Höhen).
     */
    public CourseBuilder updraft(double x, double z, double r, double rise, double bottom, double top) {
        updrafts.add(new Updraft(x, z, r, bottom, top, rise));
        steer(main, x, z, path);
        return this;
    }

    /**
     * Windzone als Schlauch von (x0, z0) nach (x1, z1) mit Radius {@code r} (von {@code bottom} bis {@code top}); sie
     * drückt jeden Tick um (px, pz) Blöcke pro Tick. Wirkt ab sofort auch auf den Musterflug.
     */
    public CourseBuilder wind(double x0, double z0, double x1, double z1, double r, double bottom, double top, double px,
                              double pz) {
        winds.add(new Wind(x0, z0, x1, z1, r, bottom, top, new Vec(px, 0, pz)));
        return this;
    }

    /** Ziel-Ring. */
    public CourseBuilder finish(double x, double z, double r) {
        place(x, z, Ring.Kind.FINISH, r, 0, 0);
        for (int i = 0; i < AFTER_FINISH; i++) {
            step(main, main.vel.yaw(), path);
        }
        return this;
    }

    public CourseBuilder landing(double x, double y, double z, float yaw) {
        this.landing = new Course.Landing(new Vec(x, y, z), yaw);
        return this;
    }

    /** Landeplatz {@code drop} Blöcke unter dem Ziel-Ring (für Ziele dicht über dem Wasser). */
    public CourseBuilder landingBelow(double x, double z, float yaw, double drop) {
        double y = Math.floor(rings.get(rings.size() - 1).center().y() - drop);
        this.landing = new Course.Landing(new Vec(x, y, z), yaw);
        return this;
    }

    /** Wo der Musterflug gerade ist (Füße) – für Bauten, die sich nach dem Flug richten. */
    public Vec here() {
        return main.pos;
    }

    public List<Ring> rings() {
        return rings;
    }

    // ---------------------------------------------------------------------------------------------

    /** Flugzeit (Ticks), zu der der Musterflug durch den zuletzt gesetzten Ring ging. */
    private double anchor() {
        return main.tick - 0.5;
    }

    private void replaceLast(Ring r) {
        rings.set(rings.size() - 1, r);
    }

    private Ring place(double x, double z, Ring.Kind kind, double r, double power, double up) {
        if (start == null) {
            throw new IllegalStateException(id + ": erst start(…)");
        }
        Vec[] seg = steer(main, x, z, path);
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
            finishMs = Math.round((main.tick - 1 + 0.5) * 50);
        }
        main.vel = ring.boost(main.vel);
        // Der Punkt dieses Ticks liegt schon hinter dem Ring: er gehört zum nächsten Abschnitt.
        pathRing.set(pathRing.size() - 1, rings.size());
        checkForks(ring);
        return ring;
    }

    /** Abzweige zu Bonus-Ringen: Schaffen sie diesen Pflicht-Ring noch? */
    private void checkForks(Ring target) {
        for (Fork f : forks) {
            State s = f.after();
            List<Vec> trail = new ArrayList<>();
            boolean ok = false;
            String why = "nicht erreicht";
            for (int i = 0; i < MAX_LEG_TICKS && !ok; i++) {
                Vec body = Course.center(s.pos);
                Vec d = target.center().sub(body).add(0, -s.vel.y() * 10, 0);
                Vec from = s.pos;
                s.vel = Glide.step(s.vel, d.yaw(), Math.max(-45, Math.min(60, d.pitch())));
                s.pos = s.pos.add(s.vel);
                s.tick++;
                trail.add(s.pos);
                s.vel = drift(Course.center(s.pos), s.vel, s.tick);
                RingCheck.Result r = RingCheck.check(target, Course.center(from), Course.center(s.pos), s.tick - 1);
                if (r.hit() == RingCheck.Hit.THROUGH) {
                    ok = true;
                } else if (r.hit() != RingCheck.Hit.NONE) {
                    why = String.format(Locale.ROOT, "%s um %.1f bei %s", r.hit(), r.distance(), s.pos);
                    break;
                }
            }
            if (!ok) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "%s: vom Bonus-Ring bei %.0f/%.0f/%.0f aus ist Ring %d (%.0f/%.0f/%.0f) nicht zu schaffen: %s", id,
                        f.ring().center().x(), f.ring().center().y(), f.ring().center().z(), target.index() + 1,
                        target.center().x(), target.center().y(), target.center().z(), why));
            }
            bonus.add(new Ring(target.index(), f.ring().center(), f.ring().normal(), f.ring().radius(), Ring.Kind.BONUS,
                    0, 0, null, null, f.ring().credit()));
            List<Vec> fp = new ArrayList<>(f.after().path);
            fp.addAll(trail);
            forkPaths.add(fp);
        }
        forks.clear();
    }

    /** Fliegt bis zur größten Annäherung an (x, z); gibt die letzten beiden Füße-Punkte zurück (davor, danach). */
    private Vec[] steer(State s, double x, double z, List<Vec> out) {
        double before = Math.hypot(s.pos.x() - x, s.pos.z() - z);
        Vec last = s.pos;
        for (int i = 0; i < MAX_LEG_TICKS; i++) {
            Vec from = s.pos;
            double yaw = new Vec(x - s.pos.x(), 0, z - s.pos.z()).yaw();
            step(s, yaw, out == null ? s.path : out);
            double d = Math.hypot(s.pos.x() - x, s.pos.z() - z);
            if (d > before && i > 0) {
                if (before > MAX_PASS) {
                    throw new IllegalStateException(String.format(Locale.ROOT,
                            "%s: Wegpunkt %.0f/%.0f um %.1f Blöcke verfehlt (Kurve zu eng) – Ring %d", id, x, z, before,
                            rings.size() + 1));
                }
                return new Vec[] {from, s.pos};
            }
            before = d;
            last = s.pos;
        }
        throw new IllegalStateException(id + ": Wegpunkt " + x + "/" + z + " nicht erreicht (" + last + ")");
    }

    private void step(State s, double yaw, List<Vec> out) {
        s.vel = Glide.step(s.vel, yaw, pitch);
        s.pos = s.pos.add(s.vel);
        s.tick++;
        s.vel = drift(Course.center(s.pos), s.vel, s.tick);
        if (out == path) {
            record();
        } else {
            out.add(s.pos);
        }
        if (s.pos.y() < -50) {
            throw new IllegalStateException(id + ": Musterflug stürzt ab bei " + s.pos);
        }
    }

    private Vec drift(Vec center, Vec v, int tick) {
        return new Air(List.of(), List.of(), updrafts, winds, wind).drift(center, v, tick);
    }

    private void record() {
        path.add(main.pos);
        pathRing.add(rings.size());
    }

    /**
     * Fertige Strecke: Ringe in {@code sections} gleich große Abschnitte (Farben), Medaillen aus dem Musterflug und den
     * Prüf-Piloten (ohne Landschaft – {@link #medals(Course, FlightSim.Solid)} rechnet sie mit Landschaft nach).
     */
    public Course build(int sections) {
        if (finishMs < 0 || landing == null) {
            throw new IllegalStateException(id + ": Ziel oder Landeplatz fehlt");
        }
        if (!forks.isEmpty()) {
            throw new IllegalStateException(id + ": Bonus-Ring ohne folgenden Pflicht-Ring");
        }
        List<Ring> out = new ArrayList<>();
        int n = rings.size();
        for (Ring r : rings) {
            int section = r.finish() ? sections - 1 : Math.min(sections - 1, r.index() * sections / Math.max(1, n - 1));
            out.add(r.renumber(r.index(), section));
        }
        float[] p = new float[path.size() * 3];
        int[] pr = new int[path.size()];
        for (int i = 0; i < path.size(); i++) {
            p[i * 3] = (float) path.get(i).x();
            p[i * 3 + 1] = (float) path.get(i).y();
            p[i * 3 + 2] = (float) path.get(i).z();
            pr[i] = pathRing.get(i);
        }
        Course draft = new Course(id, nameDe, nameEn, textDe, textEn, difficulty, icon, biome, start, out, bonus,
                updrafts, winds, wind, landing, medals(finishMs, finishMs), p, pr, null, finishMs, finishMs, maxOff, day);
        draft = timeGates(draft, out, p, pr);
        if (!bonus.isEmpty()) {
            FlightSim.Result hunt = FlightSim.fly(start.line(), start.launch(), draft.air(), Pilots.hunter(draft), null,
                    MAX_FLIGHT);
            if (!hunt.finished() || hunt.bonusCount() < bonus.size()) {
                throw new IllegalStateException(id + ": Flug über alle Bonus-Ringe klappt nicht (" + hunt.bonusCount()
                        + "/" + bonus.size() + ", " + hunt.problem() + ")");
            }
            // wie beim Musterflug nach dem Ziel noch ein Stück weiter (der Autopilot braucht den Punkt hinter dem Ziel)
            List<Vec> way = new ArrayList<>(hunt.path());
            Vec last = way.get(way.size() - 1);
            Vec step = last.sub(way.get(way.size() - 2));
            for (int i = 1; i <= AFTER_FINISH; i++) {
                way.add(last.add(step.mul(i)));
            }
            float[] bp = new float[way.size() * 3];
            for (int i = 0; i < way.size(); i++) {
                Vec v = way.get(i);
                bp[i * 3] = (float) v.x();
                bp[i * 3 + 1] = (float) v.y();
                bp[i * 3 + 2] = (float) v.z();
            }
            draft = draft.withBonusPath(bp);
        }
        return medals(draft, null);
    }

    /**
     * Takt-Tore: Der Musterflug (Test-Autopilot) und der einfache Pilot kommen zu verschiedenen Zeiten an. Das Tor ist
     * genau in der Mitte zwischen beiden am weitesten offen – so kommen beide durch, und wer schneller oder langsamer
     * ist, muss sein Tempo anpassen.
     */
    private Course timeGates(Course draft, List<Ring> out, float[] p, int[] pr) {
        if (out.stream().noneMatch(r -> r.gate() != null)) {
            return draft;
        }
        List<Ring> open = new ArrayList<>();
        for (Ring r : out) {
            open.add(r.gate() == null ? r : new Ring(r.index(), r.center(), r.normal(), r.radius(), r.kind(), r.section(),
                    r.power(), r.motion(), null, r.credit()));
        }
        Course loose = new Course(id, nameDe, nameEn, textDe, textEn, difficulty, icon, biome, start, open, bonus,
                updrafts, winds, wind, landing, draft.medals, p, pr, null, finishMs, finishMs, maxOff, day);
        FlightSim.Result aim = FlightSim.fly(start.line(), start.launch(), loose.air(), Pilots.aim(loose), null,
                MAX_FLIGHT);
        List<Ring> timed = new ArrayList<>();
        for (Ring r : out) {
            if (r.gate() == null || aim.ringTicks()[r.index()] < 0) {
                timed.add(r);
                continue;
            }
            double atAim = aim.ringTicks()[r.index()] - 1 + aim.ringFrac()[r.index()];
            double anchor = (r.gate().anchor() + atAim) / 2;
            if (Math.abs(r.gate().anchor() - atAim) > r.gate().open() - Gate.SWING * 2) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "%s: Takt-Tor %d – Musterflug (%.0f) und Pilot (%.0f) kommen zu weit auseinander an", id,
                        r.index() + 1, r.gate().anchor(), atAim));
            }
            timed.add(r.gated(new Gate(r.gate().period(), r.gate().open(), anchor)));
        }
        out.clear();
        out.addAll(timed);
        return new Course(id, nameDe, nameEn, textDe, textEn, difficulty, icon, biome, start, out, bonus, updrafts,
                winds, wind, landing, draft.medals, p, pr, null, finishMs, finishMs, maxOff, day);
    }

    /** Wege der Abzweige zu den Bonus-Ringen (Füße), für die Flugschneisen der Landschaft. */
    public List<List<Vec>> forkPaths() {
        return forkPaths;
    }

    /**
     * Medaillen aus dem Musterflug und den Prüf-Piloten: Der einfache Pilot und der Musterflug bestimmen Adler bis
     * Bronze, der beste geübte Pilot (Kurven anschneiden, Tempo holen, Bonus-Ringe) den Steinadler. Mit
     * {@code solid} zählen nur Flüge, die nirgends anstoßen.
     */
    public static Course medals(Course c, FlightSim.Solid solid) {
        long design = Math.round((c.tickBefore(c.ringCount(), 0) - 0.5) * 50);
        FlightSim.Result aim = FlightSim.fly(c.start.line(), c.start.launch(), c.air(), Pilots.aim(c), solid, MAX_FLIGHT);
        long base = aim.finished() ? Math.min(design, aim.scoreMs()) : design;
        List<long[]> tries = new ArrayList<>();
        List<FlightSim.Pilot> experts = Pilots.experts(c);
        for (int i = 0; i < experts.size(); i++) {
            FlightSim.Result r = FlightSim.fly(c.start.line(), c.start.launch(), c.air(), experts.get(i), null,
                    MAX_FLIGHT);
            if (r.finished()) {
                tries.add(new long[] {r.scoreMs(), i});
            }
        }
        tries.sort(Comparator.comparingLong(a -> a[0]));
        long expert = base;
        for (long[] t : tries) {
            if (t[0] >= expert) {
                break;
            }
            if (solid == null) {
                expert = t[0];
                break;
            }
            // nur Flüge, die nirgends anstoßen (Piloten neu anlegen: sie merken sich unterwegs etwas)
            FlightSim.Result check = FlightSim.fly(c.start.line(), c.start.launch(), c.air(),
                    Pilots.experts(c).get((int) t[1]), solid, MAX_FLIGHT);
            if (check.finished()) {
                expert = check.scoreMs();
                break;
            }
        }
        return c.withMedals(medals(base, expert), base, expert);
    }

    /**
     * Medaillen: Bronze, Silber, Gold und Adler aus der Zeit des einfachen Piloten bzw. des Musterflugs (Adler etwas
     * schneller als er), der Steinadler so schnell wie der beste geübte Pilot. Jede Stufe mindestens eine Viertelsekunde
     * schneller als die vorige.
     */
    static Course.Medals medals(long base, long expert) {
        long stein = round(Math.min(base, expert));
        long adler = Math.max(round(base * 0.98), stein + 250);
        long gold = Math.max(round(base * 1.06), adler + 250);
        long silber = Math.max(round(base * 1.15), gold + 250);
        long bronze = Math.max(round(base * 1.30), silber + 250);
        return new Course.Medals(bronze, silber, gold, adler, stein);
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
