# Rapport Validation Finale — Release `1.12.0` (Sprint C EP-15)

| Champ | Valeur |
|---|---|
| Date | 2026-07-19 |
| `PRODUCTION_DEPLOYED` | **2026-07-19 ~11:57 UTC** |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Tag en Production | `sha-359f4d63` |
| Digest API | `sha256:ea040492bb5ad6b6a72b84665e22cd47a66d79c293b874fca481d5a276afe1c8` |
| Digest Web | `sha256:e70ebc7ba7d71406edaec6f890c2f57f06ae9d7c855680e0fba01914b4251968` |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint** |

## Autorisation

Réactivation temporaire de `bailleur-test@test.local` (et de `directAccessGrants` sur
`loyertracker-spa`, révoqué automatiquement par le script) : **autorisation PO explicite donnée le
2026-07-19** (« Instruis la validation finale de la release 1.12.0 »), distincte de l'autorisation
du déploiement technique — même discipline que toutes les releases précédentes.

## Smoke Production

Invocation :

```
env BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml bash infra/smoke/smoke-stack.sh
```

`bailleur-test@test.local` (`43443d1e-…` KC / `c7296c69-…` DB) réactivé temporairement
(`enabled=true`), redésactivé après le run.

**Résultat : 63 PASS / 0 FAIL au premier passage** — identique au résultat du Gate Staging
(`gate-staging-sprint-c-ep15-decision.md`). Couverture : sanity Flyway **26/26**, pool
`loyertracker_api` NOSUPERUSER/NOBYPASSRLS, JWT réels via Nginx, parcours bailleur (inscription,
patrimoine, bien, **création de `Locataire` via `POST /api/locataires`**, bail créé avec
`locataireId`), invitation → acceptation (Admin API réelle) → JWT gestionnaire,
affectation/échéances/pointage/honoraires, alertes (PREAVIS) et audit, scoping gestionnaire,
isolation cross-tenant live (2e bailleur), **RGPD retargeté** (export, `DELETE
/api/locataires/{id}/effacement` — 403 gestionnaire, 204 bailleur, `locataireNom` anonymisé et
`locataireEmail` effacé confirmés via l'export post-effacement, `EFFACEMENT_LOCATAIRE` tracé dans
l'audit), garde-fous AuthN/ports, surface publique de vérification des quittances sans oracle.
Échafaudage `directAccessGrants` révoqué automatiquement par le script (vérifié `false` après).

Ce smoke exerce donc de bout en bout, en conditions réelles de Production, le nouveau contrat
`locataireId` (US-113) et l'effacement RGPD retargeté sur `Locataire` (US-114) — les deux
changements de comportement introduits par la migration V26.

## Nettoyage transactionnel

RUN_ID `1784458624`. Toutes les entités créées par le run ont été identifiées par leur identifiant
réel avant toute suppression, puis supprimées **en une seule transaction** :

| Entité | Quantité | Détail |
|---|---:|---|
| Paiement | 10 | `bailleur_id = c7296c69-…` |
| Honoraire | 10 | idem |
| Alerte | 6 | idem |
| Audit log | 4 | idem (pointage, validation honoraire, création locataire, effacement) |
| Invitation | 1 | `gest-smoke-1784458624@test.local` |
| Affectation | 1 | patrimoine smoke ↔ gestionnaire smoke |
| Bail | 1 | créé par le run |
| Bien | 1 | créé par le run |
| Locataire | 1 | « Locataire Smoke » (créé via `POST /api/locataires`, supprimé — pas une simple anonymisation, données 100 % synthétiques) |
| Patrimoine (bailleur-test) | 1 | patrimoine smoke |
| Gestionnaire | 1 | `gest-smoke-1784458624@test.local` |
| Patrimoine (bailleur2, auto-créé à l'inscription) | 1 | `bailleur_id = cf7154d5-…` |
| Bailleur (2e bailleur smoke) | 1 | `bailleur2-smoke-1784458624@test.local` (`cf7154d5-…`) |

Comptes Keycloak du run supprimés : `gest-smoke-1784458624@test.local`
(`cf719405-d1d8-4d1a-82fc-39ec8833a76e`), `bailleur2-smoke-1784458624@test.local`
(`23df6717-aeb8-445c-855f-ceb4ddab4594`). `bailleur-test` redésactivé (`enabled=false`),
`directAccessGrantsEnabled=false` confirmé sur `loyertracker-spa`.

## État final

| Contrôle | Résultat |
|---|---|
| Résidus du RUN_ID en base | 0 bailleur, 0 gestionnaire, 0 patrimoine, 0 locataire surnuméraires |
| Baseline métier post-nettoyage | 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties, 1 gestionnaire, **8 locataires** (backfill V26 — nouvelle baseline attendue, un par bail historique), 6 quittances |
| Services | 8/8 actifs, 4/4 healthy, `RestartCount=0` |
| Erreurs API | 0 ligne 5xx |
| Production publique | `https://loyertracker.loyerpro.org` → 200 |

**Validation finale PASS — `PRODUCTION_DEPLOYED` atteint le 2026-07-19 (release `1.12.0`,
`sha-359f4d63`, Sprint C EP-15).** Migration V26 non additive confirmée stable en conditions
réelles (backfill 8/8 vérifié au déploiement technique, contrat `locataireId` et RGPD retargeté
désormais exercés de bout en bout par le smoke). Hypercare (T0/T+12/T+24) et clôture de release
restent des étapes distinctes.
