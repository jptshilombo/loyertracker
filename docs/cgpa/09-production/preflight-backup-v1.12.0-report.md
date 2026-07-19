# Rapport Préflight + Backup renforcé — Release `1.12.0` (Sprint C EP-15)

| Champ | Valeur |
|---|---|
| Date | 2026-07-19, ~11:33–11:47 UTC |
| Hôte | `loyertracker-prod-server` — `18.158.70.88` |
| Candidat | `sha-359f4d63` |
| Production / rollback | `1.11.0` — `sha-cba13f52` |
| Verdict | **PASS** |

## Contrôles lecture seule

- 8/8 conteneurs actifs, 4/4 healthy applicables, restart=0 (`api`/`nginx`/`postgres`/`keycloak`) ;
  tag courant `sha-cba13f52` inchangé.
- Flyway **25/25** ; V26 (`bail.locataire_id`, table `locataire`, suppression
  `locataire_nom`/`locataire_email`) **pas encore appliquée** — état propre avant migration.
- `bailleur-test@test.local` confirmé **désactivé** (`enabled: false`) ;
  `directAccessGrantsEnabled=false` sur `loyertracker-spa` (vérifié via `kcadm.sh`) — état restauré
  après la validation finale `1.11.0` rejouée plus tôt dans cette même session.
- Prometheus **5/5** cibles `up` ; Alertmanager : **0 alerte active**.
- **0 ligne 5xx** (30 dernières minutes) ; site public `https://loyertracker.loyerpro.org` → **200**.
- 31 Gio disque libres (21 %), ~1,9 Gio mémoire disponible, charge 0,49/0,26/0,22.
- Données métier baseline : 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties,
  1 gestionnaire, 0 locataire, 6 quittances — identique à la clôture `1.11.0` (§0L,
  `prod-state.md`).
- Dépôt hôte au commit `cba13f5` (`1.11.0`), en retard sur `origin/main` (`359f4d6`) — à
  synchroniser au déploiement technique (`git pull --ff-only`) ; aucun `pull` exécuté durant ce
  Préflight.
- Aucune commande de mutation exécutée (lecture seule + `pg_dump` uniquement).

## Backup vérifié (avant migration)

| Fichier | Taille | Mode | SHA-256 |
|---|---:|---:|---|
| `loyertracker-20260719-114702.dump` | 761471 | 600 | `a73b81c04f8874fdff982d9fdc9102aa4d219703ebc0b6e546d87e952049409d` |
| `loyertracker-20260719-114702.globals.sql` | 1108 | 600 | `00d7c736389a6e0770ab53b763f2985c654df8400fefc008478ee66bc265e60a` |

`pg_restore --list` : **799 entrées** (vérifié via `docker cp` dans le conteneur `postgres`, même
méthode que les Préflights précédents). Copie hebdomadaire déposée automatiquement (dimanche,
script `infra/backup/backup-postgres.sh`). Heartbeat de sauvegarde poussé avec succès vers le
Pushgateway.

## Volet renforcé — migration V26 non additive (RSV-EP15-03)

La migration V26 supprime `bail.locataire_nom`/`bail.locataire_email` après backfill : un rollback
applicatif seul devient **non viable** une fois V26 appliquée (même profil que V20/Sprint 9 et
V24/EP-15 Sprints A+B). Ce Préflight ne couvre que le backup **avant** migration (ci-dessus).

**Condition bloquante reportée sur le déploiement technique**, conformément au Gate Production
(`gate-production-sprint-c-ep15-decision.md`, condition 5) : un **second backup vérifié** (dump +
globals + `pg_restore --list` + SHA-256) devra être produit **immédiatement après** l'application
de V26 et consigné dans `deploiement-technique-v1.12.0-report.md`, avant toute validation finale.
Sans ce second backup, la validation finale ne doit pas être autorisée.

## Secrets et rollback

- **Aucune nouvelle variable d'environnement/secret requise** : `git diff --stat cba13f5 359f4d6`
  sur `docker-compose.yml`/`docker-compose.prod.yml`/`.env.example` ne montre aucun changement
  (confirmé en local, le dépôt hôte n'ayant pas encore avancé jusqu'à `359f4d6`).
- `.env.bak-pre-1.12.0` créé, mode 600, `.env` hôte inchangé (tag `sha-cba13f52`, aucun service
  redémarré).
- Rollback `sha-cba13f52` : images API/Web déjà présentes localement sur l'hôte (aucun pull requis
  pour un retour arrière **avant** application de V26) ; **après** application de V26, seule la
  restauration du backup pré-migration (ci-dessus) permet un retour arrière complet (RSV-EP15-03,
  acceptée par le PO).

## Verdict

`CHANGELOG.md` `[Non publié]` couvre déjà le Sprint C EP-15 — promotion en `[1.12.0]` datée
laissée à la décision explicite du PO au moment du déploiement (la numérotation `1.12.0` suit
`1.11.0`, proposée mais non encore actée dans `CHANGELOG.md`).

**Préflight Production renforcé `1.12.0` : PASS.** Aucun déploiement autorisé. Une instruction
explicite distincte est requise pour déployer `api` + `nginx` et appliquer la migration V26 ; le
déploiement technique devra produire le second backup post-migration exigé ci-dessus avant toute
validation finale.
