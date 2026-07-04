# Gate Staging — Sprint 10 (Garantie usage métier, EP-12b, US-95/96/97)

| Champ | Valeur |
|---|---|
| Date | 2026-07-04 |
| Type | Sprint unique — première promotion Staging de Sprint 10 |
| Périmètre | Sprint 10 EP-12b : US-95 (retenue sur impayé), US-96 (complément), US-97 (historique + export CSV), migration V21 |
| Tag candidat | `sha-1d1c2a5d` (merge commit PR #168) |
| Digest API (image locale) | `sha256:6d7ce46f6c0df5a2d0239bf4303977b2cb1d5afe179424f4fb92ea935b85a82d` |
| Digest Web (image locale) | `sha256:41600e4bb003dfdffdce02fe075ba7d65835a4c97a543d4a6de902f4cb0aa8d1` |
| Staging précédent | `sha-6a358eb6` (Sprint 9, déployé 2026-07-03 — devenu Production `1.7.0`, release clôturée 2026-07-04) |
| Environnement | `ai-test-server` — Staging mutualisé (accès via IP privée `172.31.11.102`) |
| Décision | **GO — `STAGING_DEPLOYED` atteint (2026-07-04, ~10:59 UTC)** |
| Plans | `plan-execution-evolutions-ep10-ep13.md` (§Sprint 10) |
| PR | #168 (code, merge commit `1d1c2a5d` ; inclut correctif Quality Gate `b0b797f`), #169/#170 (docs), #171 (correctif smoke Flyway 20→21) |

## 1. Périmètre

**PR #168 — Sprint 10 EP-12b US-95/96/97** (merge commit `1d1c2a5d`)
- Migration **V21** : `paiement.garantie_movement_id` (FK nullable vers `garantie_movement` +
  index) — lien entre un mouvement `RETENUE_LOYER` et le paiement qu'il couvre (ADR-14 §5).
- **US-95** : `POST .../garanties/{id}/retenue-loyer` — retenue explicite, jamais automatique ;
  transition du paiement vers `RECU`/`PARTIEL`, recalcul honoraires, gardes 400 (solde/reste dû)
  et 409 (paiement déjà couvert).
- **US-96** : `POST .../garanties/{id}/complement` — motif obligatoire, audit
  `COMPLEMENT_GARANTIE`.
- **US-97** : `GET .../mouvements` + `GET .../mouvements/export` (CSV échappé anti
  formula-injection) ; UI triable/filtrable ; export RGPD incluant le ledger.
- Correctif : `Garantie.restituerPartiel` calcule depuis `soldeActuel` (et non le montant
  initial) — critique dès qu'une retenue/complément existe.

## 2. Conditions préalables — état avant déploiement

| Critère | Statut | Preuve |
|---|---|---|
| CI verte sur le candidat | ✅ | PR #168 : 7/7 checks SUCCESS (dont Quality Gates SonarQube backend/frontend, corrigés par `b0b797f` et pré-validés sur l'instance réelle) |
| Release courante clôturée | ✅ | `1.7.0` clôturée CDO GO 2026-07-04 (`cloture-release-v1.7.0.md`) |
| Correctif smoke V21 mergé | ✅ | PR #171 (défaut récurrent R-S04-1/PR #158 anticipé cette fois **avant** le déploiement) |
| Image GHCR du candidat disponible | ✅ | `sha-1d1c2a5d` API+Web présents sur `ghcr.io/jptshilombo` |
| `STG-ISOL-01` avant | ✅ | §4 |

## 3. Déploiement exécuté (2026-07-04)

1. **Snapshot `STG-ISOL-01` avant** (10:56 UTC) : 8 conteneurs `loyertracker-staging-*` +
   `nginx-proxy-manager` (mutualisé), réseaux/volumes namespacés — §4.
2. **Synchronisation du dépôt hôte** : `bcb3c0d` → `057aae1` (`git pull --ff-only`), même
   leçon récurrente que le déploiement Production `1.7.0`.
3. **Sauvegarde pré-déploiement** : `~/staging-backups/loyertracker-staging-20260704-105627.dump`
   (365 Kio, `pg_restore --list` 740 entrées OK).
4. **Bascule de tag** : `.env` `sha-6a358eb6` → `sha-1d1c2a5d` (backup `.env.bak-pre-sprint10`,
   600).
5. **Incident de déploiement, corrigé immédiatement (~1 min)** : première recréation lancée avec
   `COMPOSE_FILE=docker-compose.yml:docker-compose.staging.yml` (pattern Production) — la fusion
   des listes `ports` a fait tenter au nginx projet un bind sur `0.0.0.0:80`, refusé car le port
   appartient à `nginx-proxy-manager`. **Aucun impact sur les autres projets** (le bind a échoué,
   NPM intact, restart=0). Correction : invocation canonique Staging
   `docker compose -f docker-compose.staging.yml` seul (fichier autonome, `name:
   loyertracker-staging`), vérifiée sur les labels Compose des conteneurs intacts. Recréation
   ciblée `api`+`nginx` OK — nginx publié uniquement sur 18080/18443.
6. **Flyway V21 appliquée** : 21/21 `success`, colonne `paiement.garantie_movement_id` présente.
   Actuator `{"status":"UP"}`, 4/4 healthy, restart=0.
7. **Snapshot `STG-ISOL-01` après** (10:59 UTC) : identique à l'avant — §4.

## 4. Contrôle `STG-ISOL-01` — PASS avant/après

- Avant (10:56 UTC) comme après (10:59 UTC) : 8 conteneurs `loyertracker-staging-*` +
  `nginx-proxy-manager` (`running`, restart=0), aucun autre projet actif touché.
- Réseaux : `loyertracker-staging_loyertracker-net` (projet) + `ubuntu_default` (NPM) —
  inchangés. Volumes projet : `loyertracker-staging_postgres-data`,
  `loyertracker-staging_prometheus-data` — inchangés ; volumes tiers (`infra_*`, `tools_*`)
  intacts.
- Aucune commande Docker globale ; recréations ciblées `api`/`nginx` uniquement.
- L'incident §3.5 est un **échec de bind refusé par le noyau**, pas une perturbation de NPM
  (vérifié : `running`, restart=0, ports 80-81/443 conservés).

## 5. Smoke Staging — 59 PASS / 0 FAIL

`BASE=https://localhost:18443 COMPOSE_FILE=docker-compose.staging.yml
./infra/smoke/smoke-stack.sh` (invocation documentée dans l'en-tête du script) : sanity Flyway
**21/21** (compteur corrigé PR #171), pool `loyertracker_api` NOSUPERUSER/NOBYPASSRLS, JWT réels,
parcours S01→S04, RGPD, isolation cross-tenant, garde-fous AuthN/ports — **59/59**. Échafaudage
`directAccessGrants` activé puis révoqué par le script.

## 6. Vérification fonctionnelle manuelle US-95/96/97 (API réelle)

Le smoke n'exerçant aucun endpoint garantie (gap identique au Sprint 9), scénario dédié exécuté
sur données synthétiques (`bailleur-test@test.local`, échafaudage kcadm activé puis révoqué,
nettoyage transactionnel vérifié 0 résidu) : **20 contrôles PASS** —

- Dépôt garantie 1000 → US-95 : retenue 1500 → **400** ; retenue 900 sur paiement impayé →
  **200**, solde 100, paiement → **RECU**, `paiement.garantie_movement_id` renseigné (V21) ;
  re-couverture du même paiement → **409**.
- US-96 : complément 400 → **200**, solde 500.
- US-97 : `GET mouvements` → 3 mouvements ; export CSV → en-tête + 3 lignes, colonnes conformes.
- Invariant `solde_actuel = Σ (credit−debit)` : PASS sur la garantie de test **et 4/4 sur toutes
  les garanties de la base** après nettoyage.

**1 observation (défaut mineur, non bloquant) → réserve RSV-S10-01** : l'ordre de restitution
des mouvements d'un même jour n'est pas chronologique. `garantie_movement.date_mouvement` est un
`DATE` et le tri `ORDER BY date_mouvement ASC, id ASC` retombe sur l'UUID aléatoire pour
départager — constaté en réel : `RETENUE_LOYER, DEPOT_INITIAL, COMPLEMENT` pour trois mouvements
du 2026-07-04. Sans impact sur les soldes ni l'invariant (chaque mouvement porte son
`solde_apres`), mais l'historique US-97 peut s'afficher dans le désordre à l'intérieur d'une
journée. **Action** : ajouter un critère de tri stable et chronologique (ex. timestamp de
création ou séquence) — assignée backend, avant le Gate Production Sprint 10.

## 7. Réserves

| ID | Nature | Statut |
|----|--------|--------|
| **RSV-S10-01** | Ordre intra-jour du ledger non déterministe (`date_mouvement` DATE + tie-break UUID) | **OUVERTE** — non bloquante pour Staging ; correction attendue avant le Gate Production Sprint 10 (2026-07-04, assignée backend) |
| RSV-S9-03 | Rollback applicatif seul non viable (héritée, V20) | Acceptée permanente — V21 est additive (FK nullable), le rollback V21 seul resterait possible mais sans objet tant que RSV-S9-03 s'applique |

## 8. Décision

**GO — `STAGING_DEPLOYED` atteint le 2026-07-04 (~10:59 UTC).**

- `STG-ISOL-01` PASS avant/après ; sauvegarde pré-déploiement vérifiée ; Flyway 21/21 ;
  smoke 59/0 ; US-95/96/97 vérifiées en direct sur l'API réelle avec chemins d'erreur ;
  invariant ledger 4/4 ; nettoyage synthétique 0 résidu.
- **Ce Gate ne constitue pas une autorisation Production.** Le Gate Production Sprint 10 est une
  décision distincte, conditionnée notamment au traitement de RSV-S10-01.
