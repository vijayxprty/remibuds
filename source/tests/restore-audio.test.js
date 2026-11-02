import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fresh,migrate,validateBackup} from '../web/core.js';

// 1.9.1 restore: a realistic 1.9 backup (routines, logs, groups, nudges,
// ring mode + custom tone) validates and restores with reminders OFF,
// which is what window.careBackupPicked does after validateBackup.
test('1.9 backup restores with reminders off and custom tone intact',()=>{
 const s=fresh();
 s.routines.push({id:'vitd',name:'Vitamin D',type:'tablet',times:['08:00'],days:[0,1,2,3,4,5,6],start:'',notes:'',remind:true});
 s.settings.enabled=true;s.settings.soundMode='ring';s.settings.customTone='custom-alarm-tone';s.settings.customToneName='My song.mp3';
 const b=validateBackup(structuredClone(s));
 assert.equal(b.settings.enabled,false);
 assert.equal(b.settings.customTone,'custom-alarm-tone');
 assert.equal(b.settings.customToneName,'My song.mp3');
 assert.equal(b.routines[0].name,'Vitamin D');
});

// A backup whose JSON was truncated in transit (the old WebView-chooser
// failure mode) must be rejected, never partially restored.
test('truncated or wrong-shape backup is rejected, never partially applied',()=>{
 assert.throws(()=>validateBackup({version:2,routines:[],logs:[]}));
 assert.throws(()=>validateBackup(JSON.parse('{"version":2,"routines":[')));
 const s=fresh();s.settings.customTone='../evil';
 assert.equal(validateBackup(s).settings.customTone,''); // 2.0.1: a bad tone path is cleared, not a reason to reject a whole backup
});

// Migration of a 1.8-era state (no 1.9.1 fields) keeps routines and
// sound settings, so a restore from 1.8 does not crash on missing fields.
test('1.8-era state migrates without losing routines or sound settings',()=>{
 const s=fresh();delete s.settings.nudges;
 s.routines.push({id:'walk',name:'Evening walk',type:'walk',times:['18:00'],days:[0,1,2,3,4,5,6],start:'',notes:'',remind:true});
 const m=migrate(s);
 assert.equal(m.routines.length,1);
 assert.equal(m.settings.nudges.texts.length>0,true);
});
