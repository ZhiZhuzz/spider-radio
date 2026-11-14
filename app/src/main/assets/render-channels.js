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
