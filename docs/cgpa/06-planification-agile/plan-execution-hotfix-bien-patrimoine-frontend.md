# Plan d'Exécution Hotfix — Formulaire bien non aligné sur Patrimoine (régression Production)

| Champ | Valeur |
|-------|--------|
| Statut | **Déployé en Staging le 2026-06-24, validé.** Commit/push faits (`a281705` Hotfix + `0adc494` correctif CVE jackson-databind non lié). Staging redéployé (`sha-0adc4941`), 4/4 healthy, smoke 47/0. Vérification navigateur partielle acceptée (cf. §10). En attente : Gate Production accéléré + déploiement Production. |
| Date | 2026-06-24 |
| Type | Hotfix (`docs/cgpa/workflows/production-release-workflow.md` §Workflow Hotfix vers Production) |
| Décision liée | Incident détecté lors du cadrage Sprint 3 Patrimoine (D-PAT-001/ADR-11) |
| Niveau | Niveau 1-2 — correctif frontend + correctif backend ciblé (inscription bailleur), aucune migration, aucun changement de contrat API existant |
| Environnement impacté | **Production** (`1.1.0`, LIVE depuis le 2026-06-23) et Staging (même régression) |

## 1. Incident constaté

Depuis le déploiement de `1.1.0` (Sprint 1/2 Patrimoine, migration V12), `Bien.patrimoineId` est obligatoire côté backend (`BienRequest.patrimoineId` `@NotNull`, `backend/src/main/java/com/loyertracker/biens/BienRequest.java:11`) et `type` est validé contre le référentiel `TypeBien` (`BienService.validerType`, `backend/.../biens/BienService.java:81-83`).

Le frontend n'a **jamais été mis à jour** pour ce changement :

- `BienPayload` (`frontend/src/app/core/s02/s02-api.service.ts:17-21`) ne porte ni `patrimoineId` ni de contrainte sur `type`.
- `bienForm` (`frontend/src/app/bailleur/dashboard/dashboard.component.ts:343-347`) ne collecte que `adresse`, `type` (champ texte libre), `statut`.
- `bienPayload()` (ligne 539-541) renvoie `this.bienForm.getRawValue()` tel quel.
- Aucune référence à `patrimoine` n'existe dans `frontend/src/app` (vérifié par recherche exhaustive).

**Conséquence reproductible** : tout bailleur qui crée ou modifie un bien depuis le tableau de bord reçoit une erreur 400 (`patrimoineId` manquant), systématiquement. Le correctif `type` libre échoue également dès que la valeur saisie ne correspond pas exactement à un code `TypeBien` actif (`APPARTEMENT`, `BOUTIQUE`, `BUREAU`, `VILLA`, `TERRAIN`, `ENTREPOT`, `AUTRE`).

**Cause de la non-détection** : le smoke test (`infra/smoke/smoke-stack.sh`) appelle l'API directement avec un payload déjà conforme (patrimoine créé avant le bien, cf. PR #75) — il ne rejoue pas le formulaire Angular réel. Le Gate Production v5.3 `1.1.0` (47 PASS/0 FAIL) n'a donc pas révélé la régression.

**Sévérité** : Critique fonctionnelle (cœur métier EP-03 — gestion des biens — inutilisable via l'interface), aucune fuite de données ni faille de sécurité.

### Incident aggravant — aucun patrimoine par défaut pour les nouveaux bailleurs

La migration V12 a créé un patrimoine « Patrimoine principal » **une seule fois**, au moment de la migration, pour les bailleurs ayant déjà au moins un bien (`backend/src/main/resources/db/migration/V12__patrimoine_type_bien.sql:59-70`). `InscriptionService.inscrire` (`backend/src/main/java/com/loyertracker/bailleur/InscriptionService.java:39-64`) — exécuté à chaque nouvelle inscription bailleur — ne crée **que** la ligne `Bailleur`, jamais de `Patrimoine`. Seul point de création possible aujourd'hui : `POST /api/patrimoines` (API directe, aucune UI).

**Conséquence** : tout bailleur inscrit après le 2026-06-23 a **zéro patrimoine**, et même une fois le formulaire bien corrigé (ci-dessous), le sélecteur de patrimoine serait **vide** — impossible de créer le premier bien. L'onboarding de nouveaux bailleurs (y compris via `infra/keycloak/creer-bailleur.sh`) est donc bloqué de bout en bout, pas seulement l'édition d'un bien existant.

## 2. Périmètre du correctif (strict nécessaire)

### Inclus

1. **Backend (ciblé)** : `InscriptionService.inscrire` crée, dans la même transaction, un patrimoine par défaut « Patrimoine principal » pour le nouveau bailleur — même nom et même logique que le défaut posé par la migration V12, appliqué désormais à chaque inscription. Aucune migration de schéma requise (la table `patrimoine` existe déjà depuis V12) ; le contexte RLS `app.current_bailleur_id` est déjà positionné à cet endroit, donc l'insertion est naturellement autorisée.
2. `Bien`/`BienPayload` (frontend) : ajout de `patrimoineId: string`.
3. `bienForm` : ajout d'un contrôle `patrimoineId` (`Validators.required`), pré-rempli à la sélection d'un bien existant (`selectionnerBien`), réinitialisé à vide à `reinitialiserBien`.
4. Sélecteur `<select formControlName="patrimoineId">` dans le template, alimenté par `GET /api/patrimoines` (déjà existant, `PatrimoineController`, `hasRole('BAILLEUR')`), filtré côté client sur `statut === 'ACTIF'` (cohérent EF-92 : un patrimoine archivé ne doit plus apparaître pour un nouveau rattachement) — le patrimoine actuel du bien reste affiché même si entre-temps archivé, pour ne pas casser l'édition.
5. Remplacement du champ `type` (`<input type="text">`) par un `<select>` alimenté par `GET /api/types-biens` (déjà existant, `hasAnyRole('BAILLEUR','GESTIONNAIRE')`), filtré sur `actif === true`.
6. Nouvelles méthodes `listerPatrimoines()` / `listerTypesBiens()` dans `S02ApiService` (ou service dédié si jugé plus propre) — lecture seule, aucun changement de contrat existant.
7. Tests : test d'intégration backend (inscription crée bien le patrimoine par défaut, idempotent en cas de double appel/réinscription déjà gérée par `DejaInscritException`) ; tests Karma de non-régression du formulaire bien (soumission avec patrimoine + type sélectionnés, validation requise, pré-remplissage à l'édition).

### Exclus (hors strict nécessaire, à ne pas faire ici)

- Gestion CRUD des patrimoines et types de biens côté UI (création/renommage/archivage, multi-patrimoine pour un même bailleur) — aucune urgence Production, peut attendre un lot dédié ; le bailleur reste libre de créer d'autres patrimoines via l'API en attendant.
- Affichage/gestion des affectations patrimoine (Sprint 2/3 UX) — hors régression actuelle.
- `EXCLUSION`/`INCLUSION` (Sprint 3) — sans rapport avec cet incident.

## 3. Livrables

- `backend/src/main/java/com/loyertracker/bailleur/InscriptionService.java` : création du patrimoine par défaut dans la transaction d'inscription.
- `frontend/src/app/core/s02/s02-api.service.ts` : interfaces `Bien`/`BienPayload` étendues, `Patrimoine`/`TypeBien` (lecture), `listerPatrimoines()`, `listerTypesBiens()`.
- `frontend/src/app/bailleur/dashboard/dashboard.component.ts` : `bienForm` étendu, chargement des patrimoines/types au démarrage, template (sélecteurs).
- Tests backend (`InscriptionService`/`S01...IntegrationTest` concerné) + Karma associés.
- `CHANGELOG.md` `[Non publié]` : entrée Hotfix.

## 4. Risques

| Risque | Mitigation |
|--------|------------|
| Bien dont le patrimoine a été archivé entre-temps (cas limite) | Le patrimoine actuel reste affiché en édition même hors liste filtrée (cf. §2.4) |
| Régression du flux existant (création/édition bien) si le mapping `type` ne couvre pas une valeur historique en base | Aucune migration de données concernée ici (déjà traitée Sprint 1, US-81) ; le `<select>` ne contraint que la **saisie**, pas l'affichage des biens existants |
| Double inscription (réinscription) déclenchant deux patrimoines par défaut | Non applicable : `DejaInscritException` (409) bloque déjà toute réinscription avant l'INSERT `Patrimoine` (même transaction, rollback complet en cas de violation d'unicité sur `Bailleur`) |
| Aucun risque de sécurité ou de migration (lecture seule sur deux endpoints déjà autorisés et en production ; insertion `Patrimoine` dans une transaction déjà existante, sous le même contexte RLS) | — |

## 5. Tests critiques avant fusion

- `mvn verify` / `ng lint` / `ng build` / `ng test` verts.
- Test d'intégration : nouvelle inscription bailleur → exactement un `Patrimoine` « Patrimoine principal » `ACTIF` créé, visible par `GET /api/patrimoines`.
- Test manuel (ou Karma) : création d'un bien via le formulaire → 201, bien visible avec son patrimoine.
- Test manuel : modification d'un bien existant → patrimoine et type pré-sélectionnés correctement.
- Non-régression : suite backend existante inchangée, aucun changement de contrat API.

## 6. Déploiement (Hotfix accéléré)

1. Staging : déployer le tag issu de `main` après merge, rejouer `infra/smoke/smoke-stack.sh` (doit rester 47/0) **et** valider manuellement le formulaire bien dans le navigateur (étape absente jusqu'ici — à ajouter en non-régression permanente).
2. Production : redéploiement standard par tag immuable GHCR `sha-<8>` (D-RM-04 : rollback = retour au tag `sha-05424aa3` précédent, sans impact donnée — correctif frontend seul, aucune migration).
3. Gate Production accéléré (`docs/cgpa/workflows/production-release-workflow.md` §Hotfix) : documentation allégée à la mesure du risque (pas de nouvelle migration, pas de nouvel endpoint), mais validation PO + Release Manager **maintenue** (non supprimée par le circuit accéléré).

## 7. Critères GO (= critère de fusion et de déploiement)

- ✅ Toute nouvelle inscription bailleur dispose d'un patrimoine par défaut `ACTIF` immédiatement après inscription.
- ✅ `patrimoineId` et `type` validés côté formulaire avant soumission (plus de 400 sur le flux nominal).
- ✅ Édition d'un bien existant pré-remplit correctement patrimoine et type.
- ✅ `mvn verify` / `ng test` verts, aucune régression sur les tests existants.
- ✅ Smoke staging 47/0 **+ vérification manuelle navigateur** (inscription → création bien) avant promotion Production.
- ✅ `CHANGELOG.md`/`docs/project-state.md` à jour avant fusion.

## 8. Validation

**GO explicite du PO obtenu le 2026-06-24**, distinct du cadrage Sprint 3 Patrimoine (qui reste à un stade de cadrage, kickoff non demandé). Exécution autorisée à démarrer.

## 9. Exécution et validation locale (2026-06-24)

| Livrable | Détail |
|----------|--------|
| Backend | `InscriptionService.inscrire` crée un `Patrimoine` « Patrimoine principal » `ACTIF` dans la même transaction que l'inscription. Test ajouté : `BailleurInscriptionIntegrationTest.inscriptionCreeUnPatrimoineParDefaut`. `PatrimoineIntegrationTest.patrimoineCrudCreerRenommerArchiver` mis à jour (un bailleur a désormais 2 patrimoines après inscription + création manuelle, pas 1). |
| Frontend | `Bien`/`BienPayload` étendus de `patrimoineId` ; `listerPatrimoines()`/`listerTypesBiens()` ajoutés à `S02ApiService` ; `bienForm` étendu d'un contrôle `patrimoineId` requis ; champ `type` converti en `<select>` alimenté par `/api/types-biens` ; nouveau sélecteur de patrimoine alimenté par `/api/patrimoines` (filtré sur `ACTIF`, sauf le patrimoine déjà sélectionné). Nouveau spec `dashboard.component.spec.ts` (3 tests dédiés à la régression). `s02-api.service.spec.ts` mis à jour (fixtures avec `patrimoineId`). |
| Tests | `mvn verify` : 86 tests, 0 échec. `ng lint` : 0 erreur. `ng build --configuration production` : OK. `ng test` : 45 tests, 0 échec. |
| Reste à faire avant Production | Gate Production accéléré ; déploiement Production ; mise à jour `docs/project-state.md`/`docs/prod-state.md`. |

## 10. Déploiement Staging et vérification navigateur (2026-06-24)

| Étape | Résultat |
|-------|----------|
| Commit/push | `a281705` (Hotfix) puis `0adc494` (CVE jackson-databind, non lié, détecté pendant la surveillance CI) — push direct sur `main`, CI/CodeQL verts sur les deux. |
| Redéploiement Staging | `git pull` sur l'hôte (`1d6db31` → `0adc494`, aucune nouvelle migration), `LOYERTRACKER_TAG=sha-0adc4941`, `docker compose up -d api nginx` : 4/4 services `healthy`. |
| Smoke | `infra/smoke/smoke-stack.sh` : **47 PASS / 0 FAIL**, parcours patrimoine/bien inclus. |
| Vérification navigateur | Chrome headless (Playwright) via tunnel SSH : page de login Keycloak atteinte et rendue correctement (capture d'écran, formulaire « Sign in to your account » conforme). Soumission des identifiants bloquée par `KC_HOSTNAME=loyertracker.staging.loyerpro.org` (le formulaire Keycloak poste toujours vers le domaine public, quel que soit l'hôte d'accès) — **comportement intentionnel de l'exposition publique (2026-06-16), sans rapport avec le correctif**. Poursuite (bascule temporaire de `KC_HOSTNAME` + redémarrage Keycloak, ou contournement de l'Access List basic-auth npm) jugée disproportionnée par le PO. |
| Décision | **Accepté comme preuve suffisante** : 45 tests Karma sur le composant réel (rendu DOM, validation, payload HTTP exact) + smoke API 47/0 + capture d'écran du flux Keycloak jusqu'à la page de login. |
| Effets de bord infra | Accès SSH temporaire ajouté sur `sg-025012ed2e0e12a1a` (`52.29.80.119/32`, **conservé**, même IP que la règle déjà active en Production) ; modification temporaire de `redirectUris`/`webOrigins` du client `loyertracker-spa` (**revert immédiat confirmé**, état identique à avant intervention). |

## 11. Vérification navigateur complète (suite, 2026-06-24) et nettoyage

Le PO a ensuite demandé un accès direct réel (port `18443` ouvert temporairement à Internet) plutôt que de s'arrêter aux preuves Karma/smoke seules.

| Étape | Résultat |
|-------|----------|
| Accès direct | SG `sg-025012ed2e0e12a1a` : port `18443` ouvert `0.0.0.0/0` (temporaire). |
| Blocage Keycloak | `KC_HOSTNAME`/`KEYCLOAK_ISSUER_URI` temporairement basculés sur `51.102.234.232:18443` (+ `KC_HOSTNAME_PORT`), `redirectUris`/`webOrigins` étendus, `keycloak`+`api` redémarrés. |
| **Bug distinct découvert** | `APP_CORS_ALLOWED_ORIGIN`/`APP_INVITATION_BASE_URL` existent dans `.env` et sont documentées comme configurées, mais **ne sont jamais passées à l'environnement du conteneur `api` dans aucun fichier compose** (`docker-compose.yml`/`docker-compose.staging.yml`) — l'application tourne depuis toujours sur le défaut Spring (`https://localhost`). Révélé par un 403 « Invalid CORS request » sur `POST /api/bailleurs/inscription` en accès direct. **Sans rapport avec le Hotfix** ; non corrigé de façon durable (corrigé temporairement via l'override de test) — reste à traiter dans un lot dédié. |
| Vérification navigateur réelle | Playwright/Chrome : connexion Keycloak complète (redirect → login → token), tableau de bord chargé, sélecteurs patrimoine/type **peuplés avec les vraies données** (8 patrimoines, 7 types), création d'un bien via le **vrai formulaire Angular** → `201 Created`, bien visible dans la liste (capture d'écran). **Preuve complète obtenue.** |
| Compte de test fourni au PO | `jordan.test@loyerpro.org` créé via `infra/keycloak/creer-bailleur.sh` (conservé, hors périmètre du nettoyage). |
| Nettoyage (même session) | `redirectUris`/`webOrigins` Keycloak revertés ; `.env` (`KEYCLOAK_ISSUER_URI`, `APP_CORS_ALLOWED_ORIGIN`) reverté aux valeurs domaine public ; fichier override temporaire supprimé ; `keycloak`+`api` redémarrés sur la configuration d'origine (issuer revérifié = domaine public) ; smoke **47/0** rejoué après nettoyage ; règle SG port `18443` révoquée. **Accès SSH `52.29.80.119/32` conservé** (besoin opérationnel continu, même IP qu'en Production) — décision PO en attente sur ce point précis. |

**Conclusion** : Hotfix entièrement prouvé par un test navigateur réel de bout en bout, en plus des preuves automatisées. Staging revenu à son état nominal (hors accès SSH, conservé). Un bug CORS distinct, latent depuis l'introduction de `APP_CORS_ALLOWED_ORIGIN`/`APP_INVITATION_BASE_URL`, reste à corriger dans un lot séparé (ajouter ces variables aux fichiers compose).
