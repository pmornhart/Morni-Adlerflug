package de.moinmorni.adlerflug.course;

/** Schwierigkeit einer Strecke (Farbe und Symbol in Menüs und Tafeln). */
public enum Difficulty {
    LEICHT("Leicht", "Easy", "#5fd068", "lime_concrete"),
    MITTEL("Mittel", "Medium", "#f5c542", "yellow_concrete"),
    SCHWER("Schwer", "Hard", "#ff8a3d", "orange_concrete"),
    EXTREM("Extrem", "Extreme", "#ff4d5e", "red_concrete");

    public final String de;
    public final String en;
    public final String colour;
    public final String block;

    Difficulty(String de, String en, String colour, String block) {
        this.de = de;
        this.en = en;
        this.colour = colour;
        this.block = block;
    }

    public String name(String lang) {
        return "en".equals(lang) ? en : de;
    }

    /** ★☆☆☆ bis ★★★★. */
    public String stars() {
        return "★".repeat(ordinal() + 1) + "☆".repeat(values().length - ordinal() - 1);
    }
}
