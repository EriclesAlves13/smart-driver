package com.smartdriver.calc;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 10;
    private static final String UBER_DRIVER = "com.ubercab.driver";

    private EditText kmL, fuel, city, words, places;
    private TextView status;
    private Button btnOverlay, btnStart, btnStop;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView title = text("Smart Driver", 24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(title);
        col.addView(text("Calculadora de ofertas. Lê a tela com a sua autorização e mostra "
                + "ACEITAR ou RECUSAR. Você sempre decide e toca no Uber — o app não aceita nada sozinho.", 14));

        col.addView(section("Regras"));
        col.addView(text("• Mínimo R$ 2,00 por km (valor ÷ km totais: busca + viagem)\n"
                + "• Mínimo R$ 8,00 por corrida\n"
                + "• Destino somente na sua cidade ou em um local liberado\n"
                + "• Lucro = valor − combustível dos km totais", 14));

        col.addView(section("Seu carro (obrigatório)"));
        kmL = field("Consumo (km por litro) ex: 11,5");
        fuel = field("Preço do combustível (R$/litro) ex: 6,19");
        col.addView(kmL);
        col.addView(fuel);
        col.addView(section("Cidade onde você roda"));
        city = new EditText(this);
        city.setHint("Maranguape");
        col.addView(city);

        col.addView(section("Bairros da cidade (opcional)"));
        col.addView(text("O Uber às vezes mostra só rua e bairro. Liste bairros da sua cidade "
                + "separados por vírgula para reconhecer o destino.", 13));
        words = new EditText(this);
        words.setHint("ex: Novo Maranguape, Parque São João");
        col.addView(words);

        col.addView(section("Locais liberados fora da cidade (opcional)"));
        col.addView(text("Destinos fora da cidade que você aceita. Um por linha. Escreva o nome como "
                + "aparece no Uber (ou o nome da rua). A ordem das palavras e os acentos não importam.", 13));
        places = new EditText(this);
        places.setHint("ex: Shopping Maracanaú");
        places.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        places.setMinLines(2);
        col.addView(places);

        Rules r = Prefs.load(this);
        if (r.kmPerLiter > 0) kmL.setText(Render.num(r.kmPerLiter));
        if (r.fuelPrice > 0) fuel.setText(String.format(Render.BR, "%.2f", r.fuelPrice));
        city.setText(Prefs.city(this));
        words.setText(Prefs.insideExtra(this));
        places.setText(Prefs.allowedPlaces(this));

        col.addView(section("Iniciar"));
        btnOverlay = button("1. Permitir janela flutuante", v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        btnStart = button("2. Iniciar leitura da tela", v -> startCapture());
        btnStop = button("Parar leitura", v -> { CaptureService.stop(this); refresh(); });
        col.addView(btnOverlay);
        col.addView(btnStart);
        col.addView(btnStop);
        col.addView(button("Calcular manualmente", v -> {
            if (saveConfig()) startActivity(new Intent(this, ManualActivity.class));
        }));

        status = text("", 13);
        status.setPadding(0, pad / 2, 0, 0);
        col.addView(status);

        col.addView(section("Como funciona"));
        col.addView(text("Usa a captura de tela oficial do Android (MediaProjection) e reconhecimento de "
                + "texto no próprio celular, sem internet. Não usa API da Uber, não lê dados internos "
                + "do app e não toca na tela. Se o Uber bloquear a captura ou algum dado não for "
                + "lido, a janela avisa \"não identificado\" e você pode tocar em Editar.\n\n"
                + "No Android 14+, ao autorizar, escolha \"Tela inteira\" (ou o app Uber Driver).", 13));

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        CaptureService.paused = true;
        refresh();
    }

    @Override protected void onPause() {
        super.onPause();
        CaptureService.paused = false;
    }

    private void refresh() {
        boolean overlayOk = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        btnOverlay.setVisibility(overlayOk ? View.GONE : View.VISIBLE);
        btnStart.setEnabled(overlayOk && !CaptureService.running);
        btnStop.setVisibility(CaptureService.running ? View.VISIBLE : View.GONE);
        status.setText(CaptureService.running
                ? "● Leitura ativa. Abra o Uber Driver."
                : overlayOk ? "Pronto para iniciar." : "Falta permitir a janela flutuante.");
    }

    private boolean saveConfig() {
        Double k = parse(kmL), f = parse(fuel);
        if (k == null || k <= 0 || f == null || f <= 0) {
            Toast.makeText(this, "Preencha consumo (km/L) e preço do combustível.", Toast.LENGTH_LONG).show();
            return false;
        }
        Prefs.save(this, k.floatValue(), f.floatValue(), city.getText().toString(),
                words.getText().toString(), places.getText().toString());
        return true;
    }

    private void startCapture() {
        if (!saveConfig()) return;
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_CAPTURE) return;
        if (res != RESULT_OK || data == null) {
            Toast.makeText(this, "Captura não autorizada. Use o cálculo manual.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent svc = new Intent(this, CaptureService.class)
                .putExtra(CaptureService.EXTRA_CODE, res)
                .putExtra(CaptureService.EXTRA_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);

        Intent uber = getPackageManager().getLaunchIntentForPackage(UBER_DRIVER);
        if (uber != null) startActivity(uber);
        else Toast.makeText(this, "Leitura ativa. Abra o Uber Driver.", Toast.LENGTH_LONG).show();
    }

    static Double parse(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return null;
        try { return OfferParser.parseNumber(s); } catch (NumberFormatException ex) { return null; }
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(0xFF202124);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 16);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, (int) (18 * getResources().getDisplayMetrics().density), 0, 4);
        return t;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT);   // aceita virgula
        return e;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }
}
