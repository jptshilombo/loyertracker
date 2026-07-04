# Gate Production — Release `1.8.0` (Sprint 10 EP-12b, Garantie usage métier, US-95/96/97)

| Champ | Valeur |
|---|---|
| Date | 2026-07-04 |
| Type | Release **MINOR** — Sprint 10 EP-12b (US-95/96/97, migration V21) + correctif RSV-S10-01 |
| Version | `1.8.0` |
| Commit applicatif | `2c5f43c7f6e854a9b1649e531d6711c07875a85e` (merge PR #173 dans `main` — contient le merge PR #168 `1d1c2a5d`) |
| Tag candidat | `sha-2c5f43c7` |
| Digest GHCR API | `sha256:bab66aa35d9b70d045be284cd0132746e36377110dd29a3079f1ca821a2b45a5` |
| Digest GHCR Web | `sha256:9c8915f0eca279bf75c548d7a4846d48c266a9d44be7c9b405bd526847cd3f87` |
| Production actuelle | `1.7.0` — `sha-6a358eb6` (release clôturée CDO GO 2026-07-04) |
| Rollback disponible | **Oui — rollback applicatif seul viable** vers `sha-6a358eb6` (V21 additive, FK nullable) — voir §3 Rollback |
| Décision | **NO GO en l'état — en attente d'arbitrage PO sur RSV-S10-02 (§4.1)** ; le reste de la checklist est PASS |
| Statut | `PRODUCTION_READY` **non atteint** |

## 1. Objet

Statuer l'autorisation de promotion Production de Sprint 10 EP-12b (US-95 retenue explicite,
US-96 complément, US-97 historique + export, migration V21), depuis son état `STAGING_DEPLOYED`
(tag `sha-1d1c2a5d`, `docs/cgpa/07-devsecops/gate-staging-sprint10-v5.4.1-decision.md`), **en y
intégrant le correctif RSV-S10-01** (tri stable du ledger intra-jour, PR #173) dont l'inclusion
dans le tag candidat était une condition explicite posée par le Gate Staging Sprint 10 (§8) et
par l'addendum de levée (§9).

Cette exigence crée un **écart candidat/Staging** : le tag candidat `sha-2c5f43c7` (qui contient
le correctif) n'est pas le tag validé en Staging (`sha-1d1c2a5d`, qui ne le contient pas). Cet
écart est analysé au §4.1 et consigné **RSV-S10-02, bloquante**, avec deux options d'arbitrage
présentées au PO — aucune n'est retenue par ce document.

## 2. Périmètre

**PR #168 — Sprint 10 EP-12b US-95/96/97** (merge commit `1d1c2a5d`, validé en Staging) :

- Migration **V21** : `paiement.garantie_movement_id` (colonne nullable + FK vers
  `garantie_movement` + index) — **additive, aucun backfill, aucune suppression**.
- **US-95** : `POST .../garanties/{id}/retenue-loyer` — retenue explicite, jamais automatique
  (ADR-14 §5) ; transition du paiement couvert vers `RECU`/`PARTIEL`, recalcul des honoraires,
  gardes 400 (solde/reste dû) et 409 (paiement déjà couvert).
- **US-96** : `POST .../garanties/{id}/complement` — motif obligatoire, audit
  `COMPLEMENT_GARANTIE`.
- **US-97** : `GET .../mouvements` + export CSV (échappé anti formula-injection), UI
  triable/filtrable, export RGPD incluant le ledger complet (chargement batch, sans N+1).
- Correctif embarqué : `Garantie.restituerPartiel` calcule depuis `soldeActuel` — critique dès
  qu'une retenue ou un complément existe.

**PR #173 — correctif RSV-S10-01** (commit `71a7a73`, merge `2c5f43c7`, **postérieur au Gate
Staging**) :

- `garantie_movement.cree_le` (`TIMESTAMPTZ DEFAULT now()`, présent depuis V20 mais non mappé)
  mappé en lecture seule (`insertable = false`) ; tri du ledger rendu stable
  `date_mouvement, cree_le, id` (repository US-97 + chargement batch de l'export RGPD).
- Test d'intégration : 3 mouvements le même jour, ordre vérifié sur `GET /mouvements`.
- **Aucune migration Flyway** — le compteur reste 21 (le smoke, aligné par PR #171, reste
  valide tel quel).

Entre `1d1c2a5d` et `2c5f43c7`, les seuls autres commits sont des merges **documentation
uniquement** (PR #169, #170, #172) et le correctif du **script** de smoke (PR #171, hors images).
**Le delta applicatif entre le tag validé en Staging et le tag candidat se réduit donc au seul
commit `71a7a73`** (2 classes backend modifiées, 1 repository, 1 service RGPD, 1 test).

**Version SemVer** : `1.8.0` (MINOR) — nouveaux endpoints additifs, migration additive, aucun
endpoint supprimé ; cohérent avec le raisonnement des releases `1.5.0` → `1.7.0`.

## 3. Checklist Gate Production (CGPA v5.4.1)

### Identification

| Critère | Statut | Preuve |
|---|---|---|
| Périmètre Release identifié | ✅ | §2 — MINOR, Sprint 10 EP-12b + correctif RSV-S10-01 |
| Version SemVer identifiée | ✅ | `1.8.0` |
| Commit et artefact identifiés | ✅ | `2c5f43c7`, tag `sha-2c5f43c7`, digests GHCR relevés (en-tête) |
| Environnement source | ⚠️ | Staging `ai-test-server` — `STAGING_DEPLOYED` porte sur `sha-1d1c2a5d`, **pas sur le candidat** (§4.1, RSV-S10-02) |
| Environnement cible | ✅ | Production `loyertracker-prod-server` |

### Preuves Staging

| Critère | Statut | Preuve |
|---|---|---|
| Candidat déployé en Staging | ⚠️ **RSV-S10-02** | `sha-1d1c2a5d` déployé et validé (Gate Staging Sprint 10 GO) ; le candidat `sha-2c5f43c7` ajoute le seul commit `71a7a73`, **non stagé** — arbitrage §4.1 |
| Services Staging | ✅ | 9/9 conteneurs, restart=0 (Gate Staging §3/§4) |
| Smoke Staging | ✅ | 59/0 **au premier passage** (compteur Flyway aligné avant déploiement, PR #171) |
| Flyway Staging | ✅ | 21/21 — V21 appliquée sans erreur |
| `STG-ISOL-01` | ✅ **PASS** | Avant/après (Gate Staging §4) — l'incident de bind port 80 est un échec de bind refusé par le noyau, sans perturbation de NPM |
| Vérification fonctionnelle US-95/96/97 | ✅ **PASS** | 20 contrôles manuels en direct sur l'API réelle (chemins nominaux + 400/409), invariant ledger 4/4, nettoyage synthétique 0 résidu (Gate Staging §6) |
| Accumulation Staging analysée | ✅ | Delta candidat/stagé = 1 commit applicatif (`71a7a73`) + docs/script smoke — §2 et §4.1 |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---|---|---|
| Sprint 10 validé | ✅ | PR #168 mergée (clôture côté `main`, 2026-07-04) ; promotion Staging demandée par le PO après clôture `1.7.0` ; Gate Staging GO |
| Correction RSV-S10-01 exigée avant ce Gate | ✅ | Levée le 2026-07-04 (PR #173, addendum §9 du Gate Staging) — condition du Gate Staging §8 satisfaite |
| Release notes disponibles | ⚠️ | Non encore rédigées pour `1.8.0` — à produire avant déploiement (pattern `docs/release-notes-v1.6.0.md`) |
| Changelog disponible | ⚠️ | `[Non publié]` porte le contenu Sprint 10 ; la ligne du correctif RSV-S10-01 est ajoutée par la PR du présent dossier ; promotion `[Non publié]` → `[1.8.0]` (avec date) à faire avant déploiement |

### Contrôles techniques et DevSecOps

| Critère | Statut | Preuve |
|---|---|---|
| Build CI stable sur le candidat | ✅ | `2c5f43c7` (push `main`) : CI + CodeQL SUCCESS ; PR #173 : 7/7 checks SUCCESS |
| Quality Gates SonarQube | ✅ | Passés en CI sur PR #168 (pré-validés sur l'instance réelle, correctif `b0b797f`) et sur PR #173 |
| Gitleaks / SCA / Trivy | ✅ | Job Sécurité SUCCESS sur le candidat |
| Migrations Production | ✅ profil bas | **V21 additive** : colonne nullable + FK + index, aucun backfill, aucune suppression — sans équivalent avec le profil destructif de V20 (§4.2) |
| Observabilité | ✅ | Dispositif Production existant inchangé (5 cibles Prometheus, Alertmanager) |
| Secrets | ✅ | `.env` hors dépôt, tags immuables `sha-<8>` + digests GHCR relevés |

### Rollback

| Critère | Statut | Note |
|---|---|---|
| Tag rollback applicatif | ✅ | `sha-6a358eb6` (`1.7.0`) disponible sur GHCR |
| Migration V21 et rollback | ✅ **viable** | V21 est additive : l'ancien code (`sha-6a358eb6`) ne mappe pas `paiement.garantie_movement_id` — la colonne resterait en place, ignorée. Contraste explicite avec V20/RSV-S9-03 (colonne **supprimée**, rollback applicatif impossible) |
| Limite documentée | ⚠️ à accepter | Un rollback après usage réel des nouveaux endpoints laisserait en base des mouvements `RETENUE_LOYER`/`COMPLEMENT` et des liaisons `garantie_movement_id` créés entre-temps : soldes et invariant restent cohérents (le code `1.7.0` lit déjà ledger + `solde_actuel`), mais les transitions de paiement opérées par une retenue ne seraient plus « explicables » dans l'UI `1.7.0` (pas d'écran historique). À qualifier au moment d'un éventuel rollback, pas bloquant |
| Conditions de déclenchement | ✅ | Mêmes seuils que `1.7.0` : échec smoke, 5xx, invariant ledger violé, migration en échec |
| Responsable rollback | ✅ | DevSecOps Lead, coordination Release Manager |

## 4. Analyse de risque

### 4.1 Écart candidat/Staging — RSV-S10-02 (bloquante, à arbitrer par le PO)

Le Gate Staging Sprint 10 a validé `sha-1d1c2a5d` et a **exigé** le traitement de RSV-S10-01
avant le Gate Production. Le correctif (PR #173) a été mergé **après** — le tag candidat qui
l'inclut (`sha-2c5f43c7`) n'a donc **jamais tourné en Staging**. Le delta applicatif est minimal
(1 commit backend : mapping lecture seule + clause de tri + test), mais la checklist « éléments
candidats déployés ou vérifiés en Staging » n'est pas intégralement satisfaite au sens strict.

| Option | Description | Couverture | Coût / délai |
|---|---|---|---|
| **S1 — Redéploiement Staging ciblé du candidat (recommandée)** | Déployer `sha-2c5f43c7` sur `ai-test-server` (bascule de tag seule, **aucune migration** — Flyway reste 21/21), STG-ISOL-01 avant/après, smoke 59, **vérification live de l'ordre intra-jour sur les mouvements réels du 2026-07-04** (le désordre `RETENUE_LOYER, DEPOT_INITIAL, COMPLEMENT` constaté à l'ouverture de RSV-S10-01 doit devenir `DEPOT_INITIAL, RETENUE_LOYER, COMPLEMENT`) | Complète — le candidat exact est stagé **et** le correctif est vérifié sur le cas réel qui a motivé la réserve | Faible — session Staging courte (~15 min), pas de nouvelle CI, pas de rejeu du Gate fonctionnel US-95/96/97 (déjà validé, code identique) |
| **S2 — Acceptation PO du delta non stagé** | Promouvoir `sha-2c5f43c7` directement, sur la foi de : delta = 1 commit, CI 7/7 verte, test d'intégration dédié, aucune migration | Partielle — le correctif n'aura jamais été observé en environnement réel avant la Production | Nul — mais requiert un accord PO écrit et explicite (pas un contournement silencieux) |

**Recommandation CDO : S1.** Le précédent Sprint 9 (RSV-PROD-S9-01, anomalie découverte
uniquement grâce aux vérifications sur données réelles) plaide pour ne pas court-circuiter la
preuve en environnement réel, d'autant que le coût de S1 est faible et que Staging détient
précisément le jeu de données qui a révélé le défaut.

### 4.2 Données réelles Production

**Lecture directe non effectuée dans cette session** (accès au serveur de Production non
autorisé sans approbation explicite ; hôte par ailleurs volontairement éteint hors sessions —
pratique documentée). L'analyse s'appuie sur l'état vérifié il y a quelques heures : hypercare
`1.7.0` T+12/T+24 PASS (2026-07-04, 10:18 UTC) — Flyway 20/20, invariant
`garantie.solde_actuel = Σ mouvements` 3/3, 0 erreur API, tag/digests sans dérive.

Profil de risque données de V21, par construction :

- Prérequis unique : existence de `garantie_movement` (V20, appliquée et vérifiée en Production).
- Colonne **nullable**, aucun backfill : aucune ligne `paiement` existante n'est modifiée, aucune
  contrainte ne peut échouer sur les données réelles — le mode de défaillance « données
  divergentes » de V20 (RSV-PROD-S9-01) n'a pas d'équivalent ici.
- Le tri corrigé (RSV-S10-01) lit `cree_le`, posé par Postgres depuis V20 sur toutes les lignes
  (y compris le backfill V20 et les reconstitutions A1) — aucune valeur NULL attendue.

**Garde-fou hérité du Sprint 9 (condition de Préflight, non bloquante pour ce Gate)** : au
Préflight, avant migration, vérifier en lecture seule (1) `garantie_movement.cree_le IS NOT NULL`
sur toutes les lignes, (2) l'invariant ledger 3/3, (3) l'absence de colonne préexistante
`paiement.garantie_movement_id`.

### 4.3 Synthèse

Le profil de risque de `1.8.0` est **nettement plus bas** que celui de `1.7.0` : migration
additive (vs destructive), rollback applicatif seul viable (vs restauration de backup
uniquement), aucune anomalie de données possible par construction (vs RSV-PROD-S9-01). Le seul
point ouvert est procédural : la preuve Staging du candidat exact (§4.1).

## 5. Réserves et conditions

| ID | Nature | Sévérité | Traitement |
|----|--------|----------|------------|
| **RSV-S10-02** | Le tag candidat `sha-2c5f43c7` (incluant le correctif RSV-S10-01 exigé) n'a pas été déployé en Staging — delta d'un commit backend par rapport au tag validé `sha-1d1c2a5d` | **Bloquante** | **À arbitrer par le PO** : option S1 (redéploiement Staging ciblé + vérification live, recommandée) ou S2 (acceptation écrite du delta) — §4.1. Assignée PO (jordan), 2026-07-04 |
| RSV-S10-01 | Ordre intra-jour du ledger non déterministe | **✅ Levée le 2026-07-04** | PR #173 (`2c5f43c7`), incluse dans le candidat — addendum §9 du Gate Staging Sprint 10 |
| RSV-S9-03 | Aucun rollback applicatif seul pour V20 (héritée, acceptée permanente) | Acceptée | **Ne s'applique pas à V21** (additive) ; reste vraie pour tout retour antérieur à `1.7.0`, hors périmètre de ce Gate |
| — | Vérifications lecture seule au Préflight (§4.2) : `cree_le` non NULL, invariant 3/3, absence de colonne préexistante | Condition de Préflight | À exécuter avec le backup pré-déploiement, avant migration V21 |
| — | Release notes `1.8.0` non rédigées ; `CHANGELOG.md` `[Non publié]` à promouvoir en `[1.8.0]` daté | Non bloquant pour cette analyse | À produire avant le déploiement technique si ce Gate progresse vers un GO |

## 6. Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | **NO GO en l'état** — dossier complet et traçable, mais la lettre de la checklist (« candidat déployé ou vérifié en Staging ») exige l'arbitrage RSV-S10-02 ; S1 la satisfait entièrement à coût faible |
| Enterprise Architect | **GO sous condition S1 ou S2** — profil de risque technique bas (migration additive, rollback viable) ; le delta d'un commit de tri est architecturalement bénin, mais la vérification live sur les données Staging qui ont révélé le défaut (S1) est la seule preuve empirique possible du correctif |
| DevSecOps Lead | **GO sous condition S1** — recommande S1 : bascule de tag sans migration, STG-ISOL-01, smoke déjà aligné (21/21), vérification de l'ordre sur les mouvements réels du 2026-07-04 |
| Release Manager | **GO sous condition** — candidat `sha-2c5f43c7` techniquement recevable (CI verte, images GHCR présentes, digests relevés) ; recevabilité définitive suspendue à RSV-S10-02 |
| Product Owner | **⏳ Arbitrage attendu** — choix S1/S2 (RSV-S10-02) |

## 7. Décision finale

**Chief Delivery Officer : NO GO en l'état — en attente de l'arbitrage PO sur RSV-S10-02.**

- `PRODUCTION_READY` : **non atteint**.
- Tous les autres critères de la checklist sont PASS ou couverts par des conditions de Préflight
  définies (§4.2, §5).
- **Recommandation CDO : option S1** (redéploiement Staging ciblé de `sha-2c5f43c7`, sans
  migration, avec vérification live de l'ordre intra-jour) — elle transforme la réserve en preuve
  positive pour un coût d'une session Staging courte.

### Conditions bloquantes avant `PRODUCTION_READY`

1. **RSV-S10-02** — arbitrage PO : S1 exécutée avec succès (STG-ISOL-01 PASS, smoke 59/0, ordre
   intra-jour vérifié) **ou** acceptation écrite explicite du delta non stagé (S2).

### Conditions de Préflight (si GO prononcé ensuite)

1. Sauvegarde pré-déploiement vérifiée (`pg_dump -Fc`, `pg_restore --list`).
2. Vérifications lecture seule §4.2 (avant migration V21).
3. Release notes `1.8.0` rédigées ; `CHANGELOG.md` promu `[1.8.0]` daté.

### Services cibles

`api` + `nginx` — migration V21 (additive) sur le schéma Postgres. `postgres`, `keycloak` et le
monitoring restent inchangés.

### Prochaines étapes

| Étape | Statut | Document |
|---|---|---|
| Arbitrage PO RSV-S10-02 (S1/S2) | ⏳ Attendu | Ce document §4.1/§5 |
| Si S1 : redéploiement Staging `sha-2c5f43c7` + vérifications ciblées | ⏳ | Consigner dans `staging-state.md` + addendum de ce document |
| Mise à jour de ce Gate (GO / GO sous réserve) | ⏳ | Ce document |
| Préflight + backup Production + vérifications §4.2 | ⏳ | `preflight-backup-v1.8.0-report.md` (à créer) |
| Déploiement technique | ⏳ | `deploiement-technique-v1.8.0-report.md` (à créer) |
| Validation finale + smoke Production | ⏳ | `validation-finale-v1.8.0-report.md` (à créer) |
| Hypercare + clôture | ⏳ | `plan-etape-hypercare-v1.8.0.md` (à créer) |
