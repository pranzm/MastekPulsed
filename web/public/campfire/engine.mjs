/** Campfire POC engagement rules. Pure functions keep the synthetic browser journey testable.
 * Production must enforce the same rules in Java transactions; local state is not authorization. */
export const users = [
 {id:'asha',name:'Asha Patel',team:'Engineering',role:'Creator'},
 {id:'daniel',name:'Daniel Wright',team:'Delivery',role:'Creator'},
 {id:'priya',name:'Priya Nair',team:'People',role:'Reviewer'},
 {id:'marcus',name:'Marcus Vance',team:'Operations',role:'Publisher'},
 {id:'elena',name:'Elena Rostova',team:'Platform',role:'Creator'},
 {id:'rohan',name:'Rohan Das',team:'Marketing',role:'Creator'}
];
export function weekKey(date=new Date()) {const d=new Date(date);d.setUTCHours(0,0,0,0);d.setUTCDate(d.getUTCDate()-((d.getUTCDay()+6)%7));return d.toISOString().slice(0,10);}
export function initialState(week=weekKey()) {return {schema:1,week,hidden:[],kudos:[],stories:[
 {id:'seed-daniel',author:'daniel',title:'The photographer behind the delivery lead',body:'Photography taught me to notice what others miss. I enjoy early morning walks and would love to exchange composition tips with colleagues.',status:'PUBLISHED',revision:1,approvedRevision:1,reviewer:'priya',note:'Seeded, reviewed example.'},
 {id:'seed-elena',author:'elena',title:'Small gardens, patient progress',body:'I grow herbs on my balcony. A few minutes of care each day taught me that small, consistent effort matters. Ask me about starting with mint.',status:'PUBLISHED',revision:1,approvedRevision:1,reviewer:'priya',note:'Seeded, reviewed example.'},
 {id:'seed-marcus',author:'marcus',title:'Finding rhythm beyond work',body:'I am learning percussion. Practising with friends reminds me to listen before jumping in. I would enjoy meeting other beginner musicians.',status:'PUBLISHED',revision:1,approvedRevision:1,reviewer:'priya',note:'Seeded, reviewed example.'}
]};}
const person=id=>users.find(u=>u.id===id);
function requireRule(condition,message){if(!condition)throw Error(message);}
export function act(state,event){
 const s=structuredClone(state),u=person(event.user);requireRule(u,'Choose a demo persona.');
 if(event.type==='visibility'){s.hidden=s.hidden.filter(id=>id!==u.id);if(event.hidden)s.hidden.push(u.id);return s;}
 if(event.type==='submit'){
  requireRule(event.title?.trim().length>=5 && event.title.length<=100,'Use a title of 5–100 characters.');
  requireRule(event.body?.trim().length>=30 && event.body.length<=1200,'Write a story of 30–1,200 characters.');
  const existing=s.stories.find(x=>x.author===u.id && x.status!=='WITHDRAWN');
  requireRule(!existing || existing.status==='CHANGES_REQUESTED','One challenge story per person each week.');
  if(existing){existing.title=event.title.trim();existing.body=event.body.trim();existing.revision++;existing.status='IN_REVIEW';existing.approvedRevision=null;existing.reviewer=null;}
  else s.stories.push({id:event.id,author:u.id,title:event.title.trim(),body:event.body.trim(),status:'IN_REVIEW',revision:1,note:''});
  return s;
 }
 const story=s.stories.find(x=>x.id===event.story);requireRule(story,'Story not found.');
 if(event.type==='review'){
  requireRule(u.role==='Reviewer' && story.author!==u.id,'Only a different reviewer can review this story.');
  requireRule(story.status==='IN_REVIEW' && story.revision===event.revision,'This revision is no longer awaiting review.');
  requireRule(event.approve || event.note?.trim(),'Explain the changes needed.');
  story.status=event.approve?'APPROVED':'CHANGES_REQUESTED';story.approvedRevision=event.approve?story.revision:null;story.reviewer=u.id;story.note=event.note||'Reviewed for this synthetic demo.';return s;
 }
 if(event.type==='publish'){
  requireRule(u.role==='Publisher','Only the publisher can release a story.');
  requireRule(story.status==='APPROVED' && story.approvedRevision===story.revision && story.revision===event.revision,'Publication requires approval of the exact revision.');
  story.status='PUBLISHED';return s;
 }
 if(event.type==='withdraw'){requireRule(u.role==='Publisher' && story.status==='PUBLISHED','Only the publisher can withdraw a published story.');story.status='WITHDRAWN';return s;}
 if(event.type==='appreciate'){
  requireRule(story.status==='PUBLISHED','Only published stories can receive appreciation.');
  requireRule(story.author!==u.id,'Appreciate a colleague’s contribution.');
  requireRule(!s.kudos.some(k=>k.user===u.id&&k.story===story.id),'You already appreciated this story.');
  s.kudos.push({user:u.id,story:story.id});return s;
 }
 throw Error('Unknown action.');
}
export function progress(s,id){
 const pubs=s.stories.filter(x=>x.status==='PUBLISHED');
 const given=s.kudos.filter(k=>k.user===id&&pubs.some(p=>p.id===k.story));
 const receivers=s.kudos.filter(k=>pubs.some(p=>p.id===k.story&&p.author===id));
 const distinct=new Set(receivers.map(k=>k.user)).size;
 const contribution=pubs.some(p=>p.author===id)?20:0;
 const teams=new Set(given.map(k=>person(pubs.find(p=>p.id===k.story).author).team)).size;
 return {points:contribution+Math.min(3,given.length)*5+Math.min(5,distinct)*5,contribution,given:given.length,received:distinct,teams,badges:[...(contribution?['First Spark']:[]),...(teams>=3?['Connection Maker']:[]),...(distinct>=5?['Community Favourite']:[])]};
}
export function leaderboard(s){return users.filter(u=>!s.hidden.includes(u.id)).map(u=>({...u,...progress(s,u.id)})).sort((a,b)=>b.points-a.points||a.name.localeCompare(b.name));}
export function summary(s){const stories=s.stories.filter(x=>x.status==='PUBLISHED');return stories.length?stories.map(x=>`${person(x.author).name}: ${x.title}. ${x.body.split('. ')[0]}.`).join('\n'):'No published stories yet.';}
