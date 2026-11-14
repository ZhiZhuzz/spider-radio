const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {JSDOM}=require('jsdom');
const assets=path.resolve(__dirname,'../app/src/main/assets');
const dom=new JSDOM(fs.readFileSync(path.join(assets,'index.html'),'utf8'),{url:'https://local.test/',runScripts:'outside-only',pretendToBeVisual:true});
const w=dom.window,errors=[],calls=[];
w.addEventListener('error',e=>errors.push(e.message));
w.matchMedia=()=>({matches:true,addEventListener(){},addListener(){}});
w.HTMLCanvasElement.prototype.getContext=()=>({setTransform(){},clearRect(){}});
w.requestAnimationFrame=()=>1;w.cancelAnimationFrame=()=>{};
w.deleted=[];w.tracks=[{uri:'demo.wav',title:'蓝色时差',builtin:true},{uri:'a',title:'晚风 <测试>'},{uri:'b',title:'夜雨'}];
w.Radio={library:()=>JSON.stringify(w.tracks.filter(s=>!w.deleted.includes(s.uri))),deletedSongs:()=>JSON.stringify(w.deleted),
  backgrounds:()=> '{}',noises:()=>JSON.stringify([{id:'custom-rain',title:'雨声 <录音>'}]),presets:()=> '[]',
  command:(a,v)=>calls.push([a,v]),deleteSongs:j=>calls.push(['deleteSongs',JSON.parse(j)]),
  confirmDeletePlaylist:n=>calls.push(['deletePlaylist',n]),restoreBuiltIn:()=>{},
  systemPlaybackInfo:()=> '{}'};
for(const el of w.document.querySelectorAll('script')) {
  const src=el.getAttribute('src');new vm.Script(src?fs.readFileSync(path.join(assets,src),'utf8'):el.textContent).runInContext(dom.getInternalVMContext());
}
const ev=s=>new vm.Script(s).runInContext(dom.getInternalVMContext());
const get=s=>w.document.querySelector(s);
ev("show('mixer')");assert.equal(get('#vmusic').textContent,'75%');assert.equal(get('#vambient').textContent,'25%');
assert.equal(get('#soundChoice').value,'-1');assert.equal(calls.filter(([a])=>a==='channel').length,1,'initial scene should issue one command');
let writes=0;const stored=w.Storage.prototype.setItem;
w.Storage.prototype.setItem=function(k,v){if(k==='cfg')writes++;return stored.call(this,k,v)};
w.nativeState({live:false,noiseOnly:false,mode:'随机',customSound:'custom-rain',music:.6,ambient:.4,soundChoice:2,liveNoise:false,playing:false,wanted:false,uri:'a',position:3000,duration:60000});
assert.equal(writes,1,'one state snapshot should commit preferences once');
w.nativeState({...ev('state')});assert.equal(writes,1,'identical state should not write preferences');
ev("show('mixer')");assert.equal(get('#soundChoice').value,'custom:custom-rain');assert.equal(get('.sound-note').textContent,'雨声 <录音> · 自定义声音，连续循环');assert.equal(get('#panel 录音'),null);
// outside-only JSDOM does not execute inline HTML handlers; invoke the same public handler.
get('#soundChoice').value='1';ev("selectSound('1')");
assert.equal(ev('cfg.customSound'),'');assert.equal(calls.at(-1)[0],'sound');assert.equal(get('#soundChoice').value,'1');
for(let i=0;i<10;i++){ev("show('settings');show('mixer');show('library')");assert.equal(w.document.querySelectorAll('.library-actions').length,1);assert.equal(w.document.querySelectorAll('.library-select').length,1);}
ev("lists={'睡前 <歌单>':['a','b']};selectedList='睡前 <歌单>';show('library')");
assert.equal(w.document.querySelectorAll('.song-row').length,2);assert.equal(get('#panel 歌单'),null);
get('#deletePlaylist').click();assert.equal(calls.at(-1)[1],'睡前 <歌单>');assert.equal(ev('Object.keys(lists).length'),1,'native confirmation required');
ev('startSongSelection()');get('.song-select-row input').click();get('#deleteSelected').click();
assert.equal(ev('deletionPending'),true);ev("closeSheet();show('settings')");assert.equal(get('#sheet').dataset.type,'library');assert(get('#sheet').classList.contains('open'));
assert.equal(w.handleBack(),true);assert.equal(get('#selectedCount').textContent,'已选 1 首');
w.nativeState({live:true,noiseOnly:false,playing:false,wanted:true,buffering:true,stationId:'love',mode:'列表',uri:'radio:love'});
assert.equal(get('#play').querySelector('path').getAttribute('d'),'M8 6v12M16 6v12');assert.equal(get('#elapsed').textContent,'连接中');assert(get('#seek').disabled);
assert.equal(get('#selectedCount').textContent,'已选 1 首','native state does not discard pending selection');
w.songDeletionFinished(false);assert.equal(ev('deletionPending'),false);assert(get('.song-select-row input').checked);
get('#deleteSelected').click();w.deleted=['a'];w.songDeletionFinished(true);
assert.deepEqual(Array.from(ev("lists['睡前 <歌单>']")),['b']);assert.equal(w.document.querySelectorAll('.song-select-row').length,1);assert.equal(get('#selectedCount').textContent,'已选 0 首');
assert.equal(w.handleBack(),true);assert.equal(w.document.querySelectorAll('.library-select').length,1);
assert.equal(w.handleBack(),true);assert(!get('#sheet').classList.contains('open'));
ev('immerse()');assert.equal(w.handleBack(),true);assert(!w.document.body.classList.contains('immersed'));assert.equal(w.handleBack(),false);
w.nativeState({live:false,noiseOnly:true,playing:true,wanted:true,buffering:false,title:'雨声',mode:'单曲',uri:'b',duration:10000});
assert(get('#prev').disabled&&get('#next').disabled&&get('#seek').disabled);assert(get('#homeMode').hidden);assert.equal(get('#elapsed').textContent,'');
w.nativeState({live:false,noiseOnly:false,playing:true,wanted:true,title:'夜雨',mode:'单曲',uri:'b',position:1000,duration:20000});
get('#seek').value='5000';get('#seek').dispatchEvent(new w.Event('input'));
w.nativeState({live:false,noiseOnly:false,playing:true,wanted:true,uri:'b',position:2000,duration:20000});assert.equal(get('#seek').value,'5000','polling must not move a dragged thumb');
get('#seek').dispatchEvent(new w.Event('change'));assert.deepEqual(calls.at(-1),['seek','5000']);
assert.deepEqual(errors,[]);
console.log('PASS defaults, single preference commit, stable sheets, custom sound escaping, pending deletion/native state interleaving, cancellation/success, back/immersive, live/noise/local transitions, seek drag');
dom.window.close();
