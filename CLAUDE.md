# Morni Adlerflug

@STAND.md

## Regeln für dieses Projekt

- Repo ist **öffentlich**: keine Geheimnisse, Tokens oder privaten Daten committen (Geheimes nur in `.env`, nie im Repo).
  Auch keine Test-Server-Ordner, Welten, Spielerdaten, Geister-Dateien oder RCON-Passwörter.
- Der Modus heißt **Adlerflug** (englisch „Eagle Flight“), das Repo `pmornhart/morni-adlerflug`, die Jar
  `Morni-Adlerflug.jar`, das Plugin `MorniAdlerflug`, die Kennung im Netzwerk `adlerflug`.
- Texte im Spiel auf Deutsch (Standard) und Englisch, freundlich, keine Fachbegriffe ohne Erklärung. Alle Texte stehen
  in `ui/Texts.java` (MiniMessage, beide Sprachen); `TextsTest` prüft Platzhalter, Zeichen, dass jeder Schlüssel aus
  dem Quelltext einen Text hat und dass jede Strecke einen Namen und eine Beschreibung hat.
- **„Verfasst mit Claude 🤖“** als letzte Zeile in Commit-Messages (durch eine Leerzeile getrennt), in PRs, Issues und
  Release-Notes; einmal als Fußzeile im README.
- Commits auf Deutsch mit Stichpunkten `- Neu:` / `- Behoben:` / `- Verbessert:` / `- Entfernt:`.
- Keine fremden Marken, Figuren, Namen, Texte oder Grafiken (nichts von Mojang, Nintendo, Hypixel oder anderen Servern
  und Spielen): nur die Spielidee „mit Flügeln durch Ringe auf Zeit“, eigene Strecken, eigene Namen, eigene Bauten.
- Die Netzwerk-Schnittstelle `src/api/java/de/moinmorni/api/MoinMorni.java` ist eine Kopie aus `pmornhart/moinmorni.net`
  (`plugins/moinmorni-network/src/main/java/de/moinmorni/api/MoinMorni.java`) und muss **genau gleich** bleiben. Sie wird
  nur mitübersetzt und kommt **nicht** in die Jar. Nur `net/Connected` darf sie benutzen.
- Adlerflug muss auch ohne Netzwerk laufen (`softdepend: [MoinMorni]`; allein kommt die Sprache aus dem Client).
- Paper 26.x, Java 25. Der Code muss auch gegen Paper 26.1 übersetzen (`mvn -Ppaper-26.1 compile`, läuft in der CI).
  Blocknamen von 26.x benutzen (z. B. `iron_chain`, nicht `chain`) – `PlanTest` prüft bekannte alte Namen.
- Flugphysik, Ring-Erkennung, Strecken, Landschaft, Medaillen, Bestenliste und Woche gehören in `course/`, `flight/`,
  `courses/`, `stats/` und `time/` – ohne Bukkit, mit Unit-Tests. Werte dort nur zusammen mit den Tests ändern.
- Jede Strecke muss fliegbar sein: `CourseTest` lässt einen nachgebauten Vanilla-Gleitflug (`flight/Glide`) mit einem
  einfachen Piloten durch alle Ringe fliegen und prüft, dass nichts im Weg ist. Strecken nur so ändern, dass das grün
  bleibt; die Physik nur mit Beleg aus dem Spiel ändern.
- Ändern sich Basislager, Strecken oder Landschaft: `Construction.VERSION` erhöhen – dann entsteht die Welt beim
  nächsten Start neu (sie wird gelöscht, Spieler bauen dort nichts).
- Neue Version: `<version>` in `pom.xml` erhöhen und `docs/releases/v<version>.md` anlegen; ein Push auf `main` baut das
  Release mit `Morni-Adlerflug.jar` – das Netzwerk holt es sich von dort.
- Vor jeder Veröffentlichung (Merge nach `main`) Philips Go abwarten.
- Nach jeder sichtbaren Änderung Bilder wie im echten Minecraft (echte Client-Texturen nur lokal, nie ins Repo).
- „Alles im Panel“: jede Einstellung, die per Befehl geht, muss auch im Panel gehen. Darum hat Adlerflug nur die
  `config.yml` und Verwaltungs-Befehle zum Testen (`/adleradmin`); die Anbindung an den Einstellungs-Katalog gehört ins
  Netzwerk-Repo.
- Echter Test nach jeder Änderung am Spielablauf: `test/run.sh` (siehe README).
- `STAND.md` nach jedem größeren Schritt aktualisieren.
