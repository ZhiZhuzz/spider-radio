const assert=require('node:assert/strict'),path=require('node:path');
const {chromium}=require('./browser.cjs');
(async()=>{
 require('node:fs').mkdirSync(path.resolve(__dirname,'../../deliverables'),{recursive:true});
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:412,height:892}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.calls=[];window.saved=[];window.noises=[{id:'rain-test',title:'我的雨声 <录音>',uri:'file:///test/rain',duration:4000}];window.bg={};
  window.Radio={library:()=>JSON.stringify([{title:'我的音乐',uri:'file:///test/music'}]),noises:()=>JSON.stringify(noises),backgrounds:()=>JSON.stringify(bg),presets:()=>JSON.stringify(saved),command:(a,v)=>calls.push([a,v]),importMusic:()=>{},importNoise:()=>calls.push(['importNoise']),pickBackground:()=>{},clearBackground:()=>{bg={};refreshBackgrounds()},diagnostics:()=>{},deleteNoise:id=>{noises=noises.filter(n=>n.id!==id);refreshNoises()},savePreset:raw=>{const v=JSON.parse(raw);saved.push({...v,id:'p'+saved.length,background:bg[v.scene]||'',...JSON.parse(localStorage.cfg)});setTimeout(presetSaved,0)},applyPreset:id=>applyPresetVisual(saved.find(p=>p.id===id)),deletePreset:id=>{saved=saved.filter(p=>p.id!==id);showPresets()}};
 });
 await page.goto(require('node:url').pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/index.html')).href);
 const initial={playing:false,wanted:false,live:false,noiseOnly:false,music:.75,ambient:.25,title:'蓝色时差',uri:'demo.wav',soundChoice:-1,customSound:'',liveNoise:false};await page.evaluate(s=>nativeState(s),initial);
 assert.equal(await page.locator('.tools button').count(),4,'Main page must not grow new controls');
 await page.evaluate(()=>show('settings'));await page.getByRole('button',{name:/自定义白噪音/}).click();
 assert.equal(await page.locator('#sheet').getAttribute('data-type'),'noises');await page.getByRole('button',{name:'＋ 导入白噪音',exact:true}).click();assert.equal((await page.evaluate(()=>calls)).at(-1)[0],'importNoise');
 await page.locator('.row>button').first().click();assert.equal(await page.locator('#soundChoice').inputValue(),'custom:rain-test');assert.equal((await page.evaluate(()=>calls)).at(-1)[0],'customSound');assert.equal(await page.locator('.sound-note').textContent(),'我的雨声 <录音> · 自定义声音，连续循环');
 await page.locator('#soundChoice').selectOption('2');assert.equal(await page.evaluate(()=>cfg.customSound),'');
 await page.locator('#soundChoice').selectOption('custom:rain-test');
 await page.evaluate(()=>{showAppearance();});await page.getByLabel('背景压暗').fill('35');await page.getByLabel('背景压暗').dispatchEvent('input');assert.equal(await page.evaluate(()=>cfg.dim),.35);
 await page.getByRole('button',{name:/调整播放器位置/}).click();assert(await page.locator('#layoutEditor').isVisible());
 await page.getByLabel('播放器上移').fill('70');await page.getByLabel('播放器上移').dispatchEvent('input');assert.equal(await page.evaluate(()=>cfg.lift),70);
 for(const size of [{width:320,height:568},{width:360,height:640},{width:412,height:892},{width:740,height:360},{width:892,height:412},{width:600,height:400}]){
  await page.setViewportSize(size);await page.waitForTimeout(120);const bounds=await page.evaluate(()=>({player:JSON.parse(JSON.stringify($('.player').getBoundingClientRect())),width:document.documentElement.scrollWidth,docH:document.documentElement.scrollHeight,hero:$('.hero').getBoundingClientRect().bottom}));
  assert(bounds.width<=size.width,JSON.stringify({size,bounds}));assert(bounds.player.left>=0&&bounds.player.right<=size.width+1);assert(bounds.player.top>=0);assert(bounds.player.bottom<=bounds.docH+1);if(size.width<700)assert(bounds.player.top>=bounds.hero,'Player must not overlap title');
 }
 await page.setViewportSize({width:412,height:892});await page.getByRole('button',{name:'完成',exact:true}).click();assert(!await page.locator('#layoutEditor').count());
 await page.evaluate(()=>{bg={3:'rain-v2.png?custom=rain'};refreshBackgrounds();showPresets()});await page.getByLabel('搭配名称').fill('雨夜 <测试>');await page.getByRole('button',{name:'保存当前搭配',exact:true}).click();await page.waitForTimeout(100);assert.equal(await page.locator('.preset-row strong').textContent(),'雨夜 <测试>');
 await page.evaluate(()=>{cfg.dim=0;cfg.lift=25;apply(0);save();showPresets()});await page.getByRole('button',{name:'使用并播放',exact:true}).click();assert.equal(await page.evaluate(()=>ch),3);assert.equal(await page.evaluate(()=>cfg.dim),.35);assert.equal(await page.evaluate(()=>cfg.lift),25,'Preset cannot overwrite global layout');
 await page.reload();assert.equal(await page.evaluate(()=>cfg.lift),25);assert.equal(await page.evaluate(()=>cfg.dim),.35);assert.equal(await page.evaluate(()=>cfg.customSound),'rain-test');
 await page.evaluate(()=>{nativeVisibility(false)});const stopped=await page.evaluate(()=>({t:animTime,raf:animationFrame}));await page.waitForTimeout(200);assert.equal(await page.evaluate(()=>animTime),stopped.t);assert.equal(stopped.raf,0);
 await page.evaluate(()=>nativeVisibility(true));await page.waitForTimeout(200);assert(await page.evaluate(t=>animTime>t,stopped.t));await page.evaluate(()=>{cfg.eco=true;updateAnimation()});assert.equal(await page.evaluate(()=>animationFrame),0);
 await page.evaluate(()=>{cfg.eco=false;updateAnimation();show('settings')});assert.equal(await page.evaluate(()=>handleBack()),true);assert.equal(await page.evaluate(()=>handleBack()),false);
 await page.evaluate(()=>{immerse()});assert.equal(await page.evaluate(()=>handleBack()),true);
 await page.evaluate(()=>{cfg.lift=40;save();closeSheet();applyAppearance()});await page.waitForTimeout(600);await page.screenshot({path:path.resolve(__dirname,'../../deliverables/SpiderRadio-home-v6.png')});
 await page.evaluate(()=>showPresets());await page.waitForTimeout(600);await page.screenshot({path:path.resolve(__dirname,'../../deliverables/SpiderRadio-customize-v6.png')});
 await page.evaluate(()=>localStorage.setItem('cfg','broken'));await page.reload();assert.equal(await page.evaluate(()=>cfg.ch),3);assert.deepEqual(errors,[]);
 await browser.close();console.log('PASS: custom noise bridge, escaping, built-in fallback, appearance, bounded live layout (6 viewports), preset UI/restore, global layout independence, persistence, animation suspension, back navigation, corrupt preference recovery. Native file storage still requires device testing.');
})().catch(e=>{console.error(e);process.exit(1)});
