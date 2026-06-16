# Changelog — LoyerTracker

Toutes les évolutions notables de ce projet sont consignées dans ce fichier.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le projet adhère au
[Semantic Versioning](https://semver.org/lang/fr/) (D-REL-002, CGPA v5.2).

## [Non publié]

- Validation staging par simulation d'incident de l'alerting (OBS-02/03) et re-validation du
  Gate Staging enrichi.
- Ratification du Gate 07A — Release Readiness.
- Go-live production réel (provisioning hôte, Gate 09/10) — lot ultérieur.

## [1.0.0] — 2026-06-16

Première release destinée à la production : MVP de gestion locative bailleur-centrée
fonctionnellement complet (S01→S04), industrialisé et observable. Validée sur staging ;
go-live production différé à un lot ultérieur.

### Ajouts — Fonctionnel (EP-03 / EP-04 / EP-05)

- **Comptes & délégation** : inscription bailleur, invitation gestionnaire tokenisée,
  acceptation d'invitation, autorisation fine ReBAC par bien (US-10→US-13).
- **Biens, baux, affectations** : CRUD biens, baux avec unicité, historiques, affectations avec
  honoraires (US-20→US-24) + frontend bailleur/gestionnaire.
- **Paiements & garanties** : génération des loyers attendus, pointage (RECU/PARTIEL/EN_RETARD/
  IMPAYE), dépôt/restitution de garantie, audit des écritures (US-30→US-32).
- **Honoraires & pilotage** : calcul des honoraires (POURCENTAGE/FORFAIT), alertes
  LOYER_EN_RETARD / FIN_BAIL / PREAVIS / GARANTIE_NON_RESTITUEE, scoping, journal d'audit
  bailleur (US-40, US-50/51/52, US-62) + consoles frontend.

### Ajouts — Sécurité & architecture

- Cloisonnement multi-tenant PostgreSQL RLS `FORCE` réellement exercée (rôle applicatif à
  privilèges minimaux), fonctions `SECURITY DEFINER` pour batch et résolution de tenant.
- AuthN Keycloak OIDC/PKCE, resource server JWT, RBAC realm + ReBAC applicatif.
- Schéma Flyway V1→V10.

### Ajouts — Industrialisation (Production Readiness)

- CD vers GHCR par tag immuable `sha-<8>` + scan Trivy des images api/web.
- Stack staging durcie (non-root, images GHCR), déploiement staging réel + smoke 46/0.
- Sauvegarde/restauration PostgreSQL versionnée, drill de restauration réel (RPO 24 h / RTO < 1 h).
- Observabilité : métriques Prometheus, `/healthz`, runbook, cron de backup installable.
- **Alerting des composants critiques** : Prometheus + Alertmanager + blackbox-exporter +
  Pushgateway (profil `monitoring`), 10 règles (API, base de données, Keycloak, jobs planifiés,
  sauvegarde) — OBS-02/03.

### Ajouts — Gouvernance (CGPA v5.2)

- Reprise CGPA v5.2 (`Resume Approved with Reservations`), Gate 06A — DevSecOps Readiness **GO**,
  Environment Promotion Model (ENV-01), Observability Governance, Release Governance.

### DevSecOps

- CI GitHub Actions : build, tests (backend Testcontainers, frontend Karma, smoke stack),
  SAST (CodeQL + SonarQube quality gate bloquant), SCA + scan d'images (Trivy bloquant), secrets
  (Gitleaks). Alignement `docker-compose.prod.yml` sur images GHCR par tag immuable.

### Limitations connues

- Go-live production non encore réalisé (Gate 07A à statuer, Gate 09/10 ultérieurs).
- Alerting livré ; validation staging par simulation d'incident à rejouer.
- OpenAPI non encore produit ; UX S02 minimale.

[Non publié]: https://github.com/jptshilombo/loyertracker/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/jptshilombo/loyertracker/releases/tag/v1.0.0
