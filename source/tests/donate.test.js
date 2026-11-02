import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {DONATE_UPI_ID} from '../web/core.js';

const root=fileURLToPath(new URL('..',import.meta.url));
const read=rel=>fs.readFileSync(path.join(root,rel),'utf8');
const PAYLOAD='upi://pay?pa=jaynishad2010@okhdfcbank&pn=Vijay%20Kumar&aid=uGICAgIDQqveaLw';

test('donate UPI ID is the one the user supplied',()=>{
 assert.equal(DONATE_UPI_ID,'jaynishad2010@okhdfcbank');
});

test('native donate payload is exact, with no amount parameter',()=>{
 const java=read('android/app/src/main/java/app/dailycare/MainActivity.java');
 const flow=read('android/app/src/main/java/app/dailycare/DonateFlow.java');
 assert.ok(flow.includes(`"${PAYLOAD}"`),'DonateFlow must carry the exact verified UPI payload');
 assert.ok(java.includes('DONATE_URI=DonateFlow.PAYLOAD'),'MainActivity must use the single DonateFlow payload');
 assert.ok(!PAYLOAD.includes('am='),'donors pay any amount, so the payload must not fix one');
 assert.ok(!PAYLOAD.includes('am%3D'),'no encoded amount either');
 assert.ok(java.includes('Intent.createChooser(intent,DonateFlow.CHOOSER_TITLE)'),'donate opens the installed-apps chooser');
 assert.ok(flow.includes('CHOOSER_TITLE = "Choose an app to pay with"'),'chooser title names the choice');
 assert.ok(java.includes('return "no-app"'),'donate reports when no payment app resolves');
 assert.ok(!java.includes('setPackage'),'donate must never pin a payment app package');
});

test('manifest declares package-visibility queries so every UPI app resolves',()=>{
 const manifest=read('android/app/src/main/AndroidManifest.xml');
 assert.match(manifest,/<queries>/);
 assert.match(manifest,/android\.intent\.action\.VIEW/);
 assert.match(manifest,/android:scheme="upi"/);
 assert.match(manifest,/android:host="instagram\.com"/);
});

test('donate UI, QR asset and footer credit are wired in the web app',()=>{
 const appJs=read('web/app.js');
 assert.match(appJs,/donate-qr\.png/);
 assert.match(appJs,/Click to pay/);
 assert.match(appJs,/Donate to the developer/);
 assert.match(appJs,/helps keep Remibuds running and ad-free/);
 assert.match(appJs,/Made with love/);
 assert.match(appJs,/@momentswithvms/);
 assert.match(appJs,/instagram\.com\/momentswithvms/);
 assert.match(appJs,/DONATE_UPI_ID/);
 const qr=path.join(root,'web/donate-qr.png');
 assert.ok(fs.existsSync(qr),'donate QR image missing');
 const buf=fs.readFileSync(qr);
 assert.ok(buf.length>1000,'donate QR image suspiciously small');
 assert.equal(buf.subarray(0,8).toString('hex'),'89504e470d0a1a0a','donate QR is not a PNG');
 const art=path.join(root,'web/art/art-donate.svg');
 assert.ok(fs.existsSync(art),'donate illustration missing');
 assert.ok(fs.statSync(art).size>500,'donate illustration suspiciously small');
 assert.match(read('web/art/art-donate.svg'),/<svg/);
 const css=read('web/style.css');
 assert.match(css,/\.shell>header\.brand-header\{padding-top:10px\}/);
 assert.match(css,/\.donate-row/);
 assert.match(css,/\.donate-qr/);
 assert.match(css,/\.footer-credit/);
});
