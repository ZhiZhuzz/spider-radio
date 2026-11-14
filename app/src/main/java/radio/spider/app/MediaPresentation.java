package radio.spider.app;

/** One policy for the system card: live/noise never advertise a seek bar. */
final class MediaPresentation {
  enum Status {
    STOPPED,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR
  }

  final Status status;
  final boolean skip, seek;
  final long position, duration;
  final float speed;

  private MediaPresentation(
      Status status, boolean skip, boolean seek, long position, long duration, float speed) {
    this.status = status;
    this.skip = skip;
    this.seek = seek;
    this.position = position;
    this.duration = duration;
    this.speed = speed;
  }

  static MediaPresentation of(
      boolean visible,
      boolean live,
      boolean noise,
      boolean prepared,
      boolean playing,
      boolean buffering,
      boolean error,
      int count,
      long position,
      long duration) {
    boolean local = !live && !noise;
    Status state =
        !visible
            ? Status.STOPPED
            : buffering
                ? Status.BUFFERING
                : error ? Status.ERROR : playing ? Status.PLAYING : Status.PAUSED;
    long length = local ? Math.max(0, duration) : -1;
    return new MediaPresentation(
        state,
        visible && !noise && (live || count > 1),
        visible && local && prepared && length > 0,
        local ? Math.max(0, length > 0 ? Math.min(position, length) : position) : 0,
        length,
        state == Status.PLAYING ? 1f : 0f);
  }
}
