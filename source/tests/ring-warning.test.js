import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {ringBlockReason} from '../web/core.js';

const root=fileURLToPath(new URL('..',import.meta.url));
const read=rel=>fs.readFileSync(path.join(root,rel),'utf8');

test('ringBlockReason names the first blocking permission, or null when ringing can sound',()=>{
 assert.equal(ringBlockReason(null),null);
 assert.equal(ringBlockReason({permission:'granted',exact:'allowed',fullscreen:'allowed'}),null);
 assert.equal(ringBlockReason({permission:'denied',exact:'allowed',fullscreen:'allowed'}),'notifications');
 assert.equal(ringBlockReason({permission:'default',exact:'allowed',fullscreen:'allowed'}),'notifications');
 assert.equal(ringBlockReason({permission:'granted',exact:'blocked',fullscreen:'allowed'}),'exact');
 assert.equal(ringBlockReason({permission:'granted',exact:'allowed',fullscreen:'blocked'}),'fullscreen');
 assert.equal(ringBlockReason({permission:'denied',exact:'blocked',fullscreen:'blocked'}),'notifications');
 assert.equal(ringBlockReason({permission:'granted',exact:'blocked',fullscreen:'blocked'}),'exact');
});

test('sound card renders the ring warning with a button to the right settings screen',()=>{
 const appJs=read('web/app.js');
 assert.match(appJs,/soundCard\(st\)/);
 assert.match(appJs,/ringBlockReason\(st\)/);
 assert.match(appJs,/Ringing needs a phone permission/);
 assert.match(appJs,/data-phone="\$\{blockReason\}"/);
 assert.match(appJs,/Open phone settings/);
});

test('notification tap key survives until the card is focused',()=>{
 const appJs=read('web/app.js');
 assert.match(appJs,/openingNotification/);
 assert.match(appJs,/native\(\)\.keyHandled/);
 assert.match(appJs,/window\.careOpenNotification/);
 assert.match(appJs,/careOpenNotification\(\)/);
});

test('donate and reminder-launch diagnostics are wired end to end',()=>{
 const java=read('android/app/src/main/java/app/dailycare/MainActivity.java');
 const rem=read('android/app/src/main/java/app/dailycare/Reminders.java');
 const flow=read('android/app/src/main/java/app/dailycare/DonateFlow.java');
 const key=read('android/app/src/main/java/app/dailycare/LaunchKey.java');
 assert.match(java,/queryIntentActivities/);
 assert.match(java,/DonateFlow\.record\(/);
 assert.match(java,/donateReport/);
 assert.match(java,/launchReport/);
 assert.match(java,/markLaunchDelivered/);
 assert.match(rem,/recordShown\(/);
 assert.match(rem,/recordNotificationLaunch\(/);
 assert.match(rem,/tapRequestCode\(key\)/);
 assert.match(flow,/summarizeAttempt/);
 assert.match(flow,/getSharedPreferences\(PREF/);
 assert.match(key,/isNewLaunch/);
 assert.match(key,/matchesLaunch/);
 assert.match(read('web/app.js'),/donate-view/);
 assert.match(read('web/app.js'),/launch-view/);
 assert.match(read('web/app.js'),/Last donate check/);
 assert.match(read('web/app.js'),/Last reminder launch/);
});
