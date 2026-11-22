package com.smartdriver.calc;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 10;
    private static final String UBER_DRIVER = "com.ubercab.driver";

    private EditText kmL, fuel, city, minKm, minTrip;
    private FlowLayout citiesView, placesView, hoodsView;
    private TextView pill;
    private Button btnMain;
    private List<String> cities, places, hoods;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        kmL = findViewById(R.id.kmL);
        fuel = findViewById(R.id.fuel);
        city = findViewById(R.id.city);
        minKm = findViewById(R.id.minKm);
        minTrip = findViewById(R.id.minTrip);
        citiesView = findViewById(R.id.cities);
        placesView = findViewById(R.id.places);
        hoodsView = findViewById(R.id.hoods);
        pill = findViewById(R.id.pill);
        btnMain = findViewById(R.id.btnMain);

        Rules r = Prefs.load(this);
        if (r.kmPerLiter > 0) kmL.setText(Render.num(r.kmPerLiter));
        if (r.fuelPrice > 0) fuel.setText(Render.dec2(r.fuelPrice));
        city.setText(Prefs.city(this));
        minKm.setText(Render.dec2(r.minPerKm));
        minTrip.setText(Render.dec2(r.minPerTrip));
        cities = Prefs.split(Prefs.allowedCities(this));
        places = Prefs.split(Prefs.allowedPlaces(this));
        hoods = Prefs.split(Prefs.insideExtra(this));
        renderChips(citiesView, cities, "Nova cidade liberada", "ex: Maracanaú");
        renderChips(placesView, places, "Novo local liberado", "ex: Shopping Maracanaú");
        renderChips(hoodsView, hoods, "Novo bairro", "ex: Novo Maranguape");

        btnMain.setOnClickListener(v -> {
            if (!overlayOk()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else if (CaptureService.running) {
                CaptureService.stop(this);
                btnMain.postDelayed(this::refresh, 300);
            } else {
                startCapture();
            }
        });
        findViewById(R.id.btnManual).setOnClickListener(v -> {
            if (saveConfig(true)) startActivity(new Intent(this, ManualActivity.class));
        });

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
        saveConfig(false);
        CaptureService.paused = false;
    }

    private boolean overlayOk() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void refresh() {
        Double k = MainActivity.parse(kmL), f = MainActivity.parse(fuel);
        boolean carOk = k != null && k > 0 && f != null && f > 0;
        if (CaptureService.running) setPill("● Lendo a tela", false);
        else if (!overlayOk()) setPill("● Falta permissão", true);
        else if (!carOk) setPill("● Configure o carro", true);
        else setPill("● Pronto", false);

        if (!overlayOk()) {
            btnMain.setText("Permitir janela flutuante");
            btnMain.setBackgroundTintList(null);
            btnMain.setTextColor(getColor(R.color.on_accent));
        } else if (CaptureService.running) {
            btnMain.setText("Parar leitura");
            btnMain.setBackgroundTintList(ColorStateList.valueOf(Render.RED));
            btnMain.setTextColor(0xFFFFFFFF);
        } else {
            btnMain.setText("Iniciar leitura");
            btnMain.setBackgroundTintList(null);
            btnMain.setTextColor(getColor(R.color.on_accent));
        }
    }

    private void setPill(String s, boolean warn) {
        pill.setText(s);
        pill.setTextColor(warn ? Render.AMBER_TEXT : Render.GREEN_TEXT);
        pill.setBackgroundTintList(ColorStateList.valueOf(warn ? 0xFF2A2210 : Render.GREEN_BG));
    }

    /** etiquetas: toque para remover, "+ Adicionar" para incluir */
    private void renderChips(FlowLayout box, List<String> list, String title, String hint) {
        box.removeAllViews();
        int px14 = dp(14);
        for (String item : list) {
            TextView chip = chip(item);
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setTextColor(getColor(R.color.blue_soft));
            Drawable d = getDrawable(R.drawable.ic_check).mutate();
            d.setTint(0xFF7DB6FF);
            d.setBounds(0, 0, px14, px14);
            chip.setCompoundDrawables(d, null, null, null);
            chip.setCompoundDrawablePadding(dp(6));
            chip.setContentDescription(item + ". Toque para remover");
            chip.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("Remover “" + item + "”?")
                    .setPositiveButton("Remover", (dl, w) -> {
                        list.remove(item);
                        Prefs.saveLists(this, cities, hoods, places);
                        renderChips(box, list, title, hint);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show());
            box.addView(chip);
        }
        TextView add = chip("+ Adicionar");
        add.setBackgroundResource(R.drawable.bg_chip_add);
        add.setTextColor(getColor(R.color.muted));
        add.setOnClickListener(v -> {
            EditText in = new EditText(this);
            in.setHint(hint);
            in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            in.setTextColor(getColor(R.color.text));
            FrameLayout wrap = new FrameLayout(this);
            wrap.setPadding(dp(20), dp(8), dp(20), 0);
            wrap.addView(in);
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(wrap)
                    .setPositiveButton("Adicionar", (dl, w) -> {
                        String s = in.getText().toString().replace(",", " ").trim();
                        if (!s.isEmpty() && !list.contains(s)) {
                            list.add(s);
                            Prefs.saveLists(this, cities, hoods, places);
                            renderChips(box, list, title, hint);
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
        box.addView(add);
    }

    private TextView chip(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setMinHeight(dp(36));
        t.setGravity(android.view.Gravity.CENTER_VERTICAL);
        t.setPadding(dp(10), dp(6), dp(12), dp(6));
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private boolean saveConfig(boolean warn) {
        Double k = parse(kmL), f = parse(fuel);
        boolean ok = k != null && k > 0 && f != null && f > 0;
        Rules cur = Prefs.load(this);
        Prefs.save(this, ok ? k.floatValue() : (float) cur.kmPerLiter, ok ? f.floatValue() : (float) cur.fuelPrice,
                city.getText().toString(), String.join("\n", hoods), String.join("\n", places));
        Double mk = parse(minKm), mt = parse(minTrip);
        Prefs.saveLimits(this, mk != null && mk >= 0 ? mk.floatValue() : 2.00f,
                mt != null && mt >= 0 ? mt.floatValue() : 8.00f);
        if (!ok && warn) {
            Toast.makeText(this, "Preencha consumo (km/L) e preço do combustível.", Toast.LENGTH_LONG).show();
        }
        return ok;
    }

    private void startCapture() {
        if (!saveConfig(true)) return;
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
}
