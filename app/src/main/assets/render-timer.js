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
