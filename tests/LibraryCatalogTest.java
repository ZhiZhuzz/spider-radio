package radio.spider.app;
import org.json.*;import java.util.*;
public class LibraryCatalogTest {
 static void check(boolean condition){if(!condition)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  JSONArray imports=new JSONArray("[{\"uri\":\"file:///a\"},{\"uri\":\"file:///b\"}]");
  JSONArray all=LibraryCatalog.available(imports,new JSONArray(),123);
  check(all.length()==3&&all.getJSONObject(0).getBoolean("builtin"));
  Set<String> removed=LibraryCatalog.targets(all,new HashSet<>(Arrays.asList("demo.wav","file:///a","file:///outside")));
  check(removed.size()==2&&removed.contains("demo.wav")&&!removed.contains("file:///outside"));
  JSONArray persisted=new JSONArray(new JSONArray(removed).toString());
  check(LibraryCatalog.available(imports,persisted,123).length()==1);
  QueueRemoval queue=new QueueRemoval(all,0,removed);check(queue.currentRemoved&&queue.queue.length()==1);
  check(LibraryCatalog.available(new JSONArray(),new JSONArray("[\"demo.wav\"]"),0).length()==0);
  JSONArray restored=LibraryCatalog.available(imports,new JSONArray("[\"file:///a\"]"),123);check(restored.length()==2&&restored.getJSONObject(0).optString("uri").equals("demo.wav"));
  JSONArray duplicates=new JSONArray("[{\"uri\":\"file:///a\"},{\"uri\":\"file:///a\"},null,{}]");check(LibraryCatalog.available(duplicates,new JSONArray(),0).length()==2);
  System.out.println("PASS: source-aware catalog, built-in deletion targets, persisted tombstones, current queue deletion, empty library, restore, invalid IDs and duplicates.");
 }
}
