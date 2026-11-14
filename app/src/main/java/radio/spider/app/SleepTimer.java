package radio.spider.app;

/** 0 is an explicit cancellation; otherwise whole minutes from 1 to 720. */
final class SleepTimer {
  static int parse(String value) {
    if (value == null || !value.matches("0|[1-9][0-9]{0,2}"))
      throw new IllegalArgumentException("请输入1至720的整数分钟");
    int minutes = Integer.parseInt(value);
    if (minutes > 720) throw new IllegalArgumentException("请输入1至720的整数分钟");
    return minutes;
  }
}
