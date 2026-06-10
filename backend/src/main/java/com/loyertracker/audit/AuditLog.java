package com.loyertracker.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ligne du journal d'audit (BNF-05 / ENF-05), isolée par RLS sur {@code bailleur_id}. Mapping en
 * lecture seule pour la consultation (US-62) ; l'écriture passe par {@link AuditService} (SQL natif).
 * La colonne {@code details} (JSONB) n'est pas exposée à la consultation et n'est donc pas mappée.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", updatable = false)
    private UUID bailleurId;

    @Column(name = "acteur_id", nullable = false, updatable = false)
    private UUID acteurId;

    @Column(name = "acteur_role", nullable = false, updatable = false)
    private String acteurRole;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime horodatage;

    protected AuditLog() {
        // requis par JPA
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getActeurId() { return acteurId; }
    public String getActeurRole() { return acteurRole; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public OffsetDateTime getHorodatage() { return horodatage; }
}
