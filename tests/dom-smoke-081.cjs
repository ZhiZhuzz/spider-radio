const vm=require('node:vm'),fs=require('fs'),path=require('path'),assert=require('node:assert/strict');
const {JSDOM}=require('jsdom');
const assets=path.resolve(__dirname,'../app/src/main/assets');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const dom=new JSDOM(html,{url:'https://local.test/',runScripts:'outside-only',pretendToBeVisual:true});
const w=dom.window,errors=[];
w.addEventListener('error',e=>errors.push(e.message));
w.matchMedia=()=>({matches:true,addEventListener(){},addListener(){}});
w.HTMLCanvasElement.prototype.getContext=()=>({setTransform(){},clearRect(){}});
w.requestAnimationFrame=()=>1;w.cancelAnimationFrame=()=>{};
w.calls=[];w.deleted=[];
w.tracks=[{uri:'demo.wav',title:'蓝色时差',builtin:true},{uri:'a',title:'晚风.mp3'},{uri:'b',title:'夜雨.flac'}];
w.Radio={
 library:()=>JSON.stringify(w.tracks.filter(s=>!w.deleted.includes(s.uri))),deletedSongs:()=>JSON.stringify(w.deleted),
 backgrounds:()=> '{}',noises:()=> '[]',command:(a,v)=>w.calls.push([a,v]),
 systemPlaybackInfo:()=>JSON.stringify({device:'vivo <测试设备>',mediaFamily:'vivo / iQOO',mediaGuidance:'系统说明 <unsafe>',notifications:true,channelEnabled:true,android:'16'}),
 notificationSettings:()=>w.calls.push(['notifications']),applicationSettings:()=>w.calls.push(['application']),diagnostics:()=>w.calls.push(['diagnostics']),
 deleteSongs:j=>{w.pending=JSON.parse(j)}
};
for(const script of dom.window.document.querySelectorAll('script')){
 const src=script.getAttribute('src');
 new vm.Script(src?fs.readFileSync(path.join(assets,src),'utf8'):script.textContent).runInContext(dom.getInternalVMContext());
}
const panel=()=>w.document.querySelector('#panel'),ev=s=>new vm.Script(s).runInContext(dom.getInternalVMContext());
function click(el){assert(el);const code=el.getAttribute('onclick');if(code)ev(code);else el.click();}
w.show('settings');assert.match(panel().textContent,/0\.8\.3/);
w.showPlaybackHelp();assert.match(panel().textContent,/系统说明 <unsafe>/);assert.equal(panel().querySelectorAll('unsafe').length,0);
assert.equal(panel().querySelectorAll('测试设备').length,0);
for(const [text,action] of [['通知设置','notifications'],['应用设置','application'],['播放诊断','diagnostics']]){
 click([...panel().querySelectorAll('button')].find(b=>b.textContent===text));assert.equal(w.calls.at(-1)[0],action);
}
w.Radio.systemPlaybackInfo=()=>'{bad';w.showPlaybackHelp();assert.match(panel().textContent,/锁屏/);
console.log('PASS DOM settings / compatibility escaping / bridges / malformed report fallback');
ev("handleBack();nativeState({live:false,noiseOnly:false,mode:'单曲',title:'测试歌曲'})");
assert.equal(w.document.querySelectorAll('#homeMode').length,1);assert.equal(w.document.querySelectorAll('.tools button').length,4);
assert.equal(w.document.querySelector('#song').textContent,'测试歌曲');
console.log('PASS DOM native playback state / restrained home controls');
ev("lists={'测试一':['a'],'测试二':['b']};selectedList='测试一';show('library')");
assert.equal(panel().querySelectorAll('.song-row').length,1);
click(panel().querySelector('.song-row button'));
assert.deepEqual(JSON.parse(w.calls.filter(c=>c[0]==='play').at(-1)[1]).queue.map(s=>s.uri),['a']);
console.log('PASS DOM playlist filtering / playback queue bridge');
ev("show('library');startSongSelection()");
assert.equal(panel().querySelectorAll('.song-select-row').length,1);
click(panel().querySelector('#selectAllSongs'));assert.equal(panel().querySelector('#selectAllSongs').textContent,'取消全选');
click(panel().querySelector('#deleteSelected'));assert.deepEqual(Array.from(w.pending),['a']);
w.songDeletionFinished(false);assert.equal(panel().querySelector('.song-select-row input').checked,true);
click(panel().querySelector('#deleteSelected'));w.deleted=['a'];w.songDeletionFinished(true);
assert.deepEqual(Array.from(ev("lists['测试一']")),[]);assert.deepEqual(Array.from(ev("lists['测试二']")),['b']);
console.log('PASS DOM selection / deletion cancellation / playlist cleanup');
ev("cancelSongSelection();handleBack();nativeState({live:false,noiseOnly:true,playing:true,title:'窗前雨声'})");
assert.equal(w.document.querySelector('#seek').disabled,true);
assert.equal(w.document.querySelector('#next').disabled,true);
assert.deepEqual(errors,[]);
console.log('PASS DOM noise-only controls / no initialization errors');
console.log('LIMIT: DOM simulation has no browser layout, audio decoder, Android service, or OEM lockscreen.');
dom.window.close();
