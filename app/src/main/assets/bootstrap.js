// All feature definitions are loaded before events and native commands are enabled.
cfg.music ??= 0.75;
cfg.ambient ??= 0.25;
cfg.mode ??= "顺序";
cfg.soundChoice ??= -1;
cfg.liveNoise ??= false;
cfg.customSound ||= "";
cfg.dim = Math.max(0, Math.min(0.65, Number(cfg.dim) || 0));
cfg.lift = Math.max(0, Math.min(100, Number(cfg.lift) || 0));
$("#play").onclick = () => cmd("toggle");
$("#prev").onclick = () => cmd("prev");
$("#next").onclick = () => cmd("next");
$("#timer").onclick = () => show("timer");
$("#homeMode").onclick = () => {
  const modes = Object.keys(modeLabels);
  cfg.mode = modes[(modes.indexOf(cfg.mode) + 1) % modes.length];
  save();
  cmd("mode", cfg.mode);
  updateHomeMode();
  toast("已切换为" + modeLabel(cfg.mode));
};
$("#seek").oninput = () => (dragging = true);
$("#seek").onchange = () => {
  cmd("seek", $("#seek").value);
  dragging = false;
};
$("#fav").onclick = toggleFavorite;
$("#immersive").onclick = immerse;
document
  .querySelectorAll("[data-sheet]")
  .forEach(
    (b) =>
      (b.onclick = () =>
        show(b.dataset.sheet === "library" ? "radio" : b.dataset.sheet)),
  );
$("#sheet").onclick = (e) => {
  if (e.target === $("#sheet")) closeSheet();
};
addEventListener("resize", positionPlayer);
if (document.fonts) document.fonts.ready.then(positionPlayer);
if (window.ResizeObserver)
  new ResizeObserver(positionPlayer).observe($(".player"));
addEventListener("keydown", (e) => {
  if (e.key === "Escape") handleBack();
  if (e.key !== "Tab" || !$("#sheet").classList.contains("open")) return;
  const items = [...$("#panel").querySelectorAll("button,input,select")].filter(
    (el) => !el.disabled && el.offsetParent !== null,
  );
  const first = items[0],
    last = items[items.length - 1];
  if (e.shiftKey && document.activeElement === first) {
    last?.focus();
    e.preventDefault();
  } else if (!e.shiftKey && document.activeElement === last) {
    first?.focus();
    e.preventDefault();
  }
});
reconcileDeletedSongs();
readLibrary();
refreshNoises();
refreshBackgrounds();
station(ch, false);
updateHomeMode();
