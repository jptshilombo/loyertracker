# Release Notes — LoyerTracker `1.8.0`

| Champ | Valeur |
|---|---|
| Version | `1.8.0` |
| Date de release | 2026-07-04 (Gate Production GO, `PRODUCTION_READY` — déploiement technique à venir) |
| Type | Release MINOR — Garantie : usage métier du ledger (Sprint 10, EP-12b) |
| Tag Production candidat | `sha-2c5f43c7` |
| Release précédente | `1.7.0` — `sha-6a358eb6` (2026-07-03) |
| Flyway | V20→**V21** (1 nouvelle migration, additive) |

---

## Nouveautés

### Retenue explicite sur un loyer impayé (US-95)

Nouvel endpoint `POST /api/biens/{bienId}/baux/{bailId}/garanties/{id}/retenue-loyer` — jamais
un prélèvement automatique (ADR-14 §5) : le gestionnaire ou le bailleur choisit le paiement et
le montant. Le paiement couvert transitionne vers `RECU`/`PARTIEL` (mêmes seuils qu'un pointage
manuel), les honoraires sont recalculés, et le mouvement `RETENUE_LOYER` est lié au paiement via
`paiement.garantie_movement_id` (migration V21). Gardes : 400 si le montant excède le solde de
la garantie ou le reste dû, 409 si le paiement est déjà couvert.

### Réapprovisionnement d'une garantie active (US-96)

Endpoint `POST .../garanties/{id}/complement` avec motif obligatoire, journalisé `COMPLEMENT`
au ledger et `COMPLEMENT_GARANTIE` à l'audit.

### Historique des mouvements de garantie (US-97)

- `GET .../garanties/{id}/mouvements` : ledger chronologique (chaque ligne porte débit, crédit
  et `soldeApres`).
- `GET .../mouvements/export` : export CSV, tous les champs échappés contre la formula
  injection.
- UI : panneau Garanties triable et filtrable par type de mouvement.
- L'export RGPD du bailleur inclut désormais le ledger complet (chargement batch, sans N+1).

### Correctifs

- **Ordre chronologique stable du ledger intra-jour (RSV-S10-01)** : `date_mouvement` est un
  `DATE` et le tri retombait sur l'UUID aléatoire pour les mouvements d'un même jour. Tri
  désormais `date_mouvement, cree_le, id` (`cree_le` TIMESTAMPTZ posé par Postgres depuis V20),
  appliqué à l'historique US-97 et à l'export RGPD. Vérifié en Staging avec preuve
  discriminante (l'ancien tri aurait rendu `RETENUE_LOYER, DEPOT_INITIAL, COMPLEMENT` sur les
  données de test réelles).
- `Garantie.restituerPartiel` calculait le solde depuis le montant initial au lieu du
  `soldeActuel` courant — sans effet avant ce sprint, critique dès qu'une retenue ou un
  complément existe.
- ADR-14 §8 exécuté : `sommeMontantDeposeParBail` (valeur `depotGarantie` du bail) recalculé
  depuis le ledger `garantie_movement`.

---

## Périmètre technique

| Aspect | Détail |
|---|---|
| Nouvelle migration | **V21** — `paiement.garantie_movement_id` (colonne nullable + FK vers `garantie_movement` + index). **Additive** : aucun backfill, aucune suppression, aucune ligne existante modifiée |
| Flyway après upgrade | 21/21 |
| API ajoutée | `retenue-loyer`, `complement`, `mouvements`, `mouvements/export` (additif) |
| Rétrocompatibilité | Totalement additive — aucun endpoint modifié ou supprimé |
| Rollback schéma | **Rollback applicatif seul viable** (voir ci-dessous) |

---

## Compatibilité et rollback

- **Rollback applicatif seul** (retour à `sha-6a358eb6`, `1.7.0`) : **viable** — l'ancien code
  ne mappe pas `paiement.garantie_movement_id`, la colonne resterait en place, ignorée.
  Contraste explicite avec `1.7.0`/V20 (RSV-S9-03 : colonne supprimée, restauration de backup
  obligatoire).
- **Limite documentée** : un rollback après usage réel des nouveaux endpoints laisserait en base
  des mouvements `RETENUE_LOYER`/`COMPLEMENT` créés entre-temps — soldes et invariant restent
  cohérents (le code `1.7.0` lit déjà ledger + `solde_actuel`), mais les transitions de paiement
  opérées par une retenue ne seraient plus « explicables » dans l'UI `1.7.0` (pas d'écran
  historique).
- **Rollback complet (schéma inclus)** : restauration du backup pré-déploiement
  (`loyertracker-20260704-170836.dump`, vérifié au Préflight).

---

## Observation portée depuis le Gate Production

**OBS-S10-01** (cosmétique, non bloquante) : les lignes backfillées par V20 dans une même
transaction partagent le même `cree_le` — le tri retombe sur l'UUID pour ces lignes historiques
(ordre stable, chaque ligne porte son `soldeApres`). **Aucun cas en Production** (vérifié au
Préflight : aucune garantie n'y a deux mouvements le même jour).

---

## Déploiement Production

| Étape | Statut |
|---|---|
| Gate Staging Sprint 10 | ✅ GO — `sha-1d1c2a5d`, STG-ISOL-01 PASS, Flyway 21/21, smoke 59/0 (2026-07-04) |
| Correctif RSV-S10-01 + S1 (redéploiement Staging du candidat) | ✅ `sha-2c5f43c7`, smoke 59/0, ordre vérifié live (2026-07-04) |
| Gate Production `1.8.0` | ✅ **GO** — `PRODUCTION_READY` atteint (2026-07-04) |
| Préflight + backup | ✅ PASS (2026-07-04) — `preflight-backup-v1.8.0-report.md` |
| Déploiement technique | À faire (décision distincte) |
| Smoke Production | À faire |
| `PRODUCTION_DEPLOYED` | À faire |
| Hôte | `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |
