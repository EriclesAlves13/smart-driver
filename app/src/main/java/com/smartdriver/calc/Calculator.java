package com.smartdriver.calc;

import java.util.ArrayList;
import java.util.List;

/** Calcula rentabilidade e decide ACEITAR / RECUSAR. Nunca supoe dados ausentes. */
public final class Calculator {

    public enum Verdict { ACCEPT, REJECT, INCOMPLETE, CHECK_DESTINATION }

    public static class Result {
        public Verdict verdict;
        public Double perKm;
        public Double fuelCost;
        public Double profit;
        public final List<String> reasons = new ArrayList<>();   // motivos de recusa
        public final List<String> missing = new ArrayList<>();   // dados nao identificados
    }

    private static final java.util.Locale BR = new java.util.Locale("pt", "BR");

    private Calculator() {}

    public static Result evaluate(Offer o, Rules r) {
        Result res = new Result();
        Double km = o.totalKm();

        if (o.price == null) res.missing.add("valor");
        if (o.pickupKm == null) res.missing.add("km até o passageiro");
        if (o.tripKm == null) res.missing.add("km da viagem");
        if (o.destination == Offer.Destination.UNKNOWN) res.missing.add("destino");
        if (!r.fuelConfigured()) res.missing.add("consumo/preço do combustível (configuração)");

        if (o.price != null && km != null && km > 0) res.perKm = o.price / km;
        if (km != null && r.fuelConfigured()) res.fuelCost = km / r.kmPerLiter * r.fuelPrice;
        if (o.price != null && res.fuelCost != null) res.profit = o.price - res.fuelCost;

        // Regras que ja podem reprovar mesmo com dados parciais
        if (res.perKm != null && res.perKm < r.minPerKm)
            res.reasons.add(String.format(BR, "R$ %.2f/km — abaixo de R$ %.2f", res.perKm, r.minPerKm));
        if (o.price != null && o.price < r.minPerTrip)
            res.reasons.add(String.format(BR, "Valor R$ %.2f — abaixo de R$ %.2f", o.price, r.minPerTrip));
        if (o.destination == Offer.Destination.OUTSIDE)
            res.reasons.add("Destino fora de " + r.city
                    + (r.allowedPlaces.length > 0 ? " e não é local liberado" : ""));
        if (res.profit != null && res.profit <= 0)
            res.reasons.add("Sem lucro após o combustível");

        if (!res.reasons.isEmpty()) {
            res.verdict = Verdict.REJECT;
        } else if (o.price == null || km == null || !r.fuelConfigured()) {
            res.verdict = Verdict.INCOMPLETE;
        } else if (o.destination == Offer.Destination.UNKNOWN) {
            res.verdict = Verdict.CHECK_DESTINATION;
        } else {
            res.verdict = Verdict.ACCEPT;
        }
        return res;
    }
}
