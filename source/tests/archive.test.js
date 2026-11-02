import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fresh,migrate,record,undo,occurrences,deleteRoutine,updateRoutine,dayKey} from '../web/core.js';

function routine(id,name,type='tablet'){
 return {id,name,type,times:['08:00'],days:[0,1,2,3,4,5,6],start:'',notes:'',remind:true,archived:false};
}

test('archiving never removes history; records keep their own snapshot',()=>{
 const s=fresh();
 s.routines.push(routine('r1','Morning dawai'));
 const o={key:'r1|2026-10-28|08:00',routine:s.routines[0],date:'2026-10-28',time:'08:00'};
 record(s,o,'recorded');
 updateRoutine(s,{...s.routines[0],archived:true});
 assert.equal(s.logs.length,1);
 assert.equal(s.logs[0].name,'Morning dawai');
 assert.equal(s.logs[0].type,'tablet');
 const tomorrow=dayKey(new Date(Date.now()+86400000));
 assert.equal(occurrences(s,tomorrow).length,0,'archived routine offers no future occurrences');
 const hist=s.logs.filter(l=>!l.undone);
 assert.equal(hist.length,1,'history still holds the record');
});

test('deleteRoutine removes the definition, memberships and snoozes but never the logs',()=>{
 const s=fresh();
 s.routines.push(routine('r1','A'),routine('r2','B'));
 s.groups.push({id:'g1',name:'Morning',members:[{routineId:'r1',time:'08:00'},{routineId:'r2',time:'08:00'}]});
 s.groups.push({id:'g2',name:'Solo',members:[{routineId:'r1',time:'08:00'}]});
 s.snoozes['r1|2026-10-28|08:00']=Date.now()+60000;
 s.snoozes['r2|2026-10-28|08:00']=Date.now()+60000;
 record(s,{key:'r1|2026-10-28|08:00',routine:s.routines[0],date:'2026-10-28',time:'08:00'},'recorded');
 const removed=deleteRoutine(s,'r1');
 assert.equal(removed.name,'A');
 assert.equal(s.routines.length,1);
 assert.equal(s.routines[0].id,'r2');
 assert.equal(s.groups.length,1,'the group left empty is removed');
 assert.deepEqual(s.groups[0].members,[{routineId:'r2',time:'08:00'}]);
 assert.equal(s.snoozes['r1|2026-10-28|08:00'],undefined);
 assert.notEqual(s.snoozes['r2|2026-10-28|08:00'],undefined);
 assert.equal(s.logs.length,1,'logs untouched');
 assert.equal(s.logs[0].name,'A','log snapshot survives the routine');
 assert.equal(occurrences(s,'2026-10-28').filter(x=>x.routine.id==='r1').length,0,'no future occurrences');
 assert.equal(deleteRoutine(s,'missing'),null);
});

test('undo still works on records whose routine was deleted',()=>{
 const s=fresh();
 s.routines.push(routine('r1','A'));
 const l=record(s,{key:'r1|2026-10-28|08:00',routine:s.routines[0],date:'2026-10-28',time:'08:00'},'recorded');
 deleteRoutine(s,'r1');
 undo(s,[l.id]);
 assert.ok(s.logs[0].undone,'undo marks the record even after the routine is gone');
 assert.equal(s.logs[0].name,'A');
});

test('migration backfills missing log snapshots without rewriting history',()=>{
 const s=fresh();
 s.routines.push(routine('r1','Kept name','cream'));
 s.logs.push({id:'l1',key:'r1|2026-10-27|08:00',routineId:'r1',date:'2026-10-27',scheduled:'08:00',status:'recorded',reported:'2026-10-27T08:05:00.000Z',created:'2026-10-27T08:05:00.000Z'});
 s.logs.push({id:'l2',key:'r9|2026-10-27|09:00',routineId:'r9',name:'Already named',type:'tablet',date:'2026-10-27',scheduled:'09:00',status:'skipped',reported:'2026-10-27T09:00:00.000Z',created:'2026-10-27T09:00:00.000Z'});
 const m=migrate(s);
 assert.equal(m.logs[0].name,'Kept name');
 assert.equal(m.logs[0].type,'cream');
 assert.equal(m.logs[1].name,'Already named','present values are never overwritten');
 const orphan=fresh();
 orphan.logs.push({id:'l3',key:'rx|2026-10-27|08:00',routineId:'rx',date:'2026-10-27',scheduled:'08:00',status:'recorded',reported:'2026-10-27T08:00:00.000Z',created:'2026-10-27T08:00:00.000Z'});
 const m2=migrate(orphan);
 assert.equal(m2.logs[0].name,'Archived item');
 assert.equal(m2.logs[0].type,'other');
});
