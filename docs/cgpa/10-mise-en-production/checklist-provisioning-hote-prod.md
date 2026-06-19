# Check-list de provisioning — Hôte Production dédié (LoyerTracker `1.0.0`)

> Livrable CGPA v5.2 préparatoire au **Gate 10 — Mise en production**
> (`setup-cgpa/docs/cgpa/gates/gate-10-mise-en-production.md`,
> `setup-cgpa/docs/cgpa/workflows/workflow-preparation-production.md`, étapes 6→8).
> Cible arbitrée par le PO le 2026-06-19 : **hôte Production dédié** distinct de staging (ENV-01 strict,
> Staging ≠ Production), domaine `loyertracker.loyerpro.org`, stack `loyertracker-prod`.
> Comble la « section go-live production » signalée en réserve du runbook (Production Readiness Review,
> axe Runbooks 3/4) et adresse les réserves **RG-09-1** (capacity) / **RG-09-2** (support) du Gate 09.
> Ancrée sur les artefacts réels : `docker-compose.prod.yml`, `docker-compose.monitoring.yml`, clés `.env`,
> `infra/smoke/smoke-stack.sh`, `infra/backup/`. Date : **2026-06-19**.

## 0. Dépendances bloquantes (à confirmer avant toute étape)

- [ ] **Hôte/VM Production dédié** distinct de `ai-test-server` — IP publique + accès SSH.
- [ ] **Autorité DNS** sur `loyerpro.org` pour créer `loyertracker.loyerpro.org`.
- [ ] **Sizing cible défini** (lève **RG-09-1**) : baseline PME ex. **2 vCPU / 4 Go RAM / 40 Go SSD**
      (PostgreSQL + Keycloak + API + Nginx + overlay monitoring). À ajuster selon le volume.
- [ ] **Canal d'astreinte/incident défini** (lève **RG-09-2**) : URL webhook réelle + point de contact.

## 1. Hôte & système

- [ ] OS à jour, fuseau **UTC**, NTP actif.
- [ ] Docker Engine + plugin Compose v2 installés.
- [ ] Clone du dépôt sur l'hôte au commit de `main` (post-merge Gate 09).
- [ ] **Pare-feu** : exposer **uniquement 80/443**. PostgreSQL, Keycloak, Prometheus, Pushgateway et
      Alertmanager **non publiés** (réseau Docker interne / loopback) — parité staging.

## 2. DNS & TLS

- [ ] Enregistrement **`A loyertracker.loyerpro.org → <IP prod>`** (TTL 300).
- [ ] **TLS** sur le domaine public, au choix :
  - reverse-proxy mutualisé (pattern staging : nginx-proxy-manager + Let's Encrypt), **ou**
  - certbot/Let's Encrypt directs → certificats déposés dans **`infra/nginx/certs/`** (montés en RO par
    `docker-compose.prod.yml`).
- [ ] Keycloak `start --optimized` **exige HTTPS en amont + hostname fixe** : vérifier que `KC_HOSTNAME`
      correspond au domaine public.

## 3. Secrets & `.env` production (jamais versionné, perms `600`)

**Secrets neufs — ne PAS réutiliser ceux de staging :**

- [ ] `POSTGRES_PASSWORD`, `DB_APP_PASSWORD`, `DB_BATCH_PASSWORD`
- [ ] `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_API_CLIENT_SECRET`
- [ ] `ALERTMANAGER_WEBHOOK_URL` (canal d'astreinte réel — RG-09-2)

**Clés réglées au domaine public :**

- [ ] `KC_HOSTNAME=loyertracker.loyerpro.org`
- [ ] `KEYCLOAK_ISSUER_URI=https://loyertracker.loyerpro.org/auth/realms/<realm>`
- [ ] `KEYCLOAK_JWK_SET_URI=` **interne** (reste sur le réseau Docker, comme en staging)
- [ ] `APP_CORS_ALLOWED_ORIGIN=https://loyertracker.loyerpro.org`
- [ ] `APP_INVITATION_BASE_URL=https://loyertracker.loyerpro.org`
- [ ] `KEYCLOAK_ADMIN_BASE_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_API_CLIENT_ID`, `KEYCLOAK_ADMIN`
- [ ] `DB_URL`, `DB_APP_USER`, `DB_BATCH_USER`, `POSTGRES_DB`, `POSTGRES_USER`
- [ ] `LOYERTRACKER_TAG=sha-73359c5c` (tag immuable de `1.0.0` — **jamais `latest`**)
- [ ] `REGISTRY_BASE=ghcr.io/jptshilombo` (défaut) ; `WEB_HTTP_PORT=80` / `WEB_HTTPS_PORT=443` (hôte dédié)
- [ ] `PUSHGATEWAY_URL=http://127.0.0.1:9091` (loopback)

> ⚠️ **Ne pas** définir `KEYCLOAK_TEST_BAILLEUR_PASSWORD` ni seeder de compte de test en production
> (échafaudage réservé au smoke — cf. §8).

## 4. Realm Keycloak production

- [ ] Realm prod importé avec des **secrets distincts** de staging.
- [ ] `redirectUris` / `webOrigins` / `post.logout` = `https://loyertracker.loyerpro.org/*`.
- [ ] `pkce.code.challenge.method = S256`.
- [ ] Client service-account `loyertracker-admin` (rôles realm-management minimaux), secret hors dépôt.
- [ ] `directAccessGrants` **désactivé** (activé temporairement pour le smoke uniquement, puis révoqué — §8).

## 5. Déploiement de l'artefact `1.0.0`

- [ ] `docker login ghcr.io` (lecture des images privées).

```bash
export LOYERTRACKER_TAG=sha-73359c5c
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

- [ ] 4/4 services `healthy` ; Flyway V1→V10 appliqué (en admin) ; pool API sous `loyertracker_api`.

## 6. Observabilité & alerting (overlay)

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  -f docker-compose.monitoring.yml --profile monitoring up -d
```

- [ ] 4/4 cibles Prometheus `up` ; scrape interne 200 / public 404.
- [ ] `ALERTMANAGER_WEBHOOK_URL` injecté via `url_file` (secret hors dépôt) ; notification vers le canal réel.

## 7. Sauvegarde

- [ ] Cron installé : `infra/backup/install-cron.sh` (défaut `15 2 * * *`).
- [ ] Script ciblant la composition prod :
      `COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml` (heartbeat → Pushgateway).
- [ ] Un backup manuel exécuté + intégrité vérifiée (`pg_restore --list`).

## 8. Validation (AVANT ouverture aux vrais utilisateurs)

- [ ] **Smoke** ciblé prod :
      `BASE=https://loyertracker.loyerpro.org CACERT=… infra/smoke/smoke-stack.sh` → **46/0**.
  > ⚠️ Le smoke **crée des données** (parcours S01→S04). À exécuter **avant** l'ouverture publique, sur un
  > tenant jetable ; échafaudage `directAccessGrants` **révoqué** après preuve ; nettoyer les données de
  > test si nécessaire.
- [ ] **1 simulation d'incident** alerting (ex. `BackupHeartbeatMissing`) → FIRING→notifié→resolved sur le
      canal réel.
- [ ] **Rollback éprouvé** : redéploiement du `LOYERTRACKER_TAG` précédent (procédure runbook).

## 9. Go-live & traçabilité (lève **RR-2**)

- [ ] Exposition publique activée (Access List / auth selon besoin).
- [ ] **`docs/release-notes-v1.0.0.md` §5** renseignée : date/heure, tag déployé, opérateur, résultat,
      rollback disponible.
- [ ] **`docs/prod-state.md`** créé (miroir de `docs/staging-state.md`).
- [ ] **Gate 10 — Mise en production** statué + `docs/project-state.md` mis à jour.

## 10. Solde des réserves Gate 09

- [ ] **RG-09-1** (capacity) : sizing baseline acté + surveillance live (CPU / latence p99 / pool Hikari).
- [ ] **RG-09-2** (support) : processus incident minimal documenté (canal, contact, escalade).

---
*Livrable CGPA v5.2 préparatoire au Gate 10. Une fois l'hôte dédié provisionné (IP + SSH + DNS), l'exécution
se déroule en mode « commandes côté hôte » (cf. RR-1), puis le Gate 10 est statué et tracé dans
`docs/project-state.md`.*
