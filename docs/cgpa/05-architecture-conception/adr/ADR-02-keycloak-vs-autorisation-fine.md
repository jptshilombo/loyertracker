# ADR-02 — Keycloak vs autorisation fine

| Champ | Valeur |
|-------|--------|
| Statut | Acceptée |
| Date | 2026-06-04 |
| Phase | 05 — Architecture |
| Condition Gate 2 | « ADR Keycloak vs autorisation fine » |
| Exigences couvertes | ENF-01, EF-06, EF-72, BNF-01 |

## Contexte

Keycloak fournit l'authentification (OIDC) et un RBAC par rôles. Mais LoyerTracker exige une **autorisation fine et dynamique** : « ce gestionnaire peut-il accéder à ce bien ? » dépend de l'existence d'une `Affectation` ACTIVE (EF-21/22/23, EF-71/72). La frontière entre ce que porte l'IdP et ce que porte l'application doit être tranchée nettement — c'est la source de confusion la plus fréquente sur ce type de projet.

## Décision

**Séparation stricte des responsabilités :**

| Couche | Responsable | Porte | Ne porte PAS |
|--------|-------------|-------|--------------|
| AuthN + RBAC grossier | **Keycloak** | identité, rôle `BAILLEUR`/`GESTIONNAIRE` (claim JWT) | les droits par bien |
| Autorisation fine (ReBAC) | **Application** | « gestionnaire ↔ bien » via `Affectation` ACTIVE | — |

- Le backend valide le **JWT** (resource server) et en extrait l'identité + le rôle.
- Un **`AuthorizationService`** central est la **source de vérité** de l'autorisation fine ; il résout le périmètre effectif (cf. ADR-01) et est invoqué via Spring Security `@PreAuthorize`.
- **EF-72** (refus 403 côté serveur sur bien non affecté) = test d'intégration de cet `AuthorizationService`, indépendant de l'UI.
- Le provisioning du compte gestionnaire à l'acceptation d'invitation (EF-04) se fait via l'**Admin API** Keycloak, encapsulé dans un adaptateur du module `comptes`.

## Alternatives écartées

| Alternative | Raison du rejet |
|-------------|-----------------|
| **Permissions par-bien dans Keycloak** (groupes/policies fines, Authorization Services) | Explosion combinatoire (1 entrée par couple gestionnaire×bien) ; **désynchronisation** à chaque rotation/révocation ; couplage fort de la logique métier à l'IdP ; difficile à tester. |
| **Autorisation 100 % applicative sans Keycloak** | Réinvente l'AuthN/OIDC ; contraire à la stack imposée (CLAUDE.md §7) et aux standards DevSecOps. |
| **OPA/sidecar de politique** | Surcouche opérationnelle injustifiée au regard du périmètre MVP. |

## Conséquences

- ✅ IdP stable : aucune écriture Keycloak lors des rotations d'affectation (seule la table `Affectation` change).
- ✅ Autorisation testable et versionnée avec le code métier.
- ⚠️ La cohérence rôle (Keycloak) ↔ affectations (DB) doit être maintenue applicativement ; couverte par les tests d'autorisation (ENF-02).
