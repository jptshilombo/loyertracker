# Dossier candidat — Release `1.2.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-27 |
| Version | `1.2.1` |
| Type | PATCH — correctif frontend uniquement |
| Commit HEAD `main` | `47172297089c392c12254de5abb04b050e71b7a4` |
| Commit applicatif | `c1e9c735e39c0375b907be9da3302e67f5cb10d4` |
| Tag candidat | **`sha-47172297`** |
| Digest GHCR API | `sha256:eb6e362b1e76bc69669e71a2f0f7877011480247fd55972322038d47af703b52` |
| Digest GHCR Web | `sha256:ce9564195cb887b2db254d54003057b3a0e056a1eb4e4c87ba1947f664172cd0` |
| Production actuelle | `1.2.0` — `sha-5bf187af` |
| Rollback disponible | `sha-5bf187af` — applicatif seul, sans pg_restore |
| Plan d'exécution | `docs/cgpa/09-production/plan-execution-v1.2.1.md` |

## 1. Choix du tag candidat

Deux tags valides incluent le correctif `c1e9c73` :

| Tag | Commit HEAD | CI | Code applicatif |
|-----|-------------|----|----|
| `sha-c1e9c735` | `c1e9c73` (fix applicatif) | SUCCESS | Identique |
| **`sha-47172297`** | `47172297` (docs clôture `1.2.0`) | **SUCCESS** | Identique |

**Décision : `sha-47172297`** — HEAD courant de `main`, CI SUCCESS, image GHCR disponible avec
tags `sha-47172297` et `latest`. Les commits entre `c1e9c73` et `HEAD` sont exclusivement
documentaires (`docs/cgpa/09-production/`, `docs/project-state.md`) ; le code compilé (Java,
Angular) est identique entre les deux tags.

## 2. Vérification CI

| Job | Résultat |
|---|---|
| Backend (build + tests + SonarQube) | ✅ SUCCESS |
| Frontend (lint + build + tests) | ✅ SUCCESS |
| Sécurité (Gitleaks + SCA + Trivy) | ✅ SUCCESS |
| CodeQL (Java/Kotlin + JS/TS) | ✅ SUCCESS |
| Packaging Docker (push GHCR) | ✅ SUCCESS |

Commit HEAD `47172297` — CI SUCCESS constatée le 2026-06-27.

## 3. Périmètre applicatif — confirmation du diff

Diff `5bf187af..HEAD` restreint aux répertoires `backend/src`, `frontend/src`, `infra`,
`docker-compose*.yml`, `.github` :

```
frontend/src/app/bailleur/dashboard/dashboard.component.ts   |   8 +--
1 file changed (code applicatif)
```

**Un seul fichier applicatif modifié.** Aucun changement backend, aucune migration SQL,
aucun changement Compose, aucun changement CI.

### Détail de la modification

```diff
+import { finalize } from 'rxjs';
 ...
-    this.inscription.inscrire().subscribe({
+    this.chargerReferentielsBien();
+    this.inscription.inscrire().pipe(
+      finalize(() => this.chargerBiens()),
+    ).subscribe({
       next: (result) => {
         this.inscriptionStatus.set(...);
-        this.chargerBiens();
-        this.chargerReferentielsBien();
       },
```

**Comportement corrigé :** `chargerBiens()` s'exécute désormais via `finalize` (succès ET
erreur). `chargerReferentielsBien()` est lancé en parallèle de l'inscription. Le tableau de
bord ne reste plus vide lors d'une erreur d'inscription (401, 500, réseau).

## 4. Comparaison avec la Production `1.2.0`

| Axe | `1.2.0` (`sha-5bf187af`) | `1.2.1` (`sha-47172297`) |
|-----|--------------------------|--------------------------|
| Backend | ✅ Sprint 3 US-85, RS-04 | Inchangé |
| Migration Flyway | V15 (rang max = 15) | Aucune — rang max = 15 inchangé |
| CORS Compose | ✅ Câblé | Inchangé |
| Dashboard Angular | Biens non chargés si erreur inscription | **Corrigé via `finalize`** |
| Keycloak | Inchangé | Inchangé |
| Infra Compose | Inchangé | Inchangé |

## 5. Rollback

| Étape de rollback | Procédure |
|---|---|
| Rollback applicatif `1.2.1` → `1.2.0` | `LOYERTRACKER_TAG=sha-5bf187af docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d nginx api` |
| pg_restore requis ? | **Non** — aucune migration entre `1.2.0` et `1.2.1` |
| Rollback au-delà de `1.2.0` | Requiert `pg_restore` (V15 additive non réversible par Flyway) — RP-120-02 maintenue |

## 6. Verdict

**Candidat recevable.** Le tag `sha-47172297` est conforme au périmètre `1.2.1` :
- CI SUCCESS toutes jobs.
- Digests GHCR immuables disponibles.
- Un seul fichier applicatif modifié vs `1.2.0`.
- Pas de migration supplémentaire.
- Rollback trivial vers `sha-5bf187af`.

**Prochaine étape autorisée :** Gate Staging `1.2.1` (Étape 2 du plan d'exécution), sous
décision PO/Release Manager distincte.
