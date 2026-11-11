'use strict';
const fs=require('fs'),vm=require('vm'),{webcrypto}=require('crypto'),assert=require('assert');
const path=require('path');const root=path.resolve(__dirname,'..');let storage={};let tests=[];
const ctx={window:{},crypto:webcrypto,TextEncoder,URL,localStorage:{getItem:k=>storage[k]||null,setItem:(k,v)=>storage[k]=v},console};vm.createContext(ctx);vm.runInContext(fs.readFileSync(root+'/app/src/main/assets/ui/demo.js','utf8'),ctx);const api=ctx.window.Demo.request;
const ok=(name,value)=>{assert(value,name);tests.push({test:name,pass:true});console.log('PASS '+name);};
(async()=>{
 let b=await api('boot');ok('Sample has exactly one source',b.sources.length===1);
 let r=await api('browse',{kind:'movies',shelf:'new',sort:'az',limit:60});ok('Latest shelf remains latest even with other requested sort',r.total===50&&r.items[0].id==='movies_0');
 r=await api('browse',{kind:'all',q:'Deniz'});ok('Global cast search',r.total>0);
 r=await api('browse',{kind:'movies',q:'Deniz'});ok('Scoped search excludes cast',r.total===0);
 await api('setPin',{pin:'2684'});ok('PIN not stored in plaintext',!Object.values(storage).join('').includes('2684'));
 await api('groupAction',{id:'movies_0',action:'locked',value:true});await api('lock');
 for(const kind of ['all','movies','favorites']){
   r=await api('browse',{kind,limit:60});ok('Folder lock filters '+kind,r.items.every(i=>i.gid!=='movies_0'));
 }
 for(const shelf of ['recent','continue','new']){
   r=await api('browse',{kind:'movies',shelf,limit:60});ok('Folder lock filters '+shelf,r.items.every(i=>i.gid!=='movies_0'));
 }
 for(const method of ['details','play','favorite']){let rejected=false;try{await api(method,{id:'movies_0'});}catch(e){rejected=true;}ok('Folder lock blocks '+method,rejected);}
 let denied=false;try{await api('setPin',{pin:'1111'});}catch(e){denied=true;}ok('PIN cannot be overwritten while locked',denied);
 await api('unlock',{pin:'2684'});r=await api('browse',{kind:'movies',group:'movies_0'});ok('PIN unlock restores access',r.total>0);
 await api('progress',{id:'movies_0',pos:1234000,duration:5400000});r=await api('details',{id:'movies_0'});ok('Resume position retained',r.pos===1234000);
 await api('settings',{nav:['home','movies','live','series','favorites','settings']});b=await api('boot');ok('Navigation reorder persisted',b.settings.nav[1]==='movies');
 await api('groupOrder',{ids:['movies_2','movies_0','movies_1','movies_3']});r=await api('groups',{kind:'movies'});ok('Category reorder persisted',r[0].id==='movies_2');
 await api('groupAction',{id:'movies_0',action:'hidden',value:true});r=await api('browse',{kind:'all',limit:60});ok('Hidden content excluded from global view',r.items.every(i=>i.gid!=='movies_0'));
 await api('lock');let wrong=0;for(let n=0;n<5;n++){try{await api('unlock',{pin:'1111'});}catch(e){wrong++;}}ok('Five wrong PINs rejected',wrong===5);
 let throttle=false;try{await api('unlock',{pin:'2684'});}catch(e){throttle=e.message.includes('30');}ok('PIN attempt throttle enforced',throttle);
 const restored={...ctx,window:{}};vm.createContext(restored);vm.runInContext(fs.readFileSync(root+'/app/src/main/assets/ui/demo.js','utf8'),restored);b=await restored.window.Demo.request('boot');ok('Reload preserves settings',b.settings.nav[1]==='movies');ok('Reload resets unlocked session',b.unlocked===false);
 fs.writeFileSync(root+'/qa/demo-tests.json',JSON.stringify(tests,null,2));console.log(tests.length+' backend checks passed.');
})().catch(e=>{console.error(e);process.exit(1)});
