const assert=require('node:assert/strict'),path=require('node:path');
const {chromium}=require('./browser.cjs');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:360,height:800}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.Radio={command:()=>{},library:()=> '[]',backgrounds:()=> '{}',noises:()=> '[]',systemPlaybackInfo:()=>JSON.stringify({mediaFamily:'vivo / iQOO',mediaGuidance:'检查原子岛 <unsafe>',mediaSession:'state=PLAYING'})};});
 await page.goto(require('node:url').pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/index.html')).href);
 await page.evaluate(()=>showPlaybackHelp());
 assert.match(await page.locator('#panel').textContent(),/检查原子岛 <unsafe>/);
 assert.equal(await page.locator('#panel unsafe').count(),0);
 assert(!((await page.locator('#panel').textContent()).includes('state=PLAYING')));
 for(const width of [320,412]){await page.setViewportSize({width,height:700});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
 await page.evaluate(()=>{Radio.systemPlaybackInfo=()=>JSON.stringify({mediaFamily:'荣耀',mediaGuidance:'专属胶囊尚未接入'});showPlaybackHelp()});
 assert.match(await page.locator('#panel').textContent(),/尚未接入/);
 await page.evaluate(()=>{handleBack();nativeState({live:false,noiseOnly:false,mode:'随机',title:'歌'})});
 assert.equal(await page.locator('#homeMode').count(),1);assert.equal(await page.locator('.tools button').count(),4);
 assert.deepEqual(errors,[]);await browser.close();console.log('PASS OEM guidance, escaping, diagnostics separation, small screens, unchanged home');
})().catch(e=>{console.error(e);process.exit(1)});
