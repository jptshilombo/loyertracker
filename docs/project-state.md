# Project State — LoyerTracker

## 1. Identification du projet

* Nom du projet : LoyerTracker
* Type de projet : application web de gestion locative bailleur-centree avec delegation fine par bien
* Version actuelle : 0.1.0-SNAPSHOT
* Depot : `/home/ubuntu/loyertracker`
* Branche active : `feat/s01-delegation-gestionnaire`
* Derniere mise a jour : 2026-06-08
* Agent ayant mis a jour le fichier : Claude Code — CGPA Governance Officer, consolidation delegation S02

## 2. Resume executif

LoyerTracker est un MVP de gestion locative structure par le CGPA. Le projet a ete demarre sous CGPA v1.0, puis enrichi avec des pratiques proches de CGPA v3.0 : gates documentes, DevSecOps, plan d'implementation, ADR, tests et CI. La migration cible est CGPA v3.0.1, dont l'ecart principal est l'obligation du Project State File et son maintien apres chaque etape significative.

Le socle technique est operationnel ou tres avance : backend Spring Boot, frontend Angular, Keycloak, PostgreSQL, Flyway, RLS, Nginx, Docker Compose, CI GitHub Actions, CodeQL, lint, scans de securite. Les phases de cadrage et DevSecOps sont validees jusqu'au Gate 06. Le projet est en Phase 7 Developpement. S01 est cloture documentairement. S02 est realise en backend pour US-20 a US-24 et dispose maintenant d'un frontend minimal bailleur/gestionnaire pour exploiter biens, baux et affectations.

## 3. Phase CGPA actuelle

* Phase actuelle : Phase 7 — Developpement
* Gate actuel : Gate 07 — Developpement
* Statut du gate : non statue
* Dernier gate statue : Gate 06 — DevSecOps
* Statut du dernier gate : Go, ratifie le 2026-06-06
* Decision actuelle : GO sous reserve
* Justification : les phases 0 a 6 disposent de livrables et decisions Go, le developpement est autorise. La resynchronisation documentaire minimale et la cloture S01 ont ete realisees le 2026-06-07. Le Plan d'Execution S02 backend a ete approuve, execute puis accepte en GO sous reserve. Le frontend S02 minimal a ete approuve, execute puis accepte en GO sous reserve le 2026-06-07. La poursuite reste sous reserve de valider les points runtime restants et de produire le Plan d'Execution S03 avant tout nouveau developpement.

## 4. D'ou l'on vient

* Idee initiale : centraliser la gestion locative pour un bailleur, avec delegation fine par bien a des gestionnaires.
* Livrables deja valides :
  * fiche idee, Gate 0 Go ;
  * expression de besoin, Gate 1 Go ;
  * etude de faisabilite, Gate 2 Go ;
  * cahier des charges, Gate 3 Go ;
  * dossier d'architecture et ADR, Gate 4 Go ;
  * backlog et sprint planning S01, Gate 5 Go ;
  * plan DevSecOps, checklists et Gate 6 Go.
* Decisions structurantes prises :
  * monolithe modulaire Spring Boot ;
  * Angular SPA derriere Nginx ;
  * Keycloak pour AuthN et RBAC grossier ;
  * autorisation fine ReBAC dans l'application ;
  * cloisonnement par `bailleur_id` et PostgreSQL RLS `FORCE` ;
  * fonctions PostgreSQL `SECURITY DEFINER` pour resolution tenant et predicats d'autorisation ;
  * port/adaptateur pour l'Admin API Keycloak gestionnaire ;
  * pseudonymisation RGPD pour l'effacement locataire ;
  * DevSecOps shift-left avec CI, SCA, secrets, image scan, SAST et lint.
* Phases deja franchies : phases CGPA cible 0, 1, 2, 3, 4, 5 et 6.
* Principales fonctionnalites deja realisees :
  * stack Docker Compose ;
  * realm Keycloak ;
  * API Spring Boot securisee JWT ;
  * schéma Flyway complet V1 a V4 ;
  * inscription bailleur ;
  * invitation gestionnaire tokenisee 72h ;
  * acceptation d'invitation avec creation/reutilisation gestionnaire ;
  * moteur d'autorisation fine pour biens ;
  * CRUD biens backend ;
  * creation et historique des baux backend ;
  * creation, revocation et rotation des affectations backend ;
  * frontend Angular minimal pour biens, baux et affectations S02 ;
  * CI/CD de build, tests, scans et packaging Docker.

## 5. Ou l'on est

* Etat actuel du projet : socle technique, securite et premier lot metier EP-03 livres avec backend complet et frontend minimal.
* Fonctionnalites terminees ou tres avancees :
  * US-01 stack Docker Compose ;
  * US-02 realm Keycloak et JWT resource server ;
  * US-03 Flyway schema complet, index, RLS ;
  * US-04 CI, tests, scans, CodeQL, lint ;
  * US-10 inscription bailleur ;
  * US-11 invitation gestionnaire ;
  * US-12 acceptation invitation avec faux IdP en test ;
  * US-13 autorisation fine ReBAC et tests cross-tenant ;
  * US-20 CRUD biens backend avec archivage ;
  * US-21 creation de bail actif sur bien libre avec unicite 409 ;
  * US-22 historique des baux d'un bien ;
  * US-23 creation d'affectation active avec honoraires ;
  * US-24 revocation, rotation et historique des affectations ;
  * frontend S02 minimal bailleur : liste, creation, modification, archivage des biens, baux, historiques et affectations ;
  * frontend S02 minimal gestionnaire : biens affectes, historique baux et creation de bail.
* Fonctionnalites en cours :
  * validation runtime OIDC/PKCE et Admin API Keycloak ;
  * synchronisation documentaire post-frontend S02.
* Fonctionnalites non commencees :
  * interfaces frontend avancees pour biens, baux et affectations ;
  * paiements, garanties, honoraires ;
  * alertes et batch ;
  * dashboards metier ;
  * audit log applicatif ;
  * export/effacement RGPD ;
  * QA/recette, production readiness, exploitation.
* Dette technique :
  * adaptateur `KeycloakGestionnaireIdentityProvider` teste en runtime R6 mais non valide : acceptation invitation KO 401 cote Admin API ;
  * endpoints backend S02 presents mais OpenAPI non produit ;
  * observabilite limitee aux logs/Actuator, sans metriques ni alerting ;
  * image Nginx dev encore root ;
  * registry, CD, backup/restore et rollback formel absents.
* Dette documentaire :
  * certains documents historiques gardent la trace CGPA v1.0 ou ancienne numerotation ;
  * R6 documentee partiellement : OIDC/PKCE/API OK, Admin API gestionnaire KO ;
  * rapport d'execution S01 separe non formalise ;
  * certains livrables historiques ne refletent pas encore S02, sauf Project State, rapport d'execution S02 backend et rapport frontend S02.
* Blocages connus : R6 Admin API gestionnaire bloquee par configuration Keycloak/runtime ; blocage de gouvernance pour demarrer S03 sans Plan d'Execution approuve.


## 6. Ou l'on va

* Prochaine phase : maintien en Phase 7 — Developpement.
* Prochaine etape recommandee : corriger la configuration Admin API Keycloak pour cloturer R6, ou arbitrer explicitement un report avant Plan d'Execution S03.
* Objectif du prochain Plan d'Execution : cadrer le prochain lot avant toute modification, probablement S03 paiements/garanties si S02 complet est accepte.
* Priorites immediates :
  * corriger et revalider l'Admin API gestionnaire Keycloak ;
  * faire une revue PO du frontend S02 minimal et des rapports d'execution ;
  * produire le Plan d'Execution S03 avant tout nouveau code ;
  * maintenir le Project State apres chaque etape significative.
* Dependances a lever :
  * client confidentiel/service account Keycloak avec droits minimaux et injection des variables Admin API dans le conteneur `api` ;
  * decision PO sur validation de S02 complet avant S03 ;
  * criteres Gate 07 cible.


## 7. Architecture et choix techniques

* Stack backend : Java 21, Spring Boot 3.5.14, Spring Security, OAuth2 Resource Server, JPA, Flyway, PostgreSQL driver, Testcontainers.
* Stack frontend : Angular 20, keycloak-angular 20, keycloak-js, RxJS, Karma/Jasmine, ESLint.
* Base de donnees : PostgreSQL 16, schema Flyway V1 a V4, RLS `FORCE`, index uniques metier.
* IAM / Authentification : Keycloak 24, OIDC/PKCE, roles realm `BAILLEUR` et `GESTIONNAIRE`.
* DevOps / CI-CD : GitHub Actions CI, CodeQL, Gitleaks, Trivy, OWASP Dependency-Check informatif, Docker build.
* Hebergement cible : self-hosting conteneurise, staging/prod a definir.
* Choix techniques valides : monolithe modulaire, Nginx point d'entree unique, RLS defense en profondeur, ReBAC applicatif, port IdP gestionnaire.
* Choix techniques a confirmer : registry cible, CD staging/prod, backup/restore, chiffrement au repos, observabilite centralisee.

## 8. Securite et conformite

* Exigences securite : AuthN centralisee, RBAC, ReBAC, RLS, secrets hors depot, TLS, scans CI, RGPD by design.
* Donnees sensibles : identites bailleur, gestionnaire, locataire, emails, donnees financieres de baux/paiements/garanties.
* Mesures deja prevues :
  * RLS `ENABLE` + `FORCE` ;
  * Gitleaks et Trivy ;
  * CodeQL Java/TypeScript ;
  * CORS restreint ;
  * CSP et headers Nginx ;
  * logs sans PII ;
  * pseudonymisation RGPD prevue.
* Risques securite ouverts :
  * validation live Keycloak Admin API executee mais KO en 401 ;
  * endpoints metier futurs a couvrir par tests cross-tenant ;
  * role SQL privilegie en test pouvant contourner RLS, compense par filtres applicatifs explicites et tests ;
  * backup, chiffrement au repos et restauration non traites ;
  * observabilite securite non centralisee.
* Actions de mitigation :
  * correction runtime R6 Admin API puis retest complet ;
  * tests d'autorisation obligatoires par endpoint S03+ ;
  * revue securite des fonctions `SECURITY DEFINER` ;
  * plan production readiness avant staging/prod.

## 9. DevSecOps et qualite

* Pipeline CI/CD : CI GitHub Actions active sur push/PR ; CD non implemente.
* Tests unitaires : backend et frontend presents.
* Tests d'integration : backend avec PostgreSQL Testcontainers.
* Tests securite : RLS, ReBAC, 401/403, cross-tenant/cross-affectation S02, scans secrets/dependances/images, CodeQL.
* Couverture de test : `mvn verify` backend passe avec 36 tests verts (2026-06-08, ajout du test cross-bailleur sur les ecritures d'affectation) ; gate JaCoCo cible sur `com.loyertracker.securite` respecte ; frontend passe avec 14 tests Karma headless (non impacte par la consolidation delegation).
* Qualite du code : Spotless Maven, ESLint Angular, builds CI verts historiquement ; verification backend et frontend locale verte le 2026-06-07.
* Observabilite : logs JSON ECS backend, access logs JSON Nginx, Actuator health/info ; pas encore de metriques ni alerting.

## 10. Score de maturite CGPA

* Gouvernance : 3/4
* Architecture : 3/4
* Securite : 3/4
* DevSecOps : 3/4
* Qualite : 3/4
* Documentation : 3/4
* Exploitation : 1/4
* Valeur metier : 3/4
* Score global : 22/32
* Commentaire : niveau Partiel avance. Le projet est solide pour poursuivre le developpement et dispose maintenant d'un premier lot metier S02 teste avec frontend minimal. Il n'est pas pret pour staging/prod et doit conserver la discipline CGPA : plan approuve avant S03.

## 11. Historique des decisions

| Date | Decision | Motif | Responsable | Impact |
| ---- | -------- | ----- | ----------- | ------ |
| 2026-06-03 | Gate 0 Go | Opportunite validee, modele bailleur/gestionnaire/affectation pose | jptshilombo@gmail.com | Passage expression de besoin |
| 2026-06-04 | Gate 1 Go | Besoin et perimetre MVP valides | jptshilombo@gmail.com | Passage faisabilite |
| 2026-06-04 | Gate 2 Go | Faisabilite technique/orga/eco confirmee | jptshilombo@gmail.com | Passage CDC |
| 2026-06-04 | Gate 3 Go | CDC complet avec EF/ENF et criteres testables | jptshilombo@gmail.com | Passage architecture |
| 2026-06-04 | Gate 4 Go | Architecture validee, verrou initial de codage leve | jptshilombo@gmail.com | Passage backlog/developpement gouverne |
| 2026-06-04 | Gate 5 Go | Backlog et sprint planning S01 valides | jptshilombo@gmail.com | Passage DevSecOps/S01 |
| 2026-06-06 | Gate 6 Go | CI, securite, secrets, observabilite de base valides | jptshilombo@gmail.com | Ouverture developpement |
| 2026-06-07 | ADR-09 acceptee | Resolution tenant sous RLS via fonctions `SECURITY DEFINER` | jptshilombo@gmail.com | Debloque US-11/12/13 |
| 2026-06-07 | ADR-10 acceptee | Encapsulation Keycloak Admin API via port/adaptateur | jptshilombo@gmail.com | Debloque US-12 |
| 2026-06-08 | Rapport de Reprise CGPA v3.0.1 — GO sous reserve (consolidation S02) / NO GO (nouveau lot S03) | Livrables S02 non versionnes + faille autorisation Critique a traiter avant tout nouveau lot | jptshilombo@gmail.com | Autorise la consolidation/securisation de la delegation S02 |
| 2026-06-08 | Correctif Fix-1 approuve (controle d'autorisation applicatif sur revoquer) | Faille cross-bailleur revelee par un test ; Option A initiale (RLS seule) invalidee | jptshilombo@gmail.com | Ferme la faille sans migration ; ouvre une investigation RLS |

## 12. Historique des etapes realisees

| Date | Etape | Resultat | Fichiers impactes | Agent |
| ---- | ----- | -------- | ----------------- | ----- |
| 2026-06-04 | Cadrage CGPA initial | Phases 0 a 5 documentees et gates Go | `docs/cgpa/*` | Agents IA / PO |
| 2026-06-06 | Phase DevSecOps | Gate 6 Go, CI et securite en place | `.github/`, `backend/`, `frontend/`, `infra/`, `docs/cgpa/07-devsecops/` | Agents IA / PO |
| 2026-06-06 | Inscription bailleur | US-10 developpee et testee | `backend/src/main/java/com/loyertracker/bailleur/`, `frontend/src/app/bailleur/` | Agents IA |
| 2026-06-06 | Migration frontend | Angular/keycloak-angular portes en version 20 | `frontend/` | Agents IA |
| 2026-06-07 | Invitation gestionnaire | US-11 developpee et testee | `backend/src/main/java/com/loyertracker/comptes/` | Agents IA |
| 2026-06-07 | Acceptation invitation | US-12 developpee avec faux IdP en test | `backend/src/main/java/com/loyertracker/comptes/` | Agents IA |
| 2026-06-07 | Autorisation fine | US-13 developpee et testee | `backend/src/main/java/com/loyertracker/securite/`, migrations V3 | Agents IA |
| 2026-06-07 | SAST/lint | CodeQL, Spotless, ESLint en CI | `.github/workflows/`, `backend/pom.xml`, `frontend/` | Agents IA |
| 2026-06-07 | Migration CGPA v3.0.1 priorite 1 | Creation du Project State File | `docs/project-state.md` | CGPA Migration Auditor v3.0.1 / Codex |
| 2026-06-07 | Resynchronisation documentaire minimale et cloture S01 | R1 cloturee, R6 clarifiee, S01 cloture documentaire | `docs/cgpa/07-devsecops/`, `docs/cgpa/06-planification-agile/sprint-planning-s01.md`, `README.md`, `docs/project-state.md` | Codex |
| 2026-06-07 | Execution S02 backend | US-20 a US-24 realisees et verifiees par `mvn verify` | `backend/src/main/java/com/loyertracker/biens/`, `backend/src/main/java/com/loyertracker/baux/`, `backend/src/main/java/com/loyertracker/affectations/`, migration V4, tests S02, `docs/project-state.md` | Codex |
| 2026-06-07 | Approbation S02 backend | GO sous reserve accepte par le PO/CGPA | `docs/project-state.md`, `docs/cgpa/06-planification-agile/rapport-execution-s02.md` | PO / Codex |
| 2026-06-07 | Execution frontend S02 minimal | Dashboards bailleur/gestionnaire branches sur les endpoints S02, lint/build/tests verts | `frontend/src/app/bailleur/dashboard/`, `frontend/src/app/gestionnaire/dashboard/`, `frontend/src/app/core/s02/`, `docs/project-state.md` | Codex |
| 2026-06-07 | Approbation frontend S02 minimal | GO sous reserve accepte par le PO/CGPA | `docs/project-state.md`, `docs/cgpa/06-planification-agile/rapport-execution-frontend-s02.md` | PO / Codex |
| 2026-06-07 | Validation runtime R6 | OIDC/PKCE et API protegee OK ; Admin API gestionnaire KO 401 ; R6 non cloturee | `docs/cgpa/07-devsecops/rapport-validation-r6.md`, `docs/cgpa/07-devsecops/`, `docs/project-state.md` | Codex |
| 2026-06-08 | Consolidation delegation S02 — durcissement autorisation affectations | ReBAC `peutAccederBien` sur `creer` (403 cross-bailleur) ; controle de propriete sur `revoquer` (404 cross-bailleur) ; test d'integration cross-bailleur ajoute ; `mvn verify` vert 36 tests ; lot S02 versionne | `backend/.../affectations/AffectationController.java`, `backend/.../affectations/AffectationService.java`, `backend/.../s02/S02BiensBauxAffectationsIntegrationTest.java`, `docs/project-state.md` | Claude Code |

## 13. Risques ouverts

| Risque | Niveau | Impact | Mitigation | Statut |
| ------ | ------ | ------ | ---------- | ------ |
| Documentation CGPA residuelle obsolescente | Mineur | Confusion possible sur quelques mentions historiques v1.0 | Corriger progressivement hors historique de decision | Ouvert |
| Admin API Keycloak gestionnaire KO en runtime | Majeur | US-12 echoue a l'acceptation invitation / creation gestionnaire | Creer/configurer client confidentiel ou service account, injecter variables Admin API dans `api`, reexecuter R6 | Ouvert |
| Tests d'autorisation incomplets sur futurs endpoints | Critique | Fuite cross-bailleur/cross-affectation | Imposer tests d'autorisation par endpoint dans S03+ ; ecritures d'affectation desormais couvertes | En reduction |
| Faille cross-bailleur sur `POST /affectations/{id}/revocation` (200 au lieu de 404/403) | Critique | Un bailleur pouvait revoquer l'affectation d'un autre bailleur | Corrige le 2026-06-08 (controle de propriete applicatif dans `AffectationService.revoquer`) + test de non-regression | Ferme |
| RLS n'a pas isole l'affectation lors d'un acces par-id sous tenant tiers | Majeur | Defense en profondeur (2e couche RLS) non effective sur ce chemin ; l'autorisation applicative la couvre desormais | Investiguer la propagation du contexte tenant (`set_config` local vs connexion JPA) sur les acces par-id ; verifier les autres modules | Ouvert |
| Pas de Plan d'Execution S03 approuve | Critique gouvernance | Codage non conforme CGPA v3.0.1 | Produire et faire approuver un Plan d'Execution standard | Ouvert |
| Pas de staging/prod/CD/backup | Majeur | Non readiness production | Traiter en phase production readiness | Differe |
| Interfaces frontend S02 minimales seulement | Mineur produit | Ergonomie suffisante pour reprise mais pas pour recette large | Prevoir ameliorations UX avant beta | Ouvert |
| OpenAPI S02 absent | Mineur | Contrat API moins explicite | Generer/documenter OpenAPI avant recette large | Ouvert |

## 14. Prochaine action claire

La delegation S02 a ete consolidee et securisee le 2026-06-08 (faille cross-bailleur fermee, lot versionne). Prochaines etapes recommandees, par priorite : (1) investiguer la non-isolation RLS sur les acces par-id (risque Majeur ouvert) et verifier les autres modules ; (2) corriger la configuration Admin API Keycloak et reexecuter R6 jusqu'a cloture, ou arbitrer explicitement le report de R6 ; (3) produire un Plan d'Execution S03 paiements/garanties avant tout nouveau code. Aucun nouveau developpement ne doit demarrer sans approbation explicite.
