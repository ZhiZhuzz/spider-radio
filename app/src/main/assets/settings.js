const defaultBackgrounds = [...document.querySelectorAll(".world")].map(
  (el) => el.style.backgroundImage,
);
let customNoises = [],
  layoutPreview = false,
  currentBackgrounds = {};
function refreshBackgrounds() {
  try {
    const backgrounds = JSON.parse(
      window.Radio && Radio.backgrounds ? Radio.backgrounds() : "{}",
    );
    currentBackgrounds = backgrounds;
    document
      .querySelectorAll(".world")
      .forEach(
        (el, i) =>
          (el.style.backgroundImage = backgrounds[i]
            ? "url(" + JSON.stringify(backgrounds[i]) + ")"
            : defaultBackgrounds[i]),
      );
  } catch (e) {
    toast("背景设置读取失败，可重新选择图片");
  }
  applyAppearance();
}
function refreshNoises() {
  try {
    customNoises = JSON.parse(
      window.Radio && Radio.noises ? Radio.noises() : "[]",
    );
  } catch (e) {
    customNoises = [];
  }
  if ($("#sheet").classList.contains("open")) {
    if ($("#sheet").dataset.type === "noises") showNoises();
    else if ($("#sheet").dataset.type === "mixer") show("mixer");
  }
}
function updateSceneCopy() {
  const hidden = !!currentBackgrounds[ch];
  $(".hero").classList.toggle("custom-background", hidden);
  $(".hero").setAttribute("aria-hidden", String(hidden));
}
function applyAppearance() {
  updateSceneCopy();
  document.documentElement.style.setProperty("--background-dim", cfg.dim);
  positionPlayer();
  updateAnimation();
}
function positionPlayer() {
  const p = $(".player"),
    hero = $(".hero"),
    shell = $(".shell");
  if (!p || !hero) return;
  const landscape = innerWidth >= 700 && innerWidth > innerHeight;
  // Reserve the original title block even while it is hidden during live preview.
  const heroBottom = hero.offsetTop + hero.offsetHeight;
  const gap = landscape
    ? innerHeight - p.offsetHeight - 75
    : innerHeight - heroBottom - p.offsetHeight - 55;
  const max = Math.max(0, Math.min(innerHeight * 0.25, gap));
  document.documentElement.style.setProperty(
    "--player-lift",
    Math.round((max * cfg.lift) / 100) + "px",
  );
  // Small windows and large font settings must scroll instead of clipping controls.
  shell.style.minHeight =
    Math.max(
      innerHeight,
      landscape ? p.offsetHeight + 75 : heroBottom + p.offsetHeight + 55,
    ) + "px";
}
function chooseCustomSound(id) {
  if (!customNoises.some((n) => n.id === id)) return;
  cfg.customSound = id;
  save();
  cmd("customSound", id);
  show("mixer");
}
function sheetPage(type, title, body) {
  openSheet(type);
  $("#panel").innerHTML =
    `<div class="handle"></div><button class="close" onclick="closeSheet()" aria-label="关闭">×</button><h2>${title}</h2>${body}`;
  $("#panel").setAttribute("aria-label", title);
  $("#panel").scrollTop = 0;
}
function showAppearance() {
  sheetPage(
    "appearance",
    "画面与布局",
    `<p>${stations[ch][0]} · 图片仅保存在本机。</p><button class="pill primary" onclick="if(window.Radio)Radio.pickBackground(ch)">选择图片</button><button class="pill" onclick="if(window.Radio)Radio.clearBackground(ch)">恢复默认背景</button><div class="label"><span>背景压暗</span><span id="dimValue">${Math.round(cfg.dim * 100)}%</span></div><input type="range" aria-label="背景压暗" min="0" max="65" value="${cfg.dim * 100}" oninput="cfg.dim=Number(this.value)/100;save();applyAppearance();document.querySelector('#dimValue').textContent=this.value+'%' "><p>只调整背景明暗，文字和按钮保持清晰。</p><button class="setting-link" onclick="editLayout()">调整播放器位置<small>在主页面预览，整体向上移动；横竖屏分别按可用空间适配。</small></button><p>播放器位置为全局设置，不随搭配改变。大图会缩小处理，动态图片使用静态画面。</p><div class="extras"><button onclick="show('settings')">返回设置</button></div>`,
  );
}
function showBackgroundSettings() {
  showAppearance();
}
function editLayout() {
  closeSheet();
  layoutPreview = true;
  document.body.classList.add("layout-preview");
  const editor = document.createElement("section");
  editor.id = "layoutEditor";
  editor.innerHTML = `<div class="label"><span>播放器上移</span><span id="liftValue">${cfg.lift}%</span></div><input aria-label="播放器上移" type="range" min="0" max="100" value="${cfg.lift}" oninput="cfg.lift=Number(this.value);save();positionPlayer();document.querySelector('#liftValue').textContent=this.value+'%'"><p>位置会自动限制在屏幕内，设置即时保存。</p><button class="pill" onclick="cfg.lift=0;save();positionPlayer();document.querySelector('#layoutEditor input').value=0;document.querySelector('#liftValue').textContent='0%'">恢复默认位置</button><button class="pill primary" onclick="finishLayout()">完成</button>`;
  document.body.appendChild(editor);
  positionPlayer();
}
function finishLayout() {
  layoutPreview = false;
  $("#layoutEditor")?.remove();
  document.body.classList.remove("layout-preview");
  positionPlayer();
  showAppearance();
}
function showNoises() {
  sheetPage(
    "noises",
    "自定义白噪音",
    `<p>导入一段喜欢的环境声，在调音台里选择。可以单独听，也可以与音乐或电台一起播放。</p><button class="pill primary" onclick="if(window.Radio&&Radio.importNoise)Radio.importNoise()">＋ 导入白噪音</button>${customNoises.length ? customNoises.map((n, i) => `<div class="row"><button onclick="chooseCustomSound(customNoises[${i}].id)">${esc(n.title)}${cfg.customSound === n.id ? " · 已选择" : ""}</button><button aria-label="删除${esc(n.title)}" onclick="if(window.Radio)Radio.deleteNoise(customNoises[${i}].id)">删除</button></div>`).join("") : "<p>还没有自定义声音。现有的四种环境声仍可直接使用。</p>"}<p>支持设备可解码的普通音频，单个文件上限 200 MB；导入会占用本机存储空间。声音自动循环，首尾衔接取决于录音本身。</p><div class="extras"><button onclick="show('mixer')">返回调音台</button><button onclick="show('settings')">返回设置</button></div>`,
  );
}
function getPresets() {
  try {
    return JSON.parse(window.Radio && Radio.presets ? Radio.presets() : "[]");
  } catch (e) {
    return [];
  }
}
function showPresets() {
  const presets = getPresets();
  sheetPage(
    "presets",
    "我的搭配",
    `<p>保存场景、背景、音乐来源、白噪音和声音比例。使用时开始播放；播放器位置保持你的全局设置。</p><input id="presetName" class="v6-input" maxlength="32" aria-label="搭配名称" placeholder="给此刻取个名字"><button id="savePreset" class="pill primary" onclick="saveCurrentPreset()">保存当前搭配</button>${presets.map((p, i) => `<div class="preset-row"><div><strong>${esc(p.name)}</strong><small>${stations[p.scene]?.[0] || "场景"} · ${p.noiseOnly ? "纯白噪音" : p.live ? "直播电台" : "本地音乐"}${p.background ? " · 自定义背景" : ""}</small></div><button onclick="usePreset(${i})">使用并播放</button><button aria-label="删除搭配${esc(p.name)}" onclick="deleteSavedPreset(${i})">删除</button></div>`).join("") || "<p>还没有保存搭配。先调整喜欢的画面和声音，再回到这里保存。</p>"}<p>最多保存 20 个，仅保存在本机；背景保留独立副本。睡眠定时不写入搭配。</p><div class="extras"><button onclick="show('settings')">返回设置</button></div>`,
  );
}
function saveCurrentPreset() {
  const name = $("#presetName").value.trim();
  if (!name) {
    toast("先给搭配取个名字");
    $("#presetName").focus();
    return;
  }
  if (!window.Radio || !Radio.savePreset) {
    toast("请在安卓安装包中保存搭配");
    return;
  }
  Radio.savePreset(JSON.stringify({ name, scene: ch, dim: cfg.dim }));
  $("#savePreset").disabled = true;
  setTimeout(() => {
    if ($("#savePreset")) $("#savePreset").disabled = false;
  }, 1500);
}
function presetSaved() {
  showPresets();
  toast("搭配已保存");
}
function usePreset(i) {
  const p = getPresets()[i];
  if (p && window.Radio) Radio.applyPreset(p.id);
}
function deleteSavedPreset(i) {
  const p = getPresets()[i];
  if (p && window.Radio) Radio.deletePreset(p.id);
}
function applyPresetVisual(p) {
  cfg.dim = p.dim || 0;
  for (const k of [
    "customSound",
    "soundChoice",
    "liveNoise",
    "music",
    "ambient",
    "noiseOnly",
    "mode",
  ])
    if (p[k] !== undefined) cfg[k] = p[k];
  apply(p.scene);
  save();
  refreshBackgrounds();
  closeSheet();
  toast("已使用「" + p.name + "」");
}
function showPlaybackHelp() {
  let info = {};
  try {
    if (window.Radio?.systemPlaybackInfo)
      info = JSON.parse(Radio.systemPlaybackInfo());
  } catch (e) {}
  const device = info.device
    ? `<p class="quiet-note">${esc(info.device)} · Android ${esc(info.android)}<br>通知：${info.notifications && info.channelEnabled ? "已开启" : "请检查系统通知设置"} · 后台：${info.backgroundRestricted ? "受系统限制" : "未检测到应用后台限制"}</p>`
    : "";
  const surface = info.mediaFamily
    ? `<p><strong>${esc(info.mediaFamily)} · 系统媒体展示</strong><br>${esc(info.mediaGuidance || "显示效果以本机系统为准。")}</p><p class="quiet-note">播放后返回桌面，再分别检查锁屏与顶部动态区域。<br>岛 / 胶囊是否显示，需要在手机上确认。</p>`
    : "";
  sheetPage(
    "playbackHelp",
    "系统播放与兼容性",
    `${device}${surface}<p>播放后可在通知栏和锁屏查看媒体卡片。卡片外观由手机系统决定；本地歌曲支持进度控制，直播和白噪音不显示拖动进度。</p><div class="extras"><button onclick="window.Radio?.notificationSettings?.()">通知设置</button><button onclick="window.Radio?.applicationSettings?.()">应用设置</button><button onclick="window.Radio?.diagnostics?.()">播放诊断</button></div><p>锁屏看不到卡片时，先检查系统是否允许显示锁屏通知和媒体信息。Android 的媒体通知可能不受普通通知权限控制，以上通知状态不能单独判断卡片是否可用。</p><p>息屏或切换应用后，正在播放的声音会继续。若出现中断，请在系统的应用耗电或后台管理中检查蜘蛛电台；不同品牌和系统版本的设置名称可能不同。</p><p>耳机拔出时暂停；短暂被其他声音打断后尝试恢复。主动暂停后不会自行重播。直播切换网络后尝试重连，连续失败会停止重试。</p><p>诊断中可检测当前电台连接，并复制设备、系统、网络与播放日志，便于定位问题。不会上传这些信息。</p><p>系统强制停止或回收进程后，不保证继续播放。系统媒体卡片不等同于厂商专属灵动岛功能。</p><div class="extras"><button onclick="show('settings')">返回设置</button></div>`,
  );
}
