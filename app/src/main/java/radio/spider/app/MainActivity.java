package radio.spider.app;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.webkit.*;
import java.io.*;
import org.json.*;

public class MainActivity extends Activity {
  int backgroundScene;
  final java.util.concurrent.ExecutorService imports =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  final java.util.concurrent.ExecutorService probes =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  boolean probeRunning = false;
  String probeResult = "";
  WebView web;
  final Handler h = new Handler();
  boolean visible;
  Runnable ticker =
      new Runnable() {
        public void run() {
          if (visible && web != null) {
            js("window.nativeState&&nativeState(" + RadioService.state(MainActivity.this) + ")");
            h.postDelayed(this, 700);
          }
        }
      };

  android.content.SharedPreferences prefs() {
    return getPreferences(0);
  }

  JSONArray list(String key) {
    try {
      return new JSONArray(prefs().getString(key, "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  void js(String code) {
    if (!isDestroyed() && web != null) web.evaluateJavascript(code, null);
  }

  void message(String text) {
    runOnUiThread(
        () -> {
          if (!isDestroyed()) android.widget.Toast.makeText(this, text, 1).show();
        });
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    if (b != null) backgroundScene = b.getInt("backgroundScene", 3);
    getWindow().setStatusBarColor(0xff071124);
    getWindow().setNavigationBarColor(0xff071124);
    web = new WebView(this);
    web.setBackgroundColor(0xff071124);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(true);
    web.getSettings().setAllowContentAccess(false);
    web.setWebViewClient(
        new WebViewClient() {
          public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            return true;
          }

          public void onPageFinished(WebView v, String u) {
            js("window.nativeVisibility&&nativeVisibility(" + visible + ")");
          }
        });
    web.addJavascriptInterface(new Bridge(), "Radio");
    setContentView(web);
    if (Build.VERSION.SDK_INT >= 35)
      web.setOnApplyWindowInsetsListener(
          (v, insets) -> {
            android.graphics.Insets bars =
                insets.getInsets(
                    android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
          });
    web.loadUrl("file:///android_asset/index.html");
  }

  protected void onSaveInstanceState(Bundle b) {
    b.putInt("backgroundScene", backgroundScene);
    super.onSaveInstanceState(b);
  }

  public void onResume() {
    super.onResume();
    visible = true;
    web.onResume();
    js("window.nativeVisibility&&nativeVisibility(true)");
    h.removeCallbacks(ticker);
    h.post(ticker);
  }

  public void onPause() {
    visible = false;
    h.removeCallbacks(ticker);
    js("window.nativeVisibility&&nativeVisibility(false)");
    web.onPause();
    super.onPause();
  }

  public void onBackPressed() {
    web.evaluateJavascript(
        "window.handleBack?handleBack():false",
        r -> {
          if (!"true".equals(r)) moveTaskToBack(true);
        });
  }

  public void onDestroy() {
    visible = false;
    h.removeCallbacksAndMessages(null);
    imports.shutdown();
    probes.shutdownNow();
    web.removeJavascriptInterface("Radio");
    web.destroy();
    web = null;
    super.onDestroy();
  }

  void pick(String type, int req, boolean multi) {
    try {
      startActivityForResult(
          new Intent(Intent.ACTION_OPEN_DOCUMENT)
              .setType(type)
              .addCategory(Intent.CATEGORY_OPENABLE)
              .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi)
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
          req);
    } catch (ActivityNotFoundException e) {
      message("未找到系统文件选择器，请启用手机的文件管理应用");
    }
  }

  String diagnosticText() {
    return RadioService.diagnostics(this)
        + (probeResult.isEmpty() ? "" : "\n连接检测：\n" + probeResult);
  }

  void showDiagnostics() {
    final AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("播放诊断")
            .setMessage(diagnosticText())
            .setPositiveButton(
                "复制",
                (d, w) ->
                    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("播放诊断", diagnosticText())))
            .setNeutralButton("检测当前电台", null)
            .setNegativeButton("关闭", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(
                    v -> {
                      RadioService service = RadioService.self;
                      if (service == null || !service.live || service.noiseOnly) {
                        message("请先切换到要检测的直播电台");
                        return;
                      }
                      if (probeRunning) {
                        message("连接检测正在进行");
                        return;
                      }
                      JSONObject station = service.station();
                      JSONArray urls = station == null ? null : station.optJSONArray("urls");
                      if (urls == null || urls.length() == 0) return;
                      String url = urls.optString(service.retries % urls.length()),
                          name = station.optString("name");
                      probeRunning = true;
                      dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                      dialog.setMessage(diagnosticText() + "\n正在检测 " + name + "…");
                      probes.execute(
                          () -> {
                            String result = name + "\n" + StreamProbe.inspect(url);
                            runOnUiThread(
                                () -> {
                                  probeRunning = false;
                                  probeResult = result;
                                  if (!isDestroyed() && dialog.isShowing()) {
                                    dialog.setMessage(diagnosticText());
                                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                                  }
                                });
                          });
                    }));
    dialog.show();
  }

  void openSystemSettings(boolean notification) {
    try {
      Intent i =
          notification
              ? new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                  .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())
              : new Intent(
                  android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  Uri.parse("package:" + getPackageName()));
      startActivity(i);
    } catch (ActivityNotFoundException e) {
      message("请在系统设置中查找蜘蛛电台的通知和电池设置");
    }
  }

  void send(String a, String v) {
    Intent i = new Intent(this, RadioService.class).setAction(a).putExtra("value", v);
    if (a.equals("play")
        || a.equals("toggle")
        || a.equals("noise")
        || a.equals("radio")
        || a.equals("local")
        || a.equals("preset")) startForegroundService(i);
    else startService(i);
  }

  class Bridge {
    @JavascriptInterface
    public String backgrounds() {
      JSONObject o = new JSONObject();
      try {
        for (int n = 0; n < 4; n++) {
          File f = new File(getFilesDir(), "background-" + n + ".jpg");
          if (f.exists()) o.put("" + n, Uri.fromFile(f).toString() + "?v=" + f.lastModified());
        }
      } catch (Exception ignored) {
      }
      return o.toString();
    }

    @JavascriptInterface
    public void pickBackground(int scene) {
      runOnUiThread(
          () -> {
            backgroundScene = Math.max(0, Math.min(3, scene));
            pick("image/*", 8, false);
          });
    }

    @JavascriptInterface
    public void clearBackground(int scene) {
      imports.execute(
          () -> {
            new File(getFilesDir(), "background-" + Math.max(0, Math.min(3, scene)) + ".jpg")
                .delete();
            runOnUiThread(
                () -> {
                  js("refreshBackgrounds()");
                  if (RadioService.self != null) RadioService.self.refreshArtwork();
                });
          });
    }

    @JavascriptInterface
    public void diagnostics() {
      runOnUiThread(() -> showDiagnostics());
    }

    @JavascriptInterface
    public String systemPlaybackInfo() {
      return SystemPlaybackInfo.read(MainActivity.this).toString();
    }

    @JavascriptInterface
    public void notificationSettings() {
      runOnUiThread(() -> openSystemSettings(true));
    }

    @JavascriptInterface
    public void applicationSettings() {
      runOnUiThread(() -> openSystemSettings(false));
    }

    @JavascriptInterface
    public String library() {
      JSONArray entries = list("songs");
      for (int n = 0; n < entries.length(); n++) {
        JSONObject song = entries.optJSONObject(n);
        if (song == null) continue;
        try {
          File f = new File(Uri.parse(song.optString("uri")).getPath());
          if (f.getCanonicalFile()
                  .getParentFile()
                  .equals(new File(getFilesDir(), "music").getCanonicalFile())
              && f.exists()) song.put("size", f.length());
        } catch (Exception ignored) {
        }
      }
      try (android.content.res.AssetFileDescriptor demo = getAssets().openFd("demo.wav")) {
        return LibraryCatalog.available(entries, list("deletedSongs"), demo.getLength()).toString();
      } catch (Exception e) {
        return entries.toString();
      }
    }

    @JavascriptInterface
    public String noises() {
      return prefs().getString("noises", "[]");
    }

    @JavascriptInterface
    public String presets() {
      return prefs().getString("presets", "[]");
    }

    @JavascriptInterface
    public void command(String action, String value) {
      runOnUiThread(
          () -> {
            send(action, value);
            if ((action.equals("play")
                    || action.equals("toggle")
                    || action.equals("radio")
                    || action.equals("noise"))
                && Build.VERSION.SDK_INT >= 33
                && !prefs().getBoolean("notificationAsked", false)) {
              prefs().edit().putBoolean("notificationAsked", true).apply();
              if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0)
                requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 9);
            }
          });
    }

    @JavascriptInterface
    public void setSleep(String minutes, String request) {
      runOnUiThread(
          () -> {
            boolean ok = false;
            String message = "";
            try {
              if (RadioService.self == null) throw new IllegalStateException();
              RadioService.self.setSleep(minutes);
              ok = true;
            } catch (IllegalArgumentException e) {
              message = "请输入1至720的整数分钟";
            } catch (Exception e) {
              message = "定时未设置，请稍后重试";
            }
            js(
                "window.sleepResult&&sleepResult("
                    + JSONObject.quote(request)
                    + ","
                    + ok
                    + ","
                    + JSONObject.quote(message)
                    + ")");
          });
    }

    @JavascriptInterface
    public void importMusic() {
      runOnUiThread(() -> pick("audio/*", 7, true));
    }

    @JavascriptInterface
    public void importNoise() {
      runOnUiThread(() -> pick("audio/*", 10, true));
    }

    @JavascriptInterface
    public String deletedSongs() {
      return prefs().getString("deletedSongs", "[]");
    }

    @JavascriptInterface
    public void confirmDeletePlaylist(String name) {
      runOnUiThread(
          () ->
              new AlertDialog.Builder(MainActivity.this)
                  .setTitle("删除歌单「" + name + "」？")
                  .setMessage("歌曲仍保留在曲库和其他歌单中，当前播放也会继续。此操作无法撤销。")
                  .setNegativeButton("取消", null)
                  .setPositiveButton(
                      "删除歌单",
                      (d, w) -> js("deletePlaylistConfirmed(" + JSONObject.quote(name) + ")"))
                  .show());
    }

    @JavascriptInterface
    public void restoreBuiltIn() {
      imports.execute(
          () -> {
            try {
              JSONArray kept = new JSONArray(), old = list("deletedSongs");
              for (int i = 0; i < old.length(); i++)
                if (!LibraryCatalog.DEMO.equals(old.optString(i))) kept.put(old.get(i));
              if (!prefs().edit().putString("deletedSongs", kept.toString()).commit())
                throw new IOException();
              runOnUiThread(
                  () -> {
                    js("reloadLibrary()");
                    message("内置试听已恢复，原歌单和收藏不会自动恢复");
                  });
            } catch (Exception e) {
              message("恢复失败，请重试");
            }
          });
    }

    boolean deletingSongs;

    @JavascriptInterface
    public void deleteSongs(String json) {
      runOnUiThread(
          () -> {
            if (deletingSongs) {
              js("songDeletionFinished(false)");
              return;
            }
            final java.util.Set<String> requested = new java.util.HashSet<>();
            try {
              JSONArray values = new JSONArray(json);
              for (int n = 0; n < values.length(); n++) requested.add(values.getString(n));
            } catch (Exception e) {
              message("未能读取所选歌曲");
              js("songDeletionFinished(false)");
              return;
            }
            final java.util.Set<String> targets;
            try {
              targets = LibraryCatalog.targets(new JSONArray(library()), requested);
            } catch (Exception e) {
              message("曲库读取失败，请重试");
              js("songDeletionFinished(false)");
              return;
            }
            if (targets.isEmpty()) {
              message("所选歌曲已不存在");
              js("songDeletionFinished(true)");
              return;
            }
            deletingSongs = true;
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("删除所选的 " + targets.size() + " 首歌曲？")
                .setMessage(
                    (targets.contains(LibraryCatalog.DEMO)
                            ? "内置试听将从曲库移除，可在曲库底部恢复。安装包内资源会保留。\n\n"
                            : "")
                        + "导入歌曲只清理应用内副本，手机原文件会保留。\n\n"
                        + "歌曲会同时移出收藏、歌单和搭配；没有剩余音乐的本地搭配也会删除。若包含正在播放的歌曲，将暂停播放。")
                .setNegativeButton(
                    "取消",
                    (d, w) -> {
                      deletingSongs = false;
                      js("songDeletionFinished(false)");
                    })
                .setOnCancelListener(
                    d -> {
                      deletingSongs = false;
                      js("songDeletionFinished(false)");
                    })
                .setPositiveButton(
                    "删除",
                    (d, w) ->
                        imports.execute(
                            () -> {
                              try {
                                JSONArray old = list("songs"), kept = new JSONArray();
                                java.util.Set<String> removed = new java.util.HashSet<>(targets);
                                for (int n = 0; n < old.length(); n++) {
                                  JSONObject song = old.getJSONObject(n);
                                  String uri = song.optString("uri");
                                  if (targets.contains(uri)) removed.add(uri);
                                  else kept.put(song);
                                }
                                JSONArray presets = list("presets"), updated = new JSONArray();
                                java.util.ArrayList<File> backgrounds = new java.util.ArrayList<>();
                                for (int n = 0; n < presets.length(); n++) {
                                  JSONObject preset = presets.getJSONObject(n);
                                  JSONArray q = preset.optJSONArray("queue");
                                  if (q == null) {
                                    updated.put(preset);
                                    continue;
                                  }
                                  QueueRemoval edit =
                                      new QueueRemoval(q, preset.optInt("index"), removed);
                                  if (edit.removesPreset(
                                      preset.optBoolean("live"), preset.optBoolean("noiseOnly"))) {
                                    String id = preset.optString("id");
                                    if (id.matches("[a-f0-9-]{36}"))
                                      backgrounds.add(
                                          new File(getFilesDir(), "presets/" + id + ".jpg"));
                                    continue;
                                  }
                                  preset.put("queue", edit.queue).put("index", edit.index);
                                  updated.put(preset);
                                }
                                JSONArray deleted = list("deletedSongs");
                                java.util.Set<String> known = new java.util.HashSet<>();
                                for (int n = 0; n < deleted.length(); n++)
                                  known.add(deleted.optString(n));
                                for (String uri : removed) if (known.add(uri)) deleted.put(uri);
                                if (!prefs()
                                    .edit()
                                    .putString("songs", kept.toString())
                                    .putString("presets", updated.toString())
                                    .putString("deletedSongs", deleted.toString())
                                    .commit()) throw new IOException();
                                boolean cleanupFailed = false;
                                for (String uri : removed) {
                                  try {
                                    if (uri.startsWith("file:")) {
                                      File f = new File(Uri.parse(uri).getPath());
                                      if (f.getCanonicalFile()
                                              .getParentFile()
                                              .equals(
                                                  new File(getFilesDir(), "music")
                                                      .getCanonicalFile())
                                          && f.exists()
                                          && !f.delete()) cleanupFailed = true;
                                    }
                                  } catch (Exception e) {
                                    cleanupFailed = true;
                                  }
                                }
                                for (File f : backgrounds) f.delete();
                                final boolean failedCleanup = cleanupFailed;
                                runOnUiThread(
                                    () -> {
                                      if (RadioService.self != null)
                                        RadioService.self.removeSongs(removed);
                                      deletingSongs = false;
                                      js("songDeletionFinished(true)");
                                      message(
                                          failedCleanup
                                              ? "歌曲已移除，部分副本未能清理"
                                              : "已删除 " + removed.size() + " 首歌曲，手机原文件已保留");
                                    });
                              } catch (Exception e) {
                                runOnUiThread(
                                    () -> {
                                      deletingSongs = false;
                                      message("删除未完成，请重试");
                                      js("songDeletionFinished(false)");
                                    });
                              }
                            }))
                .show();
          });
    }

    @JavascriptInterface
    public void deleteNoise(String id) {
      runOnUiThread(
          () ->
              new AlertDialog.Builder(MainActivity.this)
                  .setTitle("删除这段白噪音？")
                  .setMessage("只删除应用中的副本，不会删除原文件。使用它的搭配需要先删除或重新保存。")
                  .setNegativeButton("取消", null)
                  .setPositiveButton(
                      "删除",
                      (d, w) ->
                          imports.execute(
                              () -> {
                                JSONArray presets = list("presets");
                                for (int i = 0; i < presets.length(); i++) {
                                  JSONObject p = presets.optJSONObject(i);
                                  if (p != null && id.equals(p.optString("customSound"))) {
                                    message("这段声音仍被「" + p.optString("name") + "」使用，请先处理该搭配");
                                    return;
                                  }
                                }
                                JSONArray old = list("noises"), kept = new JSONArray();
                                File target = null;
                                for (int i = 0; i < old.length(); i++) {
                                  JSONObject p = old.optJSONObject(i);
                                  if (p == null) continue;
                                  if (id.equals(p.optString("id")))
                                    target = new File(Uri.parse(p.optString("uri")).getPath());
                                  else kept.put(p);
                                }
                                if (!prefs().edit().putString("noises", kept.toString()).commit()) {
                                  message("删除未完成，请重试");
                                  return;
                                }
                                final File file = target;
                                runOnUiThread(
                                    () -> {
                                      send("removeNoise", id);
                                      js("refreshNoises()");
                                    });
                                if (file != null
                                    && file.getParentFile()
                                        .equals(new File(getFilesDir(), "noises"))) file.delete();
                              }))
                  .show());
    }

    @JavascriptInterface
    public void savePreset(String visual) {
      runOnUiThread(
          () -> {
            final JSONObject audio = RadioService.preset(MainActivity.this);
            imports.execute(
                () -> {
                  File snapshot = null;
                  try {
                    JSONArray presets = list("presets");
                    if (presets.length() >= 20) {
                      message("最多保存 20 个搭配，请先删除不再使用的搭配");
                      return;
                    }
                    JSONObject v = new JSONObject(visual);
                    String name = v.optString("name").trim();
                    if (name.isEmpty() || name.length() > 32) throw new IOException();
                    int scene = Math.max(0, Math.min(3, v.optInt("scene", 3)));
                    String id = java.util.UUID.randomUUID().toString();
                    audio
                        .put("id", id)
                        .put("name", name)
                        .put("scene", scene)
                        .put("dim", Math.max(0, Math.min(.65, v.optDouble("dim", 0))));
                    File background = new File(getFilesDir(), "background-" + scene + ".jpg");
                    if (background.exists()) {
                      File dir = new File(getFilesDir(), "presets");
                      dir.mkdirs();
                      snapshot = new File(dir, id + ".jpg");
                      copy(new FileInputStream(background), snapshot, 20L * 1024 * 1024);
                      audio.put("background", snapshot.getAbsolutePath());
                    }
                    presets.put(audio);
                    if (!prefs().edit().putString("presets", presets.toString()).commit())
                      throw new IOException();
                    runOnUiThread(() -> js("presetSaved()"));
                  } catch (Exception e) {
                    if (snapshot != null) snapshot.delete();
                    message("搭配未保存，请检查名称和剩余存储空间");
                  }
                });
          });
    }

    @JavascriptInterface
    public void applyPreset(String id) {
      imports.execute(
          () -> {
            try {
              JSONObject p = find(list("presets"), id);
              if (p == null) throw new IOException();
              String sound = p.optString("customSound");
              if (!sound.isEmpty() && find(list("noises"), sound) == null) {
                message("搭配中的白噪音已丢失，请重新导入并保存搭配");
                return;
              }
              JSONArray queue = p.optJSONArray("queue");
              if (!p.optBoolean("live") && !p.optBoolean("noiseOnly") && queue != null)
                for (int n = 0; n < queue.length(); n++) {
                  String u = queue.getJSONObject(n).optString("uri");
                  if (u.startsWith("file:") && !new File(Uri.parse(u).getPath()).exists()) {
                    message("搭配中的本地音乐已丢失，请重新导入");
                    return;
                  }
                }
              int scene = Math.max(0, Math.min(3, p.optInt("scene", 3)));
              File target = new File(getFilesDir(), "background-" + scene + ".jpg");
              if (p.has("background"))
                atomicCopy(
                    new FileInputStream(p.getString("background")), target, 20L * 1024 * 1024);
              else if (target.exists() && !target.delete()) throw new IOException();
              runOnUiThread(
                  () -> {
                    send("preset", p.toString());
                    js("applyPresetVisual(" + p + ")");
                  });
            } catch (Exception e) {
              message("搭配无法恢复，背景文件可能已丢失");
            }
          });
    }

    @JavascriptInterface
    public void deletePreset(String id) {
      runOnUiThread(
          () ->
              new AlertDialog.Builder(MainActivity.this)
                  .setTitle("删除这个搭配？")
                  .setNegativeButton("取消", null)
                  .setPositiveButton(
                      "删除",
                      (d, w) ->
                          imports.execute(
                              () -> {
                                JSONArray old = list("presets"), kept = new JSONArray();
                                for (int i = 0; i < old.length(); i++) {
                                  JSONObject p = old.optJSONObject(i);
                                  if (p != null && !id.equals(p.optString("id"))) kept.put(p);
                                }
                                if (prefs().edit().putString("presets", kept.toString()).commit()) {
                                  if (id.matches("[a-f0-9-]{36}"))
                                    new File(getFilesDir(), "presets/" + id + ".jpg").delete();
                                  runOnUiThread(() -> js("showPresets()"));
                                }
                              }))
                  .show());
    }
  }

  static JSONObject find(JSONArray entries, String id) {
    for (int n = 0; n < entries.length(); n++) {
      JSONObject p = entries.optJSONObject(n);
      if (p != null && id.equals(p.optString("id"))) return p;
    }
    return null;
  }

  static void copy(InputStream stream, File target, long max) throws IOException {
    try (InputStream in = stream;
        OutputStream out = new FileOutputStream(target)) {
      if (in == null) throw new IOException();
      byte[] bytes = new byte[65536];
      long total = 0;
      int n;
      while ((n = in.read(bytes)) != -1) {
        total += n;
        if (total > max) throw new IOException("size");
        out.write(bytes, 0, n);
      }
      if (total == 0) throw new IOException("empty");
    }
  }

  static void atomicCopy(InputStream stream, File target, long max) throws IOException {
    File temp = File.createTempFile("import-", ".tmp", target.getParentFile());
    try {
      copy(stream, temp, max);
      if (!temp.renameTo(target)) throw new IOException("rename");
    } finally {
      temp.delete();
    }
  }

  protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (result != RESULT_OK || data == null) return;
    if (req == 8 && data.getData() != null) {
      final Uri u = data.getData();
      final int scene = backgroundScene;
      message("正在处理背景…");
      imports.execute(() -> importBackground(u, scene));
      return;
    }
    if (req != 7 && req != 10) return;
    final java.util.ArrayList<Uri> selected = new java.util.ArrayList<>();
    if (data.getClipData() != null) {
      for (int j = 0; j < Math.min(50, data.getClipData().getItemCount()); j++)
        selected.add(data.getClipData().getItemAt(j).getUri());
    } else if (data.getData() != null) selected.add(data.getData());
    message("正在导入…每次最多处理 50 个文件");
    imports.execute(() -> importAudio(selected, req == 10));
  }

  void importBackground(Uri u, int scene) {
    File temp = null;
    android.graphics.Bitmap image = null;
    try {
      android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
      opts.inJustDecodeBounds = true;
      try (InputStream in = getContentResolver().openInputStream(u)) {
        android.graphics.BitmapFactory.decodeStream(in, null, opts);
      }
      if (opts.outWidth <= 0 || opts.outHeight <= 0) throw new IOException();
      opts.inJustDecodeBounds = false;
      opts.inSampleSize = 1;
      while (Math.max(opts.outWidth, opts.outHeight) / opts.inSampleSize > 2048)
        opts.inSampleSize *= 2;
      try (InputStream in = getContentResolver().openInputStream(u)) {
        image = android.graphics.BitmapFactory.decodeStream(in, null, opts);
      }
      if (image == null) throw new IOException();
      try (InputStream in = getContentResolver().openInputStream(u)) {
        int orientation =
            new android.media.ExifInterface(in)
                .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
        android.graphics.Matrix m = new android.graphics.Matrix();
        switch (orientation) {
          case 2:
            m.setScale(-1, 1);
            break;
          case 3:
            m.setRotate(180);
            break;
          case 4:
            m.setScale(1, -1);
            break;
          case 5:
            m.setRotate(90);
            m.postScale(-1, 1);
            break;
          case 6:
            m.setRotate(90);
            break;
          case 7:
            m.setRotate(-90);
            m.postScale(-1, 1);
            break;
          case 8:
            m.setRotate(-90);
            break;
        }
        if (!m.isIdentity()) {
          android.graphics.Bitmap rotated =
              android.graphics.Bitmap.createBitmap(
                  image, 0, 0, image.getWidth(), image.getHeight(), m, true);
          if (rotated != image) {
            image.recycle();
            image = rotated;
          }
        }
      } catch (IOException ignored) {
      }
      temp = File.createTempFile("background-", ".tmp", getFilesDir());
      try (OutputStream out = new FileOutputStream(temp)) {
        if (!image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out))
          throw new IOException();
      }
      if (!temp.renameTo(new File(getFilesDir(), "background-" + scene + ".jpg")))
        throw new IOException();
      runOnUiThread(
          () -> {
            js("refreshBackgrounds()");
            if (RadioService.self != null) RadioService.self.refreshArtwork();
          });
      message("背景已更新");
    } catch (Exception | OutOfMemoryError e) {
      message("图片无法读取，请选择较小的其他图片并检查存储空间");
    } finally {
      if (image != null) image.recycle();
      if (temp != null) temp.delete();
    }
  }

  void importAudio(java.util.List<Uri> selected, boolean noise) {
    String key = noise ? "noises" : "songs";
    JSONArray entries = list(key);
    int ok = 0, failed = 0, duplicate = 0;
    for (Uri u : selected) {
      File target = null;
      try {
        boolean exists = false;
        int replace = -1;
        for (int k = 0; k < entries.length(); k++) {
          JSONObject p = entries.getJSONObject(k);
          if (u.toString().equals(p.optString("source", p.optString("uri")))) {
            String prior = p.optString("uri");
            if (prior.startsWith("file:") && new File(Uri.parse(prior).getPath()).exists())
              exists = true;
            else replace = k;
          }
        }
        if (exists) {
          duplicate++;
          continue;
        }
        String name = noise ? "我的白噪音" : "本地音乐";
        try (Cursor c =
            getContentResolver()
                .query(u, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
          if (c != null && c.moveToFirst() && c.getString(0) != null) name = c.getString(0);
        }
        File dir = new File(getFilesDir(), noise ? "noises" : "music");
        dir.mkdirs();
        target = File.createTempFile("audio-", ".audio", dir);
        copy(getContentResolver().openInputStream(u), target, 200L * 1024 * 1024);
        android.media.MediaMetadataRetriever metadata = new android.media.MediaMetadataRetriever();
        long duration;
        TrackInfo tags;
        try {
          metadata.setDataSource(target.getAbsolutePath());
          duration =
              Long.parseLong(
                  metadata.extractMetadata(
                      android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));
          if (duration <= 0) throw new IOException("format");
          tags = MediaArtwork.readTags(metadata, name);
        } finally {
          metadata.release();
        }
        String id =
            replace >= 0
                ? entries
                    .getJSONObject(replace)
                    .optString("id", java.util.UUID.randomUUID().toString())
                : java.util.UUID.randomUUID().toString();
        JSONObject entry =
            new JSONObject()
                .put("id", id)
                .put("title", noise ? name : tags.title)
                .put("fileName", name)
                .put("tagTitle", tags.title)
                .put("artist", tags.artist)
                .put("album", tags.album)
                .put("uri", Uri.fromFile(target).toString())
                .put("source", u.toString())
                .put("duration", duration)
                .put("size", target.length());
        if (replace >= 0) entries.put(replace, entry);
        else entries.put(entry);
        if (!prefs().edit().putString(key, entries.toString()).commit())
          throw new IOException("save");
        ok++;
      } catch (Exception e) {
        failed++;
        if (target != null) target.delete();
        entries = list(key);
      }
    }
    final String text = "导入 " + ok + " 个，重复 " + duplicate + " 个，失败 " + failed + " 个";
    runOnUiThread(
        () -> {
          if (!isDestroyed()) {
            js(noise ? "refreshNoises()" : "reloadLibrary()");
            new AlertDialog.Builder(this)
                .setMessage(
                    text
                        + "\n支持设备可解码的普通音频；单个文件上限 200 MB。保存本机副本，卸载后清除。"
                        + (noise ? "\n自定义声音会循环播放，衔接效果取决于文件本身。" : ""))
                .setPositiveButton("知道了", null)
                .show();
          }
        });
  }
}
