/* StreamBox web — lightweight companion to the Android app.
 * Parses iptv-org M3U playlists in the browser, plays HLS via hls.js.
 * Favorites / custom lists / recents live in localStorage.
 */
"use strict";

const DEFAULT_URL = "https://iptv-org.github.io/iptv/index.m3u";
const PRESETS = [
  ["iptv-org: All channels", DEFAULT_URL],
  ["iptv-org: Grouped by category", "https://iptv-org.github.io/iptv/index.category.m3u"],
  ["iptv-org: Grouped by country", "https://iptv-org.github.io/iptv/index.country.m3u"],
  ["iptv-org: Grouped by language", "https://iptv-org.github.io/iptv/index.language.m3u"],
];
const PAGE = 120;

const $ = (id) => document.getElementById(id);
const store = {
  get(k, d) { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } },
  set(k, v) { localStorage.setItem(k, JSON.stringify(v)); },
};

// ---------- state ----------
let channels = [];            // {key,name,url,logo,category,country}
let favorites = new Set(store.get("favorites", []));
let recents = store.get("recents", []);            // [key]
let lists = store.get("lists", []);                // [{id,name,keys:[]}]
let groupBy = store.get("groupBy", "category");
let selected = store.get("selected", { type: "all" }); // {type:'all'|'fav'|'recent'|'group'|'list', value?}
let query = "";
let filtered = [];
let rendered = 0;
let current = -1;             // index into filtered while playing
let hls = null;

const flag = (cc) => cc && /^[A-Z]{2}$/.test(cc)
  ? String.fromCodePoint(...[...cc].map(c => 0x1F1E6 + c.charCodeAt(0) - 65))
  : "";

function hash(s) {
  let h = 5381;
  for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) >>> 0;
  return h.toString(16);
}

// ---------- M3U parsing (same rules as the Android app) ----------
function parseM3U(text) {
  const out = [];
  const attr = /([a-zA-Z0-9-]+)="(.*?)"/g;
  const countryRe = /\.([a-z]{2})(?:@[^.]*)?$/i;
  let pending = null;
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) continue;
    if (/^#EXTINF/i.test(line)) { pending = line; continue; }
    if (line.startsWith("#")) continue;
    if (!pending || !line.includes("://") || line.includes(" ")) { pending = null; continue; }
    const extinf = pending; pending = null;
    // name = after first comma outside quotes
    let inQ = false, name = null;
    for (let i = 0; i < extinf.length; i++) {
      const c = extinf[i];
      if (c === '"') inQ = !inQ;
      else if (c === "," && !inQ) { name = extinf.slice(i + 1).trim(); break; }
    }
    if (name === null) continue;
    const attrs = {};
    for (const m of extinf.matchAll(attr)) attrs[m[1].toLowerCase()] = m[2];
    const tvgId = attrs["tvg-id"] || "";
    const cm = tvgId.match(countryRe);
    out.push({
      key: hash(line),
      name: name || line,
      url: line,
      logo: attrs["tvg-logo"] || "",
      category: attrs["group-title"] || "",
      country: cm ? cm[1].toUpperCase() : "",
    });
  }
  return out;
}

// ---------- data loading ----------
async function loadPlaylist() {
  const url = store.get("playlistUrl", DEFAULT_URL);
  $("status").textContent = "Loading playlist…";
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error("HTTP " + res.status);
    const text = await res.text();
    channels = parseM3U(text);
    $("status").textContent = channels.length.toLocaleString() + " channels";
    renderGroups();
    applyFilter();
  } catch (e) {
    $("status").textContent = "Playlist failed: " + e.message;
  }
}

// ---------- groups sidebar ----------
function groupCounts() {
  const map = new Map();
  const field = groupBy === "category" ? "category" : "country";
  for (const c of channels) {
    const g = c[field];
    if (g) map.set(g, (map.get(g) || 0) + 1);
  }
  return [...map.entries()].sort((a, b) => a[0].localeCompare(b[0]));
}

function renderGroups() {
  const nav = $("groups");
  nav.innerHTML = "";
  const add = (label, count, sel, onClick) => {
    const b = document.createElement("button");
    b.className = "group-row" + (sel ? " active" : "");
    b.innerHTML = `<span class="name"></span>${count != null ? `<span class="count">${count}</span>` : ""}`;
    b.querySelector(".name").textContent = label;
    b.onclick = () => { onClick(); store.set("selected", selected); renderGroups(); applyFilter(); };
    nav.appendChild(b);
  };
  const sep = (t) => {
    const d = document.createElement("div");
    d.className = "group-sep";
    d.textContent = t;
    nav.appendChild(d);
  };

  add("♥ Favorites", favorites.size, selected.type === "fav", () => selected = { type: "fav" });
  add("Continue watching", recents.length, selected.type === "recent", () => selected = { type: "recent" });
  if (lists.length) sep("My lists");
  for (const l of lists) {
    add("★ " + l.name, l.keys.length, selected.type === "list" && selected.value === l.id,
      () => selected = { type: "list", value: l.id });
  }
  sep(groupBy === "category" ? "Categories" : "Countries");
  add("All channels", channels.length, selected.type === "all", () => selected = { type: "all" });
  for (const [g, n] of groupCounts()) {
    const label = groupBy === "country" ? `${flag(g)} ${g}`.trim() : g;
    add(label, n, selected.type === "group" && selected.value === g,
      () => selected = { type: "group", value: g });
  }
}

// ---------- filtering + grid ----------
function currentList() {
  const field = groupBy === "category" ? "category" : "country";
  let base;
  switch (selected.type) {
    case "fav": base = channels.filter(c => favorites.has(c.key)); break;
    case "recent": {
      const byKey = new Map(channels.map(c => [c.key, c]));
      base = recents.map(k => byKey.get(k)).filter(Boolean);
      break;
    }
    case "list": {
      const l = lists.find(x => x.id === selected.value);
      const keys = new Set(l ? l.keys : []);
      base = channels.filter(c => keys.has(c.key));
      break;
    }
    case "group": base = channels.filter(c => c[field] === selected.value); break;
    default: base = channels;
  }
  const q = query.trim().toLowerCase();
  return q ? base.filter(c => c.name.toLowerCase().includes(q)) : base;
}

function groupTitle() {
  switch (selected.type) {
    case "fav": return "♥ Favorites";
    case "recent": return "Continue watching";
    case "list": return "★ " + (lists.find(x => x.id === selected.value)?.name || "List");
    case "group": return groupBy === "country"
      ? `${flag(selected.value)} ${selected.value}`.trim() : selected.value;
    default: return "All channels";
  }
}

function applyFilter() {
  filtered = currentList();
  rendered = 0;
  $("grid").innerHTML = "";
  $("groupTitle").textContent = groupTitle();
  $("empty").hidden = filtered.length > 0;
  renderMore();
}

const PH = `<svg class="ph" viewBox="0 0 24 24" width="44" height="44"><rect x="2" y="5" width="20" height="12" rx="2" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M9 20h6" stroke="currentColor" stroke-width="1.5"/></svg>`;

function renderMore() {
  const grid = $("grid");
  const end = Math.min(filtered.length, rendered + PAGE);
  const frag = document.createDocumentFragment();
  for (let i = rendered; i < end; i++) {
    const c = filtered[i];
    const el = document.createElement("div");
    el.className = "card";
    el.tabIndex = 0;
    el.role = "listitem";
    el.innerHTML = `${c.logo
      ? `<img loading="lazy" src="${c.logo.replace(/"/g, "&quot;")}" alt="" onerror="this.outerHTML='${PH.replace(/'/g, "\\'")}'">`
      : PH}
      <div class="nm"></div><div class="ct"></div>`;
    el.querySelector(".nm").textContent = c.name;
    el.querySelector(".ct").textContent =
      (favorites.has(c.key) ? "♥ " : "") + (c.category || (c.country ? flag(c.country) + " " + c.country : ""));
    const open = () => play(i);
    el.onclick = open;
    el.onkeydown = (e) => { if (e.key === "Enter") open(); };
    frag.appendChild(el);
  }
  grid.appendChild(frag);
  rendered = end;
}

new IntersectionObserver((entries) => {
  if (entries[0].isIntersecting && rendered < filtered.length) renderMore();
}).observe($("sentinel"));

// ---------- player ----------
function stopPlayback() {
  if (hls) { hls.destroy(); hls = null; }
  const v = $("video");
  v.pause();
  v.removeAttribute("src");
  v.load();
}

function showError(msg) {
  $("playerErrorMsg").textContent = msg;
  $("playerError").hidden = false;
}

function play(index) {
  const c = filtered[index];
  if (!c) return;
  current = index;
  $("player").hidden = false;
  $("playerError").hidden = true;
  $("pName").textContent = c.name;
  $("pSub").textContent = c.category || (c.country ? `${flag(c.country)} ${c.country}` : "");
  $("pLogo").src = c.logo || "";
  $("favBtn").classList.toggle("on", favorites.has(c.key));
  document.body.style.overflow = "hidden";

  // recents (cap 30)
  recents = [c.key, ...recents.filter(k => k !== c.key)].slice(0, 30);
  store.set("recents", recents);

  stopPlayback();
  const v = $("video");

  if (location.protocol === "https:" && c.url.startsWith("http://")) {
    showError("This stream uses plain http://, which browsers block on secure pages. " +
      "It may still play in the Android app or VLC.");
    return;
  }

  const isHls = /\.m3u8(\?|$)/i.test(c.url);
  if (isHls && window.Hls && Hls.isSupported()) {
    hls = new Hls({ maxBufferLength: 15 });
    hls.loadSource(c.url);
    hls.attachMedia(v);
    hls.on(Hls.Events.MANIFEST_PARSED, () => v.play().catch(() => {}));
    hls.on(Hls.Events.ERROR, (_e, data) => {
      if (data.fatal) {
        showError("Stream unavailable — it may be offline, geo-blocked, or its server " +
          "doesn't allow browser playback (CORS).");
        stopPlayback();
      }
    });
  } else {
    // native HLS (Safari) or progressive formats
    v.src = c.url;
    v.play().catch(() => {});
    v.onerror = () => showError("Stream unavailable — it may be offline, geo-blocked, " +
      "or in a format browsers can't play (e.g. raw MPEG-TS).");
  }
}

function zap(step) {
  if (!filtered.length) return;
  play((current + step + filtered.length) % filtered.length);
}

function closePlayer() {
  stopPlayback();
  $("player").hidden = true;
  document.body.style.overflow = "";
  applyFilter(); // refresh fav hearts / recents
}

$("closeBtn").onclick = closePlayer;
$("nextChan").onclick = () => zap(1);
$("prevChan").onclick = () => zap(-1);
$("retryBtn").onclick = () => play(current);
$("nextBtn").onclick = () => zap(1);
$("copyBtn").onclick = () => {
  const c = filtered[current];
  if (c) navigator.clipboard?.writeText(c.url);
  $("copyBtn").textContent = "Copied!";
  setTimeout(() => $("copyBtn").textContent = "Copy stream URL", 1500);
};
$("favBtn").onclick = () => {
  const c = filtered[current];
  if (!c) return;
  favorites.has(c.key) ? favorites.delete(c.key) : favorites.add(c.key);
  store.set("favorites", [...favorites]);
  $("favBtn").classList.toggle("on", favorites.has(c.key));
  renderGroups();
};

document.addEventListener("keydown", (e) => {
  if ($("player").hidden) return;
  if (e.key === "Escape") closePlayer();
  else if (e.key === "ArrowUp") { e.preventDefault(); zap(1); }
  else if (e.key === "ArrowDown") { e.preventDefault(); zap(-1); }
});

// ---------- custom lists ----------
function renderListRows() {
  const c = filtered[current];
  const box = $("listRows");
  box.innerHTML = "";
  $("listDialogChannel").textContent = c ? c.name : "";
  for (const l of lists) {
    const row = document.createElement("label");
    row.className = "list-row";
    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = c && l.keys.includes(c.key);
    cb.onchange = () => {
      if (!c) return;
      l.keys = cb.checked ? [...l.keys, c.key] : l.keys.filter(k => k !== c.key);
      store.set("lists", lists);
      renderGroups();
      row.querySelector(".count").textContent = l.keys.length;
    };
    row.append(cb, document.createTextNode(l.name));
    const n = document.createElement("span");
    n.className = "count";
    n.textContent = l.keys.length;
    row.appendChild(n);
    box.appendChild(row);
  }
}
$("addListBtn").onclick = () => { renderListRows(); $("listDialog").showModal(); };
$("listDone").onclick = () => $("listDialog").close();
$("newListForm").onsubmit = (e) => {
  e.preventDefault();
  const name = $("newListName").value.trim();
  if (!name) return;
  const c = filtered[current];
  lists.push({ id: Date.now(), name, keys: c ? [c.key] : [] });
  store.set("lists", lists);
  $("newListName").value = "";
  renderListRows();
  renderGroups();
};

// ---------- settings ----------
$("settingsBtn").onclick = () => {
  $("playlistUrl").value = store.get("playlistUrl", DEFAULT_URL);
  const box = $("presets");
  box.innerHTML = "";
  for (const [name, url] of PRESETS) {
    const b = document.createElement("button");
    b.textContent = name;
    b.onclick = () => { store.set("playlistUrl", url); $("settingsDialog").close(); loadPlaylist(); };
    box.appendChild(b);
  }
  $("settingsDialog").showModal();
};
$("settingsDone").onclick = () => $("settingsDialog").close();
$("urlForm").onsubmit = (e) => {
  e.preventDefault();
  const url = $("playlistUrl").value.trim();
  if (url) { store.set("playlistUrl", url); $("settingsDialog").close(); loadPlaylist(); }
};

// ---------- toolbar ----------
let searchTimer;
$("search").oninput = (e) => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => { query = e.target.value; applyFilter(); }, 200);
};
$("byCategory").onclick = () => setGroupBy("category");
$("byCountry").onclick = () => setGroupBy("country");
function setGroupBy(v) {
  groupBy = v;
  store.set("groupBy", v);
  if (selected.type === "group") selected = { type: "all" };
  $("byCategory").setAttribute("aria-selected", v === "category");
  $("byCountry").setAttribute("aria-selected", v === "country");
  renderGroups();
  applyFilter();
}

// ---------- boot ----------
$("byCategory").setAttribute("aria-selected", groupBy === "category");
$("byCountry").setAttribute("aria-selected", groupBy === "country");
loadPlaylist();
