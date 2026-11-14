package radio.spider.app;

import java.util.concurrent.*;
import java.util.function.*;

/**
 * Transfers a resource to its owner only after blocking setup finishes. Cancellation invalidates a
 * token; it never releases a resource during setup. A bounded worker pool keeps a stalled platform
 * decoder from spawning threads.
 */
final class SourceLoader<T> implements AutoCloseable {
  interface Setup<T> {
    void run(T resource) throws Exception;
  }

  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2));
  private final ExecutorService releases = Executors.newSingleThreadExecutor();
  private final Consumer<Runnable> dispatch;
  private final Consumer<T> releaser;

  SourceLoader(Consumer<Runnable> dispatch, Consumer<T> releaser) {
    this.dispatch = dispatch;
    this.releaser = releaser;
  }

  void release(T resource) {
    if (resource == null) return;
    Runnable task = () -> releaser.accept(resource);
    try {
      releases.execute(task);
    } catch (RejectedExecutionException e) {
      task.run();
    }
  }

  void load(
      T resource,
      Setup<T> setup,
      BooleanSupplier valid,
      Consumer<T> ready,
      Consumer<Exception> failure) {
    Runnable job =
        () -> {
          if (!valid.getAsBoolean()) {
            release(resource);
            return;
          }
          try {
            setup.run(resource);
          } catch (Exception e) {
            release(resource);
            dispatch.accept(
                () -> {
                  if (valid.getAsBoolean()) failure.accept(e);
                });
            return;
          }
          if (!valid.getAsBoolean()) {
            release(resource);
            return;
          }
          dispatch.accept(
              () -> {
                if (!valid.getAsBoolean()) {
                  release(resource);
                  return;
                }
                // Ownership transfers here. The owner handles preparation and its errors.
                ready.accept(resource);
              });
        };
    try {
      workers.execute(job);
    } catch (RejectedExecutionException e) {
      release(resource);
      if (valid.getAsBoolean()) failure.accept(e);
    }
  }

  public void close() {
    workers.shutdown();
    releases.shutdown();
  }
}
