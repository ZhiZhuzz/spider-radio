// One native state entry point; update preferences once, then render the player.
function nativeState(s) {
  state = s;
  let changed = false;
  const keys = [
    "noiseOnly",
    "customSound",
    "mode",
    "liveNoise",
    "soundChoice",
    "music",
    "ambient",
  ];
  for (const key of keys) {
    if (s[key] !== undefined && cfg[key] !== s[key]) {
      cfg[key] = s[key];
      changed = true;
    }
  }
  if (changed) save();
  renderPlayerState(s);
  updateHomeMode();
  positionPlayer();
}
function renderPlayerState(s) {
  const liveMusic = !!s.live && !s.noiseOnly;
  document.body.classList.toggle("noise-only", !!s.noiseOnly);
  document.body.classList.toggle("playing", !!s.playing);
  document.body.classList.toggle("live-player", liveMusic);
  $("#play").innerHTML = (s.wanted ?? s.playing) ? icons.pause : icons.play;
  $("#status").textContent = s.playing ? "正在放送" : "等待播放";
  if (s.title) $("#song").textContent = s.title;
  $("#sourceLabel").textContent = s.noiseOnly
    ? "纯白噪音 · 切换来源"
    : s.live
      ? "直播电台 · 选台"
      : "本地音乐 · 切换来源";
  $(".subtitle").textContent = s.noiseOnly
    ? "主题白噪音 · 连续循环"
    : liveMusic
      ? s.buffering
        ? "正在连接，请稍候…"
        : s.playing
          ? currentStation().tags + " · 正在直播"
          : "已暂停 · 再播放将回到直播"
      : s.uri === "demo.wav"
        ? "原创试听 · 蓝色时差"
        : "本地音乐";
  $("#prev").disabled = !!s.noiseOnly;
  $("#next").disabled = !!s.noiseOnly;
  $("#prev").setAttribute("aria-label", s.live ? "上一台" : "上一首");
  $("#next").setAttribute("aria-label", s.live ? "下一台" : "下一首");
  $("#seek").disabled = !!s.noiseOnly || liveMusic;
  if (!dragging) {
    $("#seek").max = s.duration || 100;
    $("#seek").value = s.position || 0;
  }
  $("#elapsed").textContent = s.noiseOnly
    ? ""
    : liveMusic
      ? s.buffering
        ? "连接中"
        : s.playing
          ? "● LIVE"
          : "直播已暂停"
      : time(s.position);
  $("#duration").textContent = s.noiseOnly
    ? "连续循环"
    : liveMusic
      ? "网络电台"
      : time(s.duration);
  $("#fav").innerHTML = (
    liveMusic ? radioFavorites.includes(s.stationId) : favs.includes(s.uri)
  )
    ? icons.heartFill
    : icons.heart;
  $("#timer").textContent = s.sleepAt
    ? Math.max(1, Math.ceil((s.sleepAt - Date.now()) / 60000)) + "分"
    : "定时";
  $("#liveError").textContent = s.error || "";
  $("#liveError").style.display = s.error ? "block" : "none";
  if (s.error && s.error !== window.lastError) {
    toast(s.error);
    window.lastError = s.error;
  }
  if (!s.error) window.lastError = "";
}
function toggleFavorite() {
  if (state.noiseOnly) return;
  if (state.live) {
    favoriteStation(state.stationId);
    return;
  }
  const uri = state.uri;
  if (!uri) return;
  favs = favs.includes(uri) ? favs.filter((x) => x !== uri) : [...favs, uri];
  localStorage.setItem("favs", JSON.stringify(favs));
  $("#fav").innerHTML = favs.includes(uri) ? icons.heartFill : icons.heart;
}
function immerse() {
  document.body.classList.toggle("immersed");
  $("#exitImmersive").style.display = document.body.classList.contains(
    "immersed",
  )
    ? "block"
    : "none";
}
