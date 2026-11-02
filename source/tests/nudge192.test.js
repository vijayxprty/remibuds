import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fresh,migrate,validateBackup,deleteRoutine} from '../web/core.js';
// Nudge window: preserved on migrate, invalid resets to 9-21, fresh default is 9-21
test('nudge window preserved, invalid resets, fresh defaults to all day',()=>{
  assert.equal(fresh().settings.nudges.window,'all');
  let s=fresh(); s.settings.nudges.window='6-23'; s=migrate(s); assert.equal(s.settings.nudges.window,'6-23');
  let s2=fresh(); s2.settings.nudges.window='all'; s2=migrate(s2); assert.equal(s2.settings.nudges.window,'all');
  let s3=fresh(); s3.settings.nudges.window='bogus'; s3=migrate(s3); assert.equal(s3.settings.nudges.window,'all');
  let s4=fresh(); delete s4.settings.nudges.window; s4=migrate(s4); assert.equal(s4.settings.nudges.window,'all');
});
// Delete routine: removes routine + snoozes + group membership, keeps logs
test('deleteRoutine removes routine and group membership, keeps history',()=>{
  const s=fresh();
  s.routines.push({id:'r1',name:'Vitamin D',type:'tablet',times:['08:00'],days:[0,1,2,3,4,5,6]});
  s.groups.push({id:'g1',name:'Morning',members:[{routineId:'r1',time:'08:00'}]});
  s.logs.push({id:'l1',routineId:'r1',status:'recorded',date:'2026-11-03',reported:'2026-11-03T08:00:00',created:'2026-11-03T08:00:00'});
  s.snoozes['r1|2026-11-03|08:00']=1;
  deleteRoutine(s,'r1');
  assert.equal(s.routines.find(r=>r.id==='r1'),undefined);
  assert.equal(s.groups.length,0);
  assert.equal(s.logs.length,1);
  assert.equal(s.snoozes['r1|2026-11-03|08:00'],undefined);
});
// Backup round-trip: exported routines (including never-recorded) survive validate+migrate
test('exported backup with unrecorded routine restores fully',()=>{
  const s=fresh();
  s.settings.nudges.window='6-23';
  s.routines.push({id:'rA',name:'Evening walk',type:'walk',times:['18:00'],days:[0,1,2,3,4,5,6]});
  s.routines.push({id:'rB',name:'Vitamin D',type:'tablet',times:['08:00'],days:[0,1,2,3,4,5,6]});
  const exported=JSON.parse(JSON.stringify(s));
  const restored=migrate(validateBackup(exported));
  assert.equal(restored.routines.length,2);
  assert.ok(restored.routines.find(r=>r.id==='rA'));
  assert.equal(restored.settings.nudges.window,'6-23');
});
// Early alerts: default on, explicit off survives migrate, missing resets to on
import {test as test2} from 'node:test';
test2('earlyAlerts default on, off survives, missing resets to on',()=>{
  const {fresh: f2, migrate: m2} = {fresh, migrate};
  const a=f2(); assert.equal(a.settings.earlyAlerts,true);
  let b=f2(); b.settings.earlyAlerts=false; b=m2(b); assert.equal(b.settings.earlyAlerts,false);
  let c=f2(); delete c.settings.earlyAlerts; c=m2(c); assert.equal(c.settings.earlyAlerts,true);
});
