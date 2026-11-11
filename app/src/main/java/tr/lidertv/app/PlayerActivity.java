package tr.lidertv.app;
import android.app.*;import android.os.*;import android.content.*;import android.content.res.Configuration;import android.graphics.Color;import android.net.Uri;import android.view.*;import android.widget.*;
import androidx.media3.common.*;import androidx.media3.exoplayer.*;import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;import androidx.media3.datasource.*;import androidx.media3.ui.PlayerView;
import org.videolan.libvlc.LibVLC;import org.videolan.libvlc.Media;import org.videolan.libvlc.util.VLCVideoLayout;
import org.json.*;import java.util.*;import java.util.concurrent.*;
/** Real native players, bounded failover. No DRM/certificate/access-control bypass. */
@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private Store db;private JSONObject item,settings;private FrameLayout root,video;private TextView info;private SeekBar seek;private Button pause;
    private ExoPlayer exo;private PlayerView exoView;private LibVLC lib;private org.videolan.libvlc.MediaPlayer vlc;private VideoView platform;
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<String> attempts=new ArrayList<>(),errors=new ArrayList<>();private int attempt=-1,epoch=0;private long position=0,duration=0;private boolean resumed=false,stopped=false,userSeeking=false;
    private final Runnable pulse=new Runnable(){public void run(){if(stopped)return;long p=currentPosition(),d=currentDuration();if(p>0)position=p;if(d>0)duration=d;if(!userSeeking&&duration>0)seek.setProgress((int)Math.min(1000,position*1000/duration));save();main.postDelayed(this,5000);}};
    @Override protected void attachBaseContext(Context base){Configuration c=new Configuration(base.getResources().getConfiguration());c.setLocale(Locale.forLanguageTag("tr"));super.attachBaseContext(base.createConfigurationContext(c));}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);db=new Store(this);
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);video=new FrameLayout(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(16,8,16,12);controls.setBackgroundColor(0xc9111420);
        info=new TextView(this);info.setTextColor(Color.WHITE);info.setTextSize(15);controls.addView(info);
        seek=new SeekBar(this);seek.setMax(1000);controls.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&duration>0)seekTo(duration*p/1000);}public void onStartTrackingTouch(SeekBar s){userSeeking=true;}public void onStopTrackingTouch(SeekBar s){userSeeking=false;}});
        LinearLayout buttons=new LinearLayout(this);controls.addView(buttons);add(buttons,"Geri",()->finish());add(buttons,"-10 sn",()->seekTo(Math.max(0,currentPosition()-10000)));pause=add(buttons,"Duraklat",this::toggle);add(buttons,"+10 sn",()->seekTo(currentPosition()+10000));add(buttons,"Oynat\u0131c\u0131",this::chooseEngine);add(buttons,"Harici",this::external);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(controls,lp);setContentView(root);
        io.execute(()->{try{item=db.item(getIntent().getStringExtra("id"),true);settings=db.settings();position=getIntent().getBooleanExtra("restart",false)?0:item.optLong("pos");duration=item.optLong("duration");if(duration>0&&position>=duration*0.95)position=0;
            main.post(()->{configure();nextEngine(null);main.post(pulse);int minutes=settings.optInt("sleep",0);if(minutes>0)main.postDelayed(()->{Toast.makeText(this,"Uyku zamanlay\u0131c\u0131s\u0131 doldu.",Toast.LENGTH_LONG).show();finish();},minutes*60000L);});
        }catch(Exception e){main.post(()->new AlertDialog.Builder(this).setTitle("Yay\u0131n a\u00e7\u0131lamad\u0131").setMessage(Text.safeError(e)).setPositiveButton("Kapat",(d,w)->finish()).setOnCancelListener(d->finish()).show());}});
    }
    private Button add(LinearLayout p,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(12);b.setAllCaps(false);b.setOnClickListener(v->action.run());p.addView(b,new LinearLayout.LayoutParams(0,-2,1));return b;}
    private void configure(){attempts.clear();attempt=-1;String engine=settings.optString("engine","auto");if(engine.equals("external")){attempts.add("external");return;}
        attempts.add(engine.equals("auto")?"exo":engine);if(settings.optBoolean("fallback",true)||engine.equals("auto"))for(String k:new String[]{"exo","vlc","vlc_sw","platform"})if(!attempts.contains(k))attempts.add(k);
    }
    private String label(String engine){return switch(engine){case "vlc"->"VLC / donan\u0131m";case "vlc_sw"->"VLC / yaz\u0131l\u0131m";case "platform"->"Android MediaPlayer";case "external"->"Harici oynat\u0131c\u0131";default->"Media3 ExoPlayer";};}
    private void nextEngine(String error){if(stopped)return;if(error!=null)errors.add(error);long p=currentPosition();if(p>0)position=p;release();attempt++;
        if(attempt>=attempts.size()){showFailure();return;}String type=attempts.get(attempt);int token=++epoch;
        info.setText(item.optString("title")+"  \u00b7  "+label(type)+(error==null?"":"\n"+error+" Alternatif deneniyor."));
        seek.setVisibility(item.optString("kind").equals("live")?View.GONE:View.VISIBLE);resumed=false;
        try{
            String url=item.getString("url");if(!M3uParser.validUrl(url))throw new Text.UserError("Oynat\u0131labilir yay\u0131n adresi bulunamad\u0131.");JSONObject headers=item.optJSONObject("extra");if(headers==null)headers=new JSONObject();
            if(type.equals("external")){external();return;}
            if(type.equals("exo")){
                Map<String,String> requestHeaders=new HashMap<>();if(!headers.optString("referer").isEmpty())requestHeaders.put("Referer",headers.optString("referer"));
                DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(headers.optString("userAgent","LiderTv/0.1.0")).setConnectTimeoutMs(15000).setReadTimeoutMs(25000).setDefaultRequestProperties(requestHeaders);
                DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
                exo=new ExoPlayer.Builder(this,new DefaultRenderersFactory(this).setEnableDecoderFallback(true)).setMediaSourceFactory(new DefaultMediaSourceFactory(data)).build();
                exoView=new PlayerView(this);exoView.setUseController(true);exoView.setPlayer(exo);video.addView(exoView,new FrameLayout.LayoutParams(-1,-1));
                exo.addListener(new Player.Listener(){
                    @Override public void onPlaybackStateChanged(int state){if(token!=epoch||stopped)return;if(state==Player.STATE_READY){resumed=true;info.setText(item.optString("title")+" \u00b7 "+label(type));}if(state==Player.STATE_ENDED){position=Math.max(duration,currentDuration());save();}}
                    @Override public void onPlayerError(PlaybackException e){if(token!=epoch||stopped)return;String message="Yay\u0131n bu oynat\u0131c\u0131yla a\u00e7\u0131lamad\u0131. Kod: "+e.getErrorCodeName();boolean denied=false;
                        for(Throwable c=e;c!=null;c=c.getCause())if(c instanceof HttpDataSource.InvalidResponseCodeException h){message="Sunucu HTTP "+h.responseCode+" yan\u0131t\u0131 verdi.";if(h.responseCode==401||h.responseCode==403||h.responseCode==404)denied=true;}
                        if(denied){errors.add(message+" Oynat\u0131c\u0131 de\u011fi\u015ftirmek hesap yetkisi veya bulunamayan adres sorununu gidermez.");release();showFailure();}else nextEngine(message);
                    }
                });exo.setMediaItem(MediaItem.fromUri(url));if(position>0&&!item.optString("kind").equals("live"))exo.seekTo(position);exo.prepare();exo.play();
            }else if(type.equals("platform")){
                platform=new VideoView(this);video.addView(platform,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));Map<String,String> h=new HashMap<>();if(!headers.optString("referer").isEmpty())h.put("Referer",headers.optString("referer"));if(!headers.optString("userAgent").isEmpty())h.put("User-Agent",headers.optString("userAgent"));
                platform.setOnPreparedListener(ply->{if(token!=epoch)return;resumed=true;if(position>0)platform.seekTo((int)Math.min(Integer.MAX_VALUE,position));platform.start();});platform.setOnErrorListener((p1,what,extra)->{if(token==epoch)nextEngine("Android MediaPlayer hata kodu: "+what+"/"+extra);return true;});platform.setVideoURI(Uri.parse(url),h);
            }else{
                ArrayList<String> options=new ArrayList<>(Arrays.asList("--quiet","--network-caching=1500","--no-video-title-show"));lib=new LibVLC(this,options);vlc=new org.videolan.libvlc.MediaPlayer(lib);
                VLCVideoLayout layout=new VLCVideoLayout(this);video.addView(layout,new FrameLayout.LayoutParams(-1,-1));vlc.attachViews(layout,null,false,false);
                Media media=new Media(lib,Uri.parse(url));media.setHWDecoderEnabled(!type.equals("vlc_sw"),false);if(!headers.optString("referer").isEmpty())media.addOption(":http-referrer="+headers.optString("referer"));if(!headers.optString("userAgent").isEmpty())media.addOption(":http-user-agent="+headers.optString("userAgent"));
                vlc.setEventListener(e->main.post(()->{if(token!=epoch||stopped||vlc==null)return;if(e.type==org.videolan.libvlc.MediaPlayer.Event.Playing&&!resumed){resumed=true;if(position>0&&!item.optString("kind").equals("live"))vlc.setTime(position);info.setText(item.optString("title")+" \u00b7 "+label(type));}
                    if(e.type==org.videolan.libvlc.MediaPlayer.Event.EncounteredError)nextEngine("VLC yay\u0131n\u0131 a\u00e7amad\u0131; ayr\u0131nt\u0131l\u0131 bir hata kodu sa\u011flamad\u0131.");if(e.type==org.videolan.libvlc.MediaPlayer.Event.EndReached){position=duration;save();}}));vlc.setMedia(media);media.release();vlc.play();
            }
            main.postDelayed(()->{if(token==epoch&&!stopped&&!resumed)nextEngine("Yay\u0131n 25 saniyede ba\u015flamad\u0131.");},25000);
        }catch(Exception|LinkageError e){nextEngine(e instanceof Exception?Text.safeError((Exception)e):"Bu cihaz i\u00e7in oynat\u0131c\u0131 bile\u015feni y\u00fcklenemedi.");}
    }
    private void chooseEngine(){new AlertDialog.Builder(this).setTitle("Oynat\u0131c\u0131 se\u00e7").setItems(new String[]{"Media3 ExoPlayer","VLC / donan\u0131m","VLC / yaz\u0131l\u0131m","Android MediaPlayer"},(d,w)->{String k=new String[]{"exo","vlc","vlc_sw","platform"}[w];attempts.clear();attempts.add(k);attempt=-1;nextEngine(null);}).show();}
    private void showFailure(){info.setText("Yay\u0131n a\u00e7\u0131lamad\u0131");new AlertDialog.Builder(this).setTitle("Yay\u0131n a\u00e7\u0131lamad\u0131").setMessage(String.join("\n\n",errors)+"\n\nYay\u0131n\u0131n kapat\u0131lmas\u0131, hesap yetkisi, DRM veya sunucu sorunu olabilir. Kesin neden sa\u011flanmam\u0131\u015f olabilir.").setPositiveButton("Tekrar dene",(d,w)->{errors.clear();configure();nextEngine(null);}).setNeutralButton("Harici oynat\u0131c\u0131",(d,w)->external()).setNegativeButton("Geri",(d,w)->finish()).show();}
    private void external(){if(item==null)return;new AlertDialog.Builder(this).setTitle("Harici oynat\u0131c\u0131").setMessage("Yay\u0131n adresi, varsa eri\u015fim bilgileriyle se\u00e7ti\u011finiz uygulamaya iletilir. Harici oynat\u0131c\u0131dan izleme konumu otomatik al\u0131namaz.").setPositiveButton("Devam",(d,w)->{try{save();release();Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.optString("url")),"video/*");startActivity(Intent.createChooser(i,"Oynat\u0131c\u0131 se\u00e7"));}catch(ActivityNotFoundException e){Toast.makeText(this,"Uygun harici oynat\u0131c\u0131 y\u00fckl\u00fc de\u011fil.",Toast.LENGTH_LONG).show();}}).setNegativeButton("Vazge\u00e7",null).show();}
    private long currentPosition(){try{if(exo!=null)return exo.getCurrentPosition();if(vlc!=null)return vlc.getTime();if(platform!=null)return platform.getCurrentPosition();}catch(Exception ignored){}return position;}
    private long currentDuration(){try{if(exo!=null)return exo.getDuration();if(vlc!=null)return vlc.getLength();if(platform!=null)return platform.getDuration();}catch(Exception ignored){}return duration;}
    private void seekTo(long p){position=Math.max(0,p);if(exo!=null)exo.seekTo(position);if(vlc!=null)vlc.setTime(position);if(platform!=null)platform.seekTo((int)Math.min(position,Integer.MAX_VALUE));}
    private void toggle(){boolean playing=false;if(exo!=null){playing=exo.isPlaying();exo.setPlayWhenReady(!playing);}if(vlc!=null){playing=vlc.isPlaying();if(playing)vlc.pause();else vlc.play();}if(platform!=null){playing=platform.isPlaying();if(playing)platform.pause();else platform.start();}pause.setText(playing?"Oynat":"Duraklat");}
    private void save(){if(item==null||!resumed||io.isShutdown())return;String id=item.optString("id");long p=item.optString("kind").equals("live")?0:position,d=duration;io.execute(()->{try{db.history(id,p,d);}catch(Exception ignored){}});}
    private void release(){epoch++;if(exoView!=null){exoView.setPlayer(null);exoView=null;}if(exo!=null){exo.release();exo=null;}if(vlc!=null){vlc.setEventListener(null);vlc.stop();vlc.detachViews();vlc.release();vlc=null;}if(lib!=null){lib.release();lib=null;}if(platform!=null){platform.stopPlayback();platform=null;}if(video!=null)video.removeAllViews();}
    @Override protected void onPause(){super.onPause();position=currentPosition();long d=currentDuration();if(d>0)duration=d;save();if(exo!=null)exo.pause();if(vlc!=null)vlc.pause();if(platform!=null)platform.pause();}
    @Override protected void onStop(){super.onStop();stopped=true;main.removeCallbacksAndMessages(null);release();io.execute(()->Vault.unlocked=false);}
    @Override protected void onRestart(){super.onRestart();finish();}
    @Override protected void onDestroy(){stopped=true;main.removeCallbacksAndMessages(null);release();io.shutdown();super.onDestroy();}
}
