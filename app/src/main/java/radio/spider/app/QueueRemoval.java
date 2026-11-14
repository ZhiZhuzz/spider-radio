package radio.spider.app;

import org.json.*;

/** Shared by active playback and saved presets so deletions retain the same selection. */
final class QueueRemoval {
  final JSONArray queue = new JSONArray();
  final int index;
  boolean removed = false, currentRemoved = false;

  QueueRemoval(JSONArray original, int current, String uri) {
    this(original, current, java.util.Collections.singleton(uri));
  }

  QueueRemoval(JSONArray original, int current, java.util.Set<String> uris) {
    int before = 0;
    for (int n = 0; n < original.length(); n++) {
      JSONObject item = original.optJSONObject(n);
      if (item == null) continue;
      if (uris.contains(item.optString("uri"))) {
        removed = true;
        if (n == current) currentRemoved = true;
      } else {
        if (n < current) before++;
        queue.put(item);
      }
    }
    index = Math.max(0, Math.min(before, queue.length() - 1));
  }

  boolean removesPreset(boolean live, boolean noiseOnly) {
    return removed && queue.length() == 0 && !live && !noiseOnly;
  }
}
