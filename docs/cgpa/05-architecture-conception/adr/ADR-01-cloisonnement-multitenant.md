# ADR-01 — Cloisonnement multi-tenant

| Champ | Valeur |
|-------|--------|
| Statut | Acceptée |
| Date | 2026-06-04 |
| Phase | 05 — Architecture |
| Condition Gate 2 | « ADR cloisonnement » |
| Exigences couvertes | ENF-02, EF-72, BNF-02 |

## Contexte

LoyerTracker est mono-déploiement mais **multi-bailleur logique** : les données d'un bailleur ne doivent jamais être visibles d'un autre. Surtout, le **gestionnaire** n'est **pas** un simple périmètre statique : son accès est **dynamique**, porté par les `Affectation` au statut `ACTIVE`, et change à chaque révocation/rotation (EF-22/23). Un discriminant `bailleurId` seul ne couvre donc pas le cas gestionnaire.

## Décision

**Cloisonnement en défense en profondeur, à trois couches :**

1. **Service layer (primaire)** — chaque service filtre par `bailleurId` ; le périmètre du gestionnaire est résolu dynamiquement par jointure sur `Affectation` ACTIVE via l'`AuthorizationService` (cf. ADR-02).
2. **PostgreSQL Row-Level Security (filet)** — policy RLS sur la colonne `bailleur_id` de chaque table métier. Le contexte tenant est injecté par requête (`SET app.current_bailleur`). Un oubli de `WHERE` au service layer ne peut **pas** provoquer de fuite cross-bailleur.
3. **Tests d'autorisation (preuve)** — suite couvrant chaque endpoint ; objectif **0 accès cross-bailleur / cross-affectation réussi** (ENF-02).

Toute table métier porte une colonne `bailleur_id` (dénormalisée au besoin), servant à la fois de discriminant et de clé d'index de performance.

## Alternatives écartées

| Alternative | Raison du rejet |
|-------------|-----------------|
| **Discriminant `bailleurId` seul** (sans RLS) | Aucun filet : un oubli de filtre = fuite RGPD. Ne couvre pas le périmètre dynamique gestionnaire. |
| **Schema-per-tenant** (un schéma SQL par bailleur) | Sur-ingénierie pour un dev solo self-hosted ; complexité de migration/provisioning disproportionnée. |
| **Base par tenant** | Idem, coût opérationnel prohibitif. |

## Conséquences

- ✅ Fuite cross-bailleur structurellement improbable (deux barrières indépendantes).
- ✅ Périmètre gestionnaire correct lors des rotations.
- ⚠️ La RLS impose d'injecter le contexte tenant à chaque connexion/requête — encapsulé dans un intercepteur transverse.
- ⚠️ Les requêtes du batch (multi-bailleur) s'exécutent avec un rôle technique contournant la RLS, sous contrôle strict du module `batch`.
