package com.loyertracker.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditDto(UUID id, UUID acteurId, String acteurRole, String action, String entityType,
        UUID entityId, OffsetDateTime horodatage) {

    public static AuditDto from(AuditLog l) {
        return new AuditDto(l.getId(), l.getActeurId(), l.getActeurRole(), l.getAction(),
                l.getEntityType(), l.getEntityId(), l.getHorodatage());
    }
}
