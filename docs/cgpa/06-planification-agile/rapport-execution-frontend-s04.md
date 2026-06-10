# Rapport d'exécution — Frontend S04 (consoles Honoraires / Alertes / Audit)

- **Sprint :** S04 (Phase 07 — Réalisation)
- **Branche :** `feat/s04-frontend-honoraires-alertes-audit` (depuis `main` @ `ac42d25`)
- **Niveau du Plan d'Exécution :** 3 (standard)
- **Agent :** Claude Opus 4.8 (1M context)
- **Date :** 2026-06-10
- **Statut :** ✅ Codé, vérifié (lint + build + tests verts), prêt pour revue/merge (décision PO)

## 1. Objet

Exposer à l'IHM Angular les fonctions backend S04 déjà livrées (PR #11/#12/#14) : honoraires de
gestion (US-40), alertes de pilotage (US-50/51/52), journal d'audit (US-62). **Aucune modification
backend** (ni migration, ni endpoint). Patrons frontend S03 réutilisés : composants standalone,
signals, service API typé mono-origine `/api`. Plan approuvé avec les défauts A–D.

## 2. Livrables (diff)

| Fichier | Nature | Détail |
|---|---|---|
| `core/s04/s04-api.service.ts` (+ `.spec.ts`) | NEW | Types (`Honoraire`, `Alerte`, `AuditEntry`, statuts/types, DTOs batch) et méthodes : `listerHonoraires`, `changerStatutHonoraire`, `recalculerHonoraires`, `listerAlertes`, `marquerAlerteLue`, `genererAlertes`, `listerAudit`. |
| `honoraires/honoraires-bien.component.ts` (+ `.spec.ts`) | NEW | `input bienId` + `input peutValider`. Liste par bien ; **défaut B** : deux boutons d'action « Mettre en attente » (→EN_ATTENTE) / « Marquer payé » (→PAYE), masqués dès `PAYE`. Bouton « Recalculer » gardé par `peutValider`. |
| `alertes/alertes-liste.component.ts` (+ `.spec.ts`) | NEW | `input peutGenerer`. Liste de l'acteur ; **défaut C** : NON_LUE en tête (tri client), bouton « Marquer lue » par ligne, « Générer les alertes » gardé par `peutGenerer`. |
| `audit/audit-journal.component.ts` (+ `.spec.ts`) | NEW | **défaut D** : liste brute la plus récente d'abord (ordre backend), sans filtre/pagination. Composant bailleur uniquement. |
| `bailleur/dashboard/dashboard.component.ts` | MOD | Honoraires sous le bien sélectionné (`peutValider=true`) + sections globales alertes (`peutGenerer=true`) et audit. |
| `gestionnaire/dashboard/dashboard.component.ts` | MOD | Honoraires sous le bien (lecture seule `peutValider=false`) + alertes (`peutGenerer=false`). **Pas d'audit** (403). |

## 3. Conformité au contrat de rôles backend

- `PATCH /api/honoraires/{id}/statut`, `POST /api/batch/honoraires`, `POST /api/batch/alertes`,
  `GET /api/audit` sont **BAILLEUR-only** : les contrôles correspondants sont masqués côté
  gestionnaire (`peutValider=false`, `peutGenerer=false`, audit non monté). Le backend reste
  l'autorité : un 403/404 éventuel est rendu via `signalerErreur` (miroir S03).
- `GET /api/biens/{bienId}/honoraires` et `GET|PATCH /api/alertes` sont ouverts aux deux rôles, le
  cloisonnement (ReBAC + RLS) restant serveur.

## 4. Tests & vérification

- `npm run lint` → **All files pass linting**.
- `npm run build` → **bundle généré** (warning `js-sha256`/keycloak-js préexistant, hors périmètre).
- `npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox` → **27 SUCCESS** (18 baseline + 6
  service S04 + 3 créations de composants S04), 0 échec.
- Backend non touché → aucune régression (lots S04 backend restent à 54 tests).

## 5. Conformité CGPA

- Plan d'Exécution approuvé (défauts A–D) avant codage (gate Phase 07).
- Aucun secret, aucune PII affichée (audit limité à id/rôle/action/type d'entité/horodatage, ENF-03).
- Décision de merge **réservée au PO**.

## 6. Hors périmètre / suite

- Hors périmètre (assumé) : pagination/recherche audit, dashboards KPIs, notifications, i18n.
- Suite : synchro `docs/project-state.md` post-merge (PR de doc séparée). Suivis techniques
  (double datasource / smoke test runtime) restent des lots distincts à cadrer.
