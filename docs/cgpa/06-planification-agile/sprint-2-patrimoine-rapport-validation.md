# Rapport de validation — Sprint 2 Patrimoine backend-first

> **Statut :** validation locale backend/frontend/sécurité exécutée le 2026-06-23 sur la branche `sprint-2-patrimoine-reprise`.
>
> **Verdict :** GO technique local complet. Backend, frontend, secrets et SCA HIGH/CRITICAL sont verts après relance du scan Trivy global avec cache Maven local monté.

## 1. Périmètre validé

- US-84 : affectation patrimoine créée/révoquée par bailleur propriétaire.
- Héritage ReBAC : gestionnaire affecté à un patrimoine voit les biens du patrimoine, y compris les biens ajoutés après affectation.
- Liste effective : `GET /api/biens` gestionnaire inclut les biens hérités via patrimoine sans doublons.
- RS-06 : archivage d’un patrimoine avec affectation patrimoine `ACTIVE` bloqué.
- Non-régression : affectations bien existantes conservées.
- Frontend : aucune adaptation fonctionnelle requise ; lint/build/tests restent verts.

## 2. Validations exécutées

### Backend

Commande :

```bash
mvn -f backend/pom.xml verify
```

Résultat :

- `BUILD SUCCESS`
- `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`
- Flyway : `Successfully applied 13 migrations to schema "public", now at version v13`
- Spotless : `0 needs changes`
- JaCoCo : `All coverage checks have been met`
- Artefact généré : `backend/target/loyertracker-api-1.0.0.jar`

### Frontend lint

Commande :

```bash
cd frontend && npm run lint
```

Résultat :

- `All files pass linting.`

### Frontend build production

Commande :

```bash
cd frontend && npm run build -- --configuration production
```

Résultat :

- Build OK en `4.755 seconds`
- Bundle initial : `321.06 kB` brut / `92.04 kB` estimé transféré
- Warning non bloquant : `js-sha256` utilisé par `keycloak-js` n’est pas ESM
- Output : `frontend/dist/loyertracker`

### Frontend tests

Commande :

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox --code-coverage
```

Résultat :

- `TOTAL: 41 SUCCESS`
- Coverage : statements `75.83%`, branches `57.44%`, functions `71.15%`, lines `74.89%`

## 3. Sécurité

### Secrets

Commande :

```bash
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest detect --source=/repo --redact --no-banner
```

Résultat :

- `147 commits scanned`
- `no leaks found`

### SCA frontend

Commande :

```bash
docker run --rm -v "$PWD/frontend:/project" aquasec/trivy:latest fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed /project
```

Résultat :

- `package-lock.json` scanné
- `Vulnerabilities: 0`

### SCA global repository

Première commande :

```bash
docker run --rm -v "$PWD:/project" aquasec/trivy:latest fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed /project
```

Premier résultat :

- **Bloqué hors code** : Maven Central a retourné `429 Too Many Requests` sur `spring-framework-bom-6.2.18.pom`.
- Message Trivy : `The repository blocks all subsequent requests from this IP until the block clears.`

Relance avec cache Maven local monté :

```bash
docker run --rm -v "$PWD:/project" -v "$HOME/.m2:/root/.m2:ro" aquasec/trivy:latest fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed /project
```

Résultat final :

- `backend/pom.xml` scanné : `Vulnerabilities: 0`
- `frontend/package-lock.json` scanné : `Vulnerabilities: 0`

## 4. État Git au moment de la validation

```bash
## sprint-2-patrimoine-reprise
```

La documentation de validation est ajoutée après les commits fonctionnels Sprint 2 déjà présents :

- `9cb6f39 feat: enforce patrimoine affectation inheritance`
- `96565d1 test: block patrimoine archival with active affectation`

## 5. Décision technique locale

- **GO local backend** : oui.
- **GO local frontend** : oui.
- **GO local secrets** : oui.
- **GO local SCA frontend** : oui.
- **GO final sécurité avant PR/merge** : oui localement ; à confirmer par le gate CI officiel après push/PR.

## 6. Points restants avant PR/merge

1. Créer/pousser la branche distante `sprint-2-patrimoine-reprise`.
2. Ouvrir une PR Sprint 2 Patrimoine backend-first.
3. Vérifier les checks GitHub : Backend, Frontend, Sécurité, CodeQL, Packaging Docker.
4. Décider la promotion staging uniquement après CI verte et revue PO/technique.
