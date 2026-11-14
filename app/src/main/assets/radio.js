const soundNames = ["江畔远声", "雪夜窗外风", "列车行驶声", "窗前雨声"];
const soundDetails = [
  "江风、远处车流与轻柔水声",
  "隔着窗的柔和风声，缓慢起伏",
  "车厢低鸣、轮轨滚动与轻微节奏",
  "连续雨幕、细小落滴，无突然雷声",
];
let radioFilter = "全部",
  radioQuery = "",
  radioFavorites = readStore("radioFavorites", []);
function currentStation() {
  return (
    radioStations.find((r) => r.id === state.stationId) || radioStations[0]
  );
}
function tune(id) {
  if (!radioStations.some((r) => r.id === id)) return;
  cmd("radio", id);
  closeSheet();
}
function favoriteStation(id) {
  radioFavorites = radioFavorites.includes(id)
    ? radioFavorites.filter((x) => x !== id)
    : [...radioFavorites, id];
  localStorage.setItem("radioFavorites", JSON.stringify(radioFavorites));
  renderRadios();
  if (state.live)
    $("#fav").innerHTML = radioFavorites.includes(state.stationId)
      ? icons.heartFill
      : icons.heart;
}
function renderRadios() {
  const area = $("#radioResults");
  if (!area) return;
  const rows = radioStations.filter(
    (r) =>
      (radioFilter === "全部" ||
        (radioFilter === "收藏" && radioFavorites.includes(r.id)) ||
        r.group === radioFilter) &&
      `${r.name} ${r.tags}`.toLowerCase().includes(radioQuery.toLowerCase()),
  );
  area.innerHTML = rows.length
    ? rows
        .map(
          (r) =>
            `<div class="radio-row ${state.live && state.stationId === r.id ? "current" : ""}"><button class="tune" onclick="tune('${r.id}')"><strong>${esc(r.name)}${state.live && state.stationId === r.id ? " · 当前" : ""}</strong><small>${esc(r.tags)} · ${esc(r.provider)}</small></button><button class="star" aria-label="${radioFavorites.includes(r.id) ? "取消收藏" : "收藏"}${esc(r.name)}" onclick="favoriteStation('${r.id}')">${radioFavorites.includes(r.id) ? "♥" : "♡"}</button></div>`,
        )
        .join("")
    : "<p>没有匹配的电台。试试其他分类或搜索词。</p>";
}
function chooseSound(n) {
  cfg.customSound = "";
  cfg.soundChoice = n;
  save();
  cmd("sound", n);
  show("mixer");
}
function resetMix() {
  cfg.music = 0.75;
  cfg.ambient = cfg.noiseOnly ? 0.6 : state.live ? 0.15 : 0.3;
  save();
  cmd("volumes", { music: cfg.music, ambient: cfg.ambient });
  show("mixer");
}
