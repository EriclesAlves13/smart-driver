package com.smartdriver.calc;

/**
 * Dados de uma oferta. Cada campo pode ser null (nao identificado).
 * Cada campo guarda a origem: lido da tela (AUTO) ou digitado (MANUAL).
 */
public class Offer {
    public enum Source { AUTO, MANUAL }
    /** ALLOWED = fora da cidade, mas e um local liberado pelo motorista */
    public enum Destination { INSIDE, ALLOWED, OUTSIDE, UNKNOWN }

    public Double price;          // valor da corrida (R$)
    public Double pickupKm;       // distancia ate o passageiro
    public Double tripKm;         // distancia da viagem
    public Integer pickupMin;
    public Integer tripMin;
    public String destinationText;               // texto do destino lido na tela
    public Destination destination = Destination.UNKNOWN;
    /** local liberado reconhecido no destino (quando destination == ALLOWED) */
    public String matchedPlace;

    public Source priceSrc = Source.AUTO;
    public Source pickupSrc = Source.AUTO;
    public Source tripSrc = Source.AUTO;
    public Source destSrc = Source.AUTO;

    /** momento (ms, SystemClock.elapsedRealtime) em que a tela foi capturada */
    public long capturedAt;

    public boolean anyManual() {
        return (price != null && priceSrc == Source.MANUAL)
                || (pickupKm != null && pickupSrc == Source.MANUAL)
                || (tripKm != null && tripSrc == Source.MANUAL)
                || (destination != Destination.UNKNOWN && destSrc == Source.MANUAL);
    }

    public Double totalKm() {
        if (pickupKm == null || tripKm == null) return null;
        return pickupKm + tripKm;
    }

    /** Assinatura para nao reprocessar a mesma oferta a cada quadro. */
    public String signature() {
        return price + "|" + pickupKm + "|" + tripKm + "|" + destination + "|" + destinationText;
    }

    public Offer copy() {
        Offer o = new Offer();
        o.price = price; o.pickupKm = pickupKm; o.tripKm = tripKm;
        o.pickupMin = pickupMin; o.tripMin = tripMin;
        o.destinationText = destinationText; o.destination = destination; o.matchedPlace = matchedPlace;
        o.priceSrc = priceSrc; o.pickupSrc = pickupSrc; o.tripSrc = tripSrc; o.destSrc = destSrc;
        o.capturedAt = capturedAt;
        return o;
    }
}
