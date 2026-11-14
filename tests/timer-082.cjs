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

const messages=[];ev("toast=function(t){window.timerMessages.push(t)}");w.timerMessages=messages;
w.Radio.setSleep=(minutes,request)=>w.calls.push(['setSleep',minutes,request]);
ev("show('timer');sleep(15)");assert.equal(w.calls.at(-1)[0],'setSleep');assert.equal(messages.length,0);
let request=w.calls.at(-1)[2];w.sleepResult(request,false,'拒绝设置');assert.equal(messages.at(-1),'拒绝设置');assert.equal(w.document.querySelector('#sheet').classList.contains('open'),true);
ev("sleep(30)");request=w.calls.at(-1)[2];w.sleepResult(request,true,'');assert.equal(messages.at(-1),'将在30分钟后停止');
ev("show('timer');sleep(60)");const earlier=w.calls.at(-1)[2];ev("sleep(90)");request=w.calls.at(-1)[2];const count=messages.length;w.sleepResult(earlier,true,'');assert.equal(messages.length,count);w.sleepResult(request,true,'');assert.equal(messages.at(-1),'将在90分钟后停止');
for(const value of ['','0','0.5','1.5','721','-1','1e2']){ev("show('timer')");w.document.querySelector('#minutes').value=value;const n=w.calls.length;w.sleepCustom();assert.equal(w.calls.length,n,'invalid custom input '+value);}
ev("show('timer')");w.document.querySelector('#minutes').value='720';w.sleepCustom();request=w.calls.at(-1)[2];w.sleepResult(request,true,'');assert.equal(messages.at(-1),'将在720分钟后停止');
ev("show('timer');sleep(0)");request=w.calls.at(-1)[2];w.sleepResult(request,true,'');assert.equal(messages.at(-1),'已取消定时');
assert.deepEqual(errors,[]);console.log('PASS timer DOM validation, native acknowledgement, failure feedback, out-of-order results, presets and cancellation');dom.window.close();
