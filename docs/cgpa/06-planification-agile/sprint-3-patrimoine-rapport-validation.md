# Rapport de validation — Sprint 3 Patrimoine (US-85, exceptions fines par bien)

> **Statut :** validation locale backend/sécurité exécutée le 2026-06-25 sur la branche
> `codex/sprint-3-patrimoine-exceptions`, après Kickoff Sprint 3 confirmé par le PO (GO sans
> réserve, `docs/cgpa/06-planification-agile/plan-execution-patrimoine.md`).
>
> **Verdict :** GO technique local complet. Backend et secrets/SCA HIGH/CRITICAL sont verts.
> Périmètre backend-only (tranché par le PO le 2026-06-24) : aucune adaptation frontend.

## 1. Périmètre validé

- US-85 : fin de la résolution à priorité entre affectation bien et affectation patrimoine (RM-98).
- Migration **V15** : colonne `affectation.type_exception` (`INCLUSION`/`EXCLUSION`, nullable,
  `CHECK` exigeant `bien_id`), backfill `INCLUSION` sur les affectations bien existantes
  (non-régression US-23/24).
- `gestionnaire_affecte_actif`/`biens_affectes_gestionnaire` réécrits : une affectation bien
  `ACTIVE` court-circuite toujours l'héritage patrimoine (`INCLUSION` accorde, `EXCLUSION` refuse) ;
  en son absence, repli sur l'affectation patrimoine héritée.
- **RS-04** : une `EXCLUSION` sans affectation patrimoine `ACTIVE` du même gestionnaire est rejetée
  en 400 (`AffectationService.verifierExclusionAdossee`). Une `INCLUSION` redondante reste tolérée.
- **Correctif ciblé `calculer_honoraires`** (hors livrables initiaux du cadrage, identifié pendant
  l'implémentation) : une affectation/bien couvert par une `EXCLUSION` est un carve-out d'accès, pas
  un mandat de gestion — il ne doit jamais générer d'honoraire. La fonction V14 aurait sinon facturé
  une `EXCLUSION` comme une affectation normale. Corrigé dans V15, sans autre changement de
  comportement par rapport à V14.
- Aucun changement Java côté `AuthorizationService` (résolution centralisée en fonction SQL
  `SECURITY DEFINER`, conforme au cadrage).
- Frontend : hors périmètre (backend-only, tranché par le PO le 2026-06-24).

## 2. Validations exécutées

### Backend

Commande :

```bash
mvn -f backend/pom.xml verify
```

Résultat :

- `BUILD SUCCESS` (exit code 0)
- `Tests run: 99, Failures: 0, Errors: 0, Skipped: 0` (agrégat de tous les modules de test)
- Flyway : migration appliquée jusqu'à la version **v15** sur chaque conteneur Testcontainers
- Détail des classes de test touchées par Sprint 3 :
  - `SchemaMigrationTest` : 10/10 (assertion `migrationsExecuted == 15`)
  - `AffectationModelTest` : 4/4 (2 nouveaux tests : détection d'exception sans bien, défaut
    `INCLUSION`)
  - `AuthorizationServiceIntegrationTest` : 10/10 (5 nouveaux tests : les 4 combinaisons RM-98 §5
    de `securite-patrimoine.md`, plus la révocation patrimoine avec inclusion bien indépendante)
  - `S02BiensBauxAffectationsIntegrationTest` : 9/9 (2 nouveaux tests HTTP : RS-04 — rejet 400 puis
    acceptation après affectation patrimoine active ; exclusion effective sur `GET /api/biens` et
    sur la création de bail)
- Non-régression complète confirmée : aucune régression sur les suites S02/S03/S04 existantes
  (honoraires, alertes, audit, paiements, garanties), ni sur les tests RLS/cross-tenant.

## 3. Sécurité

### Secrets

Commande :

```bash
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest detect --source=/repo --redact --no-banner
```

Résultat :

- `168 commits scanned`
- `no leaks found`

### SCA (dépendances)

Commande :

```bash
docker run --rm -v "$PWD:/project" -v "$HOME/.m2:/root/.m2:ro" aquasec/trivy:latest fs \
  --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed /project
```

Résultat :

- `backend/pom.xml` scanné : `Vulnerabilities: 0`
- `frontend/package-lock.json` scanné : `Vulnerabilities: 0`

## 4. État Git au moment de la validation

Branche : `codex/sprint-3-patrimoine-exceptions`, créée depuis `main` (`083720d`, post-merge PR #79).

Fichiers touchés :

- `backend/src/main/resources/db/migration/V15__affectations_exceptions.sql` (nouveau)
- `backend/src/main/java/com/loyertracker/affectations/TypeException.java` (nouveau)
- `backend/src/main/java/com/loyertracker/affectations/Affectation.java`
- `backend/src/main/java/com/loyertracker/affectations/AffectationDto.java`
- `backend/src/main/java/com/loyertracker/affectations/AffectationRepository.java`
- `backend/src/main/java/com/loyertracker/affectations/AffectationRequest.java`
- `backend/src/main/java/com/loyertracker/affectations/AffectationService.java`
- Tests : `AffectationModelTest`, `SchemaMigrationTest`,
  `S02BiensBauxAffectationsIntegrationTest`, `AuthorizationServiceIntegrationTest`
- Documentation : `plan-execution-patrimoine.md` (Kickoff Sprint 3), `CHANGELOG.md`,
  `docs/project-state.md`, le présent rapport.

## 5. Décision technique locale

- **GO local backend** : oui.
- **GO local secrets** : oui.
- **GO local SCA** : oui.
- **GO final sécurité avant PR/merge** : oui localement ; à confirmer par le gate CI officiel
  (CodeQL, SonarQube, Trivy images, packaging Docker) après push/PR.

## 6. Points restants avant PR/merge

1. Pousser la branche distante `codex/sprint-3-patrimoine-exceptions`.
2. Ouvrir une PR Sprint 3 Patrimoine (US-85), distincte de la PR #80 (clôture Hotfix `1.1.1`).
3. Vérifier les checks GitHub : Backend, Sécurité, CodeQL, Packaging Docker.
4. Décider la promotion staging/release uniquement après CI verte et revue PO/technique — non
   couvert par ce Kickoff, qui n'autorise que l'implémentation et la clôture technique du Sprint.
