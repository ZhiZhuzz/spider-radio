"""Original procedural sound design, 60s circular stereo loops. No recordings/samples.
Requires numpy, scipy and ffmpeg. Rebuilds only the 4 ambient tracks.
"""
from pathlib import Path
import numpy as np
from scipy.io.wavfile import write
import subprocess,json
BASE=Path(__file__).resolve().parents[1]; OUT=BASE/'app/src/main/assets'; TMP=BASE/'build/audio';TMP.mkdir(parents=True,exist_ok=True)
sr=32000;duration=60;N=sr*duration;t=np.arange(N)/sr;rng=np.random.default_rng(20428)
f=np.fft.rfftfreq(N,1/sr)
def noise(lo,hi,slope=0):
    spectrum=np.fft.rfft(rng.normal(size=N))
    curve=(np.maximum(f,10)/100)**(-slope/2)
    curve*=1/(1+(lo/np.maximum(f,.01))**6) / np.sqrt(1+(f/hi)**8)
    curve[0]=0
    x=np.fft.irfft(spectrum*curve,n=N);return x/np.std(x)
def swell(periods=3,depth=.2):
    return 1+depth*np.sin(2*np.pi*periods*t/duration+rng.uniform(0,6.28))
def events(rate,kind):
    x=np.zeros(N)
    for _ in range(int(rate*duration)):
        start=rng.integers(N); d=rng.uniform(.018,.065) if kind=='drop' else rng.uniform(.06,.14)
        v=np.arange(int(sr*d))/sr
        burst=rng.normal(size=len(v));burst=np.convolve(burst,[.18,.32,.32,.18],mode='same')
        env=(1-np.exp(-v/.001))*np.exp(-v/(d*.2))
        if kind=='drop':burst+=.2*np.sin(2*np.pi*rng.uniform(700,1800)*v)*np.exp(-v/.008)
        else:burst=np.convolve(burst,np.ones(18)/18,mode='same')
        x[(start+np.arange(len(v)))%N]+=burst*env*rng.uniform(.15,.5)
    return x
def scene(kind):
    if kind=='rain':
        # Soft broad rain bed + close diffuse droplets, no thunder.
        return .66*noise(170,6800,.45)*swell(2,.10)+.23*noise(70,650,1)*swell(5,.12)+events(90,'drop')*.6
    if kind=='train':
        # Enclosed carriage: wheel roar, air ventilation, gentle track joints.
        bed=.70*noise(32,300,1.25)*swell(2,.11)+.25*noise(180,2200,.8)
        phase=2*np.pi*(90*t+.25*np.sin(2*np.pi*t/60))
        motor=.045*np.sin(phase)+.025*np.sin(phase*2)
        wheel=(.88+.12*np.cos(2*np.pi*88*t/60)**8)
        return bed*wheel+motor+events(2.4,'joint')*.75
    if kind=='snow':
        # Snow itself is quiet: softened wind outside a closed window.
        return .73*noise(35,650,1.5)*swell(3,.26)+.16*noise(230,1600,1)*swell(7,.25)
    # Riverside: low city wash, distant traffic swells, soft water movement.
    return .48*noise(45,950,1.2)*swell(2,.1)+.23*noise(80,460,.7)*swell(5,.55)+.25*noise(220,2800,1)*swell(11,.24)
report={}
for kind in ['city','snow','train','rain']:
    # Shared center with decorrelated sides, modest stereo width.
    center=scene(kind);left=.78*center+.22*scene(kind);right=.78*center+.22*scene(kind)
    x=np.stack([left,right],1);x-=x.mean(axis=0);x*=.105/np.sqrt(np.mean(x*x))
    peak=float(abs(x).max())
    if peak>.85:x*=.85/peak
    path=TMP/(kind+'.wav');write(path,sr,(x*32767).astype(np.int16))
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(path),'-c:a','libvorbis','-q:a','4',str(OUT/(kind+'.ogg'))],check=True)
    # Boundary is a normal adjacent-sample transition in circular synthesis.
    edge=float(np.max(abs(x[0]-x[-1])));diff95=float(np.quantile(abs(np.diff(x,axis=0)),.9999))
    report[kind]={'seconds':duration,'rms_dbfs':round(20*np.log10(np.sqrt(np.mean(x*x))),2),'peak':round(float(abs(x).max()),4),'boundary_delta':round(edge,5),'sample_delta_p9999':round(diff95,5),'bytes':(OUT/(kind+'.ogg')).stat().st_size}
(TMP/'validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
