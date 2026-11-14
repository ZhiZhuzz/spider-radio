package radio.spider.app;

import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

/** Explicit user-triggered HTTPS probe; does not replace or disrupt playback. */
final class StreamProbe {
  interface Factory {
    HttpURLConnection open(URL url) throws IOException;
  }

  static String inspect(String input) {
    return inspect(input, u -> (HttpURLConnection) u.openConnection());
  }

  static String inspect(String input, Factory factory) {
    long start = System.nanoTime(), deadline = start + TimeUnit.SECONDS.toNanos(8);
    HttpURLConnection connection = null;
    try {
      URL url = new URL(input);
      for (int redirects = 0; redirects <= 4; redirects++) {
        if (!"https".equalsIgnoreCase(url.getProtocol())) return "检测停止：仅允许 HTTPS 音频源";
        int remaining = (int) TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) throw new SocketTimeoutException();
        connection = factory.open(url);
        connection.setConnectTimeout(remaining);
        connection.setReadTimeout(remaining);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "SpiderRadio/0.7.0 (Android)");
        int code = connection.getResponseCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
          String to = connection.getHeaderField("Location");
          if (to == null) return "HTTP " + code + "，缺少跳转地址";
          URL next = new URL(url, to);
          connection.disconnect();
          connection = null;
          url = next;
          continue;
        }
        if (code < 200 || code >= 300) return url.getHost() + " · HTTP " + code;
        remaining = (int) TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) throw new SocketTimeoutException();
        connection.setReadTimeout(remaining);
        try (InputStream in = connection.getInputStream()) {
          int n = in.read(new byte[512]);
          return url.getHost()
              + " · HTTP "
              + code
              + " · "
              + String.valueOf(connection.getContentType())
              + " · "
              + (n > 0 ? "收到 " + n + " 字节" : "空响应")
              + " · "
              + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
              + " ms\n此结果仅验证连接和首段数据，不代表解码或持续播放成功。";
        }
      }
      return "检测停止：跳转次数过多";
    } catch (Exception e) {
      return "连接检测失败：" + e.getClass().getSimpleName() + "（请同时查看播放日志）";
    } finally {
      if (connection != null) connection.disconnect();
    }
  }
}
