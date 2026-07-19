package com.loyertracker.notifications;

import java.util.UUID;

/** Destinataire candidat au fan-out d'un {@link NotificationEvent} vers l'Outbox (ADR-18 §Modèle). */
public record Destinataire(TypeDestinataire type, UUID id) {
}
