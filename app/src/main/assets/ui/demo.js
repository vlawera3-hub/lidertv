/* Browser-only sample backend. Android uses native SQLite and native players instead. */
'use strict';
window.Demo = (() => {
  if(window.LiderNative)return {};
  const KEY='LiderTv.preview.v1', fold=s=>String(s||'').toLocaleLowerCase('tr').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/\u0131/g,'i');
  let unlocked=false,db;
  const save=()=>{try{localStorage.setItem(KEY,JSON.stringify(db));}catch(e){}};
  function seed(){
    const groups=[],items=[],now=Date.now();
    const names={live:['Ulusal','Haber','Belgesel','Spor'],movies:['Sinema','Macera','Dram','Bilim kurgu'],series:['Yerli diziler','D\u00fcnyadan diziler','Mini diziler']};
    for(const kind of Object.keys(names))names[kind].forEach((name,i)=>groups.push({id:kind+'_'+i,sid:'demo',kind,name,hidden:false,locked:false,pinned:false,position:i}));
    const films=['Geceye Yolculuk','K\u0131y\u0131daki Ev','Kay\u0131p Rota','G\u00f6ky\u00fcz\u00fc Defteri','Sessiz \u015eehir','Yar\u0131na Bir Mektup','Ufkun \u00d6tesi','Son Vapur','Kuzey I\u015f\u0131klar\u0131','Zaman\u0131n \u0130zinde','Bir Yaz Masal\u0131','Uzak \u0130stasyon'];
    for(const kind of ['live','movies','series'])for(let i=0;i<(kind==='movies'?68:kind==='series'?9:12);i++){
      const g=groups.filter(g=>g.kind===kind)[i%names[kind].length];
      const title=kind==='live'?['Lider Haber','D\u00fcnya Belgesel','Spor Ekran\u0131','K\u00fclt\u00fcr TV','G\u00fcn\u00fcn Sesi','Do\u011fa Ekran\u0131','Sahne TV','Kent Haber','Ke\u015fif TV','Bilgi Ekran\u0131','M\u00fczik Sahnesi','Yolculuk TV'][i]:kind==='movies'?films[i%films.length]+(i>=films.length?' '+(Math.floor(i/films.length)+1):''):['K\u0131y\u0131 Hik\u00e2yeleri','Son Sokak','G\u00f6ky\u00fcz\u00fc Atlas\u0131','Birlikte','Yar\u0131n Sabah','Kuzey Yolu','Yol Arkada\u015flar\u0131','Yeni Bir Hayat','G\u00f6lge Oyunu'][i];
      items.push({id:kind+'_'+i,sid:'demo',kind,gid:g.id,group:g.name,title,poster:'',cast:i%2?'Ece \u00d6rnek, Deniz \u00d6rnek':'Deniz \u00d6rnek, Can \u00d6rnek',plot:'Bu, LiderTv aray\u00fcz\u00fcn\u00fc denemek i\u00e7in haz\u0131rlanm\u0131\u015f temsili bir i\u00e7eriktir. Afi\u015f, a\u00e7\u0131klama ve oyuncu adlar\u0131 \u00f6rnek veridir; ger\u00e7ek bir yay\u0131n eklenmemi\u015ftir.',added:now-(i*86400000),seen:now,seq:i,pos:i===1&&kind!=='live'?1140000:0,duration:kind==='live'?0:5400000,lastplayed:i<3?now-(i*3600000):0,favorite:i===2,parent:'',url:''});
    }
    return {sources:[{id:'demo',name:'\u00d6rnek k\u00fct\u00fcphane',type:'demo',raw:89,unique:89,duplicates:0,inferred:0,skipped:0,updated:now}],groups,items,settings:{mode:'auto',theme:'dark',engine:'auto',fallback:true,nav:['home','live','movies','series','favorites','settings'],sleep:0},pin:null,failures:0,until:0};
  }
  try{db=JSON.parse(localStorage.getItem(KEY)||'null')||seed();}catch(e){db=seed();}save();
  const group=id=>db.groups.find(g=>g.id===id), access=i=>{const g=group(i.gid);return g&&!g.hidden&&(!g.locked||unlocked);};
  function item(id){const i=db.items.find(i=>i.id===id);if(!i||!access(i))throw Error('\u0130\u00e7erik yok, gizli veya ebeveyn kilidi etkin.');return i;}
  function admin(){if(db.pin&&!unlocked)throw Error('Bu i\u015flem i\u00e7in Ebeveyn denetimi b\u00f6l\u00fcm\u00fcnden PIN ile oturumu a\u00e7\u0131n.');}
  function boot(){return {settings:db.settings,sources:db.sources,stats:Object.fromEntries(['live','movies','series'].map(k=>[k,db.items.filter(i=>i.kind===k&&!i.parent&&access(i)).length])),hasPin:!!db.pin,unlocked,tv:false,version:'0.1.0',native:false};}
  async function hash(pin,salt){if(!crypto.subtle)throw Error('Bu taray\u0131c\u0131da g\u00fcvenli PIN denemesi kullan\u0131lam\u0131yor. Chrome veya Edge ile a\u00e7\u0131n.');const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(pin),'PBKDF2',false,['deriveBits']);const bits=await crypto.subtle.deriveBits({name:'PBKDF2',salt:new Uint8Array(salt),iterations:120000,hash:'SHA-256'},key,256);return Array.from(new Uint8Array(bits)).map(x=>x.toString(16).padStart(2,'0')).join('');}
  async function request(method,q={}){
    switch(method){
      case 'boot':return boot();
      case 'browse':{
        if(q.shelf==='new')q={...q,sort:'new'}; let a=db.items.filter(i=>!i.parent&&access(i));if(q.kind&&!['all','favorites'].includes(q.kind))a=a.filter(i=>i.kind===q.kind);if(q.sid)a=a.filter(i=>i.sid===q.sid);if(q.group&&q.group!=='all')a=a.filter(i=>i.gid===q.group);if(q.kind==='favorites')a=a.filter(i=>i.favorite);
        if(q.shelf==='recent')a=a.filter(i=>i.lastplayed>0);if(q.shelf==='continue')a=a.filter(i=>i.kind!=='live'&&i.pos>10000&&(!i.duration||i.pos<i.duration*.95));
        const tokens=fold(q.q).split(/\s+/).filter(Boolean);if(tokens.length)a=a.filter(i=>tokens.every(w=>fold(i.title+(q.kind==='all'?' '+i.cast:'')).includes(w)));
        a=a.slice().sort((x,y)=>q.shelf==='recent'||q.shelf==='continue'?y.lastplayed-x.lastplayed:q.sort==='az'?x.title.localeCompare(y.title,'tr'):q.sort==='za'?y.title.localeCompare(x.title,'tr'):q.sort==='old'?(x.added||x.seen)-(y.added||y.seen):((y.added||y.seen)-(x.added||x.seen)||y.seq-x.seq));
        if(q.shelf==='new')a=a.slice(0,50);const offset=q.offset||0,limit=Math.min(60,q.limit||48),out=a.slice(offset,offset+limit);return {items:out,total:a.length,next:offset+out.length<a.length?offset+out.length:-1};
      }
      case 'groups':return db.groups.filter(g=>(!q.kind||['all','favorites'].includes(q.kind)||g.kind===q.kind)&&(!q.sid||g.sid===q.sid)).sort((a,b)=>Number(b.pinned)-Number(a.pinned)||a.position-b.position);
      case 'details':{const i=item(q.id);return {...i,episodes:i.kind==='series'?[1,2,3].map((n)=>({...i,id:i.id+'_ep'+n,title:'1. Sezon \u00b7 '+n+'. B\u00f6l\u00fcm',parent:i.id})):[]};}
      case 'favorite':{const i=item(q.id);i.favorite=!i.favorite;save();return {};}
      case 'settings':db.settings={...db.settings,...q};save();return boot();
      case 'groupAction':{admin();const g=group(q.id);if(!g)throw Error('Klas\u00f6r bulunamad\u0131.');if(q.action==='locked'&&!db.pin)throw Error('\u00d6nce Ayarlar > Ebeveyn denetimi b\u00f6l\u00fcm\u00fcnden PIN belirleyin.');if(!['locked','hidden','pinned'].includes(q.action))throw Error('Ge\u00e7ersiz i\u015flem.');g[q.action]=q.value;save();return {};}
      case 'groupOrder':admin();q.ids.forEach((id,n)=>{const g=group(id);if(g)g.position=n;});save();return {};
      case 'setPin':{admin();if(!/^\d{4,8}$/.test(q.pin))throw Error('PIN, 4-8 rakamdan olu\u015fmal\u0131.');const salt=Array.from(crypto.getRandomValues(new Uint8Array(24)));db.pin={salt,hash:await hash(q.pin,salt)};db.failures=0;db.until=0;unlocked=true;save();return boot();}
      case 'unlock':{if(!db.pin)throw Error('\u00d6nce PIN belirleyin.');if(Date.now()<db.until)throw Error('30 saniye sonra tekrar deneyin.');if(await hash(q.pin,db.pin.salt)!==db.pin.hash){db.failures++;if(db.failures%5===0)db.until=Date.now()+30000;save();throw Error('PIN yanl\u0131\u015f.');}db.failures=0;db.until=0;unlocked=true;save();return boot();}
      case 'lock':unlocked=false;return boot();
      case 'clearHistory':admin();db.items.forEach(i=>{i.pos=0;i.lastplayed=0;});save();return {};
      case 'deleteSource':{admin();db.sources=db.sources.filter(s=>s.id!==q.id);db.items=db.items.filter(i=>i.sid!==q.id);db.groups=db.groups.filter(g=>g.sid!==q.id);save();return boot();}
      case 'progress':{const i=item(q.id.replace(/_ep\d+$/,''));i.pos=q.pos;i.duration=q.duration;i.lastplayed=Date.now();save();return {};}
      case 'play':return item(q.id.replace(/_ep\d+$/,''));
      case 'import':throw Error('A\u011f kayna\u011f\u0131 ekleme Android kaynak projesindedir. Bu HTML \u00f6rne\u011finde yerel M3U dosyas\u0131 se\u00e7ebilirsiniz.');
      case 'resetDemo':db=seed();unlocked=false;save();return boot();
      default:throw Error('Bu i\u015flem taray\u0131c\u0131 \u00f6rne\u011finde kullan\u0131lam\u0131yor.');
    }
  }
  async function importFile(file){
    admin();if(file.size>5*1024*1024)throw Error('HTML \u00f6rne\u011fi en fazla 5 MB M3U dosyas\u0131 al\u0131r. B\u00fcy\u00fck listeler i\u00e7in Android s\u00fcr\u00fcm\u00fcn\u00fcn ak\u0131\u015fl\u0131 aktar\u0131m\u0131 gerekir.');
    const text=await file.text(),sid='file_'+Date.now(),rows=[],groups=new Map(),dedup=new Set();let meta=null,raw=0,skipped=0,inferred=0;
    for(let line of text.split(/\r?\n/)){line=line.replace(/^\uFEFF/,'').trim();if(line.startsWith('#EXTINF:')){
      const attrs={};for(const m of line.matchAll(/([\w-]+)\s*=\s*"([^"]*)"/g))attrs[m[1]]=m[2];let quoted=false,comma=-1;for(let i=0;i<line.length;i++){if(line[i]==='"')quoted=!quoted;if(line[i]===','&&!quoted){comma=i;break;}}meta={attrs,title:comma>=0?line.slice(comma+1):attrs['tvg-name']||'Ads\u0131z'};continue;
    }if(!line||line[0]==='#')continue;if(!meta){skipped++;continue;}let url;try{url=new URL(line);if(!['http:','https:','rtsp:','rtmp:'].includes(url.protocol))throw Error();}catch(e){skipped++;meta=null;continue;}
      raw++;const a=meta.attrs,gname=a['group-title']||'Di\u011fer';let kind=a.type==='live'?'live':['vod','movie','movies'].includes(a.type)?'movies':a.type==='series'?'series':null;
      if(!kind){inferred++;const x=fold(gname);kind=/\/series\//.test(url.pathname)||/dizi|series/.test(x)?'series':/\/movie\//.test(url.pathname)||/film|movie|vod/.test(x)||/\.(mp4|mkv|avi|mov)$/i.test(url.pathname)?'movies':'live';}
      const key=kind+'|'+line;if(dedup.has(key)){meta=null;continue;}dedup.add(key);const gkey=kind+'|'+gname;if(!groups.has(gkey))groups.set(gkey,{id:sid+'_g'+groups.size,sid,kind,name:gname,hidden:false,locked:false,pinned:false,position:groups.size});const g=groups.get(gkey);const n=Number(a.added||0);
      rows.push({id:sid+'_'+rows.length,sid,kind,gid:g.id,group:g.name,title:meta.title,poster:a['tvg-logo']||'',cast:a.cast||a.actors||'',plot:a.plot||a.description||'',url:'',added:n>0?(n<1e11?n*1000:n):0,seen:Date.now(),seq:rows.length,parent:'',pos:0,duration:0,lastplayed:0,favorite:false});meta=null;
    }
    if(!rows.length)throw Error('Ge\u00e7erli M3U i\u00e7eri\u011fi bulunamad\u0131.');db.items.push(...rows);db.groups.push(...groups.values());db.sources.push({id:sid,name:file.name,type:'file',raw,unique:rows.length,duplicates:raw-rows.length,inferred,skipped,updated:Date.now()});save();return boot();
  }
  return {request,importFile,fold};
})();
