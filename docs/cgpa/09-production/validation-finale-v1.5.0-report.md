# Rapport Validation Finale — Release `1.5.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-01 |
| `PRODUCTION_DEPLOYED` | **2026-07-01 11:02 UTC** |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Tag en Production | `sha-08b366fa` |
| Digest API | `sha256:865dd686f76c90d514a26056ed7d6ad248ad5dd6c46d8776e88c68a144d80520` |
| Digest Web | `sha256:a7c74954700f300da1e5b40f104087da4c3bb629f0269aba0c1703b07d612b3e` |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint** |

## 1. Smoke test Production

### Prérequis Keycloak

`bailleur-test@test.local` (`43443d1e`) : réactivé temporairement (`enabled=true`), pattern
identique à `1.2.1`/`1.3.0`/`1.4.0` — redésactivé après le run (§3).

### Smoke — 59 PASS / 0 FAIL (10:47 UTC)

```
BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml bash infra/smoke/smoke-stack.sh
```

| Section | Résultat |
|---|---|
| 0. Sanity (stack, Flyway V1-V18, pool loyertracker_api NOBYPASSRLS) | **5/5 PASS** |
| 1. JWT Keycloak réel bailleur via Nginx TLS | **2/2 PASS** |
| 2. Parcours bailleur (inscription 409, patrimoine, bien, bail) | **4/4 PASS** |
| 3. Invitation → acceptation Admin API → JWT gestionnaire | **4/4 PASS** |
| 4. Affectation patrimoine, échéances SECURITY DEFINER, pointage, honoraires | **9/9 PASS** |
| 5. Alertes (PREAVIS J+75), audit bailleur | **6/6 PASS** |
| 6. Scoping gestionnaire | **4/4 PASS** |
| 7. Isolation cross-tenant live (2e bailleur) | **9/9 PASS** |
| 9. RGPD (US-70) : export, isolation, effacement locataire | **12/12 PASS** |
| 10. Garde-fous AuthN/ports | **2/2 PASS** |
| **Total** | **59 PASS / 0 FAIL** ✅ |

Échafaudage révoqué par le script : `directAccessGrants OFF` (loyertracker-spa).

### Vérification comportementale US-70 (Sprint 6) en Production

Le smoke §9 a exercé en conditions réelles les deux endpoints RGPD ajoutés par `1.5.0` :
- `GET /api/bailleurs/export` : 200, JSON scopé (bailleur 1 voit son bien, bailleur 2 ne le
  voit pas — isolation confirmée).
- `DELETE /api/biens/{bienId}/baux/{bailId}/locataire` : 403 pour le rôle `GESTIONNAIRE`, 204
  pour le `BAILLEUR` ; `locataireNom` anonymisé, `locataireEmail` effacé ; entrée `audit_log`
  action `EFFACEMENT_LOCATAIRE` confirmée.

### Détail Flyway (§0)

**18 migrations appliquées (V1→V18)** — inchangé par rapport à `1.4.0` (aucune migration `1.5.0`).

## 2. Nettoyage transactionnel

### Scope identifié

En plus des comptes créés par ce run, un orphelin d'une session antérieure (RUN_ID
`1782832512`, non nettoyé lors d'une exécution précédente) a été détecté et purgé dans la
même passe.

| Bailleur smoke | ID bailleur (table) | Action |
|---|---|---|
| `bailleur-test@test.local` | `c7296c69` | Données smoke purgées, bailleur conservé puis désactivé KC |
| `bailleur2-smoke-1782899233@test.local` (run courant) | `aebd8813` | Données + bailleur supprimés (DB + KC) |
| `bailleur2-smoke-1782832512@test.local` (orphelin) | `628338fe` | Données + bailleur supprimés (DB + KC) |
| `gest-smoke-1782899233@test.local` (run courant) | gestionnaire table | Supprimé (DB + KC) |
| `gest-smoke-1782832512@test.local` (orphelin) | gestionnaire table | Supprimé (DB + KC) |

### SQL exécuté (transaction atomique)

Suppression dans l'ordre FK : `honoraire` → `garantie` → `paiement` → `alerte` → `audit_log` →
`affectation` → `invitation` → `bail` → `bien` → `patrimoine` → `gestionnaire` → `bailleur`
(bailleur2 uniquement), filtré par `bailleur_id IN (...)` / `gestionnaire_id IN (...)`.

### Compteurs post-nettoyage

| Table | Avant | Après | Attendu |
|---|---|---|---|
| bailleurs | 4 | **2** | jptshilombo + bailleur-test ✅ |
| gestionnaires | 3 | **1** | gest-e9-e9test@test.local (réel) ✅ |
| patrimoines | 5 | **1** | Patrimoine principal (jptshilombo) ✅ |
| biens | 5 | **3** | Biens jptshilombo ✅ |
| baux | 5 | **3** | Baux jptshilombo ✅ |
| affectations | 2 | **0** | ✅ |
| paiements | 94 | **75** | Paiements jptshilombo ✅ |
| honoraires | 19 | **0** | ✅ |
| alertes | 93 | **81** | Alertes jptshilombo ✅ |
| audit_log | 7 | **2** | Écritures jptshilombo ✅ |
| invitations | 2 | **0** | ✅ |
| garanties | 1 | **1** | Garantie jptshilombo ✅ |

Compteurs cohérents avec la baseline `1.3.0`/`1.4.0` (75 paiements, 81 alertes) — aucune donnée
réelle affectée.

### Keycloak

| Compte | Action | Résultat |
|---|---|---|
| `bailleur2-smoke-1782899233` (`2bdea538`) | Supprimé | ✅ |
| `bailleur2-smoke-1782832512` (`034aa9b0`, orphelin) | Supprimé | ✅ |
| `gest-smoke-1782899233` (`fbda87e6`) | Supprimé | ✅ |
| `gest-smoke-1782832512` (`9cd36a51`, orphelin) | Supprimé | ✅ |
| `bailleur-test@test.local` (`43443d1e`) | Désactivé (`enabled=false`) | ✅ |
| `directAccessGrantsEnabled` (loyertracker-spa) | Révoqué par le script | ✅ `false` confirmé |

## 3. Persistance `.env`

```
LOYERTRACKER_TAG=sha-08b366fa
```

Mis à jour sur `loyertracker-prod-server` (`sed -i` sur `/home/ubuntu/loyertracker/.env`).
Backup `.env.bak-pre-1.5.0` créé (permissions 600) avant modification.
Vérification : `grep LOYERTRACKER_TAG .env` → `sha-08b366fa` ✅, permissions 600 ✅

## 4. Contrôles post-persistance finaux

| Contrôle | Résultat |
|---|---|
| `LOYERTRACKER_TAG=sha-08b366fa` dans `.env` | ✅ |
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ |
| Actuator `{"status":"UP"}` | ✅ |
| Prometheus 5/5 `up` | ✅ |
| Alertmanager `[]` | ✅ |

## 5. `PRODUCTION_DEPLOYED` — atteint

**2026-07-01 11:02 UTC** — release `1.5.0` (`sha-08b366fa`) déployée et validée en Production.

Prochaines étapes :
- Hypercare T0 (immédiat), T+12, T+24
- Décision CDO → clôture `1.5.0`
- Post-clôture : CHANGELOG `[Non publié]` → `[1.5.0] — 2026-07-01`, mise à jour
  `project-state.md` et `prod-state.md`, promotion `release-notes-v1.5.0.md`
