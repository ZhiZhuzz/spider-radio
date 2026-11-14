const modeLabels = {
  顺序: "顺序播放",
  列表: "列表循环",
  随机: "随机播放",
  单曲: "单曲循环",
};
const modePaths = {
  顺序: '<path d="M4 5h12M4 10h12M4 15h7M17 13v8m-3-3 3 3 3-3"/>',
  列表: '<path d="m17 2 4 4-4 4M3 11V8a2 2 0 0 1 2-2h16M7 22l-4-4 4-4m14-1v3a2 2 0 0 1-2 2H3"/>',
  随机: '<path d="m18 3 3 3-3 3M3 6h3c5 0 7 12 12 12h3m-3-3 3 3-3 3M3 18h3c2 0 4-3 6-6s4-6 6-6h3"/>',
  单曲: '<path d="m17 2 4 4-4 4M3 11V8a2 2 0 0 1 2-2h16M7 22l-4-4 4-4m14-1v3a2 2 0 0 1-2 2H3m7-8 2-1v6m-2 0h4"/>',
};
function modeLabel(mode) {
  return modeLabels[mode] || modeLabels["顺序"];
}
function updateHomeMode() {
  const b = $("#homeMode"),
    mode = modeLabels[cfg.mode] ? cfg.mode : "顺序";
  b.innerHTML =
    '<svg aria-hidden="true" viewBox="0 0 24 24">' +
    modePaths[mode] +
    "</svg><span>" +
    modeLabel(mode) +
    "</span>";
  b.setAttribute("aria-label", "播放模式：" + modeLabel(mode) + "，点击切换");
  b.title = modeLabel(mode);
  b.hidden = !!(state.live || cfg.noiseOnly);
}
function deletePlaylistConfirmed(name) {
  if (!name || !Object.prototype.hasOwnProperty.call(lists, name)) return;
  const next = Object.assign(Object.create(null), lists);
  delete next[name];
  try {
    localStorage.setItem("lists", JSON.stringify(next));
  } catch (e) {
    toast("歌单未删除，请重试");
    return;
  }
  lists = next;
  if (selectedList === name) selectedList = "";
  show("library");
  toast("歌单已删除，歌曲仍保留");
}
function reconcileDeletedSongs() {
  if (!window.Radio || !Radio.deletedSongs) return;
  try {
    const removed = new Set(JSON.parse(Radio.deletedSongs()));
    favs = favs.filter((u) => !removed.has(u));
    for (const n of Object.keys(lists))
      lists[n] = lists[n].filter((u) => !removed.has(u));
    localStorage.setItem("favs", JSON.stringify(favs));
    localStorage.setItem("lists", JSON.stringify(lists));
  } catch (e) {
    toast("曲库记录读取失败，请重新打开应用");
  }
}
let selectingSongs = false,
  selectedSongs = new Set(),
  deletionPending = false,
  selectionAdding = false;
function selectableSongs() {
  return selectionAdding
    ? songs.filter((s) => !(lists[selectedList] || []).includes(s.uri))
    : visibleSongs();
}
function sizeLabel(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return "大小未知";
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + " MB";
  return Math.max(1, Math.round(bytes / 1024)) + " KB";
}
function startSongSelection() {
  selectingSongs = true;
  selectionAdding = false;
  selectedSongs.clear();
  renderSongSelection();
}
function cancelSongSelection() {
  if (deletionPending) return;
  selectingSongs = false;
  selectionAdding = false;
  selectedSongs.clear();
  show("library");
}
function switchSelectionScope() {
  if (deletionPending) return;
  selectionAdding = !selectionAdding;
  selectedSongs.clear();
  renderSongSelection();
}
function persistPlaylistChange(name, adding) {
  if (
    deletionPending ||
    !selectedSongs.size ||
    !Object.prototype.hasOwnProperty.call(lists, name)
  )
    return;
  const valid = new Set(selectableSongs().map((s) => s.uri)),
    chosen = [...selectedSongs].filter((u) => valid.has(u)),
    old = lists[name];
  const changed = chosen.filter((u) =>
    adding ? !old.includes(u) : old.includes(u),
  );
  if (!changed.length) {
    toast(adding ? "所选歌曲已在该歌单中" : "所选歌曲不在该歌单中");
    return;
  }
  const next = Object.assign(Object.create(null), lists);
  next[name] = adding
    ? [...new Set([...old, ...changed])]
    : old.filter((u) => !changed.includes(u));
  try {
    localStorage.setItem("lists", JSON.stringify(next));
  } catch (e) {
    toast("歌单未保存，请重试");
    return;
  }
  lists = next;
  selectedSongs.clear();
  renderSongSelection();
  toast("已" + (adding ? "加入" : "移出") + " " + changed.length + " 首");
}
function showPlaylistTarget() {
  const names = Object.keys(lists);
  if (!names.length) {
    toast("请先返回曲库新建歌单");
    return;
  }
  const area = $("#playlistTarget");
  area.hidden = false;
  area.innerHTML =
    '<label for="targetPlaylist">加入歌单</label><select id="targetPlaylist" aria-label="目标歌单">' +
    names
      .map((n) => '<option value="' + esc(n) + '">' + esc(n) + "</option>")
      .join("") +
    '</select><button id="confirmPlaylistTarget">加入</button><button id="cancelPlaylistTarget">取消</button>';
  $("#confirmPlaylistTarget").onclick = () =>
    persistPlaylistChange($("#targetPlaylist").value, true);
  $("#cancelPlaylistTarget").onclick = () => {
    area.hidden = true;
    area.innerHTML = "";
  };
  $("#targetPlaylist").focus();
}
function syncSelectionControls() {
  const n = selectedSongs.size;
  $("#selectedCount").textContent = "已选 " + n + " 首";
  document
    .querySelectorAll(".selection-action")
    .forEach((b) => (b.disabled = n === 0 || deletionPending));
  $("#selectAllSongs").disabled = deletionPending || !selectableSongs().length;
  $("#selectAllSongs").textContent =
    n === selectableSongs().length && n > 0 ? "取消全选" : "全选";
  $("#cancelSelection").disabled = deletionPending;
  if ($("#selectionScope")) $("#selectionScope").disabled = deletionPending;
  if ($("#deleteSelected"))
    $("#deleteSelected").textContent = deletionPending
      ? "正在处理…"
      : "删除歌曲";
  if ($("#playlistTarget")) {
    $("#playlistTarget").hidden = true;
    $("#playlistTarget").innerHTML = "";
  }
}
function renderSongSelection() {
  const rows = selectableSongs(),
    valid = new Set(rows.map((s) => s.uri));
  selectedSongs = new Set([...selectedSongs].filter((u) => valid.has(u)));
  const panel = $("#panel");
  panel.classList.add("library-selecting");
  panel.setAttribute("aria-label", "选择歌曲");
  const scope = selectionAdding
    ? "曲库中未加入的歌曲"
    : (selectedList ? "当前歌单" : "全部歌曲") +
      (filter ? " · 收藏" : "") +
      " · " +
      rows.length +
      " 首";
  panel.innerHTML =
    '<div class="handle"></div><div class="selection-heading"><h2>选择歌曲</h2><button id="cancelSelection" onclick="cancelSongSelection()">完成</button></div>' +
    (selectedList
      ? '<div class="selection-context"><span>' +
        esc(selectedList) +
        '</span><button id="selectionScope" onclick="switchSelectionScope()">' +
        (selectionAdding ? "返回歌单" : "添加歌曲") +
        "</button></div>"
      : "") +
    '<div class="selection-scope"><span>' +
    esc(scope) +
    '</span><button id="selectAllSongs">全选</button></div><div class="selection-list">' +
    rows
      .map(
        (s, i) =>
          `<label class="song-select-row"><input type="checkbox" aria-label="选择歌曲：${esc(s.title)}" data-index="${i}" ${selectedSongs.has(s.uri) ? "checked" : ""} ${deletionPending ? "disabled" : ""}><span>${esc(s.title)}<small>${s.builtin ? "内置试听 · " : ""}${sizeLabel(s.size)}</small></span></label>`,
      )
      .join("") +
    (rows.length
      ? ""
      : "<p>" +
        (selectionAdding
          ? "曲库里的歌曲都已加入此歌单。"
          : selectedList
            ? "当前列表没有歌曲，可点击「添加歌曲」。"
            : "当前列表没有歌曲。") +
        "</p>") +
    '</div><div class="selection-footer"><span id="selectedCount" aria-live="polite"></span><div id="playlistTarget" hidden></div><div class="selection-buttons">' +
    (selectionAdding
      ? '<button class="pill primary selection-action" id="addCurrent">加入当前歌单</button>'
      : '<button class="selection-action" id="addSelected">加入歌单</button>' +
        (selectedList
          ? '<button class="selection-action" id="removeSelected">移出歌单</button>'
          : "") +
        '<button class="selection-action delete-action" id="deleteSelected">删除歌曲</button>') +
    "</div></div>";
  panel.querySelectorAll("input[type=checkbox]").forEach(
    (box) =>
      (box.onchange = () => {
        const uri = rows[Number(box.dataset.index)].uri;
        if (box.checked) selectedSongs.add(uri);
        else selectedSongs.delete(uri);
        syncSelectionControls();
      }),
  );
  $("#selectAllSongs").onclick = () => {
    if (selectedSongs.size === rows.length) selectedSongs.clear();
    else rows.forEach((s) => selectedSongs.add(s.uri));
    panel
      .querySelectorAll("input[type=checkbox]")
      .forEach(
        (b) =>
          (b.checked = selectedSongs.has(rows[Number(b.dataset.index)].uri)),
      );
    syncSelectionControls();
  };
  if ($("#addCurrent"))
    $("#addCurrent").onclick = () => persistPlaylistChange(selectedList, true);
  if ($("#addSelected")) $("#addSelected").onclick = showPlaylistTarget;
  if ($("#removeSelected"))
    $("#removeSelected").onclick = () =>
      persistPlaylistChange(selectedList, false);
  if ($("#deleteSelected"))
    $("#deleteSelected").onclick = () => {
      if (!selectedSongs.size || deletionPending) return;
      if (!window.Radio || !Radio.deleteSongs) {
        toast("请在安卓安装包中管理歌曲");
        return;
      }
      deletionPending = true;
      syncSelectionControls();
      panel.querySelectorAll("input").forEach((b) => (b.disabled = true));
      Radio.deleteSongs(JSON.stringify([...selectedSongs]));
    };
  syncSelectionControls();
}
function songDeletionFinished(success) {
  deletionPending = false;
  if (success) {
    selectedSongs.clear();
    reconcileDeletedSongs();
    reloadLibrary();
  } else if (selectingSongs && $("#sheet").classList.contains("open"))
    renderSongSelection();
}
