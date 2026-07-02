# Gate Production — Release `1.6.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-02 |
| Type | Release MINOR — Sprint 7 EP-10 Patrimoine enrichi (US-90) + Sprint 8 EP-11 Money/Devise (US-92/93), combinés (même artefact HEAD) |
| Version | `1.6.0` |
| Commit applicatif | `2da2718225a3a42ae1207948713c1a3000d1817a` (PR #143 code + #144 docs merge) |
| Tag candidat | `sha-2da27182` |
| Digest GHCR API | `sha256:ecdd14084db6fcd5a556dac5ec8f6c62ee0c0303fce4475c2ee0fb8e959b1f3f` |
| Digest GHCR Web | `sha256:64263317fd09874f910a309e22b09e748529eb671b2202a76f643667bde920aa` |
| Production actuelle | `1.5.0` — `sha-08b366fa` |
| Rollback disponible | `sha-08b366fa` — applicatif seul, **avec réserve** (voir §Rollback) |
| Décision | **GO sous réserve** |
| Statut | **`PRODUCTION_READY`** (conditionnel — réserves bloquantes §4) |

## 1. Objet

Statuer l'autorisation Production du lot combiné `1.6.0`, composé de :
- **Sprint 7 EP-10 (US-90)** : Patrimoine enrichi — validé en Staging le 2026-07-02 (premier
  passage Staging de ce sprint, jamais déployé isolément).
- **Sprint 8 EP-11 (US-92/93)** : VO `Money`, correctif devise documents, devise
  Paiements/Honoraires — validé en Staging le 2026-07-02.

Les deux sprints ont été rattrapés en un seul Gate Staging (`sha-2da27182`,
`gate-staging-sprint7-8-v5.4.1-decision.md`) car Sprint 7 avait été fusionné sur `main` sans
passer son propre Gate Staging — écart déjà qualifié et accepté par le PO à cette étape. Ce
Gate Production suit la même logique combinée : il n'existe pas d'artefact HEAD distinct pour
promouvoir Sprint 7 seul.

## 2. Périmètre

### Inclus

**PR #143 — Sprint 8 EP-11 Money/Devise** (merge commit `77549a9`, code `a478e96`)
- VO `Money(montant, devise)` (`com.loyertracker.baux.Money`), corrige `DocumentHtmlBuilder`
  (suffixe « € » codé en dur).
- `DonneesDocument` porte des `Money` ; `QuittanceService.assembler()` résout `bail.getDevise()`.
- `PaiementDto`/`HonoraireDto` exposent `devise` ; `MoneyFormatPipe` frontend partagé.
- Aucune migration Flyway (`bail.devise` existe depuis V17).

**PR #144 — docs(project-state)** (merge commit `2da2718`, doc-only)
- Confirmation de fusion, aucun changement de code applicatif.

**Sprint 7 EP-10 US-90** (déjà fusionné sur `main` avant cette session, PR #131 — antérieur à ce
Gate mais jamais promu en Staging/Production)
- Migration **V19** : 7 champs optionnels (`ville, commune, quartier, province_etat, pays,
  description, reference_interne`), `patrimoine.adresse` `NOT NULL` (backfill générique
  `"Adresse à renseigner"`).
- `PatrimoineRequest.adresse` désormais `@NotBlank` côté API.
- Extension du formulaire « Modifier un patrimoine » (US-91, décision kickoff : inline, pas de
  CRUD dédié).

### Exclus (candidat figé, même logique que Gate Production `1.5.0`)

- **PR #145** (`fix(smoke)`, commit `837ef31`) et le doc PR #146 (Gate Staging decision + mises à
  jour `staging-state.md`/`project-state.md`) : postérieurs au tag `sha-2da27182`. Vérifié —
  `git diff 2da2718..origin/main -- backend/ frontend/ infra/nginx/ docker-compose*.yml` est
  **vide** : aucun changement de code applicatif, Nginx ou Compose après le candidat. `PR #145`
  ne touche que `infra/smoke/smoke-stack.sh` (script exécuté depuis l'hôte, hors image Docker).
  Le candidat Production reste figé à `sha-2da27182`, tag exact vérifié en Staging.
- Aucune modification Keycloak, réseau ou volume.

## 3. Checklist Gate Production (CGPA v5.4.1)

### Identification

| Critère | Statut | Preuve |
|---|---|---|
| Périmètre Release identifié | ✅ | §2 — MINOR SemVer, Sprint 7 (Patrimoine) + Sprint 8 (Money/Devise) |
| Version SemVer identifiée | ✅ | `1.6.0` — MINOR (nouveaux champs additifs + resserrement validation `adresse`, aucune suppression d'endpoint) |
| Commit et artefact identifiés | ✅ | `2da2718` applicatif+docs ; tag `sha-2da27182` ; digests vérifiés lors du Gate Staging |
| Environnement source | ✅ | Staging `ai-test-server` (`https://loyertracker.staging.loyerpro.org`) |
| Environnement cible | ✅ | Production `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |

### Preuves Staging

| Critère | Statut | Preuve |
|---|---|---|
| Candidat déployé en Staging | ✅ | `sha-2da27182` déployé le 2026-07-02 |
| Services Staging | ✅ | 9/9 conteneurs `Up`/`(healthy)` ; restart count = 0 |
| Smoke Staging | ✅ | **59 PASS / 0 FAIL** (après correctif script PR #145, hors image) |
| Flyway Staging | ✅ | 19/19 — V19 appliquée sans erreur |
| `STG-ISOL-01` | ✅ **PASS** | Avant/après — 9 conteneurs `loyertracker-staging-*`, `nginx-proxy-manager` intact, restart=0 |
| Vérification comportementale US-90 | ✅ **PASS** | `POST /api/patrimoines` sans `adresse` → 400 constaté (via l'échec initial du smoke, cause racine confirmée) ; avec `adresse` → 201, round-trip 7 champs couvert par `PatrimoineIntegrationTest` (CI) |
| Vérification comportementale US-92/93 | ⚠️ **Partielle** | Honoraire EUR confirmé formaté en Staging (recalcul correct) ; **aucune vérification visuelle live USD/CDF** (quittance PDF, panneaux UI) — couverture CI uniquement (`MoneyTest`/`DocumentHtmlBuilderTest` paramétrés 3 devises). Réserve **RSV-S7-8-01** portée depuis le Gate Staging. |
| Accumulation Staging | ✅ | Candidat figé à `sha-2da27182` ; commits postérieurs (`837ef31`, `787d5ff`, `9d37517`, `c76ca93`) documentaires/outillage de test uniquement — vérifié par diff (§2) |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---|---|---|
| Sprint 7 validé | ✅ | GO PO au kickoff (2026-07-01), rapport `sprint-7-patrimoine-enrichi-rapport-validation.md`, 111/111 backend + 59/59 frontend en local |
| Sprint 8 validé | ✅ | ADR-13 acceptée au kickoff (2026-07-02), 122/122 backend + 63/63 frontend (suite complète post-fusion Sprint 7+8) |
| Release notes disponibles | ✅ | `docs/release-notes-v1.6.0.md` (créé dans le cadre de cette analyse) |
| Changelog disponible | ⚠️ | `CHANGELOG.md` section `[Non publié]` contient le contenu Sprint 7+8 **mais aussi le contenu Sprint 6, déjà livré en `1.5.0`** — la promotion `[Non publié]` → `[1.5.0]` n'a jamais été faite après le `PRODUCTION_DEPLOYED` du 2026-07-01. **Anomalie de traçabilité documentaire, non bloquante techniquement, à corriger avant de promouvoir `[1.6.0]`** (voir §4, réserve RP-160-03) |

### Contrôles techniques et DevSecOps

| Critère | Statut | Preuve |
|---|---|---|
| Build CI stable | ✅ | `sha-2da27182` (PR #143/#144) — SUCCESS toutes jobs (Backend, Frontend, CodeQL Java/Kotlin + JS/TS, Sécurité, Packaging) ; confirmé de nouveau sur `origin/main` HEAD (`9d37517`) |
| Tests backend | ✅ | 122/122 (`mvn verify`), dont `MoneyTest`, `DocumentHtmlBuilderTest` paramétré 3 devises, `PatrimoineIntegrationTest` (adresse obligatoire) |
| Tests frontend | ✅ | 63/63 (`ng test`), `ng build`/`ng lint` propres |
| Gitleaks / SCA / Trivy | ✅ | Job Sécurité SUCCESS dans CI `77549a9`/`2da2718` |
| SonarQube | — | Non explicitement revérifié dans ce Gate (même limitation que Gate Production `1.5.0`, RP-150-02) |
| Migrations Production | ⚠️ | **V19 à appliquer** — ajout 7 colonnes optionnelles + `patrimoine.adresse SET NOT NULL` (backfill générique). **Prérequis** : recompter en Production `SELECT count(*) FILTER (WHERE adresse IS NULL) FROM patrimoine` immédiatement avant déploiement (dernier comptage connu : 1/1, effectué le 2026-07-01, à reconfirmer — le serveur Production étant volontairement éteint hors fenêtre de déploiement, aucune écriture nouvelle n'est attendue, mais la vérification reste requise au Préflight) |
| Observabilité | ✅ | Dispositif Production existant inchangé (5 cibles Prometheus, Alertmanager, Pushgateway) |
| Secrets | ✅ | Aucun secret versionné ; `.env` hors dépôt ; digests immuables confirmés |

### Rollback

| Critère | Statut | Note |
|---|---|---|
| Tag rollback applicatif | ⚠️ | `sha-08b366fa` (`1.5.0`) disponible sur GHCR, **mais rollback applicatif seul est risqué** (voir ci-dessous) |
| Responsable rollback | ✅ | DevSecOps Lead, coordination Release Manager |
| Migration V19 et rollback | ⚠️ | V19 ajoute des colonnes (rétrocompatibles) **et** une contrainte `NOT NULL` sur `patrimoine.adresse`. Un rollback applicatif vers `sha-08b366fa` **sans** défaire V19 expose un risque concret : l'ancien `InscriptionService` (pré-US-90) crée le patrimoine par défaut d'un nouveau bailleur **sans** `adresse` — toute nouvelle inscription échouerait en 500 (violation `NOT NULL`) tant que la contrainte V19 reste active. |
| Procédure si rollback nécessaire | ✅ documentée | (a) Si aucune inscription n'a eu lieu depuis le déploiement `1.6.0` : rollback applicatif seul acceptable en dégradé temporaire, correctif forward requis rapidement. (b) Si rollback complet nécessaire ou inscriptions déjà en échec : restauration du backup pré-déploiement (`pg_restore`), qui annule aussi V19. |
| Rollback Production | ✅ | `sha-08b366fa` disponible ; procédure : `LOYERTRACKER_TAG=sha-08b366fa docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx` (+ `pg_restore` si option (b) retenue) |

## 4. Réserves et conditions

| ID | Nature | Traitement |
|----|--------|------------|
| RP-160-01 | Backup Production non encore créé | **Bloquant** — levé uniquement après `pg_dump -Fc` + `pg_restore --list` OK en Préflight |
| RP-160-02 | Recomptage `patrimoine.adresse IS NULL` en Production non rejoué depuis le 2026-07-01 | **Bloquant léger** — à exécuter au Préflight, avant migration ; aucun changement attendu (serveur éteint hors fenêtre de déploiement) mais vérification requise avant d'appliquer V19 |
| RP-160-03 | `CHANGELOG.md` : section `[Non publié]` mélange du contenu déjà livré (Sprint 6 → `1.5.0`) avec le contenu de ce Gate (Sprint 7+8 → `1.6.0`) | **Non bloquant pour le déploiement** — à corriger avant la clôture de release : scinder en `[1.5.0]` (rétroactif) et `[1.6.0]`, action Release Manager |
| RP-160-04 | RSV-S7-8-01 — confirmation visuelle USD/CDF (quittance PDF + UI Paiements/Honoraires) non exécutée | **Non bloquant** — couverture CI jugée suffisante (3 devises paramétrées) ; recommandé de l'exécuter pendant la validation finale Production (données réelles disponibles : bail EUR existant, pas de bail USD/CDF connu en Production à ce jour — vérification réaliste seulement en Staging ou via jeu de données de test) |
| RP-160-05 | Action différée Sprint 7 : adresse réelle du patrimoine `d753e6d6-564e-4e6d-91c4-09a7c3265a91` | **Non bloquant pour le Gate, obligatoire immédiatement après déploiement** — appliquer via `PUT /api/patrimoines/{id}` (mapping documenté dans `sprint-7-patrimoine-enrichi-rapport-validation.md` §1), pour remplacer le placeholder `"Adresse à renseigner"` posé par le backfill V19 |

## 5. Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | **GO sous réserve** — traçabilité Staging complète (STG-ISOL-01 PASS, smoke 59/0, candidat figé vérifié par diff), réserves RP-160-01/02 explicites, anomalie documentaire RP-160-03 tracée sans bloquer |
| Enterprise Architect | **GO sous réserve** — migration V19 additive et non destructive (backfill avant contrainte), mais **contrainte `NOT NULL` crée une dépendance schéma↔code sur le chemin d'inscription** : rollback applicatif seul n'est pas trivial (§Rollback). Recommandation : ne pas revenir en arrière sur l'image sans revenir aussi sur le schéma si une inscription a déjà eu lieu post-déploiement |
| DevSecOps Lead | **GO sous réserve** — CI SUCCESS toutes jobs, 0 High/Critical sécurité, backup pré-déploiement obligatoire (RP-160-01), recomptage Production requis avant migration (RP-160-02) |
| Release Manager | **GO sous réserve** — candidat recevable `sha-2da27182`, preuves Staging complètes hormis vérification visuelle Money (RP-160-04, non bloquante), dette documentaire CHANGELOG identifiée (RP-160-03, à traiter avant clôture) |
| Product Owner | **GO** — Sprint 7 et Sprint 8 validés au kickoff respectif (GO sprint par sprint déjà donné le 2026-07-01), action adresse réelle du patrimoine confirmée et documentée (RP-160-05) |

## 6. Décision finale

**Chief Delivery Officer : GO sous réserve acceptée.**

- `PRODUCTION_READY` : **atteint** pour la release `1.6.0`, sous conditions bloquantes ci-dessous.
- `PRODUCTION_DEPLOYED` : non atteint — déploiement à exécuter selon le plan
  Préflight → Déploiement → Application adresse réelle → Validation finale → Hypercare.

### Conditions bloquantes avant déploiement technique

1. **RP-160-01** — backup Production vérifié (`pg_dump -Fc` + `pg_restore --list` OK).
2. **RP-160-02** — recomptage `patrimoine.adresse IS NULL` en Production, exécuté juste avant
   la migration V19, résultat conforme à l'attendu (1/1, patrimoine
   `d753e6d6-564e-4e6d-91c4-09a7c3265a91`) ou tout écart qualifié avant de poursuivre.

### Conditions non bloquantes (suivi requis)

- **RP-160-03** — correction du `CHANGELOG.md` avant clôture de release `1.6.0`.
- **RP-160-04** — RSV-S7-8-01, vérification visuelle Money recommandée si des données de test
  USD/CDF sont disponibles pendant la validation finale.
- **RP-160-05** — application de l'adresse réelle du patrimoine immédiatement après le
  déploiement technique, avant le smoke final.

### Services cibles du déploiement

**`api` et `nginx`** — le backend reçoit la migration V19, le VO `Money`, les endpoints
Paiements/Honoraires enrichis ; le frontend reçoit le formulaire Patrimoine étendu et
`MoneyFormatPipe`. Postgres reçoit la migration V19 (schéma). Keycloak et le monitoring sont
inchangés.

### Prochaines étapes

| Étape | Document à créer |
|---|---|
| Préflight + backup Production | `preflight-backup-v1.6.0-report.md` |
| Déploiement technique (`api` + `nginx`, migration V19) | `deploiement-technique-v1.6.0-report.md` |
| Application adresse réelle patrimoine (`PUT /api/patrimoines/{id}`) | À consigner dans le rapport de validation finale |
| Validation finale + smoke Production | `validation-finale-v1.6.0-report.md` |
| Hypercare + clôture | `plan-etape-hypercare-v1.6.0.md` / `cloture-release-v1.6.0.md` |
| CHANGELOG — scinder `[Non publié]` en `[1.5.0]` (rétroactif) + promouvoir `[1.6.0]` | Après `PRODUCTION_DEPLOYED` (RP-160-03) |
