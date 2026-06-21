package com.loyertracker.documents;

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
import com.loyertracker.biens.Bien;
import com.loyertracker.biens.BienRepository;
import com.loyertracker.paiements.Paiement;
import com.loyertracker.paiements.PaiementRepository;
import com.loyertracker.paiements.StatutPaiement;
import com.loyertracker.securite.TenantContext;

/**
 * Production à la volée des documents locatifs (quittance de loyer, avis d'échéance) à partir d'un
 * loyer {@code (bien, periode)}. Aucun document n'est stocké (arbitrage C).
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
    public byte[] quittance(UUID bienId, String periode) {
        return pdf.rendre(html.construire(assembler(bienId, periode, TypeDocument.QUITTANCE)));
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
        if (type == TypeDocument.QUITTANCE && !recu) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La quittance n'est disponible que pour un loyer intégralement reçu.");
        }
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

        java.math.BigDecimal montant =
                type == TypeDocument.QUITTANCE ? paiement.getMontantRecu() : paiement.getResteDu();

        return new DonneesDocument(type,
                (bailleur.getPrenom() + " " + bailleur.getNom()).trim(), bailleur.getAdresse(),
                bail.getLocataireNom(), bien.getAdresse(), libellePeriode(periode),
                bail.getLoyerHc(), bail.getProvisionCharges(), bail.getLoyerCc(),
                montant, LocalDate.now(), paiement.getDateExigibilite());
    }

    private static String libellePeriode(String periode) {
        try {
            return YearMonth.parse(periode).format(MOIS_ANNEE);
        } catch (java.time.format.DateTimeParseException e) {
            // Période déjà validée en amont (format CHAR(7) 'YYYY-MM') ; repli défensif.
            return periode;
        }
    }
}
