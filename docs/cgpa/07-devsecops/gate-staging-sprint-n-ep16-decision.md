# Gate Staging v5.4.1 — EP-16 Sprint N (Fondation notifications)

| Champ | Valeur |
|---|---|
| Date | 2026-07-19 |
| Candidat | merge PR #236 `e4744d9`, tag immuable `sha-e4744d92` |
| Périmètre | EP-16 Sprint N — US-119/120/121, fondation sans envoi externe |
| Rollback | `sha-359f4d63` — rollback applicatif viable, V27 additive conservée |
| Environnement | `ai-test-server` mutualisé, accès Gate direct `https://localhost:18443` |
| Décision | **GO — `STAGING_DEPLOYED`** |

## Conditions d’entrée

| Critère | Résultat | Preuve |
|---|---|---|
| Plan d’Exécution et GO Sprint N | PASS | Plan EP-16 approuvé le 2026-07-19 ; GO PO distinct tracé |
| PR #235 fusionnée | PASS | merge `3ed6d3f`, correctifs SonarQube inclus |
| Régularisation PR #236 fusionnée | PASS | merge `e4744d9`, documentation et flags d’exemple alignés |
| CI post-fusion `main` | PASS | CI `29691777913` : Backend, Frontend, Sécurité et Packaging Docker SUCCESS |
| CodeQL post-fusion | PASS | run `29691777896` : Java/Kotlin et JavaScript/TypeScript SUCCESS |
| Images immuables | PASS | API `sha256:9e9a331d…`, Web `sha256:c797934a…`, tag `sha-e4744d92` |
| Migration | PASS | V27 additive ; aucune modification de `docker-compose.staging.yml` |
| Sauvegarde pré-déploiement | PASS | dump `loyertracker-staging-20260719-152116.dump`, 511266 octets, 799 entrées, SHA-256 `9b99dcf6…` ; globals 1108 octets, SHA-256 `4b2cea55…` |

## STG-ISOL-01

| Contrôle | Avant | Après | Résultat |
|---|---|---|---|
| Projet Compose | `loyertracker-staging` | identique | PASS |
| Conteneurs projet et NPM | 9 actifs | 9 actifs | PASS |
| Services recréés | aucun | `api` et `nginx` uniquement | PASS |
| Restart count | 0 | 0 sur API, Nginx, PostgreSQL, Keycloak, NPM et monitoring | PASS |
| Réseau dédié | `loyertracker-staging_loyertracker-net` | identique | PASS |
| Volumes dédiés | `loyertracker-staging_postgres-data`, `loyertracker-staging_prometheus-data` | identiques | PASS |
| Ports | 18080/18443 ; NPM sur 80/81/443 | identiques, aucune collision | PASS |
| Ressources tierces | `infra_*`, `tools_*`, `ubuntu_*` présentes | présentes, non modifiées | PASS |
| Commandes Docker | lecture seule puis `pull/up -d --no-deps api nginx` | aucune commande globale, aucun `down`, prune ou `--remove-orphans` | PASS |

**Verdict `STG-ISOL-01` : PASS.** L’avertissement Compose sur les conteneurs monitoring
« orphelins » est attendu ; ils ont été laissés actifs et n’ont pas été supprimés.

## Déploiement

- Dépôt hôte avancé par fast-forward `359f4d6` → `e4744d9`.
- `.env` sauvegardé sous `.env.bak-pre-ep16-e4744d92`.
- `LOYERTRACKER_TAG` basculé de `sha-359f4d63` vers `sha-e4744d92`.
- Images API/Web tirées avant recréation ; digests conformes au candidat.
- Déploiement strictement ciblé : `api` et `nginx` seulement.
- API et Nginx devenus `healthy`, `restart=0`.

## Migration V27 et isolation des données

Flyway a appliqué une migration en 202 ms, du schéma V26 vers **V27**, puis confirmé 27
migrations réussies.

| Table | RLS ENABLE | RLS FORCE |
|---|---:|---:|
| `notification_preference` | oui | oui |
| `notification_event` | oui | oui |
| `notification_outbox` | oui | oui |
| `notification_delivery` | oui | oui |
| `notification_template` | non, référentiel global | non |

Avant smoke, les cinq tables étaient vides. Aucun secret ni credential Twilio n’a été utilisé.

## Validation fonctionnelle

Le smoke canonique a été exécuté avec :

```bash
BASE=https://localhost:18443 COMPOSE_FILE=docker-compose.staging.yml \
  ./infra/smoke/smoke-stack.sh
```

Résultat : **63 PASS / 0 FAIL**. Flyway 27/27, rôle applicatif `NOBYPASSRLS`, santé, JWT
Keycloak, parcours bailleur/gestionnaire, isolation cross-tenant, paiements, honoraires, alertes,
RGPD et surface publique des quittances sont verts. L’échafaudage `directAccessGrants` a été
révoqué automatiquement.

### Preuve spécifique EP-16

Après smoke :

- `notification_event` : **24** événements — `BAIL_CREE` 1, `PAIEMENT_RECU` 1,
  `LOYER_EN_RETARD` 21, `PREAVIS` 1 ;
- `notification_preference` : 0 ;
- `notification_outbox` : **0** ;
- `notification_delivery` : **0** ;
- `notification_template` : 0.

Le comportement attendu est confirmé : les événements sont persistés dans la transaction métier,
mais aucune Outbox externe n’est produite sans consentement. Les quatre variables de feature flag
ne sont pas injectées dans le conteneur et utilisent les valeurs sûres de `application.yml`
(`external=false`, WhatsApp=false, SMS=false, dry-run=true). Les logs API contiennent **0**
référence Twilio.

## État final

| Contrôle | Résultat |
|---|---|
| Services | 8 services LoyerTracker actifs, 4/4 services applicatifs `healthy`, restart=0 |
| Santé directe | `https://localhost:18443/healthz` → 200 |
| Santé publique | 401 via l’Access List NPM existante, comportement documenté et inchangé |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | `BackupHeartbeatMissing` uniquement, alerte historique sans rapport avec EP-16 |
| Logs | 0 erreur API, 0 HTTP 5xx Nginx, 0 référence Twilio |
| Tag | `sha-e4744d92` |

## Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | GO : Plan, preuves CI, Gate et historique tracés additivement |
| Enterprise Architect | GO : V27 additive, RLS conforme, aucune dépendance Twilio réelle |
| DevSecOps Lead | GO : backup, images, Flyway, smoke, monitoring et STG-ISOL-01 PASS |
| Release Manager | GO Staging uniquement ; Gate Production distinct requis |
| Chief Delivery Officer | **GO — `STAGING_DEPLOYED`** |

## Rollback et suite

V27 est additive : un redéploiement de `sha-359f4d63` est techniquement viable, les tables V27
restant inutilisées par l’ancienne version. La restauration du backup reste disponible si un
retour complet des données est requis.

Ce Gate n’autorise aucune Production. La prochaine étape est une décision Gate Production propre
au Sprint N. Les Sprints N+1 et N+2 restent soumis chacun à leur GO explicite distinct.
