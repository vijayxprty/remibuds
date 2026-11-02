import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {fresh,migrate,occurrences,updateRoutine,validateBackup,ART_CATEGORIES,ART_GROUPS,ART_KEYS,ART_FILES,artSrc,guessArt,guessArtForFamily,familyForType,artFor,DONATE_UPI_ID} from '../web/core.js';

const root=fileURLToPath(new URL('..',import.meta.url));
const read=rel=>fs.readFileSync(path.join(root,rel),'utf8');

test('art catalogue has one illustration per routine category with files on disk',()=>{
 assert.equal(ART_CATEGORIES.length,11);
 assert.deepEqual(ART_CATEGORIES.map(c=>c.key),['medicine-morning','medicine-afternoon','medicine-night','meal','water','walk','cycling','gym','rest','sleep','generic']);
 assert.equal(new Set(ART_CATEGORIES.map(c=>c.key)).size,11);
 for(const c of ART_CATEGORIES){
  assert.ok(c.label.length>0);
  assert.ok(c.file.endsWith('.svg'));
  assert.ok(fs.existsSync(path.join(root,'web/art',c.file)),`missing ${c.file}`);
  assert.ok(fs.statSync(path.join(root,'web/art',c.file)).size>500,`${c.file} suspiciously small`);
  assert.equal(artSrc(c.key),'art/'+c.file);
 }
 assert.equal(ART_KEYS.size,11);
 assert.equal(ART_FILES['sleep'],'art-sleep.svg');
 assert.equal(ART_FILES['medicine-night'],'art-medicine-night.svg');
});

test('keyword guess maps each family to the right illustration',()=>{
 assert.equal(guessArt('Morning medicine','tablet','08:00'),'medicine-morning');
 assert.equal(guessArt('dawai','tablet','08:00'),'medicine-morning');
 assert.equal(guessArt('Evening tablet','tablet','20:00'),'medicine-night');
 assert.equal(guessArt('दवाई','tablet','09:00'),'medicine-morning');
 assert.equal(guessArt('Breakfast','meal'),'meal');
 assert.equal(guessArt('Lunch with family','custom'),'meal');
 assert.equal(guessArt('Drink water','custom'),'water');
 assert.equal(guessArt('पानी पीना','custom'),'water');
 assert.equal(guessArt('Morning walk','custom'),'walk');
 assert.equal(guessArt('Evening walk','custom'),'walk');
 assert.equal(guessArt('Cycle ride','custom'),'cycling');
 assert.equal(guessArt('साइकिल','custom'),'cycling');
 assert.equal(guessArt('Gym session','custom'),'gym');
 assert.equal(guessArt('Yoga','custom'),'gym');
 assert.equal(guessArt('Rest after lunch','custom'),'rest');
 assert.equal(guessArt('आराम','custom'),'rest');
 assert.equal(guessArt('Sleep early','custom'),'sleep');
 assert.equal(guessArt('Nap','custom'),'sleep');
});

test('guess falls back by routine type, then generic; word boundaries are respected',()=>{
 assert.equal(guessArt('Blood pressure pill','tablet','07:30'),'medicine-morning');
 assert.equal(guessArt('Blood pressure pill','tablet','19:30'),'medicine-night');
 assert.equal(guessArt('Untitled','tablet','07:30'),'medicine-morning');
 assert.equal(guessArt('Untitled','tablet','19:30'),'medicine-night');
 assert.equal(guessArt('Untitled','water'),'water');
 assert.equal(guessArt('Untitled','meal'),'meal');
 assert.equal(guessArt('Untitled','walk'),'walk');
 assert.equal(guessArt('Meditation','custom'),'generic');
 assert.equal(guessArt('Call home','todo'),'generic');
 assert.equal(guessArt('','todo'),'generic');
 assert.equal(guessArt('Buy groceries','todo'),'generic');
});

test('stored illustration wins; invalid stored values fall back to the guess',()=>{
 assert.equal(artFor({name:'Walk',type:'walk',times:['08:00'],art:'sleep'}),'sleep');
 assert.equal(artFor({name:'Walk',type:'walk',times:['08:00'],art:'nope'}),'walk');
 assert.equal(artFor({name:'Walk',type:'walk',times:['08:00']}),'walk');
 assert.equal(artFor(null),'generic');
});

test('template picker default is the guessed illustration and picking a template stores it',()=>{
 const s=fresh();
 const saved=updateRoutine(s,{id:'w',name:'Morning walk',type:'walk',times:['07:00'],days:[0,1,2,3,4,5,6],start:'',notes:'',amount:'',remind:true,art:artFor({name:'Morning walk',type:'walk',times:['07:00']})});
 assert.equal(saved.art,'walk');
 const changed=updateRoutine(s,{...saved,art:'cycling'});
 assert.equal(changed.art,'cycling');
 const historic=occurrences(s,'2026-10-27');
 assert.ok(historic.length>0);
});

test('backup accepts a valid illustration and repairs an unknown one; migration normalises',()=>{
 const s=fresh();
 s.routines=[{id:'a',name:'Walk',type:'walk',times:['07:00'],days:[0,1,2,3,4,5,6],start:'',notes:'',amount:'',remind:true,art:'walk'}];
 assert.equal(validateBackup(s).routines[0].art,'walk');
 const bad=structuredClone(s);bad.routines[0].art='leaf';
 assert.equal(validateBackup(bad).routines[0].art,undefined); // 2.0.1: unknown art is cleared, not a reason to reject a whole backup
 const legacy=structuredClone(s);legacy.routines[0].art='daykind';
 assert.equal(migrate(legacy).routines[0].art,undefined);
 assert.equal(artFor(migrate(legacy).routines[0]),'walk');
});

test('medicine time bands split morning, afternoon and night at 12:00 and 18:00',()=>{
 assert.equal(guessArt('Dawai','tablet','11:59'),'medicine-morning');
 assert.equal(guessArt('Dawai','tablet','12:00'),'medicine-afternoon');
 assert.equal(guessArt('Dawai','tablet','17:59'),'medicine-afternoon');
 assert.equal(guessArt('Dawai','tablet','18:00'),'medicine-night');
 assert.equal(guessArt('Dawai','tablet','23:30'),'medicine-night');
 assert.equal(guessArt('Dawai','tablet',''),'medicine-morning');
});

test('illustration groups match the Kind picker: medicine 3, activity 7, to-do 1',()=>{
 assert.deepEqual(ART_GROUPS.medicine,['medicine-morning','medicine-afternoon','medicine-night']);
 assert.deepEqual(ART_GROUPS.activity,['meal','water','walk','cycling','gym','rest','sleep']);
 assert.deepEqual(ART_GROUPS.todo,['generic']);
 assert.equal(familyForType('tablet'),'medicine');
 assert.equal(familyForType('cream'),'medicine');
 assert.equal(familyForType('water'),'activity');
 assert.equal(familyForType('custom'),'activity');
 assert.equal(familyForType('todo'),'todo');
});

test('family-constrained guess stays inside the group; Kind change re-guesses',()=>{
 assert.equal(guessArtForFamily('Cycle ride','custom','18:00','activity'),'cycling');
 assert.equal(guessArtForFamily('Cycle ride','custom','18:00','medicine'),null);
 assert.equal(guessArtForFamily('Dawai','tablet','20:00','medicine'),'medicine-night');
 assert.equal(guessArtForFamily('Morning walk','custom','07:00','todo'),null);
 assert.equal(guessArtForFamily('Dawai','tablet','08:00','todo'),null);
 assert.equal(guessArtForFamily('Drink water','water','','activity'),'water');
 assert.equal(guessArtForFamily('Call home','todo','','todo'),'generic');
});

test('stored art outside its Kind group is re-guessed; custom generic art is kept',()=>{
 assert.equal(artFor({name:'Dawai',type:'tablet',times:['20:00'],art:'cycling'}),'medicine-night');
 assert.equal(artFor({name:'Dawai',type:'tablet',times:['08:00'],art:'sleep'}),'medicine-morning');
 assert.equal(artFor({name:'Buy milk',type:'todo',times:['18:00'],art:'walk'}),'generic');
 assert.equal(artFor({name:'Call home',type:'custom',times:['18:00'],art:'generic'}),'generic');
 assert.equal(artFor({name:'Evening walk',type:'custom',times:['18:00'],art:'cycling'}),'cycling');
});

test('version and label strings carry the Remibuds 2.0.1 identity with no Daykind or leaf branding',()=>{
 const appJs=read('web/app.js');
 assert.match(appJs,/VERSION='2\.0.4',CODE=17,NAME='Remibuds'/);
 assert.equal(JSON.parse(read('web/manifest.json')).name,'Remibuds');
 assert.match(read('web/index.html'),/<title>Remibuds<\/title>/);
 assert.match(read('android/app/src/main/res/values/strings.xml'),/>Remibuds</);
 assert.match(read('android/app/build.gradle'),/versionCode 17; versionName '2.0.4'/);
 for(const rel of ['web/app.js','web/core.js','web/index.html','web/manifest.json','web/icons.js','android/app/build.gradle','android/app/src/main/res/values/strings.xml','android/app/src/main/java/app/dailycare/MainActivity.java']){
  const text=read(rel);
  assert.ok(!text.includes('Daykind'),`${rel} still mentions Daykind`);
  assert.ok(!text.includes('Routibuds'),`${rel} mentions Routibuds`);
  assert.ok(!text.includes('leaf'),`${rel} still mentions leaf branding`);
 }
 assert.match(read('web/app.js'),/Version \${VERSION} · build \${CODE}/);
});
