package com.smartdriver.calc;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Preenchimento/correcao manual. Campos lidos da tela vem preenchidos e marcados;
 * o que o motorista digitar fica marcado como MANUAL no resultado.
 */
public class ManualActivity extends Activity {
    private Offer base;     // oferta lida da tela (ou vazia)
    private EditText price, pickup, trip;
    private RadioGroup dest;
    private RadioButton rIn, rOut, rUnk;
    private TextView verdict, result;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Offer cur = Session.current;
        base = cur != null ? cur.copy() : new Offer();
        if (cur == null) {
            base.priceSrc = base.pickupSrc = base.tripSrc = base.destSrc = Offer.Source.MANUAL;
        }

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView t = label(cur == null ? "Cálculo manual" : "Conferir / completar oferta", 20);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(t);
        if (cur != null) {
            col.addView(label("Campos com \"(lido da tela)\" vieram do Uber. Corrija só se estiver errado.", 13));
        }

        price = add(col, "Valor da corrida (R$)", base.price, base.priceSrc);
        pickup = add(col, "Km até o passageiro", base.pickupKm, base.pickupSrc);
        trip = add(col, "Km da viagem", base.tripKm, base.tripSrc);

        col.addView(label(destTitle(), 14));
        dest = new RadioGroup(this);
        String c = Prefs.city(this);
        rIn = radio("Dentro de " + c + " ou local liberado");
        rOut = radio("Fora de " + c);
        rUnk = radio("Não sei");
        dest.addView(rIn); dest.addView(rOut); dest.addView(rUnk);
        switch (base.destination) {
            case INSIDE:
            case ALLOWED: rIn.setChecked(true); break;
            case OUTSIDE: rOut.setChecked(true); break;
            default: rUnk.setChecked(true);
        }
        col.addView(dest);

        Button calc = new Button(this);
        calc.setText("Calcular");
        calc.setOnClickListener(v -> calculate());
        col.addView(calc);

        verdict = label("", 22);
        verdict.setTypeface(null, android.graphics.Typeface.BOLD);
        verdict.setPadding(0, pad, 0, 0);
        col.addView(verdict);
        result = label("", 14);
        result.setTypeface(android.graphics.Typeface.MONOSPACE);
        col.addView(result);

        Button back = new Button(this);
        back.setText("Voltar ao Uber");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        col.addView(back);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }

    private String destTitle() {
        if (base.destinationText != null) return "Destino (lido da tela): " + base.destinationText;
        return "Destino: não identificado na tela";
    }

    private void calculate() {
        Offer o = base.copy();
        Double p = MainActivity.parse(price), pk = MainActivity.parse(pickup), tk = MainActivity.parse(trip);
        if (!same(p, base.price)) { o.price = p; o.priceSrc = Offer.Source.MANUAL; }
        if (!same(pk, base.pickupKm)) { o.pickupKm = pk; o.pickupSrc = Offer.Source.MANUAL; }
        if (!same(tk, base.tripKm)) { o.tripKm = tk; o.tripSrc = Offer.Source.MANUAL; }

        Offer.Destination chosen = rIn.isChecked()
                ? (base.destination == Offer.Destination.ALLOWED ? Offer.Destination.ALLOWED : Offer.Destination.INSIDE)
                : rOut.isChecked() ? Offer.Destination.OUTSIDE : Offer.Destination.UNKNOWN;
        if (chosen != base.destination) { o.destination = chosen; o.destSrc = Offer.Source.MANUAL; }

        Calculator.Result r = Calculator.evaluate(o, Prefs.load(this));
        verdict.setText(Render.verdictLabel(r.verdict));
        verdict.setTextColor(Render.verdictColor(r.verdict));
        StringBuilder extra = new StringBuilder("\n\n").append(Render.footer(o, r, -1));
        result.setText(android.text.TextUtils.concat(Render.body(o, r), extra));

        Session.current = o;
        CaptureService svc = CaptureService.get();
        if (svc != null) svc.showManual(o);
    }

    /** igual ao valor lido (o campo mostra 2 casas decimais) */
    private static boolean same(Double a, Double b) {
        if (a == null || b == null) return a == b;
        return Math.abs(a - b) < 0.006;
    }

    private EditText add(LinearLayout col, String name, Double value, Offer.Source src) {
        String tag = value == null ? " — não identificado" : (src == Offer.Source.AUTO ? " (lido da tela)" : "");
        col.addView(label(name + tag, 14));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        if (value != null) e.setText(String.format(Render.BR, "%.2f", value));
        col.addView(e);
        return e;
    }

    private RadioButton radio(String s) {
        RadioButton r = new RadioButton(this);
        r.setText(s);
        r.setId(android.view.View.generateViewId());
        return r;
    }

    private TextView label(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(0xFF202124);
        t.setPadding(0, 8, 0, 0);
        return t;
    }

    @Override protected void onResume() { super.onResume(); CaptureService.paused = true; }
    @Override protected void onPause() { super.onPause(); CaptureService.paused = false; }
}
