import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
// 2.0.4 early-alert spec, corrected from the user's own words (3 Nov 2026):
// NOT one fixed lead from {30,60,120,1440}. Every reminder ALWAYS gets
// 1 day + 1 hour + 30 minutes before, PLUS one random extra up to a day
// before (5, 45, 65 minutes, ...). Maximum lead is 1 day.
const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'android', 'app', 'src', 'main', 'java', 'app', 'dailycare');
const ea = fs.readFileSync(path.join(dir, 'EarlyAlerts.java'), 'utf8');
const er = fs.readFileSync(path.join(dir, 'EarlyAlertReceiver.java'), 'utf8');
const ma = fs.readFileSync(path.join(dir, 'MainActivity.java'), 'utf8');
const appJs = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'web', 'app.js'), 'utf8');
test('2.0.4: mandatory leads are 1 day, 1 hour, 30 minutes',()=>{
  assert.ok(ea.includes('MANDATORY_LEADS = {1440, 60, 30}'), 'mandatory leads must be 1440, 60, 30');
  assert.ok(!ea.includes('LEADS = {30, 60, 120, 1440}'), 'old single-random-lead set must be gone');
});
test('2.0.4: one stable random extra lead, 5..1440, never a mandatory lead',()=>{
  assert.ok(ea.includes('randomExtraLead'), 'random extra lead helper must exist');
  assert.ok(ea.includes('5 + (h % 1436)'), 'random lead must span 5..1440 minutes');
  assert.ok(ea.includes('lead == 30 || lead == 60 || lead == 1440'), 'random lead must avoid the mandatory leads');
});
test('2.0.4: entries are per slot AND lead, key carries the lead',()=>{
  assert.ok(ea.includes('d + "|" + time + "|" + title + "|" + lead'), 'bySlot must be keyed by lead too, or mandatory alerts collapse into one');
  assert.ok(ea.includes('"early|" + key + "|" + lead'), 'armed key must carry the lead');
  assert.ok(er.includes('parts.length >= 4'), 'receiver must accept the 5-part key (early|id|date|time|lead)');
});
test('2.0.4: lead text handles arbitrary minutes, e.g. 1 hour 5 minutes',()=>{
  assert.ok(ea.includes('lead % 60'), 'leadText must render the minutes remainder, not just whole hours');
});
test('2.0.4: exactly one Reminder sound block and a test button in the early card',()=>{
  assert.strictEqual((appJs.match(/\$\{soundCard\(st\)\}/g) || []).length, 1, 'settings() must call soundCard exactly once');
  assert.ok(appJs.includes('data-early-test'), 'early card must have a Test your notification button');
  assert.ok(appJs.includes('testEarlyAlert'), 'test button must call the native test bridge');
  assert.ok(ma.includes('testEarlyAlert'), 'native testEarlyAlert bridge must exist');
});
