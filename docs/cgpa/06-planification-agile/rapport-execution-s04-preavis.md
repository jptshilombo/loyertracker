# Rapport d'exécution — S04 / Alerte PREAVIS (US-50, EF-62)

- **Sprint :** S04 (Phase 07 — Réalisation)
- **Branche :** `feat/s04-alerte-preavis` (depuis `main` @ `f1d45ed`)
- **Niveau du Plan d'Exécution :** 1 (mini — ajout ciblé d'un type d'alerte)
- **Agent :** Claude Opus 4.8 (1M context)
- **Date :** 2026-06-10
- **Statut :** ✅ Codé, testé (BUILD SUCCESS), prêt pour revue/merge (décision PO)

## 1. Objet

Compléter le mécanisme d'alertes de pilotage (V9) avec le **4ᵉ type `PREAVIS`** (US-50, EF-62),
reporté lors du Lot 2 faute de critère d'acceptation figé. Le critère a été arbitré par le PO :

> **EF-62 figé :** une échéance de préavis est « atteinte » lorsque le terme du bail entre dans la
> **bande de préavis** — soit **avant le terme**, à **≤ 90 jours** (3 mois, arbitrage PO).

## 2. Critère d'acceptation & exclusivité avec FIN_BAIL

- `FIN_BAIL` (EF-61, V9) couvre les baux ACTIFS dont le terme est à **≤ 60 jours**.
- `PREAVIS` (EF-62) est **borné** à la bande **`]J+60 ; J+90]`** : `current_date + 60 < date_fin <= current_date + p_preavis_jours`.
- Les deux types sont donc **mutuellement exclusifs** : un même bail ne génère jamais simultanément
  un `FIN_BAIL` et un `PREAVIS` (pas de double signalement). Au-delà de J+90, aucune alerte de terme.

## 3. Livrables (diff)

| Fichier | Nature | Détail |
|---|---|---|
| `db/migration/V10__s04_alerte_preavis.sql` | NEW | `DROP FUNCTION generer_alertes()` puis recréation `generer_alertes(p_preavis_jours integer DEFAULT 90)` : 3 branches V9 reconduites à l'identique + 4ᵉ branche `PREAVIS`. SECURITY DEFINER, owner `loyertracker_batch`, GRANT EXECUTE `loyertracker_api`. Anti-doublon `ON CONFLICT (type, bien_id, periode) WHERE statut='NON_LUE' DO NOTHING`, `periode = to_char(date_fin,'YYYY-MM')`. |
| `resources/application.yml` | MOD | `app.alertes.preavis.jours` (défaut 90, surcharge env `APP_ALERTES_PREAVIS_JOURS`). |
| `alertes/AlerteService.java` | MOD | Champ `preavisJours` injecté via `@Value`; `genererBatch()` appelle `SELECT generer_alertes(CAST(:j AS integer))`. |
| `test/.../db/SchemaMigrationTest.java` | MOD | Bump `migrationsExecuted` → **10**. |
| `test/.../s04/S04AlertesAuditIntegrationTest.java` | MOD | + test `preavisGenereDansLaBandeExclusifDeFinBailEtAntiDoublon`. |

- **Aucun** changement d'enum/entité/DTO/endpoint : `TypeAlerte.PREAVIS` préexistait (V9). L'appel
  sans argument `generer_alertes()` reste résolu via le `DEFAULT` (rétro-compatibilité du scheduler).

## 4. Tests & preuve

- `mvn verify` → **BUILD SUCCESS**, **54 tests** (53 → +1), 0 échec, 0 erreur.
- Flyway : **10 migrations** appliquées, schéma à v10, `validate` Hibernate OK.
- Nouveau test (PostgreSQL Testcontainers) : bail ACTIF `2026-06-01 → 2026-09-01` (dans la bande
  ]J+60 ; J+90] à la date du jour) → **1 alerte `PREAVIS`, 0 `FIN_BAIL`** ; 2ᵉ passage → **0**
  (anti-doublon EF-65) ; lecture bailleur : `type = PREAVIS`.
- Régressions S04 (V9) inchangées : les baux à terme passé (`FIN_BAIL`) ne déclenchent pas `PREAVIS`.

## 5. Conformité CGPA

- Plan d'Exécution **approuvé** par le PO avant codage (gate Phase 07 respecté).
- Multi-tenant : patron SECURITY DEFINER inchangé, aucun élargissement de surface RLS.
- Pas de secret, pas de PII journalisée.
- Décision merge : **réservée au PO** (non mergé par l'agent).

## 6. Suite

- MAJ `docs/project-state.md` après merge (synchro post-merge, PR de doc séparée comme S03/S04).
- US-50 (EF-62) clôt le reste fonctionnel backend du S04. Reste à planifier : frontend S04.
