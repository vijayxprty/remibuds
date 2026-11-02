/** Private local model. Internal times are always HH:mm; display preference is separate. */
export const DONATE_UPI_ID='jaynishad2010@okhdfcbank';
export const TYPE_LABELS={tablet:'Tablet',capsule:'Capsule',liquid:'Liquid',drops:'Drops',cream:'Cream',gel:'Gel',spray:'Spray',inhaler:'Inhaler',injection:'Injection',patch:'Patch',other:'Other',water:'Water',meal:'Meal',walk:'Walk',outing:'Going somewhere',custom:'Custom activity',todo:'To-do'};
export const dayKey=(d=new Date())=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
export const MEDICINE_TYPES=['tablet','capsule','liquid','drops','cream','gel','spray','inhaler','injection','patch','other'];
export const ACTIVITY_TYPES=['water','meal','walk','outing','custom','todo'];
const TYPES=[...MEDICINE_TYPES,...ACTIVITY_TYPES];
export const ART_CATEGORIES=[
 {key:'medicine-morning',label:'Morning medicine',file:'art-medicine-morning.svg'},
 {key:'medicine-afternoon',label:'Afternoon medicine',file:'art-medicine-afternoon.svg'},
 {key:'medicine-night',label:'Night medicine',file:'art-medicine-night.svg'},
 {key:'meal',label:'Meal',file:'art-meal.svg'},
 {key:'water',label:'Water',file:'art-water.svg'},
 {key:'walk',label:'Walk',file:'art-walk.svg'},
 {key:'cycling',label:'Cycling',file:'art-cycling.svg'},
 {key:'gym',label:'Gym & exercise',file:'art-gym.svg'},
 {key:'rest',label:'Rest',file:'art-rest.svg'},
 {key:'sleep',label:'Sleep',file:'art-sleep.svg'},
 {key:'generic',label:'To-do & custom',file:'art-generic.svg'}
];
export const ART_KEYS=new Set(ART_CATEGORIES.map(c=>c.key));
export const ART_FILES=Object.fromEntries(ART_CATEGORIES.map(c=>[c.key,c.file]));
/** Illustration groups shown by the create/edit picker, keyed by the Kind (family) select. */
export const ART_GROUPS={medicine:['medicine-morning','medicine-afternoon','medicine-night'],activity:['meal','water','walk','cycling','gym','rest','sleep'],todo:['generic']};
export const familyForType=type=>type==='todo'?'todo':(MEDICINE_TYPES.includes(type)||type==='medicine')?'medicine':'activity';
export const medicineBand=firstTime=>!firstTime||firstTime<'12:00'?'medicine-morning':firstTime<'18:00'?'medicine-afternoon':'medicine-night';
export function artSrc(key){return 'art/'+(ART_FILES[key]||ART_FILES.generic);}
const wordHit=(text,words)=>words.some(w=>/[a-z]/.test(w[0])?new RegExp('\\b'+w.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'\\b').test(text):text.includes(w));
export function guessArt(name='',type='',firstTime=''){
 const text=String(name||'').toLowerCase();
 if(wordHit(text,['cycling','cycle','bike','biking','bicycle','साइकिल']))return 'cycling';
 if(wordHit(text,['walking','walk','stroll','saunter','सैर','टहल','घूमना']))return 'walk';
 if(wordHit(text,['gym','workout','exercise','yoga','running','run','jog','weights','lift','strength','जिम','कसरत','व्यायाम']))return 'gym';
 if(wordHit(text,['sleep','sleeping','nap','bedtime','bed','सोना','नींद','सोने']))return 'sleep';
 if(wordHit(text,['rest','resting','relax','relaxing','unwind','आराम']))return 'rest';
 if(wordHit(text,['breakfast','lunch','dinner','brunch','meal','food','eat','eating','snack','खाना','भोजन','नाश्ता','लंच','डिनर']))return 'meal';
 if(wordHit(text,['water','drink','drinking','hydration','पानी','पीना']))return 'water';
 const medicineKey=medicineBand(firstTime);
 if(wordHit(text,['medicine','meds','med','dawai','दवा','दवाई','tablet','capsule','pill','dose','injection','syrup','drops','cream','ointment']))return medicineKey;
 if(type==='water')return 'water';if(type==='meal')return 'meal';if(type==='walk')return 'walk';
 if(MEDICINE_TYPES.includes(type)||type==='medicine')return medicineKey;
 return 'generic';
}
/** The guess constrained to one Kind group; null when nothing in the group matches. */
export function guessArtForFamily(name='',type='',firstTime='',family){
 const g=guessArt(name,type,firstTime);
 if(!family||(ART_GROUPS[family]||[]).includes(g))return g;
 return null;
}
export function artFor(r){
 if(!r)return 'generic';
 if(ART_KEYS.has(r.art)){const fam=familyForType(r.type);if(ART_GROUPS[fam].includes(r.art)||(r.art==='generic'&&r.type==='custom'))return r.art;}
 const fam=familyForType(r.type);
 const g=guessArtForFamily(r.name,r.type,r.times?.[0],fam);
 if(g)return g;
 if(fam==='medicine')return medicineBand(r.times?.[0]);
 return 'generic';
}
const TIME=/^([01]\d|2[0-3]):[0-5]\d$/;
const DATE=/^\d{4}-\d{2}-\d{2}$/;
const safeId=v=>typeof v==='string'&&/^[a-zA-Z0-9_-]{1,100}$/.test(v);
const dateValid=v=>typeof v==='string'&&DATE.test(v)&&dayKey(new Date(v+'T12:00'))===v;
const instantValid=v=>typeof v==='string'&&Number.isFinite(Date.parse(v));
const clone=v=>JSON.parse(JSON.stringify(v));
export const NUDGE_DEFAULTS=[
 {id:'water',emoji:'\u{1f4a7}',text:'Drink some water \u2014 stay hydrated',on:true,weight:1},
 {id:'shower',emoji:'\u{1f6bf}',text:'Did you take a shower today?',on:true,weight:1},
 {id:'walk',emoji:'\u{1f6b6}',text:'Did you go for a walk today?',on:true,weight:1},
 {id:'sunset',emoji:'\u{1f307}',text:'Did you enjoy the sunset today?',on:true,weight:1},
 {id:'remind',emoji:'\u{1f4dd}',text:'Make a reminder about something you do not want to forget',on:true,weight:1},
 {id:'eatwell',emoji:'\u{1f37d}\ufe0f',text:'Did you eat well today?',on:true,weight:1},
 {id:'noovereat',emoji:'\u2696\ufe0f',text:'Do not overeat, okay?',on:true,weight:1},
 {id:'healthy',emoji:'\u{1f957}',text:'Eat healthy',on:true,weight:1},
 {id:'phone',emoji:'\u{1f4f1}',text:'Do not spend too much time on your phone',on:true,weight:3},
 {id:'productive',emoji:'\u2705',text:'Do something productive',on:true,weight:1},
 {id:'office',emoji:'\u{1f4bc}',text:'Any office task pending?',on:true,weight:3},
 {id:'jobs',emoji:'\u{1f50d}',text:'Find new jobs \u2014 check one listing today',on:true,weight:3},
 {id:'shop',emoji:'\u{1f3ea}',text:'Is everything fine at the shop today?',on:false,weight:1},
 {id:'family',emoji:'\u2764\ufe0f',text:'Say hi to someone in the family',on:false,weight:1},
 {id:'story',emoji:'\u{1f3ac}',text:'Got an idea for your next video story? Note it down',on:false,weight:1},
];
export const id=()=>globalThis.crypto.randomUUID();
export const fresh=()=>({version:2,routines:[],groups:[],logs:[],snoozes:{},settings:{enabled:false,onboarded:false,snooze:15,timeFormat:'12',zone:Intl.DateTimeFormat().resolvedOptions().timeZone,soundMode:'notification',toneUri:'',toneName:'',customTone:'',customToneName:'',earlyAlerts:true,nudges:{enabled:false,window:'all',texts:NUDGE_DEFAULTS.map(t=>({...t}))}}});

/** Pure migration: IDs, occurrence keys and recorded timestamps never change. */
export function migrate(input){
 const s=clone(input||fresh());
 s.version=2;s.routines=Array.isArray(s.routines)?s.routines:[];s.groups=Array.isArray(s.groups)?s.groups:[];s.logs=Array.isArray(s.logs)?s.logs:[];s.snoozes=s.snoozes&&typeof s.snoozes==='object'?s.snoozes:{};
 s.settings={...fresh().settings,...s.settings};s.settings.timeFormat=s.settings.timeFormat==='24'?'24':'12';s.settings.snooze=[5,10,15,30,60].includes(s.settings.snooze)?s.settings.snooze:15;s.settings.soundMode=['notification','ring'].includes(s.settings.soundMode)?s.settings.soundMode:'notification';if(typeof s.settings.customTone!=='string'||(s.settings.customTone!==''&&!/^[A-Za-z0-9._-]{1,80}$/.test(s.settings.customTone))||s.settings.customTone.includes('..')){s.settings.customTone='';s.settings.customToneName='';}for(const k of ['toneUri','toneName','customToneName'])if(typeof s.settings[k]!=='string')s.settings[k]='';if(s.settings.zone){try{new Intl.DateTimeFormat('en',{timeZone:s.settings.zone});}catch{s.settings.zone=Intl.DateTimeFormat().resolvedOptions().timeZone;}}s.settings.earlyAlerts=s.settings.earlyAlerts!==false;s.groups=s.groups.filter(g=>{g.members=(g.members||[]).filter(m=>m&&s.routines.some(r=>r.id===m.routineId));return g.members.length>0;});
 {const n=s.settings.nudges;if(!n||typeof n!=='object'||!Array.isArray(n.texts))s.settings.nudges={enabled:false,window:'all',texts:NUDGE_DEFAULTS.map(t=>({...t}))};else{s.settings.nudges.enabled=!!n.enabled;s.settings.nudges.window=['all','6-23','7-23','8-23','9-23','10-23','11-23','6-22','7-22','8-22','9-21','7-21','8-21'].includes(n.window)?n.window:'all';
    // 2.0 one-time: the old 1.9.2 default was 9-21, and 2.0's default is All day.
    // Reset that old default once, then remember, so a later explicit 9-21 pick is kept.
    if(!n.windowMigrated20){n.windowMigrated20=true;if(n.window==='9-21')n.window='all';}s.settings.nudges.texts=n.texts.filter(t=>t&&typeof t.text==='string'&&t.text.trim()).map(t=>({id:typeof t.id==='string'&&t.id?t.id:id(),emoji:typeof t.emoji==='string'?t.emoji:'',text:t.text.trim().slice(0,120),on:t.on!==false,weight:t.weight>1?3:1,custom:!!t.custom}));if(!s.settings.nudges.texts.length)s.settings.nudges.texts=NUDGE_DEFAULTS.map(t=>({...t}));}}
 for(const r of s.routines){if(r.type==='medicine')r.type='tablet';if(r.art!==undefined&&!ART_KEYS.has(r.art))delete r.art;r.times=Array.isArray(r.times)?r.times:[];r.days=Array.isArray(r.days)?r.days:[0,1,2,3,4,5,6];r.start=r.start||'';r.notes=r.notes||'';r.amount=r.amount||'';r.remind=r.remind!==false;r.scheduleVersions=Array.isArray(r.scheduleVersions)?r.scheduleVersions:[];for(const v of r.scheduleVersions)if(v.snapshot?.type==='medicine')v.snapshot.type='tablet';}
 // History is a permanent log: records carry their own name/type snapshot so they
 // stay readable after the routine is gone. Backfill display fields for old records
 // that lack them, from the routine when it still exists — without rewriting history.
 for(const l of s.logs){if(!l||typeof l!=='object')continue;const r=s.routines.find(x=>x.id===l.routineId);if(!l.name||typeof l.name!=='string')l.name=r&&r.name?r.name:'Archived item';if(!l.type||typeof l.type!=='string')l.type=r&&r.type?r.type:'other';if(l.amount===undefined)l.amount='';}
 return s;
}

export function formatTime(value,preference='12'){
 if(value==='untimed'||value==null||value==='')return 'Any time';
 let h,m;if(typeof value==='string'&&TIME.test(value)){[h,m]=value.split(':').map(Number);}else{const d=value instanceof Date?value:new Date(value);if(!Number.isFinite(d.getTime()))return '—';h=d.getHours();m=d.getMinutes();}
 const minutes=String(m).padStart(2,'0');return preference==='24'?`${String(h).padStart(2,'0')}:${minutes}`:`${h%12||12}:${minutes} ${h>=12?'PM':'AM'}`;
}
export function parseTime(hour,minute,period='AM'){
 if(!/^\d{1,2}$/.test(String(hour))||!/^\d{1,2}$/.test(String(minute)))throw new Error('Choose an hour and minute.');
 let h=Number(hour),m=Number(minute);if(m<0||m>59)throw new Error('Choose a minute from 00 to 59.');
 if(period==='24'){if(h<0||h>23)throw new Error('Choose an hour from 00 to 23.');}else{if(!['AM','PM'].includes(period)||h<1||h>12)throw new Error('Choose an hour from 1 to 12 and AM or PM.');h=h%12+(period==='PM'?12:0);}
 return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}`;
}
export const isMedicine=type=>MEDICINE_TYPES.includes(type)||type==='medicine';
export function actionFor(type){return ['cream','gel','patch'].includes(type)?'Mark applied':type==='injection'?'Record injection':['drops','spray','inhaler'].includes(type)?'Record use':isMedicine(type)?'Mark taken':type==='water'?'Record drink':type==='meal'?'Record meal':'Mark done';}

function versionFor(r,date){
 if(!r.scheduleFrom||date>=r.scheduleFrom)return r;
 const versions=(r.scheduleVersions||[]).filter(v=>v.from<=date).sort((a,b)=>a.from.localeCompare(b.from));
 return versions.length?{...r,...versions[versions.length-1].snapshot}:r;
}
function occurrence(r,date,time,s){
 return {key:`${r.id}|${date}|${time}`,routine:r,date,time,group:s.groups.find(g=>g.members.some(m=>m.routineId===r.id&&m.time===time))};
}
export function occurrences(s,date=dayKey()){
 const dow=new Date(date+'T12:00').getDay(),today=dayKey();let out=[];
 for(const current of s.routines){
  if(current.archived&&date>=today)continue;
  const r=versionFor(current,date);
  if(r.type==='todo'&&r.dueDate){
   if(current.archived&&date>=today)continue;
   const due=r.dueDate;const times=r.times.length?r.times:['untimed'];
   for(const time of times){const o=occurrence(r,due,time,s);const entry=active(s,o.key);const completedToday=entry&&dayKey(new Date(entry.reported))===today;if(due===date||(date===today&&due<today&&(!entry||completedToday)))out.push(o);}
   continue;
  }
  if(r.archived||!r.days.includes(dow)||(r.start&&r.start>date))continue;
  for(const time of r.times)out.push(occurrence(r,date,time,s));
 }
 return out.sort((a,b)=>{const av=a.time==='untimed'?'99:99':a.time,bv=b.time==='untimed'?'99:99':b.time;return a.date.localeCompare(b.date)||av.localeCompare(bv)||a.routine.name.localeCompare(b.routine.name);});
}
export const active=(s,key)=>s.logs.find(l=>l.key===key&&!l.undone);
export function record(s,o,status='recorded',reported=new Date().toISOString(),batch=null){
 if(!o||active(s,o.key))return null;if(!['recorded','skipped'].includes(status))throw new Error('Unknown record state.');
 const prior=s.logs.slice().reverse().find(l=>l.key===o.key);
 const l={id:id(),key:o.key,routineId:o.routine.id,name:o.routine.name,type:o.routine.type,amount:o.routine.amount??prior?.amount??'',date:o.date,scheduled:o.time,status,reported,created:new Date().toISOString(),batch,targetCount:prior?.targetCount??occurrences(s,o.date).filter(x=>x.routine.id===o.routine.id).length};
 s.logs.push(l);delete s.snoozes[o.key];return l;
}
export function undo(s,ids){const selected=new Set(ids);s.logs.forEach(l=>{if(selected.has(l.id)&&!l.undone)l.undone=new Date().toISOString()});}
export const count=(s,rid,date)=>s.logs.filter(l=>l.routineId===rid&&l.date===date&&l.status==='recorded'&&!l.undone).length;
export function scheduledCount(s,rid,date){return occurrences(s,date).filter(o=>o.routine.id===rid&&o.date===date).length;}
/** Streak for one routine: consecutive recorded days. A streak stays alive through
 *  today when yesterday was recorded but today is not yet. One-time to-dos never
 *  get streaks (callers exclude them). Skipped and undone records break the run. */
export function streakInfo(s,routineId){const dates=new Set();for(const l of s.logs||[]){if(l.routineId===routineId&&l.status==='recorded'&&!l.undone&&l.date)dates.add(l.date)}if(!dates.size)return {current:0,best:0};const step=d=>{const x=new Date(d+'T12:00');x.setDate(x.getDate()+1);return dayKey(x)};let best=0,run=0,prev=null;for(const d of [...dates].sort()){run=prev&&step(prev)===d?run+1:1;if(run>best)best=run;prev=d}let current=0;let d=dayKey();if(!dates.has(d)){const y=new Date(d+'T12:00');y.setDate(y.getDate()-1);d=dayKey(y)}while(dates.has(d)){current++;const y=new Date(d+'T12:00');y.setDate(y.getDate()-1);d=dayKey(y)}return {current,best}}

/** Edits preserve past schedule versions. A medicine schedule change after any report
 * for that date starts the next day, so an edit cannot turn one reported dose into a
 * fresh actionable occurrence. Archive/reminder-off still cancels immediately. */
export function updateRoutine(s,next,effectiveDate=dayKey()){
 if(!dateValid(effectiveDate))throw new Error('Invalid effective date.');
 const index=s.routines.findIndex(r=>r.id===next.id);const old=index>=0?s.routines[index]:null;
 if(old&&s.groups.some(g=>g.members.some(m=>m.routineId===old.id&&!next.times.includes(m.time))))throw new Error('Remove the changed time from its reminder group first.');
 const scheduleChanged=old&&['type','times','days','start'].some(k=>JSON.stringify(old[k]??'')!==JSON.stringify(next[k]??''));
 const defer=old&&(isMedicine(old.type)||isMedicine(next.type))&&scheduleChanged&&s.logs.some(l=>l.routineId===old.id&&l.date===effectiveDate);
 const tomorrow=new Date(effectiveDate+'T12:00');tomorrow.setDate(tomorrow.getDate()+1);
 const starts=defer?dayKey(tomorrow):!scheduleChanged&&old?.scheduleFrom>effectiveDate?old.scheduleFrom:effectiveDate;
 const updated=clone(next);updated.type=updated.type==='medicine'?'tablet':updated.type;updated.added=old?.added||updated.added||new Date().toISOString();
 if(old){const fields=['name','type','times','days','start','archived','remind','dueDate','dueMode','beforeMinutes','repeatMinutes','amount','notes','art'];const snapshot={start:'',archived:false,remind:true,dueDate:'',dueMode:'at',beforeMinutes:0,repeatMinutes:0,amount:'',notes:''};for(const k of fields)if(old[k]!==undefined)snapshot[k]=clone(old[k]);const previousFrom=old.scheduleFrom||'0000-01-01';const history=clone(old.scheduleVersions||[]).filter(v=>v.from!==previousFrom);history.push({from:previousFrom,snapshot});updated.scheduleVersions=history;updated.scheduleFrom=starts;s.routines[index]=updated;}
 else{updated.scheduleFrom=effectiveDate;updated.scheduleVersions=[];s.routines.push(updated);}
 return updated;
}
/** Delete an archived routine definition. History (s.logs) is never touched: records
 *  carry their own snapshot and stay as a permanent log. Group memberships pointing
 *  at the routine are removed, and groups left empty are removed too. Future
 *  occurrences vanish because the planner only reads live routines. Returns the
 *  removed routine, or null when the id is unknown. */
export function deleteRoutine(s,rid){
 const index=s.routines.findIndex(r=>r.id===rid);
 if(index<0)return null;
 const [removed]=s.routines.splice(index,1);
 for(const k of Object.keys(s.snoozes||{}))if(k.startsWith(rid+'|'))delete s.snoozes[k];
 s.groups=s.groups.filter(g=>{g.members=(g.members||[]).filter(m=>m.routineId!==rid);return g.members.length>0;});
 return removed;
}

/** Ringtone list presentation: a small list first, the full list on "Show more". */
export const TONE_COLLAPSED_COUNT=5;
export function visibleTones(tones,expanded){const list=Array.isArray(tones)?tones:[];return expanded?list.slice():list.slice(0,TONE_COLLAPSED_COUNT);}
/** Preview state machine: tapping the playing tone stops it; tapping another switches. */
export function previewNext(current,clicked){return current===clicked?null:clicked;}
/** First phone permission blocking alarm-style ringing, in the order the user should fix
 *  them, or null when ringing can sound. Pure so it is unit-tested. */
export function ringBlockReason(st){if(!st)return null;if(st.permission!=='granted')return 'notifications';if(st.exact!=='allowed')return 'exact';if(st.fullscreen!=='allowed')return 'fullscreen';return null;}
/** Resolve a notification-tap key for a notification action ("Mark taken"/"Snooze").
 *  Pure so it is unit-tested: a key matching one occurrence applies to that
 *  occurrence; a key matching a shared reminder's card applies to no single
 *  occurrence (members are marked explicitly on the card); anything else
 *  matches nothing and nothing may be applied. */
export function resolveCareAuto(os,key){
 if(!key)return null;
 const o=(os||[]).find(x=>x.key===key);
 if(o)return {occurrence:o};
 const group=(os||[]).some(x=>x.group&&`${x.group.id}|${x.date}|${x.time}`===key);
 return group?{group:true}:null;
}

const csvEscape=v=>{const s=String(v??'');return /[",\n\r]/.test(s)?'"'+s.replace(/"/g,'""')+'"':s;};
const enteredAfterwards=l=>{try{return l.retrospective||Math.abs(new Date(l.reported)-new Date(l.created))>60000;}catch{return !!l.retrospective;}};
export function earliestLogDate(s){let m='';for(const l of s.logs||[])if(l.date&&(!m||l.date<m))m=l.date;return m;}
export function csvFilename(from,to,all){return all?'remibuds-history-all.csv':`remibuds-history-${from}-to-${to}.csv`;}
/** History as CSV. Rows are filtered by the record's stored date (l.date), inclusive
 *  on both ends; undone rows are included with a status that says they do not count. */
export function historyCsv(s,from='',to=''){
 const header=['Date','Scheduled time','Name','Kind/Type','Status','Recorded at','Entered afterwards','Undone'];
 const rows=(s.logs||[]).filter(l=>(!from||l.date>=from)&&(!to||l.date<=to)).slice()
  .sort((a,b)=>(a.date+(a.reported||'')).localeCompare(b.date+(b.reported||''))||(a.created||'').localeCompare(b.created||''));
 const lines=[header.map(csvEscape).join(',')];
 for(const l of rows){
  const status=l.undone?'Undone (does not count)':l.status==='skipped'?'Skipped':'Recorded';
  lines.push([l.date,l.scheduled,l.name,TYPE_LABELS[l.type]||l.type,status,l.reported,enteredAfterwards(l)?'Yes':'No',l.undone||''].map(csvEscape).join(','));
 }
 return lines.join('\n')+'\n';
}

export function dueAt(o){if(o.time==='untimed')return null;let at=new Date(o.date+'T'+o.time).getTime();if(o.routine.type==='todo'&&o.routine.dueMode==='before')at-=Math.max(0,Number(o.routine.beforeMinutes)||0)*60000;return at;}
export function reminderPlan(s,now=new Date()){
 if(!s.settings.enabled)return [];const nowMs=now.getTime(),out=[],seen=new Set();
 const candidates=[];for(let n=-1;n<35;n++){let d=new Date(now);d.setDate(d.getDate()+n);for(const o of occurrences(s,dayKey(d))){if(seen.has(o.key))continue;seen.add(o.key);candidates.push(o);}}
 // One-off pending to-dos may be overdue by more than a day. Their original identity remains unchanged.
 for(const r of s.routines.filter(r=>r.type==='todo'&&r.dueDate&&!r.archived)){for(const time of r.times){const o=occurrence(r,r.dueDate,time,s);if(!seen.has(o.key)){seen.add(o.key);candidates.push(o);}}}
 for(const o of candidates){
  const current=s.routines.find(r=>r.id===o.routine.id);
  if(o.routine.type==='todo'&&(current?.type!=='todo'||current.dueDate!==o.date||!current.times.includes(o.time)))continue;
  if(current?.archived||current?.remind===false||active(s,o.key)||o.routine.remind===false||o.time==='untimed')continue;
  const scheduled=dueAt(o),snoozed=Number(s.snoozes[o.key]);let at=Number.isFinite(snoozed)&&snoozed>0?snoozed:scheduled;
  const repeat=o.routine.type==='todo'?Math.max(0,Number(o.routine.repeatMinutes)||0):0;
  if(at<=nowMs){if(!repeat)continue;const interval=repeat*60000;at=scheduled+(Math.floor((nowMs-scheduled)/interval)+1)*interval;if(!o.routine.dueDate&&dayKey(new Date(at))!==o.date)continue;}
  const key=o.group?`${o.group.id}|${o.date}|${o.time}`:o.key;const existing=out.find(x=>x.key===key&&x.at===at);
  if(existing){existing.keys.push(o.key);continue;}
  const title=o.group?o.group.name:o.routine.name;const shown=formatTime(o.time,s.settings.timeFormat);
  out.push({key,keys:[o.key],at,title,body:o.routine.type==='todo'?`${o.routine.dueMode==='before'?'Due by':'Due at'} ${shown}. Open to review or mark done.`:`Scheduled ${shown}. Open to record what you did.`});
 }
 return out.sort((a,b)=>a.at-b.at);
}

/** Validate imported data before replacing any personal records. Returns a migrated copy. */
export function validateBackup(input){
 const fail=(why='This is not a valid Remibuds backup.')=>{throw new Error(why);};
 if(!input||typeof input!=='object'||(input.version!==undefined&&![1,2].includes(input.version))||!Array.isArray(input.routines)||!Array.isArray(input.logs)||!Array.isArray(input.groups)||!input.settings||typeof input.settings!=='object')fail('This file does not look like a Remibuds backup: it is missing routines, history, groups or settings.');
 const ids=new Set();
 const routineCheck=(r,isSnapshot=false)=>{
  if(!r||typeof r!=='object'||(!isSnapshot&&(!safeId(r.id)||ids.has(r.id))))fail();
  if(!isSnapshot)ids.add(r.id);
  if(typeof r.name!=='string'||!r.name.trim()||r.name.length>200||![...TYPES,'medicine'].includes(r.type)||!Array.isArray(r.times)||!r.times.every(t=>TIME.test(t))||new Set(r.times).size!==r.times.length||!Array.isArray(r.days)||!r.days.every(d=>Number.isInteger(d)&&d>=0&&d<=6))fail();
  if(r.type!=='todo'&&(!r.times.length||!r.days.length))fail();
  if(r.start&&!dateValid(r.start)||r.dueDate&&!dateValid(r.dueDate))fail();
  if(r.notes!==undefined&&typeof r.notes!=='string'||r.amount!==undefined&&!['string','number'].includes(typeof r.amount))fail();
  if(r.beforeMinutes!==undefined&&(!Number.isFinite(r.beforeMinutes)||r.beforeMinutes<0||r.beforeMinutes>10080)||r.repeatMinutes!==undefined&&(!Number.isFinite(r.repeatMinutes)||r.repeatMinutes<0||r.repeatMinutes>10080))fail();
 };
 for(const r of input.routines){routineCheck(r);if(r.scheduleFrom&&!dateValid(r.scheduleFrom))fail();if(r.scheduleVersions!==undefined){if(!Array.isArray(r.scheduleVersions))fail();for(const v of r.scheduleVersions){if(!v||!(v.from==='0000-01-01'||dateValid(v.from)))fail();routineCheck(v.snapshot,true);}}}
 const groupIds=new Set(),assigned=new Set();for(const g of input.groups){if(!g||!safeId(g.id)||groupIds.has(g.id)||typeof g.name!=='string'||!g.name.trim()||g.name.length>200||!Array.isArray(g.members)||!g.members.length)fail();groupIds.add(g.id);for(const m of g.members){if(!m||!TIME.test(m.time))fail('A reminder group has a member without a valid time.');const k=m.routineId+'|'+m.time;if(assigned.has(k))fail();assigned.add(k);}}
 const logIds=new Set(),activeKeys=new Set();for(const l of input.logs){if(!l||!safeId(l.id)||logIds.has(l.id)||typeof l.key!=='string'||!/^[A-Za-z0-9_|: .-]{1,250}$/.test(l.key)||typeof l.routineId!=='string'||typeof l.name!=='string'||![...TYPES,'medicine'].includes(l.type)||!dateValid(l.date)||typeof l.scheduled!=='string'||!['recorded','skipped'].includes(l.status)||!instantValid(l.reported)||!instantValid(l.created)||l.undone&&!instantValid(l.undone))fail('A history entry in this backup is malformed (bad id, date or status).');logIds.add(l.id);if(!l.undone){if(activeKeys.has(l.key))fail();activeKeys.add(l.key);}}
 if(input.snoozes!==undefined&&(!input.snoozes||Array.isArray(input.snoozes)||typeof input.snoozes!=='object'||!Object.values(input.snoozes).every(v=>typeof v==='number'&&Number.isFinite(v)&&v>=0)))fail();
 const s=migrate(input);s.settings.enabled=false;return s;
}
