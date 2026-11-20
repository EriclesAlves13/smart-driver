package com.smartdriver.calc;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.text.NumberFormat;
import java.util.Locale;

/** Monta o texto do resultado (usado na janela flutuante e na tela manual). */
public final class Render {
    private Render() {}

    static final Locale BR = new Locale("pt", "BR");
    private static final int WARN = 0xFFFFB300;
    private static final int MANUAL = 0xFF64B5F6;

    public static String money(Double v) {
        if (v == null) return null;
        NumberFormat f = NumberFormat.getCurrencyInstance(BR);
        return f.format(v).replace(' ', ' ');
    }

    public static String num(double v) {
        return String.format(BR, "%.1f", v);
    }

    public static String verdictLabel(Calculator.Verdict v) {
        switch (v) {
            case ACCEPT: return "ACEITAR";
            case REJECT: return "RECUSAR";
            case CHECK_DESTINATION: return "CONFIRME O DESTINO";
            default: return "DADOS INCOMPLETOS";
        }
    }

    public static int verdictColor(Calculator.Verdict v) {
        switch (v) {
            case ACCEPT: return 0xFF1DB954;
            case REJECT: return 0xFFE53935;
            default: return 0xFFB27A00;
        }
    }

    public static CharSequence body(Offer o, Calculator.Result r) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        row(sb, "Valor    ", money(o.price), o.priceSrc == Offer.Source.MANUAL && o.price != null);

        String dist = null;
        Double total = o.totalKm();
        if (total != null) {
            dist = num(total) + " km (" + num(o.pickupKm) + "+" + num(o.tripKm) + ")";
        } else if (o.pickupKm != null || o.tripKm != null) {
            dist = "incompleta";
        }
        boolean distManual = (o.pickupKm != null && o.pickupSrc == Offer.Source.MANUAL)
                || (o.tripKm != null && o.tripSrc == Offer.Source.MANUAL);
        row(sb, "Distância", dist, distManual);
        row(sb, "R$/km    ", r.perKm == null ? null : money(r.perKm), false);
        row(sb, "Combust. ", r.fuelCost == null ? null : money(r.fuelCost), false);
        row(sb, "Lucro    ", r.profit == null ? null : money(r.profit), false);

        String dest;
        switch (o.destination) {
            case INSIDE: dest = "na cidade ✓"; break;
            case ALLOWED: dest = o.matchedPlace != null
                    ? "liberado ✓ " + o.matchedPlace : "local liberado ✓"; break;
            case OUTSIDE: dest = "FORA ✗"; break;
            default: dest = null;
        }
        row(sb, "Destino  ", dest, o.destSrc == Offer.Source.MANUAL && o.destination != Offer.Destination.UNKNOWN);
        if (o.destinationText != null) {
            int s = sb.length();
            sb.append("  ").append(o.destinationText).append('\n');
            sb.setSpan(new ForegroundColorSpan(Color.GRAY), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // remove ultima quebra de linha
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.delete(sb.length() - 1, sb.length());
        return sb;
    }

    private static void row(SpannableStringBuilder sb, String label, String value, boolean manual) {
        sb.append(label).append(' ');
        int s = sb.length();
        if (value == null) {
            sb.append("não identificado");
            sb.setSpan(new ForegroundColorSpan(WARN), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            sb.append(value);
            if (manual) {
                sb.append(" ✎");
                sb.setSpan(new ForegroundColorSpan(MANUAL), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        sb.append('\n');
    }

    public static String footer(Offer o, Calculator.Result r, long elapsedMs) {
        StringBuilder f = new StringBuilder();
        if (!r.reasons.isEmpty()) f.append("Motivo: ").append(String.join("; ", r.reasons)).append('\n');
        if (r.verdict == Calculator.Verdict.INCOMPLETE || r.verdict == Calculator.Verdict.CHECK_DESTINATION) {
            f.append("Não identificado: ").append(String.join(", ", r.missing)).append(". Toque em Editar.\n");
        }
        if (o.anyManual()) {
            f.append("⚠ Análise com dados digitados (✎) — não é 100% automática");
        } else if (elapsedMs >= 0) {
            f.append("Lido da tela automaticamente · ").append(String.format(BR, "%.1f s", elapsedMs / 1000.0));
            if (elapsedMs > 7000) f.append(" (acima de 7 s)");
        }
        return f.toString().trim();
    }
}
