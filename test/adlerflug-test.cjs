/**
 * Echter Test von Morni Adlerflug mit Test-Spielern (mineflayer) auf Paper 26.1.2 – gestartet von test/run.sh.
 *   node adlerflug-test.cjs <host> <port> <rcon-host:port> <rcon-passwort-datei> <server-ordner>
 *
 * Spieler: Anna (deutsch, Party-Leiterin mit Carl), Bert (englisch, fliegt alle Strecken, die Strecke des Tages und die
 * Bonus-Ringe), Carl (fliegt im Rennen mit), Dora (Übungsmodus, Tor im Takt, Lobby). Sprache und Party kommen vom
 * Test-Netzwerk (test/fake-network), das auch Statistik, Zustand, „spielt gerade“ und „zur Lobby“ in calls.log
 * mitschreibt.
 *
 * Geflogen wird zweimal echt (Absprung von der Schanze mit den Flügeln von mineflayer) und sonst mit dem Test-Autopiloten
 * des Servers (/adleradmin fliege): Er führt den Spieler Tick für Tick den Musterflug entlang – die Ringe erkennt das
 * Plugin dabei genau wie bei echten Spielern aus der Strecke zwischen zwei Positionen.
 *
 * Verfasst mit Claude 🤖
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const mineflayer = require('mineflayer');
const { rcon } = require('./rcon.cjs');

const [HOST = '127.0.0.1', PORT = '25950', RCON = '127.0.0.1:26050', PASS_FILE, SERVER] = process.argv.slice(2);
const PASS = fs.readFileSync(PASS_FILE, 'utf8').trim();
const VERSION = process.env.MC_VERSION ?? '26.1';
const ONLY = process.env.ONLY ?? '';
const TEIL = process.env.TEIL ?? '';

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function check(name, ok, detail = '') {
  results.push({ name, ok: Boolean(ok) });
  console.log(`${ok ? '✓' : '✗'} ${name}${detail ? ` – ${detail}` : ''}`);
}

/** Text aus Chat-Komponenten (String, JSON oder NBT) zusammensetzen. */
function plain(v) {
  if (v == null) return '';
  if (typeof v === 'string') {
    if (v.startsWith('{') || v.startsWith('[')) {
      try {
        return plain(JSON.parse(v));
      } catch {
        return v;
      }
    }
    return v;
  }
  if (Array.isArray(v)) return v.map(plain).join('');
  if (typeof v !== 'object') return String(v);
  if (v.type === 'compound') return plain(v.value);
  if (v.type === 'list') return plain(v.value?.value ?? []);
  if (v.type === 'string') return v.value;
  let s = '';
  if ('text' in v) s += plain(v.text);
  if ('' in v) s += plain(v['']);
  if ('translate' in v) s += plain(v.translate);
  if ('extra' in v) s += plain(v.extra);
  return s;
}

function offlineUuid(name) {
  const h = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}

const cmd = (...commands) => rcon(RCON, PASS, commands);
const readFile = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '');
const playerFile = (name) => path.join(SERVER, 'plugins/MorniAdlerflug/spieler', `${offlineUuid(name)}.yml`);
const ghostFile = (name, course) => path.join(SERVER, 'plugins/MorniAdlerflug/geister', offlineUuid(name), `${course}.bin`);
const calls = () =>
  readFile(path.join(SERVER, 'plugins/MoinMorni/calls.log'))
    .split('\n')
    .filter(Boolean)
    .map((l) => JSON.parse(l));
const statsOf = (name) => calls().filter((c) => c.t === 'stats' && c.name === name);
const sumAdd = (name, key) => statsOf(name).reduce((s, c) => s + (c.add?.[key] ?? 0), 0);
const bestOf = (name, key) => statsOf(name).map((c) => c.best?.[key]).filter((v) => v != null);

async function waitFor(cond, ms = 5000, step = 100) {
  const start = Date.now();
  while (Date.now() - start < ms) {
    if (await cond()) return true;
    await sleep(step);
  }
  return Boolean(await cond());
}

let lastJoin = 0;
async function join(name) {
  const wait = lastJoin + 3500 - Date.now();
  if (wait > 0) await sleep(wait);
  lastJoin = Date.now();
  const bot = mineflayer.createBot({ host: HOST, port: Number(PORT), username: name, version: VERSION, auth: 'offline' });
  bot.chats = [];
  bot.bars = [];
  bot.titles = [];
  bot.kickReason = null;
  bot.on('messagestr', (m, position) => (position === 'game_info' ? bot.bars : bot.chats).push(m));
  bot._client.on('action_bar', (p) => bot.bars.push(plain(p.text)));
  bot._client.on('set_title_text', (p) => bot.titles.push(plain(p.text)));
  bot._client.on('set_title_subtitle', (p) => bot.titles.push(plain(p.text)));
  bot.on('kicked', (r) => {
    bot.kickReason = plain(r);
  });
  bot.on('error', (e) => console.log(`  (${name}: ${e.message})`));
  await new Promise((resolve, reject) => {
    bot.once('spawn', resolve);
    bot.once('kicked', (r) => reject(new Error(`${name} gekickt: ${plain(r)}`)));
    setTimeout(() => reject(new Error(`${name}: kein Spawn`)), 30000);
  });
  await sleep(2500);
  return bot;
}

const slot = (bot, i) => bot.inventory.slots[36 + i];
const itemName = (it) => (it ? plain(it.customName) || it.name : '');
const sees = (bot, re) => bot.chats.some((c) => re.test(c));
const sawTitle = (bot, re) => bot.titles.some((t) => re.test(t));
const sawBar = (bot, re) => bot.bars.some((t) => re.test(t));
const clear = (...bots) =>
  bots.forEach((b) => {
    b.chats.length = 0;
    b.bars.length = 0;
    b.titles.length = 0;
  });

/** /adleradmin status als Werte: {Anna: {lang, gliding, ground, pos:[x,y,z], course, state, next, resets, …}} */
async function status() {
  const lines = (await cmd('adleradmin status'))[0].split('\n');
  const out = {};
  for (const line of lines) {
    const m = /^spieler=(\S+) lang=(\S+) gliding=(\S+) ground=(\S+) pos=([-\d.]+),([-\d.]+),([-\d.]+) flight=\[(.*)\]$/.exec(line);
    if (!m) continue;
    const s = { lang: m[2], gliding: m[3] === 'true', ground: m[4] === 'true', pos: [+m[5], +m[6], +m[7]] };
    const f = m[8];
    if (f !== 'none') {
      const parts = f.split(' ');
      s.course = parts[0];
      for (const p of parts.slice(1)) {
        const [k, v] = p.split('=');
        s[k] = /^-?\d+$/.test(v) ? Number(v) : v;
      }
    }
    out[m[1]] = s;
  }
  return out;
}

const courses = {};
async function course(id) {
  if (!courses[id]) courses[id] = JSON.parse((await cmd(`adleradmin strecke ${id}`))[0]);
  return courses[id];
}

async function openWithItem(bot, hotbar) {
  bot.setQuickBarSlot(hotbar);
  await sleep(300);
  const opened = new Promise((resolve) => {
    bot.once('windowOpen', resolve);
    setTimeout(() => resolve(null), 5000);
  });
  bot.activateItem();
  return opened;
}

async function useItem(bot, hotbar) {
  bot.setQuickBarSlot(hotbar);
  await sleep(300);
  bot.activateItem();
  await sleep(600);
}

async function closeWindow(bot) {
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(300);
}

/** Strecke über den Befehl wählen und warten, bis man auf der Schanze steht. */
async function select(bot, id) {
  bot.chat(`/adlerflug start ${id}`);
  return waitFor(async () => (await status())[bot.username]?.course === id, 6000, 250);
}

/** Mit dem Test-Autopiloten fliegen (ohne eigene Physik) und warten, bis der Flug vorbei ist. */
async function guidedFlight(bot, id, extra = '', timeoutMs = 120000) {
  const c = await course(id);
  bot.physicsEnabled = false;
  await cmd(`adleradmin fliege ${bot.username} ${extra}`.trim());
  const done = await waitFor(async () => {
    const s = (await status())[bot.username];
    return !s || s.course !== id || (extra.includes('daneben') && s.resets > 0);
  }, timeoutMs, 400);
  return { done, c };
}

async function landPhysics(bot) {
  await sleep(2600);
  bot.physicsEnabled = true;
  await sleep(500);
}

async function main() {
  console.log(`== Adlerflug-Test gegen ${HOST}:${PORT}`);
  const stationen = JSON.parse((await cmd('adleradmin stationen'))[0]);
  check('Acht Stationen + Strecke des Tages im Adlerhorst', stationen.length === 9 && stationen.some((s) => s.course === 'tagesflug'),
    stationen.map((s) => s.course).join(', '));
  const day = JSON.parse((await cmd('adleradmin tag'))[0]);
  check('Strecke des Tages ist fertig gerechnet', day.ready && /^\d{4}-\d\d-\d\d$/.test(day.day) && day.rings >= 10,
    JSON.stringify(day));

  // ---------- Ankommen ----------
  const anna = await join('Anna');
  const bert = await join('Bert');
  await waitFor(() => sees(bert, /Eagle's Nest/), 8000, 200);
  let s = await status();
  check('Anna landet im Adlerhorst', s.Anna && Math.hypot(s.Anna.pos[0], s.Anna.pos[2]) < 25 && s.Anna.pos[1] > 149,
    JSON.stringify(s.Anna?.pos));
  const chest = (await cmd('data get entity Anna equipment.chest'))[0];
  check('Flügel angelegt (Brust, unzerbrechlich)', /elytra/.test(chest) && /unbreakable/.test(chest), chest.slice(0, 90));
  check('Flugkarte in der Leiste', /Flugkarte/.test(itemName(slot(anna, 0))), itemName(slot(anna, 0)));
  check('Begrüßung auf Deutsch', sees(anna, /Adlerhorst/) && sawTitle(anna, /Adlerflug/));
  check('Begrüßung auf Englisch (Bert, vom Netzwerk)', sees(bert, /Eagle's Nest/) && s.Bert.lang === 'en',
    bert.chats.slice(-3).join(' | '));
  check('Englische Leiste', /Flight map/.test(itemName(slot(bert, 0))), itemName(slot(bert, 0)));
  check('Zustand ans Netzwerk (open)', calls().some((c) => c.t === 'state' && c.mode === 'adlerflug' && c.state === 'open'));

  // ---------- Menü ----------
  const win = await openWithItem(anna, 0);
  const titles = win ? win.slots.filter(Boolean).map(itemName) : [];
  check('Flugkarte öffnet die Strecken', win && /Strecken/.test(plain(win.title)), win && plain(win.title));
  check('Alle acht Strecken im Menü', ['Almrundflug', 'Klammflug', 'Burgruine', 'Gletscherspalte', 'Stollenflug',
    'Felsenlabyrinth', 'Gipfelkreuz', 'Höllenschlund'].every((n) => titles.some((t) => t.includes(n))), titles.join(' / '));
  check('Strecke des Tages im Menü', titles.some((t) => /Tagesflug/.test(t)), titles.join(' / '));
  await closeWindow(anna);

  // TEIL=rest überspringt Annas Flüge auf dem Almrundflug (auf einem knappen Rechner, um den Rest nachzuholen)
  let r;
  if (TEIL !== 'rest') {
    // ---------- Station anklicken → Schanze ----------
    const st = stationen.find((x) => x.course === 'almrundflug');
    // vor das Schild stellen (Reichweite), zur Mitte des Platzes hin versetzt
    const toMid = Math.hypot(st.x, st.z);
    const sx = st.x + 0.5 - (st.x / toMid) * 2.5;
    const sz = st.z + 0.5 - (st.z / toMid) * 2.5;
    await cmd(`execute in minecraft:adlerflug run tp Anna ${sx.toFixed(2)} 151 ${sz.toFixed(2)}`);
    await sleep(1200);
    const sign = anna.blockAt(new (require('vec3'))(st.x, st.y, st.z));
    check('Schild an der Station', sign && /sign/.test(sign.name), sign?.name);
    await anna.lookAt(sign.position.offset(0.5, 0.5, 0.5), true);
    await anna.activateBlock(sign);
    const onRamp = await waitFor(async () => (await status()).Anna?.state === 'READY', 6000, 250);
    s = await status();
    const alm = await course('almrundflug');
    check('Schild startet die Strecke (auf der Schanze)', onRamp && Math.hypot(s.Anna.pos[0] - alm.stand[0], s.Anna.pos[2] - alm.stand[2]) < 2,
      JSON.stringify(s.Anna));
    // mineflayer braucht nach dem Sprung zur Schanze etwas, bis die Chunks dort da sind – auch die vor der Kante: in einem
    // Chunk, den es noch nicht hat, bleibt der Test-Spieler einfach stehen (bei Almrundflug liegt die Kante schon im
    // nächsten Chunk). In einer frisch erzeugten Welt auf einem ausgelasteten Rechner dauert das auch einmal eine Minute.
    {
      const V = require('vec3');
      const len = Math.hypot(alm.line[0] - alm.stand[0], alm.line[2] - alm.stand[2]) || 1;
      const ux = (alm.line[0] - alm.stand[0]) / len;
      const uz = (alm.line[2] - alm.stand[2]) / len;
      const ahead = [0, 4, 8, 12, 16, 20].map((k) => new V(alm.line[0] + ux * k, alm.line[1] - 2, alm.line[2] + uz * k));
      const ready = await waitFor(() => Math.hypot(anna.entity.position.x - alm.stand[0], anna.entity.position.z - alm.stand[2]) < 2
        && anna.blockAt(anna.entity.position.offset(0, -1, 0)) != null && anna.entity.onGround
        && ahead.every((at) => anna.blockAt(at) != null), 120000, 250);
      if (!ready) console.log('  (Chunks vor der Schanzenkante sind beim Test-Spieler nach 2 Minuten noch nicht da)');
    }
    await sleep(1500);
    check('Unterwegs-Leiste (Zurück zum Ring)', /letzten Ring/.test(itemName(slot(anna, 0))), itemName(slot(anna, 0)));

    // ---------- Echter Absprung mit Flügeln ----------
    clear(anna);
    {
      const V = require('vec3');
      const dx = alm.line[0] - alm.stand[0];
      const dz = alm.line[2] - alm.stand[2];
      await anna.lookAt(new V(alm.line[0] + dx * 4, anna.entity.position.y + 1.6, alm.line[2] + dz * 4), true);
    }
    anna.setControlState('forward', true);
    anna.setControlState('sprint', true);
    // Bis zur Kante sind es ein paar Schritte; auf einem ausgelasteten Rechner (Test mit nice) läuft der Test-Spieler
    // deutlich langsamer als im echten Spiel – darum großzügig warten.
    const took = await waitFor(async () => (await status()).Anna?.state === 'FLYING', 60000, 150);
    anna.setControlState('forward', false);
    anna.setControlState('sprint', false);
    check('Absprung erkannt – Zeit läuft', took && sawBar(anna, /Los!/), anna.bars.slice(-2).join(' | '));
    check('Netzwerk: Anna fliegt gerade (playing)', await waitFor(() => calls().some((c) => c.t === 'playing' && c.name === 'Anna'
      && c.playing === true), 3000));
    const glidePos = [];
    for (let i = 0; i < 14; i++) {
      await sleep(200);
      const x = (await status()).Anna;
      glidePos.push(x);
    }
    const g0 = glidePos[0];
    const g1 = glidePos[glidePos.length - 1];
    const farthest = glidePos.filter((x) => x?.state === 'FLYING').reduce((m, x) => Math.max(m, Math.hypot(x.pos[0] - alm.stand[0], x.pos[2] - alm.stand[2])), 0);
    const low = glidePos.filter((x) => x?.state === 'FLYING').reduce((m, x) => Math.min(m, x.pos[1]), 999);
    // mineflayer gleitet nicht: es fällt im Bogen von der Kante (ohne Flügel ein paar Blöcke weit)
    const gone = farthest;
    check('Flügel offen (Server: gleitet)', glidePos.some((x) => x?.gliding), glidePos.map((x) => x?.gliding).join(','));
    check('Echter Absprung: in der Luft weg von der Schanze', took && gone > 4.5 && low < alm.line[1] - 3,
      `${gone.toFixed(1)} Blöcke, tiefster Punkt ${low.toFixed(1)}, danach ${g1?.state} (mineflayer gleitet nicht und landet → zurück)`);
    console.log(`  (echter Gleitflug: ${anna.entity.elytraFlying ? 'mineflayer gleitet' : 'mineflayer fällt'}, nächster Ring ${g1?.next})`);

    // ---------- Zurück-Gegenstand und Neustart ----------
    clear(anna);
    await useItem(anna, 1);
    s = await status();
    check('Neustart: zurück auf der Schanze', s.Anna.state === 'READY' && sawBar(anna, /Neustart/),
      `${s.Anna.state} ${anna.bars.slice(-1)}`);

    // ---------- Verpasster Ring (Autopilot fliegt daneben) ----------
    clear(anna);
    r = await guidedFlight(anna, 'almrundflug', 'daneben 3', 60000);
    s = await status();
    check('Ring 3 verpasst → zurück', r.done && sees(anna, /Ring 3 verpasst/), anna.chats.slice(-2).join(' | '));
    check('Weiter ab Ring 2 (in der Luft, Zeit läuft)', s.Anna?.state === 'FLYING' && s.Anna?.next === 2 && s.Anna.gliding,
      JSON.stringify(s.Anna));
    const ring2 = alm.rings[1];
    check('Position kurz hinter Ring 2', s.Anna && Math.hypot(s.Anna.pos[0] - ring2.c[0], s.Anna.pos[2] - ring2.c[2]) < 12,
      JSON.stringify(s.Anna?.pos));
    // Rest der Strecke mit dem Autopiloten (erster Flug mit Zurücksetzen = langsamer)
    clear(anna);
    r = await guidedFlight(anna, 'almrundflug', '', 90000);
    check('Ziel erreicht (erster Flug)', r.done && sawTitle(anna, /Ziel/) && sees(anna, /Erster Flug auf Almrundflug/),
      anna.chats.slice(-4).join(' | '));
    const firstTime = bestOf('Anna', 'time_almrundflug_ms')[0];
    check('Bestzeit ans Netzwerk (kleiner ist besser)', firstTime > 0 && statsOf('Anna').some((c) => c.lower === true),
      `${firstTime} ms`);
    check('Ringe gezählt (12 + 1 verpasst-Zwischenstand)', sumAdd('Anna', 'rings') >= 12, String(sumAdd('Anna', 'rings')));
    check('Zurücksetzen gezählt (resets)', sumAdd('Anna', 'resets') >= 1, String(sumAdd('Anna', 'resets')));
    check('Flug gezählt (flights)', sumAdd('Anna', 'flights') >= 2, String(sumAdd('Anna', 'flights')));
    await landPhysics(anna);
    s = await status();
    check('Nach dem Ziel auf dem Landeplatz', s.Anna && Math.hypot(s.Anna.pos[0] - alm.landing[0], s.Anna.pos[2] - alm.landing[2]) < 4,
      JSON.stringify(s.Anna?.pos));
    // Die Leiste kommt im selben Moment wie der Sprung auf den Landeplatz – unter Last beim Test-Spieler etwas später.
    await waitFor(() => /Nochmal/.test(itemName(slot(anna, 0))), 5000, 200);
    check('Leiste nach dem Ziel (Nochmal fliegen)', /Nochmal/.test(itemName(slot(anna, 0))), itemName(slot(anna, 0)));

    // ---------- Zweiter Flug: sauber → neue Bestzeit, Medaille ----------
    clear(anna);
    await useItem(anna, 0); // Nochmal fliegen
    await waitFor(async () => (await status()).Anna?.state === 'READY', 6000, 250);
    r = await guidedFlight(anna, 'almrundflug', '', 90000);
    // die Meldungen kommen im selben Tick wie das Ziel – unter Last beim Test-Spieler etwas später
    await waitFor(() => sees(anna, /Neue Bestzeit/) && sees(anna, /Neue Medaille/), 8000, 200);
    const times = bestOf('Anna', 'time_almrundflug_ms');
    check('Zweiter Flug: neue Bestzeit', r.done && sees(anna, /Neue Bestzeit/) && times.length >= 2 && times[1] < times[0],
      `${times.join(' → ')} ms | ${anna.chats.filter((c) => /Bestzeit|Medaille/.test(c)).join(' | ')}`);
    check('Medaille erflogen', sees(anna, /Neue Medaille/) && sumAdd('Anna', 'medals') >= 1, String(sumAdd('Anna', 'medals')));
    check('Zwischenzeiten im Vergleich (grün/rot)', sawBar(anna, /Ring \d+\/12 .*\([+−]/), anna.bars.filter((b) => /Ring/.test(b)).slice(-1)[0]);
    check('Tempo in der Aktionsleiste', anna.bars.some((b) => /[1-9]\d* km\/h/.test(b)), anna.bars.filter((b) => /km/.test(b)).slice(-1)[0]);
    const yml = readFile(playerFile('Anna'));
    check('Bestzeit in der Spielerdatei', /almrundflug:[\s\S]*best: \d+/.test(yml));
    check('Geist der Bestzeit gespeichert', fs.existsSync(ghostFile('Anna', 'almrundflug')));
    await landPhysics(anna);

    // ---------- Bestenliste ----------
    clear(anna);
    anna.chat('/adlerflug top almrundflug');
    await sleep(800);
    check('Bestenliste per Befehl', sees(anna, /1\. Anna/), anna.chats.slice(-2).join(' | '));
    const book = await openWithItem(anna, 2);
    check('Flugbuch öffnet', book && /Flugbuch/.test(plain(book.title)), book && plain(book.title));
    await closeWindow(anna);

    // ---------- Gelandet und zu weit weg ----------
    clear(anna);
    await select(anna, 'almrundflug');
    r = await guidedFlight(anna, 'almrundflug', 'bis 4', 40000);
    await sleep(400);
    s = await status();
    const r4 = alm.rings[3];
    const r5 = alm.rings[4];
    // auf den Boden zwischen Ring 4 und 5 setzen (Physik an: steht dort)
    anna.physicsEnabled = true;
    const mx = ((r4.c[0] + r5.c[0]) / 2).toFixed(1);
    const mz = ((r4.c[2] + r5.c[2]) / 2).toFixed(1);
    await cmd(`execute in minecraft:adlerflug positioned ${mx} ${r5.c[1].toFixed(1)} ${mz} positioned over motion_blocking_no_leaves run tp Anna ~ ~ ~`);
    clear(anna);
    const landed = await waitFor(() => sees(anna, /Gelandet|Ins Wasser|Zu weit/), 6000, 200);
    check('Gelandet → zurück zum letzten Ring', landed && sees(anna, /Gelandet/), anna.chats.slice(-2).join(' | '));
    anna.physicsEnabled = false;
    await sleep(500);
    s = await status();
    await cmd('execute as Anna at Anna run tp Anna ~ ~ ~70');
    clear(anna);
    const far = await waitFor(() => sees(anna, /Zu weit/), 4000, 200);
    check('Zu weit von der Strecke → zurück', far, anna.chats.slice(-2).join(' | '));
    clear(anna);
    anna.chat('/adlerflug ring');
    await sleep(700);
    check('„Zurück zum letzten Ring“ per Befehl', sees(anna, /Zurück zum letzten Ring/));
    anna.chat('/adlerflug aufgeben');
    await sleep(800);
    s = await status();
    check('Aufgeben → Adlerhorst', !s.Anna.course && Math.hypot(s.Anna.pos[0], s.Anna.pos[2]) < 25, JSON.stringify(s.Anna));
    anna.physicsEnabled = true;
  }

  // ---------- Bert fliegt alle Strecken (Englisch, jeder Ring zählt) ----------
  const week0 = (await cmd('adleradmin status'))[0].split('\n')[0].split('woche=')[1].trim();
  for (const st2 of stationen) {
    const id = st2.course;
    if (id === 'tagesflug') continue;
    if (ONLY && !ONLY.split(',').includes(id)) continue;
    const c = await course(id);
    clear(bert);
    const before = sumAdd('Bert', 'rings');
    const ok = await select(bert, id);
    r = await guidedFlight(bert, id, '', 180000);
    const rings = sumAdd('Bert', 'rings') - before;
    const t = bestOf('Bert', `time_${id}_ms`).slice(-1)[0];
    if (id === week0) {
      check('Strecke der Woche geflogen → Wochen-Adler', sees(bert, /Eagle of the week/), bert.chats.filter((x) => /week/.test(x)).join(' | '));
    }
    check(`${c.name}: alle ${c.rings.length} Ringe, Ziel (EN)`, ok && r.done && rings === c.rings.length && t > 0
      && (sawTitle(bert, /Finish/) || sees(bert, /First flight on|personal best/)), `Ringe ${rings}/${c.rings.length}, Zeit ${t} ms (Muster ${c.ms} ms)`);
    await landPhysics(bert);
  }
  await sleep(5500);
  const tab = plain(bert.players?.Bert?.displayName ?? '');
  check('Wochen-Adler ▲ in der Tab-Liste', tab.includes('▲'), tab);
  check('Netzwerk: nach dem Ziel spielt Bert nicht mehr', (() => {
    const p = calls().filter((c) => c.t === 'playing' && c.name === 'Bert');
    return p.length >= 2 && p[p.length - 1].playing === false;
  })());

  // ---------- Bonus-Ringe: Bert fliegt den Weg über den Bonus-Ring ----------
  {
    const id = 'klammflug';
    const c = await course(id);
    clear(bert);
    const before = sumAdd('Bert', 'bonus_rings');
    await select(bert, id);
    r = await guidedFlight(bert, id, 'bonus', 180000);
    const t = bestOf('Bert', `time_${id}_ms`).slice(-1)[0];
    check('Bonus-Ring eingesammelt (Gutschrift im Flug)', r.done && sawBar(bert, /Bonus!/) && sumAdd('Bert', 'bonus_rings') === before + c.bonus.length,
      `${sumAdd('Bert', 'bonus_rings') - before}/${c.bonus.length} ${bert.bars.filter((b) => /Bonus/.test(b)).slice(-1)[0]}`);
    check('Gutschrift im Ziel', sees(bert, /bonus ring\(s\):/), bert.chats.filter((x) => /bonus/.test(x)).join(' | '));
    await landPhysics(bert);
  }

  // ---------- Strecke des Tages ----------
  {
    const id = 'tagesflug';
    const c = await course(id);
    clear(bert);
    bert.chat('/adlerflug tag');
    await sleep(800);
    check('/adlerflug tag zeigt die Bedingungen (Englisch)', sees(bert, /Course of the day/) && sees(bert, /(calm|wind)/),
      bert.chats.slice(-3).join(' | '));
    const ok = await select(bert, id);
    await sleep(1200);
    const dayTicks = { MORGEN: 23250, MITTAG: 6000, ABEND: 12350, NACHT: 18000 }[day.time];
    const tod = Math.abs(Number(bert.time?.timeOfDay ?? -1)) % 24000;
    check('Tageszeit der Strecke des Tages (nur für den Flieger)', ok && Math.abs(tod - dayTicks) < 200, `${tod} statt ${dayTicks} (${day.time})`);
    if (day.weather === 'REGEN') {
      check('Regen auf der Strecke des Tages', bert.isRaining === true, String(bert.isRaining));
    }
    const before = sumAdd('Bert', 'rings');
    r = await guidedFlight(bert, id, '', 180000);
    const rings = sumAdd('Bert', 'rings') - before;
    check(`Strecke des Tages: alle ${c.rings.length} Ringe, Ziel`, ok && r.done && rings === c.rings.length
      && sees(bert, /First flight on .* today/), `Ringe ${rings}/${c.rings.length} | ${bert.chats.slice(-4).join(' | ')}`);
    check('Tage gezählt (days), keine Bestzeit ans Netzwerk', sumAdd('Bert', 'days') === 1 && bestOf('Bert', 'time_tagesflug_ms').length === 0,
      String(sumAdd('Bert', 'days')));
    clear(bert);
    bert.chat('/adlerflug top tagesflug');
    await sleep(800);
    check('Tages-Bestenliste', sees(bert, /1\. Bert/), bert.chats.slice(-2).join(' | '));
    await landPhysics(bert);
    clear(bert);
    bert.chat('/adlerflug basislager');
    await sleep(1500);
    const tod2 = Math.abs(Number(bert.time?.timeOfDay ?? -1)) % 24000;
    check('Zurück im Adlerhorst: wieder die Zeit der Welt', Math.abs(tod2 - 5000) < 300, String(tod2));
    // anderer Tag: neue Strecke, leere Tagesliste
    await cmd('adleradmin tag 2026-12-24');
    const changed = await waitFor(async () => JSON.parse((await cmd('adleradmin tag'))[0]).day === '2026-12-24', 30000, 500);
    delete courses.tagesflug;
    clear(bert);
    bert.chat('/adlerflug top tagesflug');
    await sleep(800);
    check('Neuer Tag: neue Strecke, Tagesliste leer', changed && !sees(bert, /1\. Bert/), bert.chats.slice(-2).join(' | '));
    const d2 = JSON.parse((await cmd('adleradmin tag'))[0]);
    const same = await (async () => {
      await cmd('adleradmin tag 2026-12-24');
      return JSON.stringify(JSON.parse((await cmd('adleradmin tag'))[0])) === JSON.stringify(d2);
    })();
    check('Gleicher Tag, gleiche Bedingungen', same, JSON.stringify(d2));
    await cmd('adleradmin tag heute');
    await waitFor(async () => JSON.parse((await cmd('adleradmin tag'))[0]).day === day.day, 30000, 500);
  }

  clear(bert);
  bert.chat('/adlerflug stats');
  await sleep(800);
  const bertFinishes = sumAdd('Bert', 'finishes');
  check('Statistik (Englisch)', sees(bert, new RegExp(`Finished: ${bertFinishes}`)) && sees(bert, /Course of the day finished on/),
    `${bertFinishes} | ${bert.chats.slice(-4).join(' | ')}`);

  // ---------- Party-Rennen: Anna (Leiterin) + Carl ----------
  const carl = await join('Carl');
  clear(anna, carl);
  await select(anna, 'burgruine');
  const invited = await waitFor(() => sees(carl, /startet ein Rennen/), 6000, 200);
  check('Carl wird zum Rennen eingeladen', invited, carl.chats.slice(-1)[0]);
  carl.chat('/adlerflug mitmachen');
  const go = await waitFor(async () => {
    const x = await status();
    return x.Anna?.state === 'FLYING' && x.Carl?.state === 'FLYING';
  }, 15000, 250);
  check('Countdown und gemeinsamer Absprung', go && sawTitle(carl, /LOS/) && sees(anna, /fliegt mit/), carl.titles.slice(-2).join(','));
  anna.physicsEnabled = false;
  carl.physicsEnabled = false;
  await cmd('adleradmin fliege Carl');
  await sleep(1500);
  await cmd('adleradmin fliege Anna');
  const raceDone = await waitFor(() => sees(anna, /Rennen vorbei/), 120000, 500);
  check('Rennen: Platzierung und Sieger', raceDone && sees(anna, /Platz 1/) && sees(anna, /Platz 2/) && sees(carl, /Sieger: Carl/),
    anna.chats.filter((c) => /Platz|Rennen/.test(c)).join(' | '));
  await landPhysics(anna);
  carl.physicsEnabled = true;

  // ---------- Tor im Takt: Dora kommt zu spät (Flugzeit um einen halben Takt verschoben) ----------
  const dora = await join('Dora');
  {
    const id = 'burgruine';
    const c = await course(id);
    const gate = c.rings.find((x) => x.gate);
    clear(dora);
    await select(dora, id);
    dora.physicsEnabled = false;
    await cmd('adleradmin fliege Dora takt 40');
    const shut = await waitFor(() => sees(dora, /Tor war zu/), 60000, 300);
    const st3 = (await status()).Dora;
    check('Tor im Takt zu → zurück zum letzten Ring', shut && st3?.resets >= 1 && st3?.next === gate.i,
      `${dora.chats.slice(-2).join(' | ')} ${JSON.stringify(st3)}`);
    clear(dora);
    r = await guidedFlight(dora, id, '', 90000);
    check('Im Takt geht das Tor auf → Ziel', r.done && sawTitle(dora, /Ziel/), dora.chats.slice(-3).join(' | '));
    await landPhysics(dora);
  }

  // ---------- Übungsmodus: Dora ab Ring 5 ----------
  clear(dora);
  dora.chat('/adlerflug uebung');
  await sleep(600);
  check('Übungsmodus an', sees(dora, /Übungsmodus an/));
  await select(dora, 'klammflug');
  await waitFor(() => /Ab Ring/.test(itemName(slot(dora, 4))), 6000, 200);
  check('Übung: „Ab Ring …“ in der Leiste', /Ab Ring/.test(itemName(slot(dora, 4))), itemName(slot(dora, 4)));
  const pick = await openWithItem(dora, 4);
  check('Ring-Auswahl öffnet', pick && /Übung/.test(plain(pick.title)), pick && plain(pick.title));
  if (pick) {
    dora.physicsEnabled = false;
    await dora.clickWindow(5, 0, 0); // Feld 5 = ab Ring 5
    await sleep(800);
  }
  s = await status();
  check('Einstieg vor Ring 5 in der Luft', s.Dora?.state === 'FLYING' && s.Dora?.next === 4, JSON.stringify(s.Dora));
  const doraBefore = statsOf('Dora').length;
  r = await guidedFlight(dora, 'klammflug', '', 60000);
  check('Übungsflug zählt nicht', r.done && sees(dora, /Übungsflug/) && statsOf('Dora').length === doraBefore,
    dora.chats.slice(-2).join(' | '));
  await landPhysics(dora);

  // ---------- Geist, Mitspieler, Lobby ----------
  clear(anna);
  anna.chat('/adlerflug geist');
  check('Geist umschalten', await waitFor(() => sees(anna, /Geist (aus|an)/), 5000, 200));
  anna.chat('/adlerflug verstecken');
  check('Mitspieler ausblenden', await waitFor(() => sees(anna, /ausgeblendet/), 5000, 200));
  anna.chat('/adlerflug verstecken');
  await sleep(300);
  check('Kein Schaden (Leben voll)', anna.health === 20 && bert.health === 20, `${anna.health}/${bert.health}`);
  // Mitten im Flug fragt das Netzwerk vor der Lobby nach (Schnittstelle 2), danach geht es sofort
  await select(dora, 'almrundflug');
  dora.physicsEnabled = false;
  await cmd('adleradmin fliege Dora bis 2');
  await waitFor(async () => (await status()).Dora?.state === 'FLYING', 10000, 250);
  clear(dora);
  dora.chat('/adlerflug lobby');
  await waitFor(() => calls().some((c) => c.t === 'ask-lobby' && c.name === 'Dora'), 4000);
  check('Lobby mitten im Flug: Netzwerk fragt nach', calls().some((c) => c.t === 'ask-lobby' && c.name === 'Dora' && c.asked === true)
    && dora.kickReason == null, JSON.stringify(calls().filter((c) => c.t === 'ask-lobby').slice(-1)[0]));
  dora.chat('/adlerflug aufgeben');
  await sleep(1000);
  check('Nach dem Aufgeben spielt Dora nicht mehr', (() => {
    const p = calls().filter((c) => c.t === 'playing' && c.name === 'Dora');
    return p.length > 0 && p[p.length - 1].playing === false;
  })());
  dora.physicsEnabled = true;
  await useItem(dora, 8); // „Zurück ins Dorf“
  await waitFor(() => dora.kickReason != null, 4000);
  check('Zur Lobby über das Netzwerk (Gegenstand → askToLobby, sofort)', calls().some((c) => c.t === 'ask-lobby' && c.name === 'Dora'
    && c.asked === false) && dora.kickReason != null, dora.kickReason ?? '');
  const states = calls().filter((c) => c.t === 'state');
  check('Zustand mit Spielerzahl', states.some((c) => c.players >= 3), JSON.stringify(states.slice(-1)[0]));

  for (const b of [anna, bert, carl]) b.quit();
  await sleep(1000);
  const ok = results.filter((x) => x.ok).length;
  console.log(`\n== ${ok}/${results.length} grün`);
  process.exit(ok === results.length ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  console.log(`\n== ${results.filter((x) => x.ok).length}/${results.length} grün (abgebrochen)`);
  process.exit(1);
});
