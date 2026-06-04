# Cahier des charges (CDC) — LoyerTracker

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Auteur | jptshilombo@gmail.com |
| Date | 2026-06-04 |
| Phase | 04 — Cahier des charges |
| Gate visé | Gate 3 (verrou de codage) |
| Statut | En revue |
| Prérequis | EB v1.1 (Gate 1 Go) + Faisabilité (Gate 2 Go) |

---

## 1. Contexte & objectifs

LoyerTracker est une application web de **gestion locative bailleur-centrée avec délégation fine par bien**. Ce CDC spécifie, de façon **contractuelle et vérifiable**, les exigences à implémenter pour le **MVP** tel que cadré par l'EB v1.1 et confirmé faisable par l'étude de faisabilité (stack de référence retenue : Spring Boot · Angular · Keycloak/OIDC · PostgreSQL · Docker).

**Objectifs du CDC :**
- Détailler chaque exigence fonctionnelle avec un **critère d'acceptation testable** (forme *Étant donné… quand… alors…*).
- Spécifier les exigences non fonctionnelles (sécurité, cloisonnement, RGPD, perf, dispo).
- Spécifier les interfaces (API REST, Keycloak, job batch).
- Garantir la **traçabilité** Besoin (BF/BNF) → Exigence (EF/ENF) → Cas de test (TC).

> **Conventions :** priorités MoSCoW (Must/Should/Could). Toute exigence `Must` est bloquante pour la recette MVP. Devise EUR, langue FR, fuseau Europe/Paris.

---

## 2. Périmètre & livrables

- **Périmètre :** strictement celui de l'EB v1.1 §2.1 (inclus) — comptes/délégation, biens/baux, affectation/rotation, paiements, garanties, honoraires, alertes in-app, tableaux de bord cloisonnés, audit log. Exclusions EB v1.1 §2.2 (quittance/mandat PDF, IRL, rapprochement bancaire, revenus fonciers, mobile natif, paiement en ligne, multi-bailleur/SCI, alertes e-mail, frais exceptionnels) **hors périmètre** de ce CDC.
- **Livrables attendus (phases aval) :** API backend + SPA Angular conteneurisées, schéma PostgreSQL + migrations, configuration Keycloak (realm/rôles/clients), suite de tests (unitaires + intégration + tests d'autorisation), documentation (README, ADR), pipeline CI/CD.
- **Jalons (indicatifs, à ordonnancer au backlog Phase 06) :**
  - J1 — Socle + auth/rôles + invitation (EF-01x).
  - J2 — Biens/baux + affectation/rotation + cloisonnement (EF-02x, EF-03x).
  - J3 — Paiements + garanties + honoraires (EF-04x, EF-05x, EF-06x).
  - J4 — Moteur d'alertes + tableaux de bord + audit (EF-07x, EF-08x, EF-09x).
  - J5 — RGPD (export/effacement) + durcissement + recette (ENF).

---

## 3. Exigences fonctionnelles détaillées

> Critères d'acceptation au format **Étant donné (ED) / Quand (Q) / Alors (A)**.

### 3.1 Module Comptes, rôles & délégation

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|-----------------------|----------|--------|
| EF-01 | Création de compte bailleur + authentification OIDC | ED un visiteur sans compte · Q il s'inscrit comme bailleur et s'authentifie via Keycloak · A un compte au rôle `BAILLEUR` est créé et une session OIDC valide est ouverte. | Must | BF-01 |
| EF-02 | Invitation d'un gestionnaire par lien tokenisé | ED un bailleur authentifié · Q il invite un gestionnaire par e-mail · A un token UUID v4 à usage unique, valide 72h, est généré et un lien d'acceptation est fourni. | Must | BF-02 |
| EF-03 | Expiration / régénération du token d'invitation | ED un token d'invitation expiré (>72h) ou déjà utilisé · Q le destinataire tente de l'utiliser · A l'acceptation est refusée et le bailleur peut générer un nouveau lien. | Must | BF-02 |
| EF-04 | Création du compte gestionnaire via invitation uniquement | ED un lien d'invitation valide · Q le destinataire l'accepte · A un compte au rôle `GESTIONNAIRE` est créé dans Keycloak ; toute tentative de création hors invitation est refusée. | Must | BF-03 |
| EF-05 | Compte gestionnaire multi-bailleur | ED un gestionnaire déjà lié au bailleur A · Q le bailleur B l'invite et il accepte · A le même compte est associé à A et B, le cloisonnement étant porté par les `Affectation` actives (pas par le compte). | Must | BF-03 |
| EF-06 | Attribution et application des rôles (RBAC) | ED un utilisateur authentifié · Q il accède à une fonction · A l'accès est autorisé/refusé selon son rôle conformément à la matrice EB §6. | Must | BF-04 |

### 3.2 Module Biens & baux

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|-----------------------|----------|--------|
| EF-10 | Gestion des biens (CRUD + statut) | ED un bailleur authentifié · Q il crée/modifie/archive un bien · A le bien est persisté avec un statut `LIBRE` / `LOUE` / `EN_TRAVAUX` et rattaché à son `bailleurId`. | Must | BF-10 |
| EF-11 | Enregistrement d'un bail | ED un bien existant · Q le bailleur (ou le gestionnaire affecté actif) enregistre un bail (locataire, loyer CC, dates début/fin, dépôt de garantie) · A le bail est persisté et lié au bien. | Must | BF-11 |
| EF-12 | Unicité du bail actif par bien | ED un bien avec un bail actif · Q on tente d'enregistrer un second bail actif sur ce bien · A l'opération est rejetée (contrainte d'unicité). | Must | BF-12 |
| EF-13 | Historique des baux d'un bien | ED un bien ayant eu plusieurs baux · Q le bailleur consulte le bien · A la liste chronologique des baux (actifs et clos) est affichée. | Should | BF-13 |

### 3.3 Module Affectation & rotation

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|-----------------------|----------|--------|
| EF-20 | Création d'une affectation | ED un bailleur et un gestionnaire invité · Q le bailleur crée une affectation (gestionnaire + bien + type d'honoraires + montant + dates) · A l'affectation est persistée au statut `ACTIVE`. | Must | BF-20 |
| EF-21 | Unicité du gestionnaire actif par bien | ED un bien avec une affectation `ACTIVE` · Q on tente d'en créer une seconde `ACTIVE` sur le même bien · A l'opération est rejetée (index unique partiel `bienId` + `ACTIVE`). | Must | BF-21 |
| EF-22 | Révocation d'une affectation | ED une affectation `ACTIVE` · Q le bailleur la révoque · A le statut passe à `REVOQUEE`, `dateRevocation` est enregistrée ; le compte Keycloak du gestionnaire n'est pas désactivé. | Must | BF-22 |
| EF-23 | Rotation sans perte d'historique | ED une affectation révoquée sur un bien · Q le bailleur crée une nouvelle affectation sur ce bien · A la nouvelle affectation est `ACTIVE` et l'historique des affectations précédentes reste intact. | Must | BF-23 |
| EF-24 | Consultation de l'historique des affectations | ED un bien avec plusieurs affectations · Q le bailleur consulte l'historique · A la liste complète est affichée ; un gestionnaire ne voit que ses propres affectations (bien, dates, statut) **sans données métier** du bien après révocation. | Should | BF-24 |

### 3.4 Module Suivi des paiements

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|-----------------------|----------|--------|
| EF-30 | Pointage d'un loyer mensuel | ED un bail actif et un mois donné · Q un acteur autorisé pointe le loyer · A un statut parmi `RECU` / `PARTIEL` / `EN_RETARD` / `IMPAYE` est enregistré pour `(bien, période)`. | Must | BF-30 |
| EF-31 | Historique des paiements par bien/période | ED un bien avec des pointages · Q l'utilisateur consulte l'historique · A la liste des loyers attendus/encaissés par mois est affichée. | Must | BF-31 |
| EF-32 | Paiement partiel | ED un loyer attendu · Q un acteur enregistre un paiement partiel (montant reçu) · A le reste dû est calculé et le statut passe à `PARTIEL`. | Should | BF-32 |
| EF-33 | Calcul automatique des loyers attendus | ED un bail actif · Q le système génère les échéances mensuelles · A chaque mois civil (du mois suivant le début jusqu'au mois du terme, sans prorata) a un loyer attendu = `loyer_CC`, conformément à EB Annexe A.3. | Must | BF-33 |

### 3.5 Module Garanties locatives

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|-----------------------|----------|--------|
| EF-40 | Enregistrement d'un dépôt de garantie | ED un bail · Q l'acteur enregistre un dépôt (montant, type : caution/garant/Visale…, date) · A la garantie est créée au statut `DETENU`. | Must | BF-40 |
| EF-41 | Suivi de l'état de la garantie | ED une garantie `DETENU` · Q l'acteur enregistre une restitution · A le statut évolue `DETENU → RESTITUE_PARTIEL → RESTITUE_TOTAL` conformément à EB Annexe A.5. | Must | BF-41 |
| EF-42 | Retenue à la restitution | ED une restitution partielle · Q l'acteur saisit une retenue (montant + motif libre) · A la retenue est enregistrée et tracée. | Should | BF-42 |

### 3.6 Module Honoraires

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|-----------------------|----------|--------|
| EF-50 | Définition des honoraires d'une affectation | ED une affectation · Q le bailleur définit le type (`POURCENTAGE`/`FORFAIT`) et le montant · A ces paramètres sont persistés sur l'affectation. | Must | BF-50 |
| EF-51 | Calcul automatique des honoraires mensuels | ED un mois et une affectation active · Q un pointage de paiement a lieu (ou le job de fin de mois s'exécute) · A l'honoraire du mois est calculé selon EB Annexe A.4 (`% × loyer encaissé` ou `forfait`). | Should | BF-51 |
| EF-52 | Suivi du statut des honoraires | ED un honoraire calculé · Q le bailleur le traite · A le statut suit `DU → EN_ATTENTE → PAYE`, la validation `→ PAYE` étant réservée au bailleur. | Should | BF-52 |

### 3.7 Module Alertes & échéances

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|-----------------------|----------|--------|
| EF-60 | Alerte loyer en retard | ED un loyer attendu non `RECU` au-delà de l'échéance + tolérance · Q le job batch quotidien s'exécute · A une alerte `LOYER_EN_RETARD` est générée pour `(bien, période)`. | Must | BF-60 |
| EF-61 | Alerte fin de bail | ED un bail dont le terme est dans ≤ J-X (défaut 60) · Q le job s'exécute · A une alerte `FIN_BAIL` est générée. | Must | BF-61 |
| EF-62 | Alerte échéance de préavis | ED une échéance de préavis atteinte · Q le job s'exécute · A une alerte `PREAVIS` est générée. | Must | BF-62 |
| EF-63 | Alerte garantie non restituée | ED une garantie `DETENU` et un bail terminé depuis > X jours (défaut 30) · Q le job (rattaché à EF-61) s'exécute · A une alerte `GARANTIE_NON_RESTITUEE` est générée. | Should | BF-63 |
| EF-64 | Cloisonnement des destinataires d'alerte | ED une alerte sur un bien · Q elle est diffusée · A le bailleur reçoit les alertes de tous ses biens et le gestionnaire uniquement celles de ses biens affectés **actifs**. | Must | BF-64 |
| EF-65 | Alertes in-app + anti-doublon | ED une alerte déjà `NON_LUE` pour `(type, bienId, periode)` · Q le job réévalue la condition · A aucune alerte en double n'est créée ; les alertes sont visibles dans le tableau de bord (in-app uniquement). | Must | BF-65, BNF-08 |

### 3.8 Module Tableaux de bord & cloisonnement

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|-----------------------|----------|--------|
| EF-70 | Tableau de bord bailleur | ED un bailleur authentifié · Q il ouvre son tableau de bord · A il voit la vue consolidée de **tous ses biens** : encaissements du mois, retards, prochaines échéances, alertes non lues. | Must | BF-70 |
| EF-71 | Tableau de bord gestionnaire | ED un gestionnaire authentifié · Q il ouvre son tableau de bord · A il ne voit **que ses biens affectés actifs** et leurs données. | Must | BF-71 |
| EF-72 | Interdiction d'accès hors périmètre (serveur) | ED un gestionnaire · Q il tente d'accéder (API directe) à un bien non affecté ou à un autre bailleur · A l'accès est refusé **côté serveur** (HTTP 403), indépendamment de l'UI. | Must | BF-72, BNF-02 |
| EF-73 | Journalisation des actions sensibles | ED une action d'écriture sensible (cf. EB BNF-05) · Q elle est exécutée · A une entrée `AuditLog` horodatée (acteur, rôle, action, entité, détails) est créée. | Should | BF-73, BNF-05 |

---

## 4. Exigences non fonctionnelles

| ID | Catégorie | Exigence | Critère d'acceptation | Source |
|----|-----------|----------|-----------------------|--------|
| ENF-01 | Sécurité — Auth | AuthN centralisée Keycloak/OIDC ; rôles `BAILLEUR`/`GESTIONNAIRE`. | ED une requête sans token valide · Q elle atteint une API protégée · A réponse 401 ; avec rôle insuffisant · A réponse 403. | BNF-01 |
| ENF-02 | Sécurité — Cloisonnement | Toute requête de données scopée par `bailleurId` (et affectations actives pour un gestionnaire), contrôlée au **service layer**. | ED un jeu de tests d'autorisation couvrant chaque endpoint · Q ils s'exécutent · A **0** accès cross-bailleur/cross-affectation réussi. | BNF-02 |
| ENF-03 | Sécurité — Moindre privilège & secrets | Permissions minimales par rôle ; secrets hors dépôt (env/vault). | ED le dépôt et la config · Q on scanne (secrets) · A aucun secret en clair ; chaque rôle n'accède qu'à ses fonctions. | BNF-03 |
| ENF-04 | RGPD | Minimisation, base légale, droits d'accès/rectification/effacement, conservation, registre. | ED un bailleur · Q il demande l'effacement d'un locataire · A les données personnelles sont anonymisées/supprimées et tracées ; ED une demande d'export · A un export des données du bailleur est fourni. | BNF-04, BNF-13 |
| ENF-05 | Traçabilité / Audit | Journalisation des écritures sensibles, conservation bail + 3 ans, accès bailleur seul. | ED une action sensible · Q elle a lieu · A `AuditLog` créé ; ED un gestionnaire · Q il tente de lire l'audit · A 403. | BNF-05 |
| ENF-06 | Performance | Tableau de bord < 2 s pour ≤ 50 biens. | ED un portefeuille de 50 biens · Q on charge le tableau de bord · A temps de réponse < 2 s (P95). | BNF-06 |
| ENF-07 | Disponibilité / Sauvegarde | Best-effort self-hosted ; sauvegardes régulières PostgreSQL. | ED une stratégie de backup planifiée · Q une restauration est testée · A la base est restaurée sans perte au-delà de la fenêtre définie. | BNF-07 |
| ENF-08 | Fiabilité des alertes | Alerte due émise ≤ J+1 ; anti-doublon `(type, bienId, periode)` sur `NON_LUE`. | ED une condition déclenchante · Q le job quotidien s'exécute · A l'alerte existe en exactement 1 exemplaire `NON_LUE`. | BNF-08 |
| ENF-09 | Portabilité | Déploiement conteneurisé Docker ; config externalisée. | ED un environnement vierge · Q on lance `docker compose up` · A la stack (api+web+postgres+keycloak) démarre via variables d'environnement. | BNF-09 |
| ENF-10 | Maintenabilité / Qualité | Tests unitaires + intégration ; DoD (tests + doc + sécurité). | ED la CI · Q elle s'exécute · A les tests passent et la couverture des règles critiques (cloisonnement, intégrité) est vérifiée. | BNF-10 |
| ENF-11 | Accessibilité / Responsive | Web responsive (consultation mobile alertes + tableau de bord). | ED un viewport mobile · Q on ouvre le tableau de bord · A l'affichage reste lisible et utilisable. | BNF-11 |
| ENF-12 | i18n / Devise | FR, EUR, Europe/Paris. | ED un montant et une date · Q ils sont affichés · A format FR, devise EUR, fuseau Europe/Paris. | BNF-12 |

---

## 5. Interfaces & intégrations

> Spécification **au niveau contrat** (ressources/règles), pas de conception interne (réservée Phase 05).

### 5.1 API REST (ressources principales)
- `auth`/session : déléguée à **Keycloak (OIDC)** ; le backend valide les jetons (Bearer JWT) et en extrait rôle + identifiant.
- Ressources métier exposées (CRUD selon droits, **toutes scopées `bailleurId`**) : `biens`, `baux`, `affectations`, `paiements`, `garanties`, `honoraires`, `alertes`, `invitations`, `audit-log`.
- Règles transverses : authentification obligatoire (sauf acceptation d'invitation), autorisation au service layer, réponses normalisées d'erreur (401/403/404/409 pour conflit d'unicité), pagination des listes.

### 5.2 Keycloak
- 1 realm dédié ; client SPA (public, PKCE) + client backend (resource server).
- Rôles realm : `BAILLEUR`, `GESTIONNAIRE`.
- Création de compte gestionnaire via **Admin API** déclenchée par l'acceptation d'invitation (EF-04).

### 5.3 Job batch d'alertes
- Planification quotidienne (`@Scheduled`, défaut 07:00 Europe/Paris).
- Parcourt baux/paiements/garanties actifs de tous les bailleurs, applique les règles EF-60→63, génère les alertes idempotentes (EF-65).

### 5.4 Intégrations externes
- **Aucune au MVP** (pas de SMTP, pas d'agrégation bancaire, pas de paiement en ligne). Surface d'intégration volontairement nulle.

---

## 6. Contraintes

- **Technologiques :** stack de référence imposée (Spring Boot, Angular, Keycloak/OIDC, PostgreSQL, Docker, CI/CD).
- **Réglementaires :** RGPD (données locataires/bailleurs) — by design (ENF-04/05).
- **Budgétaires :** open source uniquement ; self-hosting ; coûts cash quasi nuls.
- **Organisationnelles :** dev solo ; livraison incrémentale par lots/jalons.
- **Conditions issues du Gate 2 à honorer en Phase 05 :** ADR cloisonnement multi-tenant, ADR Keycloak vs autorisation fine, volet RGPD by design.

---

## 7. Matrice de traçabilité

> Couverture **besoin (EB) → exigence (CDC) → cas de test (TC)**. Les TC seront détaillés en Phase 09 (QA).

| Besoin (EB) | Exigence (CDC) | Cas de test prévu |
|-------------|----------------|-------------------|
| BF-01 | EF-01 | TC-01 inscription + session OIDC |
| BF-02 | EF-02, EF-03 | TC-02 invitation token 72h / expiration |
| BF-03 | EF-04, EF-05 | TC-03 compte sur invitation + multi-bailleur |
| BF-04 | EF-06 | TC-04 application RBAC |
| BF-10 | EF-10 | TC-10 CRUD bien + statut |
| BF-11 | EF-11 | TC-11 enregistrement bail |
| BF-12 | EF-12 | TC-12 rejet 2ᵉ bail actif |
| BF-13 | EF-13 | TC-13 historique baux |
| BF-20 | EF-20 | TC-20 création affectation |
| BF-21 | EF-21 | TC-21 rejet 2ᵉ affectation active |
| BF-22 | EF-22 | TC-22 révocation |
| BF-23 | EF-23 | TC-23 rotation sans perte |
| BF-24 | EF-24 | TC-24 historique affectations / vue gestionnaire |
| BF-30 | EF-30 | TC-30 pointage statuts |
| BF-31 | EF-31 | TC-31 historique paiements |
| BF-32 | EF-32 | TC-32 paiement partiel |
| BF-33 | EF-33 | TC-33 calcul loyers attendus (Annexe A.3) |
| BF-40 | EF-40 | TC-40 dépôt garantie |
| BF-41 | EF-41 | TC-41 transitions de statut garantie |
| BF-42 | EF-42 | TC-42 retenue + motif |
| BF-50 | EF-50 | TC-50 paramétrage honoraires |
| BF-51 | EF-51 | TC-51 calcul honoraires (Annexe A.4) |
| BF-52 | EF-52 | TC-52 statuts honoraires |
| BF-60 | EF-60 | TC-60 alerte retard |
| BF-61 | EF-61 | TC-61 alerte fin de bail |
| BF-62 | EF-62 | TC-62 alerte préavis |
| BF-63 | EF-63 | TC-63 alerte garantie non restituée |
| BF-64 | EF-64 | TC-64 cloisonnement destinataires |
| BF-65 | EF-65 | TC-65 anti-doublon in-app |
| BF-70 | EF-70 | TC-70 dashboard bailleur |
| BF-71 | EF-71 | TC-71 dashboard gestionnaire |
| BF-72 | EF-72 | TC-72 refus 403 hors périmètre |
| BF-73 | EF-73 | TC-73 écriture audit log |
| BNF-01 | ENF-01 | TC-80 401/403 |
| BNF-02 | ENF-02 | TC-81 suite d'autorisation cross-bailleur |
| BNF-03 | ENF-03 | TC-82 scan secrets / privilèges |
| BNF-04/13 | ENF-04 | TC-83 effacement + export RGPD |
| BNF-05 | ENF-05 | TC-84 audit + accès restreint |
| BNF-06 | ENF-06 | TC-85 perf 50 biens |
| BNF-07 | ENF-07 | TC-86 backup/restore |
| BNF-08 | ENF-08 | TC-87 fiabilité/anti-doublon alertes |
| BNF-09 | ENF-09 | TC-88 docker compose up |
| BNF-10 | ENF-10 | TC-89 CI tests |
| BNF-11 | ENF-11 | TC-90 responsive |
| BNF-12 | ENF-12 | TC-91 format FR/EUR |

---

## 8. Score de maturité (/20)

| Axe | Note (0–4) |
|-----|-----------|
| Complétude | 4 |
| Qualité | 4 |
| Sécurité | 4 |
| Traçabilité | 4 |
| Automatisation | 1 |
| **Total** | **17/20** |

> Lecture : **17/20 → « Solide+ »**. Toutes les exigences fonctionnelles (33 EF) et non fonctionnelles (12 ENF) ont un **critère d'acceptation testable** ; interfaces spécifiées au niveau contrat ; **matrice de traçabilité complète** BF/BNF → EF/ENF → TC. *Automatisation* à 1 : la CI/CD relève des Phases 06–07. Aucune exigence non vérifiable détectée.

---

## 9. Décision Gate 3

- **Décision recommandée :** ☑ ✅ **Go** · ☐ Go sous réserve · ☐ No Go
- **Justification :** exigences fonctionnelles détaillées et priorisées, non fonctionnelles spécifiées (sécurité/perf/dispo/RGPD), critère d'acceptation présent pour chaque exigence clé, interfaces/intégrations spécifiées, matrice de traçabilité complète, score 17/20 (≥ 14).
- **Réserves / points portés en Phase 05 (Architecture) :**
  1. Concrétiser les 3 conditions du Gate 2 (ADR cloisonnement, ADR Keycloak vs autorisation fine, RGPD by design).
  2. Définir le schéma de données détaillé et les index (unicités partielles EF-12/EF-21).
  3. Décider de la mécanique de génération des échéances de loyers (EF-33) et du déclenchement des honoraires (EF-51).
- **Date & responsable :** 2026-06-04 — jptshilombo@gmail.com (décideur Gate 3).

---
*Livrable CGPA v1.0 — Phase 04 (CDC). ⛔ Verrou de codage maintenu : **Gate 4 (Architecture)** est le dernier gate avant l'autorisation de développement. Prochaine phase : 05 — Architecture & conception (Gate 4).*
