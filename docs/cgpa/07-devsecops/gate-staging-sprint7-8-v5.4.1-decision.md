# Gate Staging — Sprint 7 (Patrimoine enrichi, US-90) + Sprint 8 (Money/Devise, US-92/93)

| Champ | Valeur |
|---|---|
| Date | 2026-07-02 |
| Type | Sprints combinés — Staging rattrape deux sprints déjà fusionnés sur `main` (Sprint 7 n'avait jamais été déployé en Staging) |
| Périmètre | Sprint 7 EP-10 : US-90 (7 champs Patrimoine + `adresse` obligatoire, migration V19). Sprint 8 EP-11 : US-92 (VO `Money`, correctif devise documents), US-93 (devise Paiements/Honoraires) |
| Tag déployé | `sha-2da27182` |
| Digest API | `sha256:ecdd14084db6fcd5a556dac5ec8f6c62ee0c0303fce4475c2ee0fb8e959b1f3f` |
| Digest Web | `sha256:64263317fd09874f910a309e22b09e748529eb671b2202a76f643667bde920aa` |
| Environnement | `ai-test-server` — `loyertracker-staging_*` (Staging mutualisé) |
| Décision | **GO — `STAGING_DEPLOYED`** |
| Plans | `docs/cgpa/06-planification-agile/plan-execution-evolutions-ep10-ep13.md` (Sprint 7 §Sprint 7, Sprint 8 §Sprint 8) |
| PR | #143 (Sprint 8, merge commit `77549a9`), #144 (doc-only), #145 (correctif smoke test) — Sprint 7 déjà fusionné avant cette session (PR #131) |

## 1. Périmètre et écart de séquencement

Le Staging était figé sur `sha-08b366fa` (Sprint 6, dernier Gate Staging du 2026-07-01). Entre
temps, **Sprint 7 (PR #131) a été fusionné sur `main` sans passer son propre Gate Staging**. Ce
Gate promeut donc les deux sprints en un seul déploiement (artefact HEAD unique) : c'est la seule
option techniquement possible sans revenir en arrière sur `main`. Écart qualifié et accepté par le
PO au démarrage de ce Gate (session du 2026-07-02).

**Migration Flyway V19** (Sprint 7, US-90) : `patrimoine.adresse` devient `NOT NULL` (backfill
générique placeholder `"Adresse à renseigner"` pour tout patrimoine sans adresse), ajout de 7
colonnes optionnelles. Migration *forward*, non destructive, déjà testée en CI
(`SchemaMigrationTest`, `PatrimoineIntegrationTest`).

**Aucune migration** pour Sprint 8 (`bail.devise` existe depuis V17 ; aucune duplication de
colonne introduite, ADR-13).

## 2. Contrôle STG-ISOL-01

### État "avant" (2026-07-02 ~09:45 UTC, avant déploiement)

9 conteneurs : `loyertracker-staging-{alertmanager,api,blackbox,keycloak,nginx,postgres,
prometheus,pushgateway}-1` (tag `sha-08b366fa` sur api/nginx) + `nginx-proxy-manager` (autre
projet, ressource mutualisée). Réseau dédié `loyertracker-staging_loyertracker-net`, volumes
dédiés `loyertracker-staging_postgres-data`/`loyertracker-staging_prometheus-data` — aucun réseau
ni volume d'un autre projet de l'hôte référencé par le Compose du projet.

### Déploiement exécuté

```bash
cd ~/loyertracker && git pull origin main
sed -i 's/^LOYERTRACKER_TAG=.*/LOYERTRACKER_TAG=sha-2da27182/' .env
docker compose -f docker-compose.staging.yml pull api nginx
docker compose -f docker-compose.staging.yml up -d
```

Aucune commande Docker globale utilisée. `up -d` a signalé des conteneurs "orphelins"
(overlay monitoring `alertmanager`/`prometheus`/`blackbox`/`pushgateway`, non inclus dans cette
invocation ciblée) sans flag `--remove-orphans` : **ils n'ont pas été touchés**.

### État "après" (2026-07-02 ~10:16 UTC, après smoke)

9 conteneurs, identiques en nombre et préfixe à l'état "avant" (8× `loyertracker-staging-*`,
tag `sha-2da27182` sur `api`/`nginx` recréés ; `postgres`/`keycloak`/monitoring non redémarrés) +
`nginx-proxy-manager` inchangé. `nginx-proxy-manager` : `Up 3h`, non affecté.
Restart count = 0 sur `api`/`nginx`/`postgres`/`keycloak`.

**Verdict STG-ISOL-01 : PASS.**

## 3. Contrôles post-déploiement

| Contrôle | Résultat |
|---|---|
| 8/8 conteneurs `(healthy)`/`Up` | ✅ PASS |
| Restart count = 0 (api/nginx/postgres/keycloak) | ✅ PASS |
| Tag `sha-2da27182` actif (API + Web) | ✅ PASS |
| Digests GHCR conformes (vérifiés `docker buildx imagetools inspect` avant pull) | ✅ PASS |
| Flyway | ✅ PASS — « Successfully applied 1 migration... now at version v19 », migration V19 appliquée sans erreur |
| Actuator `{"status":"UP"}` | ✅ PASS |
| `/healthz` (port HTTP alternatif 18080) | ✅ PASS — `ok` |
| `/api/actuator/prometheus` public (via Nginx TLS) | ✅ PASS — 404 (bloqué) |

## 4. Smoke test

Premier essai — **4 FAIL** : `POST /api/patrimoines` (400, payload sans `adresse`, devenue
`@NotBlank` depuis US-90) et 3 échecs en cascade (bien/bail/affectation/paiements dépendant du
patrimoine créé). Cause : le script `infra/smoke/smoke-stack.sh` n'avait jamais été exercé contre
le code Sprint 7 réel (Sprint 7 jamais déployé en Staging avant ce Gate) — seul le compteur Flyway
avait été ajusté au merge (`c8a799c`), pas le payload de création de patrimoine. **Correctif du
script uniquement** (aucun code applicatif touché), PR #145, CI verte, fusionnée, script
resynchronisé sur `ai-test-server` (`git pull`).

Deuxième essai, après correctif :

```
BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.staging.yml bash infra/smoke/smoke-stack.sh
```

**Résultat : 59 PASS / 0 FAIL** (2026-07-02, port interne `18443`).

Couverture confirmée en conditions réelles : inscription, patrimoine (avec `adresse`
désormais obligatoire), bien, bail, invitation→acceptation, affectation 8 %, échéances
(9 créées, `SECURITY DEFINER`), pointage, honoraire (72,00 € — 8 % de 900 encaissés, formaté par
le nouveau VO `Money`), alertes dont PREAVIS, audit (45 écritures), scoping gestionnaire,
isolation cross-tenant live (2e bailleur, 0 fuite), RGPD US-70 (export scopé, effacement
locataire 204, anonymisation confirmée), garde-fous AuthN/ports.

**Vérification visuelle US-92/93** : non exécutée dans cette session (pas de test manuel de
téléchargement de quittance USD/CDF ni de capture d'écran des panneaux Paiements/Honoraires) —
couverture jugée suffisante pour ce Gate via `MoneyTest`/`DocumentHtmlBuilderTest` (CI, 3 devises
paramétrées) + honoraire EUR confirmé formaté en Staging (`72.00` → recalcul correct, format
observable uniquement sur documents PDF/UI, non vérifié visuellement ici). **Réserve RSV-S7-8-01**
: confirmation visuelle USD/CDF (quittance PDF + UI Paiements/Honoraires) recommandée avant Gate
Production, non bloquante pour ce Gate Staging (couverture automatisée jugée suffisante).

## 5. Checklist Gate Staging v5.4.1

| Critère | Statut |
|---|---|
| Plan d'Exécution approuvé | ✅ `plan-execution-evolutions-ep10-ep13.md` |
| Commit/artefact candidat identifié | ✅ `sha-2da27182` (HEAD `main`, PR #143+#144), digests vérifiés |
| Build CI stable | ✅ SUCCESS toutes jobs (Backend, Frontend, CodeQL Java/Kotlin + JS/TS, Sécurité, Packaging) — PR #143, #144, #145 |
| Tests unitaires/intégration | ✅ 122/122 backend (`MoneyTest`, `DocumentHtmlBuilderTest` paramétré, `PatrimoineIntegrationTest`), 63/63 frontend |
| Migrations DB | ✅ V19 appliquée sans erreur, backfill générique conforme au plan Sprint 7 |
| Secrets non exposés | ✅ `.env` hors dépôt |
| Rollback Staging identifié | ✅ `sha-08b366fa` (tag précédent) — **note** : rollback image seul insuffisant si V19 doit être défaite (schéma), cf. §3 runbook |
| STG-ISOL-01 | ✅ **PASS** (avant/après) |
| Smoke Staging | ✅ **59 PASS / 0 FAIL** (après correctif script PR #145) |
| `docs/staging-state.md` | À mettre à jour (§2, §8) |

## 6. Décision

**GO — `STAGING_DEPLOYED` atteint (Sprint 7 + Sprint 8 combinés, `sha-2da27182`).**

- `PRODUCTION_READY` : non atteint — Gate Production distinct requis (Sprint 7 et/ou Sprint 8).
- `PRODUCTION_DEPLOYED` : non atteint.
- Réserve **RSV-S7-8-01** : confirmation visuelle USD/CDF (quittance PDF + panneaux Paiements/
  Honoraires) recommandée avant Gate Production — non bloquante ici.
- Rappel Sprint 7 (déjà noté dans `docs/project-state.md`) : adresse réelle du patrimoine
  Production (1 patrimoine, `adresse IS NULL`) à appliquer via `PUT /api/patrimoines/{id}` **au
  Gate Production**, non codée en dur dans la migration.

**Prochaine étape autorisée :** Gate Production (Sprint 7 et/ou Sprint 8), sous décision
CDO/Release Manager distincte.
