# Release Notes — LoyerTracker `1.2.0`

| Champ | Valeur |
|---|---|
| Version | `1.2.0` |
| Date de release | 2026-06-26 |
| Type | Release mineure (Sprint 3 Patrimoine + correctif CORS Compose) |
| Commit | `5bf187af79218377b2f7db7800961725088d31a5` |
| Artefact GHCR | `sha-5bf187af` |
| Release précédente | `1.1.1` — `sha-0adc4941` (2026-06-24) |

---

## Nouveautés — Sprint 3 Patrimoine (US-85)

### Exceptions fines par bien : `INCLUSION` et `EXCLUSION`

Une affectation **BIEN** active d'un gestionnaire court-circuite désormais l'héritage de son
affectation patrimoine :

- **`INCLUSION`** — le gestionnaire accède à ce bien précis, même si son affectation patrimoine
  ne le couvre pas ; comportement historique des affectations bien, inchangé pour les données
  existantes.
- **`EXCLUSION`** — le gestionnaire est explicitement carve-out de ce bien, même si son
  affectation patrimoine le couvrirait normalement (RS-04).

Règle **RS-04** : une `EXCLUSION` sans affectation patrimoine active du même gestionnaire est
rejetée en 400 (état incohérent).

### Migration V15

Colonne nullable `affectation.type_exception` (`INCLUSION` | `EXCLUSION`) ajoutée à la table
`affectation`. Backfill `INCLUSION` sur toutes les affectations bien existantes (idempotent,
non-régressif). Réécriture à priorité des fonctions SQL `gestionnaire_affecte_actif` et
`biens_affectes_gestionnaire`.

### Correctif `calculer_honoraires`

Une affectation `EXCLUSION` est un carve-out d'accès : elle ne génère plus d'honoraire
(comportement antérieur incorrect — une exclusion était facturée comme une affectation normale).

### Périmètre backend-only

Aucune interface utilisateur n'est livrée pour les exceptions INCLUSION/EXCLUSION dans cette
version. L'UX est différée à un lot ultérieur (décision PO 2026-06-24).

---

## Corrections — Correctif CORS Compose

### Câblage `APP_CORS_ALLOWED_ORIGIN` et `APP_INVITATION_BASE_URL`

Les deux variables étaient définies dans `.env` sur les hôtes staging et production depuis
l'exposition publique (2026-06-16), mais n'étaient pas transmises au conteneur `api` par les
fichiers Compose. Spring utilisait le fallback `https://localhost` : les requêtes CORS depuis
l'origine publique étaient refusées et les liens d'invitation pointaient vers `localhost`.

Ajoutées dans `docker-compose.yml` (héritage dev + prod) et `docker-compose.staging.yml`.
`docker-compose.prod.yml` non modifié : le service `api` y hérite du fichier de base (pas de
bloc `environment` api dupliqué).

---

## Périmètre exclu

- UI des exceptions INCLUSION/EXCLUSION (différée).
- Correctif cascade dashboard (`c1e9c73`) — post-Gate Staging, prochain lot.

---

## Compatibilité et rollback

- **Base de données** : migration V15 additive (nouvelle colonne nullable). Rollback applicatif
  seul non trivial (Flyway valide 15 migrations au démarrage). Rollback complet via restauration
  du backup pré-déploiement (procédure : Gate Production `1.2.0` §Rollback).
- **API** : rétrocompatible. Aucun endpoint supprimé ou renommé.
- **Frontend** : aucun changement d'interface livré dans cette release.
- **Keycloak** : aucune modification de configuration ou de realm.
