package de.moinmorni.adlerflug.world;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.courses.World;
import de.moinmorni.adlerflug.land.Terrain;
import de.moinmorni.adlerflug.land.Trees;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Erzeugt die Adlerflug-Welt Chunk für Chunk aus dem Modell: Berge aus {@link Terrain} (Fels, Erde, Wiese, Schnee,
 * Gletscher), Wasser, Blumen und Bäume, darüber der Bauplan (Ringe, Türme, Burg, Stollen …). Es gibt keinen langen Bau
 * beim Start – Paper erzeugt die Chunks parallel, wenn jemand hinkommt.
 */
public final class FlightGenerator extends ChunkGenerator {

    /** Blöcke des Plans in einem Chunk: Position (x 4 Bit, z 4 Bit, y Rest) und Nummer in der Palette. */
    record ChunkBlocks(int[] pos, short[] palette) {
    }

    private final World model;
    private final Map<Long, ChunkBlocks> chunks;
    private final BlockData[] palette;
    private final Map<String, BlockData> cache = new ConcurrentHashMap<>();

    FlightGenerator(World model, Map<Long, ChunkBlocks> chunks, BlockData[] palette) {
        this.model = model;
        this.chunks = chunks;
        this.palette = palette;
        for (String s : new String[] {"stone", "dirt", "grass_block", "snow_block", "packed_ice", "blue_ice", "andesite",
            "tuff", "cobblestone", "gravel", "coarse_dirt", "moss_block", "mossy_cobblestone", "water", "bedrock",
            "snow[layers=1]", "snow[layers=2]", "calcite", "podzol", "deepslate"}) {
            data(s);
        }
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private BlockData data(String s) {
        return cache.computeIfAbsent(s, k -> Bukkit.createBlockData(k.contains(":") ? k : "minecraft:" + k));
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        Terrain t = model.terrain();
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int min = info.getMinHeight();
        double[][] h = new double[18][18];
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                h[i][j] = t.heightExact(x0 + i - 1, z0 + j - 1);
            }
        }
        BlockData stone = data("stone");
        BlockData bedrock = data("bedrock");
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx;
                int z = z0 + lz;
                double he = h[lx + 1][lz + 1];
                int top = (int) Math.floor(he);
                double slope = 0;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    slope = Math.max(slope, Math.abs(h[lx + 1 + d[0]][lz + 1 + d[1]] - he));
                }
                String surface = t.surface(x, z, top, slope);
                Terrain.Style style = t.style(x, z);
                int deep = style == Terrain.Style.GLACIER ? 14 : 4;
                data.setBlock(lx, min, lz, bedrock);
                data.setRegion(lx, min + 1, lz, lx + 1, Math.max(min + 1, top - deep), lz + 1, stone);
                String under = style == Terrain.Style.GLACIER ? null : t.under(surface);
                for (int y = Math.max(min + 1, top - deep); y < top; y++) {
                    String b = under;
                    if (b == null) {
                        // Gletscher: Eis in Schichten, blaues Eis in Adern
                        double n = de.moinmorni.adlerflug.land.Noise.hash(t.seed() + 5, x * 7 + y, z);
                        b = n < 0.05 ? "blue_ice" : (top - y > 10 ? "stone" : (n < 0.6 ? "packed_ice" : "snow_block"));
                    }
                    data.setBlock(lx, y, lz, data(b));
                }
                data.setBlock(lx, top, lz, data(surface));
                int[] cl = t.clouds(x, z, top);
                for (int i = 0; i < cl.length; i += 2) {
                    if (cl[i + 1] > 0) {
                        data.setRegion(lx, cl[i], lz, lx + 1, cl[i] + cl[i + 1], lz + 1, data(Terrain.CLOUD));
                    }
                }
                int water = t.water(x, z, top);
                if (water != Integer.MIN_VALUE) {
                    data.setRegion(lx, top + 1, lz, lx + 1, water + 1, lz + 1, data("water"));
                } else {
                    if (surface.equals("snow_block") && top > Terrain.SNOW + 6
                            && de.moinmorni.adlerflug.land.Noise.hash(t.seed() + 9, x, z) < 0.5) {
                        data.setBlock(lx, top + 1, lz, data("snow[layers=" + (slope < 1 ? 2 : 1) + "]"));
                    } else {
                        String plant = t.plant(x, z, top, surface, slope);
                        if (plant != null) {
                            data.setBlock(lx, top + 1, lz, data(plant));
                        }
                    }
                }
            }
        }
        // Bäume (auch die, deren Krone von nebenan hereinragt)
        for (Trees.Tree tree : model.trees().around(x0, z0, x0 + 15, z0 + 15)) {
            for (Trees.Part p : Trees.parts(tree)) {
                if (p.x() < x0 || p.x() > x0 + 15 || p.z() < z0 || p.z() > z0 + 15) {
                    continue;
                }
                Material m = data.getType(p.x() - x0, p.y(), p.z() - z0);
                if (m.isAir() || m == Material.SHORT_GRASS || m == Material.FERN || !m.isSolid()) {
                    data.setBlock(p.x() - x0, p.y(), p.z() - z0, data(p.block()));
                }
            }
        }
        // Bauplan zuletzt (Luft schneidet Stollen und Tore frei)
        ChunkBlocks b = chunks.get(chunkKey(chunkX, chunkZ));
        if (b != null) {
            for (int i = 0; i < b.pos().length; i++) {
                int p = b.pos()[i];
                BlockData d = palette[b.palette()[i]];
                if (d != null) {
                    data.setBlock(p & 15, p >> 8, (p >> 4) & 15, d);
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(org.bukkit.World world, Random random) {
        var b = model.basecamp();
        return new Location(world, b.spawnX(), b.spawnY(), b.spawnZ(), b.spawnYaw(), 0);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new Biomes(model);
    }

    /** Biom: um jede Strecke ihr eigenes (Farben von Gras, Laub, Himmel und Wasser), sonst Almwiese. */
    static final class Biomes extends BiomeProvider {
        private final List<double[]> areas = new ArrayList<>();
        private final List<Biome> biomes = new ArrayList<>();
        private final Biome fallback;
        private final List<Biome> all;

        Biomes(World model) {
            fallback = biome("minecraft:meadow", null);
            Set<Biome> used = new LinkedHashSet<>();
            used.add(fallback);
            for (Course c : model.everyLayout()) {
                Biome b = biome(c.biome, fallback);
                used.add(b);
                biomes.add(b);
                areas.add(new double[] {c.bounds.minX() - 120, c.bounds.minZ() - 120, c.bounds.maxX() + 120,
                    c.bounds.maxZ() + 120});
            }
            all = List.copyOf(used);
        }

        private static Biome biome(String key, Biome fallback) {
            NamespacedKey k = NamespacedKey.fromString(key);
            Biome b = k == null ? null : Registry.BIOME.get(k);
            return b == null ? fallback : b;
        }

        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            for (int i = 0; i < areas.size(); i++) {
                double[] a = areas.get(i);
                if (x >= a[0] && x <= a[2] && z >= a[1] && z <= a[3]) {
                    return biomes.get(i);
                }
            }
            return fallback;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return all;
        }
    }
}
