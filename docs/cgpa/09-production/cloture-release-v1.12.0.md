# Clôture Release `1.12.0`

| Champ | Valeur |
|---|---|
| Date de clôture | 2026-07-22 |
| Heure CDO GO | ~18:06 UTC (après checkpoint combiné T+12/T+24 en rattrapage, PASS sous surveillance) |
| Release | `1.12.0` — Sprint C EP-15 (Bascule Bail → Locataire, US-113/114) |
| Tag Production | `sha-359f4d63` |
| `PRODUCTION_DEPLOYED` | 2026-07-19 ~10:51 UTC (horodatage corrigé, cf. `plan-etape-hypercare-v1.12.0.md`) |
| Statut | **RELEASE CLÔTURÉE — CDO GO sous surveillance** |

## 1. Récapitulatif du cycle `1.12.0`

| Étape | Date | Résultat | Référence |
|---|---|---|---|
| Sprint C EP-15 codé (bascule `Bail → Locataire`, US-113/114) | 2026-07-19 | Migration V26 non additive | `a483fe9` |
| Vérification manuelle navigateur du PO | 2026-07-19 | Confirmée | `docs/project-state.md` §11 |
| Gate Staging Sprint C EP-15 | 2026-07-19 | GO — `STAGING_DEPLOYED` (`sha-359f4d63`) | `gate-staging-sprint-c-ep15-decision.md` |
| Gate Production Sprint C EP-15 | 2026-07-19 | GO — `PRODUCTION_READY` | `gate-production-sprint-c-ep15-decision.md` |
| Préflight renforcé + backup pré-migration | 2026-07-19 | PASS | `preflight-backup-v1.12.0-report.md` |
| Déploiement technique | 2026-07-19 ~10:51 UTC | PASS — V26 appliquée 26/26, `api`+`nginx` seuls recréés | `deploiement-technique-v1.12.0-report.md` |
| Backfill V26 + second backup post-migration | 2026-07-19 | 8/8 baux avec `locataire_id`, 0 orphelin ; dump `loyertracker-20260719-115220.dump` vérifié (799 entrées) | `validation-finale-v1.12.0-report.md` |
| Validation finale (smoke Production) | 2026-07-19 ~11:57 UTC | **63 PASS / 0 FAIL au premier passage** ; `PRODUCTION_DEPLOYED` | idem |
| Hypercare T0 | 2026-07-19 ~11:27 UTC | PASS | `plan-etape-hypercare-v1.12.0.md` |
| Hypercare T+12/T+24 (rattrapage combiné) | 2026-07-22 ~18:06 UTC | **PASS sous surveillance** — hôte volontairement éteint entre les opérations, aucune télémétrie continue sur la fenêtre, contrôle live sans écart | idem |

## 2. Périmètre livré

**EP-15 — Gestion des personnes, Sprint C (bascule `Bail → Locataire`, ADR-16 D2/D3, US-113/114)**

- Migration **V26 non additive** : `bail.locataire_id NOT NULL` (backfill 8/8, 0 orphelin),
  suppression des colonnes texte libre `bail.locataire_nom`/`locataire_email`.
- Contrat `POST/PUT .../baux` bascule sur `locataireId` ; nouvel endpoint
  `GET /api/biens/{bienId}/locataires` (garde `@authz.peutAccederBien`) ; endpoint d'effacement
  `DELETE /api/locataires/{id}/effacement`.
- Rollback applicatif seul **non viable** après application de V26 (RSV-EP15-03) : restauration du
  backup post-migration `loyertracker-20260719-115220.dump` requise en cas de retour arrière.

## 3. Réserves

| ID | Nature | Statut à la clôture |
|----|--------|---------------------|
| RSV-EP15-01/02/04 | Statut Gestionnaire global, migration sans déduplication, photo `bytea` | ✅ Tranchées par le PO au cadrage, hors périmètre à la clôture |
| RSV-EP15-03 | Migration V26 non additive — rollback applicatif seul non viable après application | ✅ **Mitigée** : second backup post-migration produit et vérifié avant `PRODUCTION_DEPLOYED` ; reste le point de restauration de référence en cas d'incident futur |
| Écarts de fenêtre hypercare (T+12/T+24, ~3 jours sans télémétrie continue) | Hôte volontairement éteint entre les opérations (produit non annoncé publiquement, aucun trafic réel — pattern `1.7.0`→`1.9.0`, à la différence de `1.10.0` où l'hôte était resté allumé) | ✅ **QUALIFIÉ sans impact** — contrôle live en rattrapage sans écart (tag/digests inchangés, Flyway 26/26, backfill V26 stable, 0 5xx, 0 `ERROR`, observabilité 5/5 sans alerte) ; qualification actée sur instruction PO explicite du 2026-07-22, à l'identique du précédent `1.9.0` |
| `RSV-STG-01` (héritée) | Confirmation live `STG-ISOL-01` au prochain déploiement Staging mutualisé | ⚠️ **MAINTENUE** — sans rapport avec `1.12.0` |

## 4. État de Production au moment de la clôture

| Contrôle | Valeur |
|---|---|
| Tag Production | `sha-359f4d63` |
| Digest API | `sha256:ea040492bb5ad6b6a72b84665e22cd47a66d79c293b874fca481d5a276afe1c8` |
| Digest Web (nginx) | `sha256:e70ebc7ba7d71406edaec6f890c2f57f06ae9d7c855680e0fba01914b4251968` |
| Flyway | V1→**V26** (26/26) |
| Services | 8/8 Up, 4/4 `(healthy)`, `RestartCount=0` depuis le redémarrage hôte du 2026-07-22 ~16:58 UTC |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | 0 alerte active |
| Pool Hikari | `hikaricp_connections_pending` = 0 |
| 5xx (post-redémarrage) | 0 |
| Backfill V26 | 8/8 baux avec `locataire_id`, 0 orphelin ; 8 `locataire` |
| Données métier | 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties, 1 gestionnaire, 8 locataires, 6 quittances |
| `bailleur-test` | `enabled=false` ; `directAccessGrantsEnabled=false` |
| Site public | `https://loyertracker.loyerpro.org` → 200 |

## 5. Décision CDO

**Chief Delivery Officer : GO — Release `1.12.0` CLÔTURÉE le 2026-07-22 (sous surveillance).**

- Hypercare complète : T0 PASS, T+12/T+24 PASS sous surveillance (aucun critère de suspension
  observé au contrôle en rattrapage ; écart de fenêtre qualifié sans impact, hôte volontairement
  éteint, absence de trafic réel par construction).
- Bascule `Bail → Locataire` (US-113/114) confirmée stable : tag/digests inchangés depuis le
  déploiement, Flyway 26/26, backfill V26 constant (8/8, 0 orphelin), 0 5xx, 0 `ERROR`.
- RSV-EP15-01/02/04 non bloquantes (tranchées au cadrage). RSV-EP15-03 mitigée (second backup
  vérifié). `RSV-STG-01` (héritée) maintenue, sans rapport avec `1.12.0`.
- Cette clôture satisfait le prérequis « `1.12.0` clôturée » posé par le **Gate Production EP-16
  Sprint N** (`gate-production-sprint-n-ep16-decision.md`, GO sous réserve du 2026-07-19) comme
  condition bloquante avant tout Préflight du candidat `1.13.0`.
- Prochaine action autorisée : instruction explicite distincte pour le **Préflight Production
  `1.13.0`** (EP-16 Sprint N — fondation Outbox transactionnelle). Aucun déploiement ni mutation
  Production n'est autorisé par cette clôture elle-même.
