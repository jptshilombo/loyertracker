# Product Backlog — LoyerTracker

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Product Owner | jptshilombo@gmail.com |
| Date | 2026-06-04 |
| Phase | 06 — Planification Agile |
| Gate visé | Gate 5 |
| Statut | En revue |
| Prérequis | Gate 4 Go — verrou de codage levé |

> **Base :** CDC (33 EF + 12 ENF, jalons J1→J5) + DAT (8 ADR, MLD, API). Estimation en **points de complexité relative** (suite Fibonacci 1-2-3-5-8). Équipe : **dev solo**. Vélocité cible : ~20 pts/sprint (2 semaines).

---

## 1. Definition of Done (DoD)

Toute user story est **Terminée** uniquement si **tous** les critères suivants sont vérifiés :

- [ ] Code revu, conforme au CDC et à l'architecture (ADR respectés)
- [ ] Tests **unitaires** passants (règles de gestion, transitions de statut)
- [ ] Tests **d'intégration** passants (API + base de données réelle — pas de mock BDD)
- [ ] Tests **d'autorisation** passants pour le périmètre concerné (0 accès cross-bailleur/cross-affectation)
- [ ] Aucun secret en clair dans le dépôt (scan CI)
- [ ] Migration Flyway versionnée et appliquée
- [ ] Endpoint(s) documenté(s) (OpenAPI / commentaire minimal)
- [ ] Déployable via `docker compose up` (ENF-09)

> **ENF transverses** (sécurité, RGPD, cloisonnement) : intégrées à la DoD — elles ne constituent pas des stories séparées sauf quand elles représentent un livrable autonome (US-71, US-72, US-73).

---

## 2. Epics

| ID | Epic | Jalons CDC | Priorité |
|----|------|-----------|---------|
| EP-01 | **Socle & infrastructure** — Docker Compose, Nginx, Keycloak realm, Flyway, CI skeleton | Transverse | Must |
| EP-02 | **Comptes, auth & délégation** — inscription, OIDC, invitation, RBAC | J1 | Must |
| EP-03 | **Biens, baux & affectation/rotation** — CRUD, unicités, historique, cloisonnement | J2 | Must |
| EP-04 | **Paiements & garanties** — échéances terme échu, pointage, paiement partiel, garantie | J3 | Must |
| EP-05 | **Honoraires** — calcul auto (% / forfait), statuts, validation bailleur | J3 | Should |
| EP-06 | **Moteur d'alertes & batch** — job quotidien, 4 types d'alertes, anti-doublon | J4 | Must |
| EP-07 | **Tableaux de bord & cloisonnement** — vues bailleur/gestionnaire, audit | J4 | Must |
| EP-08 | **RGPD, tests sécu & durcissement** — export, effacement, tests cross-bailleur, scan | J5 | Must |

---

## 3. User Stories

> Format : *En tant que [rôle], je veux [action] afin de [valeur].*
> Critères d'acceptation au format GWT (Given / When / Then).

---

### EP-01 — Socle & infrastructure

| ID | Story | Critères d'acceptation | Pts | MoSCoW | Source |
|----|-------|------------------------|-----|--------|--------|
| US-01 | En tant que **dev**, je veux une stack Docker Compose (nginx · api · keycloak · postgres) démarrant en une commande afin d'avoir un environnement de dev reproductible. | **G** un env vierge avec Docker installé **W** `docker compose up` **T** les 4 services démarrent, nginx répond en 443, keycloak est joignable sur `/auth`, l'API répond sur `/api/actuator/health`. | 5 | Must | ENF-09 |
| US-02 | En tant que **dev**, je veux un realm Keycloak configuré (rôles `BAILLEUR`/`GESTIONNAIRE`, clients SPA+backend) afin que l'authentification OIDC soit opérationnelle. | **G** la stack démarre **W** le realm est importé **T** un compte `BAILLEUR` de test peut s'authentifier via PKCE et obtenir un JWT portant le rôle ; le backend rejette un JWT invalide (401). | 3 | Must | ENF-01 |
| US-03 | En tant que **dev**, je veux une migration Flyway initiale créant toutes les tables, contraintes et index du MLD afin de disposer du schéma cible dès le départ. | **G** l'API démarre **W** Flyway s'exécute **T** toutes les tables existent avec leurs colonnes `bailleur_id`, les index uniques partiels (EF-12/21/30/51/65) et la RLS sont en place ; une 2ᵉ exécution est idempotente. | 5 | Must | DAT §3 |
| US-04 | En tant que **dev**, je veux un pipeline CI minimal (build · tests · scan secrets) afin de détecter les régressions à chaque commit. | **G** un push sur la branche principale **W** la CI s'exécute **T** build compilé, tests passants, scan secrets sans résultat, rapport publié. | 3 | Must | ENF-10 |

---

### EP-02 — Comptes, auth & délégation

| ID | Story | Critères d'acceptation | Pts | MoSCoW | Source |
|----|-------|------------------------|-----|--------|--------|
| US-10 | En tant que **visiteur**, je veux m'inscrire comme bailleur et m'authentifier via Keycloak afin d'accéder à mon espace. | **G** un visiteur sans compte **W** il s'inscrit et s'authentifie **T** un compte rôle `BAILLEUR` est créé, une session OIDC valide est ouverte, le dashboard bailleur est accessible. | 3 | Must | EF-01 |
| US-11 | En tant que **bailleur**, je veux inviter un gestionnaire par lien tokenisé (72h) afin de lui déléguer la gestion d'un bien. | **G** un bailleur authentifié **W** il saisit l'e-mail d'un gestionnaire **T** un token UUID v4 usage-unique valide 72h est généré et le lien est retourné ; un token expiré ou déjà utilisé est rejeté (EF-03). | 5 | Must | EF-02/03 |
| US-12 | En tant que **destinataire d'une invitation valide**, je veux accepter le lien pour créer mon compte gestionnaire afin d'accéder aux biens qui me sont affectés. | **G** un lien valide **W** le destinataire l'accepte **T** un compte `GESTIONNAIRE` est créé dans Keycloak via Admin API ; si le compte existe déjà (multi-bailleur), il est réutilisé (EF-05) ; toute création hors invitation est refusée. | 5 | Must | EF-04/05 |
| US-13 | En tant que **système**, je veux appliquer le RBAC à chaque requête afin qu'aucune action ne soit accessible hors du rôle autorisé. | **G** un utilisateur authentifié avec rôle X **W** il appelle une fonction réservée à rôle Y **T** réponse 403 ; avec rôle correct, accès accordé. Matrice : bailleur = accès complet ses biens ; gestionnaire = accès biens affectés actifs uniquement. | 3 | Must | EF-06, ENF-01 |

---

### EP-03 — Biens, baux & affectation/rotation

| ID | Story | Critères d'acceptation | Pts | MoSCoW | Source |
|----|-------|------------------------|-----|--------|--------|
| US-20 | En tant que **bailleur**, je veux créer, modifier et archiver mes biens afin de gérer mon patrimoine. | **G** un bailleur authentifié **W** il crée/modifie/archive un bien **T** le bien est persisté avec statut `LIBRE`/`LOUE`/`EN_TRAVAUX`, scopé à son `bailleurId` ; un gestionnaire d'un autre bailleur ne le voit pas (403). | 3 | Must | EF-10 |
| US-21 | En tant que **bailleur ou gestionnaire affecté actif**, je veux enregistrer un bail sur un bien libre afin de suivre une location. | **G** un bien sans bail actif **W** un acteur autorisé enregistre un bail (locataire, loyer CC, dates, dépôt) **T** le bail est créé au statut `ACTIF` ; si un bail actif existe déjà, réponse 409 (index unique partiel EF-12). | 5 | Must | EF-11/12 |
| US-22 | En tant que **bailleur**, je veux consulter l'historique des baux d'un bien afin de retracer les occupations. | **G** un bien avec plusieurs baux **W** consultation **T** liste chronologique baux actifs et clos affichée. | 2 | Should | EF-13 |
| US-23 | En tant que **bailleur**, je veux créer une affectation (gestionnaire + bien + honoraires) afin de déléguer la gestion. | **G** un bailleur avec un gestionnaire invité et un bien **W** il crée l'affectation (type `POURCENTAGE`/`FORFAIT`, montant, dates) **T** affectation `ACTIVE` persistée ; si une affectation active existe déjà sur ce bien, réponse 409 (EF-21). | 5 | Must | EF-20/21, EF-50 |
| US-24 | En tant que **bailleur**, je veux révoquer une affectation et en créer une nouvelle afin de changer de gestionnaire sans perdre l'historique. | **G** une affectation `ACTIVE` **W** révocation **T** statut → `REVOQUEE`, `dateRevocation` enregistrée, compte Keycloak non désactivé ; une nouvelle affectation peut être créée ; l'historique complet est visible (EF-24 : gestionnaire ne voit que ses propres affectations). | 5 | Must | EF-22/23/24 |

---

### EP-04 — Paiements & garanties

| ID | Story | Critères d'acceptation | Pts | MoSCoW | Source |
|----|-------|------------------------|-----|--------|--------|
| US-30 | En tant que **batch**, je veux générer chaque jour les échéances mensuelles à terme échu des baux actifs afin que les loyers attendus soient disponibles pour le pointage. | **G** un bail `ACTIF` **W** le batch s'exécute **T** un `Paiement` attendu (`loyer_CC`, `date_exigibilite = 1er(periode+1)`) est upsert-é idempotent pour le mois courant, depuis le mois de début jusqu'au terme (Annexe A.3 v1.2) ; 2ᵉ run sans doublon. | 5 | Must | EF-33 |
| US-31 | En tant que **bailleur ou gestionnaire affecté actif**, je veux pointer le statut d'un loyer mensuel afin de suivre les encaissements. | **G** un loyer attendu pour `(bien, periode)` **W** pointage **T** statut mis à jour (`RECU`/`PARTIEL`/`EN_RETARD`/`IMPAYE`) ; pour `PARTIEL`, reste dû calculé (EF-32) ; historique consultable par bien/période (EF-31). | 5 | Must | EF-30/31/32 |
| US-32 | En tant que **bailleur ou gestionnaire affecté actif**, je veux enregistrer et suivre le dépôt de garantie d'un bail afin d'en tracer la restitution. | **G** un bail **W** création du dépôt (montant, type, date) **T** garantie créée à `DETENU` ; restitution totale → `RESTITUE_TOTAL` ; partielle avec retenue + motif → `RESTITUE_PARTIEL` → `RESTITUE_TOTAL` (Annexe A.5). | 3 | Must | EF-40/41/42 |

---

### EP-05 — Honoraires

| ID | Story | Critères d'acceptation | Pts | MoSCoW | Source |
|----|-------|------------------------|-----|--------|--------|
| US-40 | En tant que **système**, je veux calculer automatiquement les honoraires mensuels du gestionnaire et suivre leur statut afin de tracer la rémunération de la délégation. | **G** une affectation active avec honoraires définis **W** un pointage de paiement ou le batch de fin de mois s'exécute **T** honoraire upsert-é idempotent sur `(affectation_id, periode)` : `POURCENTAGE` recalculé sur `loyer_encaissé` tant que `≠ PAYE` ; `FORFAIT` fixe ; montant figé à `→ PAYE` (validation bailleur uniquement, EF-52). Statuts : `DU → EN_ATTENTE → PAYE`. | 5 | Should | EF-51/52 |

---

### EP-06 — Moteur d'alertes & batch

| ID | Story | Critères d'acceptation | Pts | MoSCoW | Source |
|----|-------|------------------------|-----|--------|--------|
| US-50 | En tant que **système**, je veux que le job batch quotidien (07:00) génère les alertes `LOYER_EN_RETARD`, `FIN_BAIL` et `PREAVIS` afin que les acteurs soient informés sans délai. | **G** des baux et paiements actifs **W** le batch s'exécute **T** alerte `LOYER_EN_RETARD` si loyer non `RECU` après `date_exigibilite + tolérance` ; `FIN_BAIL` si terme ≤ J+60 ; `PREAVIS` si échéance atteinte. Anti-doublon : index unique `(type, bienId, periode)` sur `NON_LUE` — 2ᵉ run sans doublon (EF-65). | 8 | Must | EF-60/61/62/65 |
| US-51 | En tant que **système**, je veux que le batch génère l'alerte `GARANTIE_NON_RESTITUEE` si une garantie est encore `DETENU` plus de 30 jours après la fin du bail. | **G** une garantie `DETENU` et un bail `CLOS` depuis > 30 jours **W** le batch s'exécute **T** alerte générée, idempotente. | 3 | Should | EF-63 |
| US-52 | En tant que **bailleur/gestionnaire**, je veux recevoir uniquement les alertes de mon périmètre afin de ne pas voir celles des autres. | **G** une alerte sur un bien **W** diffusion **T** bailleur voit toutes ses alertes ; gestionnaire voit uniquement les biens affectés actifs ; gestionnaire révoqué ne reçoit plus (EF-64). | 3 | Must | EF-64 |

---

### EP-07 — Tableaux de bord & cloisonnement

| ID | Story | Critères d'acceptation | Pts | MoSCoW | Source |
|----|-------|------------------------|-----|--------|--------|
| US-60 | En tant que **bailleur**, je veux un tableau de bord consolidé (encaissements, retards, prochaines échéances, alertes non lues) afin d'avoir une vue globale de mon patrimoine. | **G** un bailleur avec N biens **W** il ouvre le dashboard **T** vue complète de ses biens en < 2 s (P95, ENF-06) ; un autre bailleur ne voit rien (403 côté serveur). | 5 | Must | EF-70, ENF-06 |
| US-61 | En tant que **gestionnaire**, je veux un tableau de bord limité à mes biens affectés actifs afin de gérer mon périmètre. | **G** un gestionnaire authentifié **W** il ouvre son dashboard **T** seuls ses biens affectés actifs sont affichés (EF-71) ; tentative API directe sur un bien non affecté → 403 (EF-72). | 3 | Must | EF-71/72 |
| US-62 | En tant que **bailleur**, je veux consulter le journal d'audit de mes actions sensibles afin de tracer toute modification sur mon périmètre. | **G** des actions d'écriture sensibles réalisées **W** consultation de l'audit log **T** entrées horodatées (acteur, rôle, action, entité) présentes ; un gestionnaire est refusé (403). | 3 | Should | EF-73, ENF-05 |

---

### EP-08 — RGPD, tests sécu & durcissement

| ID | Story | Critères d'acceptation | Pts | MoSCoW | Source |
|----|-------|------------------------|-----|--------|--------|
| US-70 | En tant que **bailleur**, je veux pouvoir exporter mes données et effacer un locataire afin de répondre aux droits RGPD. | **G** une demande d'export ou d'effacement **W** l'endpoint est appelé **T** export = JSON scopé `bailleurId` ; effacement = PII anonymisées (pseudonymisation ADR-03), données financières conservées, opération tracée dans `AuditLog`. | 5 | Must | ENF-04, ADR-03 |
| US-71 | En tant que **équipe**, je veux une suite de tests d'autorisation couvrant chaque endpoint afin de garantir 0 accès cross-bailleur/cross-affectation. | **G** la suite de tests **W** elle s'exécute en CI **T** aucun test cross-bailleur ne passe ; aucun test cross-affectation ne passe ; résultat publié dans le rapport CI. | 5 | Must | ENF-02, ADR-01 |
| US-72 | En tant que **dev**, je veux un scan SAST/secrets automatisé en CI et les en-têtes de sécurité Nginx configurés afin de durcir la surface d'attaque. | **G** la CI + la config Nginx **W** scan exécuté et headers vérifiés **T** 0 secret en clair ; HSTS, CSP, `X-Frame-Options`, `X-Content-Type-Options` présents dans les réponses Nginx. | 3 | Must | ENF-03, ADR-08 |

---

## 4. Récapitulatif & priorisation

| Epic | Stories | Total pts | Must | Should |
|------|---------|-----------|------|--------|
| EP-01 Socle | US-01→04 | 16 | 16 | — |
| EP-02 Auth | US-10→13 | 16 | 16 | — |
| EP-03 Biens/baux/affectation | US-20→24 | 20 | 18 | 2 |
| EP-04 Paiements/garanties | US-30→32 | 13 | 13 | — |
| EP-05 Honoraires | US-40 | 5 | — | 5 |
| EP-06 Alertes/batch | US-50→52 | 14 | 11 | 3 |
| EP-07 Dashboards/audit | US-60→62 | 11 | 8 | 3 |
| EP-08 RGPD/sécu | US-70→72 | 13 | 13 | — |
| **Total** | **23 stories** | **108 pts** | **95** | **13** |

> **Charge estimée :** 108 pts ÷ ~20 pts/sprint = **~5–6 sprints** (10–12 semaines de dev solo).

---

## 5. Ordonnancement cible (jalons CDC)

| Sprint | Objectif | Stories | Pts |
|--------|----------|---------|-----|
| **S01** | Socle + Auth | US-01..04, US-10..13 | ~20 |
| **S02** | Biens, baux, affectation | US-20..24 | ~20 |
| **S03** | Paiements, garanties | US-30..32 | ~18 |
| **S04** | Honoraires, alertes batch | US-40, US-50..52 | ~19 |
| **S05** | Dashboards, audit | US-60..62 | ~18 |
| **S06** | RGPD, tests sécu, durcissement | US-70..72 | ~13 |

---

## 6. Dépendances & risques

| Élément | Type | Impact |
|---------|------|--------|
| US-03 (Flyway schéma) doit précéder toute story métier | Dépendance technique | Bloquant S02+ |
| US-02 (Realm Keycloak) doit précéder US-10..13 | Dépendance technique | Bloquant S01 US-10+ |
| US-30 (batch échéances) doit précéder US-31 (pointage) et US-50 (alertes) | Dépendance métier | Bloquant S03/S04 |
| US-23 (affectation) doit précéder US-40 (honoraires) | Dépendance métier | Bloquant S04 |
| Provisioning Keycloak Admin API (US-12) — couplage IdP | Risque technique | Encapsuler dans adaptateur `comptes` ; tester en intégration |
| RLS PostgreSQL (US-03) — injection contexte tenant par requête | Risque technique | Intercepteur Spring à valider dès S01/US-03 |
| Vélocité dev solo variable | Risque planning | Sprints S04–S05 ajustables selon le réel ; Should reportables |

---

## 7. Score de maturité (/20)

| Axe | Note (0–4) |
|-----|-----------|
| Complétude | 4 |
| Qualité | 4 |
| Sécurité | 3 |
| Traçabilité | 4 |
| Automatisation | 2 |
| **Total** | **17/20** |

> Lecture : 23 stories INVEST, critères d'acceptation GWT sur chacune, traçabilité CDC ↔ stories complète, DoD intégrant sécurité et tests. Axe *Sécurité* à 3 (ENF-02 et durcissement sont planifiés mais pas encore mis en œuvre). Axe *Automatisation* à 2 (CI en S01, outillage complet en S06).

---

## 8. Décision Gate 5

- **Décision recommandée :** ☑ ✅ **Go** · ☐ Go sous réserve · ☐ No Go
- **Réserves :** néant bloquant. Ordonnancement des sprints S04–S06 ajustable selon la vélocité réelle.
- **Date & responsable :** 2026-06-04 — jptshilombo@gmail.com.

---
*Livrable CGPA v1.0 — Phase 06 (Planification Agile). Prochaine phase : 07 — DevSecOps & développement.*
