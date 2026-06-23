# Plan d'Exécution — Sprint 2 Patrimoine

> **Statut :** approuvé — GO PO Option A confirmé le 2026-06-21.
>
> **Règle de gouvernance :** le Sprint 2 peut démarrer en implémentation backend-first selon ce plan. Toute extension de périmètre, notamment `EXCLUSION` complète ou UX avancée, nécessite un nouvel arbitrage PO.

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-21 |
| Périmètre | Sprint 2 — Gestion des affectations patrimoine + priorité de résolution |
| Stories couvertes | US-84, début US-85 |
| Pré-requis | Sprint 1 Patrimoine clôturé ; RM-98 / RS-04 / RS-05 / RS-06 déjà arbitrés par le PO le 2026-06-21 |
| Décision liée | D-PAT-001 / ADR-11 |
| Références | `plan-execution-patrimoine.md`, `addendum-patrimoine-backlog.md`, `securite-patrimoine.md`, `ADR-11-introduction-patrimoine.md` |
| Verdict proposé | **GO PO confirmé — Option A : démarrage Sprint 2 backend-first autorisé** |

## 1. Objectif Sprint 2

Permettre à un bailleur d'affecter un gestionnaire à un patrimoine entier, avec héritage dynamique sur les biens du patrimoine, tout en préparant la priorité de résolution entre affectation patrimoine et affectation bien.

Le Sprint 2 doit rester centré sur US-84 et la partie résolution de US-85 :

- affectation au niveau `patrimoineId` ;
- unicité et validation du périmètre affecté ;
- extension de l'autorisation fine `AuthorizationService` ;
- endpoint dédié `/api/patrimoines/{id}/affectations` ;
- garde RS-06 : archivage d'un patrimoine refusé si une affectation patrimoine `ACTIVE` existe ;
- tests de non-régression des affectations bien existantes.

## 2. Hors périmètre explicite

- Pas de nouveau rôle Keycloak : `BAILLEUR` / `GESTIONNAIRE` restent inchangés.
- Pas de duplication matérialisée `gestionnaire × bien` pour l'héritage patrimoine.
- Pas de dashboard ou UX avancée de visualisation du périmètre effectif, sauf adaptation minimale si un test frontend existant casse.
- Pas de refonte complète des honoraires.
- Pas de démarrage Sprint 3 : les exceptions fines complètes US-85 restent limitées aux éléments nécessaires pour la priorité de résolution Sprint 2.

## 3. Règles métier et sécurité à implémenter

### 3.1. Modèle Affectation

L'entité `Affectation` doit accepter deux granularités exclusives :

- affectation bien : `bienId` renseigné, `patrimoineId` nul ;
- affectation patrimoine : `patrimoineId` renseigné, `bienId` nul.

Règle impérative : exactement un des deux champs doit être renseigné.

### 3.2. Priorité de résolution

Pour un gestionnaire et un bien donné :

1. si une affectation bien `ACTIVE` existe pour ce gestionnaire et ce bien, elle prévaut ;
2. sinon, si une affectation patrimoine `ACTIVE` existe pour ce gestionnaire et le patrimoine du bien, l'accès est accordé ;
3. sinon, l'accès est refusé.

Le comportement existant `INCLUSION` bien doit rester compatible avec l'existant. Toute introduction d'`EXCLUSION` doit rester fail-closed et testée.

### 3.3. RS-06 — Archivage patrimoine

Un patrimoine avec au moins une affectation patrimoine `ACTIVE` ne doit pas pouvoir être archivé.

Réponse attendue : HTTP 400 avec message fonctionnel indiquant qu'une révocation explicite est requise.

## 4. Impact technique attendu

### Backend — fichiers principaux

- Migration à créer : `backend/src/main/resources/db/migration/V13__affectations_patrimoine.sql`
- Modifier : `backend/src/main/java/com/loyertracker/affectations/Affectation.java`
- Modifier : `backend/src/main/java/com/loyertracker/affectations/AffectationRequest.java`
- Modifier : `backend/src/main/java/com/loyertracker/affectations/AffectationDto.java`
- Modifier : `backend/src/main/java/com/loyertracker/affectations/AffectationRepository.java`
- Modifier : `backend/src/main/java/com/loyertracker/affectations/AffectationService.java`
- Modifier : `backend/src/main/java/com/loyertracker/affectations/AffectationController.java`
- Modifier : `backend/src/main/java/com/loyertracker/securite/AuthorizationService.java`
- Modifier : `backend/src/main/java/com/loyertracker/biens/BienService.java`
- Modifier : `backend/src/main/java/com/loyertracker/patrimoine/PatrimoineService.java`

### Backend — tests principaux

- Modifier : `backend/src/test/java/com/loyertracker/s02/S02BiensBauxAffectationsIntegrationTest.java`
- Modifier : `backend/src/test/java/com/loyertracker/securite/AuthorizationServiceIntegrationTest.java`
- Ajouter si nécessaire : `backend/src/test/java/com/loyertracker/patrimoine/PatrimoineAffectationIntegrationTest.java`

### Frontend

Le Sprint 2 peut rester backend-first. Frontend uniquement si les contrats DTO exposés forcent une adaptation de compilation ou un test existant.

## 5. Plan d'exécution TDD par tâches

### Tâche 1 — Préparer la migration V13

**Objectif :** étendre la table `affectation` pour supporter `patrimoine_id` et la contrainte d'exclusivité.

**Fichiers :**

- Créer : `backend/src/main/resources/db/migration/V13__affectations_patrimoine.sql`
- Tester : `backend/src/test/java/com/loyertracker/securite/AuthorizationServiceIntegrationTest.java`

**Étapes :**

1. Écrire un test d'intégration qui insère une affectation patrimoine valide.
2. Vérifier que le test échoue avant migration.
3. Ajouter `patrimoine_id UUID REFERENCES patrimoine(id)`.
4. Rendre `bien_id` nullable.
5. Ajouter une contrainte `CHECK` imposant exactement un périmètre : `bien_id IS NOT NULL` XOR `patrimoine_id IS NOT NULL`.
6. Ajouter les index nécessaires : `(patrimoine_id, gestionnaire_id, statut)` et maintien de l'index bien existant.
7. Exécuter : `mvn -f backend/pom.xml -Dtest=AuthorizationServiceIntegrationTest test`.

**Attendu :** migration appliquée par Flyway ; contrainte d'exclusivité effective.

### Tâche 2 — Adapter le modèle Java Affectation

**Objectif :** exposer `patrimoineId` dans entity/request/dto sans casser les affectations bien existantes.

**Fichiers :**

- Modifier : `Affectation.java`
- Modifier : `AffectationRequest.java`
- Modifier : `AffectationDto.java`

**Étapes :**

1. Ajouter `UUID patrimoineId` nullable dans le record request.
2. Retirer `@NotNull` de `bienId` et valider l'exclusivité côté service.
3. Ajouter `patrimoineId` dans le DTO.
4. Ajouter deux constructeurs/factories explicites si utile : affectation bien vs affectation patrimoine.
5. Exécuter le test S02 existant pour confirmer que l'affectation bien historique reste verte.

**Attendu :** l'API existante `/api/affectations` continue d'accepter les payloads avec `bienId`.

### Tâche 3 — Créer l'affectation patrimoine côté service

**Objectif :** permettre la création d'une affectation sur patrimoine, avec validation bailleur/propriété.

**Fichiers :**

- Modifier : `AffectationService.java`
- Modifier : `AffectationRepository.java`
- Modifier : `AffectationController.java`

**Étapes :**

1. Écrire un test MockMvc : POST `/api/patrimoines/{id}/affectations` par bailleur propriétaire retourne 201.
2. Ajouter méthode controller dédiée.
3. Valider que le patrimoine existe, appartient au bailleur courant et n'est pas archivé.
4. Valider que le gestionnaire existe.
5. Persister l'affectation `ACTIVE` avec `patrimoineId` et `bienId = null`.
6. Refuser en 400 les payloads sans périmètre ou double périmètre.
7. Exécuter : `mvn -f backend/pom.xml -Dtest=S02BiensBauxAffectationsIntegrationTest test`.

**Attendu :** affectation patrimoine créée, sans régression sur l'affectation bien.

### Tâche 4 — Étendre AuthorizationService

**Objectif :** intégrer l'héritage dynamique patrimoine dans `peutAccederBien`.

**Fichiers :**

- Modifier : `AuthorizationService.java`
- Modifier : `V13__affectations_patrimoine.sql`
- Modifier : `AuthorizationServiceIntegrationTest.java`

**Étapes :**

1. Ajouter fonction SQL `gestionnaire_affecte_patrimoine_actif(p_bien uuid, p_gestionnaire uuid)` ou équivalent auditables sous `SECURITY DEFINER`.
2. Ajouter méthode Java `estGestionnaireAffectePatrimoineActif(UUID bienId, UUID gestionnaireId)`.
3. Adapter `peutAccederBien` : affectation bien active prioritaire, sinon affectation patrimoine active, sinon false.
4. Ajouter test : gestionnaire avec affectation patrimoine voit tous les biens du patrimoine.
5. Ajouter test : nouveau bien ajouté après affectation patrimoine devient accessible sans nouvelle affectation.
6. Ajouter test : patrimoine d'un autre bailleur reste inaccessible.

**Attendu :** héritage dynamique vérifié, fail-closed conservé.

### Tâche 5 — Adapter la liste des biens gestionnaire

**Objectif :** faire apparaître dans `GET /api/biens` les biens accessibles par affectation patrimoine.

**Fichiers :**

- Modifier : `BienService.java`
- Modifier : fonction SQL `biens_affectes_gestionnaire` dans `V13__affectations_patrimoine.sql`

**Étapes :**

1. Écrire un test MockMvc : gestionnaire avec affectation patrimoine liste deux biens du patrimoine.
2. Étendre la fonction SQL pour retourner l'union des biens affectés directement et des biens hérités via patrimoine.
3. Éviter les doublons si une affectation bien redondante existe.
4. Inclure `patrimoine_id` dans la projection pour ne pas retourner `null` dans `BienDto`.
5. Exécuter le test S02 ciblé.

**Attendu :** `GET /api/biens` reflète le périmètre effectif du gestionnaire.

### Tâche 6 — Implémenter RS-06 sur archivage patrimoine

**Objectif :** empêcher l'archivage d'un patrimoine encore affecté activement.

**Fichiers :**

- Modifier : `PatrimoineService.java`
- Modifier : `AffectationRepository.java`
- Tester : `PatrimoineAffectationIntegrationTest.java` ou S02

**Étapes :**

1. Écrire un test : patrimoine avec affectation patrimoine `ACTIVE` → archivage retourne 400.
2. Ajouter requête repository `existsByPatrimoineIdAndStatut`.
3. Brancher la garde dans l'archivage patrimoine.
4. Ajouter test : après révocation de l'affectation, archivage autorisé.

**Attendu :** RS-06 effective et révocation explicite obligatoire.

### Tâche 7 — Couvrir la priorité minimale US-85

**Objectif :** prouver que l'affectation bien garde la priorité sur l'héritage patrimoine.

**Fichiers :**

- Modifier : `AuthorizationServiceIntegrationTest.java`
- Modifier si nécessaire : modèle `Affectation` / migration V13 pour `typeException`

**Étapes :**

1. Ajouter test `INCLUSION` bien sans affectation patrimoine : accès accordé, comportement existant.
2. Ajouter test affectation patrimoine + affectation bien prioritaire : la résolution bien court-circuite le patrimoine.
3. Si `EXCLUSION` est introduit en Sprint 2, ajouter test : patrimoine actif + exclusion bien → accès refusé au bien exclu.
4. Si `EXCLUSION` est reporté Sprint 3, documenter explicitement la réserve dans `project-state.md` et ne pas livrer de demi-logique non testée.

**Attendu :** aucune ambiguïté de priorité avant merge.

### Tâche 8 — Validation complète avant PR/merge

**Objectif :** verrouiller la non-régression backend/frontend/sécurité.

**Commandes minimales :**

```bash
mvn -f backend/pom.xml verify
cd frontend && npm run lint
cd frontend && npm run build -- --configuration production
cd frontend && npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox --code-coverage
```

**Sécurité minimale :**

```bash
gitleaks detect --source . --redact --no-banner
docker run --rm -v "$PWD/frontend:/project" aquasec/trivy fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed /project
```

**Attendu :** backend et frontend verts ; aucune fuite secret ; aucune vulnérabilité runtime HIGH/CRITICAL nouvelle.

## 6. Critères GO de fin Sprint 2

Le Sprint 2 ne sera clôturable que si :

- affectation patrimoine créée/révoquée par le bailleur propriétaire ;
- affectation patrimoine refusée pour patrimoine archivé ou cross-bailleur ;
- gestionnaire voit les biens actuels du patrimoine affecté ;
- gestionnaire voit automatiquement un bien ajouté après l'affectation patrimoine ;
- `GET /api/biens` gestionnaire reflète le périmètre effectif sans doublons ;
- `AuthorizationService` reste la seule source de vérité ReBAC ;
- archivage patrimoine avec affectation patrimoine `ACTIVE` retourne 400 ;
- révocation explicite puis archivage fonctionne ;
- affectations bien existantes non régressées ;
- `mvn verify`, frontend lint/build/test et scans sécurité passent.

## 7. Validation locale backend/frontend/sécurité — 2026-06-23

Le Sprint 2 backend-first a été validé localement sur la branche `sprint-2-patrimoine-reprise`.

Rapport détaillé : `docs/cgpa/06-planification-agile/sprint-2-patrimoine-rapport-validation.md`.

Résultats exécutés :

- Backend : `mvn -f backend/pom.xml verify` — `BUILD SUCCESS`, `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`, Flyway V1→V13, Spotless OK, JaCoCo OK.
- Frontend lint : `npm run lint` — `All files pass linting.`
- Frontend build production : `npm run build -- --configuration production` — build OK, bundle initial `321.06 kB` brut / `92.04 kB` estimé transféré.
- Frontend tests : `npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox --code-coverage` — `TOTAL: 41 SUCCESS`.
- Secrets : Gitleaks Docker — `147 commits scanned`, `no leaks found`.
- SCA frontend : Trivy FS sur `frontend/package-lock.json` — `Vulnerabilities: 0`.
- SCA global : Trivy FS relancé avec cache Maven local monté — `backend/pom.xml` et `frontend/package-lock.json` scannés, `Vulnerabilities: 0` HIGH/CRITICAL.

Décision locale : **GO technique local complet** ; le gate CI GitHub reste à confirmer après push/PR avant tout merge ou promotion staging.

## 8. Risques et garde-fous

- **Risque critique : fuite de périmètre gestionnaire.** Mitigation : tests d'autorisation avant implémentation, résolution centralisée dans `AuthorizationService`, fail-closed.
- **Risque migration : contrainte trop permissive sur `affectation`.** Mitigation : contrainte SQL d'exclusivité + validation applicative.
- **Risque oubli RS-06.** Mitigation : tâche dédiée et test d'archivage patrimoine.
- **Risque régression liste biens gestionnaire.** Mitigation : test `GET /api/biens` pour affectation bien seule, patrimoine seul et cas mixte.
- **Risque dette Sprint 3 masquée.** Mitigation : ne pas livrer d'`EXCLUSION` partiel sans test explicite ; documenter ce qui est reporté.

## 9. Décision demandée au PO

Choix PO confirmé : **Option A — GO Sprint 2 backend-first**.

Le Sprint 2 peut démarrer selon ce plan, avec les garde-fous suivants :

- commits courts et vérifiables ;
- TDD sur les règles ReBAC / héritage patrimoine ;
- aucune UX avancée hors adaptation minimale nécessaire ;
- pas d'`EXCLUSION` partiel sans test explicite ;
- validation complète backend/frontend/sécurité avant PR/merge.
