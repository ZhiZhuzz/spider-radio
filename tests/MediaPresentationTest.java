package radio.spider.app;
public class MediaPresentationTest {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args){
  MediaPresentation idle=MediaPresentation.of(false,false,false,false,false,false,false,3,12000,60000);
  check(idle.status==MediaPresentation.Status.STOPPED&&!idle.seek&&!idle.skip,"Idle settings must not advertise active transport");
  MediaPresentation song=MediaPresentation.of(true,false,false,true,true,false,false,3,12000,60000);
  check(song.seek&&song.skip&&song.position==12000&&song.duration==60000&&song.speed==1,"Song progress and transport");
  MediaPresentation buffered=MediaPresentation.of(true,false,false,true,true,true,false,3,12000,60000);
  check(buffered.status==MediaPresentation.Status.BUFFERING&&buffered.speed==0,"System progress must not advance while buffering");
  MediaPresentation paused=MediaPresentation.of(true,false,false,true,false,false,false,1,25000,60000);
  check(paused.position==25000&&paused.speed==0&&paused.seek&&!paused.skip,"Single song paused controls");
  for(boolean noise:new boolean[]{false,true}){
   MediaPresentation stream=MediaPresentation.of(true,!noise,noise,true,true,false,false,3,9999,60000);
   check(!stream.seek&&stream.position==0&&stream.duration==-1,"No fabricated stream duration");check(stream.skip==!noise,"Noise never skips tracks");
  }
  MediaPresentation retry=MediaPresentation.of(true,true,false,false,false,true,true,3,0,0);
  check(retry.status==MediaPresentation.Status.BUFFERING,"Retry is still connecting, not terminal error");
  MediaPresentation error=MediaPresentation.of(true,true,false,false,false,false,true,3,0,0);
  check(error.status==MediaPresentation.Status.ERROR&&error.speed==0,"Terminal error must offer restart");
  check(MediaPresentation.of(true,false,false,true,false,false,false,2,Long.MAX_VALUE,60000).position==60000,"Seek clamp");
  check(!MediaPresentation.of(true,false,false,false,false,false,false,0,0,0).seek,"Empty library cannot seek");
  System.out.println("PASS media presentation: idle, local, buffering, pause, noise, live, retry/error, empty, position bounds.");
 }
}
