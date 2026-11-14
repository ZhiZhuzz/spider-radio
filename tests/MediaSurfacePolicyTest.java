package radio.spider.app;
public class MediaSurfacePolicyTest {
 static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  eq(MediaSurfacePolicy.family("vivo","iQOO"),"vivo / iQOO");
  eq(MediaSurfacePolicy.family("Xiaomi","Redmi"),"小米 / Redmi");
  eq(MediaSurfacePolicy.family("OPLUS","OnePlus"),"OPPO / OnePlus / realme");
  eq(MediaSurfacePolicy.family("realme","realme"),"OPPO / OnePlus / realme");
  eq(MediaSurfacePolicy.family("HONOR","HONOR"),"荣耀");
  eq(MediaSurfacePolicy.family(null,null),"Android");
  eq(MediaSurfacePolicy.toggle(true),"pause"); // buffering with intent to play must pause
  eq(MediaSurfacePolicy.toggle(false),"resume");
  String local=MediaSurfacePolicy.mediaId(false,false,"s","n","/private/song.audio");
  eq(local,MediaSurfacePolicy.mediaId(false,false,"other","other","/private/song.audio"));
  if(local.contains("private")||local.equals(MediaSurfacePolicy.mediaId(true,false,"s","n","/private/song.audio")))throw new AssertionError("media identity");
  if(!MediaSurfacePolicy.guidance("荣耀").contains("尚未接入"))throw new AssertionError("false support claim");
  System.out.println("PASS brand hints, unknown fallback, buffering toggle intent, stable private media identity");
 }
}
