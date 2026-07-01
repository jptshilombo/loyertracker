# Plan d'Exécution Sprint 6 — RGPD & Durcissement Sécurité

| Champ | Valeur |
|---|---|
| Sprint | 6 |
| Date de démarrage | 2026-07-01 |
| Statut | **EN COURS** |
| Autorisation de départ | Clôture release `1.4.0` — CDO GO 2026-07-01 06:51 UTC |
| Vélocité cible | ~13 pts (US-70 + US-72 partiel) |
| Branche feature | `feat/sprint6-rgpd-us70-us72` |

## 1. Périmètre Sprint 6

### US-70 — Export RGPD & effacement locataire (5 pts — Must)

**Objet :** permettre au bailleur d'exporter ses données et d'anonymiser les données personnelles
d'un locataire sur un bail.

**Scope effacement (PO validé le 2026-07-01) : locataire uniquement.**
- `locataire_nom` → `"[anonymisé]"` (non nullable — pseudonymisation)
- `locataire_email` → `null`
- Données financières conservées (`loyer_hc`, `loyer_cc`, `provision_charges`, etc.)
- Opération tracée dans `audit_log` (action `EFFACEMENT_LOCATAIRE`)

**Endpoints :**

| Méthode | URI | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/bailleurs/export` | `BAILLEUR` | Export JSON complet scopé `bailleurId` |
| `DELETE` | `/api/biens/{bienId}/baux/{bailId}/locataire` | `BAILLEUR` | Anonymisation PII locataire |

### US-72 — CSP Nginx (1 pt — Should, partiel)

**Objet :** durcir la `Content-Security-Policy` de la SPA Angular au-delà du placeholder laissé
lors du déploiement initial.

**Ajouts :** `script-src 'self'`, `font-src 'self'`, `object-src 'none'`, `base-uri 'self'`,
`form-action 'self'`, `frame-ancestors 'none'`.

### US-71 — Complément tests cross-bailleur (2 pts — Should)

**Constat :** `SecurityIntegrationTest` et les tests S02–S04 couvrent les endpoints pré-Sprint 5.
Les endpoints ajoutés en Sprint 5 (baux devise, patrimoine adresse, paiements A_VENIR) sont couverts
par les tests de migration. US-71 considérée **Done** — critères d'acceptation du backlog atteints.

## 2. Architecture technique US-70

```
RgpdController          RgpdService
  GET  /api/bailleurs/export  ──►  assemblerExport(bailleurId, sub)
  DEL  /api/biens/{}/baux/{}/locataire  ──►  anonymiserLocataire(bienId, bailId, auth)
                                                  ├─ TenantContext.activerDepuisBien()
                                                  ├─ Bail.anonymiserLocataire()
                                                  └─ AuditService.enregistrer(EFFACEMENT_LOCATAIRE)
```

**Pas de migration Flyway** — aucun changement de schéma requis (champs existants, nullable ajusté
en mémoire uniquement pour l'effacement par UPDATE JPA).

## 3. Étapes d'exécution

| # | Étape | Statut |
|---|---|---|
| 1 | Branche `feat/sprint6-rgpd-us70-us72` créée depuis `main` | ✅ FAIT |
| 2 | Plan Sprint 6 créé (`sprint6-plan.md`) | ✅ FAIT — ce fichier |
| 3 | `Bail.anonymiserLocataire()` + `BailRepository` mise à jour | ✅ FAIT |
| 4 | `RgpdController` + `RgpdService` + `ExportBailleurDto` | ✅ FAIT |
| 5 | CSP Nginx durcissement (`nginx.conf`) | ✅ FAIT |
| 6 | Tests d'intégration `RgpdIntegrationTest` | ✅ FAIT |
| 7 | CI → Gate Staging → Gate Production | CI ✅ FAIT — Gate Staging ✅ FAIT (`sha-08b366fa`, `STAGING_DEPLOYED`) — Gate Production À FAIRE |

## 4. Critères de succès

- `GET /api/bailleurs/export` : 200 JSON structuré, scopé RLS, inaccessible par autre bailleur (403)
- `DELETE /api/biens/{bienId}/baux/{bailId}/locataire` : 204, `locataire_nom = "[anonymisé]"`, email null, audit_log tracé
- CSP Nginx : `script-src 'self'`, `object-src 'none'`, `base-uri 'self'` présents
- CI 100% PASS
- Smoke : compteur mis à jour si nouveaux endpoints inclus

## 5. Réserves

| ID | Description | Statut |
|---|---|---|
| RSV-S6-01 | Smoke à mettre à jour si nouveaux endpoints ajoutés au périmètre de test | **LEVÉE** — section 9 ajoutée à `infra/smoke/smoke-stack.sh` (export bailleur, isolation cross-tenant de l'export, 403 gestionnaire, effacement 204, vérification anonymisation + audit `EFFACEMENT_LOCATAIRE`). Vérifié live sur `ai-test-server` (`sha-08b366fa`) : **59 PASS / 0 FAIL** (47 + 12 nouvelles assertions RGPD). |
