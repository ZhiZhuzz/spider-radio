package radio.spider.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.net.*;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.*;
import org.json.*;

public class RadioService extends Service {
  static RadioService self;
  final PlaybackOrder order = new PlaybackOrder();
  // MediaPlayer callbacks and state live on the main thread. Only blocking source setup
  // and release run off-thread; generation checks prevent stale player ownership.
  volatile boolean destroyed = false;
  volatile int ambientGeneration = 0;
  boolean ambientLoading = false, ducked = false;
  String customSound = "", customTitle = "", ambientError = "";
  float ambientGain = 1f, outgoingGain = 0f;
  int fadeGeneration = 0;
  long lastPositionSave = 0;
  volatile int generation = 0;
  SystemMediaOutput mediaOutput;
  MediaPlayer player, ambient, outgoing;
  MediaSession session;
  AudioManager audio;
  AudioFocusRequest focus;
  final Handler handler = new Handler(Looper.getMainLooper());
  MediaArtwork artworkLoader;
  android.graphics.Bitmap artwork, placeholderArtwork;
  TrackInfo trackInfo;
  String artworkOrigin = "placeholder";
  boolean sessionVisible = false;
  int lastDuration = 0, bufferPercent = 0;
  Network activeNetwork;
  volatile String mediaSnapshot = "媒体会话尚未发布";
  final SourceLoader<MediaPlayer> sources =
      new SourceLoader<>(
          r -> handler.post(r),
          p -> {
            try {
              p.release();
            } catch (Exception ignored) {
            }
          });
  boolean prepared = false,
      playing = false,
      wanted = false,
      buffering = false,
      resumeFocus = false,
      noiseOnly = false,
      live = false,
      liveNoise = false;
  String title = "蓝色时差", error = "", uri = "demo.wav", mode = "顺序", stationId = "love";
  float musicVolume = .75f, ambVolume = .25f;
  int channel = 3, soundChoice = -1, index = 0, localPosition = 0, retries = 0;
  ConnectivityManager connectivity;
  ConnectivityManager.NetworkCallback networkCallback;
  boolean waitingNetwork = false;
  int bufferEpoch = 0;
  WifiManager.WifiLock wifiLock;
  PowerManager.WakeLock connectingLock;
  final java.util.ArrayDeque<String> events = new java.util.ArrayDeque<>();

  void log(String message) {
    if (events.size() >= 30) events.removeFirst();
    events.addLast(
        new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)
                .format(new java.util.Date())
            + " "
            + message);
  }

  boolean online() {
    NetworkCapabilities c = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
    return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  }

  void locks() {
    boolean hold = live && !noiseOnly && wanted && !waitingNetwork;
    if (hold && !wifiLock.isHeld()) wifiLock.acquire();
    if (!hold && wifiLock.isHeld()) wifiLock.release();
    if (hold && buffering) {
      if (!connectingLock.isHeld()) connectingLock.acquire(45000);
    } else if (connectingLock.isHeld()) connectingLock.release();
  }

  void waitNetwork() {
    cancelPlayer();
    wanted = true;
    waitingNetwork = true;
    buffering = true;
    error = "网络已断开，恢复后自动重连";
    log("waiting network");
    publish();
  }

  public static String diagnostics(Context context) {
    RadioService s = self;
    JSONObject info = SystemPlaybackInfo.read(context);
    String system = info.toString();
    return "Spider Radio 0.8.3\n"
        + system
        + "\n"
        + (s == null
            ? "service idle"
            : (s.mediaSnapshot
                + "\n"
                + "session="
                + s.sessionVisible
                + " live="
                + s.live
                + " station="
                + s.stationId
                + " playing="
                + s.playing
                + " wanted="
                + s.wanted
                + " buffering="
                + s.buffering
                + " waiting="
                + s.waitingNetwork
                + " prepared="
                + s.prepared
                + "\n"
                + android.text.TextUtils.join("\n", s.events)));
  }

  long sleepAt = 0;
  JSONArray queue = new JSONArray(), catalog = new JSONArray();
  final BroadcastReceiver noisy =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          pause();
        }
      };

  android.content.SharedPreferences prefs() {
    return getSharedPreferences("radio", 0);
  }

  public void onCreate() {
    super.onCreate();
    self = this;
    connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    wifiLock =
        ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE))
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "spider:live");
    wifiLock.setReferenceCounted(false);
    connectingLock =
        ((PowerManager) getSystemService(POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spider:connecting");
    connectingLock.setReferenceCounted(false);
    networkCallback =
        new ConnectivityManager.NetworkCallback() {
          public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            handler.post(
                () -> {
                  if (destroyed || !n.equals(connectivity.getActiveNetwork())) return;
                  boolean changed = activeNetwork != null && !activeNetwork.equals(n);
                  activeNetwork = n;
                  if (waitingNetwork && wanted && online()) {
                    waitingNetwork = false;
                    retries = 0;
                    log("network restored");
                    load();
                  } else if (changed && live && !noiseOnly && wanted) {
                    log("network switched");
                    final int token = generation;
                    handler.postDelayed(
                        () -> {
                          if (!destroyed && token == generation && wanted && live && !noiseOnly) {
                            retries = 0;
                            load();
                          }
                        },
                        700);
                  }
                });
          }

          public void onLost(Network n) {
            handler.post(
                () -> {
                  if (!destroyed && live && !noiseOnly && wanted && !online()) waitNetwork();
                });
          }
        };
    activeNetwork = connectivity.getActiveNetwork();
    connectivity.registerDefaultNetworkCallback(networkCallback);
    placeholderArtwork = MediaArtwork.placeholder();
    artwork = placeholderArtwork;
    artworkLoader = new MediaArtwork(this, handler);
    try (java.io.InputStream in = getAssets().open("radio-stations.json")) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      catalog = new JSONArray(out.toString("UTF-8"));
    } catch (Exception e) {
      error = "频道列表加载失败";
    }
    try {
      queue = new JSONArray(prefs().getString("queue", "[]"));
    } catch (Exception ignored) {
    }
    index = prefs().getInt("index", 0);
    channel = clamp(prefs().getInt("channel", 3), 0, 3);
    soundChoice = clamp(prefs().getInt("soundChoice", -1), -1, 3);
    noiseOnly = prefs().getBoolean("noiseOnly", false);
    live = prefs().getBoolean("live", false);
    liveNoise = prefs().getBoolean("liveNoise", false);
    stationId = prefs().getString("stationId", "love");
    customSound = prefs().getString("customSound", "");
    resolveCustom();
    musicVolume = prefs().getFloat("music", .75f);
    ambVolume = prefs().getFloat("ambient", .25f);
    mode = prefs().getString("mode", "顺序");
    localPosition = prefs().getInt("position", 0);
    if (!prefs().getBoolean("mode061", false)) {
      if (mode.equals("顺序") && prefs().contains("mode")) mode = "列表";
      prefs().edit().putString("mode", mode).putBoolean("mode061", true).apply();
    }
    try {
      JSONArray deleted =
          new JSONArray(getSharedPreferences("MainActivity", 0).getString("deletedSongs", "[]"));
      for (int d = 0; d < deleted.length(); d++) pruneQueue(deleted.getString(d));
    } catch (Exception ignored) {
    }
    if (queue.length() == 0) queue = availableLibrary();
    index = clamp(index, 0, Math.max(0, queue.length() - 1));
    updateTitle();
    audio = (AudioManager) getSystemService(AUDIO_SERVICE);
    focus =
        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs())
            .setOnAudioFocusChangeListener(
                change -> {
                  log("audio focus " + change);
                  if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    resumeFocus = false;
                    pause();
                  } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    pauseForFocus();
                  } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    ducked = true;
                    volumes();
                  } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                    ducked = false;
                    if (resumeFocus) {
                      resumeFocus = false;
                      startPlayback();
                    }
                    volumes();
                  }
                })
            .build();
    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
        .createNotificationChannel(
            new NotificationChannel("playback", "电台播放", NotificationManager.IMPORTANCE_LOW));
    session = new MediaSession(this, "SpiderRadio");
    mediaOutput = new SystemMediaOutput(this, session);
    session.setCallback(
        new MediaSession.Callback() {
          public void onPlay() {
            mediaCommand("resume");
          }

          public void onPause() {
            mediaCommand("pause");
          }

          public void onSkipToNext() {
            mediaCommand("next");
          }

          public void onSkipToPrevious() {
            mediaCommand("prev");
          }

          public boolean onMediaButtonEvent(Intent intent) {
            android.view.KeyEvent key = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (key != null
                && (key.getKeyCode() == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || key.getKeyCode() == android.view.KeyEvent.KEYCODE_HEADSETHOOK)) {
              if (key.getAction() == android.view.KeyEvent.ACTION_DOWN && key.getRepeatCount() == 0)
                mediaCommand(MediaSurfacePolicy.toggle(wanted));
              return true;
            }
            return super.onMediaButtonEvent(intent);
          }

          public void onSeekTo(long pos) {
            try {
              seek((int) Math.max(0, Math.min(Integer.MAX_VALUE, pos)));
            } catch (Exception e) {
              log("seek " + e.getClass().getSimpleName());
            }
          }

          public void onStop() {
            mediaCommand("stop");
          }
        });
    session.setFlags(
        MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    session.setPlaybackToLocal(attrs());
    session.setSessionActivity(openActivity());
    session.setActive(false);
    publish();
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(
          noisy,
          new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
          Context.RECEIVER_EXPORTED);
    else registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    handler.post(tick);
  }

  static int clamp(int n, int a, int b) {
    return Math.max(a, Math.min(b, n));
  }

  AudioAttributes attrs() {
    return new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build();
  }

  JSONObject station() {
    for (int i = 0; i < catalog.length(); i++) {
      JSONObject o = catalog.optJSONObject(i);
      if (o != null && o.optString("id").equals(stationId)) return o;
    }
    return catalog.optJSONObject(0);
  }

  void updateTitle() {
    String before = uri;
    if (live) {
      JSONObject s = station();
      title = s == null ? "直播电台" : s.optString("name");
      uri = "radio:" + stationId;
    } else {
      JSONObject s = queue.optJSONObject(index);
      title = s == null ? "选择一首音乐" : s.optString("title");
      uri = s == null ? "" : s.optString("uri");
    }
    if (!before.equals(uri) || trackInfo == null) {
      JSONObject item = queue.optJSONObject(index);
      lastDuration =
          live || item == null
              ? 0
              : (int) Math.min(Integer.MAX_VALUE, Math.max(0, item.optLong("duration")));
      trackInfo =
          new TrackInfo(
              item == null ? null : item.optString("tagTitle"),
              item == null ? null : item.optString("artist"),
              item == null ? null : item.optString("album"),
              title,
              lastDuration);
      invalidateMediaMetadata();
    }
  }

  Runnable tick =
      new Runnable() {
        public void run() {
          if (destroyed) return;
          if (sessionVisible
              && playing
              && SystemClock.elapsedRealtime() - mediaOutput.lastUpdate >= 5000) {
            updateSessionState();
          }
          if (playing && prepared && !live && !noiseOnly) {
            try {
              localPosition = player.getCurrentPosition();
              if (SystemClock.elapsedRealtime() - lastPositionSave >= 10000) {
                lastPositionSave = SystemClock.elapsedRealtime();
                prefs().edit().putInt("position", localPosition).apply();
              }
            } catch (Exception ignored) {
            }
          }
          if (sleepAt > 0) {
            long left = sleepAt - SystemClock.elapsedRealtime();
            if (left <= 0) {
              sleepAt = 0;
              pause();
              volumes();
            } else if (left < 10000) volumes();
          }
          handler.postDelayed(this, 1000);
        }
      };

  public int onStartCommand(Intent i, int flags, int id) {
    if (i == null) return START_NOT_STICKY;
    String a = i.getAction(), v = i.getStringExtra("value");
    if (a == null) return START_NOT_STICKY;
    try {
      if (a.equals("resume")
          || a.equals("toggle")
          || a.equals("play")
          || a.equals("radio")
          || a.equals("local")
          || a.equals("noise")
          || a.equals("preset")
          || a.equals("next")
          || a.equals("prev")) promote();
      switch (a) {
        case "resume":
          startPlayback();
          break;
        case "stop":
          stopPlayback();
          break;
        case "dismiss":
          if (!wanted) stopPlayback();
          break;
        case "artwork":
          refreshArtwork();
          break;
        case "toggle":
          if (wanted) pause();
          else startPlayback();
          break;
        case "play":
          JSONObject q = new JSONObject(v);
          JSONObject requested = q.getJSONArray("queue").optJSONObject(q.optInt("index"));
          if (requested == null || deletedSong(requested.optString("uri"))) {
            error = "歌曲已删除或不可用，请重新选择";
            publish();
            break;
          }
          rememberLocal();
          cancelPlayer();
          live = false;
          noiseOnly = false;
          queue = q.getJSONArray("queue");
          order.reset();
          index = q.getInt("index");
          JSONArray deletedQueue =
              new JSONArray(
                  getSharedPreferences("MainActivity", 0).getString("deletedSongs", "[]"));
          for (int n = 0; n < deletedQueue.length(); n++) pruneQueue(deletedQueue.optString(n));
          localPosition = 0;
          persist();
          startPlayback();
          break;
        case "radio":
          JSONObject s = null;
          for (int n = 0; n < catalog.length(); n++)
            if (catalog.getJSONObject(n).getString("id").equals(v)) s = catalog.getJSONObject(n);
          if (s == null) break;
          rememberLocal();
          cancelPlayer();
          live = true;
          noiseOnly = false;
          stationId = v;
          retries = 0;
          persist();
          startPlayback();
          break;
        case "local":
          rememberLocal();
          cancelPlayer();
          live = false;
          noiseOnly = false;
          persist();
          startPlayback();
          break;
        case "noise":
          boolean on = Boolean.parseBoolean(v);
          rememberLocal();
          cancelPlayer();
          noiseOnly = on;
          persist();
          startPlayback();
          break;
        case "pause":
          pause();
          break;
        case "next":
          next(1);
          break;
        case "prev":
          next(-1);
          break;
        case "seek":
          seek(Integer.parseInt(v));
          break;
        case "channel":
          int previous = channel;
          channel = clamp(Integer.parseInt(v), 0, 3);
          if (previous != channel) refreshArtwork();
          if (previous != channel && soundChoice == -1 && customSound.isEmpty()) loadAmbient();
          persist();
          publish();
          break;
        case "sound":
          customSound = "";
          customTitle = "";
          soundChoice = clamp(Integer.parseInt(v), -1, 3);
          loadAmbient();
          persist();
          publish();
          break;
        case "customSound":
          if (!selectCustom(v)) break;
          loadAmbient();
          persist();
          publish();
          break;
        case "removeNoise":
          if (customSound.equals(v)) {
            customSound = "";
            customTitle = "";
            soundChoice = -1;
            loadAmbient();
            persist();
            publish();
          }
          break;
        case "preset":
          applyPreset(new JSONObject(v));
          break;
        case "liveNoise":
          liveNoise = Boolean.parseBoolean(v);
          if (ambient == null) loadAmbient();
          syncAmbient();
          persist();
          publish();
          break;
        case "volumes":
          JSONObject o = new JSONObject(v);
          musicVolume = (float) Math.max(0, Math.min(1, o.getDouble("music")));
          ambVolume = (float) Math.max(0, Math.min(1, o.getDouble("ambient")));
          volumes();
          persist();
          break;
        case "sleep":
          setSleep(v);
          break;
        case "deleteSong":
          removeSong(v);
          break;
        case "mode":
          if (v.equals("顺序") || v.equals("列表") || v.equals("随机") || v.equals("单曲")) {
            mode = v;
            order.reset();
            persist();
            publish();
          }
          break;
      }
    } catch (Exception e) {
      log("command " + e.getClass().getSimpleName());
      error = "操作未完成，请重试";
      publish();
      if (!wanted) stopForeground(false);
    }
    if (!wanted && !resumeFocus) stopForeground(false);
    return START_NOT_STICKY;
  }

  void rememberLocal() {
    if (!live && prepared && player != null) {
      try {
        localPosition = player.getCurrentPosition();
      } catch (Exception ignored) {
      }
    }
  }

  void persist() {
    prefs()
        .edit()
        .putString("customSound", customSound)
        .putBoolean("noiseOnly", noiseOnly)
        .putBoolean("live", live)
        .putBoolean("liveNoise", liveNoise)
        .putString("stationId", stationId)
        .putInt("soundChoice", soundChoice)
        .putString("queue", queue.toString())
        .putInt("index", index)
        .putInt("channel", channel)
        .putInt("position", localPosition)
        .putFloat("music", musicVolume)
        .putFloat("ambient", ambVolume)
        .putString("mode", mode)
        .apply();
  }

  void source(MediaPlayer p, String source) throws Exception {
    if (source.startsWith("https://")) {
      java.util.Map<String, String> headers = new java.util.HashMap<>();
      headers.put("User-Agent", "SpiderRadio/0.8.3 (Android)");
      p.setDataSource(this, Uri.parse(source), headers);
    } else if (source.startsWith("file:")) p.setDataSource(Uri.parse(source).getPath());
    else if (source.startsWith("content:")) p.setDataSource(this, Uri.parse(source));
    else {
      try (android.content.res.AssetFileDescriptor f = getAssets().openFd(source)) {
        p.setDataSource(f.getFileDescriptor(), f.getStartOffset(), f.getLength());
      }
    }
  }

  void dispose(MediaPlayer p) {
    sources.release(p);
  }

  void cancelPlayer() {
    generation++;
    bufferEpoch++;
    waitingNetwork = false;
    MediaPlayer old = player;
    player = null;
    if (old != null && prepared) {
      try {
        old.pause();
      } catch (Exception ignored) {
      }
    }
    dispose(old);
    prepared = false;
    playing = false;
    buffering = false;
    wanted = false;
    bufferPercent = 0;
    finishAmbientFade();
    if (ambient != null) {
      try {
        if (ambient.isPlaying()) ambient.pause();
      } catch (Exception ignored) {
      }
    }
    locks();
  }

  interface Ready {
    void accept(MediaPlayer p);
  }

  void prepareSource(
      final MediaPlayer fresh,
      final String input,
      final java.util.function.BooleanSupplier valid,
      final Ready ready,
      final Runnable failure) {
    sources.load(
        fresh,
        p -> source(p, input),
        valid,
        p -> {
          try {
            ready.accept(p);
            p.prepareAsync();
          } catch (Exception e) {
            failure.run();
          }
        },
        e -> {
          log("source " + e.getClass().getSimpleName());
          failure.run();
        });
  }

  JSONArray availableLibrary() {
    try {
      android.content.SharedPreferences library = getSharedPreferences("MainActivity", 0);
      return LibraryCatalog.available(
          new JSONArray(library.getString("songs", "[]")),
          new JSONArray(library.getString("deletedSongs", "[]")),
          0);
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  void startPlayback() {
    if (!live && !noiseOnly) {
      try {
        JSONArray deleted =
            new JSONArray(getSharedPreferences("MainActivity", 0).getString("deletedSongs", "[]"));
        QueueRemoval clean = new QueueRemoval(queue, index, LibraryCatalog.removed(deleted));
        if (clean.removed) {
          cancelPlayer();
          queue = clean.queue;
          index = clean.index;
          if (clean.currentRemoved) localPosition = 0;
          order.reset();
        }
      } catch (Exception ignored) {
      }
      if (queue.length() == 0) {
        queue = availableLibrary();
        index = 0;
        localPosition = 0;
      }
      if (queue.length() == 0) {
        cancelPlayer();
        updateTitle();
        error = "曲库暂无歌曲，请导入音乐或恢复内置试听";
        persist();
        publish();
        stopForeground(false);
        return;
      }
    }
    resumeFocus = false;
    error = "";
    wanted = true;
    updateTitle();
    refreshArtwork();
    promote();
    if (noiseOnly) {
      if (ambient == null && !ambientLoading) loadAmbient();
      play();
    } else if (prepared) play();
    else {
      retries = 0;
      load();
    }
  }

  void load() {
    if (live && !noiseOnly && !online()) {
      waitNetwork();
      return;
    }
    log("load " + (live ? stationId : "local"));
    boolean intention = wanted;
    cancelPlayer();
    wanted = intention;
    buffering = true;
    error = "";
    updateTitle();
    promote();
    publish();
    final int token = generation;
    final MediaPlayer fresh = new MediaPlayer();
    fresh.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
    fresh.setAudioAttributes(attrs());
    fresh.setOnPreparedListener(
        p -> {
          if (token != generation || !wanted) return;
          log("prepared");
          lastDuration = live ? 0 : Math.max(0, p.getDuration());
          prepared = true;
          buffering = false;
          bufferEpoch++;
          if (!live) p.seekTo(Math.min(localPosition, Math.max(0, p.getDuration())));
          if (ambient == null && !ambientLoading && (!live || liveNoise)) loadAmbient();
          play();
        });
    fresh.setOnSeekCompleteListener(
        p -> {
          if (token == generation && prepared) {
            localPosition = Math.max(0, p.getCurrentPosition());
            persist();
            publish();
          }
        });
    fresh.setOnBufferingUpdateListener(
        (p, percent) -> {
          if (token == generation) bufferPercent = clamp(percent, 0, 100);
        });
    fresh.setOnInfoListener(
        (p, what, extra) -> {
          if (token != generation) return true;
          if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            buffering = true;
            final int episode = ++bufferEpoch;
            publish();
            handler.postDelayed(
                () -> {
                  if (generation == token && bufferEpoch == episode && wanted && buffering) {
                    log("buffer timeout");
                    failed(token);
                  }
                },
                20000);
          }
          if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            bufferEpoch++;
            buffering = false;
            publish();
          }
          return false;
        });
    fresh.setOnCompletionListener(
        p -> {
          if (token != generation || !wanted) return;
          if (live) {
            failed(token);
          } else if (mode.equals("单曲")) {
            p.seekTo(0);
            play();
          } else if (mode.equals("顺序") && index >= queue.length() - 1) {
            localPosition = 0;
            pause();
            localPosition = 0;
            p.seekTo(0);
            persist();
            publish();
          } else next(1);
        });
    fresh.setOnErrorListener(
        (p, w, e) -> {
          if (token == generation) log("media error " + w + "/" + e);
          failed(token);
          return true;
        });
    handler.postDelayed(
        () -> {
          if (generation == token && wanted && !prepared) {
            log("prepare timeout stage=" + (player == null ? "source setup" : "decoder prepare"));
            failed(token);
          }
        },
        18000);
    try {
      String input = uri;
      if (live) {
        JSONObject s = station();
        JSONArray urls = s == null ? null : s.optJSONArray("urls");
        if (urls == null || urls.length() == 0) throw new Exception();
        input = urls.getString(retries % urls.length());
      }
      log("source setup " + (live ? Uri.parse(input).getHost() : "local"));
      prepareSource(
          fresh,
          input,
          () -> !destroyed && generation == token,
          p -> {
            player = p;
            log("source ready; prepareAsync");
          },
          () -> failed(token));
    } catch (Exception e) {
      dispose(fresh);
      log("source " + e.getClass().getSimpleName());
      failed(token);
      return;
    }
    if (live)
      handler.postDelayed(
          () -> {
            if (generation == token && playing && !buffering) retries = 0;
          },
          60000);
  }

  void failed(int token) {
    if (token != generation) return;
    if (live && wanted && !online()) {
      waitNetwork();
      return;
    }
    log("retry " + retries);
    boolean retry = live && wanted && retries < 2;
    int attempt = retries + 1;
    cancelPlayer();
    if (retry) {
      retries = attempt;
      wanted = true;
      buffering = true;
      error = "连接中断，正在重试（" + retries + "/2）";
      publish();
      final int current = generation;
      handler.postDelayed(
          () -> {
            if (generation == current && wanted) load();
          },
          1500L * retries);
    } else {
      resumeFocus = false;
      error = live ? "电台暂时无法连接，请检查网络后重试或换台" : "歌曲无法读取，请重新导入或选择其他歌曲";
      publish();
      stopForeground(false);
      audio.abandonAudioFocusRequest(focus);
    }
  }

  void seek(int pos) {
    if (prepared && !live && !noiseOnly) {
      localPosition = clamp(pos, 0, Math.max(0, player.getDuration()));
      player.seekTo(localPosition);
      persist();
    }
  }

  void next(int d) {
    if (noiseOnly) return;
    rememberLocal();
    if (live) {
      int n = 0;
      for (int k = 0; k < catalog.length(); k++)
        if (catalog.optJSONObject(k).optString("id").equals(stationId)) n = k;
      if (catalog.length() == 0) return;
      stationId = catalog.optJSONObject(Math.floorMod(n + d, catalog.length())).optString("id");
    } else {
      if (queue.length() > 0)
        index =
            mode.equals("随机")
                ? order.move(queue.length(), index, d)
                : Math.floorMod(index + d, queue.length());
      localPosition = 0;
    }
    cancelPlayer();
    persist();
    startPlayback();
  }

  int soundIndex() {
    return soundChoice < 0 ? channel : soundChoice;
  }

  boolean ambientWanted() {
    return playing && (noiseOnly || !live || liveNoise);
  }

  // Loading a new source must not interrupt an already audible fade.
  void loadAmbient() {
    volumes();
    final int token = ++ambientGeneration;
    ambientLoading = true;
    final MediaPlayer fresh = new MediaPlayer();
    fresh.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
    fresh.setAudioAttributes(attrs());
    fresh.setLooping(true);
    fresh.setOnErrorListener(
        (p, w, e) -> {
          if (token == ambientGeneration) {
            if (ambient == p) {
              ambient = null;
              dispose(p);
            }
            ambientFailure(token);
          }
          return true;
        });
    fresh.setOnPreparedListener(p -> transitionAmbient(p, token));
    if (pendingAmbient != null && pendingAmbient != ambient) {
      dispose(pendingAmbient);
      pendingAmbient = null;
    }
    String input = new String[] {"city.ogg", "snow.ogg", "train.ogg", "rain.ogg"}[soundIndex()];
    if (!customSound.isEmpty()) {
      JSONObject custom = resolveCustom();
      if (custom == null) {
        dispose(fresh);
        ambientFailure(token);
        return;
      }
      input = custom.optString("uri");
    }
    prepareSource(
        fresh,
        input,
        () -> !destroyed && ambientGeneration == token,
        p -> pendingAmbient = p,
        () -> ambientFailure(token));
    handler.postDelayed(
        () -> {
          if (ambientGeneration == token && ambientLoading) ambientFailure(token);
        },
        12000);
  }

  void transitionAmbient(final MediaPlayer p, final int token) {
    // A superseded deferred transition was released by its new owner request.
    if (token != ambientGeneration || destroyed) return;
    if (ambientGain < 1 && ambientWanted()) {
      handler.postDelayed(() -> transitionAmbient(p, token), 30);
      return;
    }
    ambientLoading = false;
    pendingAmbient = null;
    ambientError = "";
    final MediaPlayer old = ambient;
    finishAmbientFade();
    ambient = p;
    if (noiseOnly && wanted && !playing) {
      dispose(old);
      play();
      return;
    }
    if (ambientWanted()) {
      outgoing = old;
      ambientGain = 0;
      outgoingGain = 1;
      final int fade = ++fadeGeneration;
      volumes();
      p.start();
      for (int step = 1; step <= 24; step++) {
        final float gain = step / 24f;
        handler.postDelayed(
            () -> {
              if (fadeGeneration != fade || ambient != p || destroyed) return;
              if (!ambientWanted()) {
                finishAmbientFade();
                return;
              }
              ambientGain = gain;
              outgoingGain = 1 - gain;
              volumes();
              if (gain >= 1) finishAmbientFade();
            },
            step * 30);
      }
    } else {
      dispose(old);
      volumes();
    }
    publish();
  }

  void finishAmbientFade() {
    fadeGeneration++;
    if (outgoing != null) {
      dispose(outgoing);
      outgoing = null;
    }
    ambientGain = 1;
    outgoingGain = 0;
  }

  MediaPlayer pendingAmbient;

  void ambientFailure(int token) {
    if (destroyed || token != ambientGeneration) return;
    ambientGeneration++;
    ambientLoading = false;
    if (pendingAmbient != null && pendingAmbient != ambient) dispose(pendingAmbient);
    pendingAmbient = null;
    log("ambient error");
    if (ambient != null) {
      dispose(ambient);
      ambient = null;
    }
    if (outgoing != null) {
      dispose(outgoing);
      outgoing = null;
    }
    ambientError = "白噪音无法播放，请重新导入或选择其他声音";
    finishAmbientFade();
    if (noiseOnly) {
      wanted = false;
      playing = false;
      buffering = false;
      audio.abandonAudioFocusRequest(focus);
      stopForeground(false);
    }
    publish();
  }

  void syncAmbient() {
    if (ambient != null) {
      try {
        if (ambientWanted()) ambient.start();
        else if (ambient.isPlaying()) ambient.pause();
      } catch (IllegalStateException e) {
        log("ambient invalid state");
        ambientFailure(ambientGeneration);
      }
    }
    if (!ambientWanted() && outgoing != null) finishAmbientFade();
  }

  String soundTitle() {
    if (!customSound.isEmpty()) return customTitle.isEmpty() ? "我的白噪音" : customTitle;
    return new String[] {"江畔远声", "雪夜窗外风", "列车行驶声", "窗前雨声"}[soundIndex()];
  }

  String displayTitle() {
    return noiseOnly
        ? soundTitle()
        : live ? title : trackInfo == null ? TrackInfo.filenameTitle(title) : trackInfo.title;
  }

  String displaySubtitle() {
    return live || noiseOnly ? sourceLabel() : trackInfo == null ? "本地音乐" : trackInfo.subtitle();
  }

  void play() {
    try {
      playReady();
    } catch (RuntimeException e) {
      log("start failed " + e.getClass().getSimpleName());
      if (noiseOnly) ambientFailure(ambientGeneration);
      else failed(generation);
    }
  }

  void playReady() {
    if (!wanted || destroyed) return;
    if (!noiseOnly && !prepared) return;
    if (noiseOnly && ambient == null) {
      if (!ambientLoading) loadAmbient();
      if (ambientLoading) {
        buffering = true;
        publish();
      }
      return;
    }
    if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      resumeFocus = false;
      error = "其他应用正在使用声音，请稍后播放";
      wanted = false;
      playing = false;
      buffering = false;
      publish();
      stopForeground(false);
      return;
    }
    if (ambient == null && !ambientLoading && (noiseOnly || !live || liveNoise)) loadAmbient();
    wanted = true;
    playing = true;
    buffering = false;
    error = "";
    volumes();
    if (prepared && !noiseOnly) player.start();
    syncAmbient();
    session.setActive(true);
    promote();
    publish();
  }

  void pause() {
    resumeFocus = false;
    ducked = false;
    pauseInternal();
  }

  void pauseForFocus() {
    resumeFocus = wanted;
    pauseInternal();
  }

  void pauseInternal() {
    rememberLocal();
    if (live || !prepared) cancelPlayer();
    else {
      wanted = false;
      playing = false;
      buffering = false;
      if (player != null)
        try {
          player.pause();
        } catch (IllegalStateException e) {
          log("pause invalid state");
          cancelPlayer();
        }
      syncAmbient();
    }
    persist();
    if (!resumeFocus) audio.abandonAudioFocusRequest(focus);
    error = "";
    publish();
    if (!resumeFocus) stopForeground(false);
  }

  void setVolumes(float f) {
    try {
      if (player != null && prepared) player.setVolume(musicVolume * f, musicVolume * f);
    } catch (IllegalStateException e) {
      log("volume invalid state");
    }
    try {
      if (ambient != null)
        ambient.setVolume(ambVolume * f * ambientGain, ambVolume * f * ambientGain);
    } catch (IllegalStateException e) {
      log("ambient volume invalid state");
    }
    try {
      if (outgoing != null)
        outgoing.setVolume(ambVolume * f * outgoingGain, ambVolume * f * outgoingGain);
    } catch (IllegalStateException e) {
      log("outgoing volume invalid state");
    }
  }

  float volumeFactor() {
    return (ducked ? .2f : 1f)
        * (sleepAt > 0
            ? Math.min(1, Math.max(0, (sleepAt - SystemClock.elapsedRealtime()) / 10000f))
            : 1);
  }

  void volumes() {
    setVolumes(volumeFactor());
  }

  String playbackError() {
    return !error.isEmpty() ? error : (noiseOnly || !live || liveNoise ? ambientError : "");
  }

  void setSleep(String value) {
    int mins = SleepTimer.parse(value);
    sleepAt = mins > 0 ? SystemClock.elapsedRealtime() + mins * 60000L : 0;
    volumes();
  }

  void mediaCommand(String action) {
    try {
      log("media control " + action);
      if (action.equals("resume")) startPlayback();
      else if (action.equals("pause")) pause();
      else if (action.equals("next")) next(1);
      else if (action.equals("prev")) next(-1);
      else if (action.equals("stop")) stopPlayback();
    } catch (Exception e) {
      log("media control failed " + e.getClass().getSimpleName());
      pause();
      error = "播放操作未完成，请打开应用重试";
      publish();
    }
  }

  void promote() {
    sessionVisible = true;
    refreshArtwork();
    publishMetadata();
    updateSessionState();
    session.setActive(true);
    startForeground(1, notification());
  }

  void stopPlayback() {
    pause();
    sessionVisible = false;
    updateSessionState();
    session.setActive(false);
    stopForeground(true);
    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(1);
    stopSelf();
  }

  void refreshArtwork() {
    if (artworkLoader == null) return;
    String localSource = !live && !noiseOnly ? uri : "";
    boolean changed =
        artworkLoader.load(
            channel,
            localSource,
            live || noiseOnly ? sourceLabel() : title,
            result -> {
              if (destroyed) return;
              artwork = result.bitmap == null ? placeholderArtwork : result.bitmap;
              artworkOrigin = result.origin;
              if (!live && !noiseOnly && result.info != null) {
                trackInfo = result.info;
                if (!prepared && result.info.duration > 0)
                  lastDuration = (int) Math.min(Integer.MAX_VALUE, result.info.duration);
              }
              invalidateMediaMetadata();
              publish();
            });
    if (changed) {
      artwork = placeholderArtwork;
      artworkOrigin = "placeholder";
      invalidateMediaMetadata();
    }
  }

  PendingIntent openActivity() {
    return mediaOutput.openActivity();
  }

  String sourceLabel() {
    return noiseOnly ? "纯白噪音" : live ? "网络直播" : "本地音乐";
  }

  int duration() {
    if (!live && !noiseOnly && prepared && player != null)
      try {
        return Math.max(0, player.getDuration());
      } catch (Exception ignored) {
      }
    return !live && !noiseOnly ? lastDuration : 0;
  }

  long position() {
    if (!live && !noiseOnly && prepared && player != null)
      try {
        return Math.max(0, player.getCurrentPosition());
      } catch (Exception ignored) {
      }
    return !live && !noiseOnly ? Math.max(0, localPosition) : 0;
  }

  MediaPresentation presentation() {
    return MediaPresentation.of(
        sessionVisible,
        live,
        noiseOnly,
        prepared,
        playing,
        buffering,
        !error.isEmpty() || (noiseOnly && !ambientError.isEmpty()),
        queue.length(),
        position(),
        duration());
  }

  SystemMediaOutput.View mediaView() {
    return new SystemMediaOutput.View(
        presentation(),
        live,
        noiseOnly,
        wanted,
        buffering,
        waitingNetwork,
        uri,
        displayTitle(),
        displaySubtitle(),
        sourceLabel(),
        stationId,
        soundTitle(),
        playbackError(),
        trackInfo,
        artwork,
        artworkOrigin,
        bufferPercent);
  }

  void invalidateMediaMetadata() {
    if (mediaOutput != null) mediaOutput.invalidateMetadata();
  }

  Notification notification() {
    return mediaOutput.notification(mediaView());
  }

  void updateSessionState() {
    if (mediaOutput != null) mediaSnapshot = mediaOutput.updateState(mediaView());
  }

  void publishMetadata() {
    if (mediaOutput != null) mediaOutput.publishMetadata(mediaView());
  }

  void publish() {
    locks();
    if (session == null) return;
    refreshArtwork();
    publishMetadata();
    updateSessionState();
    if (sessionVisible)
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, notification());
  }

  public static String state(Context c) {
    try {
      JSONObject o = new JSONObject();
      RadioService s = self;
      if (s == null) return o.put("playing", false).toString();
      return o.put("playing", s.playing)
          .put("wanted", s.wanted)
          .put("buffering", s.buffering)
          .put("live", s.live)
          .put("stationId", s.stationId)
          .put("liveNoise", s.liveNoise)
          .put("soundChoice", s.soundChoice)
          .put("noiseOnly", s.noiseOnly)
          .put("music", s.musicVolume)
          .put("ambient", s.ambVolume)
          .put("title", s.displayTitle())
          .put("uri", s.uri)
          .put(
              "position", !s.live && !s.noiseOnly && s.prepared ? s.player.getCurrentPosition() : 0)
          .put(
              "duration",
              !s.live && !s.noiseOnly && s.prepared ? Math.max(0, s.player.getDuration()) : 0)
          .put(
              "sleepAt",
              s.sleepAt > 0
                  ? System.currentTimeMillis()
                      + Math.max(0, s.sleepAt - SystemClock.elapsedRealtime())
                  : 0)
          .put("customSound", s.customSound)
          .put("mode", s.mode)
          .put("error", s.playbackError())
          .toString();
    } catch (Exception e) {
      return "{}";
    }
  }

  JSONObject resolveCustom() {
    try {
      JSONArray list =
          new JSONArray(getSharedPreferences("MainActivity", 0).getString("noises", "[]"));
      JSONObject o = MainActivity.find(list, customSound);
      if (o != null) customTitle = o.optString("title", "我的白噪音");
      return o;
    } catch (Exception e) {
      return null;
    }
  }

  boolean selectCustom(String id) {
    String before = customSound;
    customSound = id;
    if (resolveCustom() == null) {
      customSound = before;
      ambientError = "找不到这段白噪音，请重新导入";
      publish();
      return false;
    }
    return true;
  }

  static JSONObject preset(Context context) {
    RadioService s = self;
    android.content.SharedPreferences p = context.getSharedPreferences("radio", 0);
    JSONObject o = new JSONObject();
    try {
      o.put("mode061", true)
          .put("live", s != null ? s.live : p.getBoolean("live", false))
          .put("noiseOnly", s != null ? s.noiseOnly : p.getBoolean("noiseOnly", false))
          .put("stationId", s != null ? s.stationId : p.getString("stationId", "love"))
          .put(
              "queue",
              s != null
                  ? new JSONArray(s.queue.toString())
                  : new JSONArray(p.getString("queue", "[]")))
          .put("index", s != null ? s.index : p.getInt("index", 0))
          .put("soundChoice", s != null ? s.soundChoice : p.getInt("soundChoice", -1))
          .put("customSound", s != null ? s.customSound : p.getString("customSound", ""))
          .put("liveNoise", s != null ? s.liveNoise : p.getBoolean("liveNoise", false))
          .put("music", s != null ? s.musicVolume : p.getFloat("music", .75f))
          .put("ambient", s != null ? s.ambVolume : p.getFloat("ambient", .25f))
          .put("mode", s != null ? s.mode : p.getString("mode", "顺序"));
    } catch (Exception ignored) {
    }
    return o;
  }

  void applyPreset(JSONObject p) throws Exception {
    order.reset();
    rememberLocal();
    cancelPlayer();
    ambientGeneration++;
    ambientLoading = false;
    if (pendingAmbient != null && pendingAmbient != ambient) dispose(pendingAmbient);
    pendingAmbient = null;
    if (ambient != null) {
      dispose(ambient);
      ambient = null;
    }
    channel = clamp(p.optInt("scene", 3), 0, 3);
    soundChoice = clamp(p.optInt("soundChoice", -1), -1, 3);
    customSound = p.optString("customSound");
    resolveCustom();
    live = p.optBoolean("live");
    noiseOnly = p.optBoolean("noiseOnly");
    liveNoise = p.optBoolean("liveNoise");
    stationId = p.optString("stationId", "love");
    queue = p.optJSONArray("queue");
    if (queue == null) queue = new JSONArray();
    index = clamp(p.optInt("index"), 0, Math.max(0, queue.length() - 1));
    localPosition = 0;
    musicVolume = (float) Math.max(0, Math.min(1, p.optDouble("music", .75)));
    ambVolume = (float) Math.max(0, Math.min(1, p.optDouble("ambient", .25)));
    mode = p.optString("mode", "顺序");
    if (!p.optBoolean("mode061") && mode.equals("顺序")) mode = "列表";
    persist();
    startPlayback();
  }

  boolean deletedSong(String uri) {
    try {
      JSONArray deleted =
          new JSONArray(getSharedPreferences("MainActivity", 0).getString("deletedSongs", "[]"));
      for (int n = 0; n < deleted.length(); n++) if (uri.equals(deleted.optString(n))) return true;
    } catch (Exception ignored) {
    }
    return false;
  }

  // Drop deleted entries while retaining the current item when possible.
  boolean pruneQueue(String removed) {
    QueueRemoval result = new QueueRemoval(queue, index, removed);
    queue = result.queue;
    index = result.index;
    if (result.currentRemoved) localPosition = 0;
    order.reset();
    return result.currentRemoved;
  }

  void removeSong(String removed) {
    removeSongs(java.util.Collections.singleton(removed));
  }

  void removeSongs(java.util.Set<String> removed) {
    QueueRemoval edit = new QueueRemoval(queue, index, removed);
    if ((edit.currentRemoved || removed.contains(uri)) && !live && !noiseOnly) {
      pause();
      cancelPlayer();
    }
    queue = edit.queue;
    index = edit.index;
    if (edit.currentRemoved) localPosition = 0;
    order.reset();
    updateTitle();
    persist();
    publish();
  }

  public IBinder onBind(Intent i) {
    return null;
  }

  public void onTaskRemoved(Intent rootIntent) {
    log("task removed; wanted=" + wanted);
    if (!wanted && !resumeFocus) stopPlayback();
    super.onTaskRemoved(rootIntent);
  }

  public void onDestroy() {
    destroyed = true;
    rememberLocal();
    persist();
    connectivity.unregisterNetworkCallback(networkCallback);
    unregisterReceiver(noisy);
    cancelPlayer();
    ambientGeneration++;
    if (pendingAmbient != null && pendingAmbient != ambient) dispose(pendingAmbient);
    if (ambient != null) dispose(ambient);
    handler.removeCallbacksAndMessages(null);
    if (artworkLoader != null) artworkLoader.close();
    stopForeground(true);
    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(1);
    session.setActive(false);
    audio.abandonAudioFocusRequest(focus);
    session.release();
    sources.close();
    self = null;
    super.onDestroy();
  }
}
