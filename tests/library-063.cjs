const {chromium}=require('./browser.cjs'),assert=require('assert/strict'),path=require('path'),fs=require('fs');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:412,height:860}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.calls=[];window.tracks=[{uri:'file:///music/a',title:'晚风 · 在回家的路上.mp3',size:5310000},{uri:'file:///music/b',title:'窗外的雨.flac',size:21780000},{uri:'file:///music/c',title:'很长的歌曲名称用来检查窄屏换行和按钮是否会被挤出屏幕.mp3',size:8690000}];window.deleted=JSON.parse(localStorage.getItem('mockDeleted')||'[]');window.pending=[];window.confirmedPlaylist='';
  window.Radio={library:()=>JSON.stringify([...(deleted.includes('demo.wav')?[]:[{uri:'demo.wav',title:'蓝色时差',builtin:true,size:512000}]),...tracks]),backgrounds:()=> '{}',noises:()=> '[]',deletedSongs:()=>JSON.stringify(deleted),confirmDeletePlaylist:name=>confirmedPlaylist=name,restoreBuiltIn:()=>{deleted=deleted.filter(u=>u!=='demo.wav');localStorage.setItem('mockDeleted',JSON.stringify(deleted));reloadLibrary()},command:(a,v)=>calls.push([a,v]),deleteSongs:json=>{pending=JSON.parse(json);calls.push(['deleteSongs',pending]);}};
 });
 await page.goto(require('node:url').pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/index.html')).href);await page.evaluate(()=>document.fonts.ready);await page.evaluate(()=>{apply(1);nativeState({mode:'顺序',playing:false,live:false,noiseOnly:false,uri:'demo.wav',music:.75,ambient:.25});});
 await page.waitForTimeout(650);await page.locator('#homeMode').waitFor({state:'visible'});
 assert.equal(await page.locator('#homeMode').count(),1);
 for(const mode of ['列表','随机','单曲','顺序']){await page.locator('#homeMode').click();assert.equal((await page.evaluate(()=>calls)).at(-1)[1],mode);}
 await page.evaluate(()=>{$('#toast').style.display='none';nativeState({mode:'随机',live:false,noiseOnly:false,uri:'demo.wav',title:'蓝色时差',position:7000,duration:64000});});
 assert.match(await page.locator('#homeMode').textContent(),/随机播放/);
 fs.mkdirSync(path.resolve(__dirname,'../previews'),{recursive:true});
 await page.screenshot({path:path.resolve(__dirname,'../previews/home-063.png')});
 for(const type of ['settings','library']){await page.evaluate(type=>show(type),type);assert.equal(await page.locator('#panel #localMode').count(),0);assert(!/播放模式/.test(await page.locator('#panel').textContent()));}
 assert.equal(await page.locator('#panel button').filter({hasText:'删除'}).count(),0);
 await page.waitForTimeout(600);await page.screenshot({path:path.resolve(__dirname,'../previews/library-063.png')});

 // Real DOM + persistent WebView storage; native dialogs are explicitly mocked.
 await page.evaluate(()=>{lists={'测试一':['file:///music/a'],'保留':['file:///music/a']};localStorage.setItem('lists',JSON.stringify(lists));selectedList='测试一';show('library')});
 await page.screenshot({path:path.resolve(__dirname,'../previews/playlist-063.png')});await page.getByRole('button',{name:'删除歌单：测试一'}).click();
 assert.equal(await page.evaluate(()=>confirmedPlaylist),'测试一');
 assert.deepEqual(await page.evaluate(()=>lists['测试一']),['file:///music/a']); // No native confirmation yet.
 await page.evaluate(()=>deletePlaylistConfirmed('测试一'));
 assert.equal(await page.evaluate(()=>lists['测试一']),undefined);
 assert.deepEqual(await page.evaluate(()=>lists['保留']),['file:///music/a']);
 assert.equal(await page.locator('.row').filter({hasText:'晚风'}).count(),1);
 assert.equal(await page.getByRole('button',{name:/删除歌单/}).count(),0);
 await page.getByRole('button',{name:'选择',exact:true}).click();
 await page.locator('.song-select-row input').first().check();await page.locator('#deleteSelected').click();
 assert.deepEqual(await page.evaluate(()=>pending),['demo.wav']);
 await page.evaluate(()=>songDeletionFinished(false));
 assert.equal(await page.locator('.song-select-row input').first().isChecked(),true);
 await page.locator('#deleteSelected').click();
 await page.evaluate(()=>{deleted.push('demo.wav');localStorage.setItem('mockDeleted',JSON.stringify(deleted));songDeletionFinished(true)});
 assert.equal(await page.locator('.song-select-row').filter({hasText:'蓝色时差'}).count(),0);
 await page.reload();await page.evaluate(()=>show('library'));
 assert.equal(await page.locator('.row').filter({hasText:'蓝色时差'}).count(),0);
 assert.equal(await page.evaluate(()=>lists['测试一']),undefined);
 await page.getByRole('button',{name:'恢复内置试听',exact:true}).click();
 assert.equal(await page.locator('.row').filter({hasText:'蓝色时差'}).count(),1);
 // Remove again so the unchanged imported-track regression operates on 3 imports.
 await page.evaluate(()=>{deleted=['demo.wav'];localStorage.setItem('mockDeleted',JSON.stringify(deleted));reloadLibrary()});
 await page.getByRole('button',{name:'选择',exact:true}).click();
 assert.equal(await page.locator('.song-select-row').count(),3);assert.equal(await page.locator('#deleteSelected').isDisabled(),true);
 assert.match(await page.locator('.song-select-row').first().textContent(),/5.1 MB/);
 await page.locator('.song-select-row input').first().check();await page.locator('#deleteSelected').click();assert.deepEqual(await page.evaluate(()=>pending),['file:///music/a']);
 await page.evaluate(()=>songDeletionFinished(false));assert.equal(await page.locator('.song-select-row input').first().isChecked(),true);assert.equal(await page.locator('#deleteSelected').isEnabled(),true);
 await page.locator('#selectAllSongs').click();assert.equal(await page.locator('#selectedCount').textContent(),'已选 3 首');
 await page.screenshot({path:path.resolve(__dirname,'../previews/selection-062.png')});
 await page.evaluate(()=>{favs=['file:///music/a'];lists={'睡前':['file:///music/a','file:///music/b']};});
 await page.locator('#deleteSelected').click();assert.equal((await page.evaluate(()=>pending)).length,3);
 await page.evaluate(()=>{deleted.push(...pending);tracks=tracks.filter(t=>!pending.includes(t.uri));songDeletionFinished(true);});
 assert.equal(await page.locator('.song-select-row').count(),0);assert.deepEqual(await page.evaluate(()=>favs),[]);assert.deepEqual(await page.evaluate(()=>lists['睡前']),[]);assert.equal(await page.locator('#deleteSelected').isDisabled(),true);
 await page.evaluate(()=>handleBack());assert.equal(await page.locator('#sheet').evaluate(el=>el.classList.contains('open')),true);assert.equal(await page.getByRole('button',{name:'选择',exact:true}).count(),1);
 await page.evaluate(()=>{closeSheet();nativeState({mode:'列表',live:true,noiseOnly:false,stationId:'love'});});assert.equal(await page.locator('#homeMode').isVisible(),false);
 await page.evaluate(()=>nativeState({mode:'单曲',live:false,noiseOnly:true}));assert.equal(await page.locator('#homeMode').isVisible(),false);
 await page.evaluate(()=>nativeState({mode:'单曲',live:false,noiseOnly:false}));assert.equal(await page.locator('#homeMode').isVisible(),true);
 await page.evaluate(()=>{tracks=Array.from({length:40},(_,i)=>({uri:'file:///music/'+i,title:'长歌名与批量列表滚动测试 '+i,size:6000000}));filter=false;show('library');startSongSelection();});
 for(const viewport of [{width:320,height:568},{width:412,height:860},{width:892,height:412}]){
  await page.setViewportSize(viewport);await page.waitForTimeout(100);
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  const box=await page.locator('#deleteSelected').boundingBox();assert(box&&box.y>=0&&box.y+box.height<=viewport.height);
 }
 await page.evaluate(()=>{closeSheet();cfg.lift=100;positionPlayer();});assert.equal(await page.locator('#homeMode').count(),1);
 assert.deepEqual(errors,[]);await browser.close();console.log('PASS: homepage modes and native sync; no old controls; single/batch deletion, cancel, empty, favorites/playlists cleanup; live/noise hiding; footer on 320px and landscape.');
})().catch(e=>{console.error(e);process.exit(1)});
