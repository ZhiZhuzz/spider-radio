package radio.spider.app;

import java.util.Locale;

/** Brand identifies help text, never proves that a system surface is available. */
final class MediaSurfacePolicy {
  static String family(String manufacturer, String brand) {
    String value =
        ((manufacturer == null ? "" : manufacturer) + " " + (brand == null ? "" : brand))
            .toLowerCase(Locale.ROOT);
    if (value.contains("vivo") || value.contains("iqoo")) return "vivo / iQOO";
    if (value.contains("xiaomi") || value.contains("redmi") || value.contains("poco"))
      return "小米 / Redmi";
    if (value.contains("oppo") || value.contains("oneplus") || value.contains("realme"))
      return "OPPO / OnePlus / realme";
    if (value.contains("honor")) return "荣耀";
    return "Android";
  }

  static String guidance(String family) {
    if (family.equals("vivo / iQOO"))
      return "锁屏与通知中心的音乐卡片由 OriginOS 排版。歌曲封面、信息和播放按钮可先在锁屏检查；原子岛是否展示仍需单独确认。";
    if (family.equals("小米 / Redmi"))
      return "使用澎湃 OS 系统媒体卡片展示封面、歌曲信息和播放控制。锁屏样式由系统决定；超级岛展示仍需按机型、系统版本实测。";
    if (family.equals("OPPO / OnePlus / realme")) return "当前使用系统媒体卡片。专属流体云卡片尚未接入；通知栏可用不代表流体云已适配。";
    if (family.equals("荣耀")) return "当前使用系统媒体卡片。专属胶囊服务尚未接入；通知栏可用不代表胶囊已适配。";
    return "当前使用 Android 系统媒体卡片，显示位置和样式由系统决定。";
  }

  static String toggle(boolean wanted) {
    return wanted ? "pause" : "resume";
  }

  // Avoid exposing a private filesystem path in metadata's public media ID.
  static String mediaId(boolean live, boolean noise, String station, String sound, String uri) {
    String value = noise ? "noise:" + sound : live ? "radio:" + station : "local:" + uri;
    return java.util
        .UUID
        .nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString();
  }
}
