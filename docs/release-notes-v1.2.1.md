# Release Notes — LoyerTracker `1.2.1`

| Champ | Valeur |
|---|---|
| Version | `1.2.1` |
| Date de release | À renseigner après `PRODUCTION_DEPLOYED` |
| Type | Release corrective (PATCH) |
| Commit applicatif | `c1e9c735e39c0375b907be9da3302e67f5cb10d4` |
| Tag candidat | `sha-47172297` |
| Release précédente | `1.2.0` — `sha-5bf187af` (2026-06-26) |

---

## Corrections

### Dashboard bailleur — chargement des biens garanti même en cas d'erreur d'inscription

**Problème :** `chargerBiens()` et `chargerReferentielsBien()` étaient appelés uniquement
dans le callback `next` de `inscrire()`. En cas d'erreur lors de l'appel d'inscription
(401 session expirée, 500, erreur réseau), le tableau de bord restait vide — aucun bien,
aucun sélecteur de type, aucun menu déroulant de patrimoine n'était affiché.

**Correction :** `chargerBiens()` est désormais déclenché via l'opérateur RxJS `finalize`,
qui s'exécute en succès comme en erreur. `chargerReferentielsBien()` est lancé en parallèle
de l'appel d'inscription, sans attendre son résultat.

**Fichier modifié :** `frontend/src/app/bailleur/dashboard/dashboard.component.ts`

**Impact utilisateur :** le tableau de bord affiche désormais les biens et les référentiels
dans tous les cas, y compris lorsque l'inscription retourne une erreur (comportement déjà
inscrit, session expirée, etc.).

---

## Périmètre technique

- **Frontend Angular uniquement** — aucun changement backend, aucune migration SQL.
- **Flyway inchangé** : le rang maximal reste V15 (15 migrations) — identique à `1.2.0`.
- **API inchangée** : aucun endpoint ajouté, modifié ou supprimé.
- **Compose inchangé** : aucune variable d'environnement, aucun réseau, aucun volume modifié.
- **Keycloak inchangé** : aucune modification de configuration ou de realm.

---

## Compatibilité et rollback

- **Rétrocompatibilité** : totale. Aucune rupture de contrat API ni de schéma.
- **Rollback** : retour à `sha-5bf187af` (`1.2.0`) par simple redéploiement du service
  `nginx` — aucun `pg_restore` requis (aucune migration ajoutée entre `1.2.0` et `1.2.1`).

---

## Déploiement Production

| Étape | Statut |
|---|---|
| Gate Staging `1.2.1` | À exécuter |
| Gate Production `1.2.1` | À exécuter |
| Préflight + backup | À exécuter |
| Déploiement technique | À exécuter |
| Validation finale (smoke 47/0) | À exécuter |
| `PRODUCTION_DEPLOYED` | Non atteint |
| Opérateur | À renseigner |
| Hôte | `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |
