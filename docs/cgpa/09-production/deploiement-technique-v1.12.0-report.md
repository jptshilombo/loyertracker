# Rapport Déploiement Technique — Release `1.12.0` (Sprint C EP-15)

| Champ | Valeur |
|---|---|
| Date | 2026-07-19 |
| Fenêtre | 11:48–11:52 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Candidat | `sha-359f4d63` |
| Tag précédent | `sha-cba13f52` (`1.11.0`) |
| Autorisation PO | Explicite, après Gate Production GO (`gate-production-sprint-c-ep15-decision.md`) et Préflight renforcé PASS (`preflight-backup-v1.12.0-report.md`) |
| Verdict | **PASS technique** |

## Exécution ciblée

Le dépôt Production a été avancé par fast-forward de `cba13f5` à `c14cdc2` (39 fichiers ; écart
confirmé purement documentaire au-delà du candidat applicatif `359f4d6` — `git diff --stat 359f4d6
c14cdc2` ne touche que 3 fichiers Markdown). Aucun écart sur `docker-compose.yml`/
`docker-compose.prod.yml`/`.env.example` (`git diff --stat cba13f5 HEAD` vide sur ces fichiers).
Les digests correspondent exactement au Gate Production et au Préflight :

- API : `sha256:ea040492bb5ad6b6a72b84665e22cd47a66d79c293b874fca481d5a276afe1c8` ;
- Web : `sha256:e70ebc7ba7d71406edaec6f890c2f57f06ae9d7c855680e0fba01914b4251968`.

Le tag `.env` a été basculé vers `sha-359f4d63` (`.env.bak-pre-1.12.0` disponible depuis le
Préflight). Seuls `api` et `nginx` ont été tirés et recréés (`--no-deps`, aucune option
`--remove-orphans` malgré l'avertissement Compose « conteneurs orphelins » du monitoring, attendu
et ignoré comme à chaque déploiement précédent). PostgreSQL, Keycloak et les quatre services de
monitoring sont restés actifs et n'ont subi aucun redémarrage. Aucune commande Docker globale
n'a été exécutée.

## Migration et contrôles

Flyway a validé 25 migrations puis appliqué `26 - ep15 bascule bail locataire` avec succès. Les
objets V26 sont confirmés présents : table `locataire`, colonne `bail.locataire_id`, colonnes
`bail.locataire_nom`/`bail.locataire_email` **absentes** (confirmé via
`information_schema.columns`, 0 ligne).

### Vérification du backfill V26 (non additive) sur données réelles

Migration critique car non additive (suppression de colonnes) exécutée sur les données
Production réelles :

| Contrôle | Résultat |
|---|---|
| `bail.locataire_nom`/`locataire_email` | Colonnes absentes (0 ligne dans `information_schema.columns`) |
| Baux avec `locataire_id` renseigné | **8/8** (100 %) |
| Baux orphelins (`locataire_id` sans `Locataire` correspondant) | **0** |
| `Locataire` total après backfill | 8 (1 par bail, aucun locataire partagé entre baux dans le jeu de données Production) |
| Échantillon (5 lignes) | `nom` = valeur intégrale de l'ancien `locataire_nom`, `prenom` vide (RSV-EP15-02 confirmé en conditions réelles Production) |

## Backup post-migration (condition bloquante du Gate/Préflight)

Conformément à la condition explicite du Gate Production (`gate-production-sprint-c-ep15-decision.md`,
condition 5) et du Préflight renforcé (RSV-EP15-03, migration V26 non additive), un second backup a
été produit **immédiatement après** l'application de V26 :

| Fichier | Taille | Mode | SHA-256 |
|---|---:|---:|---|
| `loyertracker-20260719-115220.dump` | 761841 | 600 | `937e777b9c780b54cfa159e7c88581c2f868330f2d1d8a8c6e5aaf04956bc613` |
| `loyertracker-20260719-115220.globals.sql` | 1108 | 600 | `825274208c6f9d72b3d4fd90390a58d09b1282971ee5df2662a9c8dc706fee83` |

`pg_restore --list` : **799 entrées** (même méthode `docker cp` que le Préflight). Ce backup
post-migration est désormais le point de restauration de référence — un rollback applicatif seul
n'étant plus viable après V26 (RSV-EP15-03), toute restauration devra utiliser ce dump plutôt que
le backup pré-migration du Préflight (`loyertracker-20260719-114702.dump`).

## Contrôles finaux

| Contrôle | Résultat |
|---|---|
| Services | 8/8 actifs, 4/4 healthy, `RestartCount=0` sur `api`/`nginx`/`postgres`/`keycloak` (conteneurs `api`/`nginx` recréés, compteur repart à 0) |
| Images actives | `sha-359f4d63`, digests exacts du Gate/Préflight (vérifiés sur les conteneurs) |
| Flyway | V26 appliquée, 26/26 |
| Backfill V26 | 8/8 baux avec `locataire_id`, 0 orphelin, colonnes legacy absentes |
| `/healthz` | 200 |
| Production publique | `https://loyertracker.loyerpro.org` → 200 |
| Prometheus / Alertmanager | 5/5 `up` ; 0 alerte active |
| 5xx depuis le redéploiement | 0 |
| Rollback | `sha-cba13f52` (images encore présentes localement) **non viable seul après V26** — restauration du backup post-migration ci-dessus requise en cas d'anomalie |

**Déploiement technique PASS.** La validation finale (smoke Production, réactivation temporaire
autorisée de `bailleur-test@test.local`/`directAccessGrants`) reste une étape distincte, requérant
une autorisation PO explicite dédiée avant exécution — même discipline que toutes les releases
précédentes. `PRODUCTION_DEPLOYED` n'est pas encore prononcé — en attente de cette validation
finale.
