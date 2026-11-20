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
import android.widget.ImageView;
import android.widget.TextView;

/** Janela flutuante pequena e arrastavel. Somente exibe informacao. */
public class OverlayController {
    private final Context ctx;
    private final WindowManager wm;
    private View root, dot, verdictBox, warnBox, destButtons, btnComplete, details, destRow;
    private View tProfit;
    private ImageView verdictIcon, destIcon;
    private TextView status, verdict, reasons, warnText, warnAddress, perKm, perKmHint,
            vPrice, vDist, vDistSub, vFuel, vProfit, lProfit, destText, destTag, footer;
    private WindowManager.LayoutParams lp;
    private boolean minimized, hasOffer;
    private boolean showReasons, showWarn;
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
        dot = root.findViewById(R.id.dot);
        status = root.findViewById(R.id.status);
        verdictBox = root.findViewById(R.id.verdictBox);
        verdictIcon = root.findViewById(R.id.verdictIcon);
        verdict = root.findViewById(R.id.verdict);
        reasons = root.findViewById(R.id.reasons);
        warnBox = root.findViewById(R.id.warnBox);
        warnText = root.findViewById(R.id.warnText);
        warnAddress = root.findViewById(R.id.warnAddress);
        destButtons = root.findViewById(R.id.destButtons);
        btnComplete = root.findViewById(R.id.btnComplete);
        details = root.findViewById(R.id.details);
        perKm = root.findViewById(R.id.perKm);
        perKmHint = root.findViewById(R.id.perKmHint);
        vPrice = root.findViewById(R.id.vPrice);
        vDist = root.findViewById(R.id.vDist);
        vDistSub = root.findViewById(R.id.vDistSub);
        vFuel = root.findViewById(R.id.vFuel);
        vProfit = root.findViewById(R.id.vProfit);
        lProfit = root.findViewById(R.id.lProfit);
        tProfit = root.findViewById(R.id.tProfit);
        destRow = root.findViewById(R.id.destRow);
        destIcon = root.findViewById(R.id.destIcon);
        destText = root.findViewById(R.id.destText);
        destTag = root.findViewById(R.id.destTag);
        footer = root.findViewById(R.id.footer);
        applyVisibility();

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
        lp.x = (int) (12 * d);
        lp.y = (int) (64 * d);   // parte de cima: longe dos botoes do Uber

        root.findViewById(R.id.btnEdit).setOnClickListener(v -> openManual());
        btnComplete.setOnClickListener(v -> openManual());
        root.findViewById(R.id.btnMin).setOnClickListener(v -> { minimized = !minimized; applyVisibility(); });
        root.findViewById(R.id.btnInside).setOnClickListener(v -> setDestination(Offer.Destination.INSIDE));
        root.findViewById(R.id.btnOutside).setOnClickListener(v -> setDestination(Offer.Destination.OUTSIDE));

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

    private void openManual() {
        Intent i = new Intent(ctx, ManualActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    /** botoes "E na cidade" / "E fora": o motorista confirma o destino com um toque */
    private void setDestination(Offer.Destination d) {
        Offer cur = Session.current;
        if (cur == null) return;
        Offer o = cur.copy();
        o.destination = d;
        o.destSrc = Offer.Source.MANUAL;
        o.matchedPlace = null;
        Session.current = o;
        showResult(o, Calculator.evaluate(o, Prefs.load(ctx)), -1);
    }

    private void updateRect() {
        if (root == null) return;
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        screenRect = new Rect(loc[0], loc[1], loc[0] + root.getWidth(), loc[1] + root.getHeight());
    }

    private void applyVisibility() {
        boolean full = hasOffer && !minimized;
        details.setVisibility(full ? View.VISIBLE : View.GONE);
        reasons.setVisibility(full && showReasons ? View.VISIBLE : View.GONE);
        warnBox.setVisibility(full && showWarn ? View.VISIBLE : View.GONE);
    }

    public void setStatus(String s, boolean warn) {
        if (status == null) return;
        status.setText(s);
        dot.setBackgroundTintList(ColorStateList.valueOf(warn ? Render.AMBER : Render.GREEN));
    }

    public void showResult(Offer o, Calculator.Result r, long elapsedMs) {
        if (root == null) return;
        Rules rules = Prefs.load(ctx);
        hasOffer = true;

        // resultado
        int fg = Render.verdictFg(r.verdict);
        verdictBox.setBackgroundTintList(ColorStateList.valueOf(Render.verdictBg(r.verdict)));
        verdictBox.setAlpha(1f);
        verdictIcon.setImageResource(Render.verdictIcon(r.verdict));
        verdictIcon.setImageTintList(ColorStateList.valueOf(fg));
        verdict.setText(Render.verdictLabel(r.verdict));
        verdict.setTextColor(fg);
        verdict.setTextSize(r.verdict == Calculator.Verdict.ACCEPT || r.verdict == Calculator.Verdict.REJECT ? 26 : 19);

        // motivos
        showReasons = r.verdict == Calculator.Verdict.REJECT && !r.reasons.isEmpty();
        StringBuilder sb = new StringBuilder();
        for (String s : r.reasons) { if (sb.length() > 0) sb.append('\n'); sb.append("✗  ").append(s); }
        reasons.setText(sb);

        // aviso
        boolean check = r.verdict == Calculator.Verdict.CHECK_DESTINATION;
        boolean incomplete = r.verdict == Calculator.Verdict.INCOMPLETE;
        showWarn = check || incomplete;
        if (check) {
            warnText.setText("Valores OK, mas a cidade não apareceu na tela:");
            warnAddress.setText(o.destinationText != null ? o.destinationText : "endereço não lido");
        } else if (incomplete) {
            warnText.setText("Não identificado: " + String.join(", ", r.missing));
        }
        warnAddress.setVisibility(check ? View.VISIBLE : View.GONE);
        destButtons.setVisibility(check ? View.VISIBLE : View.GONE);
        btnComplete.setVisibility(incomplete ? View.VISIBLE : View.GONE);

        // R$/km
        if (r.perKm != null) {
            boolean ok = r.perKm >= rules.minPerKm;
            perKm.setText(Render.money(r.perKm));
            perKm.setTextColor(ok ? Render.TEXT : Render.RED_TEXT);
            perKmHint.setText("mín. " + Render.money(rules.minPerKm) + (ok ? " ✓" : ""));
            perKmHint.setTextColor(ok ? Render.GREEN_TEXT : Render.MUTED);
        } else {
            perKm.setText("—");
            perKm.setTextColor(Render.MUTED);
            perKmHint.setText("");
        }

        // quadro 2 x 2
        setValue(vPrice, o.price == null ? null : Render.money(o.price),
                o.price != null && o.priceSrc == Offer.Source.MANUAL);
        Double total = o.totalKm();
        boolean distManual = (o.pickupKm != null && o.pickupSrc == Offer.Source.MANUAL)
                || (o.tripKm != null && o.tripSrc == Offer.Source.MANUAL);
        setValue(vDist, total == null ? null : Render.num(total) + " km", distManual);
        if (total != null) vDistSub.setText(Render.num(o.pickupKm) + " busca + " + Render.num(o.tripKm) + " viagem");
        else if (o.pickupKm != null) vDistSub.setText("busca " + Render.num(o.pickupKm) + " km · falta viagem");
        else if (o.tripKm != null) vDistSub.setText("viagem " + Render.num(o.tripKm) + " km · falta busca");
        else vDistSub.setText("");
        vDistSub.setVisibility(vDistSub.length() > 0 ? View.VISIBLE : View.GONE);
        setValue(vFuel, r.fuelCost == null ? null : "− " + Render.money(r.fuelCost), false);
        setValue(vProfit, r.profit == null ? null : Render.money(r.profit), false);
        boolean good = r.verdict == Calculator.Verdict.ACCEPT;
        tProfit.setBackgroundTintList(ColorStateList.valueOf(good ? Render.GREEN_BG : Render.TILE));
        lProfit.setTextColor(good ? 0xFF9CE8C6 : Render.MUTED);
        if (good) vProfit.setTextColor(Render.GREEN_TEXT);

        // destino
        destRow.setVisibility(check ? View.GONE : View.VISIBLE);
        destText.setText(o.destinationText != null ? o.destinationText : "destino não lido");
        String tag; int c;
        switch (o.destination) {
            case INSIDE: tag = "na cidade ✓"; c = Render.GREEN_TEXT; break;
            case ALLOWED: tag = "liberado ✓"; c = Render.GREEN_TEXT;
                if (o.matchedPlace != null) destText.setText(o.matchedPlace); break;
            case OUTSIDE: tag = "fora ✗"; c = Render.RED_TEXT; break;
            default: tag = "não identificado"; c = Render.AMBER;
        }
        if (o.destSrc == Offer.Source.MANUAL && o.destination != Offer.Destination.UNKNOWN) tag += " ✎";
        destTag.setText(tag);
        destTag.setTextColor(c);
        destIcon.setImageTintList(ColorStateList.valueOf(
                o.destination == Offer.Destination.OUTSIDE ? Render.RED_TEXT
                        : o.destination == Offer.Destination.UNKNOWN ? Render.AMBER : Render.BLUE_SOFT));

        footer.setText(Render.footer(o, elapsedMs));
        footer.setTextColor(o.anyManual() ? Render.AMBER_TEXT : Render.MUTED);
        footer.setVisibility(footer.length() > 0 ? View.VISIBLE : View.GONE);

        setStatus(o.anyManual() ? "Oferta (com dados digitados)" : "Oferta detectada", false);
        applyVisibility();
    }

    private static void setValue(TextView v, String value, boolean manual) {
        if (value == null) {
            v.setText("não lido");
            v.setTextColor(Render.AMBER);
            v.setTextSize(14);
        } else {
            v.setText(manual ? value + " ✎" : value);
            v.setTextColor(manual ? Render.BLUE_SOFT : Render.TEXT);
            v.setTextSize(17);
        }
    }

    /** a oferta sumiu da tela: mantem o ultimo resultado, esmaecido */
    public void markStale() {
        if (verdictBox != null && hasOffer) {
            verdictBox.setAlpha(0.45f);
            setStatus("Aguardando nova oferta… (resultado anterior)", false);
            dot.setBackgroundTintList(ColorStateList.valueOf(Render.MUTED));
        }
    }

    public void hide() {
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        }
    }
}
