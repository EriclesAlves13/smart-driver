package com.smartdriver.calc;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Guarda apenas a configuracao (sem historico). */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    public static Rules load(Context c) {
        SharedPreferences p = sp(c);
        Rules r = new Rules();
        r.kmPerLiter = p.getFloat("kmPerLiter", 0f);
        r.fuelPrice = p.getFloat("fuelPrice", 0f);
        r.minPerKm = p.getFloat("minPerKm", 2.00f);
        r.minPerTrip = p.getFloat("minPerTrip", 8.00f);
        r.city = city(c);
        List<String> inside = split(p.getString("insideWords", ""));
        inside.add(0, r.city);
        r.insideWords = inside.toArray(new String[0]);
        r.allowedPlaces = split(p.getString("allowedPlaces", "")).toArray(new String[0]);
        return r;
    }

    public static String city(Context c) {
        String s = sp(c).getString("city", "Maranguape").trim();
        return s.isEmpty() ? "Maranguape" : s;
    }

    public static String insideExtra(Context c) {
        return sp(c).getString("insideWords", "");
    }

    public static String allowedPlaces(Context c) {
        return sp(c).getString("allowedPlaces", "");
    }

    public static void save(Context c, float kmPerLiter, float fuelPrice, String city,
                            String insideExtra, String allowedPlaces) {
        sp(c).edit()
                .putFloat("kmPerLiter", kmPerLiter)
                .putFloat("fuelPrice", fuelPrice)
                .putString("city", city == null ? "" : city.trim())
                .putString("insideWords", insideExtra == null ? "" : insideExtra.trim())
                .putString("allowedPlaces", allowedPlaces == null ? "" : allowedPlaces.trim())
                .apply();
    }

    /** separa por virgula, ponto e virgula ou linha */
    static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String part : s.split("[,;\\n]")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
