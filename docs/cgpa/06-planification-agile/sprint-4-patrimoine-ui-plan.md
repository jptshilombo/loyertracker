# Plan d'Exécution Sprint 4 — UI Gestion des affectations patrimoine

| Champ | Valeur |
|---|---|
| Statut | **Kickoff GO PO — 2026-06-27** |
| Sprint | Sprint 4 (UI Patrimoine) |
| Kickoff | 2026-06-27 — approbation PO explicite |
| Stories couvertes | US-84 (UI), US-85 (UI exceptions INCLUSION/EXCLUSION) |
| Backend de référence | `1.2.0` — `sha-47172297` en Production, V15 active |
| Décision liée | D-PAT-001 / ADR-11 — lot UI différé depuis Sprint 3 (2026-06-24) |
| Document parent | `plan-execution-patrimoine.md` |

> **Règle CGPA :** aucun code ne doit être produit avant ce kickoff. Ce plan reçoit l'approbation explicite du PO le **2026-06-27**. Chaque critère GO de fin de sprint conditionne la promotion Staging (Gate STG-ISOL-01 obligatoire) puis la promotion Production (Gate Production distinct).

---

## 1. Décisions PO actées au kickoff

| Réf | Question | Décision |
|---|---|---|
| A1 | Périmètre du sprint | **Sprint complet** — affectation patrimoine + exceptions INCLUSION/EXCLUSION livrés ensemble |
| B1 | Saisie gestionnaire | **UUID brut** — cohérent avec le formulaire existant ; pas de lookup par e-mail (aucun nouvel endpoint backend requis) |
| C1 | Emplacement UI | **Extension du dashboard bailleur existant** — pas de nouvelle page/route ; nouvelles sections dans `bailleur/dashboard` |

---

## 2. Contexte — état du backend en Production (`1.2.0`)

Les endpoints suivants existent et sont actifs depuis `1.2.0` (V15) :

| Endpoint | Usage Sprint 4 |
|---|---|
| `POST /api/affectations` | Affectation patrimoine : `{ patrimoineId, gestionnaireId, typeHonoraires, montantHonoraires, dateDebut, dateFin }` ; exception bien : `{ bienId, gestionnaireId, typeHonoraires, montantHonoraires, dateDebut, dateFin, typeException }` |
| `GET /api/patrimoines/{id}/affectations` | Lister les affectations d'un patrimoine (actives + archivées) |
| `GET /api/biens/{bienId}/affectations` | Lister les affectations d'un bien (existant — déjà utilisé dans dashboard) |
| `PUT /api/affectations/{id}/revoquer` | Révoquer toute affectation (déjà utilisé dans dashboard) |
| `GET /api/patrimoines` | Liste des patrimoines (déjà utilisé dans le formulaire de création de biens) |
| `GET /api/biens` | Liste des biens (déjà utilisé dans dashboard) |

**Règles métier backend à refléter dans l'UI :**
- **RS-04** : une exception `EXCLUSION` n'est acceptée que si une affectation patrimoine `ACTIVE` pour ce gestionnaire existe — l'UI ne doit proposer ce formulaire que si la condition est remplie.
- **RS-06** : archivage d'un patrimoine refusé (400) si une affectation patrimoine `ACTIVE` existe — hors scope Sprint 4 UI (déjà géré côté existant).
- Une affectation `patrimoineId` et `bienId` simultanés est rejeté (400) — l'UI doit utiliser l'un **ou** l'autre, jamais les deux.

---

## 3. Périmètre frontend (livrables)

### 3.1 Couche modèle — `frontend/src/app/core/s02/s02-api.service.ts`

**Modifications :**

| Élément | Changement |
|---|---|
| Interface `Affectation` | Ajouter `patrimoineId?: string`, `typeException?: 'INCLUSION' \| 'EXCLUSION'` |
| Interface `AffectationPayload` | `bienId` devient `bienId?: string` (optionnel) ; ajouter `patrimoineId?: string`, `typeException?: 'INCLUSION' \| 'EXCLUSION'` |
| Nouvelle méthode | `listerAffectationsPatrimoine(patrimoineId: string): Observable<Affectation[]>` — appelle `GET /api/patrimoines/{id}/affectations` |

**Contrainte :** rendre `bienId` optionnel ne casse pas l'usage existant (le code existant passe toujours un `bienId`). Vérifier qu'aucun test Karma ne force `bienId` présent.

### 3.2 Dashboard bailleur — `frontend/src/app/bailleur/dashboard/`

**Composant TypeScript (`dashboard.component.ts`) :**

Nouvelles propriétés :
- `affectationsPatrimoine: Record<string, Affectation[]>` — indexé par `patrimoineId`, affectations actives par patrimoine
- `affectationPatrimoineForm: FormGroup` — `{ patrimoineId, gestionnaireId, typeHonoraires, montantHonoraires, dateDebut, dateFin }`
- `exceptionForm: FormGroup` — `{ bienId, gestionnaireId, typeHonoraires, montantHonoraires, dateDebut, dateFin, typeException: 'INCLUSION' | 'EXCLUSION' }`

Nouvelles méthodes :
- `chargerAffectationsPatrimoine()` — pour chaque patrimoine actif, appelle `listerAffectationsPatrimoine(id)` ; alimenté au chargement du dashboard
- `creerAffectationPatrimoine()` — `POST /api/affectations` avec `patrimoineId`
- `creerException()` — `POST /api/affectations` avec `bienId` + `typeException` ; bouton affiché uniquement si une affectation patrimoine active existe pour ce patrimoine
- `revoquerAffectationPatrimoine(id)` — réutilise `revoquerAffectation(id)` existant

**Template HTML (`dashboard.component.html`) :**

Deux nouvelles sections dans le dashboard, après la section affectations au niveau bien existante :

**Section A — Affectation patrimoine :**
- Sélecteur patrimoine (dropdown sur `patrimoines` existant)
- Champ `gestionnaireId` (UUID, même UX que le champ existant)
- Champs honoraires + dates (même pattern que formulaire existant)
- Tableau des affectations patrimoine actives (patrimoineId, gestionnaireId, honoraires, dateDebut, statut, bouton Révoquer)

**Section B — Exceptions fines sur bien :**
- Sélecteur patrimoine actif (filtre `affectationsPatrimoine` pour n'afficher que ceux avec une affectation `ACTIVE`)
- Sélecteur bien (dropdown sur `biens` filtré par `patrimoineId` sélectionné)
- Champ `gestionnaireId` (pré-rempli depuis l'affectation patrimoine active, éditable)
- Sélecteur `typeException` : `EXCLUSION` | `INCLUSION`
- Champs honoraires + dates
- Tableau des exceptions existantes (bienId, typeException, statut, bouton Révoquer)

### 3.3 Tests — `frontend/src/app/bailleur/dashboard/dashboard.component.spec.ts`

Nouveaux cas de test :
- Chargement des affectations patrimoine au `ngOnInit`
- Création affectation patrimoine (succès)
- Section exception masquée si aucune affectation patrimoine active
- Création exception EXCLUSION (succès)
- Révocation affectation patrimoine

**Seuil de non-régression :** `ng test` ≥ 41 tests existants verts + nouveaux tests Sprint 4.

---

## 4. Plan d'exécution (étapes)

| Étape | Livrable | Validation |
|---|---|---|
| **E1** | Extension modèle `s02-api.service.ts` (`Affectation`, `AffectationPayload`, `listerAffectationsPatrimoine`) | `ng test` verts (pas de régression sur l'interface existante) |
| **E2** | Extension `dashboard.component.ts` (props + méthodes) | Compilation `ng build` sans erreur |
| **E3** | Extension `dashboard.component.html` (Sections A + B) | Rendu visuel, formulaires fonctionnels localement |
| **E4** | Tests `dashboard.component.spec.ts` (nouveaux cas Sprint 4) | `ng test` 0 FAIL |
| **E5** | `ng lint` + `ng build --configuration production` | 0 erreur, 0 warning bloquant |
| **E6** | `mvn verify` backend (non-régression, 0 modification backend) | 99 tests / 0 échec |
| **E7** | Gitleaks + Trivy SCA | No leaks / 0 HIGH-CRITICAL |
| **E8** | PR dédiée → CI GitHub verte (CodeQL, SonarQube, Backend, Frontend, Sécurité, Packaging) | CI SUCCESS |
| **E9** | Vérification navigateur locale ou Staging | Section A : création affectation patrimoine fonctionnelle ; Section B : exception EXCLUSION bloquée si pas d'affectation active |
| **E10** | Gate Staging (STG-ISOL-01 live + smoke 47/0 sur Staging) | `STAGING_DEPLOYED` |

---

## 5. Critères GO de fin de sprint (= critères de fusion `main`)

- ✓ `ng test` verts — régression 41 tests existants + nouveaux cas Sprint 4
- ✓ `mvn verify` 99 tests verts (backend inchangé)
- ✓ Affectation patrimoine créée et affichée dans le dashboard
- ✓ Exception EXCLUSION/INCLUSION créée et affichée
- ✓ Section exception masquée / désactivée si aucune affectation patrimoine active (garde RS-04 reflétée)
- ✓ Révocation d'une affectation patrimoine fonctionnelle
- ✓ `ng lint` et `ng build --configuration production` sans erreur
- ✓ CI GitHub verte (CodeQL, SonarQube Quality Gate, Gitleaks, Trivy, Packaging)
- ✓ `CHANGELOG.md` `[Non publié]` mis à jour avant fusion

**Ce sprint ne déploie pas en Production.** La promotion Staging et Production suivent les Gates CGPA habituels (STG-ISOL-01 + Gate Staging, puis Gate Production distinct).

---

## 6. Risques

| ID | Nature | Mitigation |
|---|---|---|
| RS-F01 | `AffectationPayload.bienId` rendu optionnel — régression possible si le compilateur TypeScript détecte un usage implicite comme requis | Vérifier au E1 que `ng build` passe sans erreur de type ; corriger les usages existants si besoin |
| RS-F02 | L'UI propose l'exception avant de savoir si une affectation patrimoine active existe → erreur 400 RS-04 | Conditionner le formulaire Section B à la présence d'une affectation patrimoine `ACTIVE` chargée au E2 |
| RS-F03 | Performance dashboard : deux nouvelles requêtes par patrimoine (`listerAffectationsPatrimoine`) au `ngOnInit` | Déclencher les requêtes en parallèle (`forkJoin`) ; ENF-06 (< 2 s) vérifié au E9 sur un jeu de données réel |
| RS-F04 | Smoke 47/0 : le script actuel ne couvre pas l'affectation patrimoine UI — il est basé sur des appels API directs | Le smoke reste API-first (non affecté par l'UI) ; la vérification navigateur E9 couvre le parcours UI |

---

## 7. Hors périmètre Sprint 4

- Lookup gestionnaire par e-mail (différé — B1 actée)
- Nouvelle page/route dédiée affectations (différé — C1 actée)
- Dashboard gestionnaire — mise à jour de la vue gestionnaire pour afficher le patrimoine d'appartenance d'une affectation (lot ultérieur)
- Déploiement Production (Gate Production distinct, non décidé par ce kickoff)
