# Observability Governance — LoyerTracker

> Formalisation de l'Observability Governance CGPA v5.2
> (`setup-cgpa/docs/cgpa/observability-governance.md`). Couvre OBS-01/02/03. Traite la
> réserve **OBS-02/03** (alerting centralisé) du cap production. Document vivant.

## OBS-01 — Logs, métriques et supervision

| Pilier | Mise en œuvre |
|---|---|
| **Logs** | JSON ECS (`api`), JSON access log (`nginx`), sur stdout (12-factor). ENF-03 : aucune PII journalisée. |
| **Métriques** | Micrometer/Prometheus à `/api/actuator/prometheus` (JVM, `http_server_requests` avec histogramme p99, pool Hikari, métriques métier des jobs planifiés). Scrape **interne uniquement**, bloqué publiquement par Nginx (404). |
| **Supervision** | Serveur **Prometheus** auto-hébergé (profil Compose `monitoring`) scrutant API, sondes blackbox (PostgreSQL, Keycloak) et Pushgateway (backup) ; **Alertmanager** pour la notification. Healthchecks Docker + `/api/actuator/health` + `/healthz`. |

La profondeur est proportionnée (profil PME) : pas d'astreinte 24/7 ni de tableaux de bord
Grafana à ce stade ; alerting par webhook vers le canal de l'équipe.

## OBS-02 — Incidents critiques détectables

Alertes définies dans `infra/monitoring/alerts.yml`, évaluées par Prometheus, notifiées par
Alertmanager (`infra/monitoring/alertmanager.yml`) :

| Incident critique (exemple OBS-02) | Alerte |
|---|---|
| API indisponible | `ApiDown` |
| Base de données inaccessible | `PostgresProbeDown`, `DbPoolPendingConnections` |
| Erreur d'authentification massive | `AuthErrorSurge` |
| Taux d'erreur anormal | `ApiHighErrorRate` |
| Latence critique | `ApiHighLatency` |
| Job planifié bloqué | `BatchJobStale` |
| Sauvegarde en échec / absente | `BackupHeartbeatStale`, `BackupHeartbeatMissing` |

## OBS-03 — Composants critiques supervisés

| Composant critique | Mécanisme de supervision |
|---|---|
| API | scrape Micrometer (`up`, erreurs, latence, pool) |
| Base de données (PostgreSQL) | sonde TCP blackbox + métriques Hikari |
| Service d'identité (Keycloak) | sonde HTTP blackbox `/auth/health/ready` |
| Jobs planifiés (échéances/honoraires, alertes) | jauge `loyertracker_batch_last_success_epoch{job}` |
| Stockage / sauvegarde | heartbeat Pushgateway (`loyertracker_backup_last_success_epoch`) |

*Hors périmètre actuel (pas de file de messages ni de service externe critique dans
l'architecture MVP).*

## Niveau minimal et exploitation

Détection : automatique (Prometheus/Alertmanager) **et** manuelle (runbook §7/§8,
`docker compose ps`, logs). Procédures d'incident et **validation par simulation** : runbook
`docs/cgpa/07-devsecops/runbook-exploitation.md` §7. Activation : profil `monitoring`
(opt-in), notification injectée par `.env` (`ALERTMANAGER_WEBHOOK_URL`, jamais versionnée).

## Lien avec les gates

- **Gate Staging Readiness (enrichi v5.2)** : logs disponibles ✅, monitoring actif ✅,
  alertes critiques définies ✅ — à re-valider sur staging par simulation d'incident.
- **Gate 07A — Release Readiness** : l'observabilité fait partie des prérequis de promotion
  production.

---
*Livrable CGPA v5.2 — Observability Governance (OBS-01/02/03). Réf. :
`setup-cgpa/docs/cgpa/observability-governance.md`. Voir `infra/monitoring/` et le runbook §7.*
