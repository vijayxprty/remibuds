import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fresh,migrate,validateBackup} from '../web/core.js';

test('reminder-sound settings default to notification only with no tone chosen',()=>{
 const s=fresh();
 assert.equal(s.settings.soundMode,'notification');
 assert.equal(s.settings.toneUri,'');
 assert.equal(s.settings.toneName,'');
 assert.equal(s.settings.customTone,'');
 assert.equal(s.settings.customToneName,'');
});

test('migration fills reminder-sound defaults on legacy states without losing settings',()=>{
 const legacy=fresh();
 delete legacy.settings.soundMode;delete legacy.settings.toneUri;delete legacy.settings.toneName;delete legacy.settings.customTone;delete legacy.settings.customToneName;
 legacy.settings.enabled=true;legacy.settings.snooze=30;
 const m=migrate(legacy);
 assert.equal(m.settings.soundMode,'notification');
 assert.equal(m.settings.toneUri,'');
 assert.equal(m.settings.customTone,'');
 assert.equal(m.settings.enabled,true);
 assert.equal(m.settings.snooze,30);
});

test('backup accepts ring mode with tone fields and repairs bad sound values',()=>{
 const s=fresh();
 s.settings.soundMode='ring';s.settings.toneUri='content://media/internal/audio/media/7';s.settings.toneName='Morning alarm';
 assert.equal(validateBackup(s).settings.soundMode,'ring');
 assert.equal(validateBackup(s).settings.toneName,'Morning alarm');
 const custom=structuredClone(s);custom.settings.toneUri='';custom.settings.customTone='custom-alarm-tone';custom.settings.customToneName='My song.mp3';
 assert.equal(validateBackup(custom).settings.customTone,'custom-alarm-tone');
 const badMode=structuredClone(s);badMode.settings.soundMode='loud';
 assert.equal(validateBackup(badMode).settings.soundMode,'notification');
 const badPath=structuredClone(s);badPath.settings.customTone='../evil';
 assert.equal(validateBackup(badPath).settings.customTone,'');
 const badSlash=structuredClone(s);badSlash.settings.customTone='a/b';
 assert.equal(validateBackup(badSlash).settings.customTone,'');
 const badType=structuredClone(s);badType.settings.toneUri=42;
 assert.equal(validateBackup(badType).settings.toneUri,'');
});
