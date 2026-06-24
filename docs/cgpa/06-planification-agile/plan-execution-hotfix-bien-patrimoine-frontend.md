# Plan d'Exécution Hotfix — Formulaire bien non aligné sur Patrimoine (régression Production)

| Champ | Valeur |
|-------|--------|
| Statut | **Implémenté et validé localement le 2026-06-24** — `mvn verify` (86 tests, 0 échec), `ng lint`/`ng build`/`ng test` (45 tests, 0 échec) verts. En attente : commit/push, déploiement Staging + vérification navigateur, Gate Production accéléré. |
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
| Reste à faire avant Production | Commit/push (PR ou direct selon décision PO) ; déploiement Staging ; smoke `infra/smoke/smoke-stack.sh` ; **vérification manuelle navigateur** (inscription → création bien, absente jusqu'ici de la procédure) ; Gate Production accéléré ; mise à jour `docs/project-state.md`/`docs/staging-state.md`/`docs/prod-state.md`. |
