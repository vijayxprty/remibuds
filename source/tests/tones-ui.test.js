import {test} from 'node:test';
import assert from 'node:assert/strict';
import {visibleTones,previewNext,TONE_COLLAPSED_COUNT,TYPE_LABELS,MEDICINE_TYPES,ACTIVITY_TYPES} from '../web/core.js';

test('tone list shows a small list first and the full list when expanded',()=>{
 assert.equal(TONE_COLLAPSED_COUNT,5);
 const tones=[{uri:'',name:'Default alarm tone'},{uri:'a',name:'A'},{uri:'b',name:'B'},{uri:'c',name:'C'},{uri:'d',name:'D'},{uri:'e',name:'E'},{uri:'f',name:'F'}];
 assert.equal(visibleTones(tones,false).length,5);
 assert.deepEqual(visibleTones(tones,false).map(t=>t.name),['Default alarm tone','A','B','C','D']);
 assert.equal(visibleTones(tones,true).length,7);
 assert.equal(visibleTones(tones.slice(0,3),false).length,3,'short lists stay whole');
 assert.deepEqual(visibleTones(null,false),[]);
 assert.deepEqual(visibleTones([],true),[]);
});

test('preview state machine: one at a time, tapping the playing tone stops it',()=>{
 assert.equal(previewNext(null,'a'),'a');
 assert.equal(previewNext('a','a'),null,'tapping the playing tone stops the preview');
 assert.equal(previewNext('a','b'),'b','tapping another tone switches the preview');
 assert.equal(previewNext(null,''),'');
 assert.equal(previewNext('',''),null);
});

test('type labels cover every routine type exactly once',()=>{
 const all=[...MEDICINE_TYPES,...ACTIVITY_TYPES];
 for(const t of all)assert.ok(TYPE_LABELS[t],`missing label for ${t}`);
 assert.equal(TYPE_LABELS.todo,'To-do');
 assert.equal(TYPE_LABELS.tablet,'Tablet');
});
