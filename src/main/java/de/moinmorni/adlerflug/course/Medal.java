package de.moinmorni.adlerflug.course;

/** Medaillen einer Strecke, von gut bis am besten. */
public enum Medal {
    BRONZE("Bronze", "Bronze", "#d08a52", "●"),
    SILBER("Silber", "Silver", "#c9d3dc", "●"),
    GOLD("Gold", "Gold", "#ffd23f", "●"),
    ADLER("Adler", "Eagle", "#f4a259", "▲");

    public final String de;
    public final String en;
    public final String colour;
    public final String symbol;

    Medal(String de, String en, String colour, String symbol) {
        this.de = de;
        this.en = en;
        this.colour = colour;
        this.symbol = symbol;
    }

    public String name(String lang) {
        return "en".equals(lang) ? en : de;
    }

    /** Zeitgrenze dieser Medaille. */
    public long limit(Course.Medals m) {
        return switch (this) {
            case BRONZE -> m.bronze();
            case SILBER -> m.silber();
            case GOLD -> m.gold();
            case ADLER -> m.adler();
        };
    }

    /** Beste Medaille für die Zeit, null wenn es nicht einmal für Bronze reicht. */
    public static Medal of(long ms, Course.Medals m) {
        if (ms <= 0) {
            return null;
        }
        Medal best = null;
        for (Medal medal : values()) {
            if (ms <= medal.limit(m)) {
                best = medal;
            }
        }
        return best;
    }

    /** Nächstbessere Medaille (null beim Adler). */
    public static Medal next(Medal current) {
        if (current == null) {
            return BRONZE;
        }
        return current == ADLER ? null : values()[current.ordinal() + 1];
    }

    /** Wie viele Stufen bis hierher (0 ohne Medaille, 4 beim Adler). */
    public static int tiers(Medal m) {
        return m == null ? 0 : m.ordinal() + 1;
    }
}
