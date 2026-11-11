package tr.lidertv.app;
import android.content.Context;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Source imports run off the UI thread. Old generation remains active until success. */
public final class Importer {
    public interface Progress {void update(long records);}
    private final Store db;private final Context context;private final Progress progress;
    public final AtomicBoolean busy=new AtomicBoolean(false);public volatile boolean cancelled=false;
    public Importer(Context c,Store db,Progress p){this.context=c;this.db=db;this.progress=p;}
    private static String enc(String s){try{return java.net.URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){throw new AssertionError(e);}}
    private Reader network(String address,boolean allowHttp)throws Exception{
        URI u=URI.create(address);if(!Arrays.asList("http","https").contains(u.getScheme()))throw new Text.UserError("Kaynak adresi http:// veya https:// ile ba\u015flamal\u0131.");
        if(u.getScheme().equals("http")&&!allowHttp)throw new Text.UserError("Bu kaynak \u015fifrelenmemi\u015f HTTP kullan\u0131yor. Eklerken HTTP onay\u0131 gerekli.");
        for(int i=0;i<6;i++){
            HttpURLConnection c=(HttpURLConnection)u.toURL().openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","LiderTv/0.1.0");
            int code=c.getResponseCode();
            if(code>=300&&code<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new Text.UserError("Ge\u00e7ersiz sunucu y\u00f6nlendirmesi.");URI n=u.resolve(next);if(!Arrays.asList("http","https").contains(n.getScheme())||(u.getScheme().equals("https")&&n.getScheme().equals("http")))throw new Text.UserError("G\u00fcvensiz sunucu y\u00f6nlendirmesi engellendi.");u=n;continue;}
            if(code!=200){c.disconnect();String reason=switch(code){case 401,403->"Eri\u015fim reddedildi; hesap veya abonelik yetkisini kontrol edin.";case 404->"Kaynak sunucuda bulunamad\u0131.";case 429->"Sunucu istek s\u0131n\u0131r\u0131na ula\u015f\u0131ld\u0131.";default->"Sunucu iste\u011fi tamamlayamad\u0131.";};throw new Text.UserError("HTTP "+code+": "+reason);}
            HttpURLConnection conn=c;
            return new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8),65536){@Override public void close()throws IOException{try{super.close();}finally{conn.disconnect();}}};
        }throw new Text.UserError("\u00c7ok fazla sunucu y\u00f6nlendirmesi.");
    }
    private String endpoint(JSONObject src,String action)throws Exception{
        String base=src.getString("url").replaceAll("/+$","");URI u=URI.create(base);
        if(u.getHost()==null||u.getRawQuery()!=null||u.getRawFragment()!=null||u.getRawUserInfo()!=null)throw new Text.UserError("Xtream sunucusuna yaln\u0131zca sunucu ve port adresini girin. Kullan\u0131c\u0131 ad\u0131 ve parolay\u0131 ayr\u0131 alanlara yaz\u0131n.");
        return base+"/player_api.php?username="+enc(src.getString("user"))+"&password="+enc(src.getString("pass"))+"&action="+action;
    }
    private Object readValue(JsonReader r)throws Exception{
        JsonToken t=r.peek();if(t==JsonToken.BEGIN_OBJECT){JSONObject o=new JSONObject();r.beginObject();while(r.hasNext()){String k=r.nextName();o.put(k,readValue(r));}r.endObject();return o;}
        if(t==JsonToken.BEGIN_ARRAY){JSONArray a=new JSONArray();r.beginArray();while(r.hasNext())a.put(readValue(r));r.endArray();return a;}
        if(t==JsonToken.NULL){r.nextNull();return JSONObject.NULL;}if(t==JsonToken.BOOLEAN)return r.nextBoolean();return r.nextString();
    }
    private JSONObject object(JSONObject src,String action)throws Exception{
        try(Reader rd=network(endpoint(src,action),src.optBoolean("allowHttp"));JsonReader r=new JsonReader(rd)){Object o=readValue(r);if(!(o instanceof JSONObject))throw new Text.UserError("Sa\u011flay\u0131c\u0131 ayr\u0131nt\u0131 yan\u0131t\u0131 beklenen bi\u00e7imde de\u011fil.");return (JSONObject)o;}
    }
    private interface Receiver {void item(JSONObject o)throws Exception;}
    private void array(JSONObject src,String action,Receiver receiver)throws Exception{
        try(Reader rd=network(endpoint(src,action),src.optBoolean("allowHttp"));JsonReader r=new JsonReader(rd)){
            if(r.peek()!=JsonToken.BEGIN_ARRAY)throw new Text.UserError("Sa\u011flay\u0131c\u0131 liste yerine farkl\u0131 bir yan\u0131t verdi. Hesap bilgilerini kontrol edin.");
            r.beginArray();while(r.hasNext()){checkCancel();Object o=readValue(r);if(o instanceof JSONObject)receiver.item((JSONObject)o);}r.endArray();
        }
    }
    private void checkCancel()throws Text.UserError{if(cancelled||Thread.currentThread().isInterrupted())throw new Text.UserError("\u0130\u00e7e aktarma iptal edildi. \u00d6nceki liste korundu.");}
    private final class Writer implements AutoCloseable {
        long n=0;boolean inTx=false;final Map<String,String>groups=new HashMap<>();final String sid;final long gen;
        Writer(String sid,long gen){this.sid=sid;this.gen=gen;}
        void add(JSONObject row)throws Exception{
            checkCancel();if(!inTx){db.getWritableDatabase().beginTransactionNonExclusive();inTx=true;}
            String kind=row.getString("kind"),name=row.optString("group","Di\u011fer"),key=kind+"\u0000"+name;
            String gid=groups.get(key);if(gid==null){gid=db.group(sid,kind,name);groups.put(key,gid);}
            db.putItem(row.put("sid",sid).put("gen",gen).put("gid",gid).put("seq",n++));
            if(n%400==0){flush();progress.update(n);}
        }
        void flush(){if(inTx){db.getWritableDatabase().setTransactionSuccessful();db.getWritableDatabase().endTransaction();inTx=false;}}
        public void close(){if(inTx){db.getWritableDatabase().endTransaction();inTx=false;}}
    }
    public JSONObject importSource(JSONObject spec)throws Exception{
        db.vault.requireAdmin();if(!busy.compareAndSet(false,true))throw new Text.UserError("Ba\u015fka bir liste i\u015fleniyor.");cancelled=false;
        String sid=spec.optString("id");boolean fresh=sid.isEmpty();long gen=System.currentTimeMillis();
        try{
            if(fresh){sid=UUID.randomUUID().toString();spec.put("id",sid);db.createSource(spec);}else spec=db.source(sid);
            String sourceId=sid;long raw=0,inferred=0,skipped=0;
            try(Writer w=new Writer(sid,gen)){
                if(spec.getString("type").equals("xtream")){
                    JSONObject src=spec;
                    for(String kind:new String[]{"live","movies","series"}){
                        Map<String,String> cats=new HashMap<>();String apiType=kind.equals("movies")?"vod":kind;
                        array(src,"get_"+apiType+"_categories",o->cats.put(o.optString("category_id"),o.optString("category_name","Di\u011fer")));
                        String action=kind.equals("series")?"get_series":"get_"+apiType+"_streams";
                        array(src,action,o->{
                            String remote=o.optString(kind.equals("series")?"series_id":"stream_id");if(remote.isEmpty())throw new Text.UserError("Sa\u011flay\u0131c\u0131 i\u00e7erik kimli\u011fi g\u00f6ndermedi.");
                            String ext=o.optString("container_extension",kind.equals("live")?"ts":"mp4").replaceAll("[^A-Za-z0-9]","");if(ext.isEmpty())ext=kind.equals("live")?"ts":"mp4";
                            String path=kind.equals("movies")?"movie":kind;
                            String stream=kind.equals("series")?"":src.getString("url").replaceAll("/+$","")+"/"+path+"/"+enc(src.getString("user"))+"/"+enc(src.getString("pass"))+"/"+remote+"."+ext;
                            w.add(new JSONObject().put("id",Text.id(sourceId+"\u0000"+kind+"\u0000"+remote)).put("kind",kind).put("title",o.optString("name","Ads\u0131z i\u00e7erik")).put("remote",remote).put("url",stream)
                                .put("group",cats.getOrDefault(o.optString("category_id"),"Di\u011fer")).put("poster",o.optString(kind.equals("series")?"cover":"stream_icon"))
                                .put("cast",o.optString("cast")).put("plot",o.optString("plot")).put("added",date(o,kind.equals("series")?"last_modified":"added")));
                        });
                    }raw=w.n;
                }else{
                    Reader rd;if(spec.getString("type").equals("file")){InputStream in=context.getContentResolver().openInputStream(Uri.parse(spec.getString("url")));if(in==null)throw new Text.UserError("Dosya a\u00e7\u0131lamad\u0131.");rd=new InputStreamReader(in,StandardCharsets.UTF_8);}else rd=network(spec.getString("url"),spec.optBoolean("allowHttp"));
                    try(Reader reader=rd){M3uParser.Stats st=M3uParser.parse(reader,e->{JSONObject extra=new JSONObject().put("userAgent",e.userAgent).put("referer",e.referer);
                        w.add(new JSONObject().put("id",Text.id(sourceId+"\u0000"+e.kind+"\u0000"+M3uParser.canonical(e.url))).put("kind",e.kind).put("title",e.title).put("url",e.url).put("group",e.group).put("poster",e.poster).put("cast",e.cast).put("plot",e.plot).put("added",e.added).put("extra",extra));
                    });raw=st.records;inferred=st.inferred;skipped=st.skipped;}
                }
                w.flush();
            }
            checkCancel();db.commitGeneration(sid,gen,raw,inferred,skipped);return new JSONObject().put("sources",db.sources()).put("stats",db.stats());
        }catch(Exception e){if(!sid.isEmpty()){db.discardGeneration(sid,gen);if(fresh)db.getWritableDatabase().delete("sources","id=? AND gen=0",new String[]{sid});}throw e;
        }finally{busy.set(false);}
    }
    private static long date(JSONObject o,String k){long n=o.optLong(k,0);return n>0&&n<100000000000L?n*1000:n;}
    public JSONObject details(String id)throws Exception{
        JSONObject row=db.item(id,true),src=db.source(row.getString("sid"));
        if(src.getString("type").equals("xtream")&&!row.optString("remote").isEmpty()&&row.optString("parent").isEmpty()&&!row.getString("kind").equals("live")){
            boolean series=row.getString("kind").equals("series");JSONObject extra=row.getJSONObject("extra");
            if(!extra.optBoolean("metadataLoaded")){
                JSONObject result=object(src,series?"get_series_info&series_id="+enc(row.getString("remote")):"get_vod_info&vod_id="+enc(row.getString("remote")));
                JSONObject info=result.optJSONObject("info");if(info!=null){row.put("plot",info.optString("plot",info.optString("description",row.optString("plot"))));row.put("cast",info.optString("cast",info.optString("actors",row.optString("cast"))));row.put("poster",info.optString("movie_image",info.optString("cover",row.optString("poster"))));}
                if(series){JSONObject episodes=result.optJSONObject("episodes");if(episodes!=null){List<String>seasons=new ArrayList<>();episodes.keys().forEachRemaining(seasons::add);seasons.sort(Comparator.comparingInt(s->{try{return Integer.parseInt(s);}catch(Exception e){return 0;}}));long seq=0;
                    for(String season:seasons){JSONArray list=episodes.optJSONArray(season);if(list==null)continue;for(int i=0;i<list.length();i++){
                        JSONObject ep=list.getJSONObject(i);String rid=ep.optString("id");if(rid.isEmpty())continue;String ext=ep.optString("container_extension","mp4").replaceAll("[^A-Za-z0-9]","");JSONObject ei=ep.optJSONObject("info");if(ei==null)ei=new JSONObject();
                        String url=src.getString("url").replaceAll("/+$","")+"/series/"+enc(src.getString("user"))+"/"+enc(src.getString("pass"))+"/"+rid+"."+(ext.isEmpty()?"mp4":ext);
                        db.putItem(new JSONObject().put("id",Text.id(row.getString("sid")+"\u0000episode\u0000"+rid)).put("sid",row.getString("sid")).put("gen",row.getLong("gen")).put("kind","series").put("gid",row.getString("gid"))
                            .put("title","S"+season+" B"+ep.optString("episode_num",""+(i+1))+" \u00b7 "+ep.optString("title",row.getString("title"))).put("parent",id).put("remote",rid).put("url",url).put("poster",ei.optString("movie_image",row.optString("poster")))
                            .put("plot",ei.optString("plot",row.optString("plot"))).put("cast",row.optString("cast")).put("seq",seq++).put("extra",new JSONObject()));
                        if(seq>=2000)break;
                    }if(seq>=2000)break;}
                }}
                extra.put("metadataLoaded",true);row.put("extra",extra);db.putItem(row);
            }
        }
        JSONObject out=db.item(id,false);out.put("episodes",db.episodes(id));return out;
    }
}
