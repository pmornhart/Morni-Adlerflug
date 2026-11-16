package de.moinmorni.adlerflug.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Difficulty;
import de.moinmorni.adlerflug.course.Medal;
import de.moinmorni.adlerflug.courses.Design;
import de.moinmorni.adlerflug.courses.Designs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.Test;

class TextsTest {

    /** Formatierungen, die keine Platzhalter sind. */
    private static final Set<String> STYLES = Set.of("gray", "white", "red", "green", "dark_gray", "bold", "gold",
            "yellow", "light_purple", "aqua", "gradient");
    // Tags wie <name> oder </gray> – aber nicht \<strecke> (maskiert).
    // https://regex101.com/?flavor=java&regex=(%3F%3C!%5C%5C)%3C%2F%3F(%5Ba-z_%5D%2B)%3E&testString=%3Cgray%3EHi%20%3Cname%3E%3C%2Fgray%3E%20%5C%3Cstrecke%3E
    private static final Pattern TAG = Pattern.compile("(?<!\\\\)</?([a-z_]+)>");
    /** Schlüssel in den Quelltexten: "flight.ring", "menu.best" … */
    // https://regex101.com/?flavor=java&regex=%22(%5Ba-z%5D%5Ba-z0-9-%5D*(%3F%3A%5C.%5Ba-z0-9-%5D%2B)%2B)%22&testString=Texts.get(l%2C%20%22flight.ring%22)
    private static final Pattern KEY = Pattern.compile("\"([a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)+)\"");

    private static Set<String> placeholders(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            if (!STYLES.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    @Test
    void everyTextExistsInBothLanguagesWithTheSamePlaceholders() {
        for (String key : Texts.keys()) {
            String de = Texts.raw("de", key);
            String en = Texts.raw("en", key);
            assertFalse(de.isBlank(), key);
            assertFalse(en.isBlank(), key);
            assertEquals(placeholders(de), placeholders(en), "Platzhalter in " + key);
        }
    }

    @Test
    void everyTextParsesStrictly() {
        MiniMessage strict = MiniMessage.builder().strict(true).build();
        for (String key : Texts.keys()) {
            for (String lang : new String[] {"de", "en"}) {
                String text = Texts.raw(lang, key);
                TagResolver.Builder resolvers = TagResolver.builder();
                placeholders(text).forEach(p -> resolvers.resolver(Placeholder.unparsed(p, "x")));
                TagResolver all = resolvers.build();
                for (String line : text.split("\n")) {
                    assertDoesNotThrow(() -> strict.deserialize(line, all), lang + " " + key + ": " + line);
                }
            }
        }
    }

    /** Minecraft zeigt nur Zeichen der „Basic Multilingual Plane“ – Emoji wie 🦅 wären ein leeres Kästchen. */
    @Test
    void onlyCharactersMinecraftCanShow() {
        for (String key : Texts.keys()) {
            for (String lang : new String[] {"de", "en"}) {
                String text = Texts.raw(lang, key);
                assertEquals(text.length(), text.codePointCount(0, text.length()), lang + " " + key);
            }
        }
        for (Medal m : Medal.values()) {
            assertEquals(1, m.symbol.length());
        }
        for (Difficulty d : Difficulty.values()) {
            assertEquals(d.stars().length(), d.stars().codePointCount(0, d.stars().length()));
        }
    }

    @Test
    void englishIsReallyEnglish() {
        assertNotEquals(Texts.raw("de", "flight.go"), Texts.raw("en", "flight.go"));
        assertEquals(Texts.raw("de", "finish.title"), Texts.raw("fr", "finish.title"), "unbekannte Sprache → Deutsch");
        assertEquals("missing.key", Texts.raw("de", "missing.key"));
    }

    /** Jede Strecke hat Namen und Beschreibung in beiden Sprachen (stehen beim Entwurf der Strecke). */
    @Test
    void everyCourseHasNameAndTextInBothLanguages() {
        for (Design d : Designs.all()) {
            Course c = d.course();
            assertFalse(c.nameDe.isBlank() || c.nameEn.isBlank(), c.id);
            assertFalse(c.textDe.isBlank() || c.textEn.isBlank(), c.id);
            assertNotEquals(c.textDe, c.textEn, c.id);
            assertTrue(c.textDe.length() < 220 && c.textEn.length() < 220, c.id + ": Beschreibung zu lang fürs Menü");
            assertEquals(c.textDe.length(), c.textDe.codePointCount(0, c.textDe.length()), c.id);
        }
    }

    /** Jeder Schlüssel, der im Quelltext steht, hat einen Text (Tippfehler fallen sofort auf). */
    @Test
    void everyKeyInTheSourceExists() throws IOException {
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = KEY.matcher(Files.readString(f));
                while (m.find()) {
                    String k = m.group(1);
                    boolean code = k.startsWith("minecraft.") || k.startsWith("adlerflug.") || k.startsWith("de.")
                            || k.endsWith(".yml") || k.endsWith(".bin") || k.endsWith(".net") || k.endsWith(".");
                    if (!code && !Texts.keys().contains(k)) {
                        missing.add(k + " (" + f.getFileName() + ")");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Texte fehlen: " + missing);
    }
}
