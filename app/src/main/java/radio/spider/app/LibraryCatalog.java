package radio.spider.app;

import java.util.*;
import org.json.*;

/** One source-aware catalog for the UI and the playback fallback. No file deletion here. */
final class LibraryCatalog {
  static final String DEMO = "demo.wav";

  static Set<String> removed(JSONArray entries) {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < entries.length(); i++) ids.add(entries.optString(i));
    return ids;
  }

  static JSONArray available(JSONArray imported, JSONArray deleted, long demoSize)
      throws JSONException {
    Set<String> gone = removed(deleted), seen = new HashSet<>();
    JSONArray result = new JSONArray();
    if (!gone.contains(DEMO)) {
      result.put(
          new JSONObject()
              .put("uri", DEMO)
              .put("title", "蓝色时差")
              .put("builtin", true)
              .put("size", demoSize));
      seen.add(DEMO);
    }
    for (int i = 0; i < imported.length(); i++) {
      JSONObject song = imported.optJSONObject(i);
      if (song == null) continue;
      String id = song.optString("uri");
      if (!id.isEmpty() && !gone.contains(id) && seen.add(id)) result.put(song);
    }
    return result;
  }

  static Set<String> targets(JSONArray catalog, Set<String> requested) {
    Set<String> result = new HashSet<>();
    for (int i = 0; i < catalog.length(); i++) {
      JSONObject song = catalog.optJSONObject(i);
      if (song != null && requested.contains(song.optString("uri")))
        result.add(song.optString("uri"));
    }
    return result;
  }
}
