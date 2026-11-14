const assert=require('node:assert/strict'),path=require('node:path');
const {chromium}=require('./browser.cjs');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH,args:['--no-sandbox']});const page=await browser.newPage({viewport:{width:412,height:892}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.calls=[];window.Radio={command:()=>{},library:()=> '[]',backgrounds:()=> '{}',noises:()=> '[]',systemPlaybackInfo:()=>JSON.stringify({device:'vivo <测试设备>',android:'16',notifications:true,channelEnabled:true,backgroundRestricted:false}),notificationSettings:()=>calls.push('notifications'),applicationSettings:()=>calls.push('application'),diagnostics:()=>calls.push('diagnostics')};});
 await page.goto(require('node:url').pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/index.html')).href);await page.evaluate(()=>show('settings'));await page.getByRole('button',{name:'系统播放与兼容性',exact:true}).click();
 assert.match(await page.locator('#panel').textContent(),/vivo <测试设备>/);assert.match(await page.locator('#panel').textContent(),/通知：已开启/);assert.equal(await page.locator('#panel 测试设备').count(),0);
 for(const [label,value] of [['通知设置','notifications'],['应用设置','application'],['播放诊断','diagnostics']]){await page.getByRole('button',{name:label,exact:true}).click();assert.equal((await page.evaluate(()=>calls)).at(-1),value);}
 await page.evaluate(()=>{Radio.systemPlaybackInfo=()=>'{bad';showPlaybackHelp()});assert.match(await page.locator('#panel').textContent(),/锁屏/);
 for(const viewport of [{width:320,height:568},{width:892,height:412}]){await page.setViewportSize(viewport);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
 await page.evaluate(()=>{handleBack();nativeState({live:false,noiseOnly:false,mode:'单曲',title:'测试歌曲'})});assert.equal(await page.locator('#homeMode').count(),1);assert.equal(await page.locator('.tools button').count(),4);
 assert.deepEqual(errors,[]);await browser.close();console.log('PASS V07 system playback help, settings bridges, escaped device report, corrupt report fallback, narrow/landscape, unchanged home control count.');
})().catch(e=>{console.error(e);process.exit(1)});
