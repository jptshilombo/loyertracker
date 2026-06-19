# Production Readiness Review — LoyerTracker (release 1.0.0)

> Assessment CGPA v5.2 (`setup-cgpa/docs/cgpa/assessments/production-readiness-review.md`), étape 5 du
> `workflow-preparation-production`. Évalue si la solution, l'organisation et l'exploitation sont prêtes
> pour la production **avant** le franchissement du **Gate 09 — Production Readiness**.
> Date : **2026-06-19** · Release : **`1.0.0`** (SemVer, Release-Ready) · Responsable : CGPA Chief Delivery Officer + PO.

## Contexte & cible

- **Cible Production** : **hôte dédié** distinct de staging (ENV-01 strict, Staging ≠ Production) — stack
  `loyertracker-prod`, domaine `loyertracker.loyerpro.org`. Arbitrage PO du 2026-06-19.
- **Recette** : **waiver tracé** — le smoke 46/0 et le **Gate Staging enrichi GO** (2026-06-19) tiennent
  lieu de recette par équivalence ; pas de Gate 08 QA/recette formel (cohérent avec l'historique projet).
- **Artefact** : image GHCR `1.0.0` au tag immuable `sha-<8>` via `docker-compose.prod.yml` (déjà aligné
  GHCR/tag immuable) + overlay `docker-compose.monitoring.yml` (profil `monitoring`).
- **Note** : l'environnement Production n'est **pas encore provisionné** ; ce gate confirme la
  *readiness-to-go*. Le déploiement effectif et sa preuve relèvent du **Gate 10 — Mise en production**.

## Barème

Note 0-4 par axe (0 absent, 1 embryonnaire, 2 partiel, 3 solide, 4 excellent). Niveau de maturité :
Partiel (< 2,5) · Solide (2,5–3,4) · Excellent (≥ 3,5).

---

## Runbooks

- **Constats** : runbook d'exploitation versionné (`docs/cgpa/07-devsecops/runbook-exploitation.md`) couvrant
  démarrage/arrêt, sauvegarde/restauration, rollback, supervision (§7 alerting). Procédure de
  redéploiement par tag immuable documentée. Cron de backup installable (`infra/backup/install-cron.sh`).
- **Preuves** : runbook éprouvé sur staging (déploiements successifs `sha-4e0d3995`→`sha-73359c5c`),
  drill de restauration réel exécuté (RPO 24 h / RTO < 1 h, PR #26).
- **Risques** : section « go-live production » dédiée (DNS/TLS/realm prod, bascule) à ajouter au runbook ;
  procédures rédigées pour staging à transposer 1:1 sur l'hôte dédié.
- **Note 0-4** : **3**

## Monitoring

- **Constats** : métriques Micrometer/Prometheus (`/api/actuator/prometheus`, scrape interne uniquement,
  bloqué publiquement 404), `/healthz` web, logs JSON ECS (backend + Nginx). Chaîne Prometheus +
  blackbox-exporter + Pushgateway en overlay réutilisable (`docker-compose.monitoring.yml`, profil
  `monitoring`), combinable à l'identique avec la stack prod.
- **Preuves** : sur staging, **4/4 cibles Prometheus `up`** en conditions réelles (RR-1, 2026-06-19) ;
  scrape interne 200 / public 404 confirmé live.
- **Risques** : monitoring non publié (loopback/réseau interne) — à reproduire tel quel sur prod ; aucune
  rétention longue durée ni dashboard centralisé (acceptable PME).
- **Note 0-4** : **4**

## Alerting

- **Constats** : 10 règles d'alerte sur les composants critiques (`infra/monitoring/alerts.yml`) — API
  down/erreurs/latence, 401, PostgreSQL TCP + pool Hikari, Keycloak, jobs planifiés, sauvegarde.
  Alertmanager v0.28.1, secret webhook hors dépôt (`url_file`).
- **Preuves** : **RR-1 (2026-06-19)** — 4/4 composants critiques (API, PostgreSQL, Keycloak, sauvegarde)
  FIRING→notifié→resolved, **notification Alertmanager bout-en-bout prouvée** (payload capturé).
  `docs/staging-state.md` §10.
- **Risques** : webhook de destination prod (canal d'astreinte réel) à configurer au go-live ; règles
  identiques transposables sans modification.
- **Note 0-4** : **4**

## Capacity

- **Constats** : aucune campagne de charge/capacité formelle. Contexte PME mono-bailleur à faible volume ;
  dimensionnement de l'hôte dédié à définir (CPU/RAM/disque PostgreSQL + Keycloak).
- **Preuves** : stack complète éprouvée fonctionnellement (smoke 46/0) ; pas de métrique de tenue en charge.
- **Risques** : pas de baseline de sizing ni de seuil de saturation testé. Mitigation : volume initial
  faible, observabilité active (CPU/latence p99/pool Hikari surveillés), hôte dédié redimensionnable.
- **Note 0-4** : **2** → **réserve RG-09-1**

## Support

- **Constats** : modèle de support/astreinte informel (opérateur unique PME). Pas de matrice
  d'escalade ni de SLA documentés.
- **Preuves** : runbook d'exploitation = base opérationnelle ; alerting notifie un canal unique.
- **Risques** : continuité de service dépendante d'un opérateur unique. Mitigation : documenter un
  processus de support minimal (canal d'alerte, procédure incident, point de contact) avant/au Gate 10.
- **Note 0-4** : **2** → **réserve RG-09-2**

## Rollback

- **Constats** : rollback par **redéploiement du tag immuable précédent** (`LOYERTRACKER_TAG`), jamais
  `latest`. Sauvegarde/restauration PostgreSQL versionnée et prouvée.
- **Preuves** : historique de bascules de tags sur staging (`staging-state.md` §8) ; drill de restauration
  réel RPO 24 h / RTO < 1 h (PR #26). `docker-compose.prod.yml` aligné GHCR/tag immuable.
- **Risques** : rollback à rejouer une fois sur la cible prod réelle (étape Gate 10).
- **Note 0-4** : **4**

---

## Conclusion

- **Scores** : Runbooks 3 · Monitoring 4 · Alerting 4 · Capacity 2 · Support 2 · Rollback 4.
- **Score global** : **19/24 ≈ 3,2/4 — niveau Solide.** Socle technique d'exploitation solide
  (monitoring, alerting et rollback prouvés en conditions réelles sur staging) ; faiblesses concentrées
  sur la maturité **organisationnelle** (capacity, support) — légitimes en contexte PME et non bloquantes.
- **Décision recommandée** : **GO sous réserve** au Gate 09 — la solution et l'exploitation sont prêtes à
  la promotion production ; deux réserves non bloquantes encadrent la trajectoire de correction.
- **Remédiation (réserves)** :
  - **RG-09-1 (Capacity)** : définir une baseline de sizing de l'hôte dédié + surveiller la tenue via le
    monitoring ; revue en exploitation. Risque accepté pour le volume PME initial.
  - **RG-09-2 (Support)** : documenter un processus de support/incident minimal (canal d'astreinte, point
    de contact) avant ou au franchissement du Gate 10.
  - **RR-2** (traçabilité production, héritée du Gate 07A) : renseignée au déploiement effectif (Gate 10).

---
*Livrable CGPA v5.2 — Production Readiness Review. Réf. : `setup-cgpa/docs/cgpa/assessments/production-readiness-review.md`,
`setup-cgpa/docs/cgpa/workflows/workflow-preparation-production.md`. Alimente `docs/cgpa/09-production/gate-09-decision.md`.*
