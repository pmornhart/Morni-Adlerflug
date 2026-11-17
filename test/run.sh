#!/usr/bin/env bash
# Echter Test von Morni Adlerflug: baut das Plugin und das Test-Netzwerk, richtet einen Paper-Server ein (offline,
# eigener Port, RCON an), startet ihn, schickt Test-Spieler (mineflayer) durch alle Abläufe und hält ihn wieder an.
#
#   test/run.sh                       # alles
#   KEEP=1 test/run.sh                # Server danach laufen lassen (zum Nachschauen)
#   KEEP=1 SKIP_TEST=1 test/run.sh    # nur Server starten
#   FRESH=1 test/run.sh               # Welt und Spielerdaten vorher löschen
#
# Erwartet (Vorgaben für die MoinMorni-Sandbox, alles per Umgebungsvariable änderbar):
#   ADLER_JAVA     Java 25 (Ordner; sonst die JDK 25 aus dem Cache, sonst JAVA_HOME)
#   PAPER_JAR      Paper 26.1.2 (mineflayer kann bis 26.1)
#   TEMPLATE       ein schon einmal gestarteter Paper-26.1.2-Ordner (libraries/, cache/, versions/ werden kopiert,
#                  damit nichts heruntergeladen werden muss) – optional
#   ADLER_NODE_MODULES  node_modules mit mineflayer
#   EXTRA_PLUGINS  weitere Jars für den Server (z. B. ViaVersion + ViaBackwards für einen echten 26.3-Client)
#   ONLY, TEIL     nur diese Strecken für Bert (z. B. ONLY=klammflug) bzw. TEIL=rest ohne Annas Flüge
#   XMS, XMX, JAVA_OPTS  Speicher und Java-Optionen des Servers (auf einem knappen Rechner z. B. XMS=256M XMX=1536M
#                  JAVA_OPTS=-XX:+UseSerialGC)
#
# Verfasst mit Claude 🤖
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(dirname "$HERE")"
CACHE="${MM_CACHE:-/home/user/mm-cache}"
CACHED_JDK="$(ls -d "$CACHE"/jdk/jdk-25* 2>/dev/null | head -1 || true)"
JAVA_HOME="${ADLER_JAVA:-${CACHED_JDK:-${JAVA_HOME:-}}}"
export JAVA_HOME
JAVA="$JAVA_HOME/bin/java"
PAPER_JAR="${PAPER_JAR:-$CACHE/jars/paper-26.1.2.jar}"
TEMPLATE="${TEMPLATE:-$CACHE/servers/t1}"
SERVER="${SERVER_DIR:-$CACHE/servers/adlerflug-test}"
PORT="${PORT:-25950}"
RCON_PORT="${RCON_PORT:-26050}"
NICE="${NICE:-nice -n 15}"
export NODE_PATH="${ADLER_NODE_MODULES:-$CACHE/node/node_modules}${NODE_PATH:+:$NODE_PATH}"

echo "== Bauen"
(cd "$REPO" && $NICE mvn -q -B -o -DskipTests package)
(cd "$REPO" && $NICE mvn -q -B -o -f test/fake-network/pom.xml package)

echo "== Server einrichten: $SERVER"
if [ "${FRESH:-0}" = "1" ]; then
  rm -rf "$SERVER/adlerflug" "$SERVER/world" "$SERVER/plugins/MorniAdlerflug"
fi
mkdir -p "$SERVER/plugins/MorniAdlerflug" "$SERVER/plugins/MoinMorni"
if [ -d "$TEMPLATE" ] && [ ! -d "$SERVER/libraries" ]; then
  cp -r "$TEMPLATE/libraries" "$TEMPLATE/cache" "$TEMPLATE/versions" "$SERVER/" 2>/dev/null || true
fi
cp "$PAPER_JAR" "$SERVER/paper.jar"
echo "eula=true" > "$SERVER/eula.txt"
RCON_PASS="$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)"
printf '%s' "$RCON_PASS" > "$SERVER/.rcon-pass"
chmod 600 "$SERVER/.rcon-pass"
cat > "$SERVER/server.properties" <<PROPS
online-mode=false
enforce-secure-profile=false
server-port=$PORT
enable-rcon=true
rcon.port=$RCON_PORT
rcon.password=$RCON_PASS
level-name=world
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1}],"biome"\:"minecraft\:plains"}
generate-structures=false
spawn-protection=0
view-distance=${VIEW:-8}
simulation-distance=4
max-players=20
white-list=false
enforce-whitelist=false
motd=Morni Adlerflug Test
allow-flight=true
PROPS
# Test-Netzwerk: Bert spricht Englisch, Anna und Carl sind eine Party (Anna führt).
cat > "$SERVER/plugins/MoinMorni/fake.properties" <<'FAKE'
lang.Bert=en
party.Anna=Anna,Carl
FAKE
rm -f "$SERVER/plugins/MoinMorni/calls.log"
rm -rf "$SERVER/plugins/MorniAdlerflug/spieler" "$SERVER/plugins/MorniAdlerflug/geister"
rm -f "$SERVER"/plugins/Morni-Adlerflug.jar
cp "$REPO/target/Morni-Adlerflug.jar" "$SERVER/plugins/"
if [ "${NO_NETWORK:-0}" = "1" ]; then
  rm -f "$SERVER/plugins/MoinMorni-FakeNetwork.jar"
else
  cp "$REPO/test/fake-network/target/MoinMorni-FakeNetwork.jar" "$SERVER/plugins/"
fi
for jar in ${EXTRA_PLUGINS:-}; do
  cp "$jar" "$SERVER/plugins/"
done

echo "== Server starten (Port $PORT, RCON $RCON_PORT)"
cd "$SERVER"
: > console.log
$NICE "$JAVA" -Xms${XMS:-1G} -Xmx${XMX:-2G} ${JAVA_OPTS:-} -jar paper.jar --nogui < /dev/null > console.log 2>&1 &
PID=$!
echo "$PID" > "$SERVER/.pid"
stop_server() {
  if kill -0 "$PID" 2>/dev/null; then
    node "$HERE/rcon.cjs" "127.0.0.1:$RCON_PORT" "$SERVER/.rcon-pass" stop >/dev/null 2>&1 || kill "$PID" || true
    for _ in $(seq 1 60); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$PID" 2>/dev/null || true
  fi
}
if [ "${KEEP:-0}" != "1" ]; then
  trap stop_server EXIT
fi

for _ in $(seq 1 300); do
  if grep -q "Adlerflug ist bereit" console.log && grep -q "RCON running" console.log; then break; fi
  if ! kill -0 "$PID" 2>/dev/null; then echo "Server ist abgestürzt:"; tail -40 console.log; exit 1; fi
  sleep 1
done
grep -q "Adlerflug ist bereit" console.log || { echo "Adlerflug wurde nicht bereit:"; tail -40 console.log; exit 1; }
# Die Strecken werden nach dem Start im Hintergrund vorbereitet (in einer frischen Welt erzeugt) – erst danach testen,
# sonst kommen die Chunks an der Schanze beim Test-Spieler zu spät an.
for _ in $(seq 1 300); do
  if grep -q "Strecken vorbereitet" console.log; then break; fi
  if ! kill -0 "$PID" 2>/dev/null; then echo "Server ist abgestürzt:"; tail -40 console.log; exit 1; fi
  sleep 1
done
# Die Strecke des Tages wird nach dem Start im Hintergrund gerechnet.
for _ in $(seq 1 120); do
  if grep -q "Strecke des Tages bereit" console.log; then break; fi
  sleep 1
done
grep -E "Modell gebaut|Adlerflug-Welt bereit|Adlerflug ist bereit|Strecken vorbereitet|Strecke des Tages|Netzwerk|nicht fliegbar" console.log || true

if [ "${SKIP_TEST:-0}" = "1" ]; then
  echo "Server läuft (PID $PID) – anhalten mit: node $HERE/rcon.cjs 127.0.0.1:$RCON_PORT $SERVER/.rcon-pass stop"
  exit 0
fi

echo "== Test-Spieler"
STATUS=0
node "$HERE/adlerflug-test.cjs" 127.0.0.1 "$PORT" "127.0.0.1:$RCON_PORT" "$SERVER/.rcon-pass" "$SERVER" || STATUS=$?

echo "== Fehler im Server-Log:"
if grep -qE "ERROR|Exception" console.log; then
  grep -E "ERROR|Exception" console.log | head -20
else
  echo "keine"
fi
exit $STATUS
