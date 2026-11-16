package de.moinmorni.adlerflug.flight;

/**
 * Gleitflug mit Flügeln, nachgebaut wie im Spiel (Vanilla, ein Schritt pro Tick): Schwerkraft, Auftrieb aus der
 * Neigung, Umlenken in Blickrichtung, Luftwiderstand. Damit prüfen die Tests ohne Server, dass jede Strecke fliegbar
 * ist, und daraus entstehen die Musterflüge für Medaillen und den Test-Spieler.
 *
 * <p>Der Client rechnet genau so (LivingEntity, Gleiten): Neigung positiv = Nase nach unten.
 */
public final class Glide {

    /** Schwerkraft eines Spielers pro Tick. */
    public static final double GRAVITY = 0.08;

    private Glide() {
    }

    /** Neue Geschwindigkeit nach einem Tick Gleiten mit Blick {@code yaw}/{@code pitch} (Grad). */
    public static Vec step(Vec v, double yawDeg, double pitchDeg) {
        Vec look = Vec.look(yawDeg, pitchDeg);
        double pitch = Math.toRadians(pitchDeg);
        double d = Math.sqrt(look.x() * look.x() + look.z() * look.z());
        double e = v.horizontal();
        double h = Math.cos(pitch);
        h = h * h;
        double vx = v.x();
        double vy = v.y() + GRAVITY * (-1.0 + h * 0.75);
        double vz = v.z();
        if (vy < 0.0 && d > 0.0) {
            double i = vy * -0.1 * h;
            vx += look.x() * i / d;
            vy += i;
            vz += look.z() * i / d;
        }
        if (pitch < 0.0 && d > 0.0) {
            double i = e * -Math.sin(pitch) * 0.04;
            vx += -look.x() * i / d;
            vy += i * 3.2;
            vz += -look.z() * i / d;
        }
        if (d > 0.0) {
            vx += (look.x() / d * e - vx) * 0.1;
            vz += (look.z() / d * e - vz) * 0.1;
        }
        return new Vec(vx * 0.99, vy * 0.98, vz * 0.99);
    }

    /** Geschwindigkeit in km/h (1 Block = 1 m, 20 Ticks pro Sekunde). */
    public static int kmh(Vec perTick) {
        return (int) Math.round(perTick.length() * 20 * 3.6);
    }
}
