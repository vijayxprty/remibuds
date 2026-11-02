import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fresh,migrate} from '../web/core.js';
test('nudge dropdown windows preserved: 10-23, 11-23, 8-21, 9-23',()=>{
  for(const w of ['10-23','11-23','8-21','9-23','6-23','7-21']){
    let s=fresh(); s.settings.nudges.window=w; s=migrate(s); assert.equal(s.settings.nudges.window,w);
  }
});
test('nudge window default is all day, invalid resets to all',()=>{
  assert.equal(fresh().settings.nudges.window,'all');
  let s=fresh(); s.settings.nudges.window='9-9'; s=migrate(s); assert.equal(s.settings.nudges.window,'all');
});
import {test as test2} from 'node:test';
test2('2.0 one-time: old default 9-21 resets to all, later 9-21 pick kept',()=>{
  let s=fresh(); s.settings.nudges.window='9-21'; s.settings.nudges.windowMigrated20=undefined; s=migrate(s);
  assert.equal(s.settings.nudges.window,'all'); assert.equal(s.settings.nudges.windowMigrated20,true);
  s.settings.nudges.window='9-21'; s=migrate(s); assert.equal(s.settings.nudges.window,'9-21');
});
