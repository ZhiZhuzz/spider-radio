package radio.spider.app;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.media.*;
import android.media.session.*;
import android.os.SystemClock;

/** Publishes an immutable playback view. It does not own players or change playback state. */
final class SystemMediaOutput {
  static final class View {
    final MediaPresentation presentation;
    final boolean live, noiseOnly, wanted, buffering, waitingNetwork;
    final String uri, title, subtitle, sourceLabel, stationId, soundTitle, error;
    final TrackInfo track;
    final Bitmap artwork;
    final String artworkOrigin;
    final int bufferPercent;

    View(
        MediaPresentation presentation,
        boolean live,
        boolean noiseOnly,
        boolean wanted,
        boolean buffering,
        boolean waitingNetwork,
        String uri,
        String title,
        String subtitle,
        String sourceLabel,
        String stationId,
        String soundTitle,
        String error,
        TrackInfo track,
        Bitmap artwork,
        String artworkOrigin,
        int bufferPercent) {
      this.presentation = presentation;
      this.live = live;
      this.noiseOnly = noiseOnly;
      this.wanted = wanted;
      this.buffering = buffering;
      this.waitingNetwork = waitingNetwork;
      this.uri = uri;
      this.title = title;
      this.subtitle = subtitle;
      this.sourceLabel = sourceLabel;
      this.stationId = stationId;
      this.soundTitle = soundTitle;
      this.error = error;
      this.track = track;
      this.artwork = artwork;
      this.artworkOrigin = artworkOrigin;
      this.bufferPercent = bufferPercent;
    }
  }

  private final Context context;
  private final MediaSession session;
  private String metadataKey = "";
  long lastUpdate = 0;

  SystemMediaOutput(Context context, MediaSession session) {
    this.context = context;
    this.session = session;
  }

  void invalidateMetadata() {
    metadataKey = "";
  }

  PendingIntent openActivity() {
    return PendingIntent.getActivity(
        context,
        0,
        new Intent(context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  private PendingIntent action(String action) {
    Intent intent = new Intent(context, RadioService.class).setAction(action).putExtra("value", "");
    int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    return action.equals("pause") || action.equals("dismiss")
        ? PendingIntent.getService(context, action.hashCode(), intent, flags)
        : PendingIntent.getForegroundService(context, action.hashCode(), intent, flags);
  }

  Notification notification(View v) {
    MediaPresentation view = v.presentation;
    Notification.Builder b =
        new Notification.Builder(context, "playback")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(v.title)
            .setContentText(v.buffering ? (v.waitingNetwork ? "等待网络恢复" : "正在连接…") : v.subtitle)
            .setContentIntent(openActivity())
            .setDeleteIntent(action("dismiss"))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(v.wanted)
            .setColor(0xffefd5a0)
            .setColorized(true);
    if (v.artwork != null) b.setLargeIcon(v.artwork);
    if (view.skip) b.addAction(R.drawable.ic_previous, v.live ? "上一台" : "上一首", action("prev"));
    b.addAction(
        v.wanted ? R.drawable.ic_pause : R.drawable.ic_play,
        v.wanted ? "暂停" : "播放",
        action(v.wanted ? "pause" : "resume"));
    if (view.skip) b.addAction(R.drawable.ic_next, v.live ? "下一台" : "下一首", action("next"));
    return b.setStyle(
            new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(view.skip ? new int[] {0, 1, 2} : new int[] {0}))
        .build();
  }

  String updateState(View v) {
    lastUpdate = SystemClock.elapsedRealtime();
    MediaPresentation view = v.presentation;
    long actions =
        PlaybackState.ACTION_PLAY
            | PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_PLAY_PAUSE
            | PlaybackState.ACTION_STOP;
    if (view.skip)
      actions |= PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
    if (view.seek) actions |= PlaybackState.ACTION_SEEK_TO;
    int state =
        view.status == MediaPresentation.Status.STOPPED
            ? PlaybackState.STATE_STOPPED
            : view.status == MediaPresentation.Status.BUFFERING
                ? PlaybackState.STATE_BUFFERING
                : view.status == MediaPresentation.Status.PLAYING
                    ? PlaybackState.STATE_PLAYING
                    : view.status == MediaPresentation.Status.ERROR
                        ? PlaybackState.STATE_ERROR
                        : PlaybackState.STATE_PAUSED;
    PlaybackState.Builder pb =
        new PlaybackState.Builder()
            .setActions(actions)
            .setState(state, view.position, view.speed, lastUpdate);
    if (view.duration > 0)
      pb.setBufferedPosition(
          !v.live && !v.noiseOnly ? view.duration : view.duration * v.bufferPercent / 100);
    if (view.status == MediaPresentation.Status.ERROR) pb.setErrorMessage(v.error);
    session.setPlaybackState(pb.build());
    return "surface=standard-media; islandVisibility=unknown; state="
        + view.status
        + "; seek="
        + view.seek
        + "; skip="
        + view.skip
        + "; position="
        + view.position
        + "; duration="
        + view.duration
        + "; artwork="
        + (v.artwork != null)
        + "; artworkSource="
        + v.artworkOrigin
        + "; artistTag="
        + (v.track != null && !v.track.artist.isEmpty())
        + "; updated="
        + lastUpdate;
  }

  void publishMetadata(View v) {
    MediaPresentation view = v.presentation;
    String key =
        v.uri
            + "|"
            + v.title
            + "|"
            + v.subtitle
            + "|"
            + (v.track == null ? "" : v.track.album)
            + "|"
            + v.sourceLabel
            + "|"
            + view.duration;
    if (key.equals(metadataKey)) return;
    metadataKey = key;
    MediaMetadata.Builder m =
        new MediaMetadata.Builder()
            .putString(
                MediaMetadata.METADATA_KEY_MEDIA_ID,
                MediaSurfacePolicy.mediaId(v.live, v.noiseOnly, v.stationId, v.soundTitle, v.uri))
            .putString(MediaMetadata.METADATA_KEY_TITLE, v.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, v.title)
            .putString(
                MediaMetadata.METADATA_KEY_ARTIST,
                v.live || v.noiseOnly ? "蜘蛛电台" : v.track == null ? "" : v.track.artist)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, v.subtitle)
            .putString(
                MediaMetadata.METADATA_KEY_ALBUM,
                v.live || v.noiseOnly ? "蜘蛛电台" : v.track == null ? "" : v.track.album)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, v.sourceLabel);
    if (view.duration >= 0) m.putLong(MediaMetadata.METADATA_KEY_DURATION, view.duration);
    if (v.artwork != null)
      m.putBitmap(MediaMetadata.METADATA_KEY_ART, v.artwork)
          .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, v.artwork)
          .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, v.artwork);
    session.setMetadata(m.build());
  }
}
