# Sprint 7 — Patrimoine enrichi (EP-10, US-90) — Rapport de validation

| Champ | Valeur |
|-------|--------|
| Statut | **✅ Implémenté et validé localement — GO technique.** Reste : PR dédiée + CI GitHub avant fusion `main`. |
| Date | 2026-07-01 |
| Stories couvertes | US-90 (champs additionnels + `adresse` obligatoire). US-91 tranchée au kickoff : extension du formulaire inline existant (pas d'écran CRUD dédié). |
| Décision liée | ADR-12 (D-PAT-002), Plan d'Exécution `plan-execution-evolutions-ep10-ep13.md` |

## 1. Prérequis bloquant — vérifié avant codage

Comptage en Production (`loyertracker-prod-server`) avant toute migration :

```sql
SELECT count(*) AS total_patrimoines, count(*) FILTER (WHERE adresse IS NULL) AS sans_adresse
FROM patrimoine;
```

Résultat : **1 patrimoine total, 1 avec `adresse IS NULL`** — le patrimoine « Patrimoine
principal » du bailleur `jptshilombo@gmail.com` (id `d753e6d6-564e-4e6d-91c4-09a7c3265a91`),
créé automatiquement à l'inscription (Hotfix 2026-06-24). Le PO a communiqué l'adresse réelle :

> 5172, Avenue Kasamvu, Kasa-Vubu, Bandalungwa, Kinshasa, Kinshasa, République Démocratique du Congo

Cette adresse réelle **n'est pas codée en dur dans la migration V19** (qui doit rester générique
pour tous les environnements — CI, Staging, Production). Elle sera appliquée via
`PUT /api/patrimoines/{id}` (endpoint déjà existant, aucune action manuelle en base requise) au
moment du Gate Production de ce sprint, avec le mapping suivant :

| Champ | Valeur à appliquer |
|---|---|
| `adresse` | `5172, Avenue Kasamvu` |
| `quartier` | `Kasa-Vubu` |
| `commune` | `Bandalungwa` |
| `ville` | `Kinshasa` |
| `provinceEtat` | `Kinshasa` |
| `pays` | `République Démocratique du Congo` |

**Action différée, à ne pas oublier au Gate Production Sprint 7** : appliquer ce contenu réel
sur `d753e6d6-564e-4e6d-91c4-09a7c3265a91` immédiatement après le déploiement, pour remplacer le
placeholder `"Adresse à renseigner"` posé par le backfill générique de la migration V19.

## 2. Livrables techniques

- **Migration V19** (`backend/src/main/resources/db/migration/V19__sprint7_patrimoine_enrichi.sql`) :
  ajout de `ville, commune, quartier, province_etat, pays, description, reference_interne`
  (nullable) ; backfill générique `adresse = 'Adresse à renseigner' WHERE adresse IS NULL` ;
  `ALTER COLUMN adresse SET NOT NULL`.
- **Backend** : `Patrimoine` (entité, constructeur `(id, bailleurId, nom, adresse)`, méthode
  `modifier(...)` étendue aux 9 champs), `PatrimoineDto`, `PatrimoineRequest` (validation
  `@NotBlank` sur `nom`/`adresse`, `@Size` sur les champs texte), `PatrimoineService` (`creer`,
  `renommer`), `InscriptionService` (patrimoine par défaut créé avec l'adresse placeholder, la
  colonne étant désormais `NOT NULL`).
- **Frontend** : `Patrimoine`/`PatrimoinePayload` (`s02-api.service.ts`) étendus ; formulaire
  « Modifier un patrimoine » du dashboard bailleur étendu avec les 7 nouveaux champs (extension
  inline, décision US-91 tranchée au kickoff) ; liste des patrimoines affiche ville/pays/référence
  interne si renseignés.
- **Tests** : nouveau test `patrimoineAdresseObligatoireEtChampsEnrichisPersistes`
  (`PatrimoineIntegrationTest`) — vérifie le rejet 400 sans `adresse` et le round-trip complet des
  7 champs. Tous les payloads de test existants qui créaient un patrimoine sans `adresse`
  (`PatrimoineIntegrationTest`, `S02BiensBauxAffectationsIntegrationTest`,
  `S03PaiementsGarantiesIntegrationTest`, `S04HonorairesIntegrationTest`,
  `S04AlertesAuditIntegrationTest`, `RgpdIntegrationTest`, `DocumentGenerationIntegrationTest`,
  `AuthorizationServiceIntegrationTest`, `SchemaMigrationTest`) ont été mis à jour pour fournir une
  `adresse` valide. Assertion Flyway (`SchemaMigrationTest`) et smoke script
  (`infra/smoke/smoke-stack.sh`) alignés V18→V19.

## 3. Validation locale

| Suite | Résultat |
|---|---|
| Backend (`mvn -o test`) | **111 tests / 0 échec** |
| Frontend (`ng build`) | ✅ build production sans erreur |
| Frontend (`ng test --browsers=ChromeHeadless`) | **59 tests / 0 échec** |

## 4. Non-régression EP-09

Aucune régression sur le CRUD Patrimoine (créer/renommer/archiver), le rattachement obligatoire
Bien→Patrimoine, l'isolation cross-bailleur (ReBAC), ni sur l'affectation patrimoine/bien —
`PatrimoineIntegrationTest` (10/10), `AuthorizationServiceIntegrationTest` (10/10) et
`S02BiensBauxAffectationsIntegrationTest` (11/11) restent intégralement verts.

## 5. Critères GO (fin de sprint) — statut

| Critère | Statut |
|---|---|
| 100 % des patrimoines existants ont une `adresse` non nulle après migration, 0 échec de contrainte | ✅ (backfill générique + vérification Production : 1/1 couvert) |
| Suite de tests existante verte sans régression | ✅ 111/111 backend, 59/59 frontend |
| Non-régression complète EP-09 | ✅ |
| CI complète verte | À confirmer sur la PR |

## 6. Reste à faire avant clôture définitive du sprint

- Ouvrir la PR dédiée, obtenir CI complète verte (CodeQL, SonarQube, Gitleaks, Trivy, Packaging).
- Au Gate Production Sprint 7 (distinct, non couvert par ce rapport) : appliquer l'adresse réelle
  du patrimoine `d753e6d6-564e-4e6d-91c4-09a7c3265a91` via `PUT /api/patrimoines/{id}` (§1).
