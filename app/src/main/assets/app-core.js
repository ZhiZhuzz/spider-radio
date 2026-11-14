// Shared preferences, commands, scene controls, library helpers and sleep acknowledgements.
function readStore(key, fallback) {
  try {
    const v = JSON.parse(localStorage.getItem(key));
    return v &&
      typeof v === typeof fallback &&
      Array.isArray(v) === Array.isArray(fallback)
      ? v
      : fallback;
  } catch (e) {
    return fallback;
  }
}
const icons = {
  play: '<svg viewBox="0 0 24 24"><path d="M9 5L20 12 9 19Z" fill="currentColor" stroke="none"/></svg>',
  pause:
    '<svg viewBox="0 0 24 24"><path d="M8 6v12M16 6v12" stroke-width="3"/></svg>',
  heart:
    '<svg viewBox="0 0 24 24"><path d="M12 20S3 14 3 8a5 5 0 0 1 9-3 5 5 0 0 1 9 3c0 6-9 12-9 12Z"/></svg>',
  heartFill:
    '<svg viewBox="0 0 24 24"><path d="M12 20S3 14 3 8a5 5 0 0 1 9-3 5 5 0 0 1 9 3c0 6-9 12-9 12Z" fill="currentColor"/></svg>',
};
const $ = (s) => document.querySelector(s),
  cfg = readStore("cfg", {}),
  stations = [
    [
      "山城午夜",
      "MIDNIGHT ON THE RIVER",
      "88.6",
      "city",
      "江风经过，灯火不眠。",
    ],
    [
      "北境来信",
      "LETTERS FROM THE NORTH",
      "92.3",
      "snow",
      "窗外是雪，耳边是你的世界。",
    ],
    [
      "末班列车",
      "THE LAST TRAIN HOME",
      "99.1",
      "train",
      "下一站之前，把时间留给自己。",
    ],
    [
      "云端夜雨",
      "RAIN ABOVE THE CITY",
      "104.8",
      "rain",
      "城市还醒着，你可以慢一点。",
    ],
  ];
let ch = Number.isInteger(cfg.ch) && cfg.ch >= 0 && cfg.ch < 4 ? cfg.ch : 3,
  songs = [],
  favs = readStore("favs", []),
  state = { playing: false, uri: "demo.wav" },
  filter = false,
  dragging = false,
  busy = false,
  selectedList = "",
  lists = readStore("lists", {});
function save() {
  localStorage.setItem("cfg", JSON.stringify(cfg));
}
function cmd(a, v = "") {
  if (window.Radio)
    Radio.command(a, typeof v === "object" ? JSON.stringify(v) : String(v));
  else toast("预览模式：音频功能请在安卓安装包中使用");
}
function toast(t) {
  $("#toast").textContent = t;
  $("#toast").style.display = "block";
  setTimeout(() => ($("#toast").style.display = "none"), 3000);
}
function station(i, animate = true) {
  apply(i);
  closeSheet();
}
function apply(i) {
  ch = i;
  cfg.ch = i;
  save();
  let s = stations[i];
  document
    .querySelectorAll(".world")
    .forEach((el, j) => el.classList.toggle("active", i === j));
  $("#title").textContent = s[0];
  $("#english").textContent = s[1];
  $("#quote").textContent = s[4];
  $("#channelLabel").textContent = s[0];
  if (!reduced.matches) {
    const h = document.querySelector(".hero");
    if (h.getAnimations) h.getAnimations().forEach((a) => a.cancel());
    if (h.animate)
      h.animate(
        [
          { opacity: 0.15, transform: "translateY(5px)" },
          { opacity: 1, transform: "translateY(0)" },
        ],
        { duration: 450, easing: "cubic-bezier(.22,1,.36,1)" },
      );
  }
  document.documentElement.style.setProperty(
    "--accent",
    ["#edb398", "#eed2a2", "#bed1b6", "#d4bddf"][i],
  );
  updateSceneCopy();
  if (window.Radio) cmd("channel", i);
}
function readLibrary() {
  try {
    songs = window.Radio
      ? JSON.parse(Radio.library())
      : [{ title: "蓝色时差", uri: "demo.wav", builtin: true }];
  } catch (e) {
    toast("曲库读取失败，请重试");
  }
}
function reloadLibrary() {
  if (window.reconcileDeletedSongs) reconcileDeletedSongs();
  readLibrary();
  if (
    $("#sheet").dataset.type === "library" &&
    $("#sheet").classList.contains("open")
  )
    show("library");
}
function esc(s) {
  return String(s).replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
}
function visibleSongs() {
  return songs.filter(
    (s) =>
      (!filter || favs.includes(s.uri)) &&
      (!selectedList || (lists[selectedList] || []).includes(s.uri)),
  );
}
function pick(i) {
  let q = visibleSongs(),
    n = q.findIndex((s) => s.uri === songs[i].uri);
  if (n < 0) {
    toast("这首歌曲已不在当前列表中");
    return;
  }
  cmd("play", { index: Math.max(0, n), queue: q });
  closeSheet();
}
function newList() {
  let n = document.querySelector("#listname").value.trim();
  if (!n) return;
  if (Object.prototype.hasOwnProperty.call(lists, n)) {
    toast("已有同名歌单");
    return;
  }
  const next = Object.assign(Object.create(null), lists);
  next[n] = [];
  try {
    localStorage.setItem("lists", JSON.stringify(next));
    lists = next;
  } catch (e) {
    toast("歌单未保存，请检查存储空间");
    return;
  }
  selectedList = n;
  show("library");
}
function setNoise(on) {
  cfg.noiseOnly = on;
  if (on && cfg.ambient === 0) cfg.ambient = 0.5;
  save();
  cmd("volumes", { music: cfg.music, ambient: cfg.ambient });
  cmd("noise", String(on));
  show("mixer");
}
function volume(k, v) {
  cfg[k] = v / 100;
  save();
  if ($("#v" + k)) $("#v" + k).textContent = v + "%";
  cmd("volumes", { music: cfg.music, ambient: cfg.ambient });
}
let sleepRequest = 0,
  sleepPending = "",
  sleepTimeout = 0,
  sleepMinutes = 0;
function sleep(n) {
  if (!Number.isInteger(n) || n < 0 || n > 720) {
    toast("请输入1至720的整数分钟");
    return;
  }
  if (!window.Radio || !Radio.setSleep) {
    toast("请在安卓安装包中设置定时");
    return;
  }
  const request = String(++sleepRequest);
  sleepPending = request;
  sleepMinutes = n;
  clearTimeout(sleepTimeout);
  sleepTimeout = setTimeout(() => {
    if (sleepPending === request) {
      sleepPending = "";
      toast("未收到定时确认，请检查后重试");
    }
  }, 5000);
  Radio.setSleep(String(n), request);
}
function sleepCustom() {
  const raw = document.querySelector("#minutes").value.trim();
  if (!/^[1-9][0-9]{0,2}$/.test(raw)) {
    toast("请输入1至720的整数分钟");
    return;
  }
  sleep(Number(raw));
}
function sleepResult(request, success, message) {
  if (request !== sleepPending) return;
  clearTimeout(sleepTimeout);
  sleepPending = "";
  if (!success) {
    toast(message || "定时未设置，请重试");
    return;
  }
  closeSheet();
  toast(sleepMinutes ? "将在" + sleepMinutes + "分钟后停止" : "已取消定时");
}
function time(ms) {
  let n = Math.floor((ms || 0) / 1000);
  return (
    String(Math.floor(n / 60)).padStart(2, "0") +
    ":" +
    String(n % 60).padStart(2, "0")
  );
}
