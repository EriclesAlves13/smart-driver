package com.smartdriver.calc;

/** Parametros do calculo. Combustivel e informado pelo motorista. */
public class Rules {
    public double minPerKm = 2.00;
    public double minPerTrip = 8.00;
    public double kmPerLiter;      // consumo do carro (km/L)
    public double fuelPrice;       // preco do litro (R$)
    /** cidade onde o motorista quer rodar */
    public String city = "Maranguape";
    /** outras cidades onde o motorista tambem aceita corridas (ex: "Maracanau") */
    public String[] extraCities = {};
    /** palavras que indicam destino na cidade (nome da cidade, bairros) */
    public String[] insideWords = {"maranguape"};
    /** locais FORA da cidade que o motorista aceita (ex: "Shopping Maracanau") */
    public String[] allowedPlaces = {};
    /** cidades da regiao = fora (a cidade escolhida sempre vence, pois e verificada antes) */
    public String[] outsideWords = {
            "maranguape", "fortaleza", "maracanau", "caucaia", "pacatuba", "palmacia", "caridade",
            "pentecoste", "guaiuba", "aquiraz", "eusebio", "itaitinga", "horizonte"
    };

    /** "Maranguape" ou "Maranguape, Maracanaú" */
    public String citiesLabel() {
        StringBuilder sb = new StringBuilder(city);
        for (String c : extraCities) sb.append(", ").append(c);
        return sb.toString();
    }

    public boolean fuelConfigured() {
        return kmPerLiter > 0 && fuelPrice > 0;
    }
}
