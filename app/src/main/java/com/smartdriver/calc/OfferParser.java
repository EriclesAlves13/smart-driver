package com.smartdriver.calc;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le o texto reconhecido (OCR) da tela e extrai os dados da oferta.
 * Se um dado nao for encontrado com seguranca, fica null - nunca e estimado.
 */
public final class OfferParser {

    // R$ 12,45 | RS 12,45 (erro comum de OCR) | +R$ 2,00 (bonus) | R$ 1,50/km
    private static final Pattern PRICE = Pattern.compile(
            "(\\+\\s*)?R\\s?[$S5]\\s?(\\d{1,4}(?:\\.\\d{3})*,\\d{2}|\\d{1,4}\\.\\d{2})(\\s*(?:/|por)\\s*km)?",
            Pattern.CASE_INSENSITIVE);

    // "4 min (1,2 km)"  "1 h 5 min (40 km)"  "3 min (800 m)"
    private static final Pattern LEG = Pattern.compile(
            "(?:(\\d{1,2})\\s*h\\s*)?(\\d{1,3})\\s*min\\S*\\s*\\(?\\s*(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*(km|m)\\b\\s*\\)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STOP_LINE = Pattern.compile(
            "(?i).*(aceitar|selecionar|confirmar|recusar|R\\s?\\$).*");

    private OfferParser() {}

    private static class Leg {
        int start, end;
        int minutes;
        double km;
    }

    /** @return oferta encontrada ou null se o texto nao parece uma oferta */
    public static Offer parse(String raw, Rules rules) {
        if (raw == null) return null;
        String text = raw.replace(' ', ' ');

        Double price = findPrice(text);
        List<Leg> legs = findLegs(text);
        if (price == null || legs.isEmpty()) return null;

        Offer o = new Offer();
        o.price = price;

        Leg pickup = null, trip = null;
        if (legs.size() >= 2) {
            Leg a = legs.get(0), b = legs.get(1);
            if (isTrip(text, a) && !isTrip(text, b)) { trip = a; pickup = b; }
            else { pickup = a; trip = b; }
        } else {
            Leg only = legs.get(0);
            if (isTrip(text, only)) trip = only;
            else if (isPickup(text, only)) pickup = only;
            // senao: nao da para saber qual trecho e -> fica sem distancia
        }
        if (pickup != null) { o.pickupKm = pickup.km; o.pickupMin = pickup.minutes; }
        if (trip != null) {
            o.tripKm = trip.km; o.tripMin = trip.minutes;
            o.destinationText = textAfter(text, trip.end);
        }
        o.destination = classify(o.destinationText, rules);
        if (o.destination == Offer.Destination.ALLOWED) o.matchedPlace = matchPlace(o.destinationText, rules);
        return o;
    }

    static Double findPrice(String text) {
        Matcher m = PRICE.matcher(text);
        while (m.find()) {
            if (m.group(1) != null) continue;  // "+R$" = bonus incluido
            if (m.group(3) != null) continue;  // "R$/km"
            return parseNumber(m.group(2));
        }
        return null;
    }

    private static List<Leg> findLegs(String text) {
        List<Leg> list = new ArrayList<>();
        Matcher m = LEG.matcher(text);
        while (m.find()) {
            Leg l = new Leg();
            l.start = m.start();
            l.end = m.end();
            int h = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
            l.minutes = h * 60 + Integer.parseInt(m.group(2));
            double v = parseNumber(m.group(3));
            l.km = "m".equalsIgnoreCase(m.group(4)) ? v / 1000.0 : v;
            list.add(l);
        }
        return list;
    }

    private static boolean isTrip(String text, Leg l) {
        String before = norm(text.substring(Math.max(0, l.start - 25), l.start));
        return before.contains("viagem");
    }

    private static boolean isPickup(String text, Leg l) {
        String after = norm(text.substring(l.end, Math.min(text.length(), l.end + 25)));
        return after.contains("distancia");
    }

    /** ate 2 linhas apos o trecho da viagem = endereco de destino */
    private static String textAfter(String text, int pos) {
        String rest = text.substring(pos);
        String[] lines = rest.split("\n");
        StringBuilder sb = new StringBuilder();
        int taken = 0;
        for (int i = 0; i < lines.length && taken < 2; i++) {
            String line = lines[i].trim();
            if (i == 0 && line.isEmpty()) continue;
            if (line.isEmpty()) continue;
            if (STOP_LINE.matcher(line).matches()) break;
            if (LEG.matcher(line).find()) break;
            if (sb.length() > 0) sb.append(", ");
            sb.append(line);
            taken++;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** dentro da cidade, local liberado, fora ou desconhecido */
    public static Offer.Destination classify(String dest, Rules rules) {
        if (dest == null || dest.trim().isEmpty()) return Offer.Destination.UNKNOWN;
        if (isInside(dest, rules)) return Offer.Destination.INSIDE;
        if (matchPlace(dest, rules) != null) return Offer.Destination.ALLOWED;
        String n = norm(dest);
        for (String w : rules.outsideWords) {
            String k = norm(w).trim();
            if (!k.isEmpty() && n.contains(k)) return Offer.Destination.OUTSIDE;
        }
        return Offer.Destination.UNKNOWN;
    }

    private static boolean isInside(String dest, Rules rules) {
        if (dest == null) return false;
        for (String w : rules.insideWords) if (allWords(dest, w)) return true;
        return false;
    }

    /** @return o local liberado encontrado no destino, ou null */
    public static String matchPlace(String dest, Rules rules) {
        if (dest == null) return null;
        for (String p : rules.allowedPlaces) if (allWords(dest, p)) return p.trim();
        return null;
    }

    /**
     * Todas as palavras da chave aparecem no texto, em qualquer ordem, sem acento.
     * "Shopping Maracanau" casa com "North Shopping Maracanaú" e "Maracanaú Shopping".
     */
    static boolean allWords(String text, String key) {
        if (key == null) return false;
        String t = " " + norm(text).replaceAll("[^a-z0-9]+", " ") + " ";
        boolean any = false;
        for (String w : norm(key).split("[^a-z0-9]+")) {
            if (w.length() < 2 || STOP_WORDS.contains(w)) continue;
            any = true;
            if (!t.contains(w)) return false;
        }
        return any;
    }

    private static final java.util.Set<String> STOP_WORDS =
            new java.util.HashSet<>(java.util.Arrays.asList("de", "da", "do", "das", "dos", "e"));

    /** "1.234,56" -> 1234.56 | "8,3" -> 8.3 | "8.3" -> 8.3 */
    public static double parseNumber(String s) {
        s = s.trim();
        if (s.contains(",")) s = s.replace(".", "").replace(',', '.');
        return Double.parseDouble(s);
    }

    static String norm(String s) {
        String d = Normalizer.normalize(s, Normalizer.Form.NFD);
        return d.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
