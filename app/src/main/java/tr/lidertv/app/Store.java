package tr.lidertv.app;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
/** SQLite, FTS, generation-based atomic refresh, per-folder parental filtering. */
public final class Store extends SQLiteOpenHelper {
    public final Vault vault; private final Context context;
    public Store(Context c){super(c,"lidertv.db",null,2);context=c.getApplicationContext();vault=new Vault(c);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE sources(id TEXT PRIMARY KEY,name TEXT NOT NULL,type TEXT NOT NULL,secret TEXT NOT NULL,gen INTEGER NOT NULL DEFAULT 0,raw_count INTEGER DEFAULT 0,unique_count INTEGER DEFAULT 0,inferred INTEGER DEFAULT 0,skipped INTEGER DEFAULT 0,updated INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE groups(id TEXT PRIMARY KEY,sid TEXT,kind TEXT,name TEXT,hidden INTEGER DEFAULT 0,pinned INTEGER DEFAULT 0,locked INTEGER DEFAULT 0,position INTEGER DEFAULT 999999)");
        db.execSQL("CREATE TABLE items(id TEXT, sid TEXT, gen INTEGER, kind TEXT,gid TEXT,title TEXT,title_key TEXT,cast_text TEXT,cast_key TEXT,plot TEXT,poster TEXT,url TEXT,remote_id TEXT,extra TEXT,added INTEGER,seen INTEGER,seq INTEGER,parent TEXT DEFAULT '',fingerprint TEXT DEFAULT '',PRIMARY KEY(id,gen))");
        db.execSQL("CREATE INDEX items_scope ON items(sid,gen,kind,parent,gid)");
        db.execSQL("CREATE INDEX items_id ON items(id)");
        db.execSQL("CREATE INDEX items_dates ON items(sid,gen,added,seen)");
        db.execSQL("CREATE INDEX items_parent ON items(parent,gen)");
        db.execSQL("CREATE TABLE history(id TEXT PRIMARY KEY,pos INTEGER DEFAULT 0,duration INTEGER DEFAULT 0,lastplayed INTEGER)");
        db.execSQL("CREATE TABLE favorites(id TEXT PRIMARY KEY)");
        db.execSQL("CREATE VIRTUAL TABLE item_search USING fts4(title_key,cast_key,tokenize=unicode61)");
        db.execSQL("CREATE TABLE staging_items(run TEXT NOT NULL,id TEXT PRIMARY KEY,sid TEXT,gen INTEGER,kind TEXT,gid TEXT,title TEXT,title_key TEXT,cast_text TEXT,cast_key TEXT,plot TEXT,poster TEXT,url TEXT,remote_id TEXT,extra TEXT,added INTEGER,seen INTEGER,seq INTEGER,parent TEXT,fingerprint TEXT)");
        db.execSQL("CREATE INDEX staging_run ON staging_items(run,sid)");
        db.execSQL("CREATE TRIGGER item_ai AFTER INSERT ON items BEGIN INSERT INTO item_search(docid,title_key,cast_key) VALUES(new.rowid,new.title_key,new.cast_key); END");
        db.execSQL("CREATE TRIGGER item_bd BEFORE DELETE ON items BEGIN DELETE FROM item_search WHERE docid=old.rowid; END");
        db.execSQL("CREATE TRIGGER item_bu BEFORE UPDATE ON items BEGIN DELETE FROM item_search WHERE docid=old.rowid; END");
        db.execSQL("CREATE TRIGGER item_au AFTER UPDATE ON items BEGIN INSERT INTO item_search(docid,title_key,cast_key) VALUES(new.rowid,new.title_key,new.cast_key); END");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int now){
        if(old<2){
            try{db.execSQL("ALTER TABLE items ADD COLUMN fingerprint TEXT DEFAULT ''");}catch(SQLException ignored){}
            try{db.execSQL("CREATE INDEX items_id ON items(id)");}catch(SQLException ignored){}
            db.execSQL("CREATE TABLE IF NOT EXISTS staging_items(run TEXT NOT NULL,id TEXT PRIMARY KEY,sid TEXT,gen INTEGER,kind TEXT,gid TEXT,title TEXT,title_key TEXT,cast_text TEXT,cast_key TEXT,plot TEXT,poster TEXT,url TEXT,remote_id TEXT,extra TEXT,added INTEGER,seen INTEGER,seq INTEGER,parent TEXT,fingerprint TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS staging_run ON staging_items(run,sid)");
        }
    }
    public JSONObject settings()throws Exception{return new JSONObject(context.getSharedPreferences("ui",0).getString("settings","{}"));}
    public void saveSettings(JSONObject s)throws Exception{
        // Security credentials and PIN are never accepted through arbitrary UI preferences.
        JSONObject safe=new JSONObject();for(String k:new String[]{"mode","theme","engine","fallback","nav","sleep","showCaptions"})if(s.has(k))safe.put(k,s.get(k));
        context.getSharedPreferences("ui",0).edit().putString("settings",safe.toString()).apply();
    }
    private static String str(Cursor c,String k){String s=c.getString(c.getColumnIndexOrThrow(k));return s==null?"":s;}
    private static long num(Cursor c,String k){return c.getLong(c.getColumnIndexOrThrow(k));}
    public JSONObject source(String id)throws Exception{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM sources WHERE id=?",new String[]{id})){
            if(!c.moveToFirst())throw new Text.UserError("Kaynak bulunamad\u0131.");JSONObject s=new JSONObject(vault.decrypt(str(c,"secret")));
            return s.put("id",id).put("name",str(c,"name")).put("type",str(c,"type")).put("gen",num(c,"gen"));
        }
    }
    public JSONArray sources()throws Exception{
        JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM sources ORDER BY name",null)){
            while(c.moveToNext())a.put(new JSONObject().put("id",str(c,"id")).put("name",str(c,"name")).put("type",str(c,"type")).put("raw",num(c,"raw_count")).put("unique",num(c,"unique_count")).put("duplicates",Math.max(0,num(c,"raw_count")-num(c,"unique_count"))).put("inferred",num(c,"inferred")).put("skipped",num(c,"skipped")).put("updated",num(c,"updated")));
        }return a;
    }
    public void createSource(JSONObject src)throws Exception{
        vault.requireAdmin();ContentValues v=new ContentValues();v.put("id",src.getString("id"));v.put("name",src.optString("name","Kayna\u011f\u0131m"));v.put("type",src.getString("type"));v.put("secret",vault.encrypt(src.toString()));getWritableDatabase().insertOrThrow("sources",null,v);
    }
    public void removeSource(String sid)throws Exception{
        vault.requireAdmin();SQLiteDatabase db=getWritableDatabase();db.beginTransactionNonExclusive();try{
            db.execSQL("DELETE FROM history WHERE id IN(SELECT id FROM items WHERE sid=?)",new Object[]{sid});db.execSQL("DELETE FROM favorites WHERE id IN(SELECT id FROM items WHERE sid=?)",new Object[]{sid});
            db.delete("items","sid=?",new String[]{sid});db.delete("groups","sid=?",new String[]{sid});db.delete("sources","id=?",new String[]{sid});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public String group(String sid,String kind,String name){
        String id=Text.id(sid+"\u0000"+kind+"\u0000"+name);ContentValues v=new ContentValues();v.put("id",id);v.put("sid",sid);v.put("kind",kind);v.put("name",name);
        getWritableDatabase().insertWithOnConflict("groups",null,v,SQLiteDatabase.CONFLICT_IGNORE);return id;
    }
    private static String fingerprint(JSONObject row){
        String raw=row.optString("kind")+"\u0000"+row.optString("title")+"\u0000"+row.optString("group")+"\u0000"+row.optString("url")+"\u0000"+row.optString("poster")+"\u0000"+row.optString("cast")+"\u0000"+row.optString("plot")+"\u0000"+row.optLong("added")+"\u0000"+row.optString("parent")+"\u0000"+row.optString("remote");
        return Text.id(raw);
    }
    private ContentValues itemValues(JSONObject row,String sid,long gen,long seen){
        ContentValues v=new ContentValues();v.put("id",row.getString("id"));v.put("sid",sid);v.put("gen",gen);v.put("kind",row.getString("kind"));v.put("gid",row.getString("gid"));
        v.put("title",row.getString("title"));v.put("title_key",Text.fold(row.getString("title")));v.put("cast_text",row.optString("cast"));v.put("cast_key",Text.fold(row.optString("cast")));v.put("plot",row.optString("plot"));v.put("poster",row.optString("poster"));
        v.put("url",vault.encrypt(row.optString("url")));v.put("remote_id",row.optString("remote"));v.put("extra",vault.encrypt(row.optJSONObject("extra")==null?"{}":row.getJSONObject("extra").toString()));
        v.put("added",row.optLong("added"));v.put("seen",seen);v.put("seq",row.optLong("seq"));v.put("parent",row.optString("parent"));v.put("fingerprint",fingerprint(row));return v;
    }
    public void stageItem(String run,JSONObject row,long gen)throws Exception{
        String id=row.getString("id"); SQLiteDatabase db=getWritableDatabase();
        ContentValues v=itemValues(row,row.getString("sid"),gen,row.optLong("seen",System.currentTimeMillis()));
        v.put("run",run);db.insertWithOnConflict("staging_items",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public JSONObject syncStaging(String sid,String run,long gen,long raw,long inferred,long skipped)throws Exception{
        SQLiteDatabase db=getWritableDatabase();
        long unique=DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM staging_items WHERE run=? AND sid=? AND parent=''",new String[]{run,sid});
        if(unique==0)throw new Text.UserError("Kaynak bo\u015f d\u00f6nd\u00fc; mevcut liste korunuyor.");
        db.beginTransactionNonExclusive();
        try{
            // Preserve first-seen dates and existing metadata when the provider omits them.
            db.execSQL("UPDATE staging_items SET seen=COALESCE((SELECT old.seen FROM items old WHERE old.id=staging_items.id AND old.sid=? ORDER BY old.gen DESC LIMIT 1),seen), cast_text=CASE WHEN trim(cast_text)='' THEN COALESCE((SELECT old.cast_text FROM items old WHERE old.id=staging_items.id AND old.sid=? ORDER BY old.gen DESC LIMIT 1),cast_text) ELSE cast_text END, plot=CASE WHEN trim(plot)='' THEN COALESCE((SELECT old.plot FROM items old WHERE old.id=staging_items.id AND old.sid=? ORDER BY old.gen DESC LIMIT 1),plot) ELSE plot END WHERE run=? AND sid=?",new Object[]{sid,sid,sid,run,sid});
            // Delete only removed or changed rows. Stable IDs keep favourites/history attached to the content.
            db.execSQL("DELETE FROM items WHERE sid=? AND (NOT EXISTS(SELECT 1 FROM staging_items s WHERE s.run=? AND s.id=items.id) OR EXISTS(SELECT 1 FROM staging_items s WHERE s.run=? AND s.id=items.id AND s.fingerprint<>items.fingerprint))",new Object[]{sid,run,run});
            // Insert new and changed rows in one transaction. FTS triggers run once per affected row.
            db.execSQL("INSERT INTO items(id,sid,gen,kind,gid,title,title_key,cast_text,cast_key,plot,poster,url,remote_id,extra,added,seen,seq,parent,fingerprint) SELECT s.id,s.sid,?,?,s.gid,s.title,s.title_key,s.cast_text,s.cast_key,s.plot,s.poster,s.url,s.remote_id,s.extra,s.added,s.seen,s.seq,s.parent,s.fingerprint FROM staging_items s WHERE s.run=? AND s.sid=? AND NOT EXISTS(SELECT 1 FROM items i WHERE i.id=s.id AND i.sid=s.sid)",new Object[]{gen,gen,run,sid});
            db.execSQL("UPDATE sources SET raw_count=?,unique_count=?,inferred=?,skipped=?,updated=? WHERE id=?",new Object[]{raw,unique,inferred,skipped,System.currentTimeMillis(),sid});
            db.execSQL("DELETE FROM staging_items WHERE run=?",new Object[]{run});
            db.execSQL("DELETE FROM groups WHERE sid=? AND NOT EXISTS(SELECT 1 FROM items i WHERE i.gid=groups.id)",new Object[]{sid});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return new JSONObject().put("raw",raw).put("unique",unique).put("inferred",inferred).put("skipped",skipped);
    }
    public void clearStaging(String run){getWritableDatabase().delete("staging_items","run=?",new String[]{run});}
    public void putItem(JSONObject row)throws Exception{
        SQLiteDatabase db=getWritableDatabase();String id=row.getString("id");long seen=row.optLong("seen",System.currentTimeMillis());
        ContentValues v=itemValues(row,row.getString("sid"),row.getLong("gen"),seen);db.insertOrThrow("items",null,v);
    }
    public void commitGeneration(String sid,long gen,long raw,long inferred,long skipped)throws Exception{
        SQLiteDatabase db=getWritableDatabase();long unique=DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM items WHERE sid=? AND gen=? AND parent=''",new String[]{sid,""+gen});
        if(unique==0)throw new Text.UserError("Kaynak bo\u015f d\u00f6nd\u00fc; mevcut liste korunuyor.");
        db.beginTransactionNonExclusive();try{
            db.execSQL("UPDATE sources SET raw_count=?,unique_count=?,inferred=?,skipped=?,updated=? WHERE id=?",new Object[]{raw,unique,inferred,skipped,System.currentTimeMillis(),sid});
            db.delete("items","sid=? AND gen<>?",new String[]{sid,""+gen});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void discardGeneration(String sid,long gen){getWritableDatabase().delete("items","sid=? AND gen=? AND gen<>(SELECT gen FROM sources WHERE id=?)",new String[]{sid,""+gen,sid});}
    private String access(){return " AND g.hidden=0"+(Vault.unlocked?"":" AND g.locked=0");}
    private static final String FROM=" FROM items i JOIN sources s ON i.sid=s.id AND i.gen=s.gen JOIN groups g ON g.id=i.gid LEFT JOIN history h ON h.id=i.id LEFT JOIN favorites f ON f.id=i.id ";
    private static final String COLS="i.*,g.name AS group_name,COALESCE(h.pos,0) AS pos,COALESCE(h.duration,0) AS duration,COALESCE(h.lastplayed,0) AS lastplayed,CASE WHEN f.id IS NULL THEN 0 ELSE 1 END AS favorite";
    private JSONObject row(Cursor c)throws Exception{
        JSONObject r=new JSONObject();for(String k:new String[]{"id","sid","kind","gid","title","plot","poster","parent"})r.put(k,str(c,k));
        r.put("group",str(c,"group_name")).put("cast",str(c,"cast_text"));for(String k:new String[]{"added","seen","seq","pos","duration","lastplayed"})r.put(k,num(c,k));r.put("favorite",num(c,"favorite")!=0);return r;
    }
    public JSONObject browse(JSONObject q)throws Exception{
        StringBuilder where=new StringBuilder(" WHERE i.parent='' ");List<String>args=new ArrayList<>();where.append(access());
        String kind=q.optString("kind","all");if(Arrays.asList("live","movies","series").contains(kind)){where.append(" AND i.kind=?");args.add(kind);}
        if(!q.optString("sid").isEmpty()){where.append(" AND i.sid=?");args.add(q.getString("sid"));}
        if(!q.optString("group").isEmpty()&&!q.optString("group").equals("all")){where.append(" AND i.gid=?");args.add(q.getString("group"));}
        String shelf=q.optString("shelf","all");if(shelf.equals("recent"))where.append(" AND h.lastplayed>0");if(shelf.equals("continue"))where.append(" AND h.pos>10000 AND (h.duration=0 OR h.pos<h.duration*0.95) AND i.kind<>'live'");
        if(kind.equals("favorites"))where.append(" AND f.id IS NOT NULL");
        String text=q.optString("q","").trim();if(!text.isEmpty()){
            String expr=Text.fts(text,kind.equals("all"));if(expr.isEmpty())where.append(" AND 0");else{where.append(" AND i.rowid IN(SELECT docid FROM item_search WHERE item_search MATCH ?)");args.add(expr);}
        }
        String order=switch(q.optString("sort","new")){case "old"->"CASE WHEN i.added>0 THEN i.added ELSE i.seen END ASC,i.seq ASC";case "az"->"i.title_key ASC,i.id ASC";case "za"->"i.title_key DESC,i.id ASC";default->"CASE WHEN i.added>0 THEN i.added ELSE i.seen END DESC,i.seq DESC";};
        if(shelf.equals("new"))order="CASE WHEN i.added>0 THEN i.added ELSE i.seen END DESC,i.seq DESC";
        if(shelf.equals("recent")||shelf.equals("continue"))order="h.lastplayed DESC";
        int limit=Math.max(1,Math.min(60,q.optInt("limit",48))),offset=Math.max(0,q.optInt("offset",0));
        long count=DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*)"+FROM+where,args.toArray(new String[0]));
        if(shelf.equals("new")){count=Math.min(50,count);limit=Math.min(limit,Math.max(0,50-offset));}
        JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+FROM+where+" ORDER BY "+order+" LIMIT "+limit+" OFFSET "+offset,args.toArray(new String[0]))){while(c.moveToNext())result.put(row(c));}
        return new JSONObject().put("items",result).put("total",count).put("next",offset+result.length()<count?offset+result.length():-1);
    }
    public JSONArray groups(JSONObject q)throws Exception{
        String where="WHERE EXISTS(SELECT 1 FROM items i JOIN sources s ON i.sid=s.id AND i.gen=s.gen WHERE i.gid=g.id AND i.parent='')";List<String>a=new ArrayList<>();
        if(!q.optString("kind").isEmpty()&&!q.optString("kind").equals("all")&&!q.optString("kind").equals("favorites")){where+=" AND kind=?";a.add(q.getString("kind"));}
        if(!q.optString("sid").isEmpty()){where+=" AND sid=?";a.add(q.getString("sid"));}
        JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM groups g "+where+" ORDER BY pinned DESC,position ASC,name",a.toArray(new String[0]))){while(c.moveToNext()){
            JSONObject r=new JSONObject();for(String k:new String[]{"id","sid","kind","name"})r.put(k,str(c,k));for(String k:new String[]{"locked","hidden","pinned"})r.put(k,num(c,k)!=0);r.put("position",num(c,"position"));out.put(r);
        }}return out;
    }
    public JSONObject item(String id,boolean secrets)throws Exception{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+FROM+" WHERE i.id=?"+access()+" LIMIT 1",new String[]{id})){
            if(!c.moveToFirst())throw new Text.UserError("\u0130\u00e7erik yok, gizli veya ebeveyn kilidi etkin.");JSONObject r=row(c).put("remote",str(c,"remote_id")).put("gen",num(c,"gen"));
            if(secrets)r.put("url",vault.decrypt(str(c,"url"))).put("extra",new JSONObject(vault.decrypt(str(c,"extra"))));return r;
        }
    }
    public JSONArray episodes(String parent)throws Exception{
        item(parent,false);JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+FROM+" WHERE i.parent=?"+access()+" ORDER BY i.seq LIMIT 2000",new String[]{parent})){while(c.moveToNext())a.put(row(c));}return a;
    }
    public void groupAction(JSONObject q)throws Exception{
        vault.requireAdmin();String id=q.getString("id"),action=q.getString("action");
        if(!Arrays.asList("locked","hidden","pinned").contains(action))throw new Text.UserError("Ge\u00e7ersiz klas\u00f6r i\u015flemi.");
        if(action.equals("locked")&&!vault.hasPin())throw new Text.UserError("\u00d6nce Ayarlar > Ebeveyn denetimi b\u00f6l\u00fcm\u00fcnden PIN belirleyin.");
        ContentValues v=new ContentValues();v.put(action,q.getBoolean("value")?1:0);getWritableDatabase().update("groups",v,"id=?",new String[]{id});
    }
    public void groupOrder(JSONArray ids)throws Exception{vault.requireAdmin();SQLiteDatabase db=getWritableDatabase();db.beginTransactionNonExclusive();try{for(int i=0;i<ids.length();i++)db.execSQL("UPDATE groups SET position=? WHERE id=?",new Object[]{i,ids.getString(i)});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public void favorite(String id)throws Exception{item(id,false);SQLiteDatabase db=getWritableDatabase();if(db.delete("favorites","id=?",new String[]{id})==0){ContentValues v=new ContentValues();v.put("id",id);db.insertOrThrow("favorites",null,v);}}
    public void history(String id,long pos,long duration)throws Exception{
        item(id,false);ContentValues v=new ContentValues();v.put("id",id);v.put("pos",Math.max(0,pos));v.put("duration",Math.max(0,duration));v.put("lastplayed",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("history",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        JSONObject media=item(id,false);if(!media.optString("parent").isEmpty()){v.put("id",media.getString("parent"));getWritableDatabase().insertWithOnConflict("history",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    }
    public void clearHistory()throws Exception{vault.requireAdmin();getWritableDatabase().delete("history",null,null);}
    public JSONObject stats()throws Exception{
        JSONObject o=new JSONObject();for(String k:new String[]{"live","movies","series"})o.put(k,DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*)"+FROM+" WHERE i.parent='' AND i.kind=?"+access(),new String[]{k}));return o;
    }
}
