function renderRadioSheet() {
  let html = sheetHeader();
  html +=
    '<h2>选择声音</h2><p>换一个频道，留在喜欢的夜晚。</p><div class="source-options"><button class="pill primary" onclick="show(\'radio\')">直播电台</button><button class="pill" onclick="show(\'library\')">本地音乐</button><button class="pill" onclick="setNoise(true)">纯白噪音</button></div>';
  if (state.live || state.noiseOnly)
    html +=
      '<button class="pill" onclick="cmd(\'local\');closeSheet()">继续上次的本地音乐</button>';
  html +=
    '<input class="radio-search" id="radioQuery" type="search" aria-label="搜索电台" placeholder="搜索电台或类型" value="' +
    esc(radioQuery) +
    '"><div class="radio-filters">' +
    ["全部", "收藏", "音乐", "资讯", "陪伴", "生活"]
      .map(
        (g) =>
          `<button class="pill ${radioFilter === g ? "primary" : ""}" onclick="radioFilter='${g}';show('radio')">${g}</button>`,
      )
      .join("") +
    '</div><div id="radioResults"></div><p class="quiet-note">网络直播会使用流量。节目以电台实际播出为准，暂停后再播放会回到直播。</p>';

  $("#panel").innerHTML = html;
  renderRadios();
  $("#radioQuery").oninput = (e) => {
    radioQuery = e.target.value;
    renderRadios();
  };
}
function renderMixerSheet() {
  let html = sheetHeader();
  const si = cfg.soundChoice < 0 ? ch : cfg.soundChoice;
  html +=
    '<h2>调音台</h2><p>声音自由搭配，画面保持不变。</p><div class="source-options"><button class="pill ' +
    (cfg.noiseOnly ? "primary" : "") +
    '" onclick="setNoise(true)">只听白噪音</button><button class="pill ' +
    (!cfg.noiseOnly ? "primary" : "") +
    '" onclick="setNoise(false)">返回' +
    (state.live ? "电台" : "音乐") +
    "</button></div>";
  html +=
    '<label for="soundChoice">白噪音</label><select id="soundChoice" class="sound-select" onchange="selectSound(this.value)">' +
    [[-1, "跟随场景"], ...soundNames.map((s, i) => [i, s])]
      .map(
        ([i, s]) =>
          `<option value="${i}" ${cfg.soundChoice === i ? "selected" : ""}>${s}</option>`,
      )
      .join("") +
    customNoises
      .map(
        (n) =>
          '<option value="custom:' +
          esc(n.id) +
          '" ' +
          (cfg.customSound === n.id ? "selected" : "") +
          ">" +
          esc(n.title) +
          "</option>",
      )
      .join("") +
    '</select><button class="pill" onclick="showNoises()">管理自定义白噪音</button><div class="sound-note">' +
    (customNoises.some((n) => n.id === cfg.customSound)
      ? esc(customNoises.find((n) => n.id === cfg.customSound).title) +
        " · 自定义声音，连续循环"
      : soundNames[si] + " · " + soundDetails[si]) +
    "</div>";
  if (state.live && !cfg.noiseOnly)
    html +=
      '<label class="toggle">直播时叠加白噪音<input id="liveNoise" type="checkbox" ' +
      (cfg.liveNoise ? "checked" : "") +
      " onchange=\"cfg.liveNoise=this.checked;save();cmd('liveNoise',this.checked);show('mixer')\"></label>";
  for (const [k, n] of [
    ["music", state.live ? "电台音量" : "音乐音量"],
    ["ambient", "白噪音音量"],
  ]) {
    const disabled =
      (k === "music" && cfg.noiseOnly) ||
      (k === "ambient" && state.live && !cfg.noiseOnly && !cfg.liveNoise);
    html += `<div class="label"><span>${n}${disabled ? " · 已关闭" : ""}</span><span id="v${k}">${Math.round(cfg[k] * 100)}%</span></div><input aria-label="${n}" type="range" min="0" max="100" value="${cfg[k] * 100}" ${disabled ? "disabled" : ""} oninput="volume('${k}',this.value)">`;
  }
  html +=
    '<button class="pill" onclick="resetMix()">恢复推荐音量</button><div class="extras"><button onclick="show(\'timer\')">睡眠定时</button><button onclick="show(\'settings\')">更多设置</button></div>';

  $("#panel").innerHTML = html;
}
function selectSound(value) {
  if (value.startsWith("custom:")) chooseCustomSound(value.slice(7));
  else chooseSound(Number(value));
}
