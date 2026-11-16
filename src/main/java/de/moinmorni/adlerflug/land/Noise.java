package de.moinmorni.adlerflug.land;

import java.util.SplittableRandom;

/**
 * Glattes Rauschen (Gradienten-Rauschen in 2D) mit festem Startwert – dieselbe Landschaft auf jedem Server und nach
 * jedem Neustart. Werte etwa −1 … 1. Dazu Schichten ({@link #fbm}) und Grate ({@link #ridged}) für Berge.
 */
public final class Noise {

    private final int[] perm = new int[512];

    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        SplittableRandom r = new SplittableRandom(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double grad(int hash, double x, double y) {
        return switch (hash & 7) {
            case 0 -> x + y;
            case 1 -> -x + y;
            case 2 -> x - y;
            case 3 -> -x - y;
            case 4 -> x;
            case 5 -> -x;
            case 6 -> y;
            default -> -y;
        };
    }

    /** Rauschen an (x, y), etwa −1 … 1. */
    public double at(double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double xf = x - xi;
        double yf = y - yi;
        xi &= 255;
        yi &= 255;
        double u = fade(xf);
        double v = fade(yf);
        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];
        double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }

    /** Mehrere Schichten (feiner werdend), etwa −1 … 1. */
    public double fbm(double x, double y, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += at(x * f + i * 17.3, y * f - i * 9.1) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return sum / norm;
    }

    /** Grate: scharfe Kämme wie Bergrücken, 0 … 1. */
    public double ridged(double x, double y, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double f = 1;
        double weight = 1;
        for (int i = 0; i < octaves; i++) {
            double n = 1 - Math.abs(at(x * f + i * 31.7, y * f + i * 5.3));
            n *= n;
            n *= weight;
            weight = Math.max(0, Math.min(1, n * 1.6));
            sum += n * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.1;
        }
        return sum / norm;
    }

    /** Fester Zufallswert für eine Zelle (0 … 1), z. B. ob dort ein Baum steht. */
    public static double hash(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Weicher Übergang 0 → 1 zwischen {@code edge0} und {@code edge1}. */
    public static double smooth(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }
}
