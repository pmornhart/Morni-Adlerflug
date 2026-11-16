# Morni Adlerflug

**Adlerflug** (englisch „Eagle Flight“) ist der Flug-Modus des MoinMorni-Netzwerks (moinmorni.net): Mit Flügeln von
einer Schanze abheben und auf Zeit durch eine Strecke aus Ringen in den Bergen fliegen. Zwischenzeiten an jedem Ring,
Bestzeiten auf die Millisekunde, Medaillen (Bronze, Silber, Gold, Adler), Bestenlisten, Strecke der Woche, ein Geist
der eigenen Bestzeit, Party-Rennen und ein Übungsmodus. Paper-Plugin für Paper 26.x (Java 25), läuft im Netzwerk und
ganz allein.

## So geht's

1. Im **Adlerhorst** (Basislager auf dem Gipfel) eine Strecke wählen: Schild an einem Wegweiser anklicken oder die
   **Flugkarte** in der Leiste benutzen.
2. Man steht auf der **Schanze** des Startturms. Loslaufen – an der Kante startet die Zeit, die Flügel gehen von selbst
   auf und es gibt Schwung.
3. Mit der Maus lenken: nach unten = schneller, nach oben = steigen (kostet Tempo). Der Reihe nach durch alle **Ringe**
   fliegen – die Farbe zeigt den Abschnitt (gelb → orange → rot → magenta), geschaffte Ringe leuchten grün, am nächsten
   Ring funkelt es. Die Aktionsleiste zeigt Zeit, Ring, Tempo und einen Pfeil zum nächsten Ring.
4. **Aufwind-Säulen** (aufsteigende Wirbel über einem Steinkreis mit Lagerfeuern) heben an, **Schub-Ringe** (hellblau)
   geben Schwung nach vorn. Raketen gibt es nicht.
5. Ring verpasst, gelandet, ins Wasser gefallen oder zu weit weg? Dann geht es **automatisch zurück zum letzten Ring**
   (in der Luft, mit Schwung) – oder selbst mit der **Feder** (Rechtsklick) bzw. Taste **F**. Die Zeit läuft weiter.
6. Durch den **Ziel-Ring** (schwarz-weiß mit Gold): Zeit, Medaille, Platz – danach steht man auf dem Landeplatz mit der
   Bestenliste der Strecke.

Bedrock-Spieler (über Geyser) spielen genauso mit: Gleiten geht dort auch, alle Menüs sind Truhen-Fenster, alles geht
auch per Rechtsklick auf die Gegenstände.

## Strecken

| Strecke | Stufe | Ringe | Was es zu sehen gibt |
|---|---|---|---|
| Almrundflug | ★ Leicht | 12 | Almwiesen, Heustadel, Hütten, Kapelle mit Zwiebelturm, Bergsee mit Steg, Kühe auf der Weide |
| Klammflug | ★★ Mittel | 11 | enge Schlucht mit Bach, Wasserfälle an den Felswänden, Hängebrücken hoch über der Klamm |
| Burgruine | ★★ Mittel | 11 | durchs Burgtor, Aufwind über dem Hof, zwischen zwei Türmen, unter den Bögen der alten Steinbrücke |
| Gletscherspalte | ★★★ Schwer | 12 | hinab in die eisblaue Spalte, unter Eisbögen hindurch, Aufwind hinaus, Berghütte |
| Stollenflug | ★★★ Schwer | 11 | mitten durch den Berg: beleuchteter Bergwerksstollen mit Stützbalken, Laternen und Gleisen |
| Gipfelkreuz | ★★★★ Extrem | 19 | einmal ums Gipfelkreuz, durch die Felsscharte, zwei Aufwinde, kleine Ringe bis zur Hütte |

Alle Strecken, Bauten und die Landschaft sind eigene Entwürfe und entstehen beim Start aus dem Code (siehe „Aufbau“).

## Medaillen, Woche, Geist, Rennen, Übung

- **Medaillen** je Strecke: Bronze, Silber, Gold und ▲ **Adler**. Die Zeiten richten sich nach einem nachgebauten
  Musterflug (siehe Tests) – Adler ist so schnell wie der beste Prüf-Pilot.
- **Strecke der Woche**: wechselt jeden Montag 0:00 (Berlin); wer sie schafft, bekommt bis Montag das Abzeichen
  ▲ **Wochen-Adler** (Tab-Liste, ohne Netzwerk auch im Chat) und landet in der Wochen-Bestenliste.
- **Geist**: Ein kleiner Adler aus blauen Funken fliegt die eigene Bestzeit mit (abschaltbar).
- **Party-Rennen**: Wählt die Leitung einer Party eine Strecke, bekommen die Mitglieder auf diesem Server eine
  Einladung; alle springen nach dem Countdown gleichzeitig ab, danach Platzierung und Sieger.
- **Übungsmodus**: Zeiten zählen nicht; mit „Ab Ring …“ steigt man an jedem Ring in der Luft ein.

## Im Netzwerk

Kennung **`adlerflug`**. Das Plugin meldet über die Schnittstelle `de.moinmorni.api.MoinMorni`:

- Zustand: `state("adlerflug", "open", <Spieler>)` (Lobby-Figur, Webseite)
- Zähler (`add`): `flights` (Absprünge), `finishes`, `finishes_<strecke>`, `rings` (geschaffte Ringe), `resets`
  (zurück zum letzten Ring), `medals` (neue Medaillen-Stufen), `eagles` (neue Adler-Medaillen)
- Bestwerte (`best`, kleiner ist besser): `time_<strecke>_ms`, z. B. `time_almrundflug_ms`

Strecken-Kennungen: `almrundflug`, `klammflug`, `burgruine`, `gletscherspalte`, `stollenflug`, `gipfelkreuz`.
Sprache und Party kommen vom Netzwerk, „Zurück ins Dorf“ schickt in die Lobby. Ohne Netzwerk kommt die Sprache aus dem
Spiel des Spielers, die Statistik bleibt lokal.

## Einstellungen (`config.yml`)

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `welt` | `adlerflug` | Name der Welt (wird bei neuer Bau-Version neu erzeugt) |
| `start-schwung` | `1.3` | Schwung beim Absprung in Blöcken pro Tick (0 = keiner) |
| `fluegel-automatisch` | `true` | Flügel gehen beim Fallen von selbst auf |
| `plausibel-pruefen` | `true` | unmöglich schnelle Zeiten nicht werten |
| `rennen-wartezeit` | `15` | Sekunden zum Mitmachen beim Party-Rennen |
| `geist-standard` | `true` | Geist für neue Spieler an |
| `freiflug` | `true` | Freiflug von der Schanze im Adlerhorst |

Im MoinMorni-Netzwerk gehören diese Einstellungen ins Panel (Einstellungs-Katalog im Netzwerk-Repo) – darum gibt es
keine Einstellungs-Befehle.

## Befehle

- `/adlerflug` (auch `/adler`, `/flug`, `/eagle`): Strecken-Menü; `start <strecke>`, `top <strecke>`, `stats [spieler]`,
  `woche`, `uebung`, `geist`, `verstecken`, `ring` (zurück zum letzten Ring), `neustart`, `aufgeben`, `basislager`,
  `mitmachen`, `lobby`, `hilfe`
- `/adleradmin` (Recht `adlerflug.admin`, nur zum Verwalten und Testen): `status`, `fliege <spieler> [bis <ring>]
  [daneben <ring>]` (Test-Autopilot), `strecke <id>` (Daten als JSON), `stationen`, `reset <spieler> [strecke]`,
  `neubau`, `pruefen` (alle Strecken mit dem Prüf-Piloten abfliegen)

## Aufbau

- `flight/` – Flugphysik ohne Bukkit: `Glide` (Gleitflug wie im Spiel, Tick für Tick), `Ring`, `RingCheck` (Durchflug
  aus der Strecke zwischen zwei Positionen, auch bei hohem Tempo), `Updraft`, `FlightSim` (fliegt eine Strecke mit einem
  Piloten ab), `Vec`.
- `course/` – Strecken-Modell: `CourseBuilder` (aus Wegpunkten wird mit dem Gleitflug ein Musterflug, die Ringe kommen
  genau dorthin), `Course`, `Pilots`, `Medal`, `Difficulty`, `Plan` (Bauplan), `Blk`, `Box`.
- `land/` – Landschaft aus Rechnung: `Terrain` (Berge aus Rauschen, Hochebenen, Gipfel, Seen, Bäche, Oberflächen),
  `Corridor` (Flugschneise unter jeder Strecke), `Trees`, `Noise`.
- `courses/` – die Welt: `Designs` (sechs Strecken), `Builders` und `Scenery` (Bauten), `Basecamp`, `World`.
- `world/` – `FlightGenerator` (Chunks direkt aus dem Modell), `Construction` (Bau-Version, Neubau), `Holograms`,
  `Critters`.
- `game/` – `Flights` (Flüge, Ringe, Zurücksetzen, Ziel, Rennen), `RingTint`, `Hotbar`, `Controls`, `Menus`, `Boards`,
  `Guard`. `stats/`, `time/`, `data/Store`, `net/`, `ui/`, `cmd/`.

## Bauen und testen

```
JAVA_HOME=<jdk-25> mvn -q package                 # Tests + target/Morni-Adlerflug.jar
JAVA_HOME=<jdk-25> mvn -q -Ppaper-26.1 compile    # übersetzt auch gegen Paper 26.1
FRESH=1 test/run.sh                               # echter Server (Paper 26.1.2) + vier Test-Spieler
```

Die Unit-Tests fliegen jede Strecke mit dem nachgebauten Gleitflug ab (Musterflug durch alle Ringe, nichts im Weg, ein
einfacher Pilot kommt ins Ziel). `test/run.sh` startet einen Paper-Server mit dem Test-Netzwerk (`test/fake-network`)
und lässt Test-Spieler (mineflayer) alles durchspielen: echter Absprung mit Flügeln, jede Strecke komplett mit dem
Test-Autopiloten, verpasster Ring, gelandet, zu weit weg, Ziel, Bestzeit, Medaille, Bestenliste, Englisch,
Party-Rennen, Übungsmodus, Statistik ans Netzwerk, Lobby. Server-Ordner, Welten und Spielerdaten liegen außerhalb des
Repos.

Ändern sich Landschaft, Basislager oder Strecken, `Construction.VERSION` erhöhen – dann entsteht die Welt neu.

---

Verfasst mit Claude 🤖
