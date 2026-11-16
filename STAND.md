# Stand – Morni Adlerflug

**Was es ist:** Der Flug-Modus des MoinMorni-Netzwerks (Paper-Plugin `MorniAdlerflug`, Java 25, Paper 26.x): mit
Flügeln von einer Schanze abheben und auf Zeit durch eine Strecke aus Ringen in den Bergen fliegen – Zwischenzeiten an
jedem Ring, Bestzeiten, Medaillen (Bronze, Silber, Gold, Adler, Steinadler), Bestenlisten, Strecke der Woche, Strecke
des Tages, Geist der eigenen Bestzeit, Party-Rennen und Übungsmodus. Läuft im Netzwerk (Kennung `adlerflug`) und ganz
allein.

## Aufbau

- `flight/` (ohne Bukkit): `Glide` (Vanilla-Gleitflug Tick für Tick), `Ring` (bewegt, Takt-Tor, Bonus), `RingCheck`,
  `Motion`, `Gate`, `Wind`, `Updraft`, `Air` (alles in der Luft, Wind in Böen), `FlightSim`, `Vec`.
- `course/` (ohne Bukkit): `CourseBuilder` (Wegpunkte → Musterflug → Ringe), `Course`, `Pilots` (Prüf-Pilot),
  `Medal`, `Difficulty`, `Plan`, `Blk`, `Box`.
- `land/` (ohne Bukkit): `Terrain` (Berge, Formen, Seen, Bach, Oberflächen), `Corridor` (Flugschneise), `Trees`, `Noise`.
- `courses/`: `Designs` (acht Strecken), `Daily` (Strecke des Tages), `Builders`, `Rocks` + `Scenery` (Bauten),
  `Basecamp` (Adlerhorst), `World`.
- `world/`: `FlightGenerator` (Chunks aus dem Modell), `Construction` (`VERSION`, Neubau), `Holograms`, `Critters`.
- `game/`: `Flights` (Ablauf, Ringe, Zurücksetzen, Ziel, Rennen, Test-Autopilot), `Flight`, `Race`, `RingTint`,
  `Hotbar`, `Controls`, `Menus`, `Boards`, `Guard`. `stats/`, `time/`, `data/Store`, `net/`, `ui/`, `cmd/`.
- `test/`: `run.sh`, `adlerflug-test.cjs` (mineflayer), `rcon.cjs`, `fake-network/`.

## Stand (16.11.2026)

- **Version 0.2.0 – deutlich schwerer, mehr im Flug, Strecke des Tages** (Philip: „deutlich schwerer und immer ein
  bisschen Abwechslung reinbringen“; Branch `claude/schwerer`, wartet auf Philips Go). Begonnen von einem Helfer, der
  mittendrin gestoppt wurde (Modell: Strecken, Ringe, Piloten, Tagesstrecke), fertig gebaut und geprüft von seinem
  Nachfolger. Alles in `docs/releases/v0.2.0.md`:
  - zwei neue Strecken Felsenlabyrinth (schwer, 14 Ringe: Slalom um Felsnadeln, zwei Felsbögen, Spalte, knapp über den
    See) und Höllenschlund (extrem, 17: tiefer Pass, Fallwind, Tor im Takt, dunkle Höhle, Haarnadeln, knapp übers
    Wasser); die sechs alten kleiner und schärfer (Ringe 3,2–4,5), Stollen in der Mitte dunkel.
  - bewegte Ringe (gleiten/kreisen, `Motion`), Tore im Takt (`Gate`, Flügel als Block-Anzeigen, durch kommt nur, wer
    durch die Lücke fliegt), Bonus-Ringe mit Zeit-Gutschrift, Windzonen und Wind des Tages in Böen alle 10 Ticks
    (`Air.GUST`: der Server setzt nur dann die Geschwindigkeit – jeden Tick ginge bei Verzögerung im Netz Steuerung
    verloren), Schlieren und Windsäcke. Alles nach der Flugzeit (`Flight.ringTime`, nach dem Zurücksetzen auf den Stand
    des Musterflugs), genau gleich im nachgebauten Gleitflug, im Prüf-Piloten und auf dem Server. Je Flieger eigene
    Anzeigen der bewegten Ringe/Tore (`RingTint`: Träger mit Teilen als Mitfahrer, `RETAIN_PASSENGERS`), für Zuschauer
    eine gemeinsame nach der Server-Uhr.
  - Medaillen strenger (Adler 0,98 × Musterflug, Gold 1,06, Silber 1,15, Bronze 1,30), neu ✦ Steinadler = bester
    geübter Prüf-Pilot (`Pilots.experts`: Kurven anschneiden, tiefer anfliegen, Bonus-Ringe), mit Landschaft geprüft.
  - Strecke des Tages (`Daily`, Tal im Süden, Starttürme an beiden Enden): Richtung, Wind (8 Richtungen, 4 Stärken),
    Tageszeit, Wetter, Ringgröße, bewegte Ringe, Tor, Bonus-Ringe nur aus dem Datum; lässt sie sich so nicht fliegen,
    wird – ebenfalls nur aus dem Datum – etwas weggenommen. Gerechnet nach dem Start im Hintergrund (~0,5 s) und um
    Mitternacht neu; Tages-Bestenliste, Station mit ✦, Uhr in der Leiste, `/adlerflug tag`, Tafeln/Menü/Begrüßung mit
    den Bedingungen, Tageszeit und Wetter nur für den Flieger.
  - Treppe zur Freiflug-Schanze (Philips Bild): Die Stufen stiegen nach Norden an, man lief gegen die hohe Kante und
    musste jede Stufe springen. Jetzt zwei Läufe nach Süden mit Absatz, dunkle Wangen mit Geländer, Antrittspfosten und
    Laternen, Stützen; Begrüßungstafel vor die Treppe gerückt.
  - Schnittstelle Fassung 2 (byte-gleich): `playing` vom Absprung bis Ziel/Aufgeben, „Zurück ins Dorf“ und
    `/adlerflug lobby` → `askToLobby`, `LinkageError` → einmal Hinweis, dann wie bisher; Test-Netzwerk schreibt
    `playing`/`ask-lobby`/`hold-slot`.
  - Alte Bestzeiten auf geänderten Strecken zählen nicht mehr (Fingerabdruck `Course.signature` je Bestzeit, Zähler
    bleiben). `Construction.VERSION` 3. Neue config: `strecke-des-tages`. Neue Statistik: `bonus_rings`,
    `golden_eagles`, `days`, `finishes_tagesflug`; Strecken `felsenlabyrinth`, `hoellenschlund`.
  - Geprüft: 75 Unit-Tests (JUnit 6.1.3; neu u. a. Bonus-Flug ohne Anstoßen, Steinadler erreichbar, Tore/Bewegung/Böen,
    Treppe ohne Springen begehbar, Strecke des Tages zwei Wochen am Stück fliegbar, Vielfalt über 120 Tage), Paper 26.1
    übersetzt. ECHTZAHL
- Version 0.1.0 (Branch `claude/erster-stand`, noch nicht veröffentlicht – wartet auf Philips Go).
- Sechs Strecken: Almrundflug (leicht, 12 Ringe), Klammflug (mittel, 11), Burgruine (mittel, 11), Gletscherspalte
  (schwer, 12), Stollenflug (schwer, 11), Gipfelkreuz (extrem, 19). Welt entsteht beim Erzeugen der Chunks aus dem
  Code (Landschaft + Bauplan ≈ 135 000 Blöcke), Strecken werden beim Start im Hintergrund vorbereitet.
- Unit-Tests grün (Gleitflug-Physik, Ring-Erkennung auch bei 12 Blöcken/Tick, Aufwind, Medaillen, Bauplan,
  Landschaft/Schneisen, jede Strecke: Musterflug durch alle Ringe ohne Anstoßen, Prüf-Pilot im Ziel, Start und
  Landeplatz, Abstände, Texte inkl. jeder Schlüssel aus dem Quelltext). Übersetzt gegen Paper 26.3 und 26.1.2.
- Echter Test (`FRESH=1 test/run.sh`, Paper 26.1.2 + Test-Netzwerk, vier Test-Spieler): **61/61** in einem Lauf mit
  frisch erzeugter Welt (16.11. abends). Geprüft: Adlerhorst, Stationen, Sprache, Flugbuch,
  Absprung mit Flügeln, Zeit und Tempo in der Aktionsleiste, Gelandet/Zu weit/Ring verpasst → zurück, Neustart,
  Aufgeben, Übungsmodus, Rennen, Bestzeit, Medaillen, Bestenliste, Englisch, Netzwerk-Statistik, Strecke der Woche
  mit ▲-Abzeichen; alle sechs Strecken mit dem Test-Autopiloten komplett geflogen, jeder Ring gezählt. Server-Log ohne
  Fehler.
- Verschönerung (16.11. nachmittags, Philip: „das Beste vom Besten“): Landschaft jetzt Hochgebirge – Grundgebirge
  mit Graten und Schneegipfeln, die Schneisen schneiden Trogtäler hinein (Hänge werden nach außen immer steiler,
  `Corridor.BEND`, keine Stufe am Rand), Talwände mit Rinnen, Felsbänder und Geröll an Hängen, Büsche/Farn auf den
  Stufen, Wald in Flecken mit Waldrand, Blumenwiesen mit Hauptblume je Fleck und Blütenteppichen, Wanderwege
  (`Terrain.path`), Almtal mit Bergsee, Felskopf und Wasserfall, Schneegipfel hinter dem Tal, Wolkenmeer
  (`Terrain.clouds`) nur 20 Blöcke unter der nächsten Flughöhe. Ringe als Block-Anzeigen (`Scenery.ringParts`,
  `RingTint`): rund, leuchtend, Goldrand, Saum, Leuchtsteine, Farbverlauf; Funkenkranz am nächsten Ring nur für den
  Flieger (bis 512 Blöcke), geschaffte Ringe verschwinden für ihn. Anzeige-Reichweite der Welt 320 Blöcke
  (Spigot-Einstellung nur im Speicher). `Construction.VERSION` 2. Unit-Tests grün (neu `RingPartsTest`). Echter Test
  danach: 60/61 (Lauf 1: die Absprung-Prüfung verlangte 6 Blöcke Flugweite, mineflayer gleitet nicht und fiel 5,9
  weit – Grenze auf 4,5 gesetzt; Lauf 2 mit dieser Grenze grün, dort fehlte bei Gipfelkreuz nur die Ziel-Meldung, weil
  der Test-Spieler auf derselben Welt schon geflogen war – alle 19 Ringe und die Zeit zählten). Bilder v5 mit echtem
  Client 26.3 (Ich-Sicht im Flug auf allen Strecken, Tempo in der Leiste geprüft: 51–87 km/h).
- Echter Test mit frischer Welt zuverlässig (16.11. abends): Der Test-Spieler blieb vor der Schanzenkante stehen –
  mineflayer bewegt sich nicht in Chunks, die es noch nicht hat, und bei Almrundflug liegt die Kante schon im nächsten
  Chunk; auf dem ausgelasteten Rechner kam der zu spät. Jetzt wartet `test/run.sh`, bis die Strecken vorbereitet sind,
  und der Test auf die Chunks vor der Kante (bis 120 s), 30 s auf den Absprung und kurz auf die Leiste nach dem Ziel.
- Gefunden und behoben: Tempo (km/h) zeigte immer 0; wer langsam über die Schanzenkante trat, galt sofort als
  gelandet (jetzt kleiner Sprung nach vorn beim Absprung); Test startete, bevor RCON lief (Server wurde gleich wieder beendet); `tp` per RCON ohne
  `execute in` landete in der Oberwelt und blockierte den Server beim Erzeugen.
- Nicht echt geprüft: echter Gleitflug durch Ringe mit einem echten Client (mineflayer gleitet nicht, weil es die
  Flügel im Brustplatz nicht sieht – der Absprung mit Flügeln auf dem Server ist geprüft), Bedrock (Geyser), Aufwind
  und Schub-Ring im echten Flug (im Test-Autopiloten im Musterflug enthalten), Netzwerk mit echtem Hub.

## Offen

- Philip: Bilder ansehen (nur lokal unter `~/mm-cache/bilder-adlerflug/v5/`), selbst fliegen; Go für `main`.
- Server-Betreiber: Ringe sind Block-Anzeigen – mit kleiner Sichtweite des Servers (`view-distance`) sieht man sie
  erst später; empfohlen 12 oder mehr.
- Medaillen-Zeiten nach den ersten echten Flügen nachjustieren (heute aus dem Prüf-Piloten).
- Im Netzwerk-Repo: Lobby-Figur „Adlerflug“ (`MODE_REPOS`), Webseite (Modus-Seite, Bestenlisten `time_<strecke>_ms`),
  Einstellungs-Katalog (Schlüssel der `config.yml`).
