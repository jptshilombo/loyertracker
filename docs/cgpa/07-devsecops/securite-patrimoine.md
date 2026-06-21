# Analyse sécurité & habilitations — Introduction de la notion de Patrimoine

| Champ | Valeur |
|-------|--------|
| Statut | **Validé — Plan d'Exécution Patrimoine approuvé (GO) par le PO le 2026-06-21** (RS-04/RS-05/RS-06 confirmées) ; aucun code produit à ce stade |
| Date | 2026-06-21 |
| Décision liée | D-PAT-001 / ADR-11 |
| Périmètre touché | `securite/AuthorizationService.java`, `securite/TenantContext.java`, RLS PostgreSQL (ADR-01), `affectations/*`, `biens/*` |

## 1. Rappel du modèle de sécurité actuel (référence)

Défense en profondeur à 3 couches (ADR-01) : service layer (filtre `bailleurId` + résolution `Affectation` ACTIVE) → RLS PostgreSQL (`bailleur_id`, filet) → suite de tests d'autorisation (preuve, ENF-02). Séparation stricte AuthN/RBAC grossier (Keycloak) vs ReBAC applicatif (`AuthorizationService`, ADR-02). Aujourd'hui, `AuthorizationService.peutAccederBien` résout un **unique** chemin par rôle : bailleur = propriétaire direct ; gestionnaire = affectation bien ACTIVE.

## 2. Impact RBAC (rôles Keycloak)

**Aucun impact.** Les rôles realm restent `BAILLEUR`/`GESTIONNAIRE` (ADR-02 : Keycloak ne porte jamais de droits par bien ni par patrimoine). ✅ **RS-05 validé par le PO le 2026-06-21** : l'administration de la typologie de biens reste portée par le rôle `BAILLEUR` existant — **aucun rôle Keycloak supplémentaire créé**, conforme au principe ADR-02 (toute granularité fine reste applicative).

## 3. Impact ReBAC (autorisation fine applicative)

> ✅ **Algorithme et règles de validation ci-dessous confirmés par le PO le 2026-06-21** (point de décision RM-98 dédié, cf. `plan-execution-patrimoine.md` Sprint 2). Réserve Sprint 2 levée — extension `AuthorizationService` autorisée à démarrer dès approbation du Plan d'Exécution.

`AuthorizationService` doit évoluer d'une résolution à un seul niveau vers une résolution à **deux niveaux avec priorité** :

1. **Nouveau prédicat** `estGestionnaireAffectePatrimoineActif(patrimoineId, gestionnaireId)` — symétrique de l'existant `estGestionnaireAffecteActif(bienId, gestionnaireId)`, implémenté en fonction `SECURITY DEFINER` (même patron que V3, ADR-09).
2. **Résolution d'un bien** (nouvelle logique de `peutAccederBien`) :
   - Si une affectation **bien** `ACTIVE` existe pour (gestionnaire, bien) → utiliser son `typeException` comme verdict final (`INCLUSION` → accès, `EXCLUSION` → refus). **Court-circuite** toute résolution patrimoine.
   - Sinon, si une affectation **patrimoine** `ACTIVE` existe pour (gestionnaire, patrimoine du bien) → accès accordé.
   - Sinon → refus (comportement actuel inchangé).
3. **Fail-closed inchangé** : toute ambiguïté (rôle absent, JWT invalide, identifiants nuls) renvoie `false`, comme aujourd'hui.

## 4. Visibilité des patrimoines

- Un bailleur voit tous ses patrimoines (propriété directe, comme pour les biens aujourd'hui).
- Un gestionnaire **ne doit jamais** lister les patrimoines d'un bailleur directement (`GET /api/patrimoines` reste **BAILLEUR seul**, cf. addendum CDC §5) — un gestionnaire affecté à un patrimoine accède à **ses biens**, pas à l'entité Patrimoine elle-même (pas de besoin métier identifié pour un endpoint « mes patrimoines affectés » côté gestionnaire ; à confirmer si le dashboard gestionnaire doit grouper visuellement par patrimoine).

## 5. Visibilité des biens (cas à tester explicitement)

| Cas | Attendu |
|-----|---------|
| Gestionnaire avec affectation patrimoine `ACTIVE`, aucune exception | Voit tous les biens du patrimoine, y compris ceux ajoutés après l'affectation |
| Gestionnaire avec affectation patrimoine `ACTIVE` + affectation bien `EXCLUSION` sur un bien X | Voit tous les biens du patrimoine **sauf** X |
| Gestionnaire avec affectation bien `INCLUSION` sur un bien Y, sans affectation patrimoine | Voit uniquement Y (comportement identique à l'existant US-23/24, non régressé) |
| Gestionnaire affectation patrimoine révoquée | Perd l'accès à tous les biens du patrimoine, **sauf** ceux couverts par une affectation bien `INCLUSION` active indépendante |
| Gestionnaire d'un autre bailleur | 403/404 sur tout patrimoine/bien — inchangé (couche RLS + service layer) |
| Bien retiré d'un patrimoine *(si cette opération est permise — à confirmer, absente des règles métier fournies)* | Hors périmètre de cette décision ; si elle existe, le gestionnaire doit perdre l'accès hérité immédiatement |

## 6. Héritage des droits

L'héritage est **dynamique et non dénormalisé** : aucune table de jonction matérialisant « gestionnaire × bien hérité » n'est créée — la résolution se fait à la volée par jointure (`patrimoine_id` du bien = `patrimoine_id` de l'affectation), à l'image du choix déjà fait pour les affectations bien (ADR-01 : « le périmètre gestionnaire est résolu dynamiquement »). Ce choix évite la désynchronisation lors de l'ajout/retrait de biens dans un patrimoine, au prix d'une jointure supplémentaire (cf. addendum CDC §4.4, index proposés).

## 7. Exceptions (`INCLUSION`/`EXCLUSION`)

- Une exception n'a de sens **qu'en présence d'un `bienId`** (contrainte `CHECK`, addendum CDC §4.3).
- `EXCLUSION` sans affectation patrimoine préalable pour ce gestionnaire est un **état incohérent** (rien à exclure) : **rejetée en validation applicative (400)**, pas seulement documentée — sans quoi un bailleur pourrait croire avoir restreint un accès qui n'existait pas. **Confirmé par le PO le 2026-06-21 (RS-04).**
- `INCLUSION` en présence d'une affectation patrimoine déjà active pour ce même bien est **redondante** mais non dangereuse — **tolérée (idempotence)** plutôt que rejetée, pour ne pas complexifier l'UX bailleur. **Confirmé par le PO le 2026-06-21.**

## 8. Risques de fuite d'information

| Risque | Sévérité | Mitigation recommandée |
|--------|----------|--------------------------|
| Court-circuit incorrect : une `EXCLUSION` mal résolue (ex. bug d'ordre d'évaluation) accorde l'accès au lieu de le refuser | **Critique** | Tests d'autorisation dédiés couvrant explicitement les 4 combinaisons (§5 du présent document) avant toute fusion ; principe **fail-closed** si les deux niveaux sont indéterminés |
| RLS non étendue à `patrimoine` (oubli au moment de l'implémentation) | Critique | Checklist de migration : `ENABLE`+`FORCE` RLS sur `patrimoine` dès sa création, test de verrou de fidélité RLS (même patron que PR #18, double datasource) |
| Affectation patrimoine orpheline (patrimoine archivé mais affectation toujours `ACTIVE`) | Moyen | **Tranché par le PO le 2026-06-21 (RS-06)** : l'archivage est **bloqué (400)** tant qu'une affectation patrimoine `ACTIVE` existe pour ce patrimoine — aucune cascade, donc aucun orphelin possible. Révocation explicite préalable requise (cohérent EF-22). |
| Désynchronisation lors de la migration des biens existants (US-82) si un bien reste temporairement sans patrimoine pendant le déploiement | Moyen | Migration transactionnelle unique (patrimoine par défaut + rattachement) avant d'activer la contrainte `NOT NULL` |
| Confusion UI bailleur entre « affectation patrimoine » et « affectation bien » menant à une délégation involontairement trop large | Faible (UX, pas sécurité technique) | Affichage explicite du périmètre effectif résultant avant confirmation de toute affectation (recommandation produit, hors périmètre technique) |

## 9. Règles de sécurité recommandées (à intégrer au Plan d'Exécution)

1. **RS-01** — Toute résolution de périmètre (patrimoine ou bien) reste centralisée dans `AuthorizationService` ; aucune logique de filtrage par patrimoine/bien dans les contrôleurs ou le frontend.
2. **RS-02** — `Patrimoine` est soumis à la RLS PostgreSQL au même titre que les autres tables métier (ADR-01), dès sa création, sans phase intermédiaire « sans RLS ».
3. **RS-03** — La suite de tests d'autorisation existante (`SecurityIntegrationTest`, esprit US-71/ex-US-71 du backlog déjà validé) est étendue avec un test par combinaison du tableau §5, avant toute fusion en `main`.
4. **RS-04** — Toute création d'affectation `EXCLUSION` sans affectation patrimoine active correspondante est rejetée en 400 (cohérence métier, pas seulement documentaire). **Validé par le PO le 2026-06-21**, avec tolérance symétrique pour une `INCLUSION` redondante (idempotente, non rejetée) — cf. §3/§7.
5. **RS-05** — Aucun nouveau rôle Keycloak n'est créé pour l'administration de la typologie de biens ; elle reste portée par le rôle `BAILLEUR` existant. **Validé par le PO le 2026-06-21.**
6. **RS-06** — Toute tentative d'archivage d'un patrimoine ayant au moins une affectation patrimoine `ACTIVE` est **rejetée en validation applicative (400)** ; le bailleur doit d'abord révoquer explicitement ces affectations (cohérent avec EF-22, qui exige une révocation explicite plutôt qu'un effet de bord). **Validé par le PO le 2026-06-21** — options alternatives écartées : cascade de révocation automatique (effet de bord implicite, contraire à EF-22) et archivage libre avec affectations orphelines (risque de confusion/fuite résiduel). Implémentation à porter par l'endpoint `PUT/DELETE /api/patrimoines/{id}` (US-80, Sprint 1), avec garde effective dès que `Affectation.patrimoineId` existe (US-84, Sprint 2).
