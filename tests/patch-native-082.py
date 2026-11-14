#!/usr/bin/env python3
"""Exercise production ambient methods with a deterministic player/scheduler.
No Android audio decoding or device lifecycle is simulated.
"""
from pathlib import Path
import re,subprocess,tempfile,os
root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/radio/spider/app/RadioService.java').read_text(encoding='utf-8')
def method(name):
 m=re.search(r'^\s*(?:void|boolean|int|float|String) '+name+r'\(',src,re.M)
 b=src.index('{',m.start());depth=0
 for i in range(b,len(src)):
  if src[i]=='{':depth+=1
  elif src[i]=='}':
   depth-=1
   if depth==0:return src[m.start():i+1]
methods='\n'.join(method(n) for n in ['loadAmbient','transitionAmbient','finishAmbientFade','ambientFailure','syncAmbient','setVolumes','volumeFactor','volumes','ambientWanted','soundIndex','playbackError','setSleep'])
prefix='''package radio.spider.app;import java.util.*;
public class PatchNativeTest {
 static class PowerManager {static final int PARTIAL_WAKE_LOCK=1;}
 static class SystemClock {static long now;static long elapsedRealtime(){return now;}}
 static class JSONObject {String optString(String k){return "test";}}
 static class MediaPlayer {
  interface Error {boolean run(MediaPlayer p,int w,int e);}interface Prepared {void run(MediaPlayer p);}
  float gain;boolean playing,released;Prepared onPrepared;
  void setWakeMode(Object c,int m){}void setAudioAttributes(Object a){}void setLooping(boolean b){}
  void setOnErrorListener(Error e){}void setOnPreparedListener(Prepared p){onPrepared=p;}
  void setVolume(float l,float r){if(released)throw new IllegalStateException();gain=l;}
  void start(){if(released)throw new IllegalStateException();playing=true;}void pause(){playing=false;}boolean isPlaying(){return playing;}
 }
 static class Handler {
  TreeMap<Long,List<Runnable>> tasks=new TreeMap<>();long now;
  void postDelayed(Runnable r,long t){tasks.computeIfAbsent(now+t,k->new ArrayList<>()).add(r);}
  void runTo(long t){while(!tasks.isEmpty()&&tasks.firstKey()<=t){long next=tasks.firstKey();now=next;SystemClock.now=next;for(Runnable r:tasks.remove(next))r.run();}now=t;SystemClock.now=t;}
 }
 static class FakeAudio {void abandonAudioFocusRequest(Object f){}}
 Handler handler=new Handler();MediaPlayer ambient,outgoing,player,pendingAmbient,lastFresh;FakeAudio audio=new FakeAudio();Object focus=new Object();
 int ambientGeneration=0,fadeGeneration=0,channel=3,soundChoice=-1;boolean ambientLoading=false,destroyed=false,noiseOnly=false,wanted=true,playing=true,live=false,liveNoise=false,prepared=true,ducked=false,buffering=false;
 float ambVolume=.25f,musicVolume=.75f,ambientGain=1,outgoingGain=0;long sleepAt=0;String customSound="",error="",ambientError="";MediaPresentation.Status published;
 interface Ready {void accept(MediaPlayer p);}
 PatchNativeTest(){SystemClock.now=0;player=new MediaPlayer();player.start();ambient=new MediaPlayer();ambient.start();ambient.setVolume(.25f,.25f);}
 void prepareSource(MediaPlayer p,String s,java.util.function.BooleanSupplier valid,Ready ready,Runnable fail){lastFresh=p;ready.accept(p);}
 Object attrs(){return null;}void dispose(MediaPlayer p){if(p!=null){p.released=true;p.playing=false;p.gain=0;}}
 JSONObject resolveCustom(){return null;}void stopForeground(boolean b){}void log(String s){}
 void publish(){published=MediaPresentation.of(true,live,noiseOnly,prepared,playing,buffering,!error.isEmpty()||(noiseOnly&&!ambientError.isEmpty()),3,0,1000).status;}
 void play(){playing=true;volumes();syncAmbient();publish();}
 float gain(){return (ambient==null?0:ambient.gain)+(outgoing==null?0:outgoing.gain);}
 void ready(){lastFresh.onPrepared.run(lastFresh);}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}static void near(float v,float expected){check(Math.abs(v-expected)<.00001,"gain "+v+" != "+expected);}
'''
tests='''
 public static void main(String[] args){
  PatchNativeTest a=new PatchNativeTest();a.loadAmbient();a.ready();a.handler.runTo(30);a.ambVolume=.4f;a.volumes();near(a.gain(),.4f);a.handler.runTo(60);near(a.gain(),.4f);
  a.ducked=true;a.volumes();near(a.gain(),.08f);a.ducked=false;a.sleepAt=5060;a.volumes();near(a.gain(),.2f);
  a.sleepAt=0;a.volumes();near(a.gain(),.4f);a.handler.runTo(720);near(a.gain(),.4f);check(a.outgoing==null,"old released");
  System.out.println("PASS volume / duck / sleep factors preserve fade weights");
  PatchNativeTest b=new PatchNativeTest();b.loadAmbient();b.ready();b.handler.runTo(30);float gain=b.ambient.gain;MediaPlayer audible=b.ambient;
  b.loadAmbient();b.ready();MediaPlayer superseded=b.lastFresh;near(b.ambient.gain,gain);check(b.ambient==audible,"no abrupt replacement");
  b.loadAmbient();b.ready();MediaPlayer latest=b.lastFresh;check(superseded.released,"superseded ready player released");b.handler.runTo(1500);check(b.ambient==latest&&latest.playing&&!latest.released,"latest wins");near(b.gain(),.25f);b.handler.runTo(15000);check(!latest.released,"stale timeout ignored");
  System.out.println("PASS rapid requests queue after fade; stale transitions/timeouts ignored");
  PatchNativeTest c=new PatchNativeTest();c.ambient=null;c.loadAmbient();c.ready();c.handler.runTo(30);float first=c.ambient.gain;c.loadAmbient();c.ready();near(c.ambient.gain,first);c.handler.runTo(1500);near(c.gain(),.25f);
  System.out.println("PASS first fade from silence also survives rapid switching");
  PatchNativeTest d=new PatchNativeTest();d.loadAmbient();d.ready();d.handler.runTo(30);d.playing=false;d.syncAmbient();check(d.outgoing==null,"pause releases outgoing");d.handler.runTo(720);check(!d.ambient.playing,"no auto restart");d.play();near(d.gain(),.25f);
  System.out.println("PASS pause/resume suppresses old fade callbacks");
  PatchNativeTest e=new PatchNativeTest();e.ambientFailure(e.ambientGeneration);check(e.player.playing&&e.published==MediaPresentation.Status.PLAYING,"ambient warning cannot mark music failed");check(!e.playbackError().isEmpty(),"warning retained");e.loadAmbient();e.ready();check(e.playbackError().isEmpty(),"ambient recovery clears warning");
  e.error="primary failure";e.ambientFailure(e.ambientGeneration);e.loadAmbient();e.ready();check(e.playbackError().equals("primary failure"),"primary error retained");
  PatchNativeTest f=new PatchNativeTest();f.noiseOnly=true;f.ambientFailure(f.ambientGeneration);check(!f.wanted&&!f.playing&&f.published==MediaPresentation.Status.ERROR,"pure noise failure stops");f.loadAmbient();f.ready();check(f.ambientError.isEmpty()&&!f.ambient.playing,"successful selection clears without autoplay");
  System.out.println("PASS scoped errors, recovery, primary failure preservation, pure-noise stop");
  PatchNativeTest g=new PatchNativeTest();g.setSleep("15");long previous=g.sleepAt;try{g.setSleep("1.5");throw new AssertionError();}catch(IllegalArgumentException expected){}check(g.sleepAt==previous,"failed setting retains timer");g.setSleep("0");check(g.sleepAt==0,"cancel timer");
  System.out.println("PASS native timer update / rejection / cancellation");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='spider-082-test-') as directory:
 d=Path(directory);(d/'PatchNativeTest.java').write_text(prefix+methods+tests,encoding='utf-8')
 java=os.environ.get('JAVA_BIN','java')
 subprocess.run([java,'com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(d),str(d/'PatchNativeTest.java'),str(root/'app/src/main/java/radio/spider/app/MediaPresentation.java'),str(root/'app/src/main/java/radio/spider/app/SleepTimer.java')],check=True)
 subprocess.run([java,'-cp',str(d),'radio.spider.app.PatchNativeTest'],check=True)
