# Rapport Validation Finale — Release `1.11.0` (exécuté a posteriori)

| Champ | Valeur |
|---|---|
| Date d'exécution | 2026-07-19 |
| Release | `1.11.0` — `sha-cba13f52` (déployée en Production depuis le 2026-07-16, cf. `deploiement-technique-v1.11.0-report.md`) |
| Smoke | **62 PASS / 0 FAIL au premier passage** |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` confirmé rétroactivement** |

## Contexte

Ce smoke de validation finale n'avait jamais été exécuté pour `1.11.0` : le Préflight du
2026-07-16 s'était arrêté à `PRODUCTION_DEPLOYED` non atteint, puis le déploiement technique a été
exécuté sans que ce chaînon soit tracé, et le Sprint C EP-15 a démarré le lendemain sur cette base
(cf. réserve de gouvernance dans `deploiement-technique-v1.11.0-report.md`). Ce rapport comble le
trou en rejouant le smoke réel contre la Production, 3 jours après le déploiement effectif.

## Autorisation

Réactivation temporaire de `bailleur-test@test.local` et de `directAccessGrants` sur
`loyertracker-spa` : **autorisation PO explicite donnée le 2026-07-19** dans le cadre de la
régularisation du trou de gouvernance `1.11.0` — même discipline que `1.2.1`→`1.10.0`.

## Smoke Production

Invocation :

```
env BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml bash infra/smoke/smoke-stack.sh
```

`bailleur-test@test.local` (`43443d1e-…` KC / `c7296c69-…` DB) réactivé temporairement
(`enabled=true`), redésactivé après le run.

**Résultat : 62 PASS / 0 FAIL au premier passage.** Couverture : sanity Flyway 25/25, pool
`loyertracker_api` NOSUPERUSER/NOBYPASSRLS, JWT réels via Nginx, parcours bailleur (inscription,
patrimoine, bien, bail), invitation → acceptation (Admin API réelle) → JWT gestionnaire,
affectation/échéances/pointage/honoraires, alertes (PREAVIS) et audit, scoping gestionnaire,
isolation cross-tenant live (2e bailleur), RGPD (export, effacement locataire, anonymisation),
garde-fous AuthN/ports, surface publique de vérification des quittances sans oracle.
Échafaudage `directAccessGrants` révoqué automatiquement par le script (vérifié `false` après).

Comme aux validations finales précédentes, ce smoke ne couvre pas `/api/biens/{bienId}/baux/{bailId}/cloture`
et `.../reouverture` (US-115→118, EP-13) : ces endpoints ont été validés manuellement en conditions
réelles lors du Gate Staging EP-13 (22 PASS/0 FAIL sur le candidat identique `sha-cba13f52`,
`gate-staging-ep13-fin-de-bail-decision.md`) ; aucune vérification manuelle supplémentaire n'est
jugée nécessaire en Production, le comportement, le RBAC/ReBAC et la RLS étant indépendants de
l'environnement et déjà prouvés.

## Nettoyage transactionnel

RUN_ID `1784457409`. Entités créées par le run identifiées par leur identifiant réel puis
supprimées **en une seule transaction** :

| Entité | Quantité | Détail |
|---|---:|---|
| Paiement | 10 | `bailleur_id = c7296c69-…` (bailleur-test) |
| Honoraire | 10 | idem |
| Alerte | 6 | idem |
| Audit log | 3 | idem |
| Invitation | 1 | `gest-smoke-1784457409@test.local` |
| Affectation | 1 | patrimoine smoke ↔ gestionnaire smoke |
| Bail | 1 | créé par le run |
| Bien | 1 | créé par le run |
| Patrimoine (bailleur-test) | 1 | patrimoine smoke |
| Gestionnaire | 1 | `gest-smoke-1784457409@test.local` |
| Patrimoine (bailleur2, auto-créé à l'inscription) | 1 | `bailleur_id = e359d67b-…` |
| Bailleur (2e bailleur smoke) | 1 | `bailleur2-smoke-1784457409@test.local` (`e359d67b-…`) |

Comptes Keycloak du run supprimés : `gest-smoke-1784457409@test.local`
(`f429fcc6-4b94-48db-8bb4-76915da5bbb9`), `bailleur2-smoke-1784457409@test.local`
(`0e4f8a8b-ca24-4f86-bbbf-7d0f649307ea`). `bailleur-test` redésactivé (`enabled=false`),
`directAccessGrantsEnabled=false` confirmé sur `loyertracker-spa`.

## État final

| Contrôle | Résultat |
|---|---|
| Résidus du RUN_ID en base | 0 bailleur, 0 gestionnaire, 0 patrimoine surnuméraire |
| Baseline métier post-nettoyage | 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties, 1 gestionnaire, 0 locataire, 6 quittances — identique au Préflight du 2026-07-16 |
| Services | 8/8 actifs, 4/4 healthy, `RestartCount=0` |
| Erreurs API | 0 ligne 5xx |
| Production publique | `https://loyertracker.loyerpro.org` → 200 |

**Validation finale PASS — `PRODUCTION_DEPLOYED` confirmé rétroactivement pour la release
`1.11.0` (`sha-cba13f52`), déployée le 2026-07-16, validée le 2026-07-19.** Hypercare (T0/T+12/T+24)
n'a pas pu être rejouée rétroactivement : l'hôte reste intentionnellement éteint entre les
sessions (produit non annoncé publiquement), donc aucune fenêtre continue T0→T+24 n'a été
observée après le déploiement du 2026-07-16. En compensation, ce contrôle du 2026-07-19 (J+3)
confirme 3 jours de fonctionnement sans incident rapporté : 0 alerte active, 0 5xx, baseline de
données inchangée, `RestartCount=0` sur les 4 services critiques. Accepté comme preuve de
stabilité substitutive — cf. réserve dédiée dans `project-state.md` §13.
