package jp.ryu.foldarnav;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.view.WindowManager;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final String CHANNEL = "ar_nav_capture";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread worker;
    private Handler handler;
    private long lastAnalyzeMs = 0L;

    @Override public void onCreate() {
        super.onCreate();
        worker = new HandlerThread("MapsCapture");
        worker.start();
        handler = new Handler(worker.getLooper());
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "ARナビ画面解析", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("Fold AR Nav")
                .setContentText("Googleマップ画面を解析中")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(42, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(42, n);
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startProjection(resultCode, data);
        return START_STICKY;
    }

    private void startProjection(int resultCode, Intent data) {
        cleanupProjection();
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        projection = mpm.getMediaProjection(resultCode, data);
        if (projection == null) { stopSelf(); return; }

        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                handler.post(() -> cleanupProjection());
            }
        }, handler);

        WindowManager wm = getSystemService(WindowManager.class);
        android.graphics.Rect bounds = wm.getMaximumWindowMetrics().getBounds();
        int srcW = Math.max(640, bounds.width());
        int srcH = Math.max(640, bounds.height());
        float scale = Math.min(1f, 1100f / Math.max(srcW, srcH));
        int w = Math.max(480, Math.round(srcW * scale));
        int h = Math.max(480, Math.round(srcH * scale));
        int dpi = getResources().getDisplayMetrics().densityDpi;

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> {
            Image image = null;
            try {
                image = r.acquireLatestImage();
                if (image == null) return;
                long now = SystemClock.elapsedRealtime();
                if (now - lastAnalyzeMs < 280) return;
                lastAnalyzeMs = now;
                Image.Plane plane = image.getPlanes()[0];
                ByteBuffer buf = plane.getBuffer();
                BlueRouteAnalyzer.analyze(
                        buf, image.getWidth(), image.getHeight(),
                        plane.getRowStride(), plane.getPixelStride());
            } finally {
                if (image != null) image.close();
            }
        }, handler);

        display = projection.createVirtualDisplay(
                "GoogleMapsAnalyzer", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, handler);
    }

    private void cleanupProjection() {
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { projection.stop(); projection = null; }
    }

    @Override public void onDestroy() {
        cleanupProjection();
        if (worker != null) worker.quitSafely();
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
