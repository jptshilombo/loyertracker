package com.loyertracker.rgpd;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.affectations.AffectationDto;
import com.loyertracker.affectations.AffectationRepository;
import com.loyertracker.audit.AuditService;
import com.loyertracker.baux.Bail;
import com.loyertracker.baux.BailDto;
import com.loyertracker.baux.BailRepository;
import com.loyertracker.baux.Devise;
import com.loyertracker.biens.BienDto;
import com.loyertracker.biens.BienRepository;
import com.loyertracker.garanties.GarantieDto;
import com.loyertracker.garanties.GarantieMovementDto;
import com.loyertracker.garanties.GarantieMovementRepository;
import com.loyertracker.garanties.GarantieRepository;
import com.loyertracker.paiements.PaiementDto;
import com.loyertracker.paiements.PaiementRepository;
import com.loyertracker.quittances.QuittanceExportDto;
import com.loyertracker.quittances.QuittanceRepository;
import com.loyertracker.rgpd.ExportBailleurDto.BailExportDto;
import com.loyertracker.rgpd.ExportBailleurDto.BienExportDto;
import com.loyertracker.rgpd.ExportBailleurDto.GarantieExportDto;
import com.loyertracker.securite.TenantContext;

@Service
public class RgpdService {

    private final TenantContext tenant;
    private final BienRepository biens;
    private final BailRepository baux;
    private final PaiementRepository paiements;
    private final GarantieRepository garanties;
    private final GarantieMovementRepository mouvements;
    private final AffectationRepository affectations;
    private final QuittanceRepository quittances;
    private final AuditService audit;

    public RgpdService(TenantContext tenant, BienRepository biens, BailRepository baux,
            PaiementRepository paiements, GarantieRepository garanties,
            GarantieMovementRepository mouvements, AffectationRepository affectations,
            QuittanceRepository quittances, AuditService audit) {
        this.tenant = tenant;
        this.biens = biens;
        this.baux = baux;
        this.paiements = paiements;
        this.garanties = garanties;
        this.mouvements = mouvements;
        this.affectations = affectations;
        this.quittances = quittances;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ExportBailleurDto exporter(Authentication authentication) {
        String sub = ((Jwt) authentication.getPrincipal()).getSubject();
        UUID bailleurId = tenant.activerDepuisKeycloak(sub);

        List<BienExportDto> bienExports = biens.findAll().stream()
                .map(bien -> {
                    BienDto bienDto = BienDto.from(bien);

                    List<Bail> bauxDuBien = baux.findByBienIdOrderByDateDebutDesc(bien.getId());

                    // Garanties de chaque bail du bien, avec leur historique de mouvements
                    // (Sprint 10, US-97/RGPD) chargé par lot (pas de N+1 par garantie).
                    Map<UUID, List<GarantieDto>> garantiesParBail = bauxDuBien.stream()
                            .collect(Collectors.toMap(Bail::getId,
                                    bail -> garanties.findByBailIdOrderByDateDepotDesc(bail.getId())
                                            .stream().map(GarantieDto::from).toList()));
                    List<UUID> garantieIds = garantiesParBail.values().stream()
                            .flatMap(List::stream).map(GarantieDto::id).toList();
                    // groupingBy préserve l'ordre de parcours : le tri stable (date, cree_le, id)
                    // de la requête vaut donc aussi pour chaque liste par garantie (RSV-S10-01).
                    Map<UUID, List<GarantieMovementDto>> mouvementsParGarantie = mouvements
                            .findByGarantieIdInOrderByDateMouvementAscCreeLeAscIdAsc(garantieIds)
                            .stream()
                            .map(GarantieMovementDto::from)
                            .collect(Collectors.groupingBy(GarantieMovementDto::garantieId));

                    List<BailExportDto> bailExports = bauxDuBien.stream()
                            .map(bail -> {
                                List<GarantieExportDto> garantieExports = garantiesParBail
                                        .get(bail.getId()).stream()
                                        .map(g -> new GarantieExportDto(g,
                                                mouvementsParGarantie.getOrDefault(g.id(), List.of())))
                                        .toList();
                                // depotGarantie dérivé (ADR-14 §8, recalculé au Sprint 10) : somme
                                // en mémoire des crédits DEPOT_INITIAL/COMPLEMENT déjà chargés
                                // ci-dessus, pas de requête supplémentaire (pas de N+1).
                                BigDecimal montantDepose = garantieExports.stream()
                                        .flatMap(ge -> ge.mouvements().stream())
                                        .filter(m -> m.type().equals("DEPOT_INITIAL")
                                                || m.type().equals("COMPLEMENT"))
                                        .map(GarantieMovementDto::credit)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                return new BailExportDto(BailDto.from(bail, montantDepose),
                                        garantieExports);
                            }).toList();

                    // Résolution batch (pas de N+1) : réutilise la liste de baux déjà chargée
                    // ci-dessus pour associer chaque paiement à la devise de son bail (US-93, ADR-13).
                    Map<UUID, Devise> devisesParBail = bauxDuBien.stream()
                            .collect(Collectors.toMap(Bail::getId, Bail::getDevise));
                    List<PaiementDto> paiementDtos = paiements
                            .findByBienIdOrderByPeriodeDesc(bien.getId())
                            .stream()
                            .map(p -> PaiementDto.from(p, devisesParBail.get(p.getBailId())))
                            .toList();

                    List<AffectationDto> affectationDtos = affectations
                            .findByBienIdOrderByDateDebutDesc(bien.getId())
                            .stream().map(AffectationDto::from).toList();

                    return new BienExportDto(bienDto, bailExports, paiementDtos, affectationDtos);
                }).toList();

        // Quittances certifiées (EP-14/ADR-15 §RGPD) : métadonnées + contenu canonique certifié,
        // jamais les octets du PDF.
        List<QuittanceExportDto> quittanceExports = quittances.findByOrderByEmiseLeDesc()
                .stream().map(QuittanceExportDto::from).toList();

        return new ExportBailleurDto(bailleurId, OffsetDateTime.now(), bienExports,
                quittanceExports);
    }

    @Transactional
    public void anonymiserLocataire(UUID bienId, UUID bailId, Authentication authentication) {
        // Active RLS depuis l'identité du caller (JWT), pas depuis le bien :
        // garantit que le tenant est celui du bailleur appelant, et non celui du propriétaire
        // du bien. Sous cette RLS, findByIdAndBienId retourne vide si le bien n'appartient pas
        // à ce bailleur (cloisonnement cross-bailleur).
        String sub = ((Jwt) authentication.getPrincipal()).getSubject();
        UUID bailleurId = tenant.activerDepuisKeycloak(sub);

        Bail bail = baux.findByIdAndBienId(bailId, bienId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bail introuvable sur ce bien."));

        bail.anonymiserLocataire();

        audit.enregistrer(authentication, bailleurId, "EFFACEMENT_LOCATAIRE", "bail", bailId);
    }
}
