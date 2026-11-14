// Optional baseline comparison: BASELINE_ASSETS points to untouched v0.8.2 assets.
const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
const {chromium}=require('./browser.cjs');
const baseline=process.env.BASELINE_ASSETS;
if(!baseline)throw new Error('Set BASELINE_ASSETS to the original v0.8.2 assets directory');
const current=path.resolve(__dirname,'../app/src/main/assets');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH,args:['--no-sandbox']});
 const contexts=await Promise.all([browser.newContext({viewport:{width:412,height:892},reducedMotion:'reduce'}),browser.newContext({viewport:{width:412,height:892},reducedMotion:'reduce'})]);
 const pages=await Promise.all(contexts.map(c=>c.newPage())),errors=[];
 for(let i=0;i<2;i++){
  pages[i].on('pageerror',e=>errors.push(e.message));
  await pages[i].addInitScript(()=>{
   Math.random=()=>.5;localStorage.setItem('cfg',JSON.stringify({eco:true}));
   window.calls=[];window.Radio={library:()=>JSON.stringify([{title:'蓝色时差',uri:'demo.wav',builtin:true,size:512000},{title:'晚风',uri:'a',size:123456}]),deletedSongs:()=> '[]',
    backgrounds:()=> '{}',noises:()=>JSON.stringify([{id:'custom-rain',title:'自己的雨声'}]),presets:()=> '[]',command:(a,v)=>calls.push([a,v]),systemPlaybackInfo:()=> '{}'};
  });
  await pages[i].goto('file://'+path.join(i?current:baseline,'index.html'));await pages[i].evaluate(()=>document.fonts.ready);
 }
 const state={live:false,noiseOnly:false,playing:true,wanted:true,title:'蓝色时差',uri:'demo.wav',position:7000,duration:64000,mode:'顺序',soundChoice:-1,customSound:'',music:.75,ambient:.25,liveNoise:false,error:''};
 const out=process.env.TEST_REPORT_DIR||path.resolve(__dirname,'../verification-083/comparison');fs.mkdirSync(out,{recursive:true});
 const cases=[['home',()=>closeSheet()],['radio',()=>show('radio')],['mixer',()=>show('mixer')],['library',()=>show('library')],['selection',()=>{show('library');startSongSelection()}],['appearance',()=>showAppearance()],['timer',()=>show('timer')]];
 let comparisons=0;
 for(const size of [{width:412,height:892},{width:320,height:568},{width:892,height:412}]){
  for(const [name,action] of cases){
   const snapshots=[];
   for(const page of pages){await page.setViewportSize(size);await page.evaluate(s=>nativeState(s),state);await page.evaluate(action);await page.waitForTimeout(600);
    snapshots.push(await page.evaluate(()=>{
     const selectors=['.player','.hero','#homeMode','#song','#play','#sourcePicker',...($('#sheet').classList.contains('open')?['#panel','#panel h2']:[])];
     const result={};for(const s of selectors){const e=document.querySelector(s),r=e.getBoundingClientRect(),c=getComputedStyle(e);
      result[s]={box:[r.x,r.y,r.width,r.height].map(n=>Math.round(n*10)/10),font:c.fontSize,color:c.color};}
     return result;
    }));
   }
   for(const selector of Object.keys(snapshots[0])){
    const a=snapshots[0][selector],b=snapshots[1][selector];
    assert.equal(a.font,b.font,`${name} ${selector} font`);assert.equal(a.color,b.color,`${name} ${selector} color`);
    a.box.forEach((n,i)=>assert(Math.abs(n-b.box[i])<=1,`${name} ${size.width} ${selector} geometry ${JSON.stringify(a.box)} -> ${JSON.stringify(b.box)}`));
   }
   if(size.width===412){await pages[0].screenshot({path:path.join(out,`${name}-082.png`)});await pages[1].screenshot({path:path.join(out,`${name}-083.png`)});}
   comparisons++;
  }
 }
 assert.deepEqual(errors,[]);await browser.close();console.log(`PASS ${comparisons} baseline/current screen comparisons: geometry, font and color (portrait/narrow/landscape)`);
})().catch(e=>{console.error(e);process.exit(1)});
