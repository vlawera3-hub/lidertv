package tr.lidertv.app;
import android.app.*;import android.os.*;import android.content.*;import android.content.pm.PackageManager;import android.content.res.Configuration;import android.net.Uri;import android.view.*;import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import org.json.*;
import java.util.*;import java.util.concurrent.*;
public final class MainActivity extends Activity {
    private WebView web;private Store db;private Importer importer;private final ExecutorService work=Executors.newFixedThreadPool(2);private boolean openingPlayer=false,destroyed=false;
    private final BroadcastReceiver importReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){String type=i.getStringExtra(ImportService.EXTRA_TYPE);String raw=i.getStringExtra(ImportService.EXTRA_DATA);try{JSONObject data=raw==null?new JSONObject():new JSONObject(raw);if("progress".equals(type))event("importProgress",data);else if("started".equals(type))event("importStarted",data);else if("done".equals(type))event("importDone",data);else if("error".equals(type))event("error",data);}catch(Exception e){event("error",new JSONObjectSafe().put("message",Text.safeError(e)).value);}}};
    private static final String HOME="https://appassets.androidplatform.net/assets/ui/index.html";
    @Override protected void attachBaseContext(Context base){Configuration c=new Configuration(base.getResources().getConfiguration());c.setLocale(Locale.forLanguageTag("tr"));super.attachBaseContext(base.createConfigurationContext(c));}
    @Override public void onCreate(Bundle state){super.onCreate(state);db=new Store(this);web=new WebView(this);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);s.setMediaPlaybackRequiresUserGesture(true);
        WebViewAssetLoader assets=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return assets.shouldInterceptRequest(r.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !r.getUrl().toString().equals(HOME);}
            @Override public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){h.cancel();}
        });
        web.setBackgroundColor(0xff080b14);web.addJavascriptInterface(new Bridge(),"LiderNative");setContentView(web);
        IntentFilter f=new IntentFilter(ImportService.EVENT);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(importReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(importReceiver,f);
        importer=new Importer(this,db,n->event("importProgress",new JSONObjectSafe().put("records",n).value));web.loadUrl(HOME);
    }
    private void js(String script){runOnUiThread(()->{if(!destroyed&&web!=null)web.evaluateJavascript(script,null);});}
    private void event(String name,JSONObject data){js("window.Lider && Lider.event("+JSONObject.quote(name)+","+data.toString()+")");}
    private JSONObject boot()throws Exception{
        UiModeManager mode=(UiModeManager)getSystemService(UI_MODE_SERVICE);boolean tv=mode.getCurrentModeType()==Configuration.UI_MODE_TYPE_TELEVISION||!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        return new JSONObject().put("settings",db.settings()).put("sources",db.sources()).put("stats",db.stats()).put("hasPin",db.vault.hasPin()).put("unlocked",Vault.unlocked).put("tv",tv).put("version",BuildConfig.VERSION_NAME).put("native",true);
    }
    public final class Bridge {
        @JavascriptInterface public void request(String id,String method,String payload){
            if(payload.length()>1000000){reply(id,null,"\u0130stek \u00e7ok b\u00fcy\u00fck.");return;}
            work.execute(()->{try{JSONObject q=new JSONObject(payload);Object result=switch(method){
                case "boot" -> boot();case "browse" -> db.browse(q);case "groups" -> db.groups(q);
                case "details" -> {try{yield importer.details(q.getString("id"));}catch(Exception e){JSONObject cached=db.item(q.getString("id"),false);cached.put("episodes",db.episodes(q.getString("id"))).put("metadataError",Text.safeError(e));yield cached;}}
                case "favorite" -> {db.favorite(q.getString("id"));yield new JSONObject();}
                case "settings" -> {db.saveSettings(q);yield boot();}
                case "groupAction" -> {db.groupAction(q);yield new JSONObject();}
                case "groupOrder" -> {db.groupOrder(q.getJSONArray("ids"));yield new JSONObject();}
                case "setPin" -> {db.vault.setPin(q.getString("pin"));yield boot();}
                case "unlock" -> {db.vault.unlock(q.getString("pin"));yield boot();}
                case "lock" -> {Vault.unlocked=false;yield boot();}
                case "clearHistory" -> {db.clearHistory();yield new JSONObject();}
                case "deleteSource" -> {if(importer.busy.get())throw new Text.UserError("Aktar\u0131m tamamlanmadan kaynak silinemez.");db.removeSource(q.getString("id"));yield boot();}
                case "scanSource" -> importer.scanSource(q);
                case "saveSourceGroups" -> {importer.saveSourceGroups(q.getString("id"),q.optJSONArray("selectedGroups"));yield new JSONObject();}
                case "import" -> {startBackgroundImport(q);yield new JSONObject().put("background",true);}
                case "cancelImport" -> {importer.cancelled=true;Intent cancel=new Intent(this,ImportService.class).setAction(ImportService.ACTION_CANCEL);startService(cancel);yield new JSONObject();}
                case "file" -> {db.vault.requireAdmin();runOnUiThread(()->{
                    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    try{openingPlayer=true;startActivityForResult(intent,12);}catch(ActivityNotFoundException e){openingPlayer=false;event("error",new JSONObjectSafe().put("message","Cihazda dosya se\u00e7ici yok. M3U adresi kullan\u0131n.").value);}
                });yield new JSONObject().put("pending",true);}
                case "play" -> {play(q);yield new JSONObject();}
                default -> throw new Text.UserError("Bilinmeyen i\u015flem.");
            };reply(id,result,null);}catch(Exception e){reply(id,null,Text.safeError(e));}});
        }
    }
    private void startBackgroundImport(JSONObject q)throws Exception{
        db.vault.requireAdmin();
        if(ImportService.RUNNING.get())throw new Text.UserError("Başka bir kaynak aktarılıyor.");
        Intent s=new Intent(this,ImportService.class);s.setAction(ImportService.ACTION_START);s.putExtra(ImportService.EXTRA_SPEC,q.toString());
        if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);
    }
    private void play(JSONObject q)throws Exception{
        JSONObject item=db.item(q.getString("id"),true);
        if(item.getString("kind").equals("series")&&item.optString("url").isEmpty()){
            JSONArray episodes=importer.details(item.getString("id")).getJSONArray("episodes");if(episodes.length()==0)throw new Text.UserError("Bu dizi i\u00e7in b\u00f6l\u00fcm listesi bulunamad\u0131.");
            JSONObject selected=episodes.getJSONObject(0);long last=0;int selectedIndex=0;
            for(int i=0;i<episodes.length();i++){JSONObject ep=episodes.getJSONObject(i);if(ep.optLong("lastplayed")>last){last=ep.optLong("lastplayed");selected=ep;selectedIndex=i;}}
            if(selected.optLong("duration")>0&&selected.optLong("pos")>=selected.optLong("duration")*0.95&&selectedIndex+1<episodes.length())selected=episodes.getJSONObject(selectedIndex+1);
            item=db.item(selected.getString("id"),true);
        }
        String id=item.getString("id");boolean restart=q.optBoolean("restart");runOnUiThread(()->{openingPlayer=true;startActivity(new Intent(this,PlayerActivity.class).putExtra("id",id).putExtra("restart",restart));});
    }
    private void reply(String id,Object value,String error){String r=value==null?"null":value.toString();js("window.LiderReply && LiderReply("+JSONObject.quote(id)+","+r+","+(error==null?"null":JSONObject.quote(error))+")");}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==12&&result==RESULT_OK&&data!=null&&data.getData()!=null){
        Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        work.execute(()->{try{JSONObject spec=new JSONObject().put("type","file").put("name","Yerel M3U listesi").put("url",uri.toString());JSONObject scan=importer.scanSource(spec);event("fileScan",new JSONObjectSafe().put("spec",spec).put("scan",scan).value);}catch(Exception e){event("error",new JSONObjectSafe().put("message",Text.safeError(e)).value);}});
    }}
    @Override protected void onResume(){super.onResume();js("window.Lider && Lider.refresh()");}
    @Override protected void onStop(){super.onStop();if(!openingPlayer)Vault.unlocked=false;openingPlayer=false;}
    @Override public void onBackPressed(){if(web==null){super.onBackPressed();return;}web.evaluateJavascript("window.Lider ? Lider.back() : false",result->{if(!"true".equals(result))finish();});}
    @Override public boolean dispatchKeyEvent(KeyEvent e){int k=e.getKeyCode();if(web!=null&&(k==19||k==20||k==21||k==22||k==23||k==66||k==82||k==84||k==92||k==93)){
        js("window.Lider && Lider.key("+k+","+e.getAction()+","+e.getRepeatCount()+")");return true;
    }return super.dispatchKeyEvent(e);}
    @Override protected void onDestroy(){destroyed=true;importer.cancelled=true;try{unregisterReceiver(importReceiver);}catch(Exception ignored){}work.shutdownNow();if(web!=null){web.removeJavascriptInterface("LiderNative");web.destroy();web=null;}super.onDestroy();}
    private static final class JSONObjectSafe{final JSONObject value=new JSONObject();JSONObjectSafe put(String k,Object v){try{value.put(k,v);}catch(Exception ignored){}return this;}}
}
