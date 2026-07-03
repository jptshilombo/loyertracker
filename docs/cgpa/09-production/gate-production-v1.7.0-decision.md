# Gate Production — Release `1.7.0` (Sprint 9 EP-12a, Garantie ledger, US-94)

| Champ | Valeur |
|---|---|
| Date | 2026-07-03 |
| Type | Release **MINOR** — Sprint 9 EP-12a (US-94, `GarantieMovement`, migration V20) |
| Version | `1.7.0` |
| Commit applicatif | `6a358eb600318e12975e3ee2de1e7137a5103105` (merge PR #152 dans `main`) |
| Tag candidat | `sha-6a358eb6` |
| Digest GHCR API | `sha256:485c8574cca057d4e00f3c0de640faf4ad8b378c302604b76a752563eb98dfba` |
| Digest GHCR Web | `sha256:70ae97f2eda455b5c9640cc33aeb6ea4abda9131222b3f948a5ea29768bca5c5` |
| Production actuelle | `1.6.0` — `sha-2da27182` |
| Rollback disponible | **Aucun rollback applicatif seul viable** — voir §5 (RSV-S9-03, acceptée) |
| Décision | **GO sous réserve acceptée** — arbitrage PO rendu et A1 exécutée, 2026-07-03 (jordan) |
| Statut | `PRODUCTION_READY` **atteint** — Préflight PASS (`preflight-backup-v1.7.0-report.md`), déploiement technique restant |

## 1. Objet

Statuer l'autorisation de promotion Production de Sprint 9 EP-12a (US-94, ledger de mouvements
`GarantieMovement`, migration rétroactive V20), depuis son état `STAGING_DEPLOYED` (tag
`sha-6a358eb6`, `docs/cgpa/07-devsecops/gate-staging-sprint9-v5.4.1-decision.md`).

En vérifiant les données réelles de Production pendant la préparation de ce Gate, une anomalie de
données non détectée en Staging a été découverte (§4). **Arbitrage rendu le 2026-07-03 (PO,
jordan)** : option **A1** retenue (reconstitution des garanties manquantes au Préflight, avant
application de V20) ; **RSV-S9-03 acceptée explicitement** (absence de rollback applicatif seul
pour V20). Les deux conditions bloquantes du §7 (version initiale) sont levées — **GO sous
réserve** : l'exécution effective de A1, avec vérification, reste une condition du Préflight
avant que Flyway n'applique V20.

## 2. Périmètre

**PR #152 — Sprint 9 EP-12a US-94** (merge commit `6a358eb6`, commits applicatifs `dc89c3a` +
`2b50290`)
- Migration **V20** : table `garantie_movement` (RLS `ENABLE`+`FORCE`, pattern V12), backfill
  rétroactif des garanties existantes, `garantie.solde_actuel` (cache transactionnel),
  `ALTER TABLE bail DROP COLUMN depot_garantie`.
- `TypeMouvementGarantie`, `GarantieMovement`, `GarantieMovementRepository`, `GarantieMovementDto`.
- `GarantieService` : journalisation de chaque mouvement (création, restitution).
- `BailDto.depotGarantie` : devient une valeur calculée (`GarantieRepository.sommeMontantDeposeParBail`,
  somme des garanties rattachées), `BailRequest.depotGarantie` retiré.
- Frontend : champ « Dépôt » retiré du formulaire de création de bail.

**Exclu (reporté Sprint 10, US-95→98)** : retenue typée, réapprovisionnement (`COMPLEMENT`), écran
d'historique des mouvements.

**Version SemVer** : `1.7.0` (MINOR) — nouvelle table/fonctionnalité additive, aucun endpoint
supprimé, cohérent avec le raisonnement déjà appliqué aux releases `1.5.0`/`1.6.0`.

## 3. Checklist Gate Production (CGPA v5.4.1)

### Identification

| Critère | Statut | Preuve |
|---|---|---|
| Périmètre Release identifié | ✅ | §2 — MINOR, Sprint 9 EP-12a (US-94) |
| Version SemVer identifiée | ✅ | `1.7.0` |
| Commit et artefact identifiés | ✅ | `6a358eb6`, tag `sha-6a358eb6`, digests vérifiés |
| Environnement source | ✅ | Staging `ai-test-server`, `STAGING_DEPLOYED` |
| Environnement cible | ✅ | Production `loyertracker-prod-server` |

### Preuves Staging

| Critère | Statut | Preuve |
|---|---|---|
| Candidat déployé en Staging | ✅ | `sha-6a358eb6` déployé le 2026-07-03 |
| Services Staging | ✅ | 9/9 conteneurs `Up`/`(healthy)`, restart=0 |
| Smoke Staging | ✅ | 59 PASS / 0 FAIL (après correctif compteur Flyway, PR #158) |
| Flyway Staging | ✅ | 20/20 — V20 appliquée sans erreur |
| `STG-ISOL-01` | ✅ **PASS** | Avant et après déploiement (gate-staging-sprint9 §4/§10bis) |
| Vérification manuelle du backfill (données réelles Staging) | ✅ **PASS** | 3/3 garanties réelles, invariant `solde = Σcrédit − Σdébit` vérifié, cohérence `bailleur_id` 4/4 |
| Cycle garantie création→restitution | ✅ **PASS** | Vérifié en direct sur l'API Staging réelle (le script de smoke n'exerce aucun endpoint garantie) |
| Accumulation Staging | ✅ | Aucun commit applicatif postérieur à `sha-6a358eb6` — candidat figé |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---|---|---|
| Sprint 9 validé | ✅ | GO PO de clôture tracé le 2026-07-03, rapport `sprint-9-garantie-ledger-rapport-validation.md` |
| Release notes disponibles | ⚠️ | Non encore rédigées pour `1.7.0` — à produire avant déploiement (pattern `release-notes-v1.6.0.md`) |
| Changelog disponible | ✅ | `CHANGELOG.md` `[Non publié]` contient la section Sprint 9 (US-94) — **mais porte toujours aussi le contenu Sprint 5/6/7/8 jamais scindé** (RP-160-03, dette pré-existante non liée à Sprint 9, cf. §5) |

### Contrôles techniques et DevSecOps

| Critère | Statut | Preuve |
|---|---|---|
| Build CI stable | ✅ | `6a358eb6` — 7/7 jobs SUCCESS |
| Tests backend | ✅ | 128/128 (`GarantieLedgerBackfillMigrationTest` inclus) |
| Tests frontend | ✅ | 63/63 |
| Gitleaks / SCA / Trivy | ✅ | Job Sécurité SUCCESS |
| SonarQube | ✅ | Violation S107 détectée et corrigée avant merge |
| Migrations Production | ⚠️ | **V20 à appliquer** — voir §4, anomalie de données découverte sur les baux réels |
| Observabilité | ✅ | Dispositif Production existant inchangé (5 cibles Prometheus, Alertmanager) |
| Secrets | ✅ | `.env` hors dépôt, digests immuables confirmés |

### Rollback

| Critère | Statut | Note |
|---|---|---|
| Tag rollback applicatif | ⚠️ | `sha-2da27182` (`1.6.0`) disponible sur GHCR, **mais rollback applicatif seul non viable**, voir ci-dessous |
| Migration V20 et rollback | ❌ **Bloquant** | V20 exécute `ALTER TABLE bail DROP COLUMN depot_garantie`. Contrairement à V19 (Sprint 7, contrainte `NOT NULL`, rollback dégradé mais possible), une colonne **supprimée** ne peut pas être lue par l'ancien code (`sha-2da27182`) qui mappe encore `Bail.depotGarantie` sur cette colonne via Hibernate — un rollback applicatif seul provoquerait une erreur sur **toute requête `bail`**, pas seulement sur le chemin d'inscription comme pour V19 |
| Procédure si rollback nécessaire | ✅ documentée | Seule option : restauration complète du backup pré-déploiement (`pg_restore`), qui annule aussi V20 — **perte de tout mouvement de garantie enregistré entre le déploiement et le rollback** |
| Responsable rollback | ✅ | DevSecOps Lead, coordination Release Manager |

## 4. Analyse de risque — anomalie de données découverte en Production (RSV-PROD-S9-01)

**Constat, vérifié par requête SQL directe sur `loyertracker-prod-server` (lecture seule,
2026-07-03) :**

```sql
SELECT statut, count(*), sum(montant), sum(montant_retenu) FROM garantie GROUP BY statut;
-- statut | count | total_montant | total_retenu
-- DETENU |     1 |       2100.00 |         0.00
```

Une seule garantie réelle existe en Production (cas simple, `DETENU`, aucune retenue) — le volume
de la migration est donc faible. Mais :

```sql
SELECT b.id, b.depot_garantie, g.id AS garantie_id, g.montant
FROM bail b LEFT JOIN garantie g ON g.bail_id = b.id ORDER BY b.date_debut;

--                bail_id                | depot_garantie |             garantie_id              | montant
-- 8c905d18-b5ea-491a-84bb-50157e0824dd  |        2100.00 | 550f1d84-b88a-4de9-badc-459b4382681e | 2100.00
-- 659ea02c-79b6-4559-8401-2996e1d62152  |         600.00 |                                       |
-- cb653273-047d-42f5-a6c1-c73d88c7f5cc  |         600.00 |                                       |
```

**Deux des trois baux réels ont `bail.depot_garantie = 600.00` sans aucune ligne `garantie`
correspondante.** Confirmé par lecture du code sur `main` : `BailDto.depotGarantie` est un champ
**exclusivement calculé** (`BailService` → `GarantieRepository.sommeMontantDeposeParBail`, qui ne
lit que la table `garantie`), sans aucun repli sur la colonne legacy. Après le déploiement de V20 :
- Ces 2 baux afficheront **`depotGarantie: 0`** au lieu de `600.00`, immédiatement et
  silencieusement, sur tout endpoint qui expose un `BailDto` (dashboards bailleur et gestionnaire).
- L'**export RGPD** (`RgpdService`/`ExportBailleurDto`), un document légal remis au locataire ou au
  bailleur, affichera également `0` pour ces deux baux.

**Ce cas ne s'est pas produit en Staging** : les 3 garanties réelles de `ai-test-server`
correspondaient chacune à un bail avec une ligne `garantie` existante — aucune vérification
(automatisée ou manuelle) n'a donc exercé ce scénario. `GarantieLedgerBackfillMigrationTest` ne
couvre pas non plus ce cas (3 scénarios synthétiques : `DETENU`, `RESTITUE_PARTIEL`,
`RESTITUE_TOTAL`, tous avec une ligne `garantie` présente).

**Fait corroborant** : le panneau « Garanties » de l'UI (`garanties-bail.component.ts`) lit déjà
aujourd'hui, avant toute migration, exclusivement la table `garantie` — ces 2 baux affichent donc
déjà « Aucune garantie » dans ce panneau, alors que `depotGarantie` vaut encore 600,00 par ailleurs
(lecture directe de la colonne, avant que `BailDto` ne devienne calculé). La double source de
vérité qu'ADR-14 décrivait en théorie (« deux sources de vérité coexistent déjà aujourd'hui ») a
donc **déjà divergé concrètement** sur ces 2 baux réels — la migration ne fait que rendre cette
divergence visible dans le champ numérique lui-même, au lieu de seulement dans le panneau détaillé.

### Options de remédiation (à arbitrer par le PO — aucune n'est retenue par ce document)

| Option | Description | Exactitude des données | Coût / délai |
|---|---|---|---|
| **A1 — Reconstitution au Préflight** | Avant d'appliquer V20, créer manuellement les 2 lignes `garantie` manquantes (montant repris de `bail.depot_garantie`, pendant que la colonne existe encore) — le backfill V20 génère alors normalement leur mouvement `DEPOT_INITIAL` | Bonne — montant exact restauré ; `date_depot`/`utilisateur` reconstitués, à annoter explicitement (`commentaire: reconstitution Préflight V20`) | Faible — aucun nouveau code, aucune CI, aucun rejeu du Gate Staging déjà clos |
| **A2 — Correctif de la migration V20 elle-même** | Étendre V20 pour reconstituer ces cas automatiquement, dans la migration | La plus rigoureuse — logique au bon endroit, couverte par un nouveau test automatisé | Élevé — V20 est **déjà appliquée en Staging** (checksum Flyway figé) : nécessite de restaurer Staging et de **rejouer tout le Gate Staging Sprint 9** (STG-ISOL-01, backfill, smoke, cycle live), annule la décision `STAGING_DEPLOYED` déjà prise, nouvelle CI complète |
| **B — Acceptation du risque + recréation post-déploiement** | Déployer V20 tel quel ; recréer manuellement les 2 dépôts via le flux « Ajouter garantie » existant immédiatement après déploiement | Rétablit le solde actuel correct, mais **perd la date de dépôt d'origine et l'historique réel** (nouvelle entrée datée d'aujourd'hui) — perte de provenance à documenter explicitement comme risque accepté | Le plus rapide — aucun nouveau code/CI, mais nécessite un accord PO écrit et explicite (pas un contournement silencieux) |
| **C — Reporter la décision** | Ne pas déployer tant que le PO n'a pas tranché entre A1/A2/B | Aucun risque pris | Retarde la livraison ; c'est l'état par défaut tant qu'aucune option n'est choisie |

## 5. Réserves et conditions

| ID | Nature | Sévérité | Traitement |
|----|--------|----------|------------|
| **RSV-PROD-S9-01** | Anomalie de données réelle découverte en Production : 2 baux perdraient silencieusement l'affichage de leur dépôt (§4) | **✅ Levée le 2026-07-03** — option A1 exécutée au Préflight : 2 lignes `garantie` reconstituées (`ef87b3aa-…`, `01754057-…`, 600,00 chacune), vérifiées par requête SQL (3/3 baux ont désormais une garantie correspondante). Détail : `preflight-backup-v1.7.0-report.md` §3 |
| **RSV-S9-03** | Migration V20 (`DROP COLUMN bail.depot_garantie`) : aucune option de rollback applicatif seul, restauration de backup uniquement (§3 Rollback) | **Acceptée explicitement le 2026-07-03** par le Product Owner (jordan) — Chief Delivery Officer (Claude Code) coordonne l'exécution en connaissance de ce risque |
| RP-160-03 | `CHANGELOG.md` `[Non publié]` mélange encore du contenu déjà livré (Sprint 5/6/7/8) avec du contenu non livré | **✅ Levée le 2026-07-03** — `[Non publié]` scindé en `[1.5.0]` (2026-07-01), `[1.6.0]` (2026-07-02), `[1.7.0]` (2026-07-03), liens de comparaison mis à jour |
| — | Release notes `1.7.0` non encore rédigées | Non bloquant pour cette analyse | À produire avant le Préflight, si ce Gate progresse vers un GO |

## 6. Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | **GO sous réserve** — traçabilité Staging complète et solide (STG-ISOL-01, backfill vérifié, smoke, cycle live), anomalie de données arbitrée par le PO (A1), risque de rollback accepté explicitement |
| Enterprise Architect | **GO sous réserve** — la combinaison migration destructive (`DROP COLUMN`) + absence de rollback reste un profil de risque élevé, mais désormais couvert par une acceptation explicite et nommée du PO, condition suffisante pour procéder |
| DevSecOps Lead | **GO sous réserve** — CI et preuves techniques Staging complètes ; A1 à exécuter et vérifier au Préflight avant toute application de V20 |
| Release Manager | **GO sous réserve** — candidat techniquement recevable (`sha-6a358eb6`) ; conditions PO/rollback actées, A1 exécutée et vérifiée au Préflight |
| Product Owner | **GO** — option **A1** retenue pour RSV-PROD-S9-01, RSV-S9-03 acceptée explicitement, 2026-07-03 (jordan) |

## 7. Décision finale

**Chief Delivery Officer : GO sous réserve acceptée.**

- `PRODUCTION_READY` : **atteint**, sous condition d'exécution de A1 au Préflight.
- `PRODUCTION_DEPLOYED` : non atteint — déploiement à exécuter selon le plan Préflight →
  Déploiement → Validation finale → Hypercare.

### Conditions bloquantes avant déploiement technique

1. ~~**RSV-PROD-S9-01** — décision PO~~ **✅ Levée** : option A1 exécutée et vérifiée au Préflight
   (`preflight-backup-v1.7.0-report.md` §3) — 2 lignes `garantie` reconstituées, 3/3 baux
   cohérents.
2. ~~**RSV-S9-03** — acceptation écrite explicite du risque de rollback~~ **✅ Acceptée** par le
   Product Owner (jordan), 2026-07-03.

**Les deux conditions bloquantes sont levées.** Prochaine étape autorisée : déploiement
technique `1.7.0` (`api` + `nginx`, migration V20), sous décision distincte.

### Services cibles

`api` + `nginx` — migration V20 sur le schéma Postgres. `postgres`, `keycloak` et le monitoring
restent inchangés.

### Prochaines étapes

| Étape | Statut | Document |
|---|---|---|
| Arbitrage PO sur RSV-PROD-S9-01 (A1) et acceptation RSV-S9-03 | ✅ Fait (2026-07-03) | Ce document §5/§6, `docs/project-state.md` |
| Préflight + backup Production + exécution A1 (reconstitution des 2 garanties) | ✅ Fait (2026-07-03) | `preflight-backup-v1.7.0-report.md` |
| Déploiement technique | ✅ Fait (2026-07-03) | `deploiement-technique-v1.7.0-report.md` |
| Validation finale + smoke Production | ✅ Fait (2026-07-03) — `PRODUCTION_DEPLOYED` atteint | `validation-finale-v1.7.0-report.md` |
| Hypercare + clôture | ⏳ À exécuter | `plan-etape-hypercare-v1.7.0.md` |
