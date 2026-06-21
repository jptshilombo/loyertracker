# Plan d'Exécution CGPA — Introduction de la notion de Patrimoine

| Champ | Valeur |
|-------|--------|
| Statut | **Proposé — non approuvé**. Aucune exécution, aucun codage, aucune migration tant que le PO n'a pas validé ce plan (verrou CGPA v3.0). |
| Date | 2026-06-21 |
| Décision liée | D-PAT-001 / ADR-11 |
| Backlog couvert | EP-09, US-80→85 (`addendum-patrimoine-backlog.md`) |
| Niveau | Niveau 3 (changement structurant du modèle de données + sécurité) |

> Conformément à la règle CGPA v3.0, **« Codage suspendu : Plan d'Exécution requis avant modification du code »** s'applique : ce document est une proposition de planification, pas une autorisation d'exécution. Il devient exécutable seulement après approbation explicite du PO, sprint par sprint (chaque sprint est un point de contrôle GO/NO GO).

---

## Sprint 1 — Patrimoines & modèle de données

**Objectif :** poser l'entité `Patrimoine`, la typologie administrable, et le rattachement obligatoire des biens, sans toucher à l'affectation.

| Élément | Détail |
|---------|--------|
| Stories couvertes | US-80 (patrimoines), US-81 (typologie), US-82 (rattachement bien→patrimoine) |
| Livrables | Entité/table `Patrimoine` + RLS ; référentiel `TypeBien` administrable ; `Bien.patrimoineId` NOT NULL ; migration de données (patrimoine par défaut par bailleur + rattachement + mapping des `Bien.type` libres existants) ; endpoints `/api/patrimoines`, `/api/types-biens` ; tests unitaires + intégration ; non-régression complète des endpoints `biens` existants |
| Dépendances | Aucune dépendance externe ; nécessite la liste des valeurs distinctes actuellement présentes dans `Bien.type` (à extraire en base avant de cadrer le mapping, hors périmètre de cette analyse documentaire) |
| Risques | Migration de données irréversible si mal cadrée (cf. ADR-11 §Risques, addendum CDC §4.5) ; valeurs de `type` non mappables nécessitant un arbitrage manuel |
| Critères GO (fin de sprint) | ✅ 100 % des biens existants rattachés à un patrimoine, 0 orphelin · ✅ 100 % des `Bien.type` migrés ou liste des cas non mappables arbitrée par le PO · ✅ Suite de tests existante (US-20→24) verte sans régression · ✅ RLS `ENABLE`+`FORCE` vérifiée sur `Patrimoine` (test de verrou de fidélité, même patron que PR #18) |

---

## Sprint 2 — Gestion des affectations (patrimoine + priorité)

**Objectif :** permettre l'affectation au niveau patrimoine et trancher/implémenter l'algorithme de priorité avec le niveau bien.

| Élément | Détail |
|---------|--------|
| Stories couvertes | US-84 (affectation patrimoine), début US-85 (priorité — partie résolution) |
| Pré-requis bloquant | ~~Validation explicite du PO sur l'algorithme RM-98~~ **✅ Levé — validé par le PO le 2026-06-21** (résolution patrimoine ∪ inclusion − exclusion confirmée telle que proposée ; RS-04 rejet 400 `EXCLUSION` orpheline ; `INCLUSION` redondante tolérée). Détail : ADR-11 §Décision point 5, `securite-patrimoine.md` §3/§7/§9. Pré-requis restant avant code : approbation du présent Plan d'Exécution par le PO (statut global, cf. bandeau) |
| Livrables | `Affectation.patrimoineId` (nullable, exclusif de `bienId`) + `typeException` ; extension `AuthorizationService` (nouveau prédicat `estGestionnaireAffectePatrimoineActif`, fonction `SECURITY DEFINER` associée) ; endpoint `/api/affectations` étendu (validation 400 si patrimoineId+bienId simultanés ou absents) ; `/api/patrimoines/{id}/affectations` |
| Dépendances | Sprint 1 (Patrimoine et rattachement biens doivent exister) |
| Risques | Algorithme validé (2026-06-21) — risque résiduel : implémentation non conforme à RM-98/RS-04 tel que validé (cf. RS-01→RS-04, `securite-patrimoine.md`), à couvrir par les tests d'autorisation dédiés avant fusion ; jointure supplémentaire sur les dashboards (ENF-06) |
| Critères GO (fin de sprint) | ✅ Algorithme RM-98 validé par le PO et documenté avant tout commit applicatif **(acquis le 2026-06-21)** · ✅ Affectation patrimoine fonctionnelle avec héritage dynamique vérifié (ajout d'un bien après affectation → accès immédiat) · ✅ 0 régression sur la suite d'autorisation existante · ✅ Performance dashboard < 2 s maintenue (ENF-06) sur un jeu de test ≥ 50 biens |

---

## Sprint 3 — Contrats actifs, sécurité, tests

**Objectif :** finaliser les exceptions fines, clore la non-régression du contrat actif, et durcir la suite de tests de sécurité avant fusion finale.

| Élément | Détail |
|---------|--------|
| Stories couvertes | Fin US-85 (exceptions `INCLUSION`/`EXCLUSION`), US-83 reclassée en critère de non-régression (pas un développement) |
| Livrables | Validation applicative `EXCLUSION` sans affectation patrimoine → 400 (RS-04) ; suite de tests d'autorisation couvrant les 4 combinaisons (`securite-patrimoine.md` §5) ; confirmation non-régression EF-12/EF-96 (un seul contrat actif par bien, inchangé) ; documentation OpenAPI des nouveaux endpoints ; mise à jour `CHANGELOG.md` `[Non publié]` |
| Dépendances | Sprint 2 (affectation patrimoine + algorithme validé) |
| Risques | Risque de fuite cross-bien si un cas de résolution est oublié dans la suite de tests — la check-list §5 de `securite-patrimoine.md` doit être intégralement couverte avant fusion |
| Critères GO (fin de sprint, = critère de fusion `main`) | ✅ 4/4 combinaisons de résolution testées et vertes · ✅ 0 accès cross-bailleur/cross-patrimoine/cross-bien détecté · ✅ Non-régression EF-12/US-21 confirmée · ✅ `mvn verify`/`ng test` verts, CI complète (CodeQL, SonarQube, Trivy, Gitleaks) verte · ✅ `CHANGELOG.md` et `docs/project-state.md` à jour avant fusion |

---

## Synthèse des points de contrôle PO

| Point de contrôle | Avant quoi | Bloquant |
|---------------------|-----------|----------|
| Arbitrage des valeurs `Bien.type` non mappables | Fin Sprint 1 | Oui |
| Validation de l'algorithme RM-98 (résolution priorité/exception) | Début Sprint 2 | **✅ Validé par le PO le 2026-06-21** — formule confirmée, RS-04 (rejet 400 `EXCLUSION` orpheline) et tolérance `INCLUSION` redondante actées |
| Confirmation du rôle autorisé à administrer la typologie (RS-05) | Début Sprint 1 | Non bloquant pour le code, mais à trancher avant la livraison de `/api/types-biens` en écriture |
| Comportement d'archivage d'un patrimoine avec affectations actives (RS-06) | Avant Sprint 3 | Non bloquant pour US-80→85, mais à trancher avant mise en production du lot |

## Ce que ce plan ne couvre pas (hors périmètre, par construction de cette analyse)

- Aucune ligne de code, aucun fichier `.sql` de migration n'a été produit — conformément à la contrainte de cette mission.
- Le déploiement (staging/production) de ce lot suivra la même discipline que les lots précédents (Gate Staging si applicable, CHANGELOG, project-state) — non détaillé ici, à reprendre dans un Plan d'Exécution d'implémentation classique une fois ce plan approuvé.
