# Release Notes — LoyerTracker `1.5.0`

| Champ | Valeur |
|---|---|
| Version | `1.5.0` |
| Date de release | À renseigner après `PRODUCTION_DEPLOYED` |
| Type | Release MINOR — RGPD & durcissement sécurité (Sprint 6) |
| Tag Production candidat | `sha-08b366fa` |
| Release précédente | `1.4.0` — `sha-98afa99a` (2026-06-30) |
| Flyway | V1→V18 — **inchangé** (aucune nouvelle migration) |

---

## Nouveautés

### Export RGPD du bailleur (US-70)

`GET /api/bailleurs/export` : export JSON complet des données du bailleur authentifié
(patrimoines, biens, baux, paiements, affectations, garanties), scopé par RLS à son propre
`bailleurId`. Répond au droit d'accès RGPD.

### Effacement des données personnelles du locataire (US-70)

`DELETE /api/biens/{bienId}/baux/{bailId}/locataire` : anonymise les données personnelles du
locataire sur un bail (`locataireNom` → `"[anonymisé]"`, `locataireEmail` → `null`). Les données
financières du bail (loyers, provisions, dépôt de garantie) sont conservées. L'opération est
tracée dans `audit_log` (action `EFFACEMENT_LOCATAIRE`). Réservé au rôle `BAILLEUR`.

### Durcissement Content-Security-Policy (US-72)

La CSP servie par Nginx pour la SPA Angular est étendue : `script-src 'self'`, `font-src 'self'`,
`object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'`, en plus
des directives déjà en place (HSTS, `X-Content-Type-Options`, `X-Frame-Options`,
`Referrer-Policy`).

---

## Périmètre technique

| Aspect | Détail |
|---|---|
| Nouvelles migrations | Aucune |
| Flyway après upgrade | 18/18 (inchangé) |
| API ajoutée | `GET /api/bailleurs/export`, `DELETE /api/biens/{bienId}/baux/{bailId}/locataire` |
| Rétrocompatibilité | Additive uniquement — aucun endpoint existant modifié |
| Rollback schéma | N/A — aucune migration à annuler |

---

## Compatibilité et rollback

- **Rollback applicatif** : retour à `sha-98afa99a` (`1.4.0`), simple, sans `pg_restore`
  (aucune migration entre `1.4.0` et `1.5.0`).

---

## Déploiement Production

| Étape | Statut |
|---|---|
| Gate Staging Sprint 6 | ✅ GO — STG-ISOL-01 PASS, Flyway 18/18 inchangé, smoke 59/0 (2026-07-01) |
| Gate Production Sprint 6 | En cours |
| Préflight + backup | À faire |
| Déploiement technique | À faire |
| Smoke Production | À faire |
| `PRODUCTION_DEPLOYED` | À faire |
| Hypercare T0/T+12/T+24 | À faire |
| Clôture CDO | À faire |
| Hôte | `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |
