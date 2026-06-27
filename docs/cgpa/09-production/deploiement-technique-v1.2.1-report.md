# Rapport Déploiement Technique — Release `1.2.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-27 |
| Heure UTC | 08:57–08:59 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.2.1` |
| Tag déployé | `sha-47172297` |
| Digest Web (nginx) | `sha256:ce9564195cb887b2db254d54003057b3a0e056a1eb4e4c87ba1947f664172cd0` |
| Digest API | `sha256:eb6e362b1e76bc69669e71a2f0f7877011480247fd55972322038d47af703b52` |
| Production précédente | `1.2.0` — `sha-5bf187af` |
| Verdict déploiement | **PASS — services UP, digests conformes** |
| `PRODUCTION_DEPLOYED` | Non atteint — réservé à la validation finale (Étape 6) |

## 1. Commandes exécutées

### 1.1 Mise à jour du dépôt

```
git -C /home/ubuntu/loyertracker pull --ff-only origin main
```

Résultat : fast-forward `14a672c` → `4717229` (commits documentaires CGPA, code applicatif inchangé).

### 1.2 Déploiement

```
LOYERTRACKER_TAG=sha-47172297 \
  docker compose \
    --project-directory /home/ubuntu/loyertracker \
    -f /home/ubuntu/loyertracker/docker-compose.yml \
    -f /home/ubuntu/loyertracker/docker-compose.prod.yml \
    up -d --pull always nginx
```

**Nota bene — divergence de périmètre détectée et documentée :**

Le plan d'exécution prévoyait la recréation du service `nginx` seul. Docker Compose a également
recréé `loyertracker-api-1` pour les raisons suivantes :

1. `--pull always` a tiré toutes les images du projet (y compris `loyertracker-api:sha-47172297`).
2. L'image `api` à `sha-47172297` a un digest différent de l'image `api` à `sha-5bf187af` — même
   code Java backend, mais build CI distinct (layers de base potentiellement mis à jour par la CI).
3. `nginx` dépend de `api` (`depends_on: api:service_started`) — Docker Compose a recréé `api` en
   détectant un changement de digest, puis `nginx`.

**Impact fonctionnel : nul.** Le code Java backend (`api`) est identique entre `1.2.0` et `1.2.1`
(seul `dashboard.component.ts` a changé). La recréation du conteneur `api` avec le tag `sha-47172297`
est sûre — même application, même configuration, même Flyway (15/15 inchangé).

### 1.3 Sortie Docker Compose (extrait significatif)

```
keycloak Pulled       ← image existante, non recréée
postgres Pulled       ← image existante, non recréée
nginx Pulled          ← nouvelle image sha-47172297 (web Angular)
api Pulled            ← nouvelle image sha-47172297 (api Java — même code)
Container loyertracker-postgres-1  Running
Container loyertracker-keycloak-1  Running
Container loyertracker-api-1  Recreate → Recreated → Started
Container loyertracker-nginx-1  Recreate → Recreated → Started
```

Warning orphans (prometheus, alertmanager, pushgateway, blackbox) : attendu — ces conteneurs
sont gérés par `docker-compose.monitoring.yml`, distinct du projet principal.

## 2. Contrôles post-déploiement

### 2.1 État des conteneurs

| Conteneur | Statut | Restart | Image |
|---|---|---|---|
| `loyertracker-nginx-1` | `(healthy)` — Up | 0 | `loyertracker-web:sha-47172297` |
| `loyertracker-api-1` | `(healthy)` — Up | 0 | `loyertracker-api:sha-47172297` |
| `loyertracker-keycloak-1` | `(healthy)` — Up 30+ min | 0 | inchangé |
| `loyertracker-postgres-1` | `(healthy)` — Up 30+ min | 0 | inchangé |
| `loyertracker-prometheus-1` | Up 30+ min | 0 | inchangé |
| `loyertracker-alertmanager-1` | Up 30+ min | 0 | inchangé |
| `loyertracker-pushgateway-1` | Up 30+ min | 0 | inchangé |
| `loyertracker-blackbox-1` | Up 30+ min | 0 | inchangé |

**8/8 conteneurs Up — 4/4 `(healthy)` — restart count 0.**

### 2.2 Vérification des digests GHCR

| Service | Digest observé | Digest dossier candidat |
|---|---|---|
| `nginx` (web Angular) | `sha256:ce9564195cb887b2db254d54003057b3a0e056a1eb4e4c87ba1947f664172cd0` | ✅ Conforme |
| `api` (Java backend) | `sha256:eb6e362b1e76bc69669e71a2f0f7877011480247fd55972322038d47af703b52` | ✅ Conforme |

### 2.3 Applicatif

| Contrôle | Résultat |
|---|---|
| `GET https://localhost:18443/` | ✅ HTTP 200 (web Angular servi) |
| `GET /api/actuator/health` | ✅ `{"status":"UP"}` |

### 2.4 Observabilité

| Contrôle | Résultat |
|---|---|
| Prometheus — 5 cibles `up` | ✅ prometheus, pushgateway, loyertracker-api, blackbox-keycloak, blackbox-postgres |
| Alertmanager — alertes actives | ⚠️ 1 alerte `BackupHeartbeatMissing` — voir §3 |

## 3. Alerte `BackupHeartbeatMissing` — analyse et verdict

**Nature :** `absent(loyertracker_backup_last_success_epoch)` — la métrique de heartbeat de
sauvegarde est absente du Pushgateway.

**Cause identifiée :** pré-existante, non liée au déploiement `1.2.1`.

1. Le serveur `loyertracker-prod-server` a redémarré peu avant la fenêtre Préflight (environ
   07h30–08h00 UTC, constaté lors de l'Étape 4 : uptime 22 min à 08:49 UTC).
2. Pushgateway, étant volatile (pas de fichier de persistance configuré), a perdu la métrique
   `loyertracker_backup_last_success_epoch` lors du redémarrage.
3. Le cron de backup tourne à 02:15 UTC — il n'a pas encore repassé depuis le redémarrage du
   serveur.
4. La règle Prometheus `BackupHeartbeatMissing` comporte un délai `for` avant de déclencher —
   c'est pourquoi l'alerte n'était pas présente lors du contrôle Préflight (08:49 UTC) et a
   déclenché à 08:57 UTC (~30 min après le redémarrage du serveur).

**Vérification :** l'alerte était absente au Préflight (Étape 4, 08:49 UTC) et a démarré à
08:57 UTC — 12 secondes APRÈS le lancement de la commande de déploiement. La corrélation
temporelle est une coïncidence ; la cause est le redémarrage du serveur.

**Verdict : alerte hors périmètre déploiement `1.2.1`.** L'alerte se résoudra automatiquement
lors de la prochaine exécution du cron de backup (02:15 UTC du 2026-06-28) ou d'un backup manuel
avec push Pushgateway.

**Traçabilité :** cette alerte est documentée ici. Elle ne bloque pas la progression vers
l'Étape 6 (Validation finale).

## 4. Verdict déploiement

| Critère | Statut |
|---|---|
| git pull fast-forward réussi | ✅ PASS |
| Images GHCR tirées (`sha-47172297`) | ✅ PASS |
| Digests `nginx` et `api` conformes au dossier candidat | ✅ PASS |
| `loyertracker-nginx-1` `(healthy)`, restart=0 | ✅ PASS |
| `loyertracker-api-1` `(healthy)`, restart=0 | ✅ PASS |
| `postgres`, `keycloak` inchangés | ✅ PASS |
| Web app HTTP 200 | ✅ PASS |
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus 5/5 cibles `up` | ✅ PASS |
| Alertmanager | ⚠️ 1 alerte pré-existante (hors périmètre — cf. §3) |

**Déploiement technique PASS.** Tag `sha-47172297` actif en Production.

`PRODUCTION_DEPLOYED` non atteint — réservé à la validation finale + smoke 47/0 (Étape 6).

## 5. Rollback disponible

| Procédure | Commande |
|---|---|
| Rollback `1.2.1` → `1.2.0` | `LOYERTRACKER_TAG=sha-5bf187af docker compose --project-directory /home/ubuntu/loyertracker -f docker-compose.yml -f docker-compose.prod.yml up -d nginx api` |
| pg_restore requis | **Non** — aucune migration entre `1.2.0` et `1.2.1` |
