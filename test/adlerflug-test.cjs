/**
 * Echter Test von Morni Adlerflug mit Test-Spielern (mineflayer) auf Paper 26.1.2 – gestartet von test/run.sh.
 *   node adlerflug-test.cjs <host> <port> <rcon-host:port> <rcon-passwort-datei> <server-ordner>
 *
 * Spieler: Anna (deutsch, Party-Leiterin mit Carl), Bert (englisch, fliegt alle Strecken), Carl (fliegt im Rennen mit),
 * Dora (Übungsmodus, Lobby). Sprache und Party kommen vom Test-Netzwerk (test/fake-network), das auch Statistik,
 * Zustand und „zur Lobby“ in calls.log mitschreibt.
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
  check('Sechs Stationen im Adlerhorst', stationen.length === 6, stationen.map((s) => s.course).join(', '));

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
  check('Alle sechs Strecken im Menü', ['Almrundflug', 'Klammflug', 'Burgruine', 'Gletscherspalte', 'Stollenflug', 'Gipfelkreuz']
    .every((n) => titles.some((t) => t.includes(n))), titles.join(' / '));
  await closeWindow(anna);

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
  // mineflayer braucht nach dem Sprung zur Schanze etwas, bis die Chunks dort da sind
  await waitFor(() => Math.hypot(anna.entity.position.x - alm.stand[0], anna.entity.position.z - alm.stand[2]) < 2
    && anna.blockAt(anna.entity.position.offset(0, -1, 0)) != null && anna.entity.onGround, 25000, 250);
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
  const took = await waitFor(async () => (await status()).Anna?.state === 'FLYING', 8000, 150);
  anna.setControlState('forward', false);
  anna.setControlState('sprint', false);
  check('Absprung erkannt – Zeit läuft', took && sawBar(anna, /Los!/), anna.bars.slice(-2).join(' | '));
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
  const gone = farthest;
  check('Flügel offen (Server: gleitet)', glidePos.some((x) => x?.gliding), glidePos.map((x) => x?.gliding).join(','));
  check('Echter Absprung: in der Luft weg von der Schanze', took && gone > 6 && low < alm.line[1] - 3,
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
  let r = await guidedFlight(anna, 'almrundflug', 'daneben 3', 60000);
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
  check('Leiste nach dem Ziel (Nochmal fliegen)', /Nochmal/.test(itemName(slot(anna, 0))), itemName(slot(anna, 0)));

  // ---------- Zweiter Flug: sauber → neue Bestzeit, Medaille ----------
  clear(anna);
  await useItem(anna, 0); // Nochmal fliegen
  await waitFor(async () => (await status()).Anna?.state === 'READY', 6000, 250);
  r = await guidedFlight(anna, 'almrundflug', '', 90000);
  const times = bestOf('Anna', 'time_almrundflug_ms');
  check('Zweiter Flug: neue Bestzeit', r.done && sees(anna, /Neue Bestzeit/) && times.length >= 2 && times[1] < times[0],
    `${times.join(' → ')} ms | ${anna.chats.filter((c) => /Bestzeit|Medaille/.test(c)).join(' | ')}`);
  check('Medaille erflogen', sees(anna, /Neue Medaille/) && sumAdd('Anna', 'medals') >= 1, String(sumAdd('Anna', 'medals')));
  check('Zwischenzeiten im Vergleich (grün/rot)', sawBar(anna, /Ring \d+\/12 .*\([+−]/), anna.bars.filter((b) => /Ring/.test(b)).slice(-1)[0]);
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

  // ---------- Bert fliegt alle Strecken (Englisch, jeder Ring zählt) ----------
  const week0 = (await cmd('adleradmin status'))[0].split('\n')[0].split('woche=')[1].trim();
  for (const st2 of stationen) {
    const id = st2.course;
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
  clear(bert);
  bert.chat('/adlerflug stats');
  await sleep(800);
  check('Statistik (Englisch)', sees(bert, /Finished: 6/), bert.chats.slice(-3).join(' | '));

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

  // ---------- Übungsmodus: Dora ab Ring 5 ----------
  const dora = await join('Dora');
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
  await sleep(500);
  check('Geist umschalten', sees(anna, /Geist (aus|an)/));
  anna.chat('/adlerflug verstecken');
  await sleep(500);
  check('Mitspieler ausblenden', sees(anna, /ausgeblendet/));
  anna.chat('/adlerflug verstecken');
  await sleep(300);
  check('Kein Schaden (Leben voll)', anna.health === 20 && bert.health === 20, `${anna.health}/${bert.health}`);
  dora.chat('/adlerflug lobby');
  await waitFor(() => dora.kickReason != null || calls().some((c) => c.t === 'to-lobby' && c.name === 'Dora'), 4000);
  check('Zur Lobby über das Netzwerk', calls().some((c) => c.t === 'to-lobby' && c.name === 'Dora'));
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
