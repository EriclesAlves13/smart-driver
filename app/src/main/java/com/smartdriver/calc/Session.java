package com.smartdriver.calc;

/** Estado em memoria da oferta atual (nada e salvo em disco). */
public final class Session {
    private Session() {}

    /** oferta exibida agora (pode conter dados manuais) */
    public static volatile Offer current;
    /** assinatura da ultima oferta lida da tela; evita sobrescrever uma edicao manual */
    public static volatile String lastAutoSig;
}
