package com.smartdriver.calc;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Abre as telas e desenha a janela flutuante em todos os resultados (sem celular). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UiSmokeTest {

    private static final String OFFER =
            "R$ 25,90\n4 min (1,5 km) de distância\nRua A, Centro\n"
            + "Viagem de 18 min (8,5 km)\nRua B, Maranguape - CE\nAceitar\n";

    @Before public void config() {
        Context c = RuntimeEnvironment.getApplication();
        Prefs.save(c, 11.5f, 6.19f, "Maranguape", "", "Shopping Maracanaú");
        Session.current = null;
    }

    @Test public void mainScreenOpens() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        // sem permissao de janela flutuante (simulador), o botao pede a permissao primeiro
        assertEquals("Permitir janela flutuante", ((TextView) a.findViewById(R.id.btnMain)).getText().toString());
        assertEquals("● Falta permissão", ((TextView) a.findViewById(R.id.pill)).getText().toString());
        assertTrue(((android.view.ViewGroup) a.findViewById(R.id.places)).getChildCount() >= 2); // chip + adicionar
        assertEquals(1, ((android.view.ViewGroup) a.findViewById(R.id.cities)).getChildCount()); // so "+ Adicionar"
        assertEquals("2,00", ((TextView) a.findViewById(R.id.minKm)).getText().toString());
        assertEquals("8,00", ((TextView) a.findViewById(R.id.minTrip)).getText().toString());
    }

    @Test public void manualScreenWithOfferCalculates() {
        Session.current = OfferParser.parse(OFFER, Prefs.load(RuntimeEnvironment.getApplication()));
        ManualActivity a = Robolectric.buildActivity(ManualActivity.class).setup().get();
        assertEquals("lido da tela", ((TextView) a.findViewById(R.id.tagPrice)).getText().toString());
        a.findViewById(R.id.btnCalc).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.resultCard).getVisibility());
        assertEquals("ACEITAR", ((TextView) a.findViewById(R.id.verdict)).getText().toString());
    }

    @Test public void manualScreenEmpty() {
        ManualActivity a = Robolectric.buildActivity(ManualActivity.class).setup().get();
        a.findViewById(R.id.btnCalc).performClick();
        assertEquals("DADOS INCOMPLETOS", ((TextView) a.findViewById(R.id.verdict)).getText().toString());
    }

    @Test public void overlayRendersEveryVerdict() {
        Application app = RuntimeEnvironment.getApplication();
        Rules r = Prefs.load(app);
        OverlayController oc = new OverlayController(app);
        oc.show();
        String[] cases = {
                OFFER,                                                  // aceitar
                OFFER.replace("R$ 25,90", "R$ 12,00"),                 // recusar
                OFFER.replace(", Maranguape - CE", ", Parque Iracema"), // confirmar destino
                "R$ 20,00\n10 min (5 km)\nRua B\n",                     // incompleto
                OFFER.replace("Rua B, Maranguape - CE", "North Shopping Maracanaú") // liberado
        };
        String[] expected = {"ACEITAR", "RECUSAR", "CONFIRME O DESTINO", "DADOS INCOMPLETOS", "ACEITAR"};
        for (int i = 0; i < cases.length; i++) {
            Offer o = OfferParser.parse(cases[i], r);
            Session.current = o;
            Calculator.Result res = Calculator.evaluate(o, r);
            assertEquals(expected[i], Render.verdictLabel(res.verdict));
            oc.showResult(o, res, 400);
        }
        oc.markStale();
        oc.setStatus("teste", true);
        oc.hide();
    }
}
