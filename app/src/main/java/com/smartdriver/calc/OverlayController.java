package com.smartdriver.calc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** Janela flutuante pequena e arrastavel. Somente exibe informacao. */
public class OverlayController {
    private final Context ctx;
    private final WindowManager wm;
    private View root;
    private TextView status, verdict, body, footer;
    private WindowManager.LayoutParams lp;
    private boolean minimized;
    /** area da janela na tela (usada para tampar a propria janela antes do OCR) */
    public volatile Rect screenRect = new Rect();

    public OverlayController(Context ctx) {
        this.ctx = ctx;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    public void show() {
        if (root != null) return;
        root = LayoutInflater.from(ctx).inflate(R.layout.overlay, null);
        status = root.findViewById(R.id.status);
        verdict = root.findViewById(R.id.verdict);
        body = root.findViewById(R.id.body);
        footer = root.findViewById(R.id.footer);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        float d = ctx.getResources().getDisplayMetrics().density;
        lp.x = (int) (8 * d);
        lp.y = (int) (90 * d);   // parte de cima: longe do botao Aceitar do Uber

        root.findViewById(R.id.btnEdit).setOnClickListener(v -> {
            Intent i = new Intent(ctx, ManualActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
        root.findViewById(R.id.btnMin).setOnClickListener(v -> setMinimized(!minimized));

        root.setOnTouchListener(new View.OnTouchListener() {
            int sx, sy; float tx, ty;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = lp.x; sy = lp.y; tx = e.getRawX(); ty = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = sx + (int) (e.getRawX() - tx);
                        lp.y = sy + (int) (e.getRawY() - ty);
                        wm.updateViewLayout(root, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        updateRect();
                        return true;
                }
                return false;
            }
        });
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateRect());
        wm.addView(root, lp);
    }

    private void updateRect() {
        if (root == null) return;
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        screenRect = new Rect(loc[0], loc[1], loc[0] + root.getWidth(), loc[1] + root.getHeight());
    }

    private void setMinimized(boolean m) {
        minimized = m;
        int vis = m ? View.GONE : View.VISIBLE;
        body.setVisibility(vis);
        footer.setVisibility(vis);
    }

    public void setStatus(String s) {
        if (status != null) status.setText(s);
    }

    public void showResult(Offer o, Calculator.Result r, long elapsedMs) {
        if (root == null) return;
        verdict.setText(Render.verdictLabel(r.verdict));
        verdict.setBackgroundTintList(ColorStateList.valueOf(Render.verdictColor(r.verdict)));
        verdict.setAlpha(1f);
        body.setText(Render.body(o, r));
        footer.setText(Render.footer(o, r, elapsedMs));
        status.setText(o.anyManual() ? "Oferta (com dados manuais)" : "Oferta detectada");
    }

    /** a oferta sumiu da tela: mantem o ultimo resultado, esmaecido */
    public void markStale() {
        if (verdict != null && Session.current != null) {
            verdict.setAlpha(0.45f);
            status.setText("Aguardando oferta… (acima: anterior)");
        }
    }

    public void hide() {
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        }
    }
}
