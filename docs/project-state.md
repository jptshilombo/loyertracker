# Project State — LoyerTracker

## 1. Identification du projet

* Nom du projet : LoyerTracker
* Type de projet : application web de gestion locative bailleur-centree avec delegation fine par bien
* Version actuelle : 0.1.0-SNAPSHOT
* Depot : `/home/ubuntu/loyertracker`
* Branche active : `main` (S04 complet backend + frontend — backend via PR #11/#12/#14, frontend honoraires/alertes/audit via PR #16 merge `99e3215`)
* Derniere mise a jour : 2026-06-10
* Agent ayant mis a jour le fichier : Claude Code — CGPA Governance Officer, synchronisation post-merge Frontend S04 (PR #16)

## 2. Resume executif

LoyerTracker est un MVP de gestion locative structure par le CGPA. Le projet a ete demarre sous CGPA v1.0, puis enrichi avec des pratiques proches de CGPA v3.0 : gates documentes, DevSecOps, plan d'implementation, ADR, tests et CI. La migration cible est CGPA v3.0.1, dont l'ecart principal est l'obligation du Project State File et son maintien apres chaque etape significative.

Le socle technique est operationnel ou tres avance : backend Spring Boot, frontend Angular, Keycloak, PostgreSQL, Flyway, RLS, Nginx, Docker Compose, CI GitHub Actions, CodeQL, lint, scans de securite. Les phases de cadrage et DevSecOps sont validees jusqu'au Gate 06. Le projet est en Phase 7 Developpement. S01 est cloture documentairement. S02 est realise en backend pour US-20 a US-24 et dispose d'un frontend minimal bailleur/gestionnaire pour exploiter biens, baux et affectations. S03 (paiements & garanties, US-30/31/32) est realise (backend + frontend) et clos. S04 backend est complet : lot 1 honoraires (US-40) integre via la PR #11, lot 2 alertes & consultation d'audit (US-50/51/52, US-62) integre via la PR #12, et l'alerte PREAVIS (US-50, EF-62) — critere d'acceptation fige par le PO (bande de preavis a 90 jours, exclusive de FIN_BAIL) — integree via la PR #14. Le reste fonctionnel backend S04 est ainsi clos. Le frontend S04 (consoles honoraires, alertes et journal d'audit, bailleur et gestionnaire) a ete integre via la PR #16 : S04 est desormais complet backend + frontend. Ne subsistent que des suivis techniques (conversion des tests au double datasource, smoke test runtime).

## 3. Phase CGPA actuelle

* Phase actuelle : Phase 7 — Developpement
* Gate actuel : Gate 07 — Developpement
* Statut du gate : non statue
* Dernier gate statue : Gate 06 — DevSecOps
* Statut du dernier gate : Go, ratifie le 2026-06-06
* Decision actuelle : GO sous reserve
* Justification : les phases 0 a 6 disposent de livrables et decisions Go, le developpement est autorise. La resynchronisation documentaire minimale et la cloture S01 ont ete realisees le 2026-06-07. Le Plan d'Execution S02 backend a ete approuve, execute puis accepte en GO sous reserve. Le frontend S02 minimal a ete approuve, execute puis accepte en GO sous reserve le 2026-06-07. La reserve R6 (Admin API Keycloak gestionnaire) a ete corrigee et reexecutee avec succes le 2026-06-09 (client service account dedie, acceptation invitation 201, gestionnaire cree) : R6 est clotûree. Le Plan d'Execution S03 (Niveau 3) a ete approuve, execute (US-30/31/32, lot backend) puis integre dans `main` via la PR #7 (CI verte, merge commit `2f5c743`) le 2026-06-09. Le lot backend a ensuite passe une QA dediee (verdict GO, 44/44 verts) ; le Plan d'Execution Frontend S03 (Niveau 3) a ete approuve et execute le 2026-06-09 : volet IHM US-31/32 (pointage + garanties, bailleur et gestionnaire) plus deux correctifs backend issus de la QA (EN_RETARD automatise via V7, RECU >= attendu impose). Tests verts (backend 46, frontend 18) ; integre dans `main` via la PR #9 (CI verte, merge commit `a1f60dd`) le 2026-06-09. S03 (backend + frontend) est clos. Le Plan d'Execution S04 (Niveau 3) a ensuite ete approuve le 2026-06-10 avec les arbitrages A (backend d'abord, frontend S04 reporte), B (PREAVIS reporte), C (honoraires recalcules au pointage + batch) et D (2 PR). Lot 1 honoraires (US-40) execute et integre via la PR #11 (CI verte, merge commit `8ac0b49`) ; lot 2 alertes & consultation d'audit (US-50/51/52, US-62) execute et integre via la PR #12 (CI verte, merge commit `0f4f47c`). `mvn verify` vert a chaque lot (49 puis 53 tests). Le critere d'acceptation PREAVIS (US-50, EF-62) a ensuite ete fige par le PO (bande de preavis a 90 jours, bornee a `]J+60 ; J+90]` pour exclusivite avec FIN_BAIL) ; le Plan d'Execution PREAVIS (Niveau 1) a ete approuve puis execute et integre via la PR #14 (CI verte, merge commit `0cd3539`) le 2026-06-10 — migration V10 `generer_alertes(p_preavis_jours integer DEFAULT 90)`, `mvn verify` vert 54 tests, `SchemaMigrationTest` 10 migrations. S04 backend est desormais complet (PREAVIS inclus). Le Plan d'Execution Frontend S04 (Niveau 3) a ensuite ete approuve le 2026-06-10 avec les defauts A (3 consoles en un lot), B (boutons d'action EN_ATTENTE/PAYE pour les honoraires), C (alertes NON_LUE en tete) et D (audit liste brute) ; il a ete execute et integre via la PR #16 (CI verte, merge commit `99e3215`) le 2026-06-10 — service `core/s04`, composants honoraires/alertes/audit cables aux deux dashboards, sans aucune modification backend ; `lint`/`build` verts et `ng test` 27 verts (18 + 6 service + 3 composants). S04 est ainsi complet backend + frontend. La reserve residuelle porte uniquement sur la conversion des tests d'integration au double datasource et le smoke test runtime sous le role `loyertracker_api` — chacun a cadrer par un Plan d'Execution avant tout nouveau code.

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
  * schéma Flyway V1 a V10 (schema initial, resolution tenant, predicats, helpers S02, role applicatif RLS, echeances/EN_RETARD S03, honoraires S04, alertes S04, alerte PREAVIS S04) ;
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
  * frontend S02 minimal gestionnaire : biens affectes, historique baux et creation de bail ;
  * US-30 generation des loyers attendus a terme echu (batch idempotent, Annexe A.3) ;
  * US-31 pointage des loyers (RECU/PARTIEL/EN_RETARD/IMPAYE, reste du, historique ; EN_RETARD automatise via V7, controle RECU >= attendu) ;
  * US-32 depot et restitution de garantie (DETENU -> RESTITUE_PARTIEL -> RESTITUE_TOTAL, Annexe A.5) ;
  * journalisation d'audit des ecritures financieres (BNF-05) ;
  * frontend S03 : pointage des loyers et gestion des garanties (bailleur et gestionnaire affecte), declenchement batch reserve au bailleur ;
  * US-40 honoraires de gestion (calcul POURCENTAGE sur loyer encaisse / FORFAIT via `calculer_honoraires()` V8, recalcul synchrone au pointage + batch, gel a PAYE, validation reservee au bailleur, audit `VALIDER_HONORAIRE`) ;
  * US-50/51 alertes de pilotage (LOYER_EN_RETARD, FIN_BAIL, PREAVIS, GARANTIE_NON_RESTITUEE via `generer_alertes(p_preavis_jours)` V9+V10, idempotente/anti-doublon) + batch `@07:00` ;
  * US-50/EF-62 alerte PREAVIS (bail ACTIF dont le terme entre dans la bande de preavis `]J+60 ; J+90]`, exclusive de FIN_BAIL, delai configurable `app.alertes.preavis.jours`) ;
  * US-52 scoping des alertes (bailleur = tout son tenant ; gestionnaire = biens affectes actifs) + marquage « lue » ;
  * US-62 consultation du journal d'audit reservee au bailleur (gestionnaire 403).
  * frontend S04 : consoles honoraires (validation EN_ATTENTE/PAYE + recalcul cote bailleur, lecture seule gestionnaire), alertes (liste NON_LUE en tete, marquage lue, generation reservee au bailleur) et journal d'audit (bailleur seul) cables aux deux dashboards ;
* Fonctionnalites en cours :
  * conversion des tests d'integration au double datasource (suivi fidelite RLS, commun S02/S03) ;
  * smoke test runtime du flux S03 sous le role `loyertracker_api` (stack complete).
* Reserve R6 clotûree le 2026-06-09 : Admin API Keycloak gestionnaire operationnelle (client confidentiel service account `loyertracker-admin`, secret hors depot, acceptation invitation validee en runtime 201).
* Fonctionnalites non commencees :
  * interfaces frontend avancees pour biens, baux et affectations ;
  * dashboards metier consolides ;
  * export/effacement RGPD ;
  * QA/recette, production readiness, exploitation.
* Dette technique :
  * adaptateur `KeycloakGestionnaireIdentityProvider` valide en runtime le 2026-06-09 (acceptation invitation 201, gestionnaire cree avec role `GESTIONNAIRE`) — dette levee ;
  * endpoints backend S02 presents mais OpenAPI non produit ;
  * observabilite limitee aux logs/Actuator, sans metriques ni alerting ;
  * image Nginx dev encore root ;
  * registry, CD, backup/restore et rollback formel absents.
* Dette documentaire :
  * certains documents historiques gardent la trace CGPA v1.0 ou ancienne numerotation ;
  * R6 documentee partiellement : OIDC/PKCE/API OK, Admin API gestionnaire KO ;
  * rapport d'execution S01 separe non formalise ;
  * certains livrables historiques ne refletent pas encore S02, sauf Project State, rapport d'execution S02 backend et rapport frontend S02.
* Blocages connus : aucun blocage technique ou de gouvernance actif. R6 (Admin API) clotûree le 2026-06-09 ; S03 (backend + frontend) clos ; S04 complet backend + frontend (PR #11 + PR #12 + PR #14 + PR #16). Tout nouveau lot (suivis double datasource/smoke test, ou lot fonctionnel ulterieur) reste subordonne a un Plan d'Execution approuve.


## 6. Ou l'on va

* Prochaine phase : maintien en Phase 7 — Developpement.
* Prochaine etape recommandee : cadrer le lot suivant via un Plan d'Execution avant tout nouveau code — S04 etant complet (backend + frontend), priorite aux suivis techniques (double datasource / smoke test runtime) ou a un lot fonctionnel ulterieur au choix du PO.
* Objectif du prochain Plan d'Execution : choisir et cadrer le lot suivant (suivis techniques ou lot fonctionnel) avant toute modification.
* Priorites immediates :
  * arbitrage PO du lot suivant (suivis techniques vs lot fonctionnel) ;
  * convertir les tests d'integration au double datasource (suivi fidelite RLS, commun S02/S03/S04) ;
  * smoke test runtime des flux sous le role `loyertracker_api` (stack complete) ;
  * maintenir le Project State apres chaque etape significative.
* Dependances a lever :
  * decision PO sur le lot suivant et ses arbitrages ;
  * criteres Gate 07 cible.


## 7. Architecture et choix techniques

* Stack backend : Java 21, Spring Boot 3.5.14, Spring Security, OAuth2 Resource Server, JPA, Flyway, PostgreSQL driver, Testcontainers.
* Stack frontend : Angular 20, keycloak-angular 20, keycloak-js, RxJS, Karma/Jasmine, ESLint.
* Base de donnees : PostgreSQL 16, schema Flyway V1 a V10, RLS `FORCE`, index uniques metier, fonctions `SECURITY DEFINER` batch (echeances, EN_RETARD, honoraires, alertes dont PREAVIS) owned par `loyertracker_batch`.
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
  * (resolu 2026-06-09) validation live Keycloak Admin API : client service account dedie a privileges minimaux, secret hors depot, parcours acceptation OK 201 ;
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

* Pipeline CI/CD : CI GitHub Actions active sur push/PR ; CD non implemente. Tous les gates verts sur la PR #3 le 2026-06-08 (Backend, Frontend, Securite gitleaks/SCA/Trivy, CodeQL Java+TS, Packaging Docker).
* Tests unitaires : backend et frontend presents.
* Tests d'integration : backend avec PostgreSQL Testcontainers.
* Tests securite : RLS, ReBAC, 401/403, cross-tenant/cross-affectation S02, scans secrets/dependances/images, CodeQL.
* Couverture de test : `mvn verify` backend passe avec 54 tests verts (S04 : +3 honoraires lot 1, +4 alertes/audit lot 2, +1 PREAVIS ; `SchemaMigrationTest` valide 10 migrations) ; gate JaCoCo cible sur `com.loyertracker.securite` respecte ; Spotless vert ; frontend 27 tests Karma headless (18 + 6 service S04 + 3 composants S04), `ng lint`/`ng build` verts.
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
* Commentaire : niveau Partiel avance. Le socle couvre desormais les lots metier EP-03 (S02), EP-04 (S03 paiements/garanties) et EP-05 complet backend + frontend (S04 honoraires, alertes dont PREAVIS, audit), testes et integres. Le score global reste a 22/32 : l'axe Exploitation (1/4) demeure le frein principal (pas de staging/prod/CD/backup/observabilite centralisee). Le projet n'est pas pret pour staging/prod et doit conserver la discipline CGPA : plan approuve avant tout nouveau lot (suivis techniques ou lot fonctionnel).

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
| 2026-06-08 | Plan de remediation RLS approuve (role applicatif dedie) — D1 split Flyway/runtime, D2 placeholder, D3 oui | Investigation : RLS contournee car l'API se connecte en superutilisateur | jptshilombo@gmail.com | Migration V5 + bascule connexion ; conversion full-stack des tests reportee en suivi |
| 2026-06-09 | Plan d'Execution R6 (Niveau 2) approuve puis execute — GO | Lever la reserve Gate 6 R6 / risque Majeur Admin API gestionnaire | jptshilombo@gmail.com | Client service account dedie + injection runtime ; R6 clotûree |
| 2026-06-09 | Plan d'Execution S03 (Niveau 3) approuve puis execute | Lot metier EP-04 paiements/garanties (US-30/31/32) | jptshilombo@gmail.com | Lot backend livre + teste ; arbitrages A (backend seul), B (@Scheduled + trigger manuel), C (audit ecrit des maintenant) retenus |
| 2026-06-09 | QA du lot backend S03 — verdict GO | Verification avant de cadrer le frontend | jptshilombo@gmail.com | 44/44 verts confirmes ; 3 arbitrages decides : EN_RETARD automatise, RECU >= attendu impose, frontend = pointage + garanties |
| 2026-06-09 | Plan d'Execution Frontend S03 (Niveau 3) approuve puis execute | Volet IHM US-31/32 + correctifs QA backend | jptshilombo@gmail.com | Frontend pointage/garanties (bailleur + gestionnaire) + V7 EN_RETARD + controle RECU livres et testes (backend 46, frontend 18) |
| 2026-06-10 | Plan d'Execution S04 (Niveau 3) approuve — defauts A–D | Lot metier EP-05 honoraires & pilotage (US-40, US-50/51/52, US-62) | jptshilombo@gmail.com | Arbitrages : A backend d'abord (frontend S04 reporte), B PREAVIS reporte, C honoraires recalcules au pointage + batch, D 2 PR (honoraires ; alertes+audit) |
| 2026-06-10 | Lot 1 S04 honoraires execute et integre (PR #11) | Calcul des honoraires de gestion (US-40) | jptshilombo@gmail.com | V8 `calculer_honoraires()`, hook pointage, gel a PAYE, validation bailleur ; merge `8ac0b49` |
| 2026-06-10 | Lot 2 S04 alertes & audit execute et integre (PR #12) | Alertes de pilotage (US-50/51/52) + consultation audit (US-62) | jptshilombo@gmail.com | V9 `generer_alertes()` (PREAVIS reporte), scoping gestionnaire, audit BAILLEUR-only ; merge `0f4f47c` |
| 2026-06-10 | Critere d'acceptation PREAVIS (US-50/EF-62) fige | Lever l'ambiguite ayant motive le report au Lot 2 | jptshilombo@gmail.com | Echeance de preavis « atteinte » = terme du bail dans la bande de preavis, avant le terme, a <= 90 jours (3 mois) ; bornee a `]J+60 ; J+90]` pour exclusivite avec FIN_BAIL |
| 2026-06-10 | Plan d'Execution PREAVIS (Niveau 1) approuve, execute et integre (PR #14) | Implementer le 4e type d'alerte PREAVIS | jptshilombo@gmail.com | V10 `generer_alertes(p_preavis_jours integer DEFAULT 90)`, delai configurable `app.alertes.preavis.jours` ; merge `0cd3539` ; S04 backend clos |
| 2026-06-10 | Plan d'Execution Frontend S04 (Niveau 3) approuve — defauts A–D, execute et integre (PR #16) | Volet IHM S04 (honoraires/alertes/audit), exposer le backend S04 | jptshilombo@gmail.com | Defauts : A 3 consoles en un lot, B boutons d'action EN_ATTENTE/PAYE, C alertes NON_LUE en tete, D audit liste brute. Service `core/s04` + composants cables aux 2 dashboards, sans modif backend ; merge `99e3215` ; S04 complet backend + frontend |

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
| 2026-06-08 | Remediation RLS — role applicatif a privileges minimaux | Migration V5 (`loyertracker_api` LOGIN NOSUPERUSER NOBYPASSRLS + grants DML/EXECUTE) ; Flyway via role admin separe (`spring.flyway.user`) ; compose/.env bascules ; tests RLS sous role reel + attributs du role ; `mvn verify` vert 38 tests. Reactive la 2e couche de defense en profondeur (ADR-01) | `V5__role_applicatif_rls.sql`, `application.yml`, `src/test/resources/application.properties`, `docker-compose.yml`, `.env(.example)`, `SchemaMigrationTest.java`, `docs/project-state.md` | Claude Code |
| 2026-06-08 | Smoke test runtime sous role restreint | App bootee via jar reel + PostgreSQL dedie : Flyway migre en admin et cree `loyertracker_api`, l'app se connecte en rôle restreint (pool Hikari, health UP). Verifie live : TRUNCATE/DDL refuses, isolation RLS par tenant (sans GUC=0, tenant A voit son bien, tenant B ne le voit pas). Non couvert : flux API authentifie (Keycloak) | (validation runtime, aucun fichier source) | Claude Code |
| 2026-06-08 | PR #3 mergee dans `main` (CI verte) | Branche `feat/s02-delegation-affectations` -> `main` via PR #3 (delegation S02, autorisation cross-tenant, RLS rôle applicatif, smoke test). Gate CodeQL d'abord rouge (4 alertes high `java/concatenated-sql-query` dans le nouveau test RLS) corrige par SQL parametre (`set_config`/PreparedStatement). Tous les checks verts : Backend, Frontend, Securite (gitleaks+SCA+Trivy), CodeQL, Packaging Docker. **Mergee le 2026-06-08T07:56Z, merge commit `d6a586f`.** | `main`, PR #3 | Claude Code / PO |
| 2026-06-09 | Execution Plan R6 — correction Admin API Keycloak gestionnaire | Ajout client confidentiel service account `loyertracker-admin` (roles realm-management `manage-users`/`view-users`/`view-realm`), secret injecte post-import (`keycloak-init`), variables Admin API injectees dans `api` (base-url `/auth`), defauts Spring/adaptateur alignes. Bug d'import corrige (description client > 255 car.). `mvn verify` vert (38 tests). Reexecution runtime : token client_credentials 200, 4 ops Admin API OK (200/201/200/204), parcours API `POST /api/invitations` + acceptation OK 201 (adaptateur reel), gestionnaire cree + role `GESTIONNAIRE`. R6 clotûree. | `infra/keycloak/realm-loyertracker.json`, `infra/keycloak/bootstrap-test-account.sh`, `docker-compose.yml`, `.env.example`, `backend/.../application.yml`, `backend/.../KeycloakGestionnaireIdentityProvider.java`, `docs/cgpa/07-devsecops/rapport-validation-r6.md`, `docs/project-state.md` | Claude Code |
| 2026-06-09 | PR #5 mergee dans `main` (CI verte) | Branche `fix/r6-keycloak-admin-api` -> `main` via PR #5 (remediation R6 Admin API gestionnaire). Tous les checks verts : Backend, Frontend, Securite (gitleaks+SCA+Trivy), CodeQL Java+TS, Packaging Docker. **Mergee le 2026-06-09T08:07Z, merge commit `d77cf29`.** | `main`, PR #5 | jptshilombo / Claude Code |
| 2026-06-09 | Execution S03 backend — paiements & garanties | US-30 fonction SQL `generer_echeances_loyers()` (V6, SECURITY DEFINER owner batch, idempotente A.3) + batch `@Scheduled` + trigger `POST /api/batch/echeances` ; US-31 pointage + historique ; US-32 garantie depot/restitution (A.5) ; audit des ecritures (BNF-05). Ecart maitrise vs plan : SECURITY DEFINER au lieu d'un 2e datasource batch. Anomalies corrigees : mapping `CHAR(7)` (periode) ; mocks S03 dans `SecurityIntegrationTest` ; `SchemaMigrationTest` 6 migrations. `mvn verify` vert : 44 tests (38 + 6 S03), 0 echec. | `db/migration/V6__...sql`, `paiements/*`, `garanties/*`, `batch/*`, `audit/AuditService.java`, tests `s03/*`, `SecurityIntegrationTest`, `SchemaMigrationTest`, `docs/cgpa/06-planification-agile/rapport-execution-s03.md`, `docs/project-state.md` | Claude Code |
| 2026-06-09 | PR #7 mergee dans `main` (CI verte) | Branche `feat/s03-paiements-garanties` -> `main` via PR #7 (lot backend S03 paiements & garanties, US-30/31/32). Les 13 checks verts : Backend, Frontend, Securite (gitleaks+SCA+Trivy), CodeQL Java+TS, Packaging Docker. **Mergee le 2026-06-09T12:11Z, merge commit `2f5c743`, branche supprimee.** | `main`, PR #7 | jptshilombo / Claude Code |
| 2026-06-09 | PR #8 mergee dans `main` (CI verte) | Branche `docs/project-state-post-merge-s03` -> `main` via PR #8 (synchronisation post-merge S03). Checks verts. **Mergee le 2026-06-09T12:23Z, merge commit `b5f201c`, branche supprimee.** | `main`, PR #8 | jptshilombo / Claude Code |
| 2026-06-09 | Execution Frontend S03 + correctifs QA | Lot A (backend) : fonction SQL `marquer_loyers_en_retard()` (V7, SECURITY DEFINER owner batch, idempotente) + chainage batch (DTO `{echeancesCreees, loyersEnRetard}`) + controle `RECU >= attendu` (400). Lot B (frontend) : service `core/s03`, composants `paiements-bien` et `garanties-bail` standalone (signals) integres aux 2 dashboards, declenchement batch reserve bailleur. `mvn verify` vert 46 tests ; `ng lint`/`ng build`/`ng test` verts (18 tests). | `db/migration/V7__...sql`, `batch/*`, `paiements/PaiementService.java`, tests `s03/*` + `SchemaMigrationTest`, `frontend/src/app/core/s03/*`, `frontend/src/app/paiements/*`, `frontend/src/app/garanties/*`, `frontend/.../bailleur|gestionnaire/dashboard/*`, `docs/cgpa/06-planification-agile/rapport-execution-frontend-s03.md`, `docs/project-state.md` | Claude Code |
| 2026-06-09 | PR #9 mergee dans `main` (CI verte) | Branche `feat/s03-frontend-paiements-garanties` -> `main` via PR #9 (frontend S03 paiements & garanties US-31/32 + correctifs QA backend). Tous les checks verts : Backend, Frontend, Securite (gitleaks+SCA+Trivy), CodeQL Java+TS, Packaging Docker. **Mergee le 2026-06-09T13:23Z, merge commit `a1f60dd`, branche supprimee.** | `main`, PR #9 | jptshilombo / Claude Code |
| 2026-06-10 | Execution S04 lot 1 — honoraires de gestion | US-40 fonction SQL `calculer_honoraires(p_bien_id)` (V8, SECURITY DEFINER owner batch, upsert idempotent POURCENTAGE/FORFAIT, gel a PAYE) ; package `honoraires/` ; hook synchrone `PaiementService.pointer` ; `GET /api/biens/{bienId}/honoraires` (ReBAC) ; `PATCH /api/honoraires/{id}/statut` (BAILLEUR-only, audit `VALIDER_HONORAIRE`, garde de cloisonnement applicative) ; `POST /api/batch/honoraires` + recalcul quotidien. `mvn verify` vert : 49 tests (46 + 3 S04), `SchemaMigrationTest` 8 migrations. | `db/migration/V8__s04_honoraires.sql`, `honoraires/*`, `paiements/PaiementService.java`, `batch/BatchController.java`, `batch/EcheancesScheduler.java`, tests `s04/S04HonorairesIntegrationTest.java` + `SchemaMigrationTest` + `SecurityIntegrationTest`, `docs/cgpa/06-planification-agile/rapport-execution-s04-lot1-honoraires.md` | Claude Code |
| 2026-06-10 | PR #11 mergee dans `main` (CI verte) | Branche `feat/s04-honoraires` -> `main` via PR #11 (lot 1 S04 honoraires US-40). Tous les checks verts : Backend, Frontend, Securite (gitleaks+SCA+Trivy), CodeQL Java+TS, Packaging Docker. **Mergee le 2026-06-10, merge commit `8ac0b49`, branche supprimee.** | `main`, PR #11 | jptshilombo / Claude Code |
| 2026-06-10 | Execution S04 lot 2 — alertes & consultation audit | US-50/51 fonction SQL `generer_alertes()` (V9, SECURITY DEFINER owner batch, idempotente ON CONFLICT, periode derivee non-nulle ; LOYER_EN_RETARD, FIN_BAIL, GARANTIE_NON_RESTITUEE ; PREAVIS reporte) ; fonctions `alertes_gestionnaire`/`alerte_bailleur_pour_gestionnaire` (scoping US-52) ; package `alertes/` (`GET /api/alertes`, `PATCH /api/alertes/{id}/lecture`) ; audit US-62 (`AuditLog`, `GET /api/audit` BAILLEUR-only) ; `POST /api/batch/alertes` + `AlertesScheduler @07:00`. `mvn verify` vert : 53 tests (49 + 4 S04), `SchemaMigrationTest` 9 migrations. | `db/migration/V9__s04_alertes.sql`, `alertes/*`, `audit/AuditLog.java`+`AuditLogRepository.java`+`AuditDto.java`+`AuditController.java`+`AuditService.java`, `batch/AlertesScheduler.java`, `batch/BatchController.java`, tests `s04/S04AlertesAuditIntegrationTest.java` + `SchemaMigrationTest` + `SecurityIntegrationTest`, `docs/cgpa/06-planification-agile/rapport-execution-s04-lot2-alertes-audit.md` | Claude Code |
| 2026-06-10 | PR #12 mergee dans `main` (CI verte) | Branche `feat/s04-alertes-audit` -> `main` via PR #12 (lot 2 S04 alertes US-50/51/52 + audit US-62). Tous les checks verts : Backend, Frontend, Securite (gitleaks+SCA+Trivy), CodeQL Java+TS, Packaging Docker. **Mergee le 2026-06-10T07:52Z, merge commit `0f4f47c`, branche supprimee.** | `main`, PR #12 | jptshilombo / Claude Code |
| 2026-06-10 | Synchronisation post-merge S04 backend (PR #13) | Branche `docs/project-state-post-merge-s04` -> `main` via PR #13 (cloture documentaire backend S04, PR #11 + #12). **Mergee le 2026-06-10, merge commit `f1d45ed`.** | `docs/project-state.md`, PR #13 | jptshilombo / Claude Code |
| 2026-06-10 | Execution S04 — alerte PREAVIS | US-50/EF-62 : migration V10 `DROP` puis recreation `generer_alertes(p_preavis_jours integer DEFAULT 90)` (3 branches V9 reconduites + branche PREAVIS bornee `]J+60 ; J+90]`, exclusive de FIN_BAIL, SECURITY DEFINER owner batch, anti-doublon ON CONFLICT) ; config `app.alertes.preavis.jours` (90, env `APP_ALERTES_PREAVIS_JOURS`) ; `AlerteService.genererBatch()` passe le parametre via `@Value`. Aucun changement d'enum/entite/DTO/endpoint (`TypeAlerte.PREAVIS` preexistant). `mvn verify` vert : 54 tests (53 + 1 PREAVIS), `SchemaMigrationTest` 10 migrations. | `db/migration/V10__s04_alerte_preavis.sql`, `application.yml`, `alertes/AlerteService.java`, tests `s04/S04AlertesAuditIntegrationTest.java` + `SchemaMigrationTest`, `docs/cgpa/06-planification-agile/rapport-execution-s04-preavis.md` | Claude Code |
| 2026-06-10 | PR #14 mergee dans `main` (CI verte) | Branche `feat/s04-alerte-preavis` -> `main` via PR #14 (alerte PREAVIS US-50/EF-62). Les 13 checks verts : Backend, Frontend, Securite (gitleaks+SCA+Trivy), CodeQL Java+TS, Packaging Docker. **Mergee le 2026-06-10T14:24Z, merge commit `0cd3539`, branche supprimee.** S04 backend clos (PREAVIS inclus). | `main`, PR #14 | jptshilombo / Claude Code |
| 2026-06-10 | Synchronisation post-merge S04 PREAVIS (PR #15) | Branche `docs/project-state-post-merge-preavis` -> `main` via PR #15 (cloture documentaire backend S04, PREAVIS inclus). **Mergee le 2026-06-10T14:43Z, merge commit `ac42d25`.** | `docs/project-state.md`, PR #15 | jptshilombo / Claude Code |
| 2026-06-10 | Execution Frontend S04 — consoles honoraires/alertes/audit | Service `core/s04/s04-api.service.ts` (types + methodes honoraires/alertes/audit + batch) ; composants standalone `honoraires-bien` (validation EN_ATTENTE/PAYE + recalcul, gardes `peutValider`), `alertes-liste` (NON_LUE en tete, marquage lue, generation gardee `peutGenerer`), `audit-journal` (bailleur seul) ; cablage dashboards bailleur (valider + generer + audit) et gestionnaire (lecture seule, alertes sans generation, pas d'audit). Aucune modification backend. `ng lint`/`ng build` verts ; `ng test` 27 verts (18 + 6 service + 3 composants). | `frontend/src/app/core/s04/*`, `frontend/src/app/honoraires/*`, `frontend/src/app/alertes/*`, `frontend/src/app/audit/*`, `frontend/.../bailleur|gestionnaire/dashboard/*`, `docs/cgpa/06-planification-agile/rapport-execution-frontend-s04.md` | Claude Code |
| 2026-06-10 | PR #16 mergee dans `main` (CI verte) | Branche `feat/s04-frontend-honoraires-alertes-audit` -> `main` via PR #16 (frontend S04 US-40/50/51/52/62). Echec transitoire d'upload SARIF CodeQL (panne API GitHub `Requires authentication`, sans rapport avec le code — la CodeQL Java echouait aussi) ; jobs CodeQL relances apres retablissement -> tous les checks verts. **Mergee le 2026-06-10, merge commit `99e3215`, branche supprimee.** S04 complet backend + frontend. | `main`, PR #16 | jptshilombo / Claude Code |

## 13. Risques ouverts

| Risque | Niveau | Impact | Mitigation | Statut |
| ------ | ------ | ------ | ---------- | ------ |
| Documentation CGPA residuelle obsolescente | Mineur | Confusion possible sur quelques mentions historiques v1.0 | Corriger progressivement hors historique de decision | Ouvert |
| Admin API Keycloak gestionnaire KO en runtime | Majeur | US-12 echoue a l'acceptation invitation / creation gestionnaire | Corrige le 2026-06-09 : client confidentiel service account dedie `loyertracker-admin` (roles realm-management minimaux), secret injecte post-import hors depot, variables Admin API dans `api`. Reexecution R6 : token client_credentials 200, acceptation 201, gestionnaire cree + role assigne | Ferme |
| Tests d'autorisation incomplets sur futurs endpoints | Critique | Fuite cross-bailleur/cross-affectation | Imposer tests d'autorisation par endpoint dans S03+ ; ecritures d'affectation desormais couvertes | En reduction |
| Faille cross-bailleur sur `POST /affectations/{id}/revocation` (200 au lieu de 404/403) | Critique | Un bailleur pouvait revoquer l'affectation d'un autre bailleur | Corrige le 2026-06-08 (controle de propriete applicatif dans `AffectationService.revoquer`) + test de non-regression | Ferme |
| RLS contournee : l'API se connectait en SUPERUTILISATEUR (`POSTGRES_USER`) en test et en runtime | Majeur | Defense en profondeur (2e couche RLS, ADR-01) totalement inerte sur le chemin applicatif ; seule l'autorisation applicative cloisonnait | Diagnostic 2026-06-08 (cause = role superuser, pas la propagation du GUC). Corrige par migration V5 : role `loyertracker_api` LOGIN NOSUPERUSER NOBYPASSRLS + Flyway admin separe ; RLS prouvee sous ce role (`SchemaMigrationTest`) | Ferme (code + tests) |
| Comportement runtime sous le role restreint non verifie end-to-end | Mineur | Les tests `@SpringBootTest` tournent encore en superuser (harnais TRUNCATE/seed admin) ; un flux applicatif dependant implicitement du superuser passerait inapercu | Smoke test runtime 2026-06-08 : app bootee en `loyertracker_api` (health UP, pool Hikari sur le role restreint), Flyway en admin, TRUNCATE/DDL refuses, isolation RLS par tenant verifiee live. Reste : flux API authentifie complet (Keycloak) + automatisation via tests double datasource | En reduction (suivi) |
| Pas de Plan d'Execution pour le lot suivant (suivis techniques / lot fonctionnel) | Critique gouvernance | Codage non conforme CGPA v3.0.1 | Produire et faire approuver un Plan d'Execution avant tout nouveau code ; S03 (PR #7/#9) et S04 backend + frontend (PR #11/#12/#14/#16) ont ete couverts et integres | Ouvert (preventif) |
| Alerte PREAVIS (US-50) reportee : critere d'acceptation ambigu | Mineur produit | Couverture fonctionnelle des alertes incomplete | Critere fige par le PO le 2026-06-10 (bande de preavis 90 j, bornee `]J+60 ; J+90]`, exclusive de FIN_BAIL) puis implemente (V10, memes patrons que V9) et integre (PR #14, merge `0cd3539`) ; test d'integration dedie vert | Ferme |
| Frontend S04 non realise (honoraires/alertes/audit en backend seul) | Mineur produit | Fonctions S04 non exposees a l'IHM | Realise et integre le 2026-06-10 (PR #16, merge `99e3215`) : consoles honoraires/alertes/audit cablees aux 2 dashboards, `ng lint`/`ng build` verts, `ng test` 27 verts | Ferme |
| Pas de staging/prod/CD/backup | Majeur | Non readiness production | Traiter en phase production readiness | Differe |
| Interfaces frontend S02 minimales seulement | Mineur produit | Ergonomie suffisante pour reprise mais pas pour recette large | Prevoir ameliorations UX avant beta | Ouvert |
| OpenAPI S02 absent | Mineur | Contrat API moins explicite | Generer/documenter OpenAPI avant recette large | Ouvert |

## 14. Prochaine action claire

Le lot **S04 est complet backend + frontend** et integre dans `main` : backend honoraires (US-40, **PR #11** `8ac0b49`), alertes & audit (US-50/51/52, US-62, **PR #12** `0f4f47c`), alerte PREAVIS (US-50/EF-62, **PR #14** `0cd3539`), puis frontend honoraires/alertes/audit (**PR #16** `99e3215`, CI verte apres relance des jobs CodeQL bloques par une panne API GitHub transitoire). Tests verts : backend 54 (`SchemaMigrationTest` 10 migrations, Spotless + JaCoCo OK), frontend 27 (`ng lint`/`ng build` verts). Cette synchronisation post-merge consigne la cloture **complete de S04**. Ne subsistent que des suivis techniques, chacun subordonne a un Plan d'Execution approuve : (1) convertir les tests d'integration au double datasource (fidelite RLS, commun S02/S03/S04) ; (2) smoke test runtime des flux authentifies sous le role `loyertracker_api` sur la stack complete ; (3) tout lot fonctionnel ulterieur (dashboards consolides, RGPD, OpenAPI, UX) au choix du PO. Aucun nouveau developpement ne doit demarrer sans un Plan d'Execution approuve.
