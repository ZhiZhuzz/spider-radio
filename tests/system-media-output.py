#!/usr/bin/env python3
"""Run the actual publisher against narrow Android API fakes.
Checks published data/intents; not Android notification rendering or lifecycle.
"""
from pathlib import Path
import os, subprocess, tempfile
root = Path(__file__).resolve().parents[1]
support = {
 'android/content/Context.java': 'package android.content; public class Context {}',
 'android/content/Intent.java': '''package android.content; public class Intent {
 public static final int FLAG_ACTIVITY_SINGLE_TOP=1,FLAG_ACTIVITY_CLEAR_TOP=2;
 public final Class<?> target;public String action,value;public int flags;
 public Intent(Context c,Class<?> t){target=t;}public Intent addFlags(int f){flags|=f;return this;}
 public Intent setAction(String a){action=a;return this;}public Intent putExtra(String k,String v){value=v;return this;}}
 ''',
 'android/app/PendingIntent.java': '''package android.app;import android.content.*;
 public class PendingIntent {public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;
 public final String kind;public final Intent intent;public final int flags;
 private PendingIntent(String k,Intent i,int f){kind=k;intent=i;flags=f;}
 public static PendingIntent getActivity(Context c,int n,Intent i,int f){return new PendingIntent("activity",i,f);}
 public static PendingIntent getService(Context c,int n,Intent i,int f){return new PendingIntent("service",i,f);}
 public static PendingIntent getForegroundService(Context c,int n,Intent i,int f){return new PendingIntent("foreground",i,f);}}
 ''',
 'android/graphics/Bitmap.java': 'package android.graphics;public class Bitmap {}',
 'android/os/SystemClock.java': 'package android.os;public class SystemClock {public static long elapsedRealtime(){return 12345L;}}',
 'android/media/session/MediaSession.java': '''package android.media.session;import android.media.*;
 public class MediaSession {public MediaMetadata metadata;public PlaybackState state;public int writes;
 public Object getSessionToken(){return this;}public void setMetadata(MediaMetadata m){metadata=m;writes++;}public void setPlaybackState(PlaybackState p){state=p;}}
 ''',
 'android/media/session/PlaybackState.java': '''package android.media.session;public class PlaybackState {
 public static final long ACTION_PLAY=1,ACTION_PAUSE=2,ACTION_PLAY_PAUSE=4,ACTION_STOP=8,ACTION_SKIP_TO_NEXT=16,ACTION_SKIP_TO_PREVIOUS=32,ACTION_SEEK_TO=64;
 public static final int STATE_STOPPED=1,STATE_BUFFERING=2,STATE_PLAYING=3,STATE_ERROR=4,STATE_PAUSED=5;
 public long actions,position,updated,buffered;public int state;public float speed;public String error;
 public static class Builder {private PlaybackState p=new PlaybackState();
 public Builder setActions(long a){p.actions=a;return this;}public Builder setState(int s,long pos,float speed,long time){p.state=s;p.position=pos;p.speed=speed;p.updated=time;return this;}
 public Builder setBufferedPosition(long b){p.buffered=b;return this;}public Builder setErrorMessage(String e){p.error=e;return this;}public PlaybackState build(){return p;}}}
 ''',
 'android/media/MediaMetadata.java': '''package android.media;import android.graphics.*;import java.util.*;
 public class MediaMetadata {
 public static final String METADATA_KEY_MEDIA_ID="id",METADATA_KEY_TITLE="title",METADATA_KEY_DISPLAY_TITLE="displayTitle",METADATA_KEY_ARTIST="artist",METADATA_KEY_DISPLAY_SUBTITLE="subtitle",METADATA_KEY_ALBUM="album",METADATA_KEY_DISPLAY_DESCRIPTION="description",METADATA_KEY_DURATION="duration",METADATA_KEY_ART="art",METADATA_KEY_ALBUM_ART="albumArt",METADATA_KEY_DISPLAY_ICON="icon";
 public final Map<String,Object> values=new HashMap<>();
 public static class Builder {private MediaMetadata m=new MediaMetadata();public Builder putString(String k,String v){m.values.put(k,v);return this;}
 public Builder putLong(String k,long v){m.values.put(k,v);return this;}public Builder putBitmap(String k,Bitmap v){m.values.put(k,v);return this;}public MediaMetadata build(){return m;}}}
 ''',
 'android/app/Notification.java': '''package android.app;import android.content.*;import android.graphics.*;import java.util.*;
 public class Notification {public static final int VISIBILITY_PUBLIC=1;public static final String CATEGORY_TRANSPORT="transport";
 public String title,text;public boolean ongoing;public Bitmap art;public PendingIntent content,delete;public MediaStyle style;public final List<PendingIntent> actions=new ArrayList<>();
 public static class MediaStyle {public Object token;public int[] compact;public MediaStyle setMediaSession(Object t){token=t;return this;}public MediaStyle setShowActionsInCompactView(int... c){compact=c;return this;}}
 public static class Builder {private Notification n=new Notification();public Builder(Context c,String channel){}
 public Builder setSmallIcon(int i){return this;}public Builder setContentTitle(String v){n.title=v;return this;}public Builder setContentText(String v){n.text=v;return this;}
 public Builder setContentIntent(PendingIntent v){n.content=v;return this;}public Builder setDeleteIntent(PendingIntent v){n.delete=v;return this;}
 public Builder setVisibility(int i){return this;}public Builder setCategory(String s){return this;}public Builder setShowWhen(boolean b){return this;}public Builder setOnlyAlertOnce(boolean b){return this;}
 public Builder setOngoing(boolean b){n.ongoing=b;return this;}public Builder setColor(int c){return this;}public Builder setColorized(boolean b){return this;}
 public Builder setLargeIcon(Bitmap b){n.art=b;return this;}public Builder addAction(int i,String s,PendingIntent a){n.actions.add(a);return this;}public Builder setStyle(MediaStyle s){n.style=s;return this;}public Notification build(){return n;}}}
 ''',
 'radio/spider/app/R.java': 'package radio.spider.app;public class R {public static class drawable {public static int ic_notification=1,ic_previous=2,ic_pause=3,ic_play=4,ic_next=5;}}',
 'radio/spider/app/MainActivity.java': 'package radio.spider.app;public class MainActivity {}',
 'radio/spider/app/RadioService.java': 'package radio.spider.app;public class RadioService {}',
 'radio/spider/app/SystemMediaOutputTest.java': '''package radio.spider.app;
 import android.app.*;import android.content.*;import android.graphics.*;import android.media.*;import android.media.session.*;
 public class SystemMediaOutputTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static SystemMediaOutput.View view(boolean visible,boolean live,boolean noise,boolean wanted,boolean playing,boolean buffering,boolean error,Bitmap art){
 return new SystemMediaOutput.View(MediaPresentation.of(visible,live,noise,true,playing,buffering,error,3,12000,60000),live,noise,wanted,buffering,buffering,
 "file:///private/song",noise?"雨声":live?"直播":"Song",live||noise?"来源":"Artist",noise?"纯白噪音":live?"网络直播":"本地音乐","love","雨声",error?"连接失败":"",new TrackInfo("Song","Artist","Album","song.mp3",60000),art,"embedded",30);}
 public static void main(String[] args){
 MediaSession session=new MediaSession();SystemMediaOutput output=new SystemMediaOutput(new Context(),session);Bitmap art=new Bitmap();
 SystemMediaOutput.View local=view(true,false,false,true,true,false,false,art);
 output.publishMetadata(local);check(session.metadata.values.get("title").equals("Song"),"local title");check(session.metadata.values.get("artist").equals("Artist"),"local artist");
 check(session.metadata.values.get("duration").equals(60000L),"local duration");check(session.metadata.values.get("albumArt")==art,"art published");check(!session.metadata.values.get("id").toString().contains("private"),"private path not published");
 output.publishMetadata(local);check(session.writes==1,"same metadata cached");output.invalidateMetadata();output.publishMetadata(local);check(session.writes==2,"art invalidation republished");
 String diag=output.updateState(local);check(session.state.state==PlaybackState.STATE_PLAYING&&session.state.speed==1&&session.state.position==12000,"local state");check((session.state.actions&PlaybackState.ACTION_SEEK_TO)!=0,"local seek");check(diag.contains("artworkSource=embedded"),"diagnostics");
 Notification n=output.notification(local);check(n.actions.size()==3&&n.style.compact.length==3,"local controls");check(n.style.token==session,"media token");check(n.ongoing,"wanted ongoing");check(n.actions.get(1).intent.action.equals("pause")&&n.actions.get(1).kind.equals("service"),"pause service intent");check(n.delete.intent.action.equals("dismiss"),"dismiss");
 for(boolean noise:new boolean[]{false,true}){
 SystemMediaOutput.View stream=view(true,!noise,noise,true,true,false,false,art);output.publishMetadata(stream);output.updateState(stream);n=output.notification(stream);
 check(!session.metadata.values.containsKey("duration"),"no stream duration");check((session.state.actions&PlaybackState.ACTION_SEEK_TO)==0&&session.state.position==0,"no stream seek");check(n.actions.size()==(noise?1:3),"noise/live buttons");check(session.metadata.values.get("artist").equals("蜘蛛电台"),"stream artist");}
 SystemMediaOutput.View paused=view(true,false,false,false,false,false,false,null);output.updateState(paused);n=output.notification(paused);check(session.state.state==PlaybackState.STATE_PAUSED&&session.state.speed==0,"paused state");check(!n.ongoing&&n.actions.get(1).intent.action.equals("resume")&&n.actions.get(1).kind.equals("foreground"),"resume foreground intent");
 output.updateState(view(true,true,false,true,false,true,true,null));check(session.state.state==PlaybackState.STATE_BUFFERING&&session.state.speed==0,"retry precedence");check(output.notification(view(true,true,false,true,false,true,true,null)).text.equals("等待网络恢复"),"waiting text");
 output.updateState(view(true,true,false,false,false,false,true,null));check(session.state.state==PlaybackState.STATE_ERROR&&session.state.error.equals("连接失败"),"terminal error");
 output.updateState(view(false,false,false,false,false,false,false,null));check(session.state.state==PlaybackState.STATE_STOPPED,"stopped state");
 check(output.openActivity().intent.target==MainActivity.class,"activity destination");
 System.out.println("PASS production publisher: metadata/cache/art, private ID, local/live/noise, media token/buttons/intents, pause/retry/error/stop");}}
 '''
}
with tempfile.TemporaryDirectory(prefix='spider-media-test-') as directory:
 d = Path(directory)
 for name, content in support.items():
  p=d/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8')
 production=root/'app/src/main/java/radio/spider/app'
 sources=[str(p) for p in d.rglob('*.java')]+[str(production/n) for n in ['SystemMediaOutput.java','MediaPresentation.java','MediaSurfacePolicy.java','TrackInfo.java']]
 java=os.environ.get('JAVA_BIN','java')
 subprocess.run([java,'com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(d),*sources],check=True)
 subprocess.run([java,'-cp',str(d),'radio.spider.app.SystemMediaOutputTest'],check=True)
