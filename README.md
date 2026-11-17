# Morni Adlerflug

**Adlerflug** (englisch „Eagle Flight“) ist der Flug-Modus des MoinMorni-Netzwerks (moinmorni.net): Mit Flügeln von
einer Schanze abheben und auf Zeit durch eine Strecke aus Ringen in den Bergen fliegen. Zwischenzeiten an jedem Ring,
Bestzeiten auf die Millisekunde, Medaillen (Bronze, Silber, Gold, Adler, Steinadler), Bestenlisten, Strecke der Woche,
Strecke des Tages, ein Geist der eigenen Bestzeit, Party-Rennen und ein Übungsmodus. Paper-Plugin für Paper 26.x (Java 25), läuft im Netzwerk und
ganz allein.

## So geht's

1. Im **Adlerhorst** (Basislager auf dem Gipfel) eine Strecke wählen: Schild an einem Wegweiser anklicken oder die
   **Flugkarte** in der Leiste benutzen.
2. Man steht auf der **Schanze** des Startturms. Loslaufen – an der Kante startet die Zeit, die Flügel gehen von selbst
   auf und es gibt Schwung.
3. Mit der Maus lenken: nach unten = schneller, nach oben = steigen (kostet Tempo). Der Reihe nach durch alle **Ringe**
   fliegen – runde, leuchtende Ringe mit Goldrand, die Farbe zeigt den Abschnitt (gelb → orange → rot → magenta).
   Um den nächsten Ring kreist ein Funkenkranz (auch hinter Bergen und von weit weg zu sehen), geschaffte Ringe
   verschwinden mit einem Funkenregen. Die Aktionsleiste zeigt Zeit, Ring, Tempo und einen Pfeil zum nächsten Ring.
4. **Aufwind-Säulen** (aufsteigende Wirbel über einem Steinkreis mit Lagerfeuern) heben an, **Schub-Ringe** (hellblau)
   geben Schwung nach vorn. Raketen gibt es nicht.
   - **Bewegte Ringe** gleiten hin und her (↔) oder kreisen (↻) – man muss sie dort treffen, wo sie gerade sind.
   - **Tore im Takt** (gelb-schwarz, ▮▯): zwei Flügel gleiten zu und wieder auf; durch kommt nur, wer durch die Lücke
     fliegt. Die Leiste zeigt „Tor offen 1,2 s“ bzw. „Tor zu 0,8 s“, der Kranz leuchtet grün oder rot.
   - **Bonus-Ringe** (grün, „Bonus −1,5 s“) liegen abseits der Linie und schenken Zeit – wer sie holt, muss den
     nächsten Ring trotzdem schaffen.
   - **Windzonen**: Seitenwind in Böen, zu sehen an Schlieren und Windsäcken, die Leiste zeigt „Wind ←“.
   Bewegte Ringe, Tore und Böen laufen nach der eigenen Flugzeit seit dem Absprung – für jeden gleich.
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
| Gletscherspalte | ★★★ Schwer | 12 | hinab in die eisblaue Spalte, Eistor im Takt, auf- und abgleitender Ring, Gletscherwind |
| Stollenflug | ★★★ Schwer | 11 | mitten durch den Berg: Bergwerksstollen, in der Mitte stockdunkel, Wettertür im Takt |
| Felsenlabyrinth | ★★★ Schwer | 14 | Slalom um Felsnadeln, zwei Felsbögen, enge Spalte, knapp über den Bergsee |
| Gipfelkreuz | ★★★★ Extrem | 19 | ums Gipfelkreuz mit kreisendem Ring, Tor in der Felsscharte, Gratwind, zwei Aufwinde |
| Höllenschlund | ★★★★ Extrem | 17 | tiefer Pass, Fallwind, Tor im Takt, stockdunkle Höhle, Haarnadeln, knapp übers Wasser |
| Tagesflug | jeden Tag anders | 12 | Strecke des Tages im Tal im Süden (siehe unten) |

Alle Strecken, Bauten und die Landschaft sind eigene Entwürfe und entstehen beim Start aus dem Code (siehe „Aufbau“).

Die schweren und extremen Strecken haben Ringe mit höchstens 4 Blöcken Öffnung; jede Strecke hat einen
Bonus-Ring (außer dem Stollen), die meisten einen bewegten Ring oder ein Tor im Takt.

## Medaillen, Woche, Tag, Geist, Rennen, Übung

- **Medaillen** je Strecke: Bronze, Silber, Gold, ▲ **Adler** und ✦ **Steinadler**. Die Zeiten richten sich nach
  nachgebauten Flügen (siehe Tests): Adler ist 2 % schneller als der Musterflug bzw. der einfache Prüf-Pilot, Gold
  +6 %, Silber +15 %, Bronze +30 %; der Steinadler ist so schnell wie der beste geübte Prüf-Pilot (Kurven anschneiden,
  Tempo holen, Bonus-Ringe).
- **Strecke des Tages** (Station mit ✦, Uhr in der Leiste, `/adlerflug tag`): Was gilt, folgt nur aus dem Datum
  (deutsche Zeit) – auf jedem Server für jeden gleich: Richtung (normal, gespiegelt, andersherum oder beides), Wind aus
  acht Richtungen in vier Stärken (in Böen auf der ganzen Strecke), Tageszeit (Morgenrot, Mittag, Abendrot, Nacht) und
  Wetter (nur für den Flieger), Größe der Ringe, welche Ringe gleiten oder kreisen, ein Tor im Takt und ein oder zwei
  Bonus-Ringe. Eigene Tages-Bestenliste; Tafeln, Menü und Begrüßung zeigen die Bedingungen. Die Strecke entsteht nach
  dem Start im Hintergrund mit dem nachgebauten Gleitflug (samt Medaillen) und um Mitternacht neu.
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
  (zurück zum letzten Ring), `medals` (neue Medaillen-Stufen), `eagles` (neue Adler-Medaillen), `golden_eagles` (neue
  Steinadler), `bonus_rings` (eingesammelte Bonus-Ringe), `days` (Strecke des Tages an einem Tag geschafft)
- Bestwerte (`best`, kleiner ist besser): `time_<strecke>_ms`, z. B. `time_almrundflug_ms` (nicht für die Strecke des
  Tages – die ist jeden Tag anders)
- Schnittstelle Fassung 2: „spielt gerade“ (`playing`) vom Absprung bis zum Ziel bzw. Aufgeben; „Zurück ins Dorf“ und
  `/adlerflug lobby` fragen über das Netzwerk nach, solange man fliegt (`askToLobby`). Ein älteres Netzwerk-Plugin
  ohne diese Methoden wird erkannt (`LinkageError`), dann geht es wie bisher sofort in die Lobby.

Strecken-Kennungen: `almrundflug`, `klammflug`, `burgruine`, `gletscherspalte`, `stollenflug`, `felsenlabyrinth`,
`gipfelkreuz`, `hoellenschlund`, `tagesflug` (Strecke des Tages). Sprache und Party kommen vom Netzwerk. Ohne Netzwerk
kommt die Sprache aus dem Spiel des Spielers, die Statistik bleibt lokal.

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
| `strecke-des-tages` | `true` | Strecke des Tages an (false = aus, die Station bleibt leer) |

Im MoinMorni-Netzwerk gehören diese Einstellungen ins Panel (Einstellungs-Katalog im Netzwerk-Repo) – darum gibt es
keine Einstellungs-Befehle.

## Befehle

- `/adlerflug` (auch `/adler`, `/flug`, `/eagle`): Strecken-Menü; `start <strecke>`, `top <strecke>`, `stats [spieler]`,
  `woche`, `tag` (Strecke des Tages; `tag start` fliegt sie), `uebung`, `geist`, `verstecken`, `ring` (zurück zum letzten
  Ring), `neustart`, `aufgeben`, `basislager`, `mitmachen`, `lobby`, `hilfe`
- `/adleradmin` (Recht `adlerflug.admin`, nur zum Verwalten und Testen): `status`, `start <spieler> <strecke>` (auf die Schanze stellen), `fliege <spieler> [bis <ring>]
  [daneben <ring>] [takt <ticks>] [bonus]` (Test-Autopilot; `bonus` fliegt über die Bonus-Ringe, `takt` verschiebt die
  Flugzeit, z. B. vor ein geschlossenes Tor), `strecke <id>` (Daten als JSON), `stationen`, `reset <spieler> [strecke]`,
  `neubau`, `pruefen` (alle Strecken mit dem Prüf-Piloten abfliegen), `tag [jjjj-mm-tt|heute]` (Strecke des Tages als
  JSON bzw. für einen anderen Tag rechnen)

## Aufbau

- `flight/` – Flugphysik ohne Bukkit: `Glide` (Gleitflug wie im Spiel, Tick für Tick), `Ring` (auch bewegt, Takt-Tor,
  Bonus), `RingCheck` (Durchflug aus der Strecke zwischen zwei Positionen, auch bei hohem Tempo und zur richtigen
  Flugzeit), `Motion`, `Gate`, `Wind`, `Updraft`, `Air` (alles, was in der Luft wirkt, Wind in Böen), `FlightSim` (fliegt
  eine Strecke mit einem Piloten ab), `Vec`.
- `course/` – Strecken-Modell: `CourseBuilder` (aus Wegpunkten wird mit dem Gleitflug ein Musterflug, die Ringe kommen
  genau dorthin), `Course`, `Pilots`, `Medal`, `Difficulty`, `Plan` (Bauplan), `Blk`, `Box`.
- `land/` – Landschaft aus Rechnung: `Terrain` (Berge aus Rauschen, Hochebenen, Gipfel, Seen, Bäche, Oberflächen),
  `Corridor` (Flugschneise unter jeder Strecke), `Trees`, `Noise`.
- `courses/` – die Welt: `Designs` (acht Strecken), `Daily` (Strecke des Tages), `Builders`, `Rocks` und `Scenery`
  (Bauten), `Basecamp`, `World`.
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

Die Unit-Tests fliegen jede Strecke mit dem nachgebauten Gleitflug ab (Musterflug durch alle Ringe und offenen Tore,
nichts im Weg, ein einfacher Pilot kommt ins Ziel, der Flug über alle Bonus-Ringe klappt, ein geübter Pilot schafft den
Steinadler) und bauen die Strecke des Tages für zwei Wochen am Stück. `test/run.sh` startet einen Paper-Server mit dem Test-Netzwerk (`test/fake-network`)
und lässt Test-Spieler (mineflayer) alles durchspielen: echter Absprung mit Flügeln, jede Strecke komplett mit dem
Test-Autopiloten, verpasster Ring, gelandet, zu weit weg, Ziel, Bestzeit, Medaille, Bestenliste, Englisch,
Party-Rennen, Übungsmodus, Statistik ans Netzwerk, Lobby. Server-Ordner, Welten und Spielerdaten liegen außerhalb des
Repos.

Ändern sich Landschaft, Basislager oder Strecken, `Construction.VERSION` erhöhen – dann entsteht die Welt neu.

---

Verfasst mit Claude 🤖
