# Gate Staging v5.4.1 — Sprint 12 EP-14b

| Champ | Valeur |
|---|---|
| Date | 2026-07-06 |
| Candidat | merge PR #202 `75646d8`, tag `sha-75646d8f` |
| Rollback | `sha-9713fdaa` |
| Environnement | `ai-test-server`, `https://loyertracker.staging.loyerpro.org` |
| Décision | **GO — `STAGING_DEPLOYED`** |

## Conditions d'entrée

| Critère | Statut | Preuve |
|---|---|---|
| Sprint clôturé | PASS | `cloture-sprint12-ep14b.md` |
| Revue sécurité dédiée | PASS | `security-review-sprint12-ep14b.md` |
| CI et images | PASS | CI post-merge verte ; manifests API/Web `sha-75646d8f` présents |
| Secrets | PASS | HMAC présent (64 caractères), kid et URL de vérification présents, valeurs non exposées |
| Sauvegarde | PASS | `loyertracker-20260706-171755.dump`, 517 Kio, SHA-256 `62d7790f…`, catalogue 777 entrées ; globals 1,1 Kio, permissions 600 |

## STG-ISOL-01

| Contrôle | Avant | Après | Résultat |
|---|---|---|---|
| Projet Compose | `loyertracker-staging` | identique | PASS |
| Conteneurs projet | 9, tous Up, 4 healthy | identiques ; seuls API/Nginx recréés | PASS |
| NPM mutualisé | running, restart=0 | running, restart=0 | PASS |
| Réseau dédié | `loyertracker-staging_loyertracker-net` | identique | PASS |
| Volumes dédiés | postgres + prometheus | identiques | PASS |
| Ports | Nginx 18080/18443 ; Pushgateway 127.0.0.1:9091 | identiques | PASS |
| Services internes | aucun port hôte API/PostgreSQL/Keycloak | identique | PASS |
| Commandes | lecture globale uniquement ; mutation ciblée Compose | `pull api nginx`, `up -d --no-deps api nginx` | PASS |
| Restart counts | 0 pour tous | 0 pour tous | PASS |

**Verdict STG-ISOL-01 : PASS.** Aucun conteneur, réseau, volume ou reverse proxy tiers n'a été
arrêté, supprimé ou modifié. Aucune commande Docker globale destructive n'a été exécutée.

## Déploiement et validation

- Dépôt hôte fast-forward `9713fda` → `75646d8`; tag `.env` promu à `sha-75646d8f`.
- Déploiement strictement ciblé : API et Nginx recréés ; PostgreSQL, Keycloak, monitoring et NPM
  inchangés.
- Flyway 22/22 ; pool sous `loyertracker_api` NOSUPERUSER/NOBYPASSRLS.
- Smoke **62 PASS / 0 FAIL au premier passage** ; `directAccessGrants` révoqué automatiquement.
- Surface publique : réponse invalide sans oracle, téléchargement invalide 404, rate-limit 429,
  CSP/HSTS/clickjacking et métriques vérifiés.
- Parcours positif réel : `QT-2026-000001` VALIDE, PDF servi conforme à `pdf_hash`.

## Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | GO : clôture, revue sécurité, preuves et décisions additives présentes |
| Enterprise Architect | GO : capability HMAC, fonctions privilégiées, K2 et isolation cohérentes |
| DevSecOps Lead | GO : CI/images, backup, smoke et STG-ISOL-01 PASS |
| Release Manager | GO Staging uniquement ; Production soumise au Gate `1.9.0` distinct |
| Chief Delivery Officer | **GO — `STAGING_DEPLOYED`** |

## Statuts et suite

- `STAGING_READY` : atteint avant déploiement.
- `STAGING_DEPLOYED` : atteint sur `sha-75646d8f`.
- `PRODUCTION_READY` / `PRODUCTION_DEPLOYED` : non atteints.
- Prochaine étape autorisée : instruire le Gate Production unique `1.9.0` couvrant Sprints 11 et
  12. Ce document n'autorise aucun déploiement Production.
