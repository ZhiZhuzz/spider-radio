package radio.spider.app;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Runs the production async ownership coordinator, without an Android decoder. */
public class SourceLoaderTest {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static void until(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!condition.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("timed out");Thread.sleep(5);}}
 public static void main(String[] args)throws Exception{
  BlockingQueue<Runnable> ui=new LinkedBlockingQueue<>();List<Integer> released=Collections.synchronizedList(new ArrayList<>()),ready=Collections.synchronizedList(new ArrayList<>());AtomicInteger failed=new AtomicInteger(),token=new AtomicInteger(1);
  SourceLoader<Integer> loader=new SourceLoader<>(ui::add,released::add);
  CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);
  long before=System.nanoTime();loader.load(1,p->{started.countDown();finish.await();},()->token.get()==1,ready::add,e->failed.incrementAndGet());
  check(System.nanoTime()-before<TimeUnit.MILLISECONDS.toNanos(100),"source setup blocked caller");check(started.await(2,TimeUnit.SECONDS),"setup not started");
  token.set(2);check(released.isEmpty(),"cancel released resource during source setup");finish.countDown();until(()->released.contains(1));check(ready.isEmpty(),"stale source delivered after cancel");
  loader.load(2,p->{},()->token.get()==2,ready::add,e->failed.incrementAndGet());Runnable queued=ui.poll(2,TimeUnit.SECONDS);check(queued!=null,"ready callback missing");token.set(3);queued.run();until(()->released.contains(2));check(ready.isEmpty(),"stale UI callback delivered after a later switch");
  loader.load(3,p->{throw new java.io.IOException();},()->true,ready::add,e->failed.incrementAndGet());ui.poll(2,TimeUnit.SECONDS).run();until(()->released.contains(3));check(failed.get()==1,"source failure not surfaced");
  loader.load(4,p->{},()->true,ready::add,e->failed.incrementAndGet());ui.poll(2,TimeUnit.SECONDS).run();check(ready.equals(Arrays.asList(4)),"current source not delivered");check(!released.contains(4),"owned resource released prematurely");loader.release(4);until(()->released.contains(4));
  CountDownLatch blockers=new CountDownLatch(2),releaseBlockers=new CountDownLatch(1);AtomicBoolean valid=new AtomicBoolean(true);
  for(int n=5;n<7;n++)loader.load(n,p->{blockers.countDown();releaseBlockers.await();},valid::get,ready::add,e->failed.incrementAndGet());check(blockers.await(2,TimeUnit.SECONDS),"workers missing");
  for(int n=7;n<10;n++)loader.load(n,p->{},valid::get,ready::add,e->failed.incrementAndGet());check(failed.get()==2,"bounded overload not reported");until(()->released.contains(9));
  valid.set(false);loader.close();releaseBlockers.countDown();until(()->released.size()==9);check(new HashSet<>(released).size()==9,"duplicate resource release");check(ready.size()==1,"queued source escaped shutdown");
  System.out.println("PASS: nonblocking setup, no release during setup, cancellation before/after dispatch, current handoff, source error, bounded overload, shutdown cleanup (9 resources).");
 }
}
