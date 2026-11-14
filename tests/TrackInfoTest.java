package radio.spider.app;
public final class TrackInfoTest {
 private static void eq(Object expected,Object actual){if(!expected.equals(actual))throw new AssertionError("expected="+expected+" actual="+actual);}
 public static void main(String[] args){
  TrackInfo tagged=new TrackInfo("Tagged song","Artist","Album","wrong.mp3",180000);
  eq("Tagged song",tagged.title);eq("Artist",tagged.subtitle());eq(180000L,tagged.duration);
  TrackInfo old=new TrackInfo(null,"<unknown>","unknown","我也曾想过一了百了.MP3",-1);
  eq("我也曾想过一了百了",old.title);eq("本地音乐",old.subtitle());eq(0L,old.duration);
  eq("Album",new TrackInfo(null,null,"Album","song.flac",1).subtitle());
  eq("literal.mp3",new TrackInfo("literal.mp3",null,null,"file.mp3",1).title);
  eq("A - B.live",TrackInfo.filenameTitle("A - B.live.mp3"));
  eq("",new TrackInfo(null,null,null,"A - B.mp3",1).artist);
  eq("本地音乐",TrackInfo.filenameTitle(".mp3"));eq("Demo",TrackInfo.filenameTitle("Demo"));
  eq("Song Title",TrackInfo.clean("Song\u202e\nTitle"));
  eq("中文・日本語・한국어",TrackInfo.filenameTitle("中文・日本語・한국어.flac"));
  eq("song.xyz",TrackInfo.filenameTitle("song.xyz"));
  System.out.println("TrackInfoTest passed");
 }
}
