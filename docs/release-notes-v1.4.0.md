# Release Notes — LoyerTracker `1.4.0`

| Champ | Valeur |
|---|---|
| Version | `1.4.0` |
| Date de release | 2026-06-30 |
| Type | Release MINOR — nouvelles fonctionnalités métier |
| Tag Production | `sha-98afa99a` |
| Release précédente | `1.3.0` — `sha-a42d860d` (2026-06-29) |
| Flyway | V1→**V18** (3 nouvelles migrations : V16, V17, V18) |

---

## Nouveautés

### Statut d'échéance `A_VENIR`

Les loyers dont la date d'exigibilité est dans le futur sont désormais créés avec le statut
`A_VENIR` (au lieu de `IMPAYE`). Cela rend le statut technique cohérent avec la réalité métier :
un loyer dont la date n'est pas encore atteinte n'est pas en retard.

- La fonction `generer_echeances_loyers()` produit `A_VENIR` pour les échéances futures et
  `IMPAYE` pour les échéances passées.
- Les paiements `IMPAYE` existants dont la date d'exigibilité est dans le futur ont été basculés
  rétroactivement vers `A_VENIR` lors de la migration V18.
- La contrainte `paiement_statut_check` a été étendue pour inclure `A_VENIR`.

### Devise sur le bail (`bail.devise`)

Le bail supporte désormais une devise : **EUR**, **USD** ou **CDF** (franc congolais).
Valeur par défaut : `EUR`. Migration V17.

### Adresse du patrimoine (`patrimoine.adresse`)

Le patrimoine dispose d'un nouveau champ adresse libre. Migration V16.

### Synchronisation du statut des biens (`bien.statut`)

Les biens ayant un bail `ACTIF` ont été synchronisés rétroactivement vers le statut `LOUE`
lors de la migration V16.

---

## Corrections UX

### Navbar — profil bailleur (PR #110)

Le lien vers le profil bailleur est désormais présent dans la barre de navigation principale,
en plus du header du dashboard.

### Quittance — message 409 actionnable (PR #110)

Lorsque l'adresse bailleur est manquante, le message d'erreur 409 indique désormais clairement
l'action à réaliser (compléter le profil) au lieu d'afficher une erreur opaque.

### Alertes — affichage NON_LUE uniquement (PR #113)

La liste des alertes n'affiche plus que les alertes `NON_LUE`. Le badge de statut redondant
et le style `opacity: 0.6` sur les alertes lues ont été supprimés.

### Échéances — affichage du statut `A_VENIR` (PR #113)

La liste des échéances affiche désormais les entrées avec le statut `A_VENIR`, cohérentes
avec la migration V18.

### Inscription — suppression du bruit (PR #113)

L'erreur 409 retournée lors d'une double inscription (bailleur déjà inscrit) ne produit
plus de message parasite dans l'interface.

---

## Périmètre technique

| Aspect | Détail |
|---|---|
| Nouvelles migrations | V16 (`bien.statut` + `patrimoine.adresse`), V17 (`bail.devise`), V18 (`StatutPaiement.A_VENIR` + `paiement_statut_check` + `generer_echeances_loyers()`) |
| Flyway après upgrade | **18/18** |
| API modifiée | `generer_echeances_loyers()` retourne désormais `A_VENIR` sur les échéances futures |
| Rétrocompatibilité | Les clients existants ignorant `A_VENIR` verront ces échéances si leur filtre n'était pas sur `statut` |
| Rollback schéma | Non trivial — pg_restore V15 requis si rollback au-delà de `1.3.0` |

---

## Compatibilité et rollback

- **Rollback applicatif** : retour à `sha-a42d860d` (`1.3.0`) simple, sans `pg_restore`.
- **Rollback schéma** : si V16/V17/V18 doivent être annulées, `pg_restore` depuis le backup
  pré-déploiement `loyertracker-20260630-160619.dump` (312 Kio, SHA-256 `60b1fd74…`).

---

## Déploiement Production

| Étape | Statut |
|---|---|
| Gate Staging Sprint 5 | ✅ GO — STG-ISOL-01 PASS, Flyway 18/18, smoke 47/0 (2026-06-30) |
| Gate Production Sprint 5 | ✅ GO sous réserve — `PRODUCTION_READY` (2026-06-30) |
| Préflight + backup | ✅ PASS — `loyertracker-20260630-160619.dump` 312 Kio (2026-06-30) |
| Déploiement technique | ✅ PASS — `api` + `nginx` recréés, Flyway V16/V17/V18 (2026-06-30 ~15:12 UTC) |
| Smoke Production | ✅ **47 PASS / 0 FAIL** (2026-06-30 ~15:15 UTC) |
| `PRODUCTION_DEPLOYED` | ✅ 2026-06-30 ~15:15 UTC |
| Hypercare T0/T+12/T+24 | ✅ PASS (T+24 : 2026-07-01 06:38 UTC) |
| Clôture CDO | ✅ GO — 2026-07-01 06:51 UTC |
| Hôte | `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |
