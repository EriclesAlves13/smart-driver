package com.smartdriver.calc;

import java.text.NumberFormat;
import java.util.Locale;

/** Formatacao e cores do resultado (visual "Painel Noturno"). */
public final class Render {
    private Render() {}

    static final Locale BR = new Locale("pt", "BR");

    public static final int GREEN = 0xFF34D399, ON_GREEN = 0xFF04281A, GREEN_TEXT = 0xFF6EE7B7, GREEN_BG = 0xFF10241B;
    public static final int RED = 0xFFB91C1C, RED_TEXT = 0xFFFCA5A5;
    public static final int AMBER = 0xFFFBBF24, ON_AMBER = 0xFF2B1A00, AMBER_TEXT = 0xFFFDE68A;
    public static final int TEXT = 0xFFE8EEF5, MUTED = 0xFF93A1B2, TILE = 0xFF161E27, BLUE_SOFT = 0xFFA9CEFF;

    public static String money(Double v) {
        if (v == null) return null;
        return NumberFormat.getCurrencyInstance(BR).format(v).replace(' ', ' ');
    }

    public static String num(double v) {
        return String.format(BR, "%.1f", v);
    }

    public static String dec2(double v) {
        return String.format(BR, "%.2f", v);
    }

    public static String verdictLabel(Calculator.Verdict v) {
        switch (v) {
            case ACCEPT: return "ACEITAR";
            case REJECT: return "RECUSAR";
            case CHECK_DESTINATION: return "CONFIRME O DESTINO";
            default: return "DADOS INCOMPLETOS";
        }
    }

    /** cor de fundo do bloco do resultado */
    public static int verdictBg(Calculator.Verdict v) {
        switch (v) {
            case ACCEPT: return GREEN;
            case REJECT: return RED;
            default: return AMBER;
        }
    }

    /** cor do texto/icone sobre o bloco do resultado */
    public static int verdictFg(Calculator.Verdict v) {
        switch (v) {
            case ACCEPT: return ON_GREEN;
            case REJECT: return 0xFFFFFFFF;
            default: return ON_AMBER;
        }
    }

    public static int verdictIcon(Calculator.Verdict v) {
        switch (v) {
            case ACCEPT: return R.drawable.ic_check;
            case REJECT: return R.drawable.ic_x;
            default: return R.drawable.ic_warn;
        }
    }

    /** rodape: origem dos dados e tempo da analise */
    public static String footer(Offer o, long elapsedMs) {
        if (o.anyManual()) return "✎ Com dados digitados — não é 100% automática";
        if (elapsedMs < 0) return "";
        String s = "Lido da tela automaticamente · " + String.format(BR, "%.1f s", elapsedMs / 1000.0);
        return elapsedMs > 7000 ? s + " (acima de 7 s)" : s;
    }
}
