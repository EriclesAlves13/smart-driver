package com.smartdriver.calc;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Preenchimento/correcao manual. Campos lidos da tela vem preenchidos e marcados;
 * o que o motorista digitar fica marcado como MANUAL no resultado.
 */
public class ManualActivity extends Activity {
    private Offer base;      // oferta lida da tela (ou vazia)
    private boolean fromScreen;
    private EditText price, pickup, trip;
    private Button segIn, segOut, segUnk;
    private Offer.Destination chosen;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_manual);
        Offer cur = Session.current;
        fromScreen = cur != null;
        base = fromScreen ? cur.copy() : new Offer();
        if (!fromScreen) base.priceSrc = base.pickupSrc = base.tripSrc = base.destSrc = Offer.Source.MANUAL;

        if (!fromScreen) {
            ((TextView) findViewById(R.id.title)).setText("Cálculo manual");
            ((TextView) findViewById(R.id.subtitle)).setText("Digite os dados da oferta");
        }

        price = findViewById(R.id.price);
        pickup = findViewById(R.id.pickup);
        trip = findViewById(R.id.trip);
        bindField(price, R.id.tagPrice, R.id.boxPrice, 0, base.price, base.priceSrc);
        bindField(pickup, R.id.tagPickup, R.id.boxPickup, R.id.lPickup, base.pickupKm, base.pickupSrc);
        bindField(trip, R.id.tagTrip, R.id.boxTrip, R.id.lTrip, base.tripKm, base.tripSrc);

        TextView dest = findViewById(R.id.destText);
        String c = Prefs.city(this);
        if (base.destination == Offer.Destination.ALLOWED && base.matchedPlace != null) {
            dest.setText(base.matchedPlace + " (local liberado)");
        } else if (base.destinationText != null) {
            dest.setText(base.destinationText);
        } else {
            dest.setText(fromScreen ? "Endereço não identificado na tela" : "Onde termina a corrida?");
        }
        segIn = findViewById(R.id.segIn);
        segOut = findViewById(R.id.segOut);
        segUnk = findViewById(R.id.segUnk);
        segIn.setOnClickListener(v -> select(Offer.Destination.INSIDE));
        segOut.setOnClickListener(v -> select(Offer.Destination.OUTSIDE));
        segUnk.setOnClickListener(v -> select(Offer.Destination.UNKNOWN));
        select(base.destination == Offer.Destination.ALLOWED ? Offer.Destination.INSIDE : base.destination);

        findViewById(R.id.btnCalc).setOnClickListener(v -> calculate());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnDone).setOnClickListener(v -> finish());
    }

    private void select(Offer.Destination d) {
        chosen = d;
        style(segIn, d == Offer.Destination.INSIDE, Render.GREEN, Render.ON_GREEN);
        style(segOut, d == Offer.Destination.OUTSIDE, Render.RED, 0xFFFFFFFF);
        style(segUnk, d == Offer.Destination.UNKNOWN, 0xFF3A444F, Render.TEXT);
    }

    private static void style(Button b, boolean on, int bg, int fg) {
        b.setBackgroundTintList(ColorStateList.valueOf(on ? bg : 0x00000000));
        b.setTextColor(on ? fg : 0xFFC5D0DC);
    }

    /** etiqueta do campo: lido da tela / nao identificado / digitado */
    private void bindField(EditText e, int tagId, int boxId, int labelId, Double value, Offer.Source src) {
        TextView tag = findViewById(tagId);
        View box = findViewById(boxId);
        TextView label = labelId != 0 ? findViewById(labelId) : null;
        if (value != null) e.setText(Render.dec2(value));
        if (!fromScreen) { tag.setVisibility(View.GONE); return; }

        Runnable update = () -> {
            Double now = MainActivity.parse(e);
            boolean missing = value == null;
            if (missing && now == null) {
                tag.setText("não identificado");
                tint(tag, Render.AMBER, Render.ON_AMBER);
                box.setBackgroundResource(R.drawable.bg_input_warn);
                if (label != null) label.setTextColor(Render.AMBER_TEXT);
            } else if (!missing && same(now, value) && src == Offer.Source.AUTO) {
                tag.setText("lido da tela");
                tint(tag, 0xFF1A2A3F, Render.BLUE_SOFT);
                box.setBackgroundResource(R.drawable.bg_card);
                if (label != null) label.setTextColor(Render.MUTED);
            } else {
                tag.setText("digitado ✎");
                tint(tag, 0xFF1A232E, Render.BLUE_SOFT);
                box.setBackgroundResource(R.drawable.bg_card);
                if (label != null) label.setTextColor(Render.MUTED);
            }
        };
        update.run();
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { update.run(); }
        });
    }

    private static void tint(TextView t, int bg, int fg) {
        t.setBackgroundTintList(ColorStateList.valueOf(bg));
        t.setTextColor(fg);
    }

    private void calculate() {
        Offer o = base.copy();
        Double p = MainActivity.parse(price), pk = MainActivity.parse(pickup), tk = MainActivity.parse(trip);
        if (!same(p, base.price)) { o.price = p; o.priceSrc = Offer.Source.MANUAL; }
        if (!same(pk, base.pickupKm)) { o.pickupKm = pk; o.pickupSrc = Offer.Source.MANUAL; }
        if (!same(tk, base.tripKm)) { o.tripKm = tk; o.tripSrc = Offer.Source.MANUAL; }

        Offer.Destination d = chosen;
        if (d == Offer.Destination.INSIDE && base.destination == Offer.Destination.ALLOWED) d = Offer.Destination.ALLOWED;
        if (d != base.destination) { o.destination = d; o.destSrc = Offer.Source.MANUAL; o.matchedPlace = null; }

        Rules rules = Prefs.load(this);
        Calculator.Result r = Calculator.evaluate(o, rules);
        showResult(o, r);

        Session.current = o;
        CaptureService svc = CaptureService.get();
        if (svc != null) svc.showManual(o);
    }

    private void showResult(Offer o, Calculator.Result r) {
        findViewById(R.id.resultCard).setVisibility(View.VISIBLE);
        View box = findViewById(R.id.verdictBox);
        box.setBackgroundTintList(ColorStateList.valueOf(Render.verdictBg(r.verdict)));
        ImageView icon = findViewById(R.id.verdictIcon);
        icon.setImageResource(Render.verdictIcon(r.verdict));
        icon.setImageTintList(ColorStateList.valueOf(Render.verdictFg(r.verdict)));
        TextView v = findViewById(R.id.verdict);
        v.setText(Render.verdictLabel(r.verdict));
        v.setTextColor(Render.verdictFg(r.verdict));

        TextView reasons = findViewById(R.id.reasons);
        StringBuilder sb = new StringBuilder();
        if (r.verdict == Calculator.Verdict.REJECT) {
            for (String s : r.reasons) { if (sb.length() > 0) sb.append('\n'); sb.append("✗  ").append(s); }
            reasons.setBackgroundTintList(ColorStateList.valueOf(0xFF2A1416));
            reasons.setTextColor(0xFFFECACA);
        } else if (!r.missing.isEmpty() && r.verdict != Calculator.Verdict.ACCEPT) {
            sb.append("Falta: ").append(String.join(", ", r.missing));
            reasons.setBackgroundTintList(ColorStateList.valueOf(0xFF2A2210));
            reasons.setTextColor(Render.AMBER_TEXT);
        }
        reasons.setText(sb);
        reasons.setVisibility(sb.length() > 0 ? View.VISIBLE : View.GONE);

        ((TextView) findViewById(R.id.mPerKm)).setText(r.perKm == null ? "—" : Render.dec2(r.perKm));
        ((TextView) findViewById(R.id.mFuel)).setText(r.fuelCost == null ? "—" : Render.money(r.fuelCost));
        TextView profit = findViewById(R.id.mProfit);
        profit.setText(r.profit == null ? "—" : Render.money(r.profit));
        profit.setTextColor(r.verdict == Calculator.Verdict.ACCEPT ? Render.GREEN_TEXT : Render.TEXT);
        findViewById(R.id.manualNote).setVisibility(o.anyManual() ? View.VISIBLE : View.GONE);
    }

    /** igual ao valor lido (o campo mostra 2 casas decimais) */
    private static boolean same(Double a, Double b) {
        if (a == null || b == null) return a == b;
        return Math.abs(a - b) < 0.006;
    }

    @Override protected void onResume() { super.onResume(); CaptureService.paused = true; }
    @Override protected void onPause() { super.onPause(); CaptureService.paused = false; }
}
