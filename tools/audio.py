import numpy as np,wave,os
from scipy.signal import butter,sosfilt
out=os.path.join(os.path.dirname(__file__),'../app/src/main/assets');sr=22050;rng=np.random.default_rng(428)
def write(name,x):
 x=np.clip(x,-.9,.9);x=(x*32767).astype('<i2')
 with wave.open(out+'/'+name+'.wav','wb') as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(sr);f.writeframes(x.tobytes())
def loop(x):
 n=sr; x[:n]=x[:n]*np.linspace(0,1,n)+x[-n:]*np.linspace(1,0,n);return x[:-n]
# Original 64 second ambient composition, no samples.
t=np.arange(sr*64)/sr;x=np.zeros(len(t));chords=[[130.81,164.81,196,246.94],[110,130.81,164.81,196],[87.31,110,130.81,164.81],[98,123.47,146.83,196]]
for j in range(8):
 start=j*8;u=t-start;env=np.where((u>=0)&(u<10),np.sin(np.pi*np.clip(u/10,0,1))**2,0)
 for f in chords[j%4]:x+=.033*env*(np.sin(2*np.pi*f*t)+.25*np.sin(2*np.pi*(f*2+.2)*t))
 for k in range(4):
  v=u-k*1.75;env2=np.where(v>=0,np.exp(-np.maximum(v,0)*1.6),0);f=chords[j%4][k]*4;x+=.035*env2*np.sin(2*np.pi*f*t)
x*=np.minimum(t/3,1)*np.minimum((64-t)/4,1);write('demo',x)
