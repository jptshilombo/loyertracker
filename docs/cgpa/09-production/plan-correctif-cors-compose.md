# Plan détaillé — Lot correctif CORS Compose

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-25 |
| Niveau | 1 (correctif de câblage Compose, aucun code applicatif) |
| Autorisation | CDO §10 plan hypercare `1.1.1` — unique étape suivante autorisée après clôture |
| Release cible | `[Non publié]` → patch `1.1.2` (à décider lors du Gate Staging) |

## 1. Contexte et problème

`APP_CORS_ALLOWED_ORIGIN` et `APP_INVITATION_BASE_URL` sont des variables applicatives
attendues par `application.yml` :

```yaml
app:
  cors:
    allowed-origin: ${APP_CORS_ALLOWED_ORIGIN:https://localhost}
  invitation:
    base-url: ${APP_INVITATION_BASE_URL:https://localhost}
```

`SecurityConfig` injecte `APP_CORS_ALLOWED_ORIGIN` comme seule origine autorisée. Si la
variable n'est pas transmise au conteneur, Spring utilise le fallback `https://localhost` :
les requêtes CORS depuis l'origine publique (`https://loyertracker.loyerpro.org`) sont
refusées, et les liens d'invitation envoyés aux gestionnaires pointent vers `localhost`.

Les deux variables sont définies dans `.env` sur les hôtes staging et production depuis
l'exposition publique (2026-06-16), mais **aucun fichier compose ne les transmet au conteneur
`api`** (découvert le 2026-06-24, sans rapport avec le Hotfix `1.1.1`).

## 2. Périmètre

### Autorisé

- Modifier les blocs `environment` du service `api` dans :
  - `docker-compose.yml` (dev + prod via héritage)
  - `docker-compose.staging.yml` (staging — bloc environment distinct)
- Ajouter les deux variables avec leur valeur `.env` en paramètre.
- Ouvrir une PR, passer la CI et décider une promotion staging.

### Interdit

- Modifier `docker-compose.prod.yml` (héritage de base suffisant, aucune entrée `environment` api).
- Modifier `application.yml`, `SecurityConfig.java` ou tout autre fichier applicatif.
- Déployer en Staging sans Gate `STG-ISOL-01` et sans Gate Staging.
- Déployer en Production sans Gate Production distinct.
- Modifier `.env` sur un hôte (les valeurs sont déjà correctes).

## 3. Changements

### `docker-compose.yml` — service `api`

Ajouter après `KEYCLOAK_API_CLIENT_SECRET` :

```yaml
      APP_CORS_ALLOWED_ORIGIN: ${APP_CORS_ALLOWED_ORIGIN:-https://localhost}
      APP_INVITATION_BASE_URL: ${APP_INVITATION_BASE_URL:-https://localhost}
```

### `docker-compose.staging.yml` — service `api`

Même ajout dans le bloc `environment` du service `api` de ce fichier.

## 4. Validation locale

- `docker compose config` : vérifier que les deux variables apparaissent dans la configuration
  résolue du service `api`.
- Aucun `mvn verify` requis (zéro changement Java).
- Aucune migration SQL.

## 5. CI et promotion

- CI GitHub Actions : CodeQL, build, tests, scans, packaging — attendus verts (aucun code modifié).
- Gate `STG-ISOL-01` : déjà PASS ; réserve `RSV-STG-01` à lever lors du déploiement réel.
- Gate Staging v5.3 avant promotion Staging.
- Gate Production avant toute mise en Production.

## 6. Risques

| Risque | Probabilité | Impact | Mitigation |
|---|---|---|---|
| Variable absente de `.env` sur un hôte | Faible | Moyen — fallback `https://localhost` inchangé | Vérifier `.env` avant déploiement |
| Régression CORS | Faible | Élevé | Couverte par le smoke 47/0 (assertion 401 sans jeton, accès cross-tenant) |
