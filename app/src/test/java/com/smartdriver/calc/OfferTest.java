package com.smartdriver.calc;

import static org.junit.Assert.*;

import org.junit.Test;

public class OfferTest {

    private Rules rules() {
        Rules r = new Rules();
        r.kmPerLiter = 10;
        r.fuelPrice = 6.00;
        return r;
    }

    // Texto no formato tipico do cartao de oferta (como o OCR devolve)
    private static final String OFFER_INSIDE =
            "UberX\n" +
            "R$ 25,90\n" +
            "★ 4,95\n" +
            "4 min (1,5 km) de distância\n" +
            "Rua Coronel Antônio Botelho, Centro\n" +
            "Viagem de 18 min (8,5 km)\n" +
            "Rua Dr. Argeu Braga Herbster, Maranguape - CE\n" +
            "Aceitar\n";

    @Test public void parsesCompleteOfferAndAccepts() {
        Offer o = OfferParser.parse(OFFER_INSIDE, rules());
        assertNotNull(o);
        assertEquals(25.90, o.price, 1e-9);
        assertEquals(1.5, o.pickupKm, 1e-9);
        assertEquals(8.5, o.tripKm, 1e-9);
        assertEquals(4, (int) o.pickupMin);
        assertEquals(18, (int) o.tripMin);
        assertEquals(Offer.Destination.INSIDE, o.destination);
        assertTrue(o.destinationText.contains("Maranguape"));

        Calculator.Result r = Calculator.evaluate(o, rules());
        assertEquals(2.59, r.perKm, 1e-9);          // 25,90 / 10 km
        assertEquals(6.00, r.fuelCost, 1e-9);       // 10 km / 10 km/L * 6,00
        assertEquals(19.90, r.profit, 1e-9);
        assertEquals(Calculator.Verdict.ACCEPT, r.verdict);
        assertFalse(o.anyManual());
    }

    @Test public void rejectsLowPerKm() {
        String t = OFFER_INSIDE.replace("R$ 25,90", "R$ 15,00");   // 1,50/km
        Calculator.Result r = Calculator.evaluate(OfferParser.parse(t, rules()), rules());
        assertEquals(Calculator.Verdict.REJECT, r.verdict);
        assertTrue(r.reasons.get(0).contains("/km"));
    }

    @Test public void rejectsBelowMinimumFare() {
        String t = "R$ 7,50\n2 min (0,5 km) de distância\nRua A\nViagem de 5 min (2,0 km)\nRua B, Maranguape\n";
        Calculator.Result r = Calculator.evaluate(OfferParser.parse(t, rules()), rules());
        assertEquals(Calculator.Verdict.REJECT, r.verdict);
        assertTrue(String.join(" ", r.reasons).contains("R$ 8,00"));
    }

    @Test public void rejectsOutsideDestination() {
        String t = OFFER_INSIDE.replace("Maranguape - CE", "Maracanaú - CE");
        Offer o = OfferParser.parse(t, rules());
        assertEquals(Offer.Destination.OUTSIDE, o.destination);
        assertEquals(Calculator.Verdict.REJECT, Calculator.evaluate(o, rules()).verdict);
    }

    @Test public void unknownDestinationIsNotGuessed() {
        String t = OFFER_INSIDE.replace(", Maranguape - CE", ", Parque São João");
        Offer o = OfferParser.parse(t, rules());
        assertEquals(Offer.Destination.UNKNOWN, o.destination);
        Calculator.Result r = Calculator.evaluate(o, rules());
        assertEquals(Calculator.Verdict.CHECK_DESTINATION, r.verdict);
        assertTrue(r.missing.contains("destino"));
    }

    @Test public void neighborhoodKeywordMarksInside() {
        Rules r = rules();
        r.insideWords = new String[]{"maranguape", "Parque São João"};
        String t = OFFER_INSIDE.replace(", Maranguape - CE", ", Parque Sao Joao");
        assertEquals(Offer.Destination.INSIDE, OfferParser.parse(t, r).destination);
    }

    @Test public void ignoresBonusAndPerKmPrices() {
        String t = "+R$ 3,00 incluído\nR$ 1,80/km\nR$ 20,00\n3 min (1 km) de distância\nRua A\n"
                + "Viagem de 10 min (5 km)\nRua B, Maranguape\n";
        assertEquals(20.00, OfferParser.parse(t, rules()).price, 1e-9);
    }

    @Test public void ocrReadsDollarAsS() {
        String t = OFFER_INSIDE.replace("R$ 25,90", "RS 25,90");
        assertEquals(25.90, OfferParser.parse(t, rules()).price, 1e-9);
    }

    @Test public void metersAndHours() {
        String t = "R$ 90,00\n3 min (800 m) de distância\nRua A\nViagem de 1 h 5 min (42,3 km)\nFortaleza\n";
        Offer o = OfferParser.parse(t, rules());
        assertEquals(0.8, o.pickupKm, 1e-9);
        assertEquals(42.3, o.tripKm, 1e-9);
        assertEquals(65, (int) o.tripMin);
    }

    @Test public void singleUnlabeledLegLeavesDistanceMissing() {
        String t = "R$ 20,00\n10 min (5 km)\nRua B\n";
        Offer o = OfferParser.parse(t, rules());
        assertNull(o.pickupKm);
        assertNull(o.tripKm);
        assertEquals(Calculator.Verdict.INCOMPLETE, Calculator.evaluate(o, rules()).verdict);
    }

    @Test public void notAnOffer() {
        assertNull(OfferParser.parse("Ganhos da semana\nR$ 540,00\n", rules()));
        assertNull(OfferParser.parse("Você está online\n", rules()));
    }

    @Test public void missingFuelConfigIsIncomplete() {
        Rules r = new Rules();   // sem consumo/preco
        Calculator.Result res = Calculator.evaluate(OfferParser.parse(OFFER_INSIDE, r), r);
        assertEquals(Calculator.Verdict.INCOMPLETE, res.verdict);
        assertNull(res.fuelCost);
    }

    @Test public void manualFlag() {
        Offer o = OfferParser.parse(OFFER_INSIDE, rules());
        o.price = 30.0;
        o.priceSrc = Offer.Source.MANUAL;
        assertTrue(o.anyManual());
    }

    @Test public void numberFormats() {
        assertEquals(1234.56, OfferParser.parseNumber("1.234,56"), 1e-9);
        assertEquals(8.3, OfferParser.parseNumber("8,3"), 1e-9);
        assertEquals(8.3, OfferParser.parseNumber("8.3"), 1e-9);
    }

    // ---------- locais liberados fora da cidade ----------

    private Rules withShopping() {
        Rules r = rules();
        r.allowedPlaces = new String[]{"Shopping Maracanaú"};
        return r;
    }

    private String toDest(String dest) {
        return OFFER_INSIDE.replace("Rua Dr. Argeu Braga Herbster, Maranguape - CE", dest);
    }

    @Test public void allowedPlaceOutsideCityIsAccepted() {
        Offer o = OfferParser.parse(toDest("North Shopping Maracanaú - Av. Central, Maracanaú - CE"), withShopping());
        assertEquals(Offer.Destination.ALLOWED, o.destination);
        assertEquals("Shopping Maracanaú", o.matchedPlace);
        assertEquals(Calculator.Verdict.ACCEPT, Calculator.evaluate(o, withShopping()).verdict);
    }

    @Test public void allowedPlaceWordOrderAndAccentsDoNotMatter() {
        Offer o = OfferParser.parse(toDest("MARACANAU SHOPPING"), withShopping());
        assertEquals(Offer.Destination.ALLOWED, o.destination);
    }

    @Test public void sameCityButNotTheShoppingIsRejected() {
        Offer o = OfferParser.parse(toDest("Rua 10, Jereissati, Maracanaú - CE"), withShopping());
        assertEquals(Offer.Destination.OUTSIDE, o.destination);
        Calculator.Result r = Calculator.evaluate(o, withShopping());
        assertEquals(Calculator.Verdict.REJECT, r.verdict);
        assertTrue(r.reasons.get(0).contains("local liberado"));
    }

    @Test public void allowedPlaceStillNeedsPriceRules() {
        String t = toDest("North Shopping Maracanaú").replace("R$ 25,90", "R$ 12,00"); // 1,20/km
        Calculator.Result r = Calculator.evaluate(OfferParser.parse(t, withShopping()), withShopping());
        assertEquals(Calculator.Verdict.REJECT, r.verdict);
    }

    @Test public void configurableCity() {
        Rules r = rules();
        r.city = "Maracanaú";
        r.insideWords = new String[]{"Maracanaú"};
        Offer o = OfferParser.parse(toDest("Rua 10, Jereissati, Maracanaú - CE"), r);
        assertEquals(Offer.Destination.INSIDE, o.destination);
        Offer o2 = OfferParser.parse(OFFER_INSIDE, r);   // destino Maranguape
        assertEquals(Offer.Destination.OUTSIDE, o2.destination);
        assertTrue(Calculator.evaluate(o2, r).reasons.get(0).contains("Maracanaú"));
    }

    @Test public void extraAllowedCity() {
        Rules r = rules();
        r.extraCities = new String[]{"Maracanaú"};
        r.insideWords = new String[]{"maranguape", "Maracanaú"};
        Offer o = OfferParser.parse(toDest("Rua 10, Jereissati, Maracanaú - CE"), r);
        assertEquals(Offer.Destination.INSIDE, o.destination);
        Offer o2 = OfferParser.parse(toDest("Av. Bezerra de Menezes, Fortaleza - CE"), r);
        assertEquals(Offer.Destination.OUTSIDE, o2.destination);
        assertTrue(Calculator.evaluate(o2, r).reasons.get(0).contains("Maranguape, Maracanaú"));
    }

    @Test public void customMinimums() {
        Rules r = rules();
        r.minPerKm = 3.00;   // oferta padrao = R$ 2,59/km
        Calculator.Result res = Calculator.evaluate(OfferParser.parse(OFFER_INSIDE, r), r);
        assertEquals(Calculator.Verdict.REJECT, res.verdict);
        r.minPerKm = 2.00;
        r.minPerTrip = 30.00; // oferta padrao = R$ 25,90
        res = Calculator.evaluate(OfferParser.parse(OFFER_INSIDE, r), r);
        assertEquals(Calculator.Verdict.REJECT, res.verdict);
        assertTrue(res.reasons.get(0).contains("30,00"));
    }
}
