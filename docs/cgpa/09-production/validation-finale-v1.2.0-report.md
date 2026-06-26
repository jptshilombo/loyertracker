# Rapport final — Validation Production `1.2.0`

| Champ | Valeur |
|---|---|
| Date | 2026-06-26 |
| Début validation | 17:30 UTC |
| Fin validation | 17:49 UTC |
| Décision | **PASS — `PRODUCTION_DEPLOYED`** |
| Version | `1.2.0` |
| Commit | `5bf187af79218377b2f7db7800961725088d31a5` |
| Tag | `sha-5bf187af` |
| Rollback applicatif | `sha-0adc4941` |
| Backup | `loyertracker-20260626-182030.dump` |

## Pré-conditions

| Condition | Statut |
|---|---|
| `bailleur-test@test.local` réactivé (disabled depuis nettoyage 1.1.1) | Réactivé via kcadm avant smoke |
| Dépôt hôte mis à jour (`git pull` `05424aa3`→`14a672c`) | smoke-stack.sh V15 à jour |

## Smoke Production

Run ID : `1782495782`

| Section | Bilan |
|---|---|
| 0. Sanity (Flyway V15, pool, actuator) | 5/5 PASS |
| 1. JWT Keycloak bailleur via Nginx | 2/2 PASS |
| 2. Parcours bailleur (inscription 409, patrimoine, bien, bail) | 4/4 PASS |
| 3. Invitation → acceptation Admin API → JWT gestionnaire | 4/4 PASS |
| 4. Affectation, échéances, pointage, honoraires | 10/10 PASS |
| 5. Alertes, audit | 6/6 PASS |
| 6. Scoping gestionnaire | 4/4 PASS |
| 7. Isolation cross-tenant live (2e bailleur) | 9/9 PASS |
| 8. Garde-fous AuthN/ports | 2/2 PASS |
| **Bilan** | **47 PASS / 0 FAIL** |

Résultats notables :
- Flyway : 15 migrations appliquées (V15 `affectations_exceptions` confirmée) ✅
- Inscription bailleur-test : `409` (déjà inscrit, attendu) ✅
- `POST /api/batch/alertes` : `{"alertesCreees":42}` ✅
- Honoraire période `2025-12` = `72.00` (8 % de 900 encaissés) ✅
- Bailleur 2 : 0 bien visible du tenant 1 (isolation RLS confirmée) ✅
- Actuator : `{"status":"UP"}` ✅
- directAccessGrants révoqué à la fin (`trap scaffold_off`) ✅

## Preuve spécifique V15

La colonne `affectation.type_exception` est présente et opérationnelle. La migration V15
("affectations exceptions") a été appliquée au démarrage du conteneur `api` :

```
2026-06-26T17:26:15Z  Successfully applied 1 migration to schema "public", now at version v15
```

- Flyway validé : 15/15 migrations, rang max 15, 0 échec.
- La section 4 du smoke (POST `/api/affectations`) a traversé le code V15 sans erreur.
- La colonne `type_exception` est queryable dans la base (requête `GROUP BY type_exception`
  exécutée avec succès lors de la preuve pré-nettoyage).

## Nettoyage

Run ID smoke : `1782495782`. Un run orphelin antérieur (`1782227595`) non nettoyé à la
clôture `1.1.1` a également été supprimé de façon opportuniste.

### Données DB supprimées (2 runs combinés)

| Type | Quantité |
|---|---|
| Alertes smoke | 13 |
| Paiements smoke | 20 |
| Honoraires smoke | 20 |
| Baux smoke | 2 |
| Affectations smoke | 2 |
| Biens smoke | 2 |
| Patrimoines smoke | 2 |
| Invitations smoke | 2 |
| Gestionnaires smoke | 2 |
| Bailleur2 (run courant) | 1 (+ son patrimoine principal) |
| Bailleur2 orphelin (run 1782227595) | 1 (sans patrimoine ni bien — déjà supprimés) |
| Audit smoke | 2 (run courant) + 0 (orphelin) |

### Keycloak

| Utilisateur | Action |
|---|---|
| `bailleur2-smoke-1782227595@test.local` | Supprimé |
| `bailleur2-smoke-1782495782@test.local` | Supprimé |
| `gest-smoke-1782227595@test.local` | Supprimé |
| `gest-smoke-1782495782@test.local` | Supprimé |
| `bailleur-test@test.local` | Désactivé |
| `loyertracker-spa.directAccessGrantsEnabled` | `false` (révoqué par `trap` en fin de smoke) |

## Persistance du tag

`.env` mis à jour avant déploiement (`sha-0adc4941` → `sha-5bf187af`), backup
`.env.pre-1.2.0-20260626-172551`. `docker compose config --images` confirme :

```
ghcr.io/jptshilombo/loyertracker-api:sha-5bf187af
ghcr.io/jptshilombo/loyertracker-web:sha-5bf187af
```

Aucune mutation `.env` nécessaire post-validation (tag déjà persisté avant `docker compose up`).

## État final

| Contrôle | Résultat |
|---|---|
| API/Web | `sha-5bf187af` — digests conformes |
| Services applicatifs | `(healthy)`, restart count 0 |
| Flyway | V1→V15 (15/15) |
| API Actuator | UP |
| Racine publique | HTTP 200 |
| Prometheus | 5/5 cibles `up` |
| Alertes | Aucune (0) |
| `.env` tag | `sha-5bf187af` persisté |
| Données smoke | Nettoyées (2 runs) |
| Keycloak smoke | 4 users supprimés, `bailleur-test` désactivé |
| Bailleurs DB | 2 (production réel + bailleur-test) |
| Backup | `loyertracker-20260626-182030.dump` disponible |

## Risques maintenus

- `RP-120-02` : rollback schéma V15 non trivial — procédure `pg_restore` documentée
  (Gate Production `1.2.0` §Rollback). Aucune régression constatée.
- `RP-120-03` : correctif cascade dashboard (`c1e9c73`) exclu de `1.2.0` —
  à valider en Staging puis promu `1.2.1`.

## Décision finale

**Chief Delivery Officer : GO — `PRODUCTION_DEPLOYED`.**

La release `1.2.0` est déployée et validée sur
`https://loyertracker.loyerpro.org`.

- Sprint 3 Patrimoine (US-85, V15, RS-04) : en production ✅
- Correctif CORS Compose : en production ✅
- Smoke 47/0 : confirmé en conditions réelles ✅
