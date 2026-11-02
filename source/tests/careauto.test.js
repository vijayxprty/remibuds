import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {resolveCareAuto} from '../web/core.js';

const root=fileURLToPath(new URL('..',import.meta.url));
const read=rel=>fs.readFileSync(path.join(root,rel),'utf8');

function occ(key,group){
 const [id,date,time]=key.split('|');
 return {key,routine:{id},date,time,group};
}

test('resolveCareAuto matches a single occurrence by key',()=>{
 const os=[occ('r1|2026-10-29|08:00'),occ('r2|2026-10-29|09:00')];
 const found=resolveCareAuto(os,'r1|2026-10-29|08:00');
 assert.equal(found.occurrence.key,'r1|2026-10-29|08:00');
 assert.equal(found.group,undefined);
});

test('resolveCareAuto reports a shared-reminder card without a single occurrence',()=>{
 const g={id:'g1'};
 const os=[occ('r1|2026-10-29|08:00',g),occ('r2|2026-10-29|08:00',g)];
 const found=resolveCareAuto(os,'g1|2026-10-29|08:00');
 assert.equal(found.group,true);
 assert.equal(found.occurrence,undefined);
});

test('resolveCareAuto matches nothing for unknown, empty or missing keys',()=>{
 const os=[occ('r1|2026-10-29|08:00')];
 assert.equal(resolveCareAuto(os,'zzz|2026-10-29|08:00'),null);
 assert.equal(resolveCareAuto(os,''),null);
 assert.equal(resolveCareAuto(os,null),null);
 assert.equal(resolveCareAuto([], 'r1|2026-10-29|08:00'),null);
 assert.equal(resolveCareAuto(null,'r1|2026-10-29|08:00'),null);
});

test('notification actions apply once through the existing record/snooze paths, with a card-found guard',()=>{
 const appJs=read('web/app.js');
 assert.match(appJs,/launchAuto/);
 assert.match(appJs,/autoHandled/);
 assert.match(appJs,/applyCareAuto/);
 assert.match(appJs,/resolveCareAuto/);
 // Record goes through the single existing log() path; snooze through the same
 // snooze-length setting as the in-app button.
 assert.match(appJs,/log\(o,'recorded'\)/);
 assert.match(appJs,/state\.snoozes\[o\.key\]=Date\.now\(\)\+state\.settings\.snooze\*60000/);
 // Card not found: no action, toast says so. Group card: members stay explicit.
 assert.match(appJs,/That reminder could not be found, so nothing was recorded/);
 assert.match(appJs,/mark them there/);
 // The action is cleared when applied (keyHandled) and when it cannot be
 // applied (autoHandled), so a stale action can never fire on a later render.
 assert.match(appJs,/keyHandled/);
 assert.match(appJs,/autoHandled/);
});

test('ring warning names the exact full-screen switch',()=>{
 const appJs=read('web/app.js');
 assert.match(appJs,/Turn on \\'Full-screen notifications\\' for Remibuds on the screen that opens/);
});

test('ring service wiring: service owns the notification, actions carry careAuto, delete stops the service',()=>{
 const rem=read('android/app/src/main/java/app/dailycare/Reminders.java');
 assert.match(rem,/buildNotification\(/);
 assert.match(rem,/RingService\.start\(c,entry\)/);
 assert.match(rem,/careAuto/);
 assert.match(rem,/Mark taken/);
 assert.match(rem,/RING_STOP/);
 assert.match(rem,/stopIfShowing/);
 const svc=read('android/app/src/main/java/app/dailycare/RingService.java');
 assert.match(svc,/FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK/);
 assert.match(svc,/CAP_MS/);
 const recv=read('android/app/src/main/java/app/dailycare/ReminderReceiver.java');
 assert.match(recv,/RING_STOP/);
 const manifest=read('android/app/src/main/AndroidManifest.xml');
 assert.match(manifest,/FOREGROUND_SERVICE_MEDIA_PLAYBACK/);
 assert.match(manifest,/foregroundServiceType="mediaPlayback"/);
 assert.match(manifest,/RingService/);
});
