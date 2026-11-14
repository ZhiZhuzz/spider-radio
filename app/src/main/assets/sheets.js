// One owner for sheet lifecycle. Feature renderers build their final page directly.
function sheetHeader() {
  return '<div class="handle"></div><button class="close" onclick="closeSheet()" aria-label="关闭">×</button>';
}
function openSheet(type) {
  if (type !== "library") {
    selectingSongs = false;
    selectionAdding = false;
    selectedSongs.clear();
  }
  const sheet = $("#sheet"),
    panel = $("#panel");
  sheet.dataset.type = type;
  sheet.classList.add("open");
  document.body.classList.add("panel-open");
  panel.classList.remove("library-selecting");
  panel.setAttribute("role", "dialog");
  panel.setAttribute("aria-modal", "true");
}
function show(type) {
  const render = sheetRenderers[type];
  if (!render) return;
  // Native deletion owns its pending page until confirmation/result arrives.
  if (deletionPending && type !== "library") return;
  openSheet(type);
  render();
  $("#panel").setAttribute(
    "aria-label",
    selectingSongs ? "选择歌曲" : $("#panel h2")?.textContent || "设置",
  );
}
function closeSheet() {
  if (deletionPending) return;
  selectingSongs = false;
  selectionAdding = false;
  selectedSongs.clear();
  $("#sheet").classList.remove("open");
  document.body.classList.remove("panel-open");
}
function handleBack() {
  if (selectingSongs && $("#sheet").classList.contains("open")) {
    if (!deletionPending) {
      if (selectionAdding) switchSelectionScope();
      else cancelSongSelection();
    }
    return true;
  }
  if (layoutPreview) {
    finishLayout();
    return true;
  }
  if ($("#sheet").classList.contains("open")) {
    closeSheet();
    return true;
  }
  if (document.body.classList.contains("immersed")) {
    immerse();
    return true;
  }
  return false;
}
function renderSettingsSheet() {
  $("#panel").innerHTML =
    sheetHeader() +
    `<h2>电台设置</h2>
    <button class="setting-link" onclick="showPresets()">我的搭配<small>保存此刻的画面与声音，一键回来</small></button>
    <button class="setting-link" onclick="showAppearance()">画面与布局<small>自定义背景、画面明暗、播放器位置</small></button>
    <button class="setting-link" onclick="showNoises()">自定义白噪音<small>导入自己的雨声、风声或环境录音</small></button>
    <p>蜘蛛电台 · 0.8.3 内测版</p>
    <label class="toggle">省电模式（关闭天气动画）<input type="checkbox" ${cfg.eco ? "checked" : ""} onchange="cfg.eco=this.checked;save();updateAnimation()"></label>
    <button class="pill" onclick="immerse();closeSheet()">进入 / 退出沉浸模式</button>
    <p>无需账户。直播会联网并消耗流量，电台服务方会接收到连接信息；本地歌曲与收藏保存在本机，卸载会清除。</p>
    <p>内置试听曲与白噪音为程序合成，插画由AI生成。直播来自第三方电台，节目及广告由其安排；场景名称不代表真实FM频率。</p>
    <div class="extras"><button onclick="show('timer')">睡眠定时</button></div>
    <div class="extras"><button onclick="showPlaybackHelp()">系统播放与兼容性</button><button onclick="if(window.Radio&&Radio.diagnostics)Radio.diagnostics()">播放诊断</button></div>`;
}
function renderLibrarySheet() {
  reconcileDeletedSongs();
  readLibrary();
  if (selectingSongs) {
    renderSongSelection();
    return;
  }
  const hasPlaylist =
    selectedList && Object.prototype.hasOwnProperty.call(lists, selectedList);
  let html =
    '<button class="pill" onclick="show(\'radio\')">← 直播电台</button>' +
    sheetHeader() +
    "<h2>本地音乐</h2><p>你的本地音乐，在这里成为夜间节目。</p>" +
    '<div class="library-actions"><button class="pill primary" onclick="Radio.importMusic()">＋ 导入音乐</button>' +
    '<button class="pill" onclick="filter=!filter;show(\'library\')">' +
    (filter ? "显示全部" : "只看收藏") +
    "</button>" +
    '<button class="library-select" onclick="startSongSelection()">选择</button></div>' +
    "<div" +
    (hasPlaylist ? ' class="playlist-picker"' : "") +
    '><select aria-label="歌单" onchange="selectedList=this.value;show(\'library\')" style="margin-top:12px;padding:8px;max-width:100%"><option value="">全部歌曲</option>' +
    Object.keys(lists)
      .map(
        (n) =>
          '<option value="' +
          esc(n) +
          '" ' +
          (selectedList === n ? "selected" : "") +
          ">" +
          esc(n) +
          "</option>",
      )
      .join("") +
    "</select>" +
    (hasPlaylist
      ? '<button class="playlist-delete" id="deletePlaylist" aria-label="删除歌单：' +
        esc(selectedList) +
        '">删除歌单</button>'
      : "") +
    '</div><div class="row"><input id="listname" placeholder="新歌单名称" style="width:55%;padding:8px"><button onclick="newList()">＋新建</button></div>';
  const visible = new Set(visibleSongs().map((s) => s.uri));
  let shown = 0;
  songs.forEach((s, i) => {
    if (visible.has(s.uri))
      html += `<div class="row song-row"><button onclick="pick(${i})">${String(++shown).padStart(2, "0")}　${esc(s.title)}</button><span>${favs.includes(s.uri) ? "♥" : ""}</span></div>`;
  });
  if (!shown)
    html +=
      '<p class="library-empty">' +
      (filter
        ? "这里还没有收藏的歌曲。"
        : selectedList
          ? "歌单还空着，点击「选择」添加歌曲。"
          : "曲库还空着，导入一首喜欢的音乐吧。") +
      "</p>";
  html +=
    "<p>支持设备可解码的普通音频文件；加密音乐缓存无法导入。导入后保留本机副本，原文件移动不影响播放。</p>";
  if (window.Radio?.deletedSongs) {
    try {
      if (JSON.parse(Radio.deletedSongs()).includes("demo.wav"))
        html +=
          '<button class="restore-demo" onclick="Radio.restoreBuiltIn()">恢复内置试听</button>';
    } catch (e) {
      /* The reconciliation helper already reports malformed records. */
    }
  }
  $("#panel").innerHTML = html;
  if (hasPlaylist)
    $("#deletePlaylist").onclick = () => {
      if (window.Radio?.confirmDeletePlaylist)
        Radio.confirmDeletePlaylist(selectedList);
      else toast("请在安卓安装包中管理歌单");
    };
}
const sheetRenderers = Object.freeze({
  channels: renderChannelsSheet,
  radio: renderRadioSheet,
  library: renderLibrarySheet,
  mixer: renderMixerSheet,
  timer: renderTimerSheet,
  settings: renderSettingsSheet,
});

function renderChannelsSheet() {
  let html = sheetHeader();
  html +=
    '<h2>选择一个夜晚</h2><p>画面改变，音乐继续。</p><div class="station-grid">' +
    stations
      .map(
        (s, i) =>
          `<button class="station-card ${ch === i ? "selected" : ""}" onclick="station(${i})" style="background-image:url(${s[3]}-v2.png)"><span>${s[0]}</span><small>0${i + 1} ${ch === i ? "· 当前场景" : ""}</small></button>`,
      )
      .join("") +
    "</div>";
  $("#panel").innerHTML = html;
}

function renderTimerSheet() {
  let html = sheetHeader();
  html += "<h2>睡眠定时</h2><p>结束前10秒淡出音乐和环境声。</p>";
  [15, 30, 60, 90].forEach(
    (n) =>
      (html += `<button class="pill" onclick="sleep(${n})">${n}分钟</button>`),
  );
  html +=
    '<div class="row"><input id="minutes" type="number" min="1" max="720" step="1" placeholder="自定义分钟" style="width:55%;padding:10px"><button class="pill" onclick="sleepCustom()">设定</button></div><button class="pill" onclick="sleep(0)">取消定时</button>';
  $("#panel").innerHTML = html;
}
