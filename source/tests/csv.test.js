import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fresh,record,undo,historyCsv,earliestLogDate,csvFilename} from '../web/core.js';

function stateWithLogs(){
 const s=fresh();
 const mk=(key,name,type,date,time,status='recorded',reported)=>record(s,{key,routine:{id:'r1',name,type,amount:''},date,time},status,reported);
 const now=new Date();
 const hourAgo=new Date(now.getTime()-3600000).toISOString();
 mk('r1|2026-10-26|08:00','Morning, "dawai"','tablet','2026-10-26','08:00','recorded',hourAgo);
 mk('r1|2026-10-27|08:00','Plain name','tablet','2026-10-27','08:00','recorded',now.toISOString());
 const skipped=mk('r1|2026-10-28|08:00','Skipped one','tablet','2026-10-28','08:00','skipped',now.toISOString());
 const undone=mk('r1|2026-10-29|08:00','Undone one','tablet','2026-10-29','08:00','recorded',hourAgo);
 undo(s,[undone.id]);
 const nl=mk('r1|2026-10-30|08:00','Line one\nLine two','tablet','2026-10-30','08:00','recorded',now.toISOString());
 return {s,skipped,undone,nl};
}

test('CSV header row matches the record schema',()=>{
 const {s}=stateWithLogs();
 const lines=historyCsv(s).trim().split('\n');
 assert.equal(lines[0],'Date,Scheduled time,Name,Kind/Type,Status,Recorded at,Entered afterwards,Undone');
});

test('CSV escapes commas, quotes and newlines',()=>{
 const {s}=stateWithLogs();
 const csv=historyCsv(s);
 assert.ok(csv.includes('"Morning, ""dawai"""'),'comma+quote name must be quoted and doubled');
 assert.ok(csv.includes('"Line one\nLine two"'),'newline in name must be quoted');
});

test('CSV range filtering is inclusive on both boundary dates',()=>{
 const {s}=stateWithLogs();
 const range=historyCsv(s,'2026-10-27','2026-10-28').trim().split('\n');
 assert.equal(range.length,3,'header + 27th + 28th');
 assert.ok(range[1].includes('2026-10-27'));
 assert.ok(range[2].includes('2026-10-28'));
 const single=historyCsv(s,'2026-10-26','2026-10-26').trim().split('\n');
 assert.equal(single.length,2);
 assert.ok(single[1].includes('2026-10-26'));
});

test('CSV all-data export includes every row; empty range exports headers only',()=>{
 const {s}=stateWithLogs();
 const rows=historyCsv(s).split(/\n(?=\d{4}-\d{2}-\d{2},)/);
 assert.equal(rows.length,6,'header + 5 rows (one name carries an embedded newline)');
 for(const name of ['Morning,','Plain name','Skipped one','Undone one','Line one'])assert.ok(historyCsv(s).includes(name));
 const empty=historyCsv(s,'2026-11-01','2026-11-02');
 assert.equal(empty.trim(),'Date,Scheduled time,Name,Kind/Type,Status,Recorded at,Entered afterwards,Undone');
});

test('CSV status marks undone rows as not counting and flags entered-afterwards',()=>{
 const {s}=stateWithLogs();
 const lines=historyCsv(s).trim().split('\n');
 const undoneLine=lines.find(l=>l.includes('Undone one'));
 assert.ok(undoneLine.includes('Undone (does not count)'));
 assert.ok(undoneLine.split(',').length>=8);
 const skippedLine=lines.find(l=>l.includes('Skipped one'));
 assert.ok(skippedLine.includes(',Skipped,'));
 const oldLine=lines.find(l=>l.includes('Morning,'));
 assert.ok(oldLine.includes(',Yes,'),'reported an hour before creation counts as entered afterwards');
 const freshLine=lines.find(l=>l.includes('Plain name'));
 assert.ok(freshLine.includes(',No,'),'reported just now is not entered afterwards');
});

test('CSV filenames and earliest history date',()=>{
 assert.equal(csvFilename('2026-10-26','2026-10-28',false),'remibuds-history-2026-10-26-to-2026-10-28.csv');
 assert.equal(csvFilename('','',true),'remibuds-history-all.csv');
 const {s}=stateWithLogs();
 assert.equal(earliestLogDate(s),'2026-10-26');
 assert.equal(earliestLogDate(fresh()),'');
});
