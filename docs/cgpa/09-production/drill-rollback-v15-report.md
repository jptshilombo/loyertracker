# Rapport Drill Rollback V15 — pg_restore staging

| Champ | Valeur |
|---|---|
| Date | 2026-06-29 |
| Heure UTC | 17:05–17:12 UTC |
| Hôte | `ai-test-server` (`172.31.11.102`) — staging |
| Objectif | Prouver la procédure `pg_restore` en conditions réelles sur Flyway V15 |
| Réserve fermée | **RSV-RM-03** — drill rollback jamais exécuté |
| Verdict | **PASS — RSV-RM-03 LEVÉE** |

---

## Contexte

RSV-RM-03 (ouverte depuis CGPA v5.3) : la procédure de rollback avec `pg_restore` n'avait jamais
été exécutée sur le schéma V15 (`affectations_exceptions` — migration la plus récente). Ce drill
prouve la chaîne complète : dump → copy → restore → vérification → cleanup, sans impact sur
la DB de production ni sur la DB staging en production.

> **Périmètre** : drill sur staging uniquement. La Production n'est pas touchée.
> La DB staging reste intacte — drill exécuté dans une DB de test isolée (`loyertracker_drill_v15`).

---

## 1. Dump snapshot (source du drill)

| Paramètre | Valeur |
|---|---|
| Fichier | `loyertracker-staging-drill-v15-20260629-170556.dump` |
| Taille | 337 Kio |
| SHA-256 | `ad58f68717e83249d3f5ffdc6f9330f3809d9e55056eb2d546130f3985a54052` |
| Format | `pg_dump -Fc` (custom, compressé) |
| Base source | `loyertracker` sur `loyertracker-staging-postgres-1` |
| Conservé sur | `/home/ubuntu/loyertracker-backups/` (staging) |

### Vérification intégrité `pg_restore --list`

```
docker exec loyertracker-staging-postgres-1 pg_restore --list /tmp/drill.dump | wc -l
```

**730 entrées** — identique au dump Production v1.3.0 (`preflight-backup-v1.3.0-report.md`).
Inclut : schéma, données, index, fonctions SECURITY DEFINER, politiques RLS, ACL.

---

## 2. Restore dans DB de test isolée

```sql
CREATE DATABASE loyertracker_drill_v15;
```

```bash
docker exec loyertracker-staging-postgres-1 \
  pg_restore -U loyertracker \
             -d loyertracker_drill_v15 \
             --no-owner \
             --role=loyertracker \
             /tmp/drill.dump
```

**Exit code : 0 — aucune erreur.**

---

## 3. Vérifications post-restore

### Flyway — 15/15 migrations, toutes `success=t`

| Version | Description | Succès |
|---|---|---|
| 1 | init schema | ✅ t |
| 2 | tenant resolution | ✅ t |
| 3 | authorization predicates | ✅ t |
| 4 | s02 biens baux affectations helpers | ✅ t |
| 5 | role applicatif rls | ✅ t |
| 6 | s03 echeances loyers | ✅ t |
| 7 | s03 loyers en retard | ✅ t |
| 8 | s04 honoraires | ✅ t |
| 9 | s04 alertes | ✅ t |
| 10 | s04 alerte preavis | ✅ t |
| 11 | quittances ventilation loyer adresse bailleur | ✅ t |
| 12 | patrimoine type bien | ✅ t |
| 13 | affectations patrimoine | ✅ t |
| 14 | honoraires patrimoine | ✅ t |
| 15 | affectations exceptions | ✅ t |

**V15 présente et `success=t` ✅ — migration la plus récente, cœur du drill.**

### Compteurs tables métier

| Table | Lignes |
|---|---|
| bailleur | 18 |
| patrimoine | 18 |
| bien | 18 |
| bail | 17 |
| paiement | 179 |
| gestionnaire | 20 |
| affectation | 18 |
| alerte | 128 |

### Politiques RLS restituées (11/11 tables)

| Table | Politiques |
|---|---|
| affectation | 1 |
| alerte | 1 |
| audit_log | 1 |
| bail | 1 |
| bailleur | 1 |
| bien | 1 |
| garantie | 1 |
| honoraire | 1 |
| invitation | 1 |
| paiement | 1 |
| patrimoine | 1 |

**11 politiques RLS restituées ✅** — isolation multi-tenant préservée après restore.

### Rôle applicatif

```
loyertracker_api | rolsuper=f | rolbypassrls=f
```

**NOBYPASSRLS confirmé ✅** — RLS `FORCE` effective sur le rôle applicatif.

---

## 4. Nettoyage post-drill

| Action | Résultat |
|---|---|
| `DROP DATABASE loyertracker_drill_v15` | ✅ |
| `rm /tmp/drill.dump` (conteneur) | ✅ |
| DB staging `loyertracker` intacte | ✅ (18 bailleurs, 18 patrimoines — inchangé) |
| Aucune DB parasite | ✅ (seule `loyertracker` subsiste hors templates) |

---

## 5. Conclusion

**Toutes les vérifications PASS.** La procédure `pg_restore` sur Flyway V15 est validée en
conditions réelles :

- Dump `pg_dump -Fc` → intégrité confirmée (730 entrées)
- Restore exit 0, sans erreur
- V15 (`affectations_exceptions`) présente et `success=t`
- 11 politiques RLS restituées
- Rôle `loyertracker_api` NOBYPASSRLS intact
- DB staging inchangée après drill

**RSV-RM-03 LEVÉE — 2026-06-29.**

En cas de rollback Production nécessitant `pg_restore`, la procédure est identique au présent
drill (adaptée au dump Production et à l'hôte `loyertracker-prod-server`).
Cf. `infra/backup/restore-postgres.sh` pour la procédure outillée.
