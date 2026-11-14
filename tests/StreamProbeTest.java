package radio.spider.app;
import java.io.*;import java.net.*;import java.util.*;
public class StreamProbeTest {
 static class Connection extends HttpURLConnection {
  int code;String location;boolean closed;int bytes;Connection(URL u,int c,String l){super(u);code=c;location=l;}
  public void disconnect(){closed=true;}public boolean usingProxy(){return false;}public void connect(){}
  public int getResponseCode(){return code;}public String getHeaderField(String key){return location;}public String getContentType(){return "audio/mpeg";}
  public InputStream getInputStream(){return new ByteArrayInputStream(new byte[5000]){public synchronized int read(byte[] b,int off,int len){int n=super.read(b,off,len);bytes+=Math.max(0,n);return n;}};}
 }
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args){
  List<Connection> all=new ArrayList<>();String ok=StreamProbe.inspect("https://example.test/live",u->{Connection c=new Connection(u,all.isEmpty()?302:200,"/audio");all.add(c);return c;});
  check(ok.contains("HTTP 200")&&ok.contains("512"),"Successful redirect and bounded first data");check(all.size()==2,"One redirect");for(Connection c:all)check(c.closed&&c.getConnectTimeout()>0&&c.getReadTimeout()>0&&c.bytes<=512,"Cleanup, deadlines, bounded read");
  check(StreamProbe.inspect("https://example.test/live",u->new Connection(u,403,null)).contains("403"),"HTTP failure visible");
  check(StreamProbe.inspect("https://example.test/live",u->new Connection(u,302,"http://example.test/unsafe")).contains("HTTPS"),"No downgrade to plaintext");
  check(StreamProbe.inspect("https://example.test/live",u->new Connection(u,302,"/loop")).contains("跳转次数过多"),"Redirect cap");
  check(StreamProbe.inspect("https://example.test/live",u->{throw new SocketTimeoutException();}).contains("SocketTimeoutException"),"Timeout is diagnosable");
  System.out.println("PASS stream probe: bounded HTTPS redirects, status, read, timeouts, cleanup, no HTTP downgrade.");
 }
}
