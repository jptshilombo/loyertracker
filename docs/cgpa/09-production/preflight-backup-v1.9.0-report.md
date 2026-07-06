# Rapport Préflight + Backup — Release `1.9.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-06, 18:13–18:16 CEST |
| Hôte | `loyertracker-prod-server` — `18.158.70.88` |
| Candidat | `sha-75646d8f` |
| Production / rollback | `1.8.0` — `sha-2c5f43c7` |
| Verdict | **PASS** |

## Contrôles lecture seule

- 8/8 conteneurs actifs, 4/4 healthy, restart=0 ; tag courant inchangé.
- Flyway 21/21 ; objets V22 absents, état propre avant migration.
- `cree_le` 3/3 non NULL ; invariant ledger 3/3 PASS.
- Prometheus 5/5 ; zéro erreur API depuis le boot ; HTTPS 200.
- 31 Gio libres, 2,0 Gio mémoire disponible, charge 0,06.
- Dépôt hôte `97f7caf1` à synchroniser avant déploiement.
- Aucun pull, redémarrage ou déploiement exécuté.

## Backup vérifié

| Fichier | Taille | Mode | SHA-256 |
|---|---:|---:|---|
| `loyertracker-20260706-181545.dump` | 325755 | 600 | `a017b81f603523bc415ea520dae8ba19ad79680d7efbc24693bae81c053cd8b3` |
| `loyertracker-20260706-181545.globals.sql` | 1108 | 600 | `c07cd206c99f57c0602c496ebe776229fcc35936d3bc14b28109f842ce9a8ce6` |

`pg_restore --list` : **742 entrées**. Heartbeat visible dans Prometheus.

## Secrets et rollback

- `.env.bak-pre-1.9.0` créé, mode 600 ;
- HMAC Production distinct et persistant, longueur 64, jamais affiché ni rapatrié ;
- KID=1 et URL Production confirmés ;
- `.env` mode 600, tag inchangé, aucun service redémarré ;
- rollback `sha-2c5f43c7` présent ; V22 additive ; dump disponible.

## Verdict

Release notes finalisées ; `CHANGELOG.md` promu en `[1.9.0] — 2026-07-06`.

**Préflight Production `1.9.0` : PASS.** Aucun déploiement autorisé. Une instruction explicite
distincte est requise pour déployer `api` + `nginx`.
