# Rapport Déploiement Technique — Release `1.10.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-15 |
| Fenêtre | 13:39–13:41 CEST (12:39–12:41 UTC) |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Candidat | `sha-c9200a51` |
| Tag précédent | `sha-75646d8f` (`1.9.0`) |
| Autorisation PO | Explicite, après Gate Production GO (PR #219) et Préflight PASS (PR #220) |
| Verdict | **PASS technique** |

## Exécution ciblée

Le dépôt Production a été avancé par fast-forward de `3feb1d5` à `cd0d020` (aucun écart sur les
fichiers Compose/`.env.example`, vérifié avant synchronisation). Les digests correspondent
exactement au Gate Production et au Préflight :

- API : `sha256:37de87e86dfe99d0483ef6ac1934384e773f858822ab53bbe29432e7d6858db9` ;
- Web : `sha256:7ade9816f3844f10d2e8a9f63491380546d6c68370011d38fc04368ee5e51052`.

Le tag `.env` a été basculé vers `sha-c9200a51` (`.env.bak-pre-1.10.0` disponible depuis le
Préflight). Seuls `api` et `nginx` ont été tirés et recréés (`--no-deps`, aucune option
`--remove-orphans`). PostgreSQL, Keycloak et les quatre services de monitoring sont restés
actifs et n'ont subi aucun redémarrage. Aucune commande Docker globale n'a été exécutée.

## Migration et contrôles

Flyway a validé 24 migrations puis appliqué `23 - ep15 gestionnaire statut` et
`24 - ep15 locataire` en 228 ms au total. Les objets V23/V24 sont confirmés présents (table
`locataire`, colonne `gestionnaire.statut`, fonctions `SECURITY DEFINER`
`gestionnaire_a_affectation_active`/`gestionnaire_a_relation`). L'invariant ledger garantie
reste cohérent (8/8, vérifié via le rôle bypass-RLS `loyertracker`, même précaution
méthodologique qu'au Préflight).

| Contrôle | Résultat |
|---|---|
| Services | 8/8 actifs, 4/4 healthy, restart=0 sur `api`/`nginx`/`postgres`/`keycloak` |
| Images actives | `sha-c9200a51`, digests exacts du Gate/Préflight (vérifiés sur les conteneurs) |
| Flyway | V23+V24 appliquées, 24/24 |
| Objets V23/V24 | `locataire` (table), `gestionnaire.statut` (colonne), 2 fonctions `SECURITY DEFINER` |
| Invariant ledger | 8/8 PASS (bypass RLS) |
| `/healthz` | 200 |
| Production publique | `https://loyertracker.loyerpro.org` → 200 |
| Prometheus / Alertmanager | 5/5 `up` (dont `loyertracker-api` redécouvert après recréation) ; 1 alerte `BackupHeartbeatMissing` — pattern déjà qualifié au Préflight, sans rapport avec ce déploiement |
| 5xx depuis le redéploiement | 0 |
| Rollback | `sha-75646d8f` (images encore présentes localement), V23/V24 additives, backup Préflight disponible |

**Déploiement technique PASS.** La validation finale (smoke Production) reste une étape
distincte : elle réactive temporairement `bailleur-test@test.local` et `directAccessGrants`
(pattern `1.2.1`→`1.9.0`), ce qui requiert une autorisation PO explicite dédiée avant exécution.
`PRODUCTION_DEPLOYED` n'est pas encore prononcé — en attente de cette validation finale.
