import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {validateBackup} from '../web/core.js';
test('2.0.1: backup with logs for a deleted routine restores, history kept',()=>{
  const input = JSON.parse(fs.readFileSync('/home/hatch/workspace/user/files/remibuds-private-backup__1.json','utf8'));
  const s = validateBackup(input);
  assert.equal(s.routines.length, 6);
  assert.equal(s.logs.length, 4);
  assert.equal(s.logs.filter(l=>l.routineId==='9b02304c-b8c2-477e-bfb1-daba0ab47add').length, 2);
  assert.equal(s.settings.enabled, false);
});
test('2.0.1: a truly malformed backup still fails, with a specific reason',()=>{
  assert.throws(()=>validateBackup({routines:[],logs:[],groups:[]}), /missing routines/);
  assert.throws(()=>validateBackup({version:2,routines:[],logs:[{id:'x',key:'k',routineId:'r',name:'n',type:'tablet',date:'2026-11-02',scheduled:'23:03',status:'recorded',reported:'not-a-date',created:'2026-11-02T23:03:00.000Z'}],groups:[],settings:{}}), /malformed/);
});
