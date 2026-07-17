package com.loyertracker.quittances;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.audit.AuditService;
import com.loyertracker.bailleur.Bailleur;
import com.loyertracker.bailleur.BailleurRepository;
import com.loyertracker.baux.Bail;
import com.loyertracker.baux.BailRepository;
import com.loyertracker.baux.Devise;
import com.loyertracker.baux.Money;
import com.loyertracker.biens.Bien;
import com.loyertracker.biens.BienRepository;
import com.loyertracker.documents.DocumentHtmlBuilder;
import com.loyertracker.documents.PdfRenderer;
import com.loyertracker.garanties.GarantieMovement;
import com.loyertracker.garanties.GarantieMovementRepository;
import com.loyertracker.locataires.Locataire;
import com.loyertracker.locataires.LocataireRepository;
import com.loyertracker.paiements.Paiement;
import com.loyertracker.paiements.PaiementRepository;
import com.loyertracker.paiements.StatutPaiement;
import com.loyertracker.patrimoine.Patrimoine;
import com.loyertracker.patrimoine.PatrimoineRepository;
import com.loyertracker.securite.TenantContext;

import jakarta.persistence.EntityManager;

/**
 * Émission des quittances certifiées (ADR-15, US-99/100/101). Contrairement à l'ancienne
 * génération à la volée (arbitrage C, conservé pour l'avis d'échéance), chaque quittance est un
 * exemplaire officiel persistant : numéro permanent par bailleur+année (K1), versions chaînées,
 * payload canonique haché ({@code content_hash}), PDF stocké haché ({@code pdf_hash}), token
 * HMAC dans le QR de vérification.
 *
 * <p><strong>Idempotence</strong> : redemander la quittance d'un loyer inchangé renvoie
 * l'exemplaire officiel existant (aucune nouvelle version) ; toute différence métier
 * (empreinte métier) produit une version N+1 et marque l'ancienne {@code REMPLACEE}.</p>
 */
@Service
public class QuittanceCertifieeService {

    private static final DateTimeFormatter MOIS_ANNEE =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final TenantContext tenant;
    private final PaiementRepository paiements;
    private final BailRepository baux;
    private final BienRepository biens;
    private final BailleurRepository bailleurs;
    private final PatrimoineRepository patrimoines;
    private final GarantieMovementRepository mouvementsGarantie;
    private final QuittanceRepository quittances;
    private final LocataireRepository locataires;
    private final TokenQuittanceService tokens;
    private final QrCodeQuittance qr;
    private final DocumentHtmlBuilder html;
    private final PdfRenderer pdf;
    private final ThemeQuittanceProvider themes;
    private final ScellementQuittance scellement;
    private final AuditService audit;
    private final EntityManager em;
    private final String urlBaseVerification;

    public QuittanceCertifieeService(TenantContext tenant, PaiementRepository paiements,
            BailRepository baux, BienRepository biens, BailleurRepository bailleurs,
            PatrimoineRepository patrimoines, GarantieMovementRepository mouvementsGarantie,
            QuittanceRepository quittances, LocataireRepository locataires,
            TokenQuittanceService tokens, QrCodeQuittance qr,
            DocumentHtmlBuilder html, PdfRenderer pdf, ThemeQuittanceProvider themes,
            ScellementQuittance scellement, AuditService audit, EntityManager em,
            @Value("${quittances.url-base-verification:https://loyertracker.loyerpro.org}")
            String urlBaseVerification) {
        this.tenant = tenant;
        this.paiements = paiements;
        this.baux = baux;
        this.biens = biens;
        this.bailleurs = bailleurs;
        this.patrimoines = patrimoines;
        this.mouvementsGarantie = mouvementsGarantie;
        this.quittances = quittances;
        this.locataires = locataires;
        this.tokens = tokens;
        this.qr = qr;
        this.html = html;
        this.pdf = pdf;
        this.themes = themes;
        this.scellement = scellement;
        this.audit = audit;
        this.em = em;
        this.urlBaseVerification = urlBaseVerification;
    }

    /**
     * Émet (ou renvoie, ou ré-émet en version N+1) la quittance certifiée du loyer
     * {@code (bien, periode)} et retourne l'exemplaire officiel PDF.
     */
    @Transactional
    public byte[] emettre(UUID bienId, String periode, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);

        Paiement paiement = paiements.findByBienIdAndPeriode(bienId, periode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun loyer pour cette période."));
        if (paiement.getStatut() != StatutPaiement.RECU) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La quittance n'est disponible que pour un loyer intégralement reçu.");
        }

        DonneesAssemblees donnees = assembler(bailleurId, paiement, bienId, periode);
        Optional<Quittance> active =
                quittances.findByPaiementIdAndStatut(paiement.getId(), StatutQuittance.EMISE);

        // Idempotence : loyer inchangé -> l'exemplaire officiel existant fait foi.
        if (active.isPresent()
                && active.get().getEmpreinteMetier().equals(donnees.empreinteMetier())) {
            return active.get().getPdf();
        }

        String numero;
        int version;
        if (active.isPresent()) {
            numero = active.get().getNumero();
            version = active.get().getVersion() + 1;
        } else {
            numero = prochainNumero(bailleurId);
            version = 1;
        }

        UUID id = UUID.randomUUID();
        DonneesQuittanceCertifiee certifiees = donnees.avecIdentite(numero, version);
        String contenu = ContenuQuittance.canonique(certifiees);
        String contentHash = ContenuQuittance.sha256Hex(contenu);
        String token = tokens.generer(id, version, contentHash);
        String url = urlBaseVerification + "/verify/receipt/" + id + "?token=" + token
                + "&v=" + version;

        byte[] exemplaire = scellement.sceller(pdf.rendre(html.construireQuittanceCertifiee(
                certifiees, themes.themePour(bailleurId), url, contentHash, qr.dataUri(url))));

        // L'index partiel V22 (une seule EMISE par loyer) impose l'ordre : basculer l'ancienne
        // version AVANT d'insérer la nouvelle, puis chaîner remplacee_par (FK vers la nouvelle).
        if (active.isPresent()) {
            active.get().remplacerPar(null);
            quittances.flush();
        }
        Quittance quittance = new Quittance(id, bailleurId, paiement.getId(), numero, version,
                contenu, contentHash, ContenuQuittance.sha256Hex(exemplaire), exemplaire,
                donnees.empreinteMetier(), tokens.kid());
        quittances.saveAndFlush(quittance);
        if (active.isPresent()) {
            active.get().remplacerPar(id);
        }

        audit.enregistrer(authentication, bailleurId,
                version == 1 ? "EMETTRE_QUITTANCE" : "REEMETTRE_QUITTANCE", "quittance", id);
        return exemplaire;
    }

    /**
     * Annule une quittance émise (US-99). Le numéro reste consommé à jamais ; la page de
     * vérification affichera le statut {@code ANNULEE}. Réservé au bailleur propriétaire.
     */
    @Transactional
    public void annuler(UUID quittanceId, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(
                ((org.springframework.security.oauth2.jwt.Jwt) authentication.getPrincipal())
                        .getSubject());
        Quittance quittance = quittances.findById(quittanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Quittance introuvable."));
        if (quittance.getStatut() != StatutQuittance.EMISE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une quittance émise peut être annulée.");
        }
        quittance.annuler();
        audit.enregistrer(authentication, bailleurId, "ANNULER_QUITTANCE", "quittance",
                quittanceId);
    }

    /**
     * Prochain numéro {@code QT-YYYY-NNNNNN} du bailleur courant (kickoff K1). Compteur V22
     * strictement croissant sous verrou de ligne : un numéro consommé n'est jamais réutilisé,
     * même si la transaction d'émission échoue ensuite (trou de séquence assumé, comme toute
     * numérotation de facturier).
     */
    private String prochainNumero(UUID bailleurId) {
        int annee = LocalDate.now().getYear();
        Number prochain = (Number) em.createNativeQuery("""
                INSERT INTO quittance_numerotation (bailleur_id, annee)
                VALUES (CAST(:b AS uuid), :annee)
                ON CONFLICT (bailleur_id, annee)
                DO UPDATE SET prochain = quittance_numerotation.prochain + 1
                RETURNING prochain
                """)
                .setParameter("b", bailleurId.toString())
                .setParameter("annee", annee)
                .getSingleResult();
        return "QT-%d-%06d".formatted(annee, prochain.intValue());
    }

    private DonneesAssemblees assembler(UUID bailleurId, Paiement paiement, UUID bienId,
            String periode) {
        Bail bail = baux.findById(paiement.getBailId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bail introuvable."));
        Bien bien = biens.findById(bienId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bien introuvable."));
        Bailleur bailleur = bailleurs.findById(bailleurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bailleur introuvable."));
        Patrimoine patrimoine = patrimoines.findById(bien.getPatrimoineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Patrimoine introuvable."));
        Locataire locataire = locataires.findById(bail.getLocataireId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Locataire introuvable."));

        if (bailleur.getAdresse() == null || bailleur.getAdresse().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Renseignez votre adresse dans « Mon profil » avant d'émettre un document.");
        }

        Devise devise = bail.getDevise();
        // Le modèle ne trace pas le moyen de règlement : libellé dérivé, jamais inventé (le seul
        // mode connu avec certitude est la retenue sur garantie, lien V21/US-95).
        Money garantieRetenue = null;
        String modePaiement = "Non renseigné";
        if (paiement.getGarantieMovementId() != null) {
            GarantieMovement mouvement = mouvementsGarantie
                    .findById(paiement.getGarantieMovementId()).orElse(null);
            if (mouvement != null) {
                garantieRetenue = Money.of(mouvement.getDebit(), devise);
                modePaiement = "Retenue sur dépôt de garantie";
            }
        }

        DonneesQuittanceCertifiee sansIdentite = new DonneesQuittanceCertifiee(
                "", 0,
                (bailleur.getPrenom() + " " + bailleur.getNom()).trim(), bailleur.getAdresse(),
                locataire.getNom(), patrimoine.getNom(), bien.getAdresse(),
                periode, YearMonth.parse(periode).format(MOIS_ANNEE),
                Money.of(bail.getLoyerHc(), devise), Money.of(bail.getProvisionCharges(), devise),
                Money.of(bail.getLoyerCc(), devise), Money.of(paiement.getMontantRecu(), devise),
                modePaiement, garantieRetenue, LocalDate.now());
        return new DonneesAssemblees(sansIdentite, ContenuQuittance.empreinteMetier(sansIdentite));
    }

    /** Données assemblées + empreinte métier, avant attribution du numéro et de la version. */
    private record DonneesAssemblees(DonneesQuittanceCertifiee donnees, String empreinteMetier) {

        DonneesQuittanceCertifiee avecIdentite(String numero, int version) {
            return new DonneesQuittanceCertifiee(numero, version, donnees.bailleurNom(),
                    donnees.bailleurAdresse(), donnees.locataireNom(), donnees.patrimoineNom(),
                    donnees.bienAdresse(), donnees.periode(), donnees.periodeLibelle(),
                    donnees.loyerHc(), donnees.provisionCharges(), donnees.loyerCc(),
                    donnees.montantRecu(), donnees.modePaiement(), donnees.garantieRetenue(),
                    donnees.dateEmission());
        }
    }
}
