package radio.spider.app;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import java.io.*;
import java.util.concurrent.*;

/** Bounded asynchronous local metadata/cover loading. Only the latest request wins. */
final class MediaArtwork implements AutoCloseable {
  interface Ready {
    void accept(Result result);
  }

  static final class Result {
    final Bitmap bitmap;
    final TrackInfo info;
    final String origin;

    Result(Bitmap bitmap, TrackInfo info, String origin) {
      this.bitmap = bitmap;
      this.info = info;
      this.origin = origin;
    }
  }

  private final Context context;
  private final Handler main;
  private final ThreadPoolExecutor worker =
      new ThreadPoolExecutor(
          1,
          1,
          30,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<Runnable>(1),
          new ThreadPoolExecutor.DiscardOldestPolicy());
  private int generation;
  private String key = "";
  private boolean closed;

  MediaArtwork(Context context, Handler main) {
    this.context = context;
    this.main = main;
  }

  boolean load(int scene, String source, String filename, Ready ready) {
    if (closed) return false;
    File custom = new File(context.getFilesDir(), "background-" + scene + ".jpg");
    String next =
        source
            + "|"
            + filename
            + "|"
            + scene
            + ":"
            + (custom.exists() ? custom.lastModified() + ":" + custom.length() : "default");
    if (next.equals(key)) return false;
    key = next;
    final int token = ++generation;
    worker.execute(
        () -> {
          Bitmap cover = null;
          TrackInfo info = null;
          String origin = "scene";
          if (!source.isEmpty()) {
            MediaMetadataRetriever reader = new MediaMetadataRetriever();
            try {
              if (source.startsWith("file:")) reader.setDataSource(Uri.parse(source).getPath());
              else if (source.startsWith("content:"))
                reader.setDataSource(context, Uri.parse(source));
              else if (!source.contains(":"))
                try (android.content.res.AssetFileDescriptor f =
                    context.getAssets().openFd(source)) {
                  reader.setDataSource(f.getFileDescriptor(), f.getStartOffset(), f.getLength());
                }
              else throw new IOException("Not a local source");
              info = readTags(reader, filename);
              byte[] bytes = reader.getEmbeddedPicture();
              if (bytes != null && bytes.length <= 12 * 1024 * 1024) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                sample(options);
                cover = square(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options));
                if (cover != null) origin = "embedded";
              }
            } catch (Exception | OutOfMemoryError ignored) {
            } finally {
              try {
                reader.release();
              } catch (Exception ignored) {
              }
            }
          }
          if (cover == null)
            try {
              cover = decodeScene(scene, custom);
            } catch (Exception | OutOfMemoryError ignored) {
            }
          final Result result = new Result(cover, info, cover == null ? "placeholder" : origin);
          main.post(
              () -> {
                if (closed || token != generation) {
                  if (result.bitmap != null) result.bitmap.recycle();
                  return;
                }
                ready.accept(result);
              });
        });
    return true;
  }

  static TrackInfo readTags(MediaMetadataRetriever reader, String filename) {
    long duration = 0;
    try {
      duration = Long.parseLong(extract(reader, MediaMetadataRetriever.METADATA_KEY_DURATION));
    } catch (Exception ignored) {
    }
    return new TrackInfo(
        extract(reader, MediaMetadataRetriever.METADATA_KEY_TITLE),
        extract(reader, MediaMetadataRetriever.METADATA_KEY_ARTIST),
        extract(reader, MediaMetadataRetriever.METADATA_KEY_ALBUM),
        filename,
        duration);
  }

  private static String extract(MediaMetadataRetriever reader, int key) {
    try {
      return reader.extractMetadata(key);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private InputStream open(int scene, File custom) throws IOException {
    return custom.exists()
        ? new FileInputStream(custom)
        : context
            .getAssets()
            .open(new String[] {"city", "snow", "train", "rain"}[scene] + "-v2.png");
  }

  private static void sample(BitmapFactory.Options options) {
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 768)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
  }

  private static Bitmap square(Bitmap source) {
    if (source == null) return null;
    try {
      Bitmap out = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(out);
      int side = Math.min(source.getWidth(), source.getHeight()),
          x = (source.getWidth() - side) / 2,
          y = (source.getHeight() - side) / 2;
      canvas.drawBitmap(
          source,
          new Rect(x, y, x + side, y + side),
          new Rect(0, 0, 384, 384),
          new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
      return out;
    } finally {
      source.recycle();
    }
  }

  private Bitmap decodeScene(int scene, File custom) throws IOException {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    try (InputStream in = open(scene, custom)) {
      BitmapFactory.decodeStream(in, null, options);
    }
    sample(options);
    Bitmap out;
    try (InputStream in = open(scene, custom)) {
      out = square(BitmapFactory.decodeStream(in, null, options));
    }
    if (out != null) {
      Canvas canvas = new Canvas(out);
      Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      paint.setShader(
          new LinearGradient(0, 210, 0, 384, 0x00111a28, 0xcc111a28, Shader.TileMode.CLAMP));
      canvas.drawRect(0, 210, 384, 384, paint);
      paint.setShader(null);
      paint.setColor(0xffefd5a0);
      paint.setTextSize(20);
      paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      canvas.drawText("SPIDER RADIO", 24, 352, paint);
    }
    return out;
  }

  static Bitmap placeholder() {
    Bitmap out = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setShader(
        new LinearGradient(0, 0, 384, 384, 0xff263b52, 0xff121923, Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, 384, 384, paint);
    paint.setShader(null);
    paint.setColor(0x35efd5a0);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(2);
    for (int radius = 42; radius <= 122; radius += 20) canvas.drawCircle(192, 174, radius, paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xffefd5a0);
    canvas.drawCircle(192, 174, 13, paint);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(23);
    canvas.drawText("SPIDER RADIO", 192, 340, paint);
    return out;
  }

  public void close() {
    closed = true;
    generation++;
    worker.shutdownNow();
  }
}
