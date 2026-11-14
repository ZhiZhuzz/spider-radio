package radio.spider.app;
import java.util.*;import org.json.*;
public class LibraryLogicTest{
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  for(int seed=0;seed<100;seed++){PlaybackOrder order=new PlaybackOrder(new Random(seed));Set<Integer> visited=new HashSet<>();int current=2;visited.add(current);for(int n=0;n<8;n++){int previous=current;current=order.move(9,current,1);check(current!=previous);check(visited.add(current));int back=order.move(9,current,-1);check(back==previous);check(order.move(9,back,1)==current);}check(visited.size()==9);int next=order.move(9,current,1);check(next!=current);order.reset();check(order.move(1,0,1)==0);}
  JSONArray q=new JSONArray("[{\"uri\":\"a\"},{\"uri\":\"b\"},{\"uri\":\"c\"}]");QueueRemoval r=new QueueRemoval(q,1,"a");check(r.index==0&&!r.currentRemoved&&r.queue.getJSONObject(0).getString("uri").equals("b"));r=new QueueRemoval(q,1,"b");check(r.currentRemoved&&r.index==1&&r.queue.getJSONObject(r.index).getString("uri").equals("c"));r=new QueueRemoval(q,2,"c");check(r.index==1&&r.currentRemoved);r=new QueueRemoval(q,1,"absent");check(!r.removed&&r.index==1&&r.queue.length()==3);r=new QueueRemoval(new JSONArray("[{\"uri\":\"a\"}]"),0,"a");check(r.queue.length()==0&&r.index==0&&r.removesPreset(false,false)&&!r.removesPreset(true,false)&&!r.removesPreset(false,true));r=new QueueRemoval(new JSONArray("[{\"uri\":\"a\"},{\"uri\":\"b\"},{\"uri\":\"a\"}]"),2,"a");check(r.queue.length()==1&&r.index==0&&r.currentRemoved);
  Set<String> batch=new HashSet<>(Arrays.asList("a","c"));r=new QueueRemoval(q,1,batch);check(!r.currentRemoved&&r.index==0&&r.queue.length()==1&&r.queue.getJSONObject(0).getString("uri").equals("b"));r=new QueueRemoval(q,2,batch);check(r.currentRemoved&&r.index==0);r=new QueueRemoval(q,1,new HashSet<>(Arrays.asList("a","b","c")));check(r.currentRemoved&&r.queue.length()==0&&r.index==0);
  System.out.println("PASS: 100 shuffle seeds, history/back, cycle boundary, singleton; deletion before/current/last/absent/duplicate/only song and preset retention.");
 }
}
