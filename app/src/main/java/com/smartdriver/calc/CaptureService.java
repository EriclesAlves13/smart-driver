package com.smartdriver.calc;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Captura de tela AUTORIZADA pelo usuario (MediaProjection) + OCR no aparelho.
 * Apenas le pixels. Nao toca, nao clica e nao interage com o app do Uber.
 */
public class CaptureService extends Service {
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_DATA = "data";
    public static final String ACTION_STOP = "stop";
    private static final String CHANNEL = "captura";
    private static final long FRAME_INTERVAL_MS = 700;   // ~1,4 leituras/s
    private static final int MAX_WIDTH = 720;            // reduz a imagem para o OCR ficar rapido

    public static volatile boolean running;
    /** pausado enquanto as telas do proprio app estao abertas */
    public static volatile boolean paused;
    private static CaptureService instance;

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextRecognizer ocr;
    private OverlayController overlay;

    private volatile boolean busy;
    private long lastFrame;
    private long lastOfferSeen;
    private int blackFrames;
    private float scale = 1f;

    public static CaptureService get() { return instance; }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;

        startAsForeground();

        int code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(code, data);
        if (projection == null) { stopSelf(); return START_NOT_STICKY; }

        instance = this;
        running = true;
        thread = new HandlerThread("captura");
        thread.start();
        bg = new Handler(thread.getLooper());
        ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        overlay = new OverlayController(this);
        overlay.show();
        overlay.setStatus("Lendo a tela… aguardando oferta", false);

        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { main.post(CaptureService.this::stopSelf); }
        }, main);

        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        scale = Math.min(1f, MAX_WIDTH / (float) dm.widthPixels);
        int w = Math.round(dm.widthPixels * scale);
        int h = Math.round(dm.heightPixels * scale);

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(this::onFrame, bg);
        display = projection.createVirtualDisplay("smartdriver", w, h, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, bg);
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Leitura da tela",
                    NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, CaptureService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Smart Driver lendo a tela")
                .setContentText("Somente calcula. Você decide no Uber.")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Parar", stop).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, n);
        }
    }

    private void onFrame(ImageReader r) {
        Image img = r.acquireLatestImage();
        if (img == null) return;
        long now = SystemClock.elapsedRealtime();
        if (busy || paused || now - lastFrame < FRAME_INTERVAL_MS) { img.close(); return; }
        lastFrame = now;
        busy = true;

        Bitmap bmp;
        try {
            bmp = toBitmap(img);
        } finally {
            img.close();
        }

        if (isBlack(bmp)) {
            bmp.recycle();
            busy = false;
            if (++blackFrames == 3) {
                main.post(() -> overlay.setStatus(
                        "Tela preta: captura bloqueada ou tela desligada. Use ✎ Editar.", true));
            }
            return;
        }
        blackFrames = 0;
        maskOverlay(bmp);

        final long capturedAt = now;
        ocr.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(text -> handleText(text, capturedAt))
                .addOnCompleteListener(t -> { bmp.recycle(); busy = false; });
    }

    private void handleText(Text text, long capturedAt) {
        String joined = joinLines(text);
        Rules rules = Prefs.load(this);
        Offer o = OfferParser.parse(joined, rules);
        long now = SystemClock.elapsedRealtime();
        if (o == null) {
            if (lastOfferSeen > 0 && now - lastOfferSeen > 3000) overlay.markStale();
            return;
        }
        lastOfferSeen = now;
        String sig = o.signature();
        if (sig.equals(Session.lastAutoSig)) return;   // mesma oferta (ou ja editada pelo motorista)
        Session.lastAutoSig = sig;
        o.capturedAt = capturedAt;
        Session.current = o;
        Calculator.Result res = Calculator.evaluate(o, rules);
        overlay.showResult(o, res, now - capturedAt);
    }

    /** chamado pela tela manual apos o motorista corrigir os dados */
    public void showManual(Offer o) {
        main.post(() -> {
            if (overlay != null) overlay.showResult(o, Calculator.evaluate(o, Prefs.load(this)), -1);
        });
    }

    private static String joinLines(Text t) {
        List<Text.Line> lines = new ArrayList<>();
        for (Text.TextBlock b : t.getTextBlocks()) lines.addAll(b.getLines());
        lines.sort((a, b) -> {
            Rect ra = a.getBoundingBox(), rb = b.getBoundingBox();
            int ta = ra == null ? 0 : ra.top, tb = rb == null ? 0 : rb.top;
            if (Math.abs(ta - tb) > 8) return Integer.compare(ta, tb);
            return Integer.compare(ra == null ? 0 : ra.left, rb == null ? 0 : rb.left);
        });
        StringBuilder sb = new StringBuilder();
        for (Text.Line l : lines) sb.append(l.getText()).append('\n');
        return sb.toString();
    }

    private static Bitmap toBitmap(Image img) {
        Image.Plane p = img.getPlanes()[0];
        ByteBuffer buf = p.getBuffer();
        int pixelStride = p.getPixelStride();
        int rowPadding = p.getRowStride() - pixelStride * img.getWidth();
        Bitmap full = Bitmap.createBitmap(img.getWidth() + rowPadding / pixelStride,
                img.getHeight(), Bitmap.Config.ARGB_8888);
        full.copyPixelsFromBuffer(buf);
        if (rowPadding == 0) return full;
        Bitmap crop = Bitmap.createBitmap(img.getWidth(), img.getHeight(), Bitmap.Config.ARGB_8888);
        new Canvas(crop).drawBitmap(full, 0, 0, null);   // mutavel (a mascara desenha nele)
        full.recycle();
        return crop;
    }

    private static boolean isBlack(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        for (int y = 1; y < 20; y++) {
            for (int x = 1; x < 20; x++) {
                int c = b.getPixel(x * w / 20, y * h / 20);
                if (Color.red(c) + Color.green(c) + Color.blue(c) > 30) return false;
            }
        }
        return true;
    }

    /** tampa a propria janela flutuante para o OCR nao ler os nossos numeros */
    private void maskOverlay(Bitmap b) {
        Rect r = overlay.screenRect;
        if (r == null || r.isEmpty()) return;
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        new Canvas(b).drawRect((r.left - 6) * scale, (r.top - 6) * scale,
                (r.right + 6) * scale, (r.bottom + 6) * scale, p);
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        if (display != null) display.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();
        if (ocr != null) ocr.close();
        if (overlay != null) overlay.hide();
        if (thread != null) thread.quitSafely();
        Session.lastAutoSig = null;
        super.onDestroy();
    }

    public static void stop(Context c) {
        c.startService(new Intent(c, CaptureService.class).setAction(ACTION_STOP));
    }
}
