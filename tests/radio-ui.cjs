const assert=require('node:assert/strict');
const path=require('node:path');
const {chromium}=require('./browser.cjs');
(async()=>{
 require('node:fs').mkdirSync(path.resolve(__dirname,'../../deliverables'),{recursive:true});
 const b=await chromium.launch({executablePath:process.env.CHROME_PATH,args:['--no-sandbox']});
 const page=await b.newPage({viewport:{width:412,height:892}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.calls=[];window.Radio={library:()=>JSON.stringify([{title:'测试歌曲',uri:'content://test/1'}]),command:(a,v)=>calls.push([a,v]),importMusic:()=>{},backgrounds:()=>JSON.stringify({}),pickBackground:(i)=>calls.push(["background",i]),clearBackground:()=>{},diagnostics:()=>{}}});
 await page.goto(require('node:url').pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/index.html')).href);
 const live={playing:true,wanted:true,buffering:false,live:true,stationId:'love',liveNoise:false,soundChoice:-1,noiseOnly:false,music:.75,ambient:.25,title:'上海 Love Radio 103.7',uri:'radio:love',duration:0,position:0,error:''};
 await page.evaluate(s=>nativeState(s),live);assert(await page.locator('#seek').isDisabled());assert.equal(await page.locator('#elapsed').textContent(),'● LIVE');
 await page.locator('#sourcePicker').click();await page.waitForTimeout(550);assert.equal(await page.locator('.radio-row').count(),15);
 await page.getByRole('button',{name:'音乐',exact:true}).click();assert.equal(await page.locator('.radio-row').count(),6);
 await page.locator('#radioQuery').fill('爵士');assert.equal(await page.locator('.radio-row').count(),1);
 await page.locator('#radioQuery').fill('');await page.locator('.star').first().click();await page.locator('.radio-filters').getByRole('button',{name:'收藏',exact:true}).click();assert.equal(await page.locator('.radio-row').count(),1);
 await page.getByRole('button',{name:'全部',exact:true}).click();await page.locator('.tune').filter({hasText:'上海经典947'}).click();assert((await page.evaluate(()=>calls)).some(c=>c[0]==='radio'&&c[1]==='classic'));
 await page.evaluate(()=>station(2));assert(!(await page.evaluate(()=>calls.slice(-1))).some(c=>c[0]==='radio'));
 await page.locator('[data-sheet=mixer]').click();await page.waitForTimeout(550);assert(await page.getByLabel('白噪音音量',{exact:true}).isDisabled());await page.locator('#liveNoise').check();await page.locator('#soundChoice').selectOption('3');assert.equal(await page.locator('#soundChoice').inputValue(),'3');
 await page.evaluate(s=>nativeState({...s,liveNoise:true,soundChoice:3}),live);
 await page.evaluate(()=>station(1));await page.locator('[data-sheet=mixer]').click();assert.equal(await page.locator('#soundChoice').inputValue(),'3');
 await page.getByRole('button',{name:'只听白噪音',exact:true}).click();await page.evaluate(s=>nativeState({...s,noiseOnly:true,title:'窗前雨声',liveNoise:true,soundChoice:3}),live);assert(await page.locator('#prev').isDisabled());assert(await page.getByLabel('电台音量',{exact:true}).isDisabled());
 await page.getByRole('button',{name:'返回电台',exact:true}).click();await page.evaluate(s=>nativeState({...s,buffering:true,playing:false,wanted:true}),live);assert.equal(await page.locator('#elapsed').textContent(),'连接中');
 await page.locator('.close').click();await page.locator('#play').click();assert.equal((await page.evaluate(()=>calls)).at(-1)[0],'toggle');
 await page.evaluate(s=>nativeState({...s,playing:false,wanted:false,error:'电台暂时无法连接，请检查网络后重试或换台'}),live);assert(await page.locator('#liveError').isVisible());
 await page.evaluate(()=>nativeState({playing:true,wanted:true,live:false,noiseOnly:false,title:'测试歌曲',uri:'content://test/1',duration:60000,position:10000,error:'',soundChoice:3,liveNoise:false,music:.75,ambient:.3}));assert(!await page.locator('#seek').isDisabled());assert.equal(await page.locator('#elapsed').textContent(),'00:10');assert(!(await page.locator('#liveError').isVisible()));
 // Check persistence and unclipped layouts at narrow and landscape sizes.
 await page.reload();await page.evaluate(()=>show('radio'));await page.locator('.radio-filters').getByRole('button',{name:'收藏',exact:true}).click();assert.equal(await page.locator('.radio-row').count(),1);
 for(const size of [{width:360,height:740},{width:892,height:412}]){await page.setViewportSize(size);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
 await page.setViewportSize({width:412,height:892});await page.evaluate(s=>{nativeState(s);station(2);radioFilter='全部';show('radio')},live);await page.waitForTimeout(900);
 await page.screenshot({path:path.resolve(__dirname,'../../deliverables/SpiderRadio-live-v6.png')});
 await page.evaluate(()=>show('settings'));await page.getByRole('button',{name:/画面与布局/,exact:true}).click();await page.getByRole('button',{name:'选择图片',exact:true}).click();assert.equal((await page.evaluate(()=>calls)).at(-1)[0],'background');
 await page.evaluate(()=>{Radio.backgrounds=()=>JSON.stringify({2:'train-v2.png?custom=1'});refreshBackgrounds();});assert((await page.locator('.world').nth(2).getAttribute('style')).includes('custom=1'));
 await page.evaluate(()=>{Radio.backgrounds=()=>JSON.stringify({});refreshBackgrounds();});assert(!(await page.locator('.world').nth(2).getAttribute('style')).includes('custom=1'));
 assert.deepEqual(errors,[]);console.log('PASS: 15 stations, 6 music, search, favorites/persistence, native bridge, independent noise, live states, local restore UI, narrow/landscape layout.');await b.close();
})().catch(e=>{console.error(e);process.exit(1)});
