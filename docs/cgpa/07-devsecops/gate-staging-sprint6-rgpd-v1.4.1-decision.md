# Gate Staging — Sprint 6 (US-70 RGPD + US-72 CSP Nginx)

| Champ | Valeur |
|---|---|
| Date | 2026-07-01 |
| Type | Sprint — nouvelles fonctionnalités RGPD + durcissement CSP |
| Périmètre | US-70 (export/effacement locataire, 5 pts), US-72 (CSP Nginx, 1 pt) |
| Tag déployé | `sha-08b366fa` |
| Digest API | `sha256:865dd686f76c90d514a26056ed7d6ad248ad5dd6c46d8776e88c68a144d80520` |
| Digest Web | `sha256:a7c74954700f300da1e5b40f104087da4c3bb629f0269aba0c1703b07d612b3e` |
| Environnement | `ai-test-server` — `loyertracker-staging_*` (Staging mutualisé) |
| Décision | **GO — `STAGING_DEPLOYED`** |
| Plan Sprint | `docs/cgpa/07-devsecops/sprint6-plan.md` |
| PR | #123 (fusionnée sur `main`, merge commit `08b366f`) |

## 1. Périmètre

US-70 : `GET /api/bailleurs/export` (export JSON scopé `bailleurId`), `DELETE
/api/biens/{bienId}/baux/{bailId}/locataire` (anonymisation PII locataire, `locataire_nom` →
`"[anonymisé]"`, `locataire_email` → `null`, traçage `audit_log` action `EFFACEMENT_LOCATAIRE`).
US-72 : durcissement CSP Nginx (`script-src 'self'`, `font-src 'self'`, `object-src 'none'`,
`base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'`). **Aucune migration Flyway**
(rang max inchangé, V18).

## 2. Contrôle STG-ISOL-01

Le déploiement `sha-08b366fa` a été exécuté hors de cette session (confirmé par le PO). Cette
vérification porte sur l'état constaté après déploiement, comparé à la référence documentée
(`docs/staging-state.md` §8, dernier état connu : 8 conteneurs `loyertracker-staging-*` sur
`sha-98afa99a`).

### État constaté (2026-07-01 ~08:25 UTC, live)

| Conteneur | État |
|---|---|
| `loyertracker-staging-nginx-1` | Up 14 min (healthy) — `sha-08b366fa` |
| `loyertracker-staging-api-1` | Up 14 min (healthy) — `sha-08b366fa` |
| `loyertracker-staging-postgres-1` | Up 2 h (healthy), non redémarré |
| `loyertracker-staging-keycloak-1` | Up 2 h (healthy), non redémarré |
| `loyertracker-staging-alertmanager-1` | Up 2 h, inchangé |
| `loyertracker-staging-pushgateway-1` | Up 2 h, inchangé |
| `loyertracker-staging-prometheus-1` | Up 2 h, inchangé |
| `loyertracker-staging-blackbox-1` | Up 2 h, inchangé |
| `nginx-proxy-manager` (autre projet, ressource mutualisée) | Up 2 h, inchangé |

Restart count = 0 sur `api`/`nginx`/`postgres`/`keycloak`. Réseau dédié
`loyertracker-staging_loyertracker-net` et volumes `loyertracker-staging_postgres-data` /
`loyertracker-staging_prometheus-data` namespacés, aucun réseau/volume d'un autre projet touché.
Aucune commande Docker globale utilisée pour les vérifications (ciblage exclusif des conteneurs
`loyertracker-staging-*`).

**Verdict STG-ISOL-01 : PASS.**

## 3. Contrôles post-déploiement

| Contrôle | Résultat |
|---|---|
| 8/8 conteneurs `(healthy)`/`Up` | ✅ PASS |
| Restart count = 0 (api/nginx/postgres/keycloak) | ✅ PASS |
| Tag `sha-08b366fa` actif (API + Web) | ✅ PASS |
| Digests GHCR conformes | ✅ PASS |
| Flyway | ✅ PASS — « Successfully validated 18 migrations », rang max 18 inchangé (aucune nouvelle migration Sprint 6) |
| Actuator `{"status":"UP"}` | ✅ PASS |
| CSP Nginx (US-72) | ✅ PASS — `content-security-policy` observé : `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'` |

## 4. Smoke test

```
BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.staging.yml bash infra/smoke/smoke-stack.sh
```

**Résultat : 47 PASS / 0 FAIL** (2026-07-01, port interne `18443`).

**Réserve RSV-S6-01 toujours ouverte** : le smoke script (47 items) ne couvre pas encore les
2 nouveaux endpoints RGPD — non bloquant pour ce Gate (voir §5).

## 5. Vérification comportementale US-70 / US-72

- **`GET /api/bailleurs/export`** : appelé en live avec un JWT réel (`jordan.test@loyerpro.org`,
  échafaudage `directAccessGrants` temporaire, révoqué après usage) → **200**, corps JSON
  `{bailleurId, biens[], dateExport}` scopé au bailleur authentifié.
- **`DELETE /api/biens/{bienId}/baux/{bailId}/locataire`** : non rejouée manuellement en staging
  (échec non bloquant de création d'un bien jetable de test, 400 — payload de test invalide, aucune
  donnée créée ni modifiée). La logique d'anonymisation est validée par `RgpdIntegrationTest`
  (236 lignes, Testcontainers), intégralement verte en CI sur `08b366f` : 204, `locataire_nom =
  "[anonymisé]"`, `locataire_email = null`, écriture `audit_log` action `EFFACEMENT_LOCATAIRE`.
  Confirmation *live* de ce endpoint spécifique reportée à une prochaine vérification (non
  bloquante : couverture par tests d'intégration jugée suffisante pour ce Gate).
- **Présence des routes dans l'image déployée** : confirmée par inspection du jar embarqué
  (`RgpdController.class` → `/bailleurs/export`, `/biens/{bienId}/baux/{bailId}/locataire`).
- **CSP (US-72)** : toutes les directives cibles présentes (cf. §3).

## 6. Checklist Gate Staging v5.3 / v5.4.1

| Critère | Statut |
|---|---|
| Plan d'Exécution approuvé | ✅ `sprint6-plan.md` |
| Commit/artefact candidat identifié | ✅ `sha-08b366fa` (merge PR #123), digests vérifiés |
| Build CI stable | ✅ SUCCESS toutes jobs (Backend, Frontend, CodeQL Java/Kotlin + JS/TS, Sécurité, Packaging) |
| Tests unitaires/intégration | ✅ `RgpdIntegrationTest` + suite complète, 0 FAIL |
| Migrations DB | ✅ Aucune nouvelle migration — V18 rang max inchangé |
| Secrets non exposés | ✅ `.env` hors dépôt |
| Rollback Staging identifié | ✅ `sha-98afa99a` (tag précédent, sans migration à rejouer) |
| STG-ISOL-01 | ✅ **PASS** (constaté après déploiement) |
| Smoke Staging | ✅ **47 PASS / 0 FAIL** |
| `docs/staging-state.md` | Mis à jour (§8) |

## 7. Décision

**GO — `STAGING_DEPLOYED` atteint (Sprint 6, `sha-08b366fa`).**

- `PRODUCTION_READY` : non atteint — Gate Production distinct requis.
- `PRODUCTION_DEPLOYED` : non atteint.
- Réserve maintenue : **RSV-S6-01** (smoke script à étendre aux 2 nouveaux endpoints RGPD avant Gate
  Production, ou confirmation live du `DELETE` locataire à défaut).

**Prochaine étape autorisée :** Gate Production Sprint 6, sous décision CDO/Release Manager
distincte.
