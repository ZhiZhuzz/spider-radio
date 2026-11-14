package radio.spider.app;

import java.util.*;

/** A shuffled pass visits every other queue entry once; previous follows listening history. */
final class PlaybackOrder {
  private final Random random;
  private final ArrayList<Integer> remaining = new ArrayList<>(), history = new ArrayList<>();
  private int cursor = -1, size = -1;

  PlaybackOrder() {
    this(new Random());
  }

  PlaybackOrder(Random random) {
    this.random = random;
  }

  void reset() {
    remaining.clear();
    history.clear();
    cursor = -1;
    size = -1;
  }

  int move(int count, int current, int direction) {
    if (count <= 1) return 0;
    if (size != count || cursor < 0 || history.get(cursor) != current) {
      reset();
      size = count;
      history.add(current);
      cursor = 0;
      refill(count, current);
    }
    if (direction < 0) {
      if (cursor > 0) return history.get(--cursor);
      return current;
    }
    if (cursor + 1 < history.size()) return history.get(++cursor);
    if (remaining.isEmpty()) refill(count, current);
    int next = remaining.remove(remaining.size() - 1);
    history.add(next);
    cursor++;
    if (history.size() > 500) {
      history.remove(0);
      cursor--;
    }
    return next;
  }

  private void refill(int count, int current) {
    for (int i = 0; i < count; i++) if (i != current) remaining.add(i);
    Collections.shuffle(remaining, random);
  }
}
