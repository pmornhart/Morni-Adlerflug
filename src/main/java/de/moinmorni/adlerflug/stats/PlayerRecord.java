package de.moinmorni.adlerflug.stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alles, was Adlerflug über einen Spieler speichert (lokal, damit es auch ohne Netzwerk geht): Bestzeiten mit
 * Zwischenzeiten an jedem Ring je Strecke, Zähler, Woche, Einstellungen. Als einfache Map für YAML, ohne Bukkit.
 */
public final class PlayerRecord {

    /** Werte je Strecke. */
    public static final class CourseStats {
        public long best;
        public long bestWhen;
        public List<Long> splits = new ArrayList<>();
        public int finishes;
        public int flights;
        public int resets;
        /** Fingerabdruck der Strecke, zu der die Bestzeit gehört ({@code Course#signature}). */
        public String sig = "";
    }

    public final UUID uuid;
    public String name;
    public int flights;
    public int finishes;
    public int resets;
    public long rings;
    public final Map<String, CourseStats> courses = new LinkedHashMap<>();
    /** Woche, für die {@link #weekBest} und {@link #weekDone} gelten (z. B. „2026-W47“). */
    public String weekId = "";
    public String weekCourse = "";
    public long weekBest;
    public long weekBestWhen;
    public boolean weekDone;
    public boolean ghost = true;
    public boolean hideOthers;
    /** Strecke des Tages: Tag (z. B. „2026-11-16“), für den die Werte von „tagesflug“ gelten. */
    public String dayId = "";
    /** An so vielen Tagen die Strecke des Tages geschafft. */
    public int days;

    public PlayerRecord(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public CourseStats course(String id) {
        return courses.computeIfAbsent(id, k -> new CourseStats());
    }

    public long best(String course) {
        CourseStats s = courses.get(course);
        return s == null ? 0 : s.best;
    }

    /** Woche wechseln: alte Wochenwerte verfallen. */
    public void enterWeek(String id, String course) {
        if (!id.equals(weekId)) {
            weekId = id;
            weekCourse = course;
            weekBest = 0;
            weekBestWhen = 0;
            weekDone = false;
        }
    }

    /**
     * Tag wechseln: Bestzeit und Zwischenzeiten der Strecke des Tages ({@code daily}) gelten nur für einen Tag (Zähler
     * wie Flüge und Ziele bleiben).
     */
    public void enterDay(String id, String daily) {
        if (!id.equals(dayId)) {
            dayId = id;
            CourseStats s = courses.get(daily);
            if (s != null) {
                s.best = 0;
                s.bestWhen = 0;
                s.splits = new ArrayList<>();
                s.sig = "";
            }
        }
    }

    /** Hat der Spieler in der Woche {@code id} die Strecke der Woche geschafft? (Abzeichen „Wochen-Adler“) */
    public boolean weeklyDone(String id) {
        return id.equals(weekId) && weekDone;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("flights", flights);
        m.put("finishes", finishes);
        m.put("resets", resets);
        m.put("rings", rings);
        Map<String, Object> cs = new LinkedHashMap<>();
        courses.forEach((id, s) -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("best", s.best);
            c.put("best-when", s.bestWhen);
            c.put("splits", new ArrayList<>(s.splits));
            c.put("finishes", s.finishes);
            c.put("flights", s.flights);
            c.put("resets", s.resets);
            c.put("sig", s.sig);
            cs.put(id, c);
        });
        m.put("courses", cs);
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("id", weekId);
        w.put("course", weekCourse);
        w.put("best", weekBest);
        w.put("best-when", weekBestWhen);
        w.put("done", weekDone);
        m.put("week", w);
        m.put("ghost", ghost);
        m.put("hide-others", hideOthers);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", dayId);
        d.put("days", days);
        m.put("day", d);
        return m;
    }

    public static PlayerRecord fromMap(UUID uuid, Map<String, Object> m) {
        PlayerRecord r = new PlayerRecord(uuid, str(m.get("name"), "?"));
        r.flights = (int) num(m.get("flights"));
        r.finishes = (int) num(m.get("finishes"));
        r.resets = (int) num(m.get("resets"));
        r.rings = num(m.get("rings"));
        if (m.get("courses") instanceof Map<?, ?> courses) {
            courses.forEach((k, v) -> {
                if (v instanceof Map<?, ?> c) {
                    CourseStats s = r.course(String.valueOf(k));
                    s.best = num(c.get("best"));
                    s.bestWhen = num(c.get("best-when"));
                    if (c.get("splits") instanceof List<?> list) {
                        for (Object o : list) {
                            s.splits.add(num(o));
                        }
                    }
                    s.finishes = (int) num(c.get("finishes"));
                    s.flights = (int) num(c.get("flights"));
                    s.resets = (int) num(c.get("resets"));
                    s.sig = str(c.get("sig"), "");
                }
            });
        }
        if (m.get("week") instanceof Map<?, ?> w) {
            r.weekId = str(w.get("id"), "");
            r.weekCourse = str(w.get("course"), "");
            r.weekBest = num(w.get("best"));
            r.weekBestWhen = num(w.get("best-when"));
            r.weekDone = Boolean.TRUE.equals(w.get("done"));
        }
        r.ghost = !Boolean.FALSE.equals(m.get("ghost"));
        r.hideOthers = Boolean.TRUE.equals(m.get("hide-others"));
        if (m.get("day") instanceof Map<?, ?> d) {
            r.dayId = str(d.get("id"), "");
            r.days = (int) num(d.get("days"));
        }
        return r;
    }

    private static long num(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static String str(Object o, String fallback) {
        return o == null ? fallback : String.valueOf(o);
    }
}
