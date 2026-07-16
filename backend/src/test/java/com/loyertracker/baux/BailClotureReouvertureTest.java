package com.loyertracker.baux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gardes métier de la clôture/réouverture d'un bail (US-115/116, ADR-17 K1/K2/K5). Aucun contexte
 * Spring : ces règles vivent sur l'entité, comme {@code Locataire.archiver()}/{@code restaurer()}.
 */
class BailClotureReouvertureTest {

    private Bail bail() {
        return new Bail(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Locataire Test", "loc@test.local", new BigDecimal("750.00"),
                new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                Devise.EUR);
    }

    @Test
    void cloturerPasseLeStatutACLOSEtFigeLaDateClotureSansToucherDateFin() {
        Bail b = bail();
        LocalDate dateCloture = LocalDate.of(2026, 6, 15);

        b.cloturer(dateCloture);

        assertThat(b.getStatut()).isEqualTo(StatutBail.CLOS);
        assertThat(b.getDateClotureEffective()).isEqualTo(dateCloture);
        assertThat(b.getDateFin()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void cloturerUnBailDejaClosLeve409() {
        Bail b = bail();
        b.cloturer(LocalDate.of(2026, 6, 15));

        assertThatThrownBy(() -> b.cloturer(LocalDate.of(2026, 7, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void rouvrirRemetActifEtEfaceLaDateCloture() {
        Bail b = bail();
        b.cloturer(LocalDate.of(2026, 6, 15));

        b.rouvrir();

        assertThat(b.getStatut()).isEqualTo(StatutBail.ACTIF);
        assertThat(b.getDateClotureEffective()).isNull();
    }

    @Test
    void rouvrirUnBailNonClosLeve409() {
        Bail b = bail();

        assertThatThrownBy(b::rouvrir)
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }
}
