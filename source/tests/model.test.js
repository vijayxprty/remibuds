import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fresh,migrate,dayKey,formatTime,parseTime,occurrences,record,active,undo,count,scheduledCount,updateRoutine,reminderPlan,dueAt,validateBackup,actionFor} from '../web/core.js';
const routine=(extra={})=>({id:'med',name:'My medicine',type:'tablet',times:['08:00','20:00'],days:[0,1,2,3,4,5,6],start:'',notes:'',amount:'1 tablet',remind:true,...extra});
const state=()=>{const s=fresh();s.routines=[routine()];return s;};
const prevDay=()=>{const d=new Date();d.setDate(d.getDate()-2);return dayKey(d);};

test('12-hour noon/midnight conversion is explicit and symmetric',()=>{
 assert.equal(parseTime(12,0,'AM'),'00:00');assert.equal(parseTime(12,0,'PM'),'12:00');assert.equal(parseTime(1,5,'PM'),'13:05');assert.equal(parseTime('09','07','AM'),'09:07');
 for(let h=0;h<24;h++){const t=String(h).padStart(2,'0')+':35';const displayed=formatTime(t,'12');const [clock,period]=displayed.split(' ');const [hour,minute]=clock.split(':');assert.equal(parseTime(hour,minute,period),t);}
 assert.equal(formatTime('00:00'),'12:00 AM');assert.equal(formatTime('12:00'),'12:00 PM');assert.equal(formatTime('23:59','24'),'23:59');assert.equal(formatTime('untimed'),'Any time');assert.throws(()=>parseTime(0,0,'AM'));assert.throws(()=>parseTime(12,60,'PM'));assert.throws(()=>parseTime(24,0,'24'));
});

test('migration preserves original records and medication occurrence identities',()=>{
 const s=state();s.version=1;s.routines[0].type='medicine';delete s.settings.timeFormat;const o=occurrences(s,'2026-10-27')[0],l=record(s,o);const m=migrate(s);
 assert.equal(m.version,2);assert.equal(m.routines[0].type,'tablet');assert.equal(m.settings.timeFormat,'12');assert.equal(m.logs[0].id,l.id);assert.equal(active(m,o.key).reported,l.reported);assert.equal(s.routines[0].type,'medicine');assert.deepEqual(migrate(m),m);
});

test('24-hour preference persists without changing internal schedule or reports',()=>{
 const s=state();s.settings.timeFormat='24';const m=migrate(JSON.parse(JSON.stringify(s)));assert.equal(m.settings.timeFormat,'24');assert.deepEqual(m.routines[0].times,['08:00','20:00']);assert.equal(formatTime(m.routines[0].times[1],m.settings.timeFormat),'20:00');
});

test('prior schedule targets and old names remain stable after edits and archive',()=>{
 const s=state();const o=occurrences(s,'2026-10-26')[0];record(s,o);const oldLog=JSON.stringify(s.logs[0]);
 updateRoutine(s,{...s.routines[0],name:'Changed name',times:['10:00']},'2026-10-27');
 assert.equal(scheduledCount(s,'med','2026-10-26'),2);assert.deepEqual(occurrences(s,'2026-10-26').map(o=>o.time),['08:00','20:00']);assert.equal(occurrences(s,'2026-10-26')[0].routine.name,'My medicine');assert.equal(scheduledCount(s,'med','2026-10-27'),1);assert.equal(count(s,'med','2026-10-26'),1);assert.equal(JSON.stringify(s.logs[0]),oldLog);
 updateRoutine(s,{...s.routines[0],archived:true},'2026-10-28');assert.equal(scheduledCount(s,'med','2026-10-27'),1);assert.equal(scheduledCount(s,'med','2026-10-28'),0);assert.equal(scheduledCount(s,'med','2026-10-26'),2);
});

test('editing a grouped time cannot silently change a member schedule',()=>{
 const s=state();s.groups=[{id:'g',name:'Morning',members:[{routineId:'med',time:'08:00'}]}];assert.throws(()=>updateRoutine(s,{...s.routines[0],times:['10:00']}),/group/);assert.deepEqual(s.routines[0].times,['08:00','20:00']);
});

test('untimed one-off tasks remain pending without generating notification plans',()=>{
 const s=state();s.settings.enabled=true;s.routines=[routine({id:'task',type:'todo',name:'Buy groceries',times:[],dueDate:prevDay()})];const os=occurrences(s);assert.equal(os.length,1);assert.equal(os[0].time,'untimed');assert.equal(os[0].date,prevDay());assert.equal(dueAt(os[0]),null);assert.equal(reminderPlan(s).length,0);const l=record(s,os[0]);assert.equal(occurrences(s).length,1);assert.equal(active(s,os[0].key).status,'recorded');undo(s,[l.id]);assert.equal(occurrences(s).length,1);
});

test('timed one-off todos repeat across midnight until recorded; medicines never repeat',()=>{
 const s=state();s.settings.enabled=true;s.routines.push(routine({id:'task',type:'todo',name:'Call home',times:['09:00'],dueDate:'2026-10-20',repeatMinutes:30}));
 const now=new Date('2026-10-27T09:10:00');let p=reminderPlan(s,now),task=p.find(x=>x.key==='task|2026-10-20|09:00');assert.ok(task);assert.equal(task.at,new Date('2026-10-27T09:30:00').getTime());assert.equal(p.filter(x=>x.key==='med|2026-10-27|08:00').length,0);
 record(s,{key:'task|2026-10-20|09:00',routine:s.routines[1],date:'2026-10-20',time:'09:00'});assert.equal(reminderPlan(s,now).some(x=>x.key.startsWith('task|')),false);
});

test('todo before-time reminders preserve due time and preferred formatting',()=>{
 const s=state();s.settings.enabled=true;s.routines=[routine({id:'task',type:'todo',name:'Leave for train',times:['00:10'],dueDate:'2026-10-28',dueMode:'before',beforeMinutes:30,repeatMinutes:0})];const o=occurrences(s,'2026-10-28')[0];assert.equal(dueAt(o),new Date('2026-10-27T23:40').getTime());const plan=reminderPlan(s,new Date('2026-10-27T23:00'));assert.equal(plan[0].at,dueAt(o));assert.match(plan[0].body,/12:10 AM/);assert.equal(o.time,'00:10');s.settings.timeFormat='24';assert.match(reminderPlan(s,new Date('2026-10-27T23:00'))[0].body,/00:10/);
});

test('snooze survives a midnight boundary and never changes completion state or schedule',()=>{
 const s=state();s.settings.enabled=true;s.routines[0].times=['23:55'];const o=occurrences(s,'2026-10-27')[0];s.snoozes[o.key]=new Date('2026-10-28T00:15').getTime();const p=reminderPlan(s,new Date('2026-10-28T00:05'));assert.equal(p.find(x=>x.key===o.key).at,s.snoozes[o.key]);assert.equal(active(s,o.key),undefined);assert.deepEqual(s.routines[0].times,['23:55']);record(s,o,'skipped');assert.equal(reminderPlan(s,new Date('2026-10-28T00:05')).some(x=>x.key===o.key),false);assert.equal(count(s,'med',o.date),0);
});

test('batch undo reverses only new selected records and leaves correction trail',()=>{
 const s=state();s.routines.push(routine({id:'cream',name:'Cream',type:'cream',times:['08:00']}));const os=occurrences(s,'2026-10-27');const priorOccurrence=os.find(o=>o.routine.id==='med'&&o.time==='08:00');const prior=record(s,priorOccurrence);let ids=[];for(const o of os){const l=record(s,o,'recorded',undefined,'batch1');if(l)ids.push(l.id);}assert.equal(ids.length,2);undo(s,ids);assert.equal(active(s,priorOccurrence.key).id,prior.id);assert.equal(s.logs.filter(l=>l.undone).length,2);assert.equal(count(s,'med','2026-10-27'),1);assert.equal(count(s,'cream','2026-10-27'),0);const restored=JSON.parse(JSON.stringify(s));assert.equal(count(restored,'med','2026-10-27'),1);
});

test('valid backup restores a private migrated copy with reminders off',()=>{
 const s=state();s.settings.enabled=true;record(s,occurrences(s,'2026-10-27')[0]);const b=validateBackup(s);assert.equal(b.settings.enabled,false);assert.equal(s.settings.enabled,true);assert.equal(b.logs[0].id,s.logs[0].id);b.routines[0].name='Other installation';assert.equal(s.routines[0].name,'My medicine');
});

test('backup rejects malformed schedules, duplicate active keys and unsafe identifiers',()=>{
 const s=state();const bad=structuredClone(s);bad.routines[0].times=['24:00'];assert.throws(()=>validateBackup(bad));const unsafe=structuredClone(s);unsafe.routines[0].id='x" onclick="bad';assert.throws(()=>validateBackup(unsafe));const log=record(s,occurrences(s,'2026-10-27')[0]);s.logs.push({...log,id:'different'});assert.throws(()=>validateBackup(s));assert.throws(()=>validateBackup({version:2,routines:[],groups:[],logs:[]}));
});

test('backup rejects invalid dates and snooze values; stale preferences and orphan groups are repaired',()=>{
 const s=state();for(const mutate of [x=>x.routines[0].start='2026-02-30',x=>x.snoozes.bad=Infinity]){const b=structuredClone(s);mutate(b);assert.throws(()=>validateBackup(b));}
 const stale=structuredClone(s);stale.settings.timeFormat='13';stale.settings.snooze=7;stale.groups.push({id:'g',name:'Group',members:[{routineId:'missing',time:'08:00'}]});
 const m=validateBackup(stale);assert.equal(m.settings.timeFormat,'12');assert.equal(m.settings.snooze,15);assert.equal(m.groups.length,0);
});

test('type-aware record wording does not invent quantities or prescriptions',()=>{
 assert.equal(actionFor('tablet'),'Mark taken');assert.equal(actionFor('gel'),'Mark applied');assert.equal(actionFor('patch'),'Mark applied');assert.equal(actionFor('injection'),'Record injection');assert.equal(actionFor('water'),'Record drink');assert.equal(actionFor('todo'),'Mark done');
});


test('several same-day edits retain the final schedule for that historical date',()=>{
 const s=state();updateRoutine(s,{...s.routines[0],times:['09:00']},'2026-10-25');updateRoutine(s,{...s.routines[0],times:['10:00','22:00']},'2026-10-25');updateRoutine(s,{...s.routines[0],times:['12:00']},'2026-10-26');assert.deepEqual(occurrences(s,'2026-10-25').map(o=>o.time),['10:00','22:00']);assert.equal(scheduledCount(s,'med','2026-10-24'),2);
});

test('display-equivalent times share one occurrence and reports preserve amount snapshot',()=>{
 const s=state();s.routines[0].times=[parseTime('8','00','AM')];const o=occurrences(s,'2026-10-27')[0],l=record(s,o);assert.equal(l.amount,'1 tablet');assert.equal(record(s,{...o,time:parseTime('08','00','24')}),null);s.routines[0].amount='2 tablets';assert.equal(l.amount,'1 tablet');assert.equal(count(s,'med','2026-10-27'),1);
});


test('correction retains original amount and scheduled target snapshot',()=>{
 const s=state();const o=occurrences(s,'2026-10-27')[0],l=record(s,o);undo(s,[l.id]);const corrected=record(s,{key:l.key,routine:{id:l.routineId,name:l.name,type:l.type},date:l.date,time:l.scheduled},'recorded','2026-10-27T07:45:00.000Z');assert.equal(corrected.amount,l.amount);assert.equal(corrected.targetCount,l.targetCount);assert.equal(count(s,'med',l.date),1);
});


test('archiving or disabling a routine cancels a historical occurrence snoozed into today',()=>{
 const s=state();s.settings.enabled=true;const yesterday='2026-10-27',now=new Date('2026-10-28T00:05');s.routines[0].times=['23:55'];const o=occurrences(s,yesterday)[0];s.snoozes[o.key]=new Date('2026-10-28T00:15').getTime();assert.ok(reminderPlan(s,now).some(p=>p.key===o.key));updateRoutine(s,{...s.routines[0],archived:true},'2026-10-28');assert.equal(reminderPlan(s,now).length,0);updateRoutine(s,{...s.routines[0],archived:false,remind:false},'2026-10-28');assert.equal(reminderPlan(s,now).length,0);assert.equal(scheduledCount(s,'med',yesterday),1);
});


test('overdue tasks completed today stay in Today progress but retain original due identity',()=>{
 const s=state();const today=dayKey(),due=prevDay();s.routines=[routine({id:'task',type:'todo',name:'Call',times:[],dueDate:due})];const o=occurrences(s,today)[0],l=record(s,o);const shown=occurrences(s,today);assert.equal(shown.length,1);assert.equal(active(s,shown[0].key).id,l.id);assert.equal(shown[0].date,due);assert.equal(count(s,'task',due),1);assert.equal(count(s,'task',today),0);assert.equal(shown[0].key,o.key);assert.equal(occurrences(s,'2099-01-01').length,0);
});

test('a medicine schedule edit after a recorded dose cannot offer another same-day dose',()=>{
 const s=state();s.routines[0].times=['08:00'];const date='2026-10-27',o=occurrences(s,date)[0];record(s,o);const edited=updateRoutine(s,{...s.routines[0],times:['09:00']},date);assert.equal(edited.scheduleFrom,'2026-10-28');assert.deepEqual(occurrences(s,date).map(x=>x.time),['08:00']);assert.equal(active(s,occurrences(s,date)[0].key).status,'recorded');assert.equal(scheduledCount(s,'med',date),1);assert.equal(count(s,'med',date),1);assert.deepEqual(occurrences(s,'2026-10-28').map(x=>x.time),['09:00']);
 // Editing notes afterward must not bring the pending new schedule into today.
 updateRoutine(s,{...s.routines[0],notes:'New note'},date);assert.equal(s.routines[0].scheduleFrom,'2026-10-28');assert.deepEqual(occurrences(s,date).map(x=>x.time),['08:00']);
});

test('skipped or undone medicine reports also prevent same-day schedule identity replacement',()=>{
 for(const status of ['recorded','skipped']){const s=state();s.routines[0].times=['08:00'];const date='2026-10-27',l=record(s,occurrences(s,date)[0],status);if(status==='recorded')undo(s,[l.id]);updateRoutine(s,{...s.routines[0],times:['09:00']},date);assert.equal(s.routines[0].scheduleFrom,'2026-10-28');assert.deepEqual(occurrences(s,date).map(x=>x.time),['08:00']);}
});


test('changing a timed task to untimed cancels its old repeated reminder',()=>{
 const s=state();s.settings.enabled=true;s.routines=[routine({id:'task',name:'Call',type:'todo',times:['09:00'],dueDate:'2026-10-27',repeatMinutes:15})];const now=new Date('2026-10-28T09:05');assert.ok(reminderPlan(s,now).length);updateRoutine(s,{...s.routines[0],times:[]},'2026-10-28');assert.equal(reminderPlan(s,now).length,0);assert.equal(occurrences(s,'2026-10-27')[0].time,'09:00');
});
