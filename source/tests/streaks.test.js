import {test} from 'node:test';import assert from 'node:assert/strict';
import {fresh,migrate,streakInfo,NUDGE_DEFAULTS} from '../web/core.js';
function stateWith(days, routineId='r1'){
  const s=fresh();s.routines.push({id:routineId,name:'Vitamin',type:'tablet',times:['08:00'],days:[0,1,2,3,4,5,6],start:'',remind:true});
  for(const d of days)s.logs.push({id:'l-'+d,key:`${routineId}|${d}|08:00`,routineId,name:'Vitamin',type:'tablet',date:d,scheduled:'08:00',status:'recorded',reported:d+'T08:00:00.000Z',created:d+'T08:00:00.000Z'});
  return s;
}
const today=new Date();const fmt=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
const ago=n=>{const d=new Date(today);d.setDate(d.getDate()-n);return fmt(d)};
test('streak: empty logs give zero',()=>{assert.deepEqual(streakInfo(fresh(),'r1'),{current:0,best:0})});
test('streak: three consecutive days including today',()=>{const s=stateWith([ago(2),ago(1),ago(0)]);assert.deepEqual(streakInfo(s,'r1'),{current:3,best:3})});
test('streak: alive through yesterday when today is not yet recorded',()=>{const s=stateWith([ago(3),ago(2),ago(1)]);const r=streakInfo(s,'r1');assert.equal(r.current,3);assert.equal(r.best,3)});
test('streak: gap breaks the run but best survives',()=>{const s=stateWith([ago(5),ago(4),ago(1),ago(0)]);const r=streakInfo(s,'r1');assert.equal(r.current,2);assert.equal(r.best,2)});
test('streak: undone and skipped records do not count',()=>{const s=stateWith([ago(1),ago(0)]);s.logs.push({id:'u',key:'r1|'+ago(2)+'|08:00',routineId:'r1',name:'Vitamin',type:'tablet',date:ago(2),scheduled:'08:00',status:'recorded',undone:'x',reported:'',created:''});s.logs.push({id:'sk',key:'r1|'+ago(3)+'|08:00',routineId:'r1',name:'Vitamin',type:'tablet',date:ago(3),scheduled:'08:00',status:'skipped',reported:'',created:''});assert.equal(streakInfo(s,'r1').current,2)});
test('nudges: defaults include his list with phone/office/jobs more frequent',()=>{const byId=Object.fromEntries(NUDGE_DEFAULTS.map(t=>[t.id,t]));for(const id of ['water','shower','walk','sunset','remind','eatwell','noovereat','healthy','phone','productive','office','jobs'])assert.ok(byId[id],id);assert.equal(byId.phone.weight,3);assert.equal(byId.office.weight,3);assert.equal(byId.jobs.weight,3);assert.ok(NUDGE_DEFAULTS.every(t=>t.text&&typeof t.on==='boolean'))});
test('nudges: old states migrate to defaults, existing choices survive',()=>{const old=migrate({version:2,routines:[],groups:[],logs:[],snoozes:{},settings:{enabled:false}});assert.equal(old.settings.nudges.enabled,false);assert.ok(old.settings.nudges.texts.length>0);const kept=migrate({version:2,routines:[],groups:[],logs:[],snoozes:{},settings:{nudges:{enabled:true,texts:[{id:'x',emoji:'',text:'Mine',on:false,weight:1,custom:true}]}}});assert.equal(kept.settings.nudges.enabled,true);assert.equal(kept.settings.nudges.texts[0].text,'Mine');assert.equal(kept.settings.nudges.texts[0].on,false)});
