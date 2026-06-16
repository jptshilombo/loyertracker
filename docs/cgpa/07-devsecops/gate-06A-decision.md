# Gate 06A — DevSecOps Readiness · Décision (LoyerTracker)

> Gate ajouté par **CGPA v5.2** (`setup-cgpa/docs/cgpa/gates/gate-06A-devsecops.md`,
> `devsecops-governance.md`). Vérifie que le projet dispose des contrôles DevSecOps
> minimaux pour livrer de manière **reproductible, sécurisée et traçable**.
> Ratifié le **2026-06-16** dans le cadre de la reprise CGPA v5.2 (lève la réserve **R-V52-4**).

## Conditions d'entrée

- [x] Phase DevSecOps identifiée — Gate 06 statué **Go** le 2026-06-06.
- [x] Stratégie de pipeline définie — CI GitHub Actions (`.github/workflows/ci.yml`, `codeql.yml`).
- [x] Dépendances connues — Maven (`backend/pom.xml`) et npm (`frontend/package-lock.json`) verrouillées.
- [x] Politique de secrets définie — hors dépôt (`.env` gitignoré, Actions Secrets) + Gitleaks.
- [x] Niveau de risque explicite — MVP en cap vers la production (PME) ; secrets et production toujours protégés.

## Critères de validation (GO)

| # | Critère GO | Statut | Preuve |
|---|------------|--------|--------|
| 1 | Pipeline fonctionnel | ✅ | `ci.yml` (jobs Backend, Frontend, Sécurité, Packaging Docker) + `codeql.yml` ; CI verte sur `main` |
| 2 | Build reproductible | ✅ | JDK 21 / Node 24 épinglés, dépendances verrouillées ; images publiées sur GHCR à tag immuable `sha-<8>` (PR #24) |
| 3 | Tests automatiques exécutables | ✅ | Backend `mvn verify` (55 tests, Testcontainers, RLS `FORCE` sous `loyertracker_api`) ; frontend `ng test` (27) ; smoke stack complète `infra/smoke/smoke-stack.sh` (46/0) |
| 4 | Scan sécurité (selon le risque) | ✅ | **SAST** : CodeQL Java+TS (push + cron hebdomadaire) & SonarQube (quality gate bloquant, PR #42). **SCA** : Trivy `CRITICAL,HIGH` `exit-code 1` (bloquant) + OWASP Dependency-Check (informatif) |
| 5 | Secrets gérés hors code | ✅ | Gitleaks (historique complet, chaque push) ; `.env` non versionné ; service accounts injectés au runtime |
| 6 | Dépendances critiques surveillées | ✅ | Trivy bloquant sur dépendances npm **et** images ; remédiations datées prouvées : PR #20 (CVE openssl), PR #36 (CVE Angular) |
| 7 | Résultats tracés | ✅ | `docs/project-state.md` §3B (état DevSecOps) et §9, registre des décisions §11, présent document |
| — | Images conteneurs analysées (DSO-04) | ✅ | Trivy `image` sur `loyertracker-api` **et** `loyertracker-web`, bloquant, tracé avant staging/prod |

**Couverture DevSecOps Governance** : DSO-01 (build+tests) ✅, DSO-02 (SAST+SCA) ✅ **automatisés**,
DSO-03 (secrets) ✅, DSO-04 (images) ✅, DSO-05 (dépendances) ✅.

Aucun critère **GO sous réserve** n'a eu à être invoqué (les contrôles sont exécutés
automatiquement, pas seulement « disponibles ») ; aucun critère **NO GO** déclenché.

## Réserves

| # | Réserve | Périmètre | Échéance |
|---|---------|-----------|----------|
| OBS-02/03 | Alerting centralisé non branché | **Hors Gate 06A** — relève de l'Observability Governance / Gate Staging enrichi | Avant promotion production |

> La seule réserve résiduelle (alerting centralisé) n'appartient pas au périmètre du
> Gate 06A : elle est suivie au titre de l'Observability Governance (OBS-02/03) et du
> Gate Staging enrichi v5.2, non comme une condition de DevSecOps Readiness.

## Sous-agent mobilisé

| Sous-agent | Avis |
|------------|------|
| DevSecOps Lead | **GO** — DSO-01→05 couverts et exécutés automatiquement ; build reproductible ; scans SAST/SCA/secrets/images bloquants ; remédiations de dépendances prouvées (PR #20, #36) |

## Décision

- Recommandation DevSecOps Lead : **✅ GO**
- **Décision CGPA Chief Delivery Officer : ✅ GO**
- Date & responsable : **2026-06-16** — PO **jptshilombo@gmail.com** (jordan)

> ✅ **Gate 06A — DevSecOps Readiness statué GO.** Réserve **R-V52-4** levée. Aucune
> action corrective bloquante. La capacité de delivery sécurisée est ratifiée ; le
> développement (Phase 7) se poursuit. Réserve hors périmètre suivie : alerting
> centralisé (OBS-02/03), à brancher avant la promotion production (cf. R-V52-5 / Gate 07A).

---
*Livrable CGPA v5.2 — Gate 06A (DevSecOps Readiness). Réf. : `setup-cgpa/docs/cgpa/gates/gate-06A-devsecops.md`, `setup-cgpa/docs/cgpa/devsecops-governance.md`.*
