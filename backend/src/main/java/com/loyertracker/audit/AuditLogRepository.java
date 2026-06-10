package com.loyertracker.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Journal du tenant courant (RLS), du plus récent au plus ancien. */
    List<AuditLog> findByOrderByHorodatageDesc();
}
