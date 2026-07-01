package com.loyertracker.rgpd;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
import com.loyertracker.biens.BienDto;
import com.loyertracker.biens.BienRepository;
import com.loyertracker.garanties.GarantieDto;
import com.loyertracker.garanties.GarantieRepository;
import com.loyertracker.paiements.PaiementDto;
import com.loyertracker.paiements.PaiementRepository;
import com.loyertracker.rgpd.ExportBailleurDto.BailExportDto;
import com.loyertracker.rgpd.ExportBailleurDto.BienExportDto;
import com.loyertracker.securite.TenantContext;

@Service
public class RgpdService {

    private final TenantContext tenant;
    private final BienRepository biens;
    private final BailRepository baux;
    private final PaiementRepository paiements;
    private final GarantieRepository garanties;
    private final AffectationRepository affectations;
    private final AuditService audit;

    public RgpdService(TenantContext tenant, BienRepository biens, BailRepository baux,
            PaiementRepository paiements, GarantieRepository garanties,
            AffectationRepository affectations, AuditService audit) {
        this.tenant = tenant;
        this.biens = biens;
        this.baux = baux;
        this.paiements = paiements;
        this.garanties = garanties;
        this.affectations = affectations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ExportBailleurDto exporter(Authentication authentication) {
        String sub = ((Jwt) authentication.getPrincipal()).getSubject();
        UUID bailleurId = tenant.activerDepuisKeycloak(sub);

        List<BienExportDto> bienExports = biens.findAll().stream()
                .map(bien -> {
                    BienDto bienDto = BienDto.from(bien);

                    List<BailExportDto> bailExports = baux
                            .findByBienIdOrderByDateDebutDesc(bien.getId())
                            .stream()
                            .map(bail -> {
                                List<GarantieDto> gs = garanties
                                        .findByBailIdOrderByDateDepotDesc(bail.getId())
                                        .stream().map(GarantieDto::from).toList();
                                return new BailExportDto(BailDto.from(bail), gs);
                            }).toList();

                    List<PaiementDto> paiementDtos = paiements
                            .findByBienIdOrderByPeriodeDesc(bien.getId())
                            .stream().map(PaiementDto::from).toList();

                    List<AffectationDto> affectationDtos = affectations
                            .findByBienIdOrderByDateDebutDesc(bien.getId())
                            .stream().map(AffectationDto::from).toList();

                    return new BienExportDto(bienDto, bailExports, paiementDtos, affectationDtos);
                }).toList();

        return new ExportBailleurDto(bailleurId, OffsetDateTime.now(), bienExports);
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
