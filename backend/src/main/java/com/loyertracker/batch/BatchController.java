package com.loyertracker.batch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loyertracker.alertes.AlerteService;
import com.loyertracker.honoraires.HonoraireService;
import com.loyertracker.notifications.NotificationDispatcher;

/**
 * Déclenchement manuel des jobs batch (exploitation / tests). Les jobs étant idempotents et
 * multi-bailleur, l'accès est restreint au rôle {@code BAILLEUR}.
 */
@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private final GenerationEcheancesService generation;
    private final HonoraireService honoraires;
    private final AlerteService alertes;
    private final NotificationDispatcher notifications;
    private final int limiteParBailleur;

    public BatchController(GenerationEcheancesService generation, HonoraireService honoraires,
            AlerteService alertes, NotificationDispatcher notifications,
            @Value("${app.notifications.dispatch.limite-par-bailleur:50}") int limiteParBailleur) {
        this.generation = generation;
        this.honoraires = honoraires;
        this.alertes = alertes;
        this.notifications = notifications;
        this.limiteParBailleur = limiteParBailleur;
    }

    public record DeclenchementDto(int echeancesCreees, int loyersEnRetard) {
    }

    public record HonorairesDto(int honorairesCalcules) {
    }

    public record AlertesDto(int alertesCreees) {
    }

    public record NotificationsDto(int lignesTraitees) {
    }

    @PostMapping("/echeances")
    @PreAuthorize("hasRole('BAILLEUR')")
    public DeclenchementDto genererEcheances() {
        int crees = generation.genererEcheances();
        int enRetard = generation.marquerEnRetard();
        return new DeclenchementDto(crees, enRetard);
    }

    @PostMapping("/honoraires")
    @PreAuthorize("hasRole('BAILLEUR')")
    public HonorairesDto calculerHonoraires() {
        return new HonorairesDto(honoraires.recalculerBatch());
    }

    @PostMapping("/alertes")
    @PreAuthorize("hasRole('BAILLEUR')")
    public AlertesDto genererAlertes() {
        return new AlertesDto(alertes.genererBatch());
    }

    /** EP-16 Sprint N+1 (US-123) : déclenchement manuel du dispatch de l'Outbox (exploitation / tests). */
    @PostMapping("/notifications")
    @PreAuthorize("hasRole('BAILLEUR')")
    public NotificationsDto traiterNotifications() {
        return new NotificationsDto(notifications.traiterLot(limiteParBailleur));
    }
}
