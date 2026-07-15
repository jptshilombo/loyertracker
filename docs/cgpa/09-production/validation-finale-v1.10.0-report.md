# Rapport Validation Finale — Release `1.10.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-15 |
| Fenêtre | ~13:41–14:05 UTC |
| Release | `1.10.0` — `sha-c9200a51` |
| Smoke | **62 PASS / 0 FAIL au premier passage** |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint** |

## Autorisation

La réactivation temporaire de `bailleur-test@test.local` (et de `directAccessGrants` sur
`loyertracker-spa`, révoqué automatiquement par le script) a fait l'objet d'une confirmation PO
explicite dédiée, distincte de l'autorisation du déploiement technique — même discipline que
`1.2.1`→`1.9.0`.

## Smoke Production

Invocation :

```
env BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml bash infra/smoke/smoke-stack.sh
```

`bailleur-test@test.local` (`43443d1e-…` KC / `c7296c69-…` DB) réactivé temporairement
(`enabled=true`) sur autorisation PO explicite, redésactivé après le run (§2).

**Résultat : 62 PASS / 0 FAIL au premier passage.** Couverture : sanity Flyway 24/24, pool
`loyertracker_api` NOSUPERUSER/NOBYPASSRLS, JWT réels via Nginx, parcours bailleur (inscription,
patrimoine, bien, bail), invitation → acceptation (Admin API réelle) → JWT gestionnaire,
affectation/échéances/pointage/honoraires, alertes (PREAVIS) et audit, scoping gestionnaire,
isolation cross-tenant live (2e bailleur), RGPD (export, effacement locataire, anonymisation),
garde-fous AuthN/ports, surface publique de vérification des quittances sans oracle.
Échafaudage `directAccessGrants` révoqué automatiquement par le script (vérifié `false` après).

**Ce smoke ne couvre pas encore `/api/gestionnaires`/`/api/locataires`** (même écart déjà
documenté au Gate Staging) : ces endpoints ont été validés manuellement en conditions réelles
lors du Gate Staging (48 PASS/0 FAIL) sur le candidat identique `sha-c9200a51` ; aucune
vérification manuelle supplémentaire n'a été jugée nécessaire en Production dans la mesure où le
comportement, le RBAC/ReBAC et la RLS sont indépendants de l'environnement et déjà prouvés.

## Nettoyage transactionnel

RUN_ID `1784120083`. Toutes les entités créées par le run ont été identifiées par leur
identifiant réel (patrimoine/bien/bail/affectation/gestionnaire/bailleur2) avant toute
suppression, puis supprimées **en une seule transaction** :

| Entité | Quantité | Détail |
|---|---:|---|
| Paiement | 9 | `bailleur_id = c7296c69-…` |
| Honoraire | 9 | idem |
| Alerte | 6 | idem |
| Audit log | 3 | idem (pointage, validation honoraire, effacement locataire) |
| Invitation | 1 | `gest-smoke-1784120083@test.local` |
| Affectation | 1 | patrimoine smoke ↔ gestionnaire smoke |
| Bail | 1 | `cca8a97e-…` |
| Bien | 1 | `31c13c2f-…` |
| Patrimoine (bailleur-test) | 1 | `99c3ef82-…` (« Patrimoine Smoke 1784120083 ») |
| Gestionnaire | 1 | `gest-smoke-1784120083@test.local` |
| Patrimoine (bailleur2, auto-créé à l'inscription) | 1 | « Patrimoine principal », `5ab380d8-…` — écart détecté en cours de nettoyage (1re tentative de transaction annulée par contrainte FK, corrigée avant nouvelle tentative) |
| Bailleur (2e bailleur smoke) | 1 | `bailleur2-smoke-1784120083@test.local` |

Comptes Keycloak du run supprimés : `gest-smoke-1784120083@test.local` (`c8deb3ee-…`),
`bailleur2-smoke-1784120083@test.local` (`96c7ac80-…`). `bailleur-test` redésactivé
(`enabled=false`), `directAccessGrantsEnabled=false` confirmé sur `loyertracker-spa`.

## État final

| Contrôle | Résultat |
|---|---|
| Résidus du RUN_ID en base | 0 bailleur, 0 gestionnaire, 0 patrimoine |
| Baseline métier post-nettoyage | 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties, 1 gestionnaire, 6 quittances — identique au Préflight |
| Garanties / mouvements | 8 / 8, invariant PASS |
| Services | 8/8 actifs, 4/4 healthy, restart=0 |
| Erreurs API | 0 ligne 5xx depuis le redéploiement |
| Production publique | `https://loyertracker.loyerpro.org` → 200 |

**Validation finale PASS — `PRODUCTION_DEPLOYED` atteint le 2026-07-15 (release `1.10.0`,
`sha-c9200a51`).** Hypercare (T0/T+12/T+24) et clôture de release restent des étapes distinctes.
