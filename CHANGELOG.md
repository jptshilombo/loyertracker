# Changelog — LoyerTracker

Toutes les évolutions notables de ce projet sont consignées dans ce fichier.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le projet adhère au
[Semantic Versioning](https://semver.org/lang/fr/) (D-REL-002, CGPA v5.3).

## [Non publié]

## [1.3.0] — 2026-06-29

### Ajouts — Sprint 4 UI Patrimoine

- Interface bailleur pour créer, afficher et révoquer les affectations au niveau patrimoine.
- Interface d’exceptions fines `INCLUSION`/`EXCLUSION` par bien, conditionnée à une affectation patrimoine active et alignée sur RS-04.
- Modèle frontend étendu et tests du dashboard ; PR #82 fusionnée dans `main` (`7738a08`) après CI, CodeQL, SonarQube, Gitleaks, Trivy et packaging verts.

### Ajouts — Endpoint historique affectations patrimoine

- `GET /api/patrimoines/{id}/affectations` : retourne l’historique complet des affectations d’un patrimoine, scopé par bailleur (403 sur patrimoine tiers ou inexistant). Correctif É-01 détecté en Staging lors de la validation E6 Sprint 4 ; PR #96.

### Gouvernance — Remédiation audit CGPA v5.4.1

- Activation de Dependabot et des correctifs automatiques ; mise à jour Angular DevKit/CLI `20.3.30` et override ciblé `http-proxy-middleware` `3.0.7` : `npm audit` passe de 3 alertes High à **0 High/Critical** (5 Moderate, 5 Low résiduelles dans la chaîne de build).
- Correction du clone superficiel du job Frontend (`fetch-depth: 0`) afin de restaurer le blame SonarQube et le calcul fiable du code nouveau.

## [1.2.1] — 2026-06-27

### Corrigé — Dashboard bailleur

- **Chargement des biens garanti même en cas d'erreur d'inscription** : `chargerBiens()` et
  `chargerReferentielsBien()` n'étaient appelés que dans le callback `next` de `inscrire()` ;
  une erreur d'inscription (401, 500, réseau) laissait le tableau de bord vide. `finalize`
  garantit désormais que `chargerBiens()` s'exécute en succès comme en erreur ;
  `chargerReferentielsBien()` est lancé immédiatement en parallèle de l'inscription.
  (`dashboard.component.ts` — `c1e9c73`)

## [1.2.0] — 2026-06-26

### Ajouts — Patrimoine (Sprint 3, exceptions fines par bien, US-85)

- **Exceptions `INCLUSION`/`EXCLUSION`** sur les affectations bien : une affectation bien `ACTIVE`
  court-circuite désormais toujours l'héritage d'une affectation patrimoine (RM-98) — `INCLUSION`
  accorde l'accès (comportement historique inchangé, US-23/24), `EXCLUSION` le refuse précisément
  sur ce bien tout en conservant l'accès aux autres biens du patrimoine.
- **RS-04** : une `EXCLUSION` sans affectation patrimoine `ACTIVE` du même gestionnaire est rejetée
  en 400 (état incohérent) ; une `INCLUSION` redondante reste tolérée (idempotente).
- **Migration V15** : colonne `affectation.type_exception`, backfill `INCLUSION` sur les
  affectations bien existantes, réécriture à priorité de `gestionnaire_affecte_actif` et
  `biens_affectes_gestionnaire`, et correctif ciblé de `calculer_honoraires` pour qu'une
  `EXCLUSION` ne génère jamais d'honoraire (carve-out d'accès, pas un mandat de gestion).
- **Backend-only** (périmètre tranché par le PO le 2026-06-24) : aucune UI livrée pour les
  exceptions, différée à un lot ultérieur.
- Kickoff confirmé par le PO le 2026-06-25 ; validation locale `mvn verify` 99 tests / 0 échec,
  Gitleaks 168 commits / no leaks, Trivy SCA 0 HIGH/CRITICAL. Rapport :
  `docs/cgpa/06-planification-agile/sprint-3-patrimoine-rapport-validation.md`.
- Intégrée à `main` via la PR #81 (`1c06085`, 2026-06-25) après correction d'un défaut latent de CI
  (clone Git superficiel cassant le blame SonarQube du job Backend) et résolution de 3 violations
  SonarQube nouvelles sur `AffectationService.creer()` (complexité cognitive, ternaire imbriqué,
  faux-positif NPE), sans changement de comportement.

### Corrigé — Câblage CORS Compose (2026-06-25)

- `APP_CORS_ALLOWED_ORIGIN` et `APP_INVITATION_BASE_URL` sont définies dans `.env` sur les hôtes
  staging et production depuis l'exposition publique (2026-06-16) mais n'étaient jamais transmises
  au conteneur `api` dans aucun fichier Compose — Spring utilisait le fallback `https://localhost`,
  cassant les requêtes CORS depuis l'origine publique et générant des liens d'invitation incorrects.
- Ajout des deux variables dans `docker-compose.yml` (couvre dev et prod par héritage Compose) et
  `docker-compose.staging.yml`. `docker-compose.prod.yml` non modifié : le service `api` n'y déclare
  pas de bloc `environment`, l'héritage du fichier de base est donc suffisant.
- Aucun code applicatif, aucune migration SQL. Commit `964ebfb`.

## [1.1.1] — 2026-06-24

### Release — Hotfix Production `1.1.1`

- Candidat Production recevable : commit `0adc4941`, images API/Web `sha-0adc4941`.
- CI, CodeQL, SonarQube et scans de sécurité verts ; Staging 4/4 healthy, smoke 47/0 et parcours navigateur réel validé.
- Gate Production accéléré : GO sous réserve acceptée ; `PRODUCTION_READY` atteint.
- Préflight Production PASS et backup `loyertracker-20260624-140441.dump` vérifié.
- Production `1.1.1` déployée sur `sha-0adc4941` ; smoke 47/0, nettoyage complet et `PRODUCTION_DEPLOYED` atteint.
- Hypercare 24 h : T0 PASS sous surveillance (2026-06-24 16:11:35 UTC), T+12 PASS sous surveillance (2026-06-25 06:21:54 UTC, fenêtre étendue par décision CDO), T+24 PASS sous surveillance (2026-06-25 16:48:05 UTC) — aucune alerte critique, tous seuils respectés.
- Statut CGPA v5.4.1 : **`PRODUCTION_DEPLOYED` — RELEASE CLÔTURÉE** (CDO : GO le 2026-06-25). Réserves maintenues : `RSV-STG-01`, dette CORS Compose.

### Gouvernance — Migration corrective CGPA v5.4.1 (2026-06-24)

- Normalisation de l’ADR-STG-001 au chemin canonique `docs/cgpa/adr/ADR-STG-001-staging-isolation.md`, avec conservation de l’ancien chemin v5.4 comme alias.
- Ajout des rapports de migration v5.4.1, maintien de `STG-ISOL-01` et ajout des risques RSV-STG-02 à RSV-STG-04.
- Aucun Gate rejoué ; décision GO sous réserve de la preuve live RSV-STG-01.

### Gouvernance — Migration CGPA v5.4 (2026-06-24)

- Migration additive du projet vers **CGPA v5.4** : `docs/project-state.md` passe à `framework.current_version: "5.4"` sans suppression d'historique, de décision, de risque ou de Gate validé.
- Ajout de la gouvernance des environnements Staging partagés : LoyerTracker partage l'hôte `ai-test-server` avec d'autres projets.
- Ajout du Gate bloquant **`STG-ISOL-01`** (isolation du déploiement Staging), statué **PASS** sur la base de l'architecture existante (namespace Docker, réseau/volume dédiés, ports paramétrables, reverse proxy par nom DNS, absence de commande Docker globale).
- Ajout du workflow `staging-isolation-workflow.md` et de la checklist `stg-isol-01-checklist.md`.
- Ajout des décisions D-STG-01 à D-STG-05 et du risque RSV-STG-01.
- Ajout de l'ADR obligatoire `ADR-STG-001-isolation-staging-partage.md` (rejette explicitement l'arrêt de tous les conteneurs de l'hôte avant chaque déploiement).
- Mise à jour des responsabilités Release Manager, DevSecOps Lead, Governance Officer et Enterprise Architect.
- Création des rapports de migration v5.4 dans `docs/cgpa/migration/`.

### Corrigé — Hotfix création/édition de bien cassée en Production (2026-06-24)

- Le tableau de bord bailleur n'envoyait pas `patrimoineId` (devenu obligatoire depuis `1.1.0`/V12) lors
  de la création ou la modification d'un bien : toute soumission échouait en 400. Le formulaire propose
  désormais un sélecteur de patrimoine (`GET /api/patrimoines`) et un sélecteur de type de bien
  (`GET /api/types-biens`), au lieu d'un champ texte libre.
- Tout nouveau bailleur se voit désormais créer automatiquement un patrimoine par défaut
  (« Patrimoine principal ») à l'inscription ; auparavant seuls les bailleurs déjà existants au moment
  de la migration V12 en disposaient, bloquant l'onboarding de tout nouveau bailleur.
- Aucune migration de schéma, aucun changement de contrat API existant.

## [1.1.0] — 2026-06-23

### Release — Production `1.1.0`

- Déploiement Production le 2026-06-23 avec le tag immuable GHCR `sha-05424aa3`.
- Backup pré-déploiement vérifié : `loyertracker-20260623-150659.dump`.
- Smoke Production post-déploiement : 47 PASS / 0 FAIL.
- Statut CGPA v5.3 : `PRODUCTION_DEPLOYED`.

### Gouvernance — Migration CGPA v5.3

- Migration additive du projet vers **CGPA v5.3** : `docs/project-state.md` passe à `framework.current_version: "5.3"` sans suppression d'historique, de décision, de risque ou de Gate validé.
- Ajout des statuts Release Management `STAGING_READY`, `STAGING_DEPLOYED`, `PRODUCTION_READY` et `PRODUCTION_DEPLOYED`.
- Ajout des workflows v5.3 Sprint -> Staging, Epic -> Production, Release -> Production et Hotfix -> Production.
- Ajout des checklists Gate Staging et Gate Production.
- Ajout des décisions D-RM-01 à D-RM-04 et des risques RSV-RM-01 à RSV-RM-04.
- Création des rapports de migration dans `docs/cgpa/migration/`.


### Ajouts — Patrimoine (Sprint 2, affectations patrimoine, PR #74)

- **Affectations patrimoine backend-first** : un bailleur peut affecter un gestionnaire à un
  patrimoine entier (`patrimoineId`) en complément des affectations bien existantes ; le modèle
  impose l'exclusivité bien/patrimoine et la migration V13 porte les contraintes/index nécessaires.
- **Héritage dynamique ReBAC** : `AuthorizationService` accorde l'accès aux biens d'un patrimoine
  affecté, y compris les biens ajoutés après l'affectation, tout en conservant la priorité des
  affectations bien existantes.
- **Liste effective gestionnaire** : `GET /api/biens` inclut le périmètre hérité par patrimoine sans
  doublons.
- **Garde RS-06** : l'archivage d'un patrimoine avec affectation patrimoine `ACTIVE` est refusé en
  400 jusqu'à révocation explicite.
- **Clôture Sprint 2** : validations locales backend/frontend/sécurité du 2026-06-23 puis CI GitHub
  PR #74 entièrement verte ; décision documentaire
  `docs/cgpa/06-planification-agile/sprint-2-patrimoine-cloture.md`.

### Documentation — Patrimoine (Sprint 2, plan approuvé)

- **Plan d'exécution Sprint 2 Patrimoine** : GO PO Option A confirmé pour un démarrage
  backend-first, couvrant les affectations au niveau patrimoine, l'héritage dynamique gestionnaire,
  la priorité de résolution avec les affectations bien existantes et la garde RS-06 avant archivage
  patrimoine. Document : `docs/cgpa/06-planification-agile/sprint-2-patrimoine-plan-execution.md`.

### Ajouts — Patrimoine (Sprint 1, modèle de données, PR #72)

- **Patrimoines bailleur** : nouveau niveau `Patrimoine` entre bailleur et bien, avec endpoints
  `/api/patrimoines` et statut d’archivage.
- **Typologie administrable des biens** : référentiel `TypeBien` administrable par le bailleur, en
  remplacement progressif de la saisie libre historique.
- **Rattachement obligatoire des biens** : `Bien.patrimoineId` devient obligatoire ; migration V12
  avec patrimoine par défaut par bailleur et rattachement des biens existants.
- **Sécurité / non-régression** : RLS activée/forcée sur les nouvelles tables et tests
  d’intégration Patrimoine + adaptations S02/S03/S04/documents.
- **Clôture Sprint 1 préparée** : validations complètes backend/frontend/sécurité rejouées le
  2026-06-21 (backend 78 tests, frontend 41 tests, Gitleaks/Trivy OK) ; décision documentaire
  `docs/cgpa/06-planification-agile/sprint-1-patrimoine-cloture.md`.

### Ajouts — Quittances de loyer (documents, lot post-go-live, PR2)

- **Quittance de loyer** et **avis d'échéance** générés **à la volée en PDF** (jamais stockés) à
  partir d'un loyer : `GET /api/biens/{bienId}/paiements/{periode}/quittance` (loyer `RECU`) et
  `…/avis-echeance` (loyer non soldé). Mise en page XHTML→PDF côté serveur (OpenHTMLtoPDF), avec
  ventilation loyer/charges et mentions bailleur/locataire/bien. Accès ReBAC (bailleur propriétaire
  ou gestionnaire affecté), cloisonné RLS. Adresse bailleur requise (409 explicite sinon).
- **Frontend** : boutons « Télécharger la quittance » / « Télécharger l'avis d'échéance » sur chaque
  loyer, selon son statut.

### Ajouts — Quittances de loyer (socle données, lot post-go-live, PR1)

- **Ventilation du loyer** : le bail distingue désormais le loyer hors charges (`loyer_hc`) et la
  provision de charges (`provision_charges`) ; le « charges comprises » (`loyer_cc`) en est dérivé
  et la cohérence `loyer_cc = loyer_hc + provision_charges` est imposée en base (migration V11).
  Formulaires de bail (bailleur + gestionnaire) mis à jour.
- **Profil bailleur** : adresse postale (`bailleur.adresse`), endpoint `GET`/`PUT /api/bailleurs/profil`
  et écran « Mon profil » — mention obligatoire de la future quittance.

### Suivi (déjà réalisé, consigné a posteriori)

- Validation staging par simulation d'incident de l'alerting (OBS-02/03) et re-validation du
  Gate Staging enrichi.
- Ratification du Gate 07A — Release Readiness.
- Go-live production réel (provisioning hôte, Gate 09/10) — réalisé le 2026-06-20, Gate 10 GO.

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

- Go-live production réalisé le 2026-06-20 (Gate 10 GO) ; `1.0.0` LIVE.
- Alerting validé sur staging puis prouvé en production lors du Gate 10.
- OpenAPI non encore produit ; UX S02 minimale.

[Non publié]: https://github.com/jptshilombo/loyertracker/compare/v1.3.0...HEAD
[1.3.0]: https://github.com/jptshilombo/loyertracker/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/jptshilombo/loyertracker/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/jptshilombo/loyertracker/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/jptshilombo/loyertracker/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/jptshilombo/loyertracker/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/jptshilombo/loyertracker/releases/tag/v1.0.0
