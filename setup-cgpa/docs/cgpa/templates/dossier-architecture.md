# Dossier d'architecture — {{NOM_DU_PROJET}}

| Champ | Valeur |
|-------|--------|
| Projet | {{NOM_DU_PROJET}} |
| Auteur | {{AUTEUR}} |
| Date | {{AAAA-MM-JJ}} |
| Phase | 05 — Architecture & conception |
| Gate visé | Gate 4 (dernier verrou avant code) |
| Statut | Brouillon / En revue / Validé |

## 1. Vue d'ensemble
{{Description de l'architecture cible — schéma de contexte}}

```
{{Diagramme (composants, flux) — texte/ASCII ou lien vers image}}
```

## 2. Composants & couches
| Composant | Responsabilité | Technologie |
|-----------|----------------|-------------|
| Frontend | {{UI}} | Angular |
| Backend | {{API métier}} | Spring Boot |
| Auth | {{Authn/Authz}} | Keycloak (OIDC) |
| Données | {{Persistance}} | {{BD}} |
| Déploiement | {{Conteneurs}} | Docker |

## 3. Modèle de données
{{Entités principales, relations, schéma}}

## 4. Contrats d'API
| Endpoint | Méthode | Description | Sécurité |
|----------|---------|-------------|----------|
| {{/api/...}} | {{GET}} | {{…}} | {{Rôle/scope}} |

## 5. Architecture de sécurité
- Authentification / autorisation : {{Keycloak, rôles, scopes}}
- Gestion des secrets : {{…}}
- Surface d'attaque & mesures : {{…}}
- Conformité : {{RGPD, journalisation}}

## 6. Infrastructure & déploiement
- Environnements : `dev` / `staging` / `prod`
- Conteneurisation : {{Docker, images}}
- Stratégie de déploiement : {{rolling / blue-green / canary}}

## 7. Décisions d'architecture (ADR)
| ADR | Décision | Alternatives | Justification |
|-----|----------|--------------|---------------|
| ADR-01 | {{…}} | {{…}} | {{…}} |

## 8. Risques techniques
| Risque | Impact | Mitigation |
|--------|--------|------------|
| {{…}} | {{…}} | {{…}} |

## 9. Score de maturité (/20)
| Axe | Note (0–4) |
|-----|-----------|
| Complétude | |
| Qualité | |
| Sécurité | |
| Traçabilité | |
| Automatisation | |
| **Total** | **/20** |

## 10. Décision Gate 4
- Décision : ☐ Go ☐ Go sous réserve ☐ No Go
- Réserves / actions : {{…}}
- Date & responsable : {{…}}
