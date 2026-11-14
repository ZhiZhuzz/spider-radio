package radio.spider.app;

/** Display-only metadata: never guess an artist from a filename or change a URI. */
final class TrackInfo {
  final String title, artist, album;
  final long duration;

  TrackInfo(String title, String artist, String album, String filename, long duration) {
    String tagged = clean(title);
    this.title = tagged.isEmpty() ? filenameTitle(filename) : tagged;
    this.artist = clean(artist);
    this.album = clean(album);
    this.duration = Math.max(0, duration);
  }

  String subtitle() {
    return !artist.isEmpty() ? artist : !album.isEmpty() ? album : "本地音乐";
  }

  static String clean(String value) {
    if (value == null) return "";
    String text =
        value
            .replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]", " ")
            .trim()
            .replaceAll("\\s+", " ");
    if (text.equalsIgnoreCase("<unknown>")
        || text.equalsIgnoreCase("unknown")
        || text.equalsIgnoreCase("null")) return "";
    return text.length() > 512 ? text.substring(0, 512) : text;
  }

  static String filenameTitle(String filename) {
    String value =
        clean(filename)
            .replaceFirst(
                "(?i)\\.(mp3|flac|m4a|aac|ogg|oga|opus|wav|wave|wma|aiff|aif|amr|mp4|audio)$", "")
            .trim();
    return value.isEmpty() ? "本地音乐" : value;
  }
}
