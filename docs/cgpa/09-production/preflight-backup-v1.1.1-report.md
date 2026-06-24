# Rapport d'exécution — Préflight et sauvegarde Production `1.1.1`

| Champ | Valeur |
|---|---|
| Date UTC | 2026-06-24 |
| Hôte | `loyertracker-prod-server` (`172.31.22.90`) |
| Décision | **PASS** |
| Production courante | `1.1.0` — `sha-05424aa3` |
| Candidat | `1.1.1` — `sha-0adc4941` |
| Déploiement exécuté | **Non** |

## Préflight

| Contrôle | Résultat |
|---|---|
| NTP | Synchronisé |
| Docker | 29.1.3 |
| Docker Compose | 2.40.3 |
| Commit du dépôt hôte | `05424aa3df0f03f5267f2862df978b8622ad6838` |
| Espace disque | 33 Gio libres sur 38 Gio, utilisation 14 % |
| Mémoire | 2,0 Gio disponibles sur 3,7 Gio |
| Charge | Faible |
| Services applicatifs | API, Web, PostgreSQL, Keycloak healthy |
| Restart count | 0 pour les quatre services applicatifs |
| Image API | `sha-05424aa3`, digest conforme au Gate |
| Image Web | `sha-05424aa3`, digest conforme au Gate |
| PostgreSQL | Accepte les connexions |
| Flyway | 14 migrations réussies, rang maximal 14 |
| Issuer Keycloak | Domaine Production canonique |
| Prometheus public | HTTP 404 |
| Cibles Prometheus | 5/5 `up` |
| Alertmanager | Ready |
| Cron backup | Une entrée, `02:15 UTC` |

Écart mineur constaté : deux fichiers de sauvegarde `.env` non suivis existent dans le dépôt
de l'hôte. Ils n'ont pas été lus, modifiés ou supprimés et ne sont pas utilisés comme `.env`
actif. À traiter séparément comme hygiène d'exploitation.

## Alerte de backup

Le préflight initial a trouvé `BackupHeartbeatMissing` en état `firing`. L'hôte n'était actif
que depuis environ 5 h 37 et avait manqué le cron de 02:15 UTC. Cette anomalie correspondait à
l'exception prévue par le plan : absence d'un backup récent que l'exécution de la sauvegarde
devait corriger.

Après la sauvegarde :

- la métrique `loyertracker_backup_last_success_epoch` est présente ;
- le Pushgateway et la cible Prometheus sont `up` ;
- l'alerte `BackupHeartbeatMissing` est résolue ;
- aucune autre alerte active n'est remontée.

## Sauvegarde créée

| Élément | Valeur |
|---|---|
| Dump | `loyertracker-20260624-140441.dump` |
| Taille | 313 855 octets |
| Permissions | `600` |
| SHA-256 | `4ba79c4d2d99dd88edef8283ae3cfdc8e8dc229f796d896447d3e95566167295` |
| Globals | `loyertracker-20260624-140441.globals.sql` |
| Taille globals | 1 108 octets |
| Permissions globals | `600` |
| SHA-256 globals | `322561063784102771d2eae627e6a6e4c4f2813a53eab1e2b3470aba374c2322` |
| Vérification | `pg_restore --list` : OK |
| Heartbeat | Poussé et scrappé |

Le chemin complet hors dépôt reste
`~/loyertracker-backups/daily/`; le contenu des fichiers n'a jamais été affiché.

## Vérification de non-mutation

- commit hôte inchangé ;
- tag API/Web inchangé : `sha-05424aa3` ;
- digests inchangés ;
- aucun restart et aucune recréation de service ;
- Flyway inchangé V1→V14 ;
- Production publique disponible ;
- aucun `pull`, `up`, `down`, changement `.env` ou changement de tag ;
- candidat `sha-0adc4941` non tiré sur l'hôte.

## Rollback

Le rollback applicatif `sha-05424aa3` est confirmé disponible et correspond aux images
actuellement exécutées.

## Décision

**Préflight PASS.**

La réserve `RP-111-01` (backup non produit) est levée. `RSV-STG-01` et le risque CORS restent
inchangés. Cette décision autorise uniquement la production du plan détaillé de l'Étape 4 —
déploiement du Hotfix.
