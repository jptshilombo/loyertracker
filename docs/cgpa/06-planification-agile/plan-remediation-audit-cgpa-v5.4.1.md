# Plan d'Exécution — Remédiation de l'audit CGPA v5.4.1

| Champ | Valeur |
|---|---|
| Statut | **GO sous réserve confirmé — exécution autorisée le 2026-06-27** |
| Déclencheur | Audit transversal CGPA v5.4.1 du 2026-06-27 |
| Décision CDO | **GO sous réserve** |
| Branche | `codex/remediation-audit-cgpa-v5.4.1` |
| Base | `origin/main` — merge PR #82 `7738a0841a8601fa96dc0ca64b1e585d9b4e4286` |
| Environnements | Dépôt et GitHub uniquement ; aucun déploiement Staging/Production |

## 1. Objectif

Lever les réserves de l'audit sans rejouer ni supprimer un Gate, une décision ou un risque
historique. Le lot rétablit la traçabilité du Sprint 4, renforce les contrôles GitHub, qualifie les
alertes de sécurité ouvertes, corrige la documentation de rollback et traite les écarts UX ciblés.

Ce plan n'autorise aucun déploiement. Toute promotion du Sprint 4 reste soumise au Gate Staging,
à `STG-ISOL-01` live, puis à un Gate Production distinct.

## 2. Périmètre et critères d'acceptation

| Lot | Actions | Critère PASS |
|---|---|---|
| R1 — Gouvernance | Synchroniser `project-state.md`, README, CHANGELOG et `staging-state.md` ; tracer la PR #82 et le présent plan | État courant non ambigu, historique préservé |
| R2 — GitHub | Protéger `main`, exiger PR + checks CI/CodeQL, interdire les push directs ; activer Dependabot | Protection vérifiée par API, alertes de dépendances activées |
| R3 — SAST | Corriger l'alerte SQL CodeQL de test ; documenter et qualifier la désactivation CSRF stateless Bearer | Aucune alerte CodeQL High non qualifiée |
| R4 — Rollback | Corriger les commandes `1.2.1` utilisant l'overlay Production seul | Toute commande Production combine base + overlay |
| R5 — UX/UI | Ajouter lien d'évitement, focus visible, annonces accessibles, validation ciblée, responsive navbar et confirmation des actions destructives | Tests frontend verts et critères UX documentés |
| R6 — Supply chain | Épingler les Actions sensibles par commit et remplacer l'usage de branche flottante ; ajouter la surveillance Dependabot | Aucun `uses: ...@main`, mises à jour automatisées configurées |
| R7 — Qualité | Exécuter lint, build, tests frontend/backend, contrôles de diff et CI GitHub | 0 échec bloquant |

## 3. Architecture et sécurité

- Aucun changement de contrat API, schéma SQL, RLS, Keycloak, Docker Staging ou Production.
- La désactivation CSRF reste admise uniquement parce que l'API est stateless, n'utilise aucune
  authentification par cookie et exige un Bearer JWT explicitement envoyé dans `Authorization`.
- L'alerte SQL concerne un test de schéma ; elle sera supprimée par requête paramétrée, sans
  suppression ou exclusion de la règle CodeQL.
- La protection de `main` sera appliquée après publication de la branche afin de ne pas contourner
  le flux PR qu'elle impose.

## 4. Critères UX/UI ciblés

- Navigation clavier complète et focus `:focus-visible` contrasté.
- Lien « Aller au contenu principal ».
- Messages de chargement, succès et erreur annoncés par `aria-live`.
- Erreurs des formulaires Sprint 4 associées aux champs invalides.
- Confirmation avant archivage ou révocation.
- Navbar et grilles utilisables à 375, 768, 1024 et 1440 px sans défilement horizontal global.
- Respect de `prefers-reduced-motion`.

Le choix PO « gestionnaire par UUID brut » reste conservé : il est historique et son remplacement
nécessiterait un endpoint et un arbitrage produit distincts. Une aide de saisie explicite sera ajoutée.

## 5. Séquence

1. Créer ce Plan d'Exécution et tracer la décision GO sous réserve.
2. Corriger la documentation et le registre d'état.
3. Implémenter R3, R5 et R6.
4. Exécuter les validations locales proportionnées au risque.
5. Committer et pousser la branche ; ouvrir une PR dédiée.
6. Vérifier la CI distante.
7. Activer la protection de `main` et Dependabot, puis vérifier leur état par API.
8. Produire le rapport de remédiation et statuer sur la levée des réserves.

## 6. Rollback du lot

- Modifications dépôt : revert de la PR de remédiation.
- Protection GitHub : restaurer explicitement la configuration antérieure uniquement sur décision
  CDO/Release Manager ; l'absence de protection n'est pas un état cible acceptable.
- Aucun rollback Staging/Production requis : aucun déploiement n'est autorisé par ce plan.

## 7. Rôles

| Rôle | Responsabilité |
|---|---|
| Governance Officer | Préservation de l'historique et cohérence des statuts |
| Enterprise Architect | Absence de dérive d'architecture et cohérence sécurité |
| DevSecOps Lead | CI, CodeQL, Dependabot, protection de branche et supply chain |
| Release Manager | Vérification qu'aucune promotion n'est implicite |
| Chief Delivery Officer | Décision finale de levée ou maintien des réserves |

## 8. Risques du lot

| ID | Risque | Mitigation |
|---|---|---|
| RA-01 | Protection de branche trop stricte bloquant les PR | Exiger uniquement les checks réellement produits et vérifier sur la PR du lot |
| RA-02 | Régression UX liée aux confirmations | Tests unitaires des chemins confirm/annulation |
| RA-03 | Fausse clôture d'une alerte sécurité | Corriger le SQL ; documenter factuellement le modèle CSRF avant qualification |
| RA-04 | SHA d'Action invalide | Résoudre chaque tag par l'API GitHub et laisser la CI valider |
| RA-05 | Dérive documentaire supplémentaire | Recherche globale des tags, versions et commandes Production après modification |
