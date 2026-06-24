# Plan détaillé — Étape 3 : Préflight Production et sauvegarde

| Champ | Valeur |
|---|---|
| Date | 2026-06-24 |
| Statut | **Exécutée le 2026-06-24 — préflight PASS** |
| Version cible | `1.1.1` |
| Artefact candidat | API/Web `sha-0adc4941` |
| Production actuelle attendue | `1.1.0` — API/Web `sha-05424aa3` |
| Gate Production | GO sous réserve acceptée — `PRODUCTION_READY` |
| Cible | Hôte dédié `loyertracker-prod-server` |

## 1. Objectif

Établir un état Production pré-déploiement reproductible et produire une sauvegarde PostgreSQL
complète, vérifiée et traçable.

Cette étape ne déploie pas le Hotfix. Elle ne modifie pas `LOYERTRACKER_TAG`, ne tire pas les
images candidates et ne recrée aucun service.

## 2. Autorisation requise

L'exécution est interdite avant validation explicite du présent plan par le PO.

La validation autorise uniquement :

- une connexion SSH à l'hôte Production ;
- les contrôles en lecture décrits ci-dessous ;
- l'exécution du script versionné de sauvegarde ;
- la vérification du dump et des métadonnées de rollback ;
- les mises à jour documentaires de résultat.

## 3. Accès et périmètre

- Utiliser l'IP privée de l'hôte Production depuis un serveur situé dans le même VPC.
- Utiliser la clé SSH dédiée fournie par l'inventaire sécurisé hors dépôt.
- Ne jamais afficher, copier ou versionner `.env`, les clés privées ou les secrets.
- Travailler dans le dépôt Production existant.
- Utiliser explicitement :
  `COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml`.

Sont interdits :

- `git pull`, changement de branche ou modification du dépôt sur l'hôte ;
- `docker compose pull`, `up`, `down`, `stop`, `restart` ou `rm` ;
- modification de `.env` ;
- changement de tag ;
- restauration de base ;
- commande Docker globale ou nettoyage.

## 4. Phase A — Préflight en lecture seule

### 4.1 Identité de l'hôte

Relever sans secret :

- hostname ;
- date UTC et synchronisation NTP ;
- version Docker et Docker Compose ;
- répertoire courant et commit Git de l'hôte ;
- état du worktree de déploiement.

Critères :

- hôte attendu ;
- heure UTC cohérente ;
- Docker/Compose opérationnels ;
- aucune modification locale inexpliquée empêchant la reproductibilité.

### 4.2 Capacité

Contrôler :

- espace disque global et espace du répertoire de backups ;
- mémoire disponible ;
- charge système ;
- taille actuelle du volume PostgreSQL et des derniers dumps.

Seuils d'arrêt :

- espace libre inférieur à 20 % ;
- espace disponible inférieur à deux fois la taille estimée du dump ;
- pression mémoire ou charge anormale ;
- filesystem en lecture seule.

### 4.3 État de la stack

Avec les fichiers Compose Production explicitement ciblés :

- lister les services ;
- confirmer `api`, `nginx`, `postgres`, `keycloak` healthy ;
- confirmer `keycloak-init` terminé correctement si présent ;
- relever les images et digests réellement exécutés ;
- confirmer que l'API et le Web utilisent `sha-05424aa3` ;
- confirmer que PostgreSQL est prêt ;
- vérifier Flyway V1→V14 sans appliquer de migration ;
- vérifier l'issuer Keycloak public ;
- vérifier `/healthz` public ;
- vérifier que l'endpoint Prometheus reste non public.

Critères d'arrêt :

- service unhealthy ou redémarrages répétés ;
- tag courant différent de `sha-05424aa3` sans décision documentée ;
- migration Flyway inattendue ;
- issuer ou healthcheck incorrect ;
- port interne exposé de manière inattendue.

### 4.4 Observabilité et exploitation

Vérifier :

- cibles Prometheus attendues `up` ;
- absence d'alerte critique active ;
- Alertmanager opérationnel ;
- cron de sauvegarde présent et non dupliqué ;
- dernier backup planifié disponible et lisible ;
- permissions du répertoire et des fichiers de backup.

Une alerte critique active ou un cron incohérent impose l'arrêt et une analyse avant sauvegarde.

### 4.5 Rollback

Confirmer :

- images API/Web `sha-05424aa3` disponibles localement ou accessibles sur GHCR ;
- digests conformes à la décision Gate ;
- commande de rollback ciblée connue ;
- aucun rollback de schéma requis pour `1.1.1` ;
- responsable opérationnel identifié.

Le candidat `sha-0adc4941` ne doit pas être tiré pendant cette étape.

## 5. Point de contrôle A

Après les contrôles en lecture :

- **PASS** : autorise l'exécution de la sauvegarde dans la même étape ;
- **FAIL** : interdit la sauvegarde et toute suite, sauf si le défaut concerne uniquement
  l'absence d'un backup récent que la sauvegarde planifiée doit précisément corriger.

Le résultat et les anomalies doivent être consignés avant de poursuivre.

## 6. Phase B — Sauvegarde Production

### 6.1 Commande prévue

Depuis la racine du dépôt Production :

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml \
  ./infra/backup/backup-postgres.sh
```

Le script doit :

- produire un dump PostgreSQL complet au format custom ;
- produire le fichier jumeau `globals.sql` ;
- appliquer les permissions `700` au répertoire et `600` aux fichiers ;
- exécuter `pg_restore --list` ;
- appliquer la rétention ;
- pousser le heartbeat de backup si le Pushgateway est disponible.

### 6.2 Contrôles post-sauvegarde

Relever :

- nom, chemin, date UTC et taille du dump ;
- nom et taille du fichier globals ;
- résultat explicite de `pg_restore --list` ;
- permissions ;
- checksum SHA-256 des deux fichiers ;
- présence du heartbeat de sauvegarde ;
- absence d'erreur dans la sortie du script.

Ne jamais afficher le contenu du dump, du fichier globals ou de `.env`.

### 6.3 Critères d'arrêt

La sauvegarde est invalide si :

- le script retourne un code non nul ;
- un des deux fichiers manque ou est vide ;
- `pg_restore --list` échoue ;
- les permissions sont trop ouvertes ;
- le checksum ne peut pas être produit ;
- PostgreSQL ou un autre service devient unhealthy ;
- le filesystem atteint un seuil critique.

## 7. Vérification finale de non-mutation

Après la sauvegarde :

- confirmer que les services restent healthy ;
- confirmer que les images courantes restent `sha-05424aa3` ;
- confirmer que `.env` et `LOYERTRACKER_TAG` n'ont pas été modifiés ;
- confirmer qu'aucun service n'a été recréé ;
- confirmer que Flyway reste V1→V14 ;
- confirmer que la Production publique répond normalement.

## 8. Risques et mitigations

| Risque | Mitigation |
|---|---|
| Connexion au mauvais hôte | Vérifier hostname, IP privée et contexte avant toute commande |
| Fuite de secret dans les logs | Ne pas afficher `.env`, globals ou commandes contenant des secrets |
| Backup ciblant le mauvais Compose | Définir explicitement `COMPOSE_FILE` Production |
| Espace disque insuffisant | Contrôle capacité avant écriture |
| Dump incomplet | `pg_restore --list`, taille et checksum obligatoires |
| Perturbation Production | Script en lecture logique ; arrêt immédiat si santé dégradée |
| Confusion backup/restore | `restore-postgres.sh` strictement interdit |
| Déploiement anticipé | Aucun pull, changement de tag ou `up -d` autorisé |

## 9. Mutations autorisées

- création du dump et du fichier globals ;
- mise à jour de la rétention par le script de backup ;
- heartbeat Pushgateway ;
- mise à jour documentaire de l'étape.

Toute autre mutation est hors autorisation.

## 10. Preuves de sortie

- rapport de préflight avec tous les contrôles ;
- état des services et images avant/après ;
- référence du backup et du fichier globals ;
- tailles, permissions et checksums ;
- preuve `pg_restore --list` ;
- confirmation du rollback `sha-05424aa3` ;
- décision **préflight PASS** ou **FAIL** ;
- confirmation qu'aucun déploiement n'a été effectué.

Les chemins sensibles complets peuvent être masqués dans la documentation publique ; les noms
de fichiers et checksums doivent rester traçables.

## 11. Décision de sortie

### Préflight PASS

Autorise uniquement la production du plan détaillé de l'Étape 4 — déploiement du Hotfix.

### Préflight FAIL

Interdit tout déploiement. Une action corrective et un nouveau préflight sont requis.

## 12. Résultat d’exécution

Rapport : `docs/cgpa/09-production/preflight-backup-v1.1.1-report.md`.

- Préflight : **PASS**.
- Backup et globals créés, permissions 600, checksums produits.
- `pg_restore --list` : OK.
- Heartbeat backup présent ; alerte `BackupHeartbeatMissing` résolue.
- Services healthy, zéro restart, tag courant `sha-05424aa3` inchangé.
- Aucun pull, changement de tag ou déploiement.

## 13. Prochaine action autorisée

Produire le plan détaillé de l’Étape 4 — déploiement du Hotfix.
