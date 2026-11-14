package radio.spider.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.*;
import android.os.*;
import org.json.*;

final class SystemPlaybackInfo {
  static JSONObject read(Context c) {
    JSONObject o = new JSONObject();
    try {
      NotificationManager nm =
          (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel channel = nm.getNotificationChannel("playback");
      PowerManager power = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
      ConnectivityManager cm =
          (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkCapabilities net = cm.getNetworkCapabilities(cm.getActiveNetwork());
      String family = MediaSurfacePolicy.family(Build.MANUFACTURER, Build.BRAND);
      o.put("mediaFamily", family)
          .put("mediaGuidance", MediaSurfacePolicy.guidance(family))
          .put("mediaRoute", "Android MediaSession + MediaStyle")
          .put("islandVisibility", "unknown")
          .put("systemBuild", Build.DISPLAY)
          .put(
              "mediaSession",
              RadioService.self == null ? "媒体会话尚未启动" : RadioService.self.mediaSnapshot);
      RadioService service = RadioService.self;
      if (service != null && service.session != null) {
        android.media.MediaMetadata metadata = service.session.getController().getMetadata();
        android.media.session.PlaybackState playback =
            service.session.getController().getPlaybackState();
        o.put("sessionActive", service.session.isActive())
            .put(
                "metadataDuration",
                metadata == null
                    ? -1
                    : metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION))
            .put(
                "metadataArtwork",
                metadata != null
                    && metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                        != null)
            .put("publishedActions", playback == null ? 0 : playback.getActions());
      }
      for (android.service.notification.StatusBarNotification active :
          nm.getActiveNotifications()) {
        if (active.getId() == 1) {
          Notification notification = active.getNotification();
          o.put(
                  "mediaTokenAttached",
                  notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION))
              .put(
                  "notificationTemplate",
                  notification.extras.getString(Notification.EXTRA_TEMPLATE, "unknown"));
        }
      }
      o.put(
          "keyguardLocked",
          ((KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked());
      o.put("device", Build.MANUFACTURER + " " + Build.MODEL)
          .put("android", Build.VERSION.RELEASE)
          .put("sdk", Build.VERSION.SDK_INT)
          .put("version", c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName);
      o.put("notifications", nm.areNotificationsEnabled())
          .put(
              "channelEnabled",
              channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE)
          .put("batteryExempt", power.isIgnoringBatteryOptimizations(c.getPackageName()));
      o.put(
          "backgroundRestricted",
          Build.VERSION.SDK_INT >= 28
              && ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE))
                  .isBackgroundRestricted());
      o.put(
              "network",
              net == null
                  ? "离线"
                  : net.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                      ? "Wi-Fi"
                      : net.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "移动网络" : "其他网络")
          .put(
              "validated",
              net != null && net.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
      JSONArray routes = new JSONArray();
      for (AudioDeviceInfo d :
          ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE))
              .getDevices(AudioManager.GET_DEVICES_OUTPUTS)) routes.put(d.getType());
      o.put("outputTypes", routes);
    } catch (Exception e) {
      try {
        o.put("readError", e.getClass().getSimpleName());
      } catch (Exception ignored) {
      }
    }
    return o;
  }
}
