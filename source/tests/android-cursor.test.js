import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
// Regression test for the 2026-11-03T01:02:11 crash:
// StaleDataException in Activity.performRestart, re-querying a closed
// RingtoneManager cursor. RingtoneManager with an Activity context registers
// its cursor as managed, and our finally-close() left a closed managed cursor
// behind; returning from the audio/restore picker restarts the activity and
// the system re-queries it, killing the app outside any try/catch.
const java = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'android', 'app', 'src', 'main', 'java', 'app', 'dailycare', 'MainActivity.java'), 'utf8');
test('2.0.3: RingtoneManager must use application context, never the Activity',()=>{
  assert.ok(java.includes('new RingtoneManager(getApplicationContext())'), 'RingtoneManager must be created with getApplicationContext()');
  assert.ok(!java.includes('new RingtoneManager(MainActivity.this)'), 'RingtoneManager must never be created with the Activity (managed cursor + close = StaleDataException on restart)');
  assert.ok(!java.includes('startManagingCursor'), 'no managed cursors anywhere');
});
