# Gate Staging — Sprint 9 (Garantie ledger, EP-12a, US-94)

| Champ | Valeur |
|---|---|
| Date | 2026-07-03 |
| Type | Sprint unique — première promotion Staging de Sprint 9 |
| Périmètre | Sprint 9 EP-12a : US-94 (`GarantieMovement`, migration V20 rétroactive, RLS, retrait `bail.depot_garantie`) |
| Tag candidat | `sha-6a358eb6` |
| Digest API | `sha256:485c8574cca057d4e00f3c0de640faf4ad8b378c302604b76a752563eb98dfba` |
| Digest Web | `sha256:70ae97f2eda455b5c9640cc33aeb6ea4abda9131222b3f948a5ea29768bca5c5` |
| Staging actuel | `sha-2da27182` (Sprint 7+8, déployé 2026-07-02) |
| Environnement | `ai-test-server` — Staging mutualisé |
| Décision | **GO sous réserve — déployé, backfill vérifié, smoke 59/59, cycle garantie live vérifié** |
| Statut | Déploiement + vérifications exécutés (§9/§10) — **`STAGING_DEPLOYED` non encore prononcé : `STG-ISOL-01` état après et mise à jour `docs/staging-state.md` restants** |
| Plans | `docs/cgpa/06-planification-agile/plan-execution-evolutions-ep10-ep13.md` (§Sprint 9), `sprint-9-garantie-ledger-rapport-validation.md` |
| PR | #152 (code, merge commit `6a358eb6`), #153/#154/#155/#156 (docs de gouvernance) |

## 1. Objet

Statuer l'autorisation de déploiement Staging du Sprint 9 (US-94, ledger de mouvements
`GarantieMovement`, migration rétroactive V20). Ce document **analyse la recevabilité du
candidat avant toute action sur `ai-test-server`** — il n'autorise pas encore de déploiement
effectif tant que les conditions bloquantes du §5 ne sont pas levées, et le passage à
`STAGING_DEPLOYED` nécessite l'exécution décrite au §6, à consigner en complément de ce document
(ou dans un document de suite, selon la convention déjà utilisée pour les Gates Production de ce
projet).

## 2. Périmètre

**PR #152 — Sprint 9 EP-12a US-94** (merge commit `6a358eb6`, commits applicatifs `dc89c3a` +
`2b50290`)
- Migration **V20** : table `garantie_movement` (RLS `ENABLE`+`FORCE`, pattern V12), backfill
  rétroactif de toutes les garanties existantes, `garantie.solde_actuel` (cache transactionnel),
  `ALTER TABLE bail DROP COLUMN depot_garantie`.
- `TypeMouvementGarantie`, `GarantieMovement`, `GarantieMovementRepository`, `GarantieMovementDto`.
- `GarantieService` : journalisation de chaque mouvement (création, restitution) en plus de
  l'audit existant.
- `BailDto.depotGarantie` : devient une valeur calculée (somme des garanties rattachées),
  `BailRequest.depotGarantie` retiré (dépôt saisi via le flux « Ajouter garantie » existant).
- Frontend : champ « Dépôt » retiré du formulaire de création de bail.

**Exclu de ce sprint (reporté Sprint 10, US-95→98)** : retenue typée, réapprovisionnement
(`COMPLEMENT`), écran d'historique des mouvements — cf. rapport de validation §6, non concerné
par ce Gate.

## 3. Écart de séquencement

Aucun. Contrairement au Gate Staging Sprint 7+8 (qui avait dû rattraper un sprint jamais promu),
Sprint 9 suit la séquence normale : Staging est actuellement sur `sha-2da27182` (Sprint 7+8, déjà
promu et documenté), et ce Gate promeut directement le candidat suivant.

## 4. Checklist Gate Staging (CGPA v5.4.1) — état pré-déploiement

### Identification

| Critère | Statut | Preuve |
|---|---|---|
| Sprint identifié | ✅ | Sprint 9 EP-12a, US-94 |
| Plan d'Exécution approuvé | ✅ | `plan-execution-evolutions-ep10-ep13.md` — PO GO sprint par sprint (cadrage 2026-07-01), kickoff Sprint 9 tranché 2026-07-02 (ADR-14 §8) |
| Rapport d'exécution Sprint disponible | ✅ | `sprint-9-garantie-ledger-rapport-validation.md` |
| Commit/artefact candidat identifié | ✅ | `6a358eb6` (merge PR #152 dans `main`), tag `sha-6a358eb6`, digests vérifiés ci-dessus (`docker buildx imagetools inspect`) |
| Environnement Staging identifié | ✅ | `ai-test-server`, actuellement `sha-2da27182` |

### Critères Sprint

| Critère | Statut | Preuve |
|---|---|---|
| Stories terminées listées | ✅ | US-94 |
| Stories exclues/reportées listées | ✅ | US-95→98 → Sprint 10 (rapport §6) |
| Écarts au plan acceptés | ✅ | Aucun écart — séquencement normal (§3) |
| Validation Product Owner | ✅ **GO** | GO de clôture Sprint 9 tracé le 2026-07-03 (jordan) — s'ajoute au GO de cadrage sprint-par-sprint du 2026-07-01 et à l'arbitrage kickoff `bail.depot_garantie` du 2026-07-02 (ADR-14 §8). Aucune réserve PO supplémentaire. |
| Validation Release Manager | ✅ **GO** | GO de recevabilité du candidat `sha-6a358eb6` pour promotion Staging tracé le 2026-07-03 (jordan) — CI 7/7 verte, `CHANGELOG.md`/`docs/project-state.md` à jour. Aucune réserve RM supplémentaire. |

### Contrôles DevSecOps

| Critère | Statut | Preuve |
|---|---|---|
| Build CI stable | ✅ | `6a358eb6` — 7/7 jobs SUCCESS (CodeQL Java/Kotlin + JS/TS, Backend, Frontend, Packaging Docker, Sécurité) |
| Tests unitaires/intégration | ✅ | 128/128 backend (dont `GarantieLedgerBackfillMigrationTest`, nouveau), 63/63 frontend — résultats du 2026-07-02 |
| Contrôles secrets/SCA/SAST/images | ✅ | Job Sécurité (gitleaks + SCA + Trivy) SUCCESS sur `6a358eb6` |
| SonarQube | ✅ | Violation S107 détectée et corrigée avant merge (commit `2b50290`, réduction arité `GarantieMovement`) |
| Migrations DB vérifiées | ✅ | V20 — `GarantieLedgerBackfillMigrationTest` (3 scénarios : `DETENU`, `RESTITUE_PARTIEL`, `RESTITUE_TOTAL` + invariant solde) |
| Secrets non exposés | ✅ | `.env` hors dépôt, gitleaks SUCCESS |

> **Observation hors périmètre de ce Gate** : un ré-examen de la branche le 2026-07-03 (PR #153,
> docs-only) montre un **échec CI Backend** sur un test sans lien avec Sprint 9 :
> `S04AlertesAuditIntegrationTest.preavisGenereDansLaBandeExclusifDeFinBailEtAntiDoublon`
> (`JSON path "$[0].type" expected:<PREAVIS> but was:<FIN_BAIL>`). Cause : le test fixe un terme de
> bail en dur (`2026-09-01`) et suppose implicitement une date du jour antérieure à la bande de
> préavis ]J+60;J+90] — au 2026-07-03, l'écart J+60 est atteint, faisant basculer le type d'alerte
> attendu. **Défaut de test pré-existant (dépendant de l'horloge), non introduit par Sprint 9, non
> couvert par ce Gate.** Sans incidence sur le candidat `sha-6a358eb6` dont le résultat CI (128/128)
> a été obtenu le 2026-07-02, avant que la fenêtre ne soit franchie. **Corrigé le 2026-07-03**
> (commit `92fe9f3`) : le terme est désormais calculé `LocalDate.now().plusDays(75)`, au milieu de
> la bande, insensible à la date d'exécution — `mvn verify` reconfirmé 128/128 en local. RSV-S9-04
> ci-dessous mise à jour en conséquence.

### Déploiement Staging

| Critère | Statut | Preuve |
|---|---|---|
| Rollback Staging identifié | ⚠️ **Risque élevé, voir §5** | Image : `sha-2da27182` disponible sur GHCR — **mais rollback applicatif seul seul insuffisant, voir analyse dédiée** |
| Tag immuable `sha-<8>` identifié | ✅ | `sha-6a358eb6`, digests API/Web vérifiés |
| Smoke tests Staging prévus | ⏳ À exécuter au déploiement | `infra/smoke/smoke-stack.sh` — couverture attendue : cycle garantie création→restitution, non-régression batch `GARANTIE_NON_RESTITUEE` |
| `docs/staging-state.md` prêt à être mis à jour | ⏳ À faire après déploiement | — |

### Isolation Staging (`STG-ISOL-01`)

| Critère | Statut |
|---|---|
| Contrôle `STG-ISOL-01` — état **avant** déploiement | ✅ **PASS** (2026-07-03, avant tout déploiement de `sha-6a358eb6`) — détail ci-dessous |
| Contrôle `STG-ISOL-01` — état **après** déploiement | ⏳ À exécuter une fois le déploiement effectué (§6) |

#### État "avant" (2026-07-03, ~08:20 UTC, avant tout déploiement)

Vérification par SSH (`ubuntu@172.31.11.102`, clé `aws_key/loyerpro-eu-central-1.pem`), sans
aucune commande de déploiement ni de nettoyage exécutée :

- **Namespace Docker** : `docker-compose.staging.yml` déclare `name: loyertracker-staging`
  (explicite, en tête de fichier). 8 conteneurs préfixés `loyertracker-staging-*` (`nginx`, `api`,
  `keycloak`, `postgres`, `alertmanager`, `pushgateway`, `prometheus`, `blackbox`), tous `Up`
  (4/4 applicatifs `healthy`), tag actuel `sha-2da27182`. Aucune collision de nom de projet avec
  les autres stacks de l'hôte.
- **Réseaux** : réseau dédié `loyertracker-staging_loyertracker-net` (namespacé par le projet).
  Autres réseaux de l'hôte (`bridge`, `host`, `none`, `ubuntu_default`) distincts, non rejoints par
  aucun service `loyertracker-staging-*`.
- **Volumes** : `loyertracker-staging_postgres-data` et `loyertracker-staging_prometheus-data`,
  namespacés par le projet. Volumes des autres projets hébergés (`infra_*`, `tools_*`, `npm_data`,
  `ubuntu_npm_*`) distincts, non référencés par le Compose de ce projet.
- **Reverse proxy mutualisé** : `nginx-proxy-manager` (autre projet) `Up 17h`, `RestartCount=0`,
  ports `80/81/443` inchangés — publication de `loyertracker-staging` par nom DNS
  (`https://loyertracker.staging.loyerpro.org`), aucune modification de sa configuration.
- **Ports** : `loyertracker-staging-nginx-1` publie `18080`/`18443` (alternatifs, sans collision
  avec `80/81/443` de `nginx-proxy-manager`) ; `pushgateway` publie `127.0.0.1:9091` uniquement
  (interne). Aucun autre service `loyertracker-staging-*` ne publie de port sur l'hôte.
- **Absence de commande Docker globale** : recherche explicite (`docker stop $(docker ps -q)`,
  `docker compose down` sans cible, `docker system prune -a`) — aucune occurrence dans
  `.github/workflows/`, `infra/`, `docker-compose*.yml`.
- **Restart count baseline (0 attendu partout)** : `api`=0, `nginx`=0, `postgres`=0, `keycloak`=0,
  `nginx-proxy-manager`=0.
- **Ressources partagées inventoriées** : `docs/staging-state.md` §11 (hôte, reverse proxy,
  registre GHCR) — inchangé depuis le Gate Staging Sprint 7+8.

**Verdict `STG-ISOL-01` (état avant) : PASS.** L'état « après » devra reproduire cette même
vérification une fois `sha-6a358eb6` déployé, avant de clore ce Gate en `STAGING_DEPLOYED`.

## 5. Analyse de risque — rollback et migration V20

**Constat non encore documenté ailleurs dans ce sprint** : la migration V20 exécute
`ALTER TABLE bail DROP COLUMN depot_garantie`. Contrairement à la contrainte `NOT NULL` posée par
V19 (Sprint 7, rollback dégradé mais possible), une colonne **supprimée** ne peut pas être lue par
l'ancien code (`sha-2da27182`) qui mappe encore `Bail.depotGarantie` sur cette colonne via
Hibernate — un rollback applicatif seul (sans restaurer le schéma) provoquerait une erreur au
démarrage ou à la première requête sur `bail` (colonne inexistante), pas seulement sur le chemin
d'inscription comme pour V19.

**Conséquence** : si un rollback est nécessaire après déploiement Staging de ce candidat, il n'y a
**aucune option de rollback applicatif seul** — seule la restauration d'un backup pré-déploiement
(`pg_restore`) permet de revenir à `sha-2da27182` en toute sécurité, avec perte des mouvements de
garantie enregistrés entre le déploiement et le rollback. Ce point n'était pas explicité dans
ADR-14 ni dans le rapport de validation Sprint 9 ; à consigner formellement avant la décision
finale de ce Gate, et à reporter tel quel au futur Gate Production de ce sprint (risque plus
critique qu'en Staging, données réelles en jeu).

**Recommandation exécutée** : sauvegarde (`pg_dump -Fc`) de la base Staging effectuée le
2026-07-03 à 09:42 UTC, avant tout déploiement — `infra/backup/backup-postgres.sh`
(`COMPOSE_FILE=docker-compose.staging.yml`), même script versionné que les Préflight Production.

| Élément | Valeur |
|---|---|
| Dump | `loyertracker-20260703-094234.dump` (362 861 octets) |
| SHA-256 dump | `910674b2b542360817cb253e41c8364bc8d57371c958b3554bcade8d69a3533a` |
| Globals | `loyertracker-20260703-094234.globals.sql` (rôles cluster) |
| SHA-256 globals | `dbfa4b171df0eaccb82de7c01791961997103a41b92e0d20f7a0c33b2b5f99cb` |
| Intégrité | `pg_restore --list` : 730 entrées — OK |
| Permissions | répertoire `700`, fichiers `600`, hors dépôt (`~/loyertracker-backups/daily`) |
| Heartbeat | poussé au Pushgateway — aucune alerte `BackupHeartbeatMissing` |

## 6. Ce qui reste à exécuter pour atteindre `STAGING_DEPLOYED`

Non couvert par ce document — à réaliser lors du déploiement effectif, puis à consigner (dans ce
document ou un rapport de suite, selon la convention retenue) :

1. ~~Sauvegarde Staging pré-déploiement (`pg_dump -Fc`)~~ **✅ Faite le 2026-07-03 09:42 UTC**
   (détail §5) — filet de sécurité en place avant tout déploiement.
2. ~~Exécution `STG-ISOL-01` (avant/après déploiement)~~ **État avant : ✅ PASS (2026-07-03,
   §4)** — état après restant à exécuter une fois le déploiement effectué.
3. ~~Déploiement `LOYERTRACKER_TAG=sha-6a358eb6`~~ **✅ Fait le 2026-07-03 ~10:10 UTC** (détail §9).
4. ~~Vérification manuelle ligne-à-ligne du backfill V20 sur les garanties réelles~~ **✅ PASS
   le 2026-07-03** (détail §9) — livrable central de ce Gate, non remplacé par
   `GarantieLedgerBackfillMigrationTest` (couverture automatisée sur données synthétiques
   uniquement).
5. ~~Smoke test Staging complet~~ **✅ 59/59 PASS le 2026-07-03** (détail §10) + ~~cycle garantie
   création→restitution~~ **✅ vérifié en direct sur l'API réelle** (§10, hors périmètre du script
   de smoke lui-même).
6. Mise à jour `docs/staging-state.md` — **restant à faire.**

## 7. Réserves et conditions

| ID | Nature | Traitement |
|----|--------|------------|
| RSV-S9-02 | Validation PO/RM formelle de clôture Sprint 9 non encore tracée distinctement du « GO technique » | **Levée le 2026-07-03** — GO PO (clôture Sprint 9) et GO RM (recevabilité `sha-6a358eb6`) tracés, aucune réserve |
| RSV-S9-03 | Rollback V20 : aucune option applicative seule, uniquement restauration backup (§5) | **Non bloquant pour Staging** (risque accepté, environnement de test) — **à reporter en condition bloquante explicite au futur Gate Production** de ce sprint |
| RSV-S9-04 | Test `S04AlertesAuditIntegrationTest` dépendant de l'horloge système, échec observé le 2026-07-03 sans lien avec Sprint 9 | **Levée le 2026-07-03** (commit `92fe9f3`) — terme de bail calculé relatif à la date d'exécution, `mvn verify` 128/128 reconfirmé |

## 8. Décision

**GO sous réserve — déploiement autorisé sous les conditions suivantes, `STAGING_DEPLOYED` non
encore atteint.**

### Conditions avant déploiement technique

1. ~~Tracer la validation PO et Release Manager de clôture Sprint 9 (RSV-S9-02)~~ **✅ Levée
   2026-07-03** — GO PO et GO RM tracés ci-dessus, sans réserve.
2. ~~Sauvegarde Staging pré-déploiement effectuée~~ **✅ Faite le 2026-07-03 09:42 UTC** (§5).

### Conditions faisant partie du déploiement lui-même

3. `STG-ISOL-01` = PASS (avant/après) — **état avant : ✅ PASS (2026-07-03)** ; état après
   restant à exécuter (§9).
4. ~~Vérification manuelle ligne-à-ligne du backfill V20 sur données réelles~~ **✅ PASS le
   2026-07-03** (§9).
5. ~~Smoke Staging complet~~ **✅ 59/59 PASS le 2026-07-03** (§10).

### Prochaine étape autorisée

`STG-ISOL-01` état après + mise à jour `docs/staging-state.md` (§6/§9), avant de clore ce Gate en
`STAGING_DEPLOYED`. **Aucune promotion Production n'est autorisée par ce document.**

## 9. Déploiement exécuté et vérification manuelle du backfill (2026-07-03)

### Déploiement

Exécuté sur `ai-test-server` (~10:10 UTC), procédure identique au Gate Staging Sprint 7+8 :

```bash
cd ~/loyertracker && git fetch origin main && git checkout main && git pull origin main
sed -i 's/^LOYERTRACKER_TAG=.*/LOYERTRACKER_TAG=sha-6a358eb6/' .env
docker compose -f docker-compose.staging.yml pull api nginx
docker compose -f docker-compose.staging.yml up -d api nginx
```

Digests vérifiés (`docker buildx imagetools inspect`) avant pull — conformes à ceux de l'en-tête
de ce document. Aucune commande Docker globale utilisée ; `up -d` a signalé les conteneurs
« orphelins » (overlay monitoring `alertmanager`/`prometheus`/`blackbox`/`pushgateway`, non inclus
dans cette invocation ciblée) sans `--remove-orphans` : **ils n'ont pas été touchés**.

| Contrôle | Résultat |
|---|---|
| Tag actif (api + web) | ✅ `sha-6a358eb6` |
| Migration V20 | ✅ « Successfully applied 1 migration... now at version v20 », sans erreur |
| 4/4 conteneurs `healthy` | ✅ api, nginx recréés ; postgres, keycloak inchangés |
| Restart count | ✅ 0 partout, `nginx-proxy-manager` intact |
| Actuator `{"status":"UP"}` | ✅ |
| `/healthz` (port 18080) | ✅ `ok` |
| Table `garantie_movement` | ✅ créée — RLS `ENABLE`+`FORCE`, policy `bailleur_isolation`, contraintes `CHECK` conformes à V20 |
| `bail.depot_garantie` | ✅ colonne supprimée (0 lignes dans `information_schema.columns`) |

### Vérification manuelle ligne-à-ligne du backfill V20 (données réelles)

3 garanties réelles présentes sur `ai-test-server` au moment du déploiement :

| Garantie | Statut | Montant | Retenu | `solde_actuel` | Mouvements générés | Invariant `solde = Σcrédit − Σdébit` |
|---|---|---|---|---|---|---|
| `aac230a1…` | DETENU | 600,00 | 0,00 | 600,00 | 1× `DEPOT_INITIAL` (crédit 600,00) | ✅ 600,00 = 600,00 |
| `67f652dc…` | RESTITUE_PARTIEL | 700,00 | 700,00 | 0,00 | `DEPOT_INITIAL` (crédit 700,00) + `AJUSTEMENT` (débit 700,00, motif « Erreur ») | ✅ 0,00 = 700,00−700,00 |
| `3b3d0dab…` | DETENU | 2100,00 | 0,00 | 2100,00 | 1× `DEPOT_INITIAL` (crédit 2100,00) | ✅ 2100,00 = 2100,00 |

Contrôles complémentaires :
- Nombre et type de mouvements conformes à la règle de backfill (rapport de validation §2) :
  `DEPOT_INITIAL` systématique, `AJUSTEMENT` uniquement si `montant_retenu > 0` (motif repris tel
  quel depuis `garantie.motif_retenue`), aucune `RESTITUTION` ici (aucune garantie
  `RESTITUE_TOTAL` réelle disponible).
- Cohérence `bailleur_id` garantie ↔ mouvement : **4/4 lignes cohérentes** (RLS non violée).
- Invariant `solde_actuel = Σcrédit − Σdébit` : **3/3 garanties OK** (requête `LEFT JOIN` +
  comparaison directe, pas seulement une relecture de `solde_actuel`).

**Verdict : PASS.**

**Limite de couverture à noter honnêtement** : les 3 garanties réelles disponibles en Staging ne
couvrent que `DETENU` et `RESTITUE_PARTIEL` — **aucune n'est `RESTITUE_TOTAL`**. Le chemin
`RESTITUTION` du backfill n'a donc pas pu être vérifié sur données réelles à ce Gate ; il reste
couvert uniquement par `GarantieLedgerBackfillMigrationTest` (données synthétiques, 3 scénarios
incluant `RESTITUE_TOTAL`). Ce n'est pas un défaut du backfill — c'est une limite du jeu de
données réel disponible en Staging à ce jour. À garder à l'esprit pour le futur Gate Production
(si des garanties `RESTITUE_TOTAL` existent en Production, la même vérification manuelle devra
les couvrir spécifiquement).

## 10. Smoke Staging complet et cycle garantie création→restitution (2026-07-03)

### Smoke test (`infra/smoke/smoke-stack.sh`)

Premier essai — **58 PASS / 1 FAIL** : `Flyway : 20 migrations (attendu 19)`. Cause : le script
n'avait pas été mis à jour après le passage de V19 (Sprint 7+8) à V20 (Sprint 9) — même type
d'écart déjà rencontré et corrigé au Gate Staging Sprint 7+8 (PR #145). **Correctif du script
uniquement** (aucun code applicatif touché), commit `088e272`, PR #158, CI verte, fusionnée
(`bcb3c0d`), resynchronisé sur `ai-test-server` (`git pull`).

Deuxième essai, après correctif :

```bash
BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.staging.yml bash infra/smoke/smoke-stack.sh
```

**Résultat : 59 PASS / 0 FAIL.** Couverture confirmée en conditions réelles : inscription,
patrimoine, bien, bail, invitation→acceptation, affectation 8 %, échéances (9 créées, `SECURITY
DEFINER`), pointage, honoraire (72,00 € — 8 % de 900 encaissés), alertes dont PREAVIS (terme à
J+75, cf. RSV-S9-04), audit, scoping gestionnaire, isolation cross-tenant live (2e bailleur, 0
fuite), RGPD US-70 (export scopé, effacement locataire 204, anonymisation confirmée), garde-fous
AuthN/ports.

**Constat** : le script `smoke-stack.sh` **n'exerce aucun endpoint garantie** (aucune occurrence
de `garantie`/`GARANTIE` dans le script) — la couverture « cycle garantie création→restitution »
annoncée au §6 de ce document ne pouvait donc pas venir de ce script. Complété manuellement
ci-dessous.

### Cycle garantie création→restitution — vérification manuelle live (hors smoke script)

Appel direct de l'API Staging réelle (JWT `bailleur-test@test.local`, échafaudage
`directAccessGrantsEnabled` temporaire sur `loyertracker-spa`, identique au patron du smoke
script, révoqué immédiatement après) sur un bail réel existant (`466d054f-…`, bailleur-test) :

1. **`POST /api/biens/{bienId}/baux/{bailId}/garanties`** (montant 850,00, CAUTION) → **201**,
   `soldeActuel: 850.00`, `statut: DETENU`. Mouvement journalisé : `DEPOT_INITIAL`, crédit 850,00,
   `utilisateur: bailleur-test@test.local` (contrairement au backfill où `utilisateur: system`).
2. **`POST .../garanties/{id}/restitution`** (`type: TOTALE`) → **200**, `soldeActuel: 0`,
   `statut: RESTITUE_TOTAL`. Mouvement journalisé : `RESTITUTION`, débit 850,00, motif
   « Restitution totale ».
3. **Invariant** `solde_actuel = Σcrédit − Σdébit` : `0.00 = 850.00 − 850.00` ✅.
4. **Audit** : `audit_log` trace `CREATE_GARANTIE` puis `RESTITUER_GARANTIE` sur la même
   `entity_id`, horodatage cohérent avec les deux appels API.
5. **Non-régression `GARANTIE_NON_RESTITUEE`** : 0 alerte de ce type en base (attendu — bail
   `ACTIF`, garantie restituée immédiatement, aucune condition de déclenchement réunie).
6. Échafaudage `directAccessGrantsEnabled` confirmé revenu à `false` après le test.

**Résultat : PASS.** Ce test ferme également, sur données réelles (bien que produites par ce test
et non préexistantes), le gap noté au §9 sur l'absence de garantie `RESTITUE_TOTAL` réelle : le
chemin `RESTITUTION` est désormais vérifié de bout en bout (API → service → ledger → cache
`solde_actuel` → audit) sur `ai-test-server`, pas seulement par le test automatisé synthétique.
