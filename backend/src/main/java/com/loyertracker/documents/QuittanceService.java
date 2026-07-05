package com.loyertracker.documents;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.bailleur.Bailleur;
import com.loyertracker.bailleur.BailleurRepository;
import com.loyertracker.baux.Bail;
import com.loyertracker.baux.BailRepository;
import com.loyertracker.baux.Devise;
import com.loyertracker.baux.Money;
import com.loyertracker.biens.Bien;
import com.loyertracker.biens.BienRepository;
import com.loyertracker.paiements.Paiement;
import com.loyertracker.paiements.PaiementRepository;
import com.loyertracker.paiements.StatutPaiement;
import com.loyertracker.securite.TenantContext;

/**
 * Production à la volée de l'avis d'échéance à partir d'un loyer {@code (bien, periode)}. Aucun
 * document n'est stocké (arbitrage C — maintenu pour l'avis d'échéance ; les quittances relèvent
 * depuis EP-14 de {@link com.loyertracker.quittances.QuittanceCertifieeService}, ADR-15 D1).
 *
 * <p>Cloisonnement RLS (ADR-01) : on positionne le contexte tenant via
 * {@link TenantContext#activerDepuisBien(UUID)} (bailleur propriétaire <em>ou</em> gestionnaire
 * affecté, cohérent avec l'accès aux paiements). Toutes les lectures qui suivent sont donc
 * restreintes au tenant du bien.</p>
 */
@Service
public class QuittanceService {

    private static final DateTimeFormatter MOIS_ANNEE =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final TenantContext tenant;
    private final PaiementRepository paiements;
    private final BailRepository baux;
    private final BienRepository biens;
    private final BailleurRepository bailleurs;
    private final DocumentHtmlBuilder html;
    private final PdfRenderer pdf;

    public QuittanceService(TenantContext tenant, PaiementRepository paiements, BailRepository baux,
            BienRepository biens, BailleurRepository bailleurs, DocumentHtmlBuilder html,
            PdfRenderer pdf) {
        this.tenant = tenant;
        this.paiements = paiements;
        this.baux = baux;
        this.biens = biens;
        this.bailleurs = bailleurs;
        this.html = html;
        this.pdf = pdf;
    }

    @Transactional(readOnly = true)
    public byte[] avisEcheance(UUID bienId, String periode) {
        return pdf.rendre(html.construire(assembler(bienId, periode, TypeDocument.AVIS_ECHEANCE)));
    }

    private DonneesDocument assembler(UUID bienId, String periode, TypeDocument type) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);

        Paiement paiement = paiements.findByBienIdAndPeriode(bienId, periode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun loyer pour cette période."));

        boolean recu = paiement.getStatut() == StatutPaiement.RECU;
        if (type == TypeDocument.AVIS_ECHEANCE && recu) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le loyer est soldé : aucun avis d'échéance à émettre.");
        }

        Bail bail = baux.findById(paiement.getBailId()).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Bail introuvable."));
        Bien bien = biens.findById(bienId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Bien introuvable."));
        Bailleur bailleur = bailleurs.findById(bailleurId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bailleur introuvable."));

        if (bailleur.getAdresse() == null || bailleur.getAdresse().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Renseignez votre adresse dans « Mon profil » avant d'émettre un document.");
        }

        Devise devise = bail.getDevise();
        BigDecimal montant =
                type == TypeDocument.QUITTANCE ? paiement.getMontantRecu() : paiement.getResteDu();

        return new DonneesDocument(type,
                (bailleur.getPrenom() + " " + bailleur.getNom()).trim(), bailleur.getAdresse(),
                bail.getLocataireNom(), bien.getAdresse(), libellePeriode(periode),
                Money.of(bail.getLoyerHc(), devise), Money.of(bail.getProvisionCharges(), devise),
                Money.of(bail.getLoyerCc(), devise), Money.of(montant, devise),
                LocalDate.now(), paiement.getDateExigibilite());
    }

    private static String libellePeriode(String periode) {
        // La période provient d'un paiement existant : format CHAR(7) 'YYYY-MM' garanti en base.
        return YearMonth.parse(periode).format(MOIS_ANNEE);
    }
}
