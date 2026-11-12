package tr.lidertv.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.content.pm.ServiceInfo;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

public final class ImportService extends Service {
    public static final String ACTION_START = "tr.lidertv.app.action.IMPORT_START";
    public static final String ACTION_CANCEL = "tr.lidertv.app.action.IMPORT_CANCEL";
    public static final String EVENT = "tr.lidertv.app.IMPORT_EVENT";
    public static final String EXTRA_SPEC = "spec";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_ERROR = "error";

    private static final String CHANNEL_ID = "lidertv_import";
    private static final int NOTIFICATION_ID = 4703;
    public static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private volatile boolean running = false;
    private volatile Importer importer;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_CANCEL.equals(intent.getAction())) {
            Importer current = importer;
            if (current != null) current.cancelled = true;
            return START_NOT_STICKY;
        }

        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        if (running || !RUNNING.compareAndSet(false,true)) return START_NOT_STICKY;

        String spec = intent.getStringExtra(EXTRA_SPEC);
        if (spec == null || spec.isEmpty()) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification("Kaynak aktarımı başlatılıyor..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFICATION_ID, notification("Kaynak aktarımı başlatılıyor..."));
        } catch (Exception e) {
            RUNNING.set(false);
            throw e;
        }
        running = true;

        new Thread(() -> {
            try {
                Store db = new Store(getApplicationContext());
                JSONObject source = new JSONObject(spec);
                importer = new Importer(getApplicationContext(), db, count -> {
                    sendProgress(count);
                    updateNotification("Kaynak aktarılıyor: " + count + " kayıt");
                });

                sendEvent("started", new JSONObject());
                JSONObject result = importer.importSource(source);
                sendEvent("done", result);
                updateNotification("Kaynak aktarımı tamamlandı");
            } catch (Exception e) {
                try {
                    sendEvent("error", new JSONObject().put("message", Text.safeError(e)));
                } catch (Exception ignored) {
                }
                updateNotification("Kaynak aktarımı başarısız");
            } finally {
                running = false;
                RUNNING.set(false);
                importer = null;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelfResult(startId);
            }
        }, "LiderTv-ImportService").start();

        return START_NOT_STICKY;
    }

    private void sendProgress(long records) {
        try {
            JSONObject data = new JSONObject().put("records", records);
            sendEvent("progress", data);
        } catch (Exception ignored) {
        }
    }

    private void sendEvent(String type, JSONObject data) {
        Intent out = new Intent(EVENT);
        out.setPackage(getPackageName());
        out.putExtra(EXTRA_TYPE, type);
        out.putExtra(EXTRA_DATA, data.toString());
        sendBroadcast(out);
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 4704, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("LiderTv")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification(text));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    CHANNEL_ID,
                    "LiderTv kaynak güncellemeleri",
                    NotificationManager.IMPORTANCE_LOW
            );
            c.setDescription("M3U ve IPTV kaynak aktarımı");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    @Override
    public void onDestroy() {
        Importer current = importer;
        if (current != null) current.cancelled = true;
        running = false;
        RUNNING.set(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
