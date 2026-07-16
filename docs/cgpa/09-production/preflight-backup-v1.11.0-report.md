# Rapport Préflight + Backup — Release `1.11.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-16, ~17:45–17:53 UTC |
| Hôte | `loyertracker-prod-server` — `18.158.70.88` |
| Candidat | `sha-cba13f52` |
| Production / rollback | `1.10.0` — `sha-c9200a51` |
| Verdict | **PASS** |

## Contrôles lecture seule

- 8/8 conteneurs actifs, 4/4 healthy applicables, restart=0 ; tag courant `sha-c9200a51` inchangé ;
  digests API/Web conformes au Gate `1.10.0` (`sha256:37de87e8…`/`sha256:7ade9816…`), aucune
  dérive.
- Flyway **24/24** ; objet V25 (`bail.date_cloture_effective`) absent — état propre avant
  migration.
- Invariant ledger garantie **8/8 PASS** (`solde_actuel` aligné sur le dernier mouvement du ledger
  de chaque garantie, rôle bypass-RLS `loyertracker`).
- `bailleur-test@test.local` confirmé **désactivé** (`enabled: false` via `kcadm.sh`) ;
  `directAccessGrantsEnabled=false` sur les trois clients applicatifs (`loyertracker-spa`/
  `-admin`/`-api`).
- Prometheus **5/5** cibles `up` (blackbox-keycloak, blackbox-postgres, loyertracker-api,
  prometheus, pushgateway). Alertmanager : **0 alerte active**.
- **0 ligne 5xx** dans les logs `nginx` sur les 30 dernières minutes ; site public
  `https://loyertracker.loyerpro.org` → **200**.
- 31 Gio disque libre (20 %), ~1,7 Gio mémoire disponible, charge 0,14/0,08/0,02.
- Données métier baseline : 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties,
  1 gestionnaire, 0 locataire, 6 quittances — identique à la clôture de `1.10.0`.
- Dépôt hôte 20 commits derrière `origin/main` (à synchroniser au déploiement, `git pull
  --ff-only`) — aucun `pull`/redémarrage/déploiement exécuté durant ce Préflight.

## Backup vérifié

| Fichier | Taille | Mode | SHA-256 |
|---|---:|---:|---|
| `loyertracker-20260716-175144.dump` | 759990 | 600 | `62fdf175060708a4d956fb68dbf665b35f5076717066ff162987c438ba1a546c` |
| `loyertracker-20260716-175144.globals.sql` | 1108 | 600 | `50eba80f96a63ac1c338961dbee74b3474d2e6a50178f85a453119b96a3ace55` |

`pg_restore --list` : **798 entrées** (vérifié via `docker cp` dans le conteneur `postgres`, même
méthode que les Préflights précédents — la variante `pg_restore --list - < fichier` échoue sur ce
moteur Docker sans indiquer de corruption réelle du dump).

## Secrets et rollback

- **Aucune nouvelle variable d'environnement/secret requise** pour ce périmètre EP-13 — confirmé
  au Gate Production par `git diff --stat` sur les fichiers Compose/`.env.example` (aucun
  changement).
- `.env.bak-pre-1.11.0` créé, mode 600, `.env` hôte inchangé (tag `sha-c9200a51`, aucun service
  redémarré).
- Rollback `sha-c9200a51` : images API/Web déjà présentes localement sur l'hôte (aucun pull requis
  pour un retour arrière), digests confirmés conformes.

## Verdict

`CHANGELOG.md` promu en `[1.11.0] — 2026-07-16`.

**Préflight Production `1.11.0` : PASS.** Aucun déploiement autorisé. Une instruction explicite
distincte est requise pour déployer `api` + `nginx`.
