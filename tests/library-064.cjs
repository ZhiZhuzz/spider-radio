const {chromium}=require('./browser.cjs');
const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:412,height:892}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.calls=[];window.bg=JSON.parse(localStorage.mockBg||'{}');window.deleted=[];
  window.tracks=[{uri:'demo.wav',title:'蓝色时差',size:2800000,builtin:true},{uri:'a',title:'晚风.mp3',size:5000000},{uri:'b',title:'夜雨.flac',size:20000000}];
  window.Radio={library:()=>JSON.stringify(tracks.filter(s=>!deleted.includes(s.uri))),deletedSongs:()=>JSON.stringify(deleted),backgrounds:()=>JSON.stringify(bg),noises:()=> '[]',command:(a,v)=>calls.push([a,v]),deleteSongs:j=>{window.pending=JSON.parse(j)},importMusic:()=>{}};
 });
 await page.goto(require('node:url').pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/index.html')).href);
await page.evaluate(()=>{apply(1);lists={'测试一':['a'],'测试二':['b'],'空歌单':[]};localStorage.setItem('lists',JSON.stringify(lists));selectedList='测试一';show('library')});
 assert.equal(await page.locator('.song-row').count(),1);assert.match(await page.locator('.song-row').textContent(),/晚风/);assert.equal(await page.locator('button[onclick^="addList"]').count(),0);
 await page.locator('.song-row button').click();assert.deepEqual(await page.evaluate(()=>JSON.parse(calls.filter(c=>c[0]==='play').at(-1)[1]).queue.map(s=>s.uri)),['a']);
 await page.evaluate(()=>{show('library');startSongSelection()});assert.equal(await page.locator('.song-select-row').count(),1);
 await page.locator('#selectAllSongs').click();assert.equal(await page.locator('#selectAllSongs').textContent(),'取消全选');
 await page.locator('#addSelected').click();await page.locator('#targetPlaylist').selectOption('测试二');await page.locator('#confirmPlaylistTarget').click();assert.deepEqual(await page.evaluate(()=>lists['测试二']),['b','a']);
 await page.locator('#selectAllSongs').click();await page.locator('#removeSelected').click();assert.equal(await page.locator('.song-select-row').count(),0);assert.equal(await page.evaluate(()=>songs.length),3);assert.deepEqual(await page.evaluate(()=>lists['测试二']),['b','a']);
 await page.locator('#selectionScope').click();assert.equal(await page.locator('.song-select-row').count(),3);
 await page.locator('#selectAllSongs').click();await page.locator('#selectAllSongs').click();assert.equal(await page.locator('#selectedCount').textContent(),'已选 0 首');
 await page.locator('.song-select-row input').nth(1).check();await page.locator('#addCurrent').click();assert.deepEqual(await page.evaluate(()=>lists['测试一']),['a']);assert.equal(await page.locator('.song-select-row').count(),2);
 await page.locator('#selectionScope').click();assert.equal(await page.locator('.song-select-row').count(),1);
 await page.locator('#selectAllSongs').click();await page.evaluate(()=>{window.originalSet=Storage.prototype.setItem;Storage.prototype.setItem=function(k,v){if(k==='lists')throw Error('disk full');return originalSet.call(this,k,v)}});
 await page.locator('#removeSelected').click();assert.deepEqual(await page.evaluate(()=>lists['测试一']),['a']);assert.equal(await page.locator('#selectedCount').textContent(),'已选 1 首');await page.evaluate(()=>Storage.prototype.setItem=originalSet);
 await page.locator('#deleteSelected').click();assert.deepEqual(await page.evaluate(()=>pending),['a']);await page.evaluate(()=>songDeletionFinished(false));assert.equal(await page.locator('.song-select-row input').isChecked(),true);
 await page.locator('#deleteSelected').click();await page.evaluate(()=>{deleted=['a'];songDeletionFinished(true)});assert.deepEqual(await page.evaluate(()=>lists['测试一']),[]);assert.deepEqual(await page.evaluate(()=>lists['测试二']),['b']);
 await page.evaluate(()=>{cancelSongSelection();selectedList='测试二';filter=true;favs=[];show('library')});assert.equal(await page.locator('.song-row').count(),0);
 await page.evaluate(()=>{startSongSelection();switchSelectionScope()});assert.equal(await page.locator('.song-select-row').count(),1);
 await page.reload();assert.deepEqual(await page.evaluate(()=>lists['测试二']),['b']);
 await page.evaluate(()=>{apply(1);bg={1:'snow-v2.png'};localStorage.mockBg=JSON.stringify(bg);refreshBackgrounds();closeSheet()});assert.equal(await page.locator('.hero').isVisible(),false);assert.equal(await page.locator('.brand').isVisible(),true);
 await page.evaluate(()=>apply(0));assert.equal(await page.locator('.hero').isVisible(),true);await page.evaluate(()=>apply(1));assert.equal(await page.locator('.hero').isVisible(),false);
 await page.reload();assert.equal(await page.locator('.hero').isVisible(),false);await page.evaluate(()=>{bg={};refreshBackgrounds()});assert.equal(await page.locator('.hero').isVisible(),true);
 await page.evaluate(()=>{bg={2:'train-v2.png'};applyPresetVisual({name:'测试搭配',scene:2})});assert.equal(await page.locator('.hero').isVisible(),false);
 await page.evaluate(()=>{filter=false;tracks=Array.from({length:40},(_,i)=>({uri:'track'+i,title:'很长的歌曲名称用于检查边界 '+i,size:5000000}));lists={'测试一':tracks.map(s=>s.uri),'测试二':[]};selectedList='测试一';show('library');startSongSelection()});
 for(const viewport of [{width:320,height:568},{width:412,height:892},{width:892,height:412}]){
  await page.setViewportSize(viewport);await page.waitForTimeout(650);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  for(const id of ['#selectAllSongs','#deleteSelected','#removeSelected','#addSelected']){const r=await page.locator(id).boundingBox();assert(r&&r.y>=0&&r.y+r.height<=viewport.height,JSON.stringify({viewport,id,r}));}
  await page.locator('.song-select-row input').first().check();await page.locator('#addSelected').click();const r=await page.locator('#confirmPlaylistTarget').boundingBox();assert(r&&r.y>=0&&r.y+r.height<=viewport.height);await page.locator('#cancelPlaylistTarget').click();
 }
await page.setViewportSize({width:412,height:892});await page.evaluate(()=>{tracks=[{uri:'demo.wav',title:'蓝色时差',builtin:true,size:2800000},{uri:'a',title:'我也曾想过一了百了.mp3',size:2200000}];deleted=[];lists={'测试一':['a']};selectedList='测试一';selectingSongs=false;bg={};refreshBackgrounds();apply(1);show('library')});
 fs.mkdirSync(path.resolve(__dirname,'../previews'),{recursive:true});await page.waitForTimeout(600);await page.screenshot({path:path.resolve(__dirname,'../previews/playlist-064.png')});
 await page.evaluate(()=>startSongSelection());await page.locator('#selectAllSongs').click();await page.screenshot({path:path.resolve(__dirname,'../previews/selection-064.png')});
 await page.locator('#selectionScope').click();await page.screenshot({path:path.resolve(__dirname,'../previews/add-064.png')});
 assert.deepEqual(errors,[]);await browser.close();console.log('PASS V064: filtered playlist/queue, scoped batch operations/select-all, storage failure, deletion cancel/cleanup, persistence, wallpaper/scene/preset, narrow/landscape controls.');
})().catch(e=>{console.error(e);process.exit(1)});
