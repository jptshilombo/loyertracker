# Rapport Validation Finale — Release `1.6.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-02 |
| `PRODUCTION_DEPLOYED` | **2026-07-02 16:50 UTC** |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Tag en Production | `sha-2da27182` |
| Digest API | `sha256:ecdd14084db6fcd5a556dac5ec8f6c62ee0c0303fce4475c2ee0fb8e959b1f3f` |
| Digest Web | `sha256:64263317fd09874f910a309e22b09e748529eb671b2202a76f643667bde920aa` |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint** |

## 1. Smoke test Production

### Prérequis Keycloak

`bailleur-test@test.local` (`43443d1e`) : réactivé temporairement (`enabled=true`), pattern
identique à `1.2.1`/`1.3.0`/`1.4.0`/`1.5.0` — redésactivé après le run (§2).

### Smoke — 59 PASS / 0 FAIL

```
BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml bash infra/smoke/smoke-stack.sh
```

| Section | Résultat |
|---|---|
| 0. Sanity (stack, **Flyway V1-V19**, pool loyertracker_api NOBYPASSRLS) | **5/5 PASS** |
| 1. JWT Keycloak réel bailleur via Nginx TLS | **2/2 PASS** |
| 2. Parcours bailleur (inscription 409, patrimoine avec `adresse` obligatoire, bien, bail) | **4/4 PASS** |
| 3. Invitation → acceptation Admin API → JWT gestionnaire | **4/4 PASS** |
| 4. Affectation patrimoine, échéances SECURITY DEFINER, pointage, honoraires | **9/9 PASS** |
| 5. Alertes (PREAVIS J+75), audit bailleur | **6/6 PASS** |
| 6. Scoping gestionnaire | **4/4 PASS** |
| 7. Isolation cross-tenant live (2e bailleur) | **9/9 PASS** |
| 9. RGPD (US-70) : export, isolation, effacement locataire | **12/12 PASS** |
| 10. Garde-fous AuthN/ports | **2/2 PASS** |
| **Total** | **59 PASS / 0 FAIL** ✅ |

Échafaudage révoqué par le script : `directAccessGrants OFF` (loyertracker-spa), confirmé.

### Vérification comportementale US-90 (Sprint 7) en Production

Le smoke §2 a exercé `POST /api/patrimoines` avec `adresse` désormais obligatoire (`@NotBlank`
depuis V19) : création réussie (201) avec `adresse` fournie — comportement conforme au Gate
Staging (où l'omission d'`adresse` avait été détectée et corrigée dans le script, PR #145).

### Vérification comportementale US-92/93 (Sprint 8) en Production

Honoraire calculé au pointage : **72,00** (8 % de 900 encaissés, devise EUR par défaut du bail
smoke) — recalcul correct via le VO `Money`. **Vérification visuelle USD/CDF non exercée**
(aucun bail USD/CDF réel en Production) — réserve **RSV-S7-8-01** reportée, couverture jugée
suffisante par CI (`MoneyTest`/`DocumentHtmlBuilderTest` paramétrés 3 devises), non bloquante
pour `PRODUCTION_DEPLOYED`.

### Détail Flyway (§0)

**19 migrations appliquées (V1→V19)** — V19 confirmée live par le smoke (`Flyway : 19 migrations
appliquées`), cohérent avec le déploiement technique (`deploiement-technique-v1.6.0-report.md`).

## 2. Nettoyage transactionnel

### Scope identifié

Comptes créés par ce run (RUN_ID `1783010862`) — **aucun orphelin** d'une session antérieure
détecté (vérifié par requête ciblée avant nettoyage).

| Entité smoke | ID | Action |
|---|---|---|
| `bailleur-test@test.local` | `c7296c69` | Données smoke purgées (1 patrimoine, 1 bien, 1 bail + dépendances), bailleur conservé puis désactivé KC |
| `bailleur2-smoke-1783010862@test.local` | `110b8d69` (DB) / `bf41d4ab` (KC) | Données + bailleur supprimés (DB + KC) |
| `gest-smoke-1783010862@test.local` | `fedfb6f0` (DB) / `47e14c65` (KC) | Supprimé (DB + KC) |

### SQL exécuté (transaction atomique)

Suppression dans l'ordre FK : `honoraire` → `paiement` → `alerte` → `audit_log` → `affectation`
→ `invitation` → `bail` → `bien` → `patrimoine` → `gestionnaire` → `bailleur` (bailleur2
uniquement), filtré par `bailleur_id IN ('c7296c69-…', '110b8d69-…')` pour les tables à
`bailleur_id` direct, et par identifiants explicites pour `affectation`/`invitation`/`bail`/
`bien`/`patrimoine`/`gestionnaire` (aucune table `garantie` affectée — la garantie unique en
Production appartient à `jptshilombo`, non touchée).

```sql
BEGIN;
DELETE FROM honoraire   WHERE bailleur_id IN ('c7296c69-8ffb-48fb-8f4d-cc6c4198ede4','110b8d69-00ec-4d25-8d5e-636cfdf224c3');
DELETE FROM paiement    WHERE bailleur_id IN ('c7296c69-8ffb-48fb-8f4d-cc6c4198ede4','110b8d69-00ec-4d25-8d5e-636cfdf224c3');
DELETE FROM alerte      WHERE bailleur_id IN ('c7296c69-8ffb-48fb-8f4d-cc6c4198ede4','110b8d69-00ec-4d25-8d5e-636cfdf224c3');
DELETE FROM audit_log   WHERE bailleur_id IN ('c7296c69-8ffb-48fb-8f4d-cc6c4198ede4','110b8d69-00ec-4d25-8d5e-636cfdf224c3');
DELETE FROM affectation WHERE id = '4befbbee-9bdf-4916-b2bc-f5c432ef6aaa';
DELETE FROM invitation  WHERE id = 'acd37032-af69-456b-86bd-ff7ff4e941dd';
DELETE FROM bail        WHERE id = '4cc5bd93-cc12-4aa0-a971-8c3861f24174';
DELETE FROM bien        WHERE id = '290a21c3-2bec-4faf-b5be-949685b2c784';
DELETE FROM patrimoine  WHERE id IN ('fccd921f-471a-4fcc-939d-0dd03a0c8660','c91286fa-3d38-4f99-8af9-e25a8a3510f3');
DELETE FROM gestionnaire WHERE id = 'fedfb6f0-496f-4100-9ae3-f786762a3363';
DELETE FROM bailleur    WHERE id = '110b8d69-00ec-4d25-8d5e-636cfdf224c3';
COMMIT;
```

### Compteurs post-nettoyage

| Table | Avant | Après | Attendu |
|---|---|---|---|
| bailleurs | 3 | **2** | jptshilombo + bailleur-test ✅ |
| gestionnaires | 2 | **1** | gest-e9-e9test@test.local (réel) ✅ |
| patrimoines | 3 | **1** | Patrimoine principal (jptshilombo), adresse réelle appliquée ✅ |
| biens | 4 | **3** | Biens jptshilombo ✅ |
| baux | 4 | **3** | Baux jptshilombo ✅ |
| affectations | 1 | **0** | ✅ |
| paiements | 84 | **75** | Paiements jptshilombo ✅ |
| honoraires | 9 | **0** | ✅ |
| alertes | 87 | **81** | Alertes jptshilombo ✅ |
| audit_log | 5 | **2** | Écritures jptshilombo ✅ |
| invitations | 1 | **0** | ✅ |
| garanties | 1 | **1** | Garantie jptshilombo, inchangée ✅ |

Compteurs identiques à la baseline `1.5.0` (75 paiements, 81 alertes, 2 audit_log) — aucune
donnée réelle affectée, aucun résidu.

### Keycloak

| Compte | Action | Résultat |
|---|---|---|
| `bailleur2-smoke-1783010862` (`bf41d4ab`) | Supprimé | ✅ |
| `gest-smoke-1783010862` (`47e14c65`) | Supprimé | ✅ |
| `bailleur-test@test.local` (`43443d1e`) | Désactivé (`enabled=false`) | ✅ |
| `directAccessGrantsEnabled` (loyertracker-spa) | Révoqué (par le script + reconfirmé manuellement) | ✅ `false` |

## 3. Persistance `.env`

```
LOYERTRACKER_TAG=sha-2da27182
```

Mis à jour sur `loyertracker-prod-server` (`sed -i` sur `/home/ubuntu/loyertracker/.env`).
Backup `.env.bak-pre-1.6.0` créé (permissions 600) avant modification.
Vérification : `grep LOYERTRACKER_TAG .env` → `sha-2da27182` ✅, permissions 600 ✅

## 4. Contrôles post-persistance finaux

| Contrôle | Résultat |
|---|---|
| `LOYERTRACKER_TAG=sha-2da27182` dans `.env` | ✅ |
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ |
| Actuator `{"status":"UP"}` | ✅ |
| `/healthz` (port HTTP `18080`) | ✅ `ok` |
| Prometheus 5/5 `up` | ✅ |
| Alertmanager `[]` | ✅ |

## 5. `PRODUCTION_DEPLOYED` — atteint

**2026-07-02 16:50 UTC** — release `1.6.0` (`sha-2da27182`) déployée et validée en Production.
Sprint 7 EP-10 (US-90, Patrimoine enrichi) + Sprint 8 EP-11 (US-92/93, Money/Devise) désormais
en Production. Adresse réelle du patrimoine `d753e6d6-…` confirmée persistée (appliquée au
déploiement technique, §5 de `deploiement-technique-v1.6.0-report.md`).

### Réserves restant ouvertes après ce déploiement

- **RSV-S7-8-01** (non bloquante) : confirmation visuelle USD/CDF (quittance PDF + UI
  Paiements/Honoraires) toujours différée faute de bail USD/CDF réel en Production.
- **RP-160-03** (non bloquante) : `CHANGELOG.md` — scinder `[Non publié]` en `[1.5.0]`
  (rétroactif) + `[1.6.0]`, à traiter à la clôture de release.

Prochaines étapes :
- Hypercare T0 (immédiat), T+12, T+24
- Décision CDO → clôture `1.6.0`
- Post-clôture : CHANGELOG `[Non publié]` → `[1.5.0]` + `[1.6.0] — 2026-07-02` (RP-160-03),
  mise à jour `project-state.md` et `prod-state.md`, promotion `release-notes-v1.6.0.md`
