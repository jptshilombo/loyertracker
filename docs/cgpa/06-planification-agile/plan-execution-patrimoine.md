# Plan d'Exécution CGPA — Introduction de la notion de Patrimoine

| Champ | Valeur |
|-------|--------|
| Statut | **✅ Approuvé — GO, le 2026-06-21**. Sprint 1 et Sprint 2 implémentés et clôturés côté `main` (PR #72/#73, PR #74). **Sprint 3 cadré en détail le 2026-06-24** (état réel du code vérifié) — **en attente d'un arbitrage PO sur le périmètre frontend et du point de contrôle Kickoff Sprint 3 (GO/NO GO distinct), aucun code produit**. Chaque sprint reste un point de contrôle GO/NO GO ; aucun codage ne doit dépasser les livrables du sprint en cours sans nouveau point de contrôle. |
| Date | 2026-06-21 (proposé) — **approuvé le 2026-06-21** |
| Décision liée | D-PAT-001 / ADR-11 |
| Backlog couvert | EP-09, US-80→85 (`addendum-patrimoine-backlog.md`) |
| Niveau | Niveau 3 (changement structurant du modèle de données + sécurité) |

> Conformément à la règle CGPA v3.0, **« Codage suspendu : Plan d'Exécution requis avant modification du code »** : ce plan a reçu l'**approbation explicite du PO le 2026-06-21**, sprint par sprint (chaque sprint reste un point de contrôle GO/NO GO — le franchissement du Sprint 1 ne vaut pas approbation automatique des Sprints 2/3, dont les critères GO de fin de sprint conditionnent la poursuite). RM-98 (réserve bloquante Sprint 2), RS-05 (administration de la typologie, rôle BAILLEUR existant, aucun nouveau rôle Keycloak) et RS-06 (archivage d'un patrimoine avec affectations actives, bloqué en 400) ont également été tranchés par le PO le 2026-06-21.

---

## Sprint 1 — Patrimoines & modèle de données

**Objectif :** poser l'entité `Patrimoine`, la typologie administrable, et le rattachement obligatoire des biens, sans toucher à l'affectation.

| Élément | Détail |
|---------|--------|
| Statut | **✅ Implémenté et mergé via PR #72 ; clôture technique préparée le 2026-06-21 — GO technique / sprint clôturable.** Kickoff confirmé par le PO le 2026-06-21 — GO sans réserve. |
| Stories couvertes | US-80 (patrimoines), US-81 (typologie), US-82 (rattachement bien→patrimoine) |
| Livrables | Entité/table `Patrimoine` + RLS ; référentiel `TypeBien` administrable ; `Bien.patrimoineId` NOT NULL ; migration de données (patrimoine par défaut par bailleur + rattachement + mapping des `Bien.type` libres existants) ; endpoints `/api/patrimoines`, `/api/types-biens` ; tests unitaires + intégration ; non-régression complète des endpoints `biens` existants |
| Dépendances | Aucune dépendance externe ; nécessite la liste des valeurs distinctes actuellement présentes dans `Bien.type` (à extraire en base avant de cadrer le mapping, hors périmètre de cette analyse documentaire) |
| Risques | Migration de données irréversible si mal cadrée (cf. ADR-11 §Risques, addendum CDC §4.5) ; valeurs de `type` non mappables nécessitant un arbitrage manuel |
| Critères GO (fin de sprint) | ✅ 100 % des biens existants rattachés à un patrimoine, 0 orphelin · ✅ 100 % des `Bien.type` migrés ou liste des cas non mappables arbitrée par le PO · ✅ Suite de tests existante verte sans régression (`mvn verify` 78 tests, frontend Karma 41 tests le 2026-06-21) · ✅ RLS `ENABLE`+`FORCE` vérifiée sur `Patrimoine` (test de verrou de fidélité, même patron que PR #18) · Décision de clôture : `sprint-1-patrimoine-cloture.md` |

---

## Sprint 2 — Gestion des affectations (patrimoine + priorité)

**Objectif :** permettre l'affectation au niveau patrimoine et trancher/implémenter l'algorithme de priorité avec le niveau bien.

| Élément | Détail |
|---------|--------|
| Stories couvertes | US-84 (affectation patrimoine), début US-85 (priorité — partie résolution) |
| Pré-requis bloquant | ~~Validation explicite du PO sur l'algorithme RM-98~~ **✅ Levé — validé par le PO le 2026-06-21** (résolution patrimoine ∪ inclusion − exclusion confirmée telle que proposée ; RS-04 rejet 400 `EXCLUSION` orpheline ; `INCLUSION` redondante tolérée). Détail : ADR-11 §Décision point 5, `securite-patrimoine.md` §3/§7/§9. Pré-requis restant avant code : approbation du présent Plan d'Exécution par le PO (statut global, cf. bandeau) |
| Livrables | `Affectation.patrimoineId` (nullable, exclusif de `bienId`) + `typeException` ; extension `AuthorizationService` (nouveau prédicat `estGestionnaireAffectePatrimoineActif`, fonction `SECURITY DEFINER` associée) ; endpoint `/api/affectations` étendu (validation 400 si patrimoineId+bienId simultanés ou absents) ; `/api/patrimoines/{id}/affectations` ; extension de l'endpoint d'archivage livré en Sprint 1 (US-80, `PUT/DELETE /api/patrimoines/{id}`) pour y intégrer la garde **RS-06** (rejet 400 si affectation patrimoine `ACTIVE`), dès que le modèle `Affectation.patrimoineId` existe |
| Dépendances | Sprint 1 (Patrimoine et rattachement biens doivent exister) |
| Risques | Algorithme validé (2026-06-21) — risque résiduel : implémentation non conforme à RM-98/RS-04 tel que validé (cf. RS-01→RS-04, `securite-patrimoine.md`), à couvrir par les tests d'autorisation dédiés avant fusion ; jointure supplémentaire sur les dashboards (ENF-06) ; ne pas oublier de rétrofitter la garde RS-06 sur l'endpoint d'archivage déjà mergé en Sprint 1 |
| Critères GO (fin de sprint) | ✅ Algorithme RM-98 validé par le PO et documenté avant tout commit applicatif **(acquis le 2026-06-21)** · ✅ Affectation patrimoine fonctionnelle avec héritage dynamique vérifié (ajout d'un bien après affectation → accès immédiat) · ✅ 0 régression sur la suite d'autorisation existante · ✅ Performance dashboard < 2 s maintenue (ENF-06) sur un jeu de test ≥ 50 biens · ✅ Archivage d'un patrimoine avec affectation patrimoine `ACTIVE` rejeté en 400 (RS-06, **validé par le PO le 2026-06-21**) |

---

## Sprint 3 — Contrats actifs, sécurité, tests

**Objectif :** finaliser les exceptions fines, clore la non-régression du contrat actif, et durcir la suite de tests de sécurité avant fusion finale.

> **Cadrage détaillé produit le 2026-06-24** (post-Sprint 2, état réel du code vérifié) — **statut : cadré, en attente du point de contrôle Kickoff Sprint 3 (GO/NO GO distinct)**. Aucun code produit à ce stade.

| Élément | Détail |
|---------|--------|
| Stories couvertes | Fin US-85 (exceptions `INCLUSION`/`EXCLUSION`), US-83 reclassée en critère de non-régression (pas un développement) |
| État réel vérifié (2026-06-24) | `Affectation` (`backend/.../affectations/Affectation.java`) n'a **aucune** colonne `typeException` ; `gestionnaire_affecte_actif`/`biens_affectes_gestionnaire` (V13) combinent bien et patrimoine par un simple **OR**, sans notion d'exception ni de priorité explicite (équivalent à une `INCLUSION` implicite partout) ; `AuthorizationService.estGestionnaireAffecteActif` n'est qu'un appel direct à la fonction SQL — **aucun changement Java attendu**, toute la logique de résolution vit dans la fonction `SECURITY DEFINER`. |
| Livrables techniques | **Migration V15** : colonne `affectation.type_exception` (`INCLUSION`\|`EXCLUSION`, nullable, `CHECK (type_exception IS NULL OR bien_id IS NOT NULL)`) ; backfill des affectations bien existantes à `INCLUSION` (non-régression US-23/24) ; `CREATE OR REPLACE FUNCTION gestionnaire_affecte_actif` réécrite en résolution **à priorité** (affectation bien `ACTIVE` présente → verdict = son `type_exception`, court-circuite la patrimoine ; sinon → patrimoine `ACTIVE` ; sinon → faux) ; même réécriture pour `biens_affectes_gestionnaire`. **Backend applicatif** : enum `TypeException`, `Affectation.surBien(...)` étendu, `AffectationRequest`/`AffectationService` : accepte `typeException` (uniquement si `bienId` renseigné), **RS-04** rejette en 400 toute `EXCLUSION` sans affectation patrimoine `ACTIVE` correspondante pour ce gestionnaire, tolère une `INCLUSION` redondante (idempotente). |
| Livrables gouvernance | Suite de tests d'autorisation couvrant les 4 combinaisons (`securite-patrimoine.md` §5 : patrimoine seul · patrimoine+`INCLUSION` redondante · patrimoine+`EXCLUSION` · bien seul sans patrimoine) ; confirmation non-régression EF-12/EF-96 (un seul contrat actif par bien, inchangé) ; documentation OpenAPI des endpoints `/api/affectations` étendus ; mise à jour `CHANGELOG.md` `[Non publié]` |
| Périmètre frontend | **✅ Tranché par le PO le 2026-06-24 — backend-only**, cohérent avec « Option A backend-first » de Sprint 1/2. Aucune UI livrée pour l'affectation patrimoine ni les exceptions `INCLUSION`/`EXCLUSION` (seul `POST /api/affectations` direct le permet) ; différé à un lot ultérieur. Le Hotfix séparé (`plan-execution-hotfix-bien-patrimoine-frontend.md`) ne couvre que la création de biens, pas l'affectation patrimoine. |
| Dépendances | Sprint 2 (affectation patrimoine + algorithme validé) ; **indépendant du Hotfix bien/patrimoine** (`plan-execution-hotfix-bien-patrimoine-frontend.md`), qui ne touche pas l'algorithme de résolution |
| Risques | Risque de fuite cross-bien si un cas de résolution est oublié dans la suite de tests — la check-list §5 de `securite-patrimoine.md` doit être intégralement couverte avant fusion ; réécriture de `gestionnaire_affecte_actif`/`biens_affectes_gestionnaire` = fonctions déjà en production (V13), donc changement de comportement sur fonctions existantes (et non un simple ajout) — non-régression complète de la suite d'autorisation S02/S03/S04 exigée, pas seulement des nouveaux tests Patrimoine |
| Critères GO (fin de sprint, = critère de fusion `main`) | ✅ 4/4 combinaisons de résolution testées et vertes · ✅ 0 accès cross-bailleur/cross-patrimoine/cross-bien détecté · ✅ Non-régression EF-12/US-21 **et** non-régression complète S02/S03/S04 (suite d'autorisation existante) confirmées · ✅ `mvn verify`/`ng test` verts, CI complète (CodeQL, SonarQube, Trivy, Gitleaks) verte · ✅ `CHANGELOG.md` et `docs/project-state.md` à jour avant fusion |

---

## Synthèse des points de contrôle PO

| Point de contrôle | Avant quoi | Bloquant |
|---------------------|-----------|----------|
| Kickoff Sprint 1 (GO/NO GO de démarrage, distinct du GO global du plan) | Début Sprint 1 | **✅ Confirmé par le PO le 2026-06-21** — GO sans réserve |
| Arbitrage des valeurs `Bien.type` non mappables | Fin Sprint 1 | Oui |
| Validation de l'algorithme RM-98 (résolution priorité/exception) | Début Sprint 2 | **✅ Validé par le PO le 2026-06-21** — formule confirmée, RS-04 (rejet 400 `EXCLUSION` orpheline) et tolérance `INCLUSION` redondante actées |
| Confirmation du rôle autorisé à administrer la typologie (RS-05) | Début Sprint 1 | **✅ Validé par le PO le 2026-06-21** — rôle `BAILLEUR` existant, aucun nouveau rôle Keycloak créé |
| Comportement d'archivage d'un patrimoine avec affectations actives (RS-06) | Avant Sprint 3 | **✅ Validé par le PO le 2026-06-21** — archivage bloqué (400) tant qu'une affectation patrimoine `ACTIVE` existe ; révocation explicite préalable requise (cohérent EF-22) |
| Périmètre frontend de Sprint 3 (backend-only comme S1/S2, ou UI minimale affectation patrimoine + exceptions) | Avant kickoff Sprint 3 | **✅ Tranché par le PO le 2026-06-24 — backend-only**, cohérent avec Sprint 1/2 ; l'UI d'affectation patrimoine et d'exceptions `INCLUSION`/`EXCLUSION` est différée à un lot ultérieur |
| Kickoff Sprint 3 (GO/NO GO de démarrage, distinct du cadrage) | Début Sprint 3 | **Ouvert** — cadrage produit le 2026-06-24, kickoff non encore demandé |

## Ce que ce plan ne couvre pas (hors périmètre, par construction de cette analyse)

- Aucune ligne de code, aucun fichier `.sql` de migration n'a été produit — conformément à la contrainte de cette mission.
- Le déploiement (staging/production) de ce lot suivra la même discipline que les lots précédents (Gate Staging si applicable, CHANGELOG, project-state) — non détaillé ici, à reprendre dans un Plan d'Exécution d'implémentation classique une fois ce plan approuvé.
