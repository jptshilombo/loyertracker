# Rapport Préflight + Backup — Release `1.10.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-15, 13:02–13:10 UTC |
| Hôte | `loyertracker-prod-server` — `18.158.70.88` |
| Candidat | `sha-c9200a51` |
| Production / rollback | `1.9.0` — `sha-75646d8f` |
| Verdict | **PASS** |

## Contrôles lecture seule

- 8/8 conteneurs actifs, 4/4 healthy applicables, restart=0 ; tag courant `sha-75646d8f`
  inchangé ; digests API/Web conformes au Gate `1.9.0` (`sha256:3c227910…`/`sha256:f0146fa6…`),
  aucune dérive.
- Flyway **22/22** ; objets V23 (`gestionnaire.statut`)/V24 (`locataire`) absents — état propre
  avant migration.
- Invariant ledger garantie **8/8 PASS** (`solde_actuel = Σ mouvements`). **Point de vigilance
  méthodologique** : la première requête, exécutée avec le rôle applicatif `loyertracker_api`
  (soumis à la RLS `FORCE`, aucun contexte `app.bailleur_id` fixé), retournait 0 ligne sur toutes
  les tables métier — écart apparent corrigé en interrogeant avec le rôle superutilisateur
  `loyertracker` (bypass RLS, cf. `POSTGRES_USER` du `.env` hôte), qui confirme les décomptes
  réels : 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties, 6 quittances, 1 gestionnaire.
  **Aucune perte de données** — simple effet attendu de la RLS `bailleur_isolation`, aucune
  anomalie applicative.
- `bailleur-test@test.local` confirmé **désactivé** (`enabled: false` via `kcadm.sh`).
- Prometheus **5/5** cibles `up` (blackbox-keycloak, blackbox-postgres, loyertracker-api,
  prometheus, pushgateway). Alertmanager : **1 alerte active** `BackupHeartbeatMissing` —
  pattern récurrent déjà qualifié (host redémarré, Pushgateway purgé au boot, cron de backup pas
  encore rejoué depuis) ; **non bloquant**, même qualification qu'en `1.7.0`/`1.8.0`.
- **0 ligne 5xx** dans les logs `api` depuis le dernier redémarrage ; site public
  `https://loyertracker.loyerpro.org` → **200**.
- 31 Gio disque libre, 1,1 Gio mémoire libre / 2,0 Gio disponible, charge 0,00.
- Dépôt hôte 16 commits derrière `origin/main` (à synchroniser au déploiement, `git pull
  --ff-only`) — aucun `pull`/redémarrage/déploiement exécuté durant ce Préflight.

## Backup vérifié

| Fichier | Taille | Mode | SHA-256 |
|---|---:|---:|---|
| `loyertracker-20260715-120812.dump` | 749644 | 600 | `f4d5e2cb866bc88afa54e3de33fbf99570c3b0e3d5b4e05bd64682f38d872fa0` |
| `loyertracker-20260715-120812.globals.sql` | 1108 | 600 | `1a242fc65600d0756297f844c7f18a3fa40c0d7732e092653a10300ffca67555` |

`pg_restore --list` : **777 entrées** (vérifié via `docker cp` dans le conteneur `postgres` —
la variante `pg_restore --list - < fichier` via `docker compose exec -T` échoue sur ce moteur
Docker avec « did not find magic string in file header » ; le fichier copié directement dans le
conteneur se restaure sans erreur, confirmant que le dump lui-même est intact).

## Secrets et rollback

- **Aucune nouvelle variable d'environnement/secret requise** pour ce périmètre EP-15
  (contrairement au `QUITTANCE_HMAC_SECRET` de `1.9.0`) — confirmé au Gate Production par diff
  des fichiers Compose/`.env.example`.
- `.env.bak-pre-1.10.0` créé, mode 600, `.env` hôte inchangé (tag `sha-75646d8f`, aucun service
  redémarré).
- Rollback `sha-75646d8f` : images API/Web déjà présentes localement sur l'hôte (aucun pull
  requis pour un retour arrière), digests confirmés conformes.

## Verdict

Release notes finalisées ; `CHANGELOG.md` promu en `[1.10.0] — 2026-07-15`.

**Préflight Production `1.10.0` : PASS.** Aucun déploiement autorisé. Une instruction explicite
distincte est requise pour déployer `api` + `nginx`.
