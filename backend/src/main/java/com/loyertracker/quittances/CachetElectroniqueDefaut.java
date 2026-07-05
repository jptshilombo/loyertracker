package com.loyertracker.quittances;

import org.springframework.stereotype.Component;

/**
 * Scellement par défaut : le cachet électronique est visuel (mention et empreintes portées par le
 * gabarit), les octets du PDF sont l'exemplaire officiel tel quel. Voir {@link ScellementQuittance}
 * pour l'évolution PAdES.
 */
@Component
public class CachetElectroniqueDefaut implements ScellementQuittance {

    @Override
    public byte[] sceller(byte[] pdf) {
        return pdf;
    }
}
