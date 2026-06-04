# Étude de faisabilité — LoyerTracker

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Auteur | jptshilombo@gmail.com |
| Date | 2026-06-04 |
| Phase | 03 — Faisabilité |
| Gate visé | Gate 2 (verrou de codage) |
| Statut | ✅ **Validé — Gate 2 Go** |
| Prérequis | EB v1.1 validée — Gate 1 Go (17/20) |

---

## 1. Rappel du besoin

LoyerTracker est une application web de **gestion locative bailleur-centrée avec délégation fine**. Le bailleur centralise biens, baux et paiements et délègue l'exploitation, bien par bien, à des **gestionnaires mandataires** via l'entité pivot **`Affectation`** (1 gestionnaire actif par bien, rotation sans perte d'historique). Le cœur de valeur : **suivi des loyers mois par mois**, **garanties**, **honoraires** et **alertes d'échéances**, sous un **cloisonnement RBAC strict** (`bailleurId` + affectations actives) et conformité **RGPD**.

**Points techniquement structurants à éprouver dans cette étude :**
- P1 — Cloisonnement multi-tenant logique (`bailleurId`) appliqué côté serveur.
- P2 — RBAC à deux rôles avec un compte **gestionnaire multi-bailleur** (autorisation portée par les `Affectation`, pas par le compte Keycloak).
- P3 — Moteur d'**alertes batch quotidien** avec anti-doublon.
- P4 — Contraintes d'intégrité métier (1 bail actif/bien, 1 affectation active/bien).
- P5 — Audit log + exigences RGPD (effacement, conservation, export).

---

## 2. Faisabilité technique

| Aspect | Évaluation | Risque |
|--------|-----------|--------|
| **Stack Spring Boot** (API REST, service layer, scheduling) | Maîtrisée et adaptée. `@Scheduled` (ou Quartz si besoin de persistance/cluster) couvre nativement le moteur d'alertes batch (P3). Bean Validation + contraintes JPA pour P4. | Faible |
| **Angular** (SPA responsive, tableaux de bord, gardes de routes) | Adapté aux 2 tableaux de bord cloisonnés (BF-70/71). Responsive natif (BNF-11). | Faible |
| **Keycloak / OIDC + RBAC** (P2) | Rôles `BAILLEUR` / `GESTIONNAIRE` gérés nativement. **Point clé** : l'appartenance « ce gestionnaire ↔ ce bien » n'est **pas** un rôle Keycloak mais une donnée applicative (`Affectation`). Keycloak fait l'**authentification + rôle grossier** ; l'**autorisation fine** (scoping) est faite côté service Spring. C'est le bon découpage, mais il impose une discipline de contrôle serveur. | Moyen |
| **Cloisonnement `bailleurId`** (P1) | Faisable via filtrage systématique au **service layer** (jamais au seul niveau UI — BNF-02). Mécanismes : paramètre de scoping injecté depuis le token + filtres Hibernate (`@Filter`) ou clauses `WHERE` centralisées + tests d'autorisation systématiques. | **Moyen-élevé** |
| **Invitation tokenisée** (BF-02/03) | UUID v4, usage unique, TTL 72h : trivial (table `invitation` + endpoint d'acceptation créant le compte Keycloak via Admin API). | Faible |
| **PostgreSQL** (données + `audit_log`) | Largement suffisant pour ~50 biens/bailleur. Contraintes d'unicité partielles (`UNIQUE ... WHERE statut='ACTIVE'`) supportées (index partiels) → garantit P4 au niveau base. | Faible |
| **Moteur d'alertes** (P3) | Job quotidien parcourant baux/paiements/garanties, génération idempotente (clé `type+bienId+période`, garde sur `NON_LUE`). Volumétrie faible → pas d'enjeu de perf. | Faible |
| **Docker / CI-CD** (BNF-09) | Conteneurisation standard (api + web + postgres + keycloak via compose). Self-hosting réaliste. | Faible |
| **Performance** (BNF-06, < 2 s / 50 biens) | Volumétrie très faible ; atteignable sans optimisation particulière (index sur `bailleurId`, `bienId`, `periode`). | Faible |

**Verdict technique :** **faisable**. Aucun point sans solution éprouvée. Le seul vrai sujet d'ingénierie est le **cloisonnement (P1/P2)** — non pas par difficulté technique mais par **surface d'erreur** : une seule requête non scopée = fuite cross-bailleur. À traiter par conception (pattern centralisé) + tests d'autorisation dédiés, dès l'architecture (Phase 05).

---

## 3. Faisabilité organisationnelle

- **Compétences :** la stack (Spring Boot / Angular / Keycloak / Docker) est la **stack de référence** du porteur ; pas de montée en compétence bloquante. Le point le moins courant est l'intégration Keycloak Admin API (création de compte sur invitation) et le scoping multi-tenant — surmontables, bien documentés.
- **Charge (estimation indicative, dev solo) :**

| Lot | Contenu | Charge (j-h) |
|-----|---------|-------------|
| L0 | Socle : projet, Docker compose, Keycloak, CI minimale, schéma DB | 4–6 |
| L1 | Comptes/rôles + invitation tokenisée (BF-01→04) | 3–4 |
| L2 | Biens & baux + contraintes d'intégrité (BF-10→13) | 3–4 |
| L3 | Affectation & rotation + cloisonnement service layer (BF-20→24, P1/P2) | 5–7 |
| L4 | Paiements mois/mois + calcul attendus (BF-30→33) | 4–5 |
| L5 | Garanties + restitution (BF-40→42) | 2–3 |
| L6 | Honoraires (calcul auto) (BF-50→52) | 2–3 |
| L7 | Moteur d'alertes batch + anti-doublon (BF-60→65, P3) | 4–5 |
| L8 | Tableaux de bord bailleur/gestionnaire (BF-70→73) | 4–6 |
| L9 | Audit log + socle RGPD (export, effacement) (BNF-04/05) | 3–4 |
| — | Tests transverses d'autorisation, durcissement, doc | 4–6 |
| **Total** | | **~38–53 j-h** |

> Estimation d'**ordre de grandeur** (≈ 8–11 semaines à mi-temps solo), à affiner au backlog (Phase 06). Elle n'engage pas de date externe (contrainte EB : pas de délai imposé).

- **Délais :** non contraints ; cadence Agile par sprints (1–2 sem). Compatible avec un dev solo.
- **Risque orga principal :** **dispersion / abandon** (projet perso). Mitigation : MVP strict déjà cadré (Gate 1), lots livrables indépendamment.

**Verdict organisationnel :** **faisable**.

---

## 4. Faisabilité économique

| Poste | Coût estimé |
|-------|-------------|
| **Infrastructure / hébergement** | Self-hosting (VPS unique ou home-lab). Ordre de grandeur : **0–15 €/mois** (un petit VPS suffit pour api + web + postgres + keycloak ; possible aussi sur machine perso). |
| **Licences / services tiers** | **0 €** — toute la stack est open source (Spring Boot, Angular, Keycloak, PostgreSQL, Docker). Pas de SMTP requis au MVP (alertes in-app uniquement). Nom de domaine optionnel (~10 €/an). |
| **Temps (jours-homme)** | ~38–53 j-h (cf. §3) — coût en temps personnel, pas en cash. |

**Verdict économique :** **faisable** — coûts cash quasi nuls, conformes à la contrainte « budget minimal ». Le vrai « coût » est le **temps** du porteur.

---

## 5. Options & alternatives

| Option | Avantages | Inconvénients |
|--------|-----------|---------------|
| **A. Stack de référence (Spring Boot + Angular + Keycloak + PostgreSQL + Docker)** ⟵ *recommandée* | Compétences en place ; auth/RBAC industriels (Keycloak) ; alignée CGPA ; self-host ; séparation API/SPA nette | Keycloak « lourd » pour un usage solo ; deux briques à opérer (api + web) |
| **B. Monolithe full-stack (Spring Boot + Thymeleaf, sans SPA)** | Moins de surface (un seul artefact), plus simple à déployer | UX moins riche pour tableaux de bord ; s'écarte de la stack de référence ; responsive moins naturel |
| **C. Auth applicative (Spring Security seul, sans Keycloak)** | Moins d'infra ; suffisant pour 2 rôles | Réinvente invitation/sessions/reset ; va à l'encontre de BNF-01 (auth centralisée) ; dette sécurité |
| **D. Solution existante (Rentila, BailFacile…)** | Zéro développement | Ne couvre pas le modèle **bailleur multi-gestionnaires cloisonné** (cœur différenciant) ; données chez un tiers (contraire à la souveraineté visée) |

> **Arbitrage Keycloak :** pour un usage strictement solo, l'option C serait plus légère, mais BNF-01 impose une auth centralisée standard et le besoin d'**invitation + multi-bailleur** est mieux servi par Keycloak. On **conserve l'option A** ; un repli vers une auth allégée resterait possible si Keycloak s'avérait disproportionné (à réévaluer en architecture, Phase 05).

---

## 6. Risques & mitigation

| # | Risque | Proba | Impact | Mitigation |
|---|--------|-------|--------|------------|
| R1 | **Fuite cross-bailleur** par requête non scopée (P1) | Moyenne | **Critique** | Scoping centralisé au service layer (pattern unique), filtres Hibernate, **tests d'autorisation systématiques** par endpoint, revue de sécurité Gate 6. KPI « 0 accès cross-bailleur ». |
| R2 | **Mauvais découpage Keycloak ↔ autorisation fine** (P2) | Moyenne | Élevé | Acter en architecture : Keycloak = authN + rôle grossier ; autorisation fine = `Affectation` côté service. ADR dédié. |
| R3 | **Incohérence d'intégrité** (2 baux/affectations actifs sur un bien) (P4) | Faible | Élevé | Index uniques partiels PostgreSQL + validation service + transactions. |
| R4 | **Alertes manquées ou en double** (P3) | Faible | Moyen | Job idempotent (clé `type+bienId+période`), garde sur `NON_LUE`, supervision du job, test du batch. |
| R5 | **Non-conformité RGPD** (effacement, conservation, base légale) | Moyenne | Élevé | Registre de traitement, soft-delete + purge planifiée, export bailleur, anonymisation locataire à l'effacement ; traité dès l'architecture (BNF-04). |
| R6 | **Complexité opératoire Keycloak** en solo | Moyenne | Moyen | Conteneuriser via compose, réaliste en dev/staging ; option de repli auth allégée documentée (R2/§5). |
| R7 | **Abandon / dispersion** (projet perso) | Moyenne | Moyen | MVP strict, lots indépendants livrables, cadence sprints courte. |
| R8 | **Sur-ingénierie** (sur-investir le socle avant la valeur) | Moyenne | Moyen | Prioriser L1→L4 (cœur suivi) ; alertes/dashboards ensuite ; YAGNI sur l'event-driven (déjà exclu). |

Aucun risque **bloquant sans mitigation**. R1 (cloisonnement) et R5 (RGPD) sont les plus sensibles → à adresser explicitement en architecture (Phase 05) et au Gate 6 (DevSecOps).

---

## 7. Recommandation

> **Faisable sous conditions.** Le projet est **techniquement, organisationnellement et économiquement faisable** sur la stack de référence (option A). Coûts cash quasi nuls ; compétences en place ; aucun verrou technique. Les deux conditions à porter en architecture sont : **(1)** un **pattern de cloisonnement centralisé** côté serveur avec tests d'autorisation (R1), et **(2)** un **découpage clair Keycloak ↔ autorisation fine par `Affectation`** acté dans un ADR (R2), assorti d'une prise en compte **RGPD by design** (R5).

Recommandation de décision : **✅ Go** (les conditions ci-dessus sont des exigences de conception, non des bloqueurs de faisabilité).

---

## 8. Score de maturité (/20)

| Axe | Note (0–4) |
|-----|-----------|
| Complétude | 4 |
| Qualité | 3 |
| Sécurité | 4 |
| Traçabilité | 4 |
| Automatisation | 1 |
| **Total** | **16/20** |

> Lecture : **16/20 → « Solide »**. Les trois dimensions (technique / orga / éco) sont traitées, les risques majeurs identifiés avec mitigation, alternatives étudiées et arbitrées. *Sécurité* à 4 : les risques structurants (cloisonnement, RGPD) sont nommés et mitigés dès ce stade. *Automatisation* à 1 : la CI/CD reste à mettre en place (Phases 06–07), normal ici. *Qualité* à 3 : estimations de charge en ordre de grandeur, à affiner au backlog.

---

## 9. Décision Gate 2

- **Décision (consignée par le porteur) :** ☑ ✅ **Go** · ☐ Go sous réserve · ☐ No Go
- **Justification :** faisabilité technique/orga/éco démontrée, options étudiées et arbitrées (stack A retenue), risques majeurs identifiés avec mitigation, recommandation argumentée, score 16/20 (≥ 14).
- **Conditions à porter en Phase 04 (CDC) / Phase 05 (Architecture) — n'empêchent pas le Go :**
  1. **ADR « cloisonnement multi-tenant »** : pattern de scoping `bailleurId` centralisé + stratégie de tests d'autorisation (R1).
  2. **ADR « Keycloak vs autorisation fine »** : périmètre Keycloak (authN + rôle) vs autorisation par `Affectation` (R2) ; réévaluer le ratio coût/bénéfice de Keycloak en solo.
  3. **Volet RGPD by design** : effacement/anonymisation, conservation, export, registre (R5).
- **Date & responsable :** 2026-06-04 — jptshilombo@gmail.com (décideur Gate 2).

---
*Livrable CGPA v1.0 — Phase 03 (Faisabilité). ⛔ Verrou de codage maintenu : Gates 3 (CDC) et 4 (Architecture) restants avant tout développement. Prochaine phase : 04 — Cahier des charges (Gate 3).*
