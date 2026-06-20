# Gate 10 — Mise en production · Dossier de décision (LoyerTracker)

> Gate CGPA (`setup-cgpa/docs/cgpa/gates/gate-10-mise-en-production.md`, phase 10). Acte la **mise en
> production effective** de la release `1.0.0` sur l'hôte Production dédié, après le **Gate 09 — Production
> Readiness GO sous réserve** (`docs/cgpa/09-production/gate-09-decision.md`). **Statut : GO** — ratifié le
> **2026-06-20**. Lève les réserves **RR-2** (traçabilité production), **RG-09-1** (capacity) et **RG-09-2**
> (support). S'appuie sur le rapport d'état `docs/prod-state.md` (déploiement réel + smoke + alerting).

## Version & cible

- **Release** : `1.0.0` (SemVer, Release-Ready) — image GHCR au tag immuable **`sha-73359c5c`** (correctif
  CVE Angular PR #36), `api` + `web`, jamais `latest`.
- **Environnement cible** : **Production sur hôte dédié** (distinct de staging, ENV-01 strict) — stack
  Compose `loyertracker`, hôte EC2 `t3.medium` (EIP `18.158.70.88`), domaine public
  **`https://loyertracker.loyerpro.org`** (Route53), TLS Let's Encrypt (valide jusqu'au 2026-09-17).
- **Recette** : **waiver tracé** (hérité Gate 09) — smoke `infra/smoke/smoke-stack.sh` **46/0** (staging + prod)
  + Gate Staging enrichi GO, en équivalence (pas de Gate 08 formel — arbitrage PO 2026-06-19).

## Conditions d'entrée

- [x] Gate 09 — Production Readiness statué (**GO sous réserve**, 2026-06-19).
- [x] Hôte Production dédié provisionné (IP + SSH + DNS + TLS).
- [x] Check-list de provisioning déroulée (`docs/cgpa/10-mise-en-production/checklist-provisioning-hote-prod.md`).
- [x] Artefact `1.0.0` déployable par tag immuable GHCR (`docker-compose.prod.yml`).

## Critères GO

| # | Critère | État | Preuve |
|---|---------|------|--------|
| 1 | Hôte Production dédié provisionné, isolé, durci | ✅ | EC2 `t3.medium` dédié (≠ `ai-test-server`), UFW + fail2ban, ports `22/80/443` seuls publics ; `docs/prod-state.md` §1 |
| 2 | Déploiement par image GHCR tag immuable (jamais `latest`) | ✅ | `LOYERTRACKER_TAG=sha-73359c5c`, `docker-compose.prod.yml` (images GHCR, build local retiré) |
| 3 | Stack complète `healthy` (4/4) + Flyway V1→V10 + RLS sous rôle restreint | ✅ | 4/4 `healthy`, 10 migrations `success`, pool sous `loyertracker_api` (NOSUPERUSER NOBYPASSRLS) ; `docs/prod-state.md` §3 |
| 4 | Point d'entrée TLS public valide ; ports internes non publiés | ✅ | Nginx hôte + certbot Let's Encrypt (exp. 2026-09-17) → conteneur `18080/18443` ; postgres/keycloak/monitoring non publiés ; `docs/prod-state.md` §2/§6 |
| 5 | Issuer OIDC public correct (portless) | ✅ | `KC_HOSTNAME=loyertracker.loyerpro.org` → `issuer = https://loyertracker.loyerpro.org/auth/realms/loyertracker` |
| 6 | Smoke contre production (parcours réel S01→S04 + isolation cross-tenant) | ✅ | **46 PASS / 0 FAIL** sous JWT réels ; honoraire 72,00 €, PREAVIS J+75, 0 fuite cross-tenant, 401 sans token ; échafaudage `directAccessGrants` révoqué ; `docs/prod-state.md` §4 |
| 7 | Monitoring opérationnel en production | ✅ | 5/5 cibles Prometheus `up` ; endpoints monitoring internes (`9090/9093/9115`) + Pushgateway loopback ; `docs/prod-state.md` §6 |
| 8 | Alerting prouvé bout-en-bout sur le canal réel | ✅ | Incident `Gate10NotificationDrill` : **FIRING + RESOLVED livrés à Discord** (`notifications_total=2`, `failed_total=0`), réception humaine **confirmée par le PO** ; `docs/prod-state.md` §7 |
| 9 | Sauvegarde opérationnelle (cron + intégrité) | ✅ | Backup manuel `-Fc` **vérifié `pg_restore --list`** + globals (rôles) ; cron prod 02h15 installé (`COMPOSE_FILE` prod) ; heartbeat scrapé par Prometheus ; `docs/prod-state.md` §8 |
| 10 | Rollback disponible et documenté | ✅ | Redéploiement du `LOYERTRACKER_TAG` précédent (tags immuables, procédure éprouvée staging) + backup vérifié comme filet (RPO 24 h) ; `docs/prod-state.md` §9 |
| 11 | Durcissement go-live (aucune donnée/compte de test) | ✅ | Reset propre (`down -v`/`up` → base **vierge**, 0 ligne métier) ; compte de test `bailleur-test@test.local` **désactivé** (`enabled=false`) ; `docs/prod-state.md` §5 |
| 12 | Traçabilité production renseignée | ✅ | `docs/release-notes-v1.0.0.md` §5 complétée (date, tag, opérateur, vérifications, rollback) ; `docs/prod-state.md` ; **lève RR-2** |

## Réserves Gate 09 — solde

| # | Réserve | Traitement au Gate 10 | Statut |
|---|---------|------------------------|--------|
| **RR-2** | Traçabilité production (release-notes §5 : date, tag, opérateur, vérifications) | `docs/release-notes-v1.0.0.md` §5 renseignée au déploiement réel + `docs/prod-state.md` créé | ✅ **Levée** |
| **RG-09-1** | Capacity : baseline de sizing + surveillance live | Baseline **actée** : `t3.medium` (2 vCPU / 4 GiB / 40 GiB gp3) = cible PME de la check-list ; **surveillance active** (Prometheus : latence p99, pool Hikari, sondes) ; revue en exploitation | ✅ **Levée** (surveillance continue) |
| **RG-09-2** | Support : canal d'astreinte + processus incident minimal | Canal **Discord** câblé (`discord_configs`, secret hors dépôt) et **prouvé bout-en-bout** (FIRING/RESOLVED reçus) ; processus minimal documenté (`docs/prod-state.md` §10 : canal, contact PO, escalade mono-opérateur PME) | ✅ **Levée** |

## Écarts traités au go-live (compose prod jamais déployé auparavant)

L'overlay `docker-compose.prod.yml` n'avait jamais été déployé : le go-live réel a révélé puis corrigé
trois écarts (analogues aux 4 écarts du déploiement staging réel, PR #29), tous versionnés :

1. **Ports nginx 80/443 au lieu de 18080/18443** — le compose de base fige les ports et Compose **concatène**
   les listes ; corrigé par `ports: !override` (PR #60, merge `f9fa67a`).
2. **Keycloak en crash-loop** — `start --optimized` suppose une image pré-`kc.sh build` ; l'image GHCR
   officielle est buildée sur H2 → KC ignorait `KC_DB`/`KC_HEALTH_ENABLED`/`KC_HTTP_RELATIVE_PATH` et
   retombait sur H2. Corrigé : `start` sans `--optimized` + `KC_HOSTNAME` public (PR #61, merge `e87f8f5`).
3. **Receiver Alertmanager incompatible Discord** — un webhook Discord brut rejette le JSON natif
   d'Alertmanager ; bascule sur le receiver natif `discord_configs` (secret inchangé via `webhook_url_file`)
   (PR #62, merge `d576f90`).

Plus un ajustement hôte (non versionné, spécifique machine) : permissions du certificat interne
(`infra/nginx/certs/localhost-key.pem` 644) pour lecture par le conteneur web non-root + `--force-recreate`.

## Sous-agents mobilisés

| Sous-agent | Avis |
|------------|------|
| Governance Officer | **GO** : continuité respectée (aucun gate rejoué, historique préservé, Phase 7 conservée), réserves Gate 09 soldées avec preuves, écarts go-live tracés en PR. Aucune dette de gouvernance critique. |
| Enterprise Architect | **GO** : cible Production sur **hôte dédié** distinct de staging conforme ENV-01 (Staging ≠ Production) ; isolation prouvée sur l'hôte réel (RLS `FORCE` sous `loyertracker_api`, cross-tenant 0 fuite, ports internes non publiés, monitoring non exposé). |
| DevSecOps Lead | **GO** : tag immuable, secrets hors dépôt (`.env` 600, webhook via `webhook_url_file`), TLS Let's Encrypt valide, base **vierge** au lancement, compte de test **désactivé**, monitoring/Pushgateway non publiés. |
| Release Manager | **GO** : release `1.0.0` déployée/vérifiée/réversible, traçabilité production renseignée (RR-2 levée), rollback disponible, sauvegarde opérationnelle. RG-09-1 / RG-09-2 soldées. |

## Décision

- **Statut : GO** — ratifié le **2026-06-20** par le CGPA Chief Delivery Officer.
- **La release `1.0.0` est mise en production.** Production **LIVE** sur
  **`https://loyertracker.loyerpro.org`** : 4/4 `healthy`, smoke 46/0, base vierge, supervisée
  (5/5 cibles `up`), alerting Discord prouvé, sauvegarde opérationnelle, rollback disponible.
- **Réserves Gate 09 levées** : RR-2 (traçabilité), RG-09-1 (capacity, surveillance continue),
  RG-09-2 (support). Aucune réserve bloquante résiduelle.
- **Suite (exploitation, hors gate)** : revue de capacité en exploitation, restriction du SG SSH à l'IP
  d'admin (signalée), drill de rollback significatif possible à la prochaine release.

> ✅ **Statué GO.** Décision consignée ici, dans `docs/prod-state.md`, `docs/release-notes-v1.0.0.md` §5
> et `docs/project-state.md` (§3, §11, §12, §13, §14).

---
*Livrable CGPA v5.2 — Gate 10 (Mise en production). Réf. : `setup-cgpa/docs/cgpa/gates/gate-10-mise-en-production.md`,
`setup-cgpa/docs/cgpa/phases/phase-10-mise-en-production.md`.*
