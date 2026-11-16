# Stand – Morni Adlerflug

**Was es ist:** Der Flug-Modus des MoinMorni-Netzwerks (Paper-Plugin `MorniAdlerflug`, Java 25, Paper 26.x): mit
Flügeln von einer Schanze abheben und auf Zeit durch eine Strecke aus Ringen in den Bergen fliegen – Zwischenzeiten an
jedem Ring, Bestzeiten, Medaillen (Bronze, Silber, Gold, Adler), Bestenlisten, Strecke der Woche, Geist der eigenen
Bestzeit, Party-Rennen und Übungsmodus. Läuft im Netzwerk (Kennung `adlerflug`) und ganz allein.

## Aufbau

- `flight/` (ohne Bukkit): `Glide` (Vanilla-Gleitflug Tick für Tick), `Ring`, `RingCheck`, `Updraft`, `FlightSim`, `Vec`.
- `course/` (ohne Bukkit): `CourseBuilder` (Wegpunkte → Musterflug → Ringe), `Course`, `Pilots` (Prüf-Pilot),
  `Medal`, `Difficulty`, `Plan`, `Blk`, `Box`.
- `land/` (ohne Bukkit): `Terrain` (Berge, Formen, Seen, Bach, Oberflächen), `Corridor` (Flugschneise), `Trees`, `Noise`.
- `courses/`: `Designs` (sechs Strecken), `Builders` + `Scenery` (Bauten), `Basecamp` (Adlerhorst), `World`.
- `world/`: `FlightGenerator` (Chunks aus dem Modell), `Construction` (`VERSION`, Neubau), `Holograms`, `Critters`.
- `game/`: `Flights` (Ablauf, Ringe, Zurücksetzen, Ziel, Rennen, Test-Autopilot), `Flight`, `Race`, `RingTint`,
  `Hotbar`, `Controls`, `Menus`, `Boards`, `Guard`. `stats/`, `time/`, `data/Store`, `net/`, `ui/`, `cmd/`.
- `test/`: `run.sh`, `adlerflug-test.cjs` (mineflayer), `rcon.cjs`, `fake-network/`.

## Stand (16.11.2026)

- Version 0.1.0 (Branch `claude/erster-stand`, noch nicht veröffentlicht – wartet auf Philips Go).
- Sechs Strecken: Almrundflug (leicht, 12 Ringe), Klammflug (mittel, 11), Burgruine (mittel, 11), Gletscherspalte
  (schwer, 12), Stollenflug (schwer, 11), Gipfelkreuz (extrem, 19). Welt entsteht beim Erzeugen der Chunks aus dem
  Code (Landschaft + Bauplan ≈ 135 000 Blöcke), Strecken werden beim Start im Hintergrund vorbereitet.
- Unit-Tests grün (Gleitflug-Physik, Ring-Erkennung auch bei 12 Blöcken/Tick, Aufwind, Medaillen, Bauplan,
  Landschaft/Schneisen, jede Strecke: Musterflug durch alle Ringe ohne Anstoßen, Prüf-Pilot im Ziel, Start und
  Landeplatz, Abstände, Texte inkl. jeder Schlüssel aus dem Quelltext). Übersetzt gegen Paper 26.3 und 26.1.2.
- Echter Test (`FRESH=1 test/run.sh`, Paper 26.1.2 + Test-Netzwerk, vier Test-Spieler): **61 Prüfungen**, alle grün
  (zusammen aus den letzten Läufen: 60/61 und danach die eine korrigierte Prüfung grün; die Sandbox beendete den Server
  zwischendurch mehrmals wegen Speichermangel anderer Prozesse). Geprüft: Adlerhorst, Stationen, Sprache, Flugbuch,
  Absprung mit Flügeln, Zeit und Tempo in der Aktionsleiste, Gelandet/Zu weit/Ring verpasst → zurück, Neustart,
  Aufgeben, Übungsmodus, Rennen, Bestzeit, Medaillen, Bestenliste, Englisch, Netzwerk-Statistik, Strecke der Woche
  mit ▲-Abzeichen; alle sechs Strecken mit dem Test-Autopiloten komplett geflogen, jeder Ring gezählt. Server-Log ohne
  Fehler.
- Gefunden und behoben: Tempo (km/h) zeigte immer 0; wer langsam über die Schanzenkante trat, galt sofort als
  gelandet (jetzt kleiner Sprung nach vorn beim Absprung); Test startete, bevor RCON lief (Server wurde gleich wieder beendet); `tp` per RCON ohne
  `execute in` landete in der Oberwelt und blockierte den Server beim Erzeugen.
- Nicht echt geprüft: echter Gleitflug durch Ringe mit einem echten Client (mineflayer gleitet nicht, weil es die
  Flügel im Brustplatz nicht sieht – der Absprung mit Flügeln auf dem Server ist geprüft), Bedrock (Geyser), Aufwind
  und Schub-Ring im echten Flug (im Test-Autopiloten im Musterflug enthalten), Netzwerk mit echtem Hub.

## Offen

- Philip: Bilder ansehen (nur lokal unter `~/mm-cache/bilder-adlerflug/`), selbst fliegen; Go für `main`.
- Medaillen-Zeiten nach den ersten echten Flügen nachjustieren (heute aus dem Prüf-Piloten).
- Im Netzwerk-Repo: Lobby-Figur „Adlerflug“ (`MODE_REPOS`), Webseite (Modus-Seite, Bestenlisten `time_<strecke>_ms`),
  Einstellungs-Katalog (Schlüssel der `config.yml`).
